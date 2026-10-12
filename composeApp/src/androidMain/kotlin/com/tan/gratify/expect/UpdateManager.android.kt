package com.tan.gratify.expect

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import com.tan.domain.data.model.update.UpdateData
import com.tan.domain.data.model.update.UpdateDelta
import com.tan.gratify.utils.StreamingDelta
import com.tan.gratify.utils.selectDelta
import com.tan.gratify.utils.IncompatibleUpdatePackage
import com.tan.gratify.utils.UpdatePackageIdentity
import com.tan.gratify.utils.validateUpdateIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

actual fun downloadAndInstallApk(update: UpdateData) = AndroidAppUpdater.start(update)
actual fun updateInstallState(): StateFlow<UpdateInstallState> = AndroidAppUpdater.state
actual fun installDownloadedUpdate() = AndroidAppUpdater.install()
actual fun retryApkUpdate() = AndroidAppUpdater.retry()
actual fun cancelApkUpdate() = AndroidAppUpdater.cancel()

/** DownloadManager owns the transfer; persisted records and WorkManager own recovery/verification. */
object AndroidAppUpdater {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(UpdateInstallState())
    val state: StateFlow<UpdateInstallState> = mutableState.asStateFlow()
    @Volatile private var record: Record? = null
    private var polling: Job? = null
    @Volatile private var cancelRequested = false
    private const val WORK = "gratify-apk-update"
    private const val CHANNEL = "gratify_updates"
    private const val NOTICE = 0x475550

    private data class Record(val update: UpdateData, val id: Long, val stage: UpdateStage,
        val candidateVersion: Long = 0, val message: String? = null, val canRetry: Boolean = true,
        val delta: UpdateDelta? = null)

    @Synchronized fun initialize(appContext: Context) {
        if (::context.isInitialized) return
        context = appContext.applicationContext
        preferences = context.getSharedPreferences("gratify_apk_update", Context.MODE_PRIVATE)
        record = runCatching { preferences.getString("pending", null)?.let { raw ->
            val j = JSONObject(raw)
            Record(UpdateData(j.getString("version"), null, "", j.getString("url"),
                versionCode = j.optLong("expectedCode").takeIf { it > 0 },
                sha256 = j.getString("sha256"), sizeBytes = j.getLong("size"),
                packageName = j.getString("package"), channel = j.getString("channel")),
                j.getLong("id"), UpdateStage.valueOf(j.getString("stage")), j.optLong("candidateCode"),
                j.optString("message").takeIf { it.isNotBlank() }, j.optBoolean("canRetry", true),
                j.optJSONObject("delta")?.let { d -> UpdateDelta(d.getLong("fromCode"), d.getString("fromHash"),
                    d.getLong("fromSize"), d.getString("url"), d.getString("hash"), d.getLong("size"), d.getString("format")) })
        } }.getOrNull()
        record?.let { publish(it) }
    }

    fun enqueueRecovery(completed: Boolean = false) {
        if (record == null) return
        val request = OneTimeWorkRequestBuilder<ApkUpdateWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK + if (completed) "-complete" else "-watch", ExistingWorkPolicy.KEEP, request)
    }

    fun matchesDownload(id: Long) = id > 0 && preferences.getString("pending", null)?.let {
        runCatching { JSONObject(it).getLong("id") == id }.getOrDefault(false)
    } == true

    fun observe() {
        polling?.cancel()
        polling = scope.launch {
            do {
                reconcile()
                if (state.value.stage != UpdateStage.DOWNLOADING) break
                delay(2000)
            } while (isActive)
        }
    }
    fun stopObserving() { polling?.cancel(); polling = null }

    fun start(update: UpdateData) {
        openUpdateStatus()
        scope.launch { mutex.withLock {
            if (record?.stage in setOf(UpdateStage.PREPARING, UpdateStage.DOWNLOADING, UpdateStage.APPLYING, UpdateStage.VERIFYING, UpdateStage.READY,
                    UpdateStage.NEED_PERMISSION, UpdateStage.INSTALLING)) return@withLock
            removeFilesAndDownload()
            cancelRequested = false
            var initial = Record(update, 0, UpdateStage.PREPARING)
            try {
                validateMetadata(update)
                require(update.packageName == context.packageName) {
                    "APK release ini berbeda dari Gratify debug yang terpasang. Keduanya memakai penyimpanan terpisah. Jangan hapus aplikasi lama sebelum data dicadangkan."
                }
                require(update.channel == com.tan.gratify.BuildKonfig.apkUpdateChannel) { "Varian APK berbeda dari jalur pembaruan ini" }
                save(initial)
                val job = currentCoroutineContext()
                val source = File(context.applicationInfo.sourceDir)
                val hasSplit = !context.applicationInfo.splitSourceDirs.isNullOrEmpty()
                val candidates = update.deltas.filter { it.fromVersionCode == installedVersion() && it.fromSizeBytes == source.length() }
                val delta = if (hasSplit || candidates.isEmpty()) null else selectDelta(update, installedVersion(), source.length(),
                    StreamingDelta.hash(source) { job.ensureActive(); checkCancellation() }, false)
                delta?.let { validatePatch(update, it) }
                initial = initial.copy(stage = UpdateStage.DOWNLOADING, delta = delta)
                val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: error("Penyimpanan unduhan tidak tersedia")
                require(directory.exists() || directory.mkdirs()) { "Penyimpanan unduhan tidak tersedia" }
                val needed = (if (delta != null) update.sizeBytes!! + delta.sizeBytes * 2 else update.sizeBytes!! * 2) + 16L * 1024 * 1024
                require(StatFs(directory.absolutePath).availableBytes > needed &&
                    StatFs(context.cacheDir.absolutePath).availableBytes > needed) { "Ruang penyimpanan belum cukup untuk pembaruan. Kosongkan ruang lalu coba lagi." }
                save(initial)
                val request = DownloadManager.Request(Uri.parse(delta?.patchUrl ?: update.apkUrl))
                    .setTitle("Gratify ${update.tagName}")
                    .setDescription(if (delta != null) "Mengunduh pembaruan hemat kuota" else "Mengunduh pembaruan Gratify")
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                        "Gratify-update-${System.currentTimeMillis()}.${if (delta != null) "gdelta" else "apk"}")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setMimeType(if (delta != null) "application/octet-stream" else "application/vnd.android.package-archive")
                val id = manager().enqueue(request)
                try { save(initial.copy(id = id)) } catch (error: Exception) { manager().remove(id); throw error }
                enqueueRecovery()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail(initial, error,
                    retryable = update.packageName == context.packageName && update.channel == com.tan.gratify.BuildKonfig.apkUpdateChannel) }
        }; observe() }
    }

    suspend fun reconcile() = mutex.withLock {
        val pending = record ?: return@withLock
        try {
            if (pending.candidateVersion > 0 && installedVersion() >= pending.candidateVersion) {
                clear(); return@withLock
            }
            if (pending.stage == UpdateStage.ERROR) return@withLock
            if (pending.stage in setOf(UpdateStage.READY, UpdateStage.NEED_PERMISSION, UpdateStage.INSTALLING)) {
                if (!apkFile(pending.id).isFile) {
                    save(pending.copy(stage = UpdateStage.DOWNLOADING))
                } else {
                    val stage = if (pending.stage == UpdateStage.NEED_PERMISSION && !context.packageManager.canRequestPackageInstalls())
                        UpdateStage.NEED_PERMISSION else UpdateStage.READY
                    val ready = pending.copy(stage = stage)
                    save(ready); notifyReady(ready); return@withLock
                }
            }
            require(pending.id > 0) { "Unduhan terhenti sebelum dimulai. Silakan coba lagi." }
            manager().query(DownloadManager.Query().setFilterById(pending.id)).use { cursor ->
                require(cursor.moveToFirst()) { "Unduhan pembaruan sudah tidak tersedia. Silakan coba lagi." }
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val verifying = pending.copy(stage = UpdateStage.VERIFYING)
                        save(verifying)
                        val downloaded = copyDownload(verifying)
                        val file = if (pending.delta != null) {
                            validatePatch(pending.update, pending.delta)
                            val job = currentCoroutineContext()
                            val source = File(context.applicationInfo.sourceDir)
                            require(context.applicationInfo.splitSourceDirs.isNullOrEmpty() && installedVersion() == pending.delta.fromVersionCode &&
                                source.length() == pending.delta.fromSizeBytes &&
                                StreamingDelta.hash(source) { job.ensureActive(); checkCancellation() }.equals(pending.delta.fromSha256, true)) { "APK terpasang berubah. Gunakan APK lengkap." }
                            require(StreamingDelta.hash(downloaded) { job.ensureActive(); checkCancellation() }.equals(pending.delta.sha256, true)) { "Patch berbeda dari rilis resmi" }
                            save(verifying.copy(stage = UpdateStage.APPLYING))
                            val result = apkFile(pending.id)
                            StreamingDelta.apply(source, downloaded, result, pending.update.sizeBytes!!) { job.ensureActive(); checkCancellation() }
                            save(verifying)
                            result
                        } else downloaded
                        val candidate = verify(verifying.update, file)
                        val ready = verifying.copy(stage = UpdateStage.READY, candidateVersion = candidate)
                        save(ready); notifyReady(ready)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        error(when (reason) {
                            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Penyimpanan penuh. Kosongkan ruang lalu coba lagi."
                            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Penyimpanan unduhan tidak tersedia."
                            else -> "Unduhan gagal. Periksa koneksi lalu coba lagi."
                        })
                    }
                    else -> {
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val paused = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_PAUSED
                        mutableState.value = UpdateInstallState(UpdateStage.DOWNLOADING, pending.update, pending.id,
                            downloaded.coerceAtLeast(0), total.takeIf { it > 0 } ?: pending.delta?.sizeBytes ?: pending.update.sizeBytes ?: 0,
                            if (paused) "Unduhan menunggu koneksi atau penyimpanan tersedia" else null, delta = pending.delta != null)
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail(record ?: pending, error, error !is IncompatibleUpdatePackage) }
    }

    fun retry() {
        val pending = state.value
        if (pending.canRetry) pending.update?.let(::start)
    }
    fun cancel() { cancelRequested = true; stopObserving(); scope.launch { mutex.withLock { clear() } } }

    fun install() {
        scope.launch { mutex.withLock {
            val pending = record ?: return@withLock
            if (pending.stage !in setOf(UpdateStage.READY, UpdateStage.NEED_PERMISSION, UpdateStage.INSTALLING)) return@withLock
            try {
                verify(pending.update, apkFile(pending.id))
                if (!context.packageManager.canRequestPackageInstalls()) {
                    save(pending.copy(stage = UpdateStage.NEED_PERMISSION))
                    withContext(Dispatchers.Main) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else {
                    save(pending.copy(stage = UpdateStage.INSTALLING))
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apkFile(pending.id))
                    withContext(Dispatchers.Main) {
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail(pending, error, error !is IncompatibleUpdatePackage) }
        } }
    }

    private fun manager() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private fun apkFile(id: Long) = File(context.cacheDir, "updates/gratify-update-$id.apk")
    private fun patchFile(id: Long) = File(context.cacheDir, "updates/gratify-update-$id.gdelta")
    private fun checkCancellation() { if (cancelRequested) throw CancellationException("Pembaruan dibatalkan") }
    private fun validatePatch(update: UpdateData, delta: UpdateDelta) {
        val uri = Uri.parse(delta.patchUrl)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            delta.patchUrl.substringBeforeLast('/') == update.apkUrl.substringBeforeLast('/') &&
            uri.lastPathSegment.orEmpty().matches(Regex("Gratify-from-[0-9]+-[a-f0-9]{12}\\.gdelta"))) { "Tautan patch bukan rilis resmi Gratify" }
        require(delta.format == "gratify-delta-v1" && delta.sha256.matches(Regex("[a-fA-F0-9]{64}")) &&
            delta.fromSha256.matches(Regex("[a-fA-F0-9]{64}")) && delta.fromSizeBytes in 1..StreamingDelta.MAX_APK_SIZE &&
            update.versionCode != null && delta.fromVersionCode in 1 until update.versionCode!! &&
            delta.sizeBytes in 1..(update.sizeBytes!! * 7 / 10)) { "Metadata patch tidak valid" }
    }
    private fun validateMetadata(update: UpdateData) {
        val uri = Uri.parse(update.apkUrl)
        require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.path.orEmpty().startsWith("/TanDjendra/Gratify/releases/download/") && uri.query == null && uri.fragment == null) { "Tautan pembaruan bukan rilis resmi Gratify" }
        require(update.sha256?.matches(Regex("[a-fA-F0-9]{64}")) == true) { "Verifikasi rilis belum tersedia. Coba lagi setelah rilis diperbaiki." }
        require(update.sizeBytes != null && update.sizeBytes in 1..300L * 1024 * 1024) { "Ukuran pembaruan tidak valid" }
    }
    private suspend fun copyDownload(pending: Record): File {
        val uri = manager().getUriForDownloadedFile(pending.id) ?: error("Berkas unduhan tidak tersedia")
        val file = if (pending.delta != null) patchFile(pending.id) else apkFile(pending.id)
        require(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true) { "Penyimpanan pembaruan tidak tersedia" }
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val bytes = ByteArray(128 * 1024); var size = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    checkCancellation()
                    val count = input.read(bytes); if (count < 0) break
                    size += count
                    require(size <= (pending.delta?.sizeBytes ?: pending.update.sizeBytes!!)) { "Ukuran unduhan berbeda dari rilis resmi" }
                    output.write(bytes, 0, count)
                }
            } } ?: error("Berkas unduhan tidak dapat dibaca")
            require(file.length() == (pending.delta?.sizeBytes ?: pending.update.sizeBytes)) { "Unduhan pembaruan belum lengkap" }
            return file
        } catch (error: Exception) { file.delete(); throw error }
    }
    @Suppress("DEPRECATION") private suspend fun verify(update: UpdateData, file: File): Long {
        validateMetadata(update)
        require(file.length() == update.sizeBytes) { "Berkas pembaruan belum lengkap. Coba unduh lagi." }
        val job = currentCoroutineContext()
        val hash = StreamingDelta.hash(file) { job.ensureActive(); checkCancellation() }
        require(hash.equals(update.sha256, true)) { "Berkas pembaruan berbeda dari rilis resmi. Coba unduh lagi." }
        val flags = (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES) or PackageManager.GET_META_DATA
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Berkas bukan APK Gratify")
        fun identity(info: PackageInfo) = UpdatePackageIdentity(info.packageName, packageVersion(info), info.versionName,
            certificates(info).map { certificate -> MessageDigest.getInstance("SHA-256").digest(certificate)
                .joinToString("") { "%02x".format(it) } }.toSet(),
                info.applicationInfo?.metaData?.getString("com.tan.gratify.UPDATE_CHANNEL"))
        validateUpdateIdentity(identity(installed), identity(candidate), update)
        return packageVersion(candidate)
    }
    @Suppress("DEPRECATION") private fun certificates(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        else info.signatures?.map { it.toByteArray() }.orEmpty()
    @Suppress("DEPRECATION") private fun packageVersion(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION") private fun installedVersion(): Long = packageVersion(context.packageManager.getPackageInfo(context.packageName, 0))

    private fun publish(pending: Record) {
        mutableState.value = UpdateInstallState(pending.stage, pending.update, pending.id,
            if (pending.stage in setOf(UpdateStage.READY, UpdateStage.NEED_PERMISSION, UpdateStage.INSTALLING)) pending.update.sizeBytes ?: 0 else 0,
            pending.delta?.sizeBytes ?: pending.update.sizeBytes ?: 0, pending.message, pending.canRetry,
            delta = pending.delta != null, canDownloadFull = pending.stage == UpdateStage.ERROR && pending.delta != null && pending.canRetry)
    }
    private fun save(pending: Record) {
        val u = pending.update
        val j = JSONObject().put("version", u.tagName).put("url", u.apkUrl).put("sha256", u.sha256.orEmpty())
            .put("size", u.sizeBytes ?: 0).put("package", u.packageName).put("channel", u.channel)
            .put("expectedCode", u.versionCode ?: 0).put("candidateCode", pending.candidateVersion)
            .put("id", pending.id).put("stage", pending.stage.name).put("message", pending.message.orEmpty())
            .put("canRetry", pending.canRetry)
        pending.delta?.let { d -> j.put("delta", JSONObject().put("fromCode", d.fromVersionCode).put("fromHash", d.fromSha256)
            .put("fromSize", d.fromSizeBytes).put("url", d.patchUrl).put("hash", d.sha256).put("size", d.sizeBytes).put("format", d.format)) }
        check(preferences.edit().putString("pending", j.toString()).commit()) { "Status pembaruan tidak dapat disimpan" }
        record = pending; publish(pending)
    }
    private fun fail(pending: Record, error: Exception, retryable: Boolean) {
        val failed = pending.copy(stage = UpdateStage.ERROR, message = (error.message ?: "Pembaruan belum dapat dilanjutkan") +
            if (pending.delta != null && retryable) " Pilih APK lengkap untuk melanjutkan; unduhan tambahan memerlukan pilihan Anda." else "", canRetry = retryable)
        runCatching { save(failed) }.onFailure { record = failed; publish(failed) }
    }
    private fun removeFilesAndDownload() {
        record?.let { pending -> if (pending.id > 0) { runCatching { manager().remove(pending.id) }; apkFile(pending.id).delete(); patchFile(pending.id).delete() } }
    }
    private fun clear() {
        removeFilesAndDownload()
        preferences.edit().remove("pending").remove("notifiedId").commit()
        record = null; mutableState.value = UpdateInstallState()
        context.getSystemService(NotificationManager::class.java).cancel(NOTICE)
    }
    private fun notifyReady(pending: Record) {
        if (pending.stage != UpdateStage.READY || preferences.getLong("notifiedId", 0) == pending.id) return
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Pembaruan Gratify", NotificationManager.IMPORTANCE_DEFAULT))
        if (!notifications.areNotificationsEnabled() || (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("gratify_update", true)
        val action = PendingIntent.getActivity(context, NOTICE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val icon = context.resources.getIdentifier("monochrome", "drawable", context.packageName).takeIf { it != 0 } ?: android.R.drawable.stat_sys_download_done
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(icon)
            .setContentTitle("Pembaruan Gratify siap dipasang").setContentText("Ketuk untuk memasang ${pending.update.tagName}")
            .setContentIntent(action).setAutoCancel(true).build()
        runCatching { notifications.notify(NOTICE, notification); preferences.edit().putLong("notifiedId", pending.id).apply() }
    }
}

class ApkUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AndroidAppUpdater.initialize(applicationContext)
        withContext(Dispatchers.IO) { AndroidAppUpdater.reconcile() }
        return if (AndroidAppUpdater.state.value.stage == UpdateStage.DOWNLOADING) Result.retry() else Result.success()
    }
}

class ApkUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        AndroidAppUpdater.initialize(context)
        if (AndroidAppUpdater.matchesDownload(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)))
            AndroidAppUpdater.enqueueRecovery(completed = true)
    }
}
