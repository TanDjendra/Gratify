# Penyelesaian perbaikan Gratify — 1 Oktober 2026

## Status terbaru

Perbaikan kode dan migrasi Supabase aktif sudah diterapkan. Login Auth/PostgREST dua akun, transaksi playlist dan batas privasi server lulus. APK bertanda tangan dapat dibuka pada emulator Android 17 dengan halaman memori 16 KB. Event crash terkontrol diterima Sentry dengan pesan exception disamarkan. **Rilis belum 100% selesai:** upload mapping Sentry, CI terbaru, pengujian fungsi aplikasi lebih lanjut dan persetujuan dokumen resmi masih perlu diselesaikan.

## Perubahan pada tahap ini

- Playlist memperoleh identitas tetap (`sync_id` / `client_sync_id`). ID numerik perangkat dan judul tidak digunakan untuk menyamakan atau menghapus playlist lintas perangkat.
- Room naik ke versi 27 dengan migrasi otomatis. Pemulihan menggunakan ID hasil insert dalam transaksi, bukan memilih ID terbesar setelah insert. Restore ulang tidak membuat salinan baru untuk identitas yang sama. Kegagalan tidak meninggalkan playlist setengah terisi.
- Playlist berjudul sama tetap terpisah. Pembersihan lama yang menghapus berdasarkan judul dihentikan. Semua data historis dipertahankan; salinan lama yang hubungannya ambigu perlu ditinjau pengguna, termasuk antrean hapus lama yang hanya menyimpan ID numerik. Aplikasi tidak menyatakan cadangan itu sudah terhapus.
- Publikasi cloud/shared, penyembunyian dan penghapusan berjalan lewat transaksi server. Mutasi REST lama pada empat tabel playlist dibatasi. Tombstone mencegah playlist yang sudah dihapus diterbitkan ulang oleh perangkat lama dan diterapkan saat pemulihan lokal.
- Snapshot lagu cloud/shared dibaca lengkap dan berurutan sebagai satu nilai JSON. Pengujian menggunakan 1.025 lagu, termasuk pemeriksaan bahwa snapshot tidak melewati pembatasan privasi.
- Pilihan privasi lama dengan pemilik akun yang diketahui disimpan per akun sebelum cache dibersihkan. Perubahan yang belum terkirim tetap tersedia untuk retry. Pilihan akun A tidak diterapkan ke B. Data tanpa pilihan privasi eksplisit memakai nilai awal privat. Pilihan lama tanpa identitas pemilik tidak diatribusikan ke akun lain secara otomatis.
- Profil menolak hasil akun lama setelah pergantian akun; permintaan profil sebelumnya dibatalkan saat membuka profil berikutnya. Status publik playlist mengikuti hasil transaksi server.
- Seluruh inisialisasi PipePipe di aplikasi, termasuk `NewPipeUtils` Android/JVM, memakai adapter TLS yang aman. Tes memakai koneksi HTTPS nyata dan memastikan sertifikat tidak dipercaya serta hostname salah ditolak.
- Izin penyimpanan lama yang tidak digunakan dihapus, guard Android di bawah minSdk dihapus, URI memakai KTX, resource qualifier yang tidak diperlukan dirapikan, dan widget diberi ukuran minimum untuk Android lama serta konfigurasi sel untuk API 31+.
- Penandatanganan membaca rahasia dari pengaturan lokal pemilik, memakai password melalui environment proses, memeriksa tanda tangan dan alignment, serta tidak mengubah keystore.

## Temuan tambahan saat pengujian nyata

- Pembuatan playlist tidak lagi memerlukan empat lagu. Playlist kosong dan satu lagu didukung; simpan playlist beserta lagu menggunakan transaksi, menangkap kegagalan dan mengaktifkan kembali tombol retry. Penyimpanan salinan playlist bersama memakai jalur transaksi yang sama. Tes kegagalan insert lagu membuktikan seluruh perubahan di-rollback, data lama utuh dan retry berhasil.
- Build CI pertama menemukan dependency task AboutLibraries yang belum dinyatakan. `copyNonXmlValueResourcesForCommonMain` kini menunggu `exportLibraryDefinitions`. Kedua task dijalankan bersama dan berhasil; CI perlu diulang dari perubahan ini.
- Storage/avatar diuji melalui API aktif: unggah pemilik ditolak dan tidak ada policy pada `storage.objects`. Perbaikan owner-only telah disiapkan dalam migrasi 008 dan belum diterapkan karena pemberian akses melalui dashboard menunggu konfirmasi. Akun uji Storage dibersihkan.

## Hasil verifikasi

| Pemeriksaan | Hasil |
|---|---|
| Tes JVM lintas enam modul | **41 lulus; 0 gagal** |
| Tes komponen UI di dalam jumlah tersebut | 9 lulus |
| PostgreSQL lokal terpisah | **13 pemeriksaan lulus** |
| Build debug dan release full | Berhasil |
| Android lint debug | **0 error; 116 warning** |
| APK rilis | Paket `com.tan.gratify`; bukan debuggable; tanda tangan diverifikasi |
| Pustaka native 64-bit | 30 ELF diperiksa; seluruh segmen LOAD memenuhi alignment 16 KB |
| Resource Terms/Privacy | Sama dengan draf terbaru, termasuk identitas pengelola dan dukungan |
| Supabase aktif / Auth / PostgREST | Lulus; Storage/avatar dan email belum diuji |
| Instalasi pembaruan dan startup Android 16 KB | Lulus pada emulator; UAT fungsi dan FPS masih berlangsung |

APK: `androidApp/build/outputs/apk/release/Gratify-release-signed.apk`.

SHA-256 APK: `4e462ed01bbfccf5a1bef20b53f88389ed55d5aa875894880418486c00a1d926`.

Jumlah warning mencakup 98 pemberitahuan versi dependensi/plugin. Versi yang dibatasi untuk kompatibilitas tidak diperbarui secara massal. Dua warning trust manager berasal dari kode dalam dependensi PipePipe; jalur aplikasi yang memanggil initializer tersebut sudah diganti dan tes penolakan TLS lulus. Laporan R8 untuk build rilis juga mengonfirmasi penghapusan ketiga overload `NewPipe.init`, `trustEveryone`, dan dua kelas anonimnya yang tidak dipakai. Warning ChromeOS diperiksa melalui paket APK yang benar-benar mencantumkan x86_64. Perubahan versi dependensi tetap perlu penilaian kompatibilitas dan pengujian tersendiri.

| Jenis lint | Jumlah |
|---|---|
| `AndroidGradlePluginVersion` | 4 |
| `ChromeOsAbiSupport` | 1 |
| `GradleDependency` | 30 |
| `IconDuplicates` | 6 |
| `IconLocation` | 2 |
| `IconXmlAndPng` | 2 |
| `NewerVersionAvailable` | 64 |
| `OldTargetApi` | 1 |
| `TrustAllX509TrustManager` | 2 |
| `UnusedResources` | 4 |

Alignment ELF dan tanda tangan adalah pemeriksaan paket, bukan bukti bahwa semua fungsi sudah berjalan pada perangkat nyata. [Rujukan Android untuk alignment 16 KB](https://developer.android.com/guide/practices/page-sizes).

## Status layanan dan pekerjaan berikutnya

1. **Supabase aktif diterapkan:** proyek `bnabldxsqpvkyqpjcsdv`, skema UUID. Paket `supabase-launch-fixes-uuid.sql` dijalankan sebagai satu transaksi setelah simulasi rollback. Verifikasi: 14 RPC, 0 kebijakan terbuka yang diperiksa, 13 akun asli dipertahankan. Tes SQL sintetis di-rollback; tes login menggunakan dua akun sementara sungguhan.
2. **Auth/PostgREST lulus:** akun lain dan anonim tidak bisa membaca playlist/library privat, publikasi mengikuti pilihan pemilik, tombstone menolak pemunculan ulang, hapus akun uji B mempertahankan akun A. Storage/avatar, email pemulihan dan sinkronisasi dua perangkat masih memerlukan pengujian.
3. **Startup Android diperbaiki:** binding `CoroutineScope` untuk `StreamRepository` memakai qualifier `SERVICE_SCOPE`. Masalah sebelumnya benar-benar menyebabkan crash saat startup pada APK lama. APK setelah perbaikan bisa dibuka dan dipasang sebagai pembaruan dengan sertifikat asli. Pengujian login juga menemukan batas TLD enam karakter; validasi bersama diperbaiki; dua tes regresi tambahan dan build penuh lulus.
4. **Sentry Android aktif:** DSN dikonfigurasi privat; event `GRATIFY-ANDROID-1` dari crash terkontrol emulator diterima, pengguna terhitung 0 dan pesan exception disamarkan. Endpoint tetap memproses metadata perangkat/koneksi. Mapping lokal belum terunggah; token upload sudah ada di GitHub, tetapi validitasnya perlu dibuktikan lewat workflow.
5. **CI:** workflow `Launch verification` disiapkan untuk tes bersama, lint, full APK dan upload mapping. Secret publik Supabase dan DSN diselaraskan secara terenkripsi. Hasil CI terbaru belum boleh dinyatakan lulus sampai run selesai.
6. **Dokumen:** identitas pengelola dan dukungan sudah dikonfirmasi. URL Terms/Privacy resmi belum tersedia. Contoh URL dan asumsi backup harian yang belum terbukti dihapus dari daftar tinjauan. Kedua dokumen tetap draf sampai pemilik meninjau dan menyetujui fakta/isinya.

Petunjuk rinci ada di `docs/RELEASE_SETUP.md`; dokumen legal ada di `docs/legal/REVIEW.md`.

## Bukti

- `artifacts/launch-audit/completion-final-build.log`
- `artifacts/launch-audit/fixes-verification.json`
- `artifacts/launch-audit/database-fixes-verification.json`
- `artifacts/launch-audit/database-deployment.json`
- `artifacts/launch-audit/live-api-verification.json`
- `artifacts/launch-audit/sentry-event-proof.jpg`
- `artifacts/launch-audit/signing-verification.json`
- `artifacts/launch-audit/release-package-verification.json`
- `artifacts/launch-audit/packaged-legal-verification.json`

Laporan audit awal tetap dipertahankan sebagai rekaman temuan awal, bukan status penyelesaian terbaru.
