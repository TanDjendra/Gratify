# Perbaikan issue Sentry Gratify — 8 Oktober 2026

## Temuan dan perubahan

| Issue | Temuan | Penanganan | Status |
|---|---|---|---|
| [GRATIFY-ANDROID-1](https://calestaan.sentry.io/issues/7765930944/) | Crash sengaja dipicu lewat ADB di emulator untuk memverifikasi penerimaan Sentry. | Ditandai **Resolved** di Sentry; tidak ada filter untuk menyembunyikan laporan crash. Bukti: `artifacts/sentry-fix/adb-test-resolved.jpg`. | Selesai sebagai event pengujian. |
| [GRATIFY-ANDROID-2](https://calestaan.sentry.io/issues/7767077466/) | ANR emulator Android 17. Main thread berada di `ActivityThread.handleBindApplication → Process.isIsolated → getuid`; belum ada frame aplikasi yang menjelaskan penyebabnya. | Menghapus dua pembacaan DataStore sinkron saat inisialisasi media: batas cache dan pengaturan video. Batas cache dibaca di IO dan diterapkan menggunakan LRU yang dapat diperbarui. Cache tidak dihapus sebelum pengaturan tersimpan tersedia. Pengaturan video diamati di IO; audio-only digunakan sampai nilainya tersedia. | Mitigasi startup tersedia; penyebab ANR historis belum terbukti. Issue tetap terbuka. |
| [GRATIFY-ANDROID-3](https://calestaan.sentry.io/issues/7767328623/) | `ForegroundServiceStartNotAllowedException` di iQOO I2218 / Android 16, ketika aplikasi berada di background. Stack trace versi lama menunjuk `onNotificationPosted → startForeground`. | Kode saat ini sudah menyerahkan notifikasi kepada Media3. Ditambahkan listener untuk pause lewat adapter saat promosi foreground ditolak, sehingga antrean tetap tersedia untuk retry oleh pengguna. Fallback `startForegroundService` setelah penolakan background start dihapus; Activity tetap mencoba binding dan hanya mencatat binding berhasil jika Android mengembalikan `true`. | Perbaikan kode tersedia; membutuhkan APK terbaru dan verifikasi perangkat. Issue tetap terbuka. |

## Verifikasi

- Sembilan tes regresi baru lulus: alur start/bind, kegagalan binding, propagasi kesalahan izin, callback penolakan foreground, dan penerapan batas cache termasuk LRU, touch, unlimited, dan zero budget.
- Tiga tes transport player yang sudah ada ikut lulus. Tes contoh aritmetika tidak dihitung sebagai bukti perbaikan.
- Build debug dan kompilasi modul Sentry berhasil. Bukti: `artifacts/sentry-fix-build-final.log`.
- Kompilasi ulang source media final, tes unit, build rilis full dengan Sentry, serta lint vital rilis berhasil. Bukti: `artifacts/sentry-fix/final-module-verification.log` dan `artifacts/sentry-fix-release-build.log`.
- **17 dari 17** pemeriksaan playback native lulus di emulator Android 17 dengan page size 16 KB: metadata, Home, Next/Previous, pause/play, task removal, foreground service, notifikasi, crossfade, audio focus, explicit stop-on-exit, dan service recreation.
- Tiga cold start berhasil: **6.161 ms**, **2.702 ms**, dan **2.880 ms**. Waktu launch pertama masih tinggi; ini bukan bukti bahwa penyebab ANR historis sudah diperbaiki. Tidak ada fatal crash atau ANR Gratify tercatat dalam log pengujian akhir.
- Hasil akhir: `artifacts/sentry-fix/verification.json`; bukti playback: `artifacts/sentry-fix/media/verification.json`; bukti startup: `artifacts/sentry-fix/startup.json`.
- Pengujian stop-on-exit memerlukan fixture sebagai root task; membuka fixture di atas MainActivity tidak menjalankan skenario task removal yang sama. Hasil percobaan awal dipertahankan dalam folder bukti.
- Pemeriksaan transport menunggu state dan metadata yang diharapkan agar tidak bergantung pada delay tetap saat komputer sibuk.

## Batas verifikasi dan rilis

Perubahan belum dipasang ke perangkat iQOO dan belum didistribusikan. Tidak ada perangkat fisik terhubung selama sesi ini. Hasil emulator tidak membuktikan seluruh perilaku firmware OEM atau penyebab pasti ANR lama.

Event emulator lama memiliki UUID ProGuard yang berbeda dari mapping rilis terbaru. Mapping terbaru tidak dapat dipakai sebagai pengganti mapping lama. Mapping build integrasi rilis sesi ini memiliki UUID `b6930ded-337c-3569-9617-cc65c52d90f1`. Tidak tersedia `SENTRY_AUTH_TOKEN` pada sesi lokal ini; log build mengonfirmasi upload dilewati. Upload mapping produksi tetap melalui workflow rilis yang mewajibkan token dan mengunggah mapping untuk build yang sama. APK rilis lokal belum ditandatangani atau dipublikasikan.

Referensi API: [MediaSessionService.Listener](https://developer.android.com/reference/androidx/media3/session/MediaSessionService.Listener), [Media3 service lifecycle](https://developer.android.com/reference/androidx/media3/session/MediaSessionService), [ANR Android](https://developer.android.com/topic/performance/issues/anr).
