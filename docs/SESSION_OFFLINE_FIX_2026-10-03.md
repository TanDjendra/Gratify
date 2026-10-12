# Perbaikan sesi login dan akses offline — 3 Oktober 2026

Perbaikan telah diterapkan setelah pengguna menyetujui seluruh rekomendasi dalam [audit](D:/Tan/Gratify/docs/SESSION_OFFLINE_AUDIT_2026-10-03.md). Gangguan refresh sementara sekarang dibedakan dari sesi yang benar-benar berakhir. Aplikasi dapat membuka library lokal ketika refresh gagal, tanpa menjadikan token kedaluwarsa sebagai otorisasi cloud.

## Perilaku yang berubah

- Saat refresh gagal karena jaringan atau gangguan server, akun tetap dapat membuka library offline jika identitas sesi tersimpan, akun terakhir pada preferensi, dan pemilik database lokal sama. Jalur offline tidak mengaktifkan library akun lain dan tidak membuat permintaan cloud.
- Jika build lama telanjur mengubah flag login menjadi false, flag dapat dipulihkan setelah pemeriksaan identitas dan kepemilikan berhasil. Sesi yang sudah dihapus atau akun yang telah logout tidak mendapatkan akses offline dari flag ini.
- Ketika SDK berhasil memperbarui sesi, sinkronisasi cloud dilanjutkan untuk pemilik yang sama. Mekanisme refresh, retry, dan lifecycle tetap memakai SDK Supabase 3.0.3; tidak ada impor token kedaluwarsa ke status authenticated atau loop refresh jaringan kedua.
- Kegagalan aktivasi database menampilkan layar pemulihan dengan tombol **Coba lagi**, tanpa menurunkan flag login. Main screen ditutup oleh layar pemulihan selama pergantian pemilik atau kegagalan aktivasi agar data akun sebelumnya tidak ditampilkan.
- Sesi yang benar-benar hilang/dicabut menonaktifkan akses library dan mengarsipkan akun lokal. Logout eksplisit tetap dapat menyelesaikan penghapusan sesi lokal jika server logout tidak dapat dijangkau. Jika logout jarak jauh gagal, penghentian sesi pada perangkat/server lain tidak diklaim berhasil.
- Pemilihan, pelepasan, atau kegagalan login akun YouTube lama tidak lagi mengubah flag login Gratify, membersihkan preferensi profil Gratify, atau menimpa nama profil. Metadata YouTube memakai kunci tersendiri. Tombol logout Gratify dan logout YouTube memakai tindakan yang berbeda. Status koneksi YouTube resmi di library berasal dari repositori YouTube, bukan flag login Gratify. Login Spotify juga tidak lagi menyalakan flag login Gratify.
- Navigasi Main tidak lagi mengikuti flag umum sebagai satu-satunya penentu logout. Transisi login baru menunggu aktivasi akun dan tidak langsung diarahkan keluar ketika status coordinator belum menyusul.

## Implementasi

[AccountSessionCoordinator.kt](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/sync/AccountSessionCoordinator.kt) menjadi pengelola status akses lokal: loading, siap online/offline, keluar, atau perlu pemulihan. Splash dan Main menggunakan status ini. Pemrosesan perubahan status dilakukan berurutan sehingga penulisan preferensi tidak membatalkan transaksi aktivasi akun yang sedang berjalan.

[UserDataSyncManager.kt](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/sync/UserDataSyncManager.kt) memeriksa pemilik database melalui repositori, mengaktifkan akses offline tanpa mengganti akun, memulai kembali sinkronisasi setelah refresh pulih, dan menutup akses ketika sesi dicabut. Tidak ada migrasi Supabase atau perubahan konfigurasi sesi server dalam perbaikan ini.

[SettingsViewModel.kt](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SettingsViewModel.kt) memisahkan koneksi layanan musik dari akun Gratify. [AccountRecoveryScreen.kt](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/component/AccountRecoveryScreen.kt) menyediakan pemulihan dengan teks Indonesia dan English; locale lainnya menggunakan teks default.

## Verifikasi

**55 pemeriksaan JVM lulus**, 0 failure, 0 error, 0 skipped. Kompilasi Android `compileDebugKotlin` juga berhasil.

| Kelompok | Jumlah |
|---|---:|
| Pemulihan sesi, offline, logout, perpindahan akun, dan pemisahan YouTube | 16 |
| Perilaku SDK pada token kedaluwarsa dan gangguan server | 5 |
| Login Google | 6 |
| Kembali ke profil setelah login | 4 |
| Regresi profil dan privasi | 17 |
| Isolasi, arsip, dan rollback library pada SQLite/Room asli | 7 |

Pengujian baru mencakup token kedaluwarsa saat server tidak tersedia, pemulihan koneksi tanpa login ulang, identitas tersimpan tidak cocok, pemilik database berbeda, instalasi tanpa sesi, kegagalan database dan retry, refresh token dicabut, logout saat refresh gagal, logout dengan server 503, pergantian akun, pemulihan flag lama, transisi login baru, pelepasan/pergantian akun YouTube, serta login YouTube gagal yang mengembalikan cookie sebelumnya.

Tes autentikasi memakai SDK asli, sesi sintetis, dan server HTTP lokal. Tes isolasi database memakai Room/SQLite asli. Tidak memakai token akun pengguna atau mengubah Supabase produksi. Pengujian belum dijalankan pada HP teman pengguna; versi APK dan kondisi kejadian awal masih belum diketahui.

Bukti: `artifacts/session-fix-2026-10-03/verification.log`, `validation.json`, dan subfolder `test-results/`.

Belum mengemas APK baru, memasang aplikasi, atau menjalankannya di perangkat untuk perbaikan sesi ini. APK dari build lirik sebelumnya tidak berubah, SHA-256 `CB06DB7E7ABE0D1A33516CBDB025893C8EDC778B97DD861249698816A506B59B`; perbaikan sesi ini baru masuk aplikasi setelah build APK berikutnya.
