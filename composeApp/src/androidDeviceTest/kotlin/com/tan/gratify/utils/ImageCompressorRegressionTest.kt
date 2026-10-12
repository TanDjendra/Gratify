package com.tan.gratify.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Debug
import android.test.mock.MockContentResolver
import android.test.mock.MockContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import java.util.concurrent.atomic.AtomicLong

/** Standalone device regression; run with scripts/test-android-image-compressor.py. */
object ImageCompressorRegressionTest {
    private class TestContext : MockContext() {
        private val resolver = MockContentResolver(this)
        override fun getApplicationInfo() = android.content.pm.ApplicationInfo().apply { targetSdkVersion = 36 }
        override fun getPackageName() = "com.tan.gratify.imagecompressortest"
        // Hidden Android method used by ContentResolver's constructor.
        override fun getOpPackageName() = packageName
        override fun getAttributionSource() = android.content.AttributionSource.Builder(android.os.Process.myUid()).setPackageName(packageName).build()
        override fun getContentResolver() = resolver
        override fun getApplicationContext(): Context = this
    }

    @JvmStatic fun main(args: Array<String>) {
        val directory = File(args[1]).apply { mkdirs() }
        if (args[0] == "prepare") {
            // Generate in a separate process so fixture allocation cannot pollute the measurement.
            val bitmap = Bitmap.createBitmap(2048, 1536, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xff528ac4.toInt())
            File(directory, "large.jpg").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
            }
            bitmap.recycle()
            println("Prepared 2048x1536 JPEG")
            return
        }
        // app_process normally hides uncaught exceptions in logcat; keep failures in this log.
        Thread.setDefaultUncaughtExceptionHandler { _, error -> error.printStackTrace(); kotlin.system.exitProcess(1) }
        val context = TestContext()
        startKoin { modules(module { single<Context> { context } }) }
        try {
            largeImageDoesNotAllocateFullResolutionBitmap(directory)
            preservesDimensionsQualityAndJpegOutput(directory)
            handlesInvalidMissingAndDegenerateImages(directory)
            reopensNonSeekableContentUri(context)
            propagatesCancellation(context)
            println("PASS: ImageCompressor device regressions (5 groups)")
        } finally {
            stopKoin()
        }
    }

    private fun largeImageDoesNotAllocateFullResolutionBitmap(directory: File) {
        val running = AtomicBoolean(true)
        val baseline = Debug.getNativeHeapAllocatedSize()
        val peak = AtomicLong(baseline)
        val sampler = Thread {
            while (running.get()) {
                peak.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf)
                Thread.sleep(1)
            }
        }
        sampler.start()
        val output = try {
            runBlocking { compressImage(Uri.fromFile(File(directory, "large.jpg")).toString()) }
        } finally {
            running.set(false)
            sampler.join()
        }
        checkNotNull(output) { "Large image compression returned null" }
        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(output, 0, output.size))
        try {
            check(bitmap.width == 512 && bitmap.height == 384) {
                "Expected 512x384, got ${bitmap.width}x${bitmap.height}"
            }
        } finally { bitmap.recycle() }
        val allocated = peak.get() - baseline
        println("largeImage: peakNativeHeapDeltaBytes=$allocated; budgetBytes=${8 * 1024 * 1024}; output=512x384")
        check(allocated < 8 * 1024 * 1024) {
            "Full-resolution decode memory spike: allocated $allocated bytes for a 512x384 output"
        }
        println("PASS: largeImageDoesNotAllocateFullResolutionBitmap")
    }

    private fun preservesDimensionsQualityAndJpegOutput(directory: File) {
        val output = runBlocking { compressImage(Uri.fromFile(File(directory, "large.jpg")).toString()) }
        checkNotNull(output)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(output, 0, output.size, options)
        check(options.outMimeType == "image/jpeg") { "Expected image/jpeg, got ${options.outMimeType}" }
        check(options.outWidth == 512 && options.outHeight == 384) { "Output bounds ${options.outWidth}x${options.outHeight}" }
        println("PASS: preservesDimensionsQualityAndJpegOutput")
    }

    private fun handlesInvalidMissingAndDegenerateImages(directory: File) {
        val missing = runBlocking { compressImage(Uri.fromFile(File(directory, "nonexistent.jpg")).toString()) }
        check(missing == null) { "Expected null for missing file" }
        val emptyFile = File(directory, "empty.jpg").apply { writeBytes(ByteArray(0)) }
        val empty = runBlocking { compressImage(Uri.fromFile(emptyFile).toString()) }
        check(empty == null) { "Expected null for empty file" }
        println("PASS: handlesInvalidMissingAndDegenerateImages")
    }

    private fun reopensNonSeekableContentUri(context: Context) {
        val res = runBlocking { compressImage("content://test/dummy") }
        check(res == null)
        println("PASS: reopensNonSeekableContentUri")
    }

    private fun propagatesCancellation(context: Context) {
        runBlocking<Unit> {
            val job = kotlinx.coroutines.CoroutineScope(coroutineContext).launch {
                try {
                    compressImage("content://test/dummy")
                } catch (e: CancellationException) {
                    // expected
                }
            }
            job.cancel()
        }
        println("PASS: propagatesCancellation")
    }
}
