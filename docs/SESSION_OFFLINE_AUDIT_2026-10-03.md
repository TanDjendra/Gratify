# Audit sesi login dan akses offline — 3 Oktober 2026

Keluhan: pengguna yang sudah login terkadang diminta login lagi setelah membuka Gratify. Versi APK, keadaan jaringan, dan log perangkat teman pengguna belum diketahui. Audit menemukan jalur yang dapat menjelaskan keluhan tersebut dan mereproduksi kondisi SDK yang memicunya; belum memastikan kejadian spesifik di perangkat teman.

## Temuan utama — P1: kegagalan refresh sementara dianggap logout

[SplashScreen.kt](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/login/SplashScreen.kt:79) menunggu inisialisasi SDK lalu 300 ms. Sesudah itu, aplikasi membaca `currentSessionOrNull()`. Jika null, flag login disetel false dan pengguna diarahkan ke halaman login.

Supabase Auth 3.0.3, versi yang dipakai Gratify, mengeluarkan status `RefreshFailure` ketika refresh gagal karena jaringan atau respons server 5xx. Dalam status itu, `currentSessionOrNull()` mengembalikan null meskipun sesi masih tersimpan. `awaitInitialization()` juga selesai pada status tersebut. SDK mencoba lagi setelah jeda dan dapat memulihkan sesi tanpa login ulang. Perilaku ini diverifikasi pada SDK yang benar-benar terpasang dan dibandingkan dengan [AuthImpl 3.0.3](https://github.com/supabase-community/supabase-kt/blob/3.0.3/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/AuthImpl.kt) dan [Auth 3.0.3](https://github.com/supabase-community/supabase-kt/blob/3.0.3/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/Auth.kt).

Alur pemicu:

1. Login sebelumnya berhasil dan sesi tersimpan.
2. Aplikasi dibuka kembali ketika access token sudah kedaluwarsa.
3. Refresh tidak dapat diselesaikan karena offline, jaringan bermasalah, atau gangguan server.
4. Splash melihat session null, mengubah flag login menjadi false, dan membuka halaman login.

Mengapa terasa acak: jika access token belum kedaluwarsa, pemulihan sesi dapat berhasil saat offline. Jika sudah kedaluwarsa, SDK perlu refresh lewat jaringan. Lama token dan pengaturan server produksi belum diperiksa. Menurut [dokumentasi Supabase](https://supabase.com/docs/guides/auth/sessions), access token memang berumur pendek dan diperbarui dengan refresh token; habisnya access token sendiri tidak selalu berarti pengguna harus login ulang.

Flag false juga diamati [MainScreen.kt](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/MainScreen.kt:161) untuk memicu navigasi keluar. Flag dan status SDK merupakan dua sumber keputusan yang dapat berbeda sementara.

## Temuan tambahan

- **P2 — Kesalahan aktivasi data lokal juga menjadi logout.** [SplashScreen.kt](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/login/SplashScreen.kt:87) menangkap seluruh exception dari `performLoginSync(waitForCloud = false)` sebagai `accountReady = false`, lalu menurunkan flag login. Pengaktifan akun lokal melibatkan transaksi database dan penyimpanan preferensi; kegagalan bagian ini tidak membuktikan autentikasi dicabut. Jalur ini dikonfirmasi dari kode; belum mereproduksi kegagalan database pada perangkat.
- **P2 — Flag login Gratify masih dipakai pengaturan akun YouTube lama.** [SettingsViewModel.kt](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SettingsViewModel.kt:1455) mengubah flag yang sama menjadi false ketika `setUsedAccount(null)` dipanggil, sekaligus membersihkan preferensi profil. Sesi Supabase tidak dicabut pada jalur tersebut. Tombolnya ada pada pengaturan akun YouTube. Ini pemicu terpisah jika pengguna menekan tombol itu; belum ada bukti teman pengguna melakukannya.

## Dampak offline dan penyimpanan

Sesi Android sudah disimpan pada SharedPreferences `supabase_session` melalui [SessionManagerFactory.android.kt](D:/Tan/Gratify/composeApp/src/androidMain/kotlin/com/tan/gratify/di/SessionManagerFactory.android.kt:9) dan `SettingsSessionManager`. Tidak ditemukan konfigurasi yang sengaja mematikan penyimpanan sesi.

[Media3ServiceModule.kt](D:/Tan/Gratify/core/media/media3/src/main/java/com/tan/media3/di/Media3ServiceModule.kt:233) memeriksa download cache sebelum meminta stream jaringan. Cabang cache itu tidak meminta autentikasi Supabase. Namun halaman library berada di dalam aplikasi setelah splash, sehingga navigasi ke login dapat menghalangi pengguna mencapai lagu unduhan. Audit tidak menunjukkan bahwa kegagalan refresh ini menghapus file unduhan.

## Pengujian

[SessionPersistenceAuditTest.kt](D:/Tan/Gratify/composeApp/src/jvmTest/kotlin/com/tan/gratify/SessionPersistenceAuditTest.kt) memakai SDK asli 3.0.3, sesi sintetis, penyimpanan sesi dalam memori, dan server HTTP lokal. Tidak menggunakan akun/token pengguna atau server Supabase produksi. Tidak menguji persistensi SharedPreferences setelah Android process death; konfigurasi penyimpanan itu diperiksa dari kode.

| Kondisi | Hasil SDK setelah inisialisasi dan jeda splash 300 ms |
|---|---|
| Token belum kedaluwarsa, jaringan tidak tersedia | Authenticated; sesi dapat dibaca |
| Token kedaluwarsa, jaringan tidak tersedia | RefreshFailure; current session null; sesi tersimpan tetap ada |
| Token kedaluwarsa, server 503 | RefreshFailure; current session null; sesi tersimpan tetap ada |
| Refresh token ditolak dengan 400 | NotAuthenticated; sesi tersimpan benar-benar dihapus |
| Gangguan 503 pulih menjadi 200 | SDK kembali Authenticated tanpa pengguna login ulang |

**5 pemeriksaan lulus**, 0 failure, 0 error, 0 skipped. Bukti tersimpan pada `artifacts/session-audit-2026-10-03/verification.log` dan `test-results.xml`. Pengujian ini membuktikan perilaku SDK pemicu; navigasi Compose ditelusuri dari kode, belum diuji langsung pada Android.

## Rekomendasi untuk persetujuan

1. Bedakan refresh sementara gagal, sesi benar-benar dicabut, dan logout yang dilakukan pengguna. Jangan mengubah flag login menjadi false hanya karena refresh sementara gagal.
2. Izinkan akses library/download offline milik akun terakhir yang telah berhasil diaktifkan secara lokal. Pertahankan isolasi antar akun; fitur cloud tetap perlu autentikasi online yang valid. Logout atau pergantian akun eksplisit harus tetap ditangani sesuai kepemilikan data.
3. Tampilkan kesalahan aktivasi database sebagai masalah pemulihan lokal yang dapat dicoba lagi, bukan logout.
4. Pisahkan flag akun Gratify dari koneksi YouTube agar melepas koneksi YouTube tidak mengeluarkan pengguna dari Gratify.

Belum mengubah perilaku produksi, mengubah pengaturan Supabase, membuat APK baru, atau memasang APK dalam audit ini. Perbaikan menunggu persetujuan pengguna sesuai permintaan sebelumnya agar rekomendasi diajukan dahulu.

Pembaruan setelah audit: pengguna menyetujui seluruh perbaikan. Implementasi, 55 pemeriksaan yang lulus, dan batas verifikasi dijelaskan pada [laporan perbaikan](D:/Tan/Gratify/docs/SESSION_OFFLINE_FIX_2026-10-03.md). Pernyataan menunggu persetujuan di atas menggambarkan saat audit selesai, sebelum tindak lanjut ini.
