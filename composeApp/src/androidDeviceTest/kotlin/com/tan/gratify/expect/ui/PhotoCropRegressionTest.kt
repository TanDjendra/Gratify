package com.tan.gratify.expect.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.test.mock.MockContentResolver
import android.test.mock.MockContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Exercises the production decoder/exporter on Android with generated images only. */
object PhotoCropRegressionTest {
    private class TestContext(private val directory: File) : MockContext() {
        private val resolver = MockContentResolver(this)
        override fun getApplicationInfo() = android.content.pm.ApplicationInfo().apply { targetSdkVersion = 36 }
        override fun getPackageName() = "com.tan.gratify.photocroptest"
        override fun getOpPackageName() = packageName
        override fun getAttributionSource() = android.content.AttributionSource.Builder(android.os.Process.myUid()).setPackageName(packageName).build()
        override fun getContentResolver() = resolver
        override fun getApplicationContext(): Context = this
        override fun getCacheDir() = directory
    }
    private fun near(actual: Int, expected: Int) = abs(Color.red(actual)-Color.red(expected))<35 &&
        abs(Color.green(actual)-Color.green(expected))<35 && abs(Color.blue(actual)-Color.blue(expected))<35
    @JvmStatic fun main(args: Array<String>) {
        Thread.setDefaultUncaughtExceptionHandler { _, error -> error.printStackTrace(); kotlin.system.exitProcess(1) }
        val directory = File(args[1]).apply { mkdirs() }
        val context = TestContext(directory)
        if (args[0] == "prepare") {
            val long = Bitmap.createBitmap(2000, 6000, Bitmap.Config.ARGB_8888)
            Canvas(long).apply {
                drawColor(Color.RED)
                drawRect(0f,2000f,2000f,4000f,Paint().apply { color=Color.GREEN })
                drawRect(0f,4000f,2000f,6000f,Paint().apply { color=Color.BLUE })
            }
            File(directory,"long.jpg").outputStream().use { check(long.compress(Bitmap.CompressFormat.JPEG,95,it)) }
            long.recycle()
            for (orientation in 1..8) {
                val bitmap=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888)
                Canvas(bitmap).apply {
                    drawColor(Color.RED)
                    drawRect(40f,0f,80f,20f,Paint().apply { color=Color.GREEN })
                    drawRect(0f,20f,40f,40f,Paint().apply { color=Color.BLUE })
                    drawRect(40f,20f,80f,40f,Paint().apply { color=Color.YELLOW })
                }
                val file=File(directory,"orientation-$orientation.jpg")
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG,100,it)) }
                bitmap.recycle()
                val exif=java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                    put("Exif\u0000\u0000II".encodeToByteArray()); putShort(42); putInt(8); putShort(1)
                    putShort(0x112); putShort(3); putInt(1); putShort(orientation.toShort()); putShort(0); putInt(0)
                }.array()
                val jpeg=file.readBytes()
                file.writeBytes(jpeg.copyOfRange(0,2)+byteArrayOf(0xff.toByte(),0xe1.toByte(),0,34)+exif+jpeg.copyOfRange(2,jpeg.size))
            }
            println("Prepared synthetic photos")
            return
        }
        for (legacy in listOf(false,true)) {
            val image=decodeCropPhoto(context,Uri.fromFile(File(directory,"long.jpg")),legacy)
            check(max(image.width,image.height)<=2048)
            check(image.allocationByteCount<=2048*2048*4)
            val centered=SquarePhotoCrop.centered(image.width,image.height)
            for ((crop,color) in listOf(centered to Color.GREEN,centered.move(image.width,image.height,0f,-10000f,320f,1f) to Color.BLUE)) {
                val uri=exportCropPhoto(context,image,crop.pixels(image.width,image.height))
                val output=BitmapFactory.decodeFile(Uri.parse(uri).path)!!
                check(output.width==512 && output.height==512)
                check(near(output.getPixel(256,256),color)) { "Crop exported wrong source region" }
                output.recycle()
            }
            image.recycle()
            val expected=listOf(Color.RED,Color.GREEN,Color.YELLOW,Color.BLUE,Color.RED,Color.BLUE,Color.YELLOW,Color.GREEN)
            for (orientation in 1..8) {
                val rotated=decodeCropPhoto(context,Uri.fromFile(File(directory,"orientation-$orientation.jpg")),legacy)
                check(rotated.width==if(orientation<5)80 else 40)
                check(rotated.height==if(orientation<5)40 else 80)
                check(near(rotated.getPixel(5,5),expected[orientation-1])) { "Incorrect EXIF orientation $orientation, legacy=$legacy" }
                rotated.recycle()
            }
            println("PASS: bounded long-photo decode, chosen crop, 512 square JPEG, all eight EXIF orientations; legacy=$legacy")
        }
        val transparent=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        val output=BitmapFactory.decodeFile(Uri.parse(exportCropPhoto(context,transparent,CropPixels(0,0,20))).path)!!
        check(near(output.getPixel(256,256),Color.WHITE)); output.recycle(); transparent.recycle()
        var rejected=false
        try { decodeCropPhoto(context,Uri.fromFile(File(directory,"missing.jpg"))) } catch (_: Exception) { rejected=true }
        check(rejected)
        println("PASS: transparent photos use white JPEG background; missing image rejected")
    }
}
