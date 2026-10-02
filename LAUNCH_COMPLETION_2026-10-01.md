# Penyelesaian perbaikan Gratify — diperbarui 2 Oktober 2026

## Status terbaru

Perbaikan kode dan migrasi Supabase aktif sudah diterapkan. Auth/PostgREST, transaksi playlist dan batas privasi server lulus. Avatar dan retry profil offline telah diverifikasi. Satu email pemulihan dikonfirmasi masuk inbox; callback dan perubahan password diuji terpisah pada akun sementara. **APK rilis terbaru lulus pemulihan cloud pada dua perangkat:** dua judul sama tetap terpisah, 1.025 lagu lengkap, pergantian A/B terisolasi, restore ulang tidak menggandakan data, dan retry setelah offline berhasil. Penghapusan akun dari UI beserta avatar juga lulus setelah retry; kedua akun uji dibersihkan dan 13 akun asli tetap utuh. **Rilis belum 100% selesai:** UAT menyeluruh pada perangkat fisik/desktop dan persetujuan dokumen resmi masih diperlukan.

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
- Build CI pertama menemukan dependency task AboutLibraries yang belum dinyatakan. `copyNonXmlValueResourcesForCommonMain` kini menunggu `exportLibraryDefinitions`. Kedua task dijalankan bersama dan berhasil. CI commit `e8112fe2` lulus seluruh tahap, termasuk upload mapping; CI commit pencarian terbaru `90bb5a1f` juga lulus seluruh tahap.
- Storage/avatar semula gagal karena policy kosong. Migrasi 008 kini diterapkan; enam pengujian API lulus, termasuk batas ukuran/MIME dan penolakan mutasi oleh akun lain. Unggah dari pemilih foto APK rilis menghasilkan JPEG yang diverifikasi pada server. Akun uji Storage dibersihkan. URL publik yang pernah diakses dapat tetap berada dalam cache CDN setelah penghapusan; pemeriksaan origin dan metadata dilakukan terpisah.
- Edit profil menyimpan nama lokal dan menutup form setelah backend berhasil. Pengujian offline membuktikan form dan nilai edit tetap tersedia; tombol retry dapat dipakai dan penyimpanan online berhasil. Update Auth metadata dan tabel profil tetap merupakan operasi terpisah, sehingga retry diperlukan jika salah satunya gagal.
- Pengiriman satu email pemulihan ke alias kotak dukungan berhasil dan penerima mengonfirmasi inbox. Akun alias sementara dibersihkan. Callback recovery dan perubahan password diuji terpisah pada akun sementara tanpa mengirim email tambahan; password lama ditolak dan password baru diterima untuk pemilik yang benar.
- Timeout koneksi native dipastikan melalui debugger akun uji. Pembacaan cloud kini mencoba kembali maksimal tiga kali, mendukung pembatalan dan tidak mengulang mutasi. Kegagalan library tidak mencegah pemulihan playlist. Login mengaktifkan pemilik lokal sebelum menuju beranda; pemulihan berjalan di belakang layar. Tombol cadangkan/pulihkan menampilkan proses, kegagalan dan hasil retry. Transaksi lokal menjaga perubahan like/antrean yang lebih baru dan menolak hasil akun lama. Sembilan tes regresi tambahan lulus.
- Pengujian rilis menemukan playlist telah tersimpan lengkap tetapi tidak terlihat karena `owner_email` kosong. Identitas akun kini disiapkan sebelum background restore; pemulihan ulang memperbaiki email kosong hanya untuk identitas cloud yang sama dalam database pemilik aktif. ID, isi dan nama playlist dipertahankan. Pengujian pembaruan membuktikan playlist lama kembali terlihat tanpa duplikasi; akun kedua juga berhasil memulihkan tiga playlist yang sama.

- Kolom pencarian kini dapat difokuskan dan diketik. Pada APK pencarian sebelumnya (`ae11f391`, source `90bb5a1f`), keyboard terbuka, pencarian "Thriller" menghasilkan lagu/album, filter dan detail album sembilan lagu bekerja, playback berubah ke `PLAYING(3)` dan pause bekerja. Pengujian ini tetap terkait APK tersebut; tidak dinyatakan sebagai pengujian playback baru pada APK cloud terakhir.

## Hasil verifikasi

| Pemeriksaan | Hasil |
|---|---|
| Tes JVM lintas enam modul | **50 lulus; 0 gagal** |
| Tes composeApp di dalam jumlah tersebut | 11 lulus |
| PostgreSQL lokal terpisah | **13 pemeriksaan lulus** |
| Build debug dan release full | Berhasil |
| Android lint debug | **0 error; 116 warning** |
| APK rilis | Paket `com.tan.gratify`; bukan debuggable; tanda tangan diverifikasi |
| Pustaka native 64-bit | 30 ELF diperiksa; seluruh segmen LOAD memenuhi alignment 16 KB |
| Resource Terms/Privacy | Sama dengan draf terbaru, termasuk identitas pengelola dan dukungan |
| Supabase aktif / Auth / PostgREST | Auth/PostgREST dan enam tes Storage lulus; avatar APK rilis berhasil; satu email pemulihan dikonfirmasi masuk inbox |
| Instalasi pembaruan dan startup Android 16 KB | APK terbaru dipasang sebagai pembaruan dan sesi tersimpan; pemulihan dua perangkat, isolasi akun, 1.025 lagu dan retry offline lulus. Search/album/playback serta avatar/profil terkait APK sebelumnya. UAT/FPS perangkat fisik belum selesai |

APK: `androidApp/build/outputs/apk/release/Gratify-release-signed.apk`.

SHA-256 APK terbaru: `5f04ebe76538c8537bc1e2db8b75d23377ecd33ea746813ea61c5b4d527e06c8` (source `f1664925`). Bukti avatar/profil terkait APK `352a59451d5b261a47deb5d25c61b092c9518763c3756697e738b354de5e0c32` / source `c7e61166`. Form recovery/password diuji pada APK `f9167f31` / source `1297a805`; password lama ditolak dan password baru diterima untuk akun uji. Tautan email dukungan yang diterima tidak dikonsumsi untuk pengujian native tersebut.

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

30 ELF lulus alignment, dan APK terbaru dijalankan pada emulator 16 KB. Ini merupakan smoke test terbatas, bukan UAT seluruh fitur. Emulator menggunakan audio nonaktif; keluaran suara belum diperiksa. Android System UI sempat mengalami ANR saat boot sebelumnya; tidak ada crash Gratify pada pemeriksaan akhir. [Rujukan Android untuk alignment 16 KB](https://developer.android.com/guide/practices/page-sizes).

## Status layanan dan pekerjaan berikutnya

1. **Supabase aktif diterapkan:** proyek `bnabldxsqpvkyqpjcsdv`, skema UUID. Paket `supabase-launch-fixes-uuid.sql` diterapkan sebagai satu transaksi setelah simulasi rollback. Verifikasi: 14 RPC, 0 kebijakan terbuka yang diperiksa. Tes SQL sintetis di-rollback. Akun uji API/Storage, alias recovery dan kedua akun UAT perangkat sudah dibersihkan; jumlah akhir 13 akun asli, seluruh identitas asli dipertahankan.
2. **Auth/PostgREST dan UAT cloud lulus:** akun lain/anonim tidak dapat membaca data privat; publikasi mengikuti pilihan pemilik dan tombstone menolak pemunculan ulang. Pemulihan cloud, pergantian akun dan restore ulang diuji pada dua emulator. Hapus akun A dari APK mempertahankan B dan semua akun asli; avatar A dihapus melalui Storage API. Percobaan pertama tidak selesai dan retry UI berhasil. Recovery/password diuji terpisah dari email yang dikonfirmasi masuk inbox. Publikasi/penghapusan playlist lintas perangkat dan seluruh fitur lainnya masih memerlukan UAT menyeluruh.
3. **Startup Android diperbaiki:** binding `CoroutineScope` untuk `StreamRepository` memakai qualifier `SERVICE_SCOPE`. Masalah sebelumnya benar-benar menyebabkan crash saat startup pada APK lama. APK setelah perbaikan bisa dibuka dan dipasang sebagai pembaruan dengan sertifikat asli. Pengujian login juga menemukan batas TLD enam karakter; validasi bersama diperbaiki; dua tes regresi tambahan dan build penuh lulus.
4. **Sentry Android aktif:** DSN dikonfigurasi privat; event `GRATIFY-ANDROID-1` dari crash terkontrol diterima, pengguna 0 dan pesan exception disamarkan. Endpoint tetap memproses metadata perangkat/koneksi. Event crash lama memakai mapping berbeda yang belum terunggah. Mapping APK terbaru `8d7fc4eb-2026-325f-8375-c8363c97e07b` cocok dengan unggahan nyata dalam log CI `36944868214`; SHA APK juga dicocokkan. Runtime desktop belum diverifikasi.
5. **CI:** [CI source APK terakhir `f1664925`](https://github.com/TanDjendra/Gratify/actions/runs/36944868214) **lulus seluruh tahap**, termasuk tes/lint, full build dan upload mapping. CI avatar/profil, pencarian dan perubahan cloud sebelumnya tetap menjadi bukti historis.
6. **Dokumen:** identitas pengelola dan dukungan sudah dikonfirmasi. Pratinjau draf sudah diterbitkan di https://tandjendra.github.io/Gratify/terms/ dan https://tandjendra.github.io/Gratify/privacy/; masing-masing HTTP 200, HTTPS, dan berlabel DRAF. Contoh URL dan asumsi backup harian yang belum terbukti dihapus dari daftar tinjauan. Kedua dokumen tetap draf sampai pemilik meninjau dan menyetujui fakta/isinya.

[CI source avatar/profil c7e61166](https://github.com/TanDjendra/Gratify/actions/runs/36933973732) juga lulus seluruh tahap.

Petunjuk rinci ada di `docs/RELEASE_SETUP.md`; dokumen legal ada di `docs/legal/REVIEW.md`.

## Bukti

- `artifacts/launch-audit/search-final-release-build.log`
- `artifacts/launch-audit/cloud-owner-recovery-final-build.log`
- `artifacts/launch-audit/two-device-fixtures.json`
- `artifacts/launch-audit/cloud-restore-diagnosis.json`
- `artifacts/launch-audit/native-account-deletion.json`
- `artifacts/launch-audit/device-account-cleanup.json`
- `artifacts/launch-audit/recovery-email-verification.json`
- `artifacts/launch-audit/recovery-deeplink-verification.json`
- `artifacts/launch-audit/device-runtime-verification.json`
- `artifacts/launch-audit/android-album-release-proof.png`
- `artifacts/launch-audit/ci-verification.json`
- `artifacts/launch-audit/ci-mapping-verification.json`
- `artifacts/launch-audit/test-account-cleanup.json`
- `artifacts/launch-audit/fixes-verification.json`
- `artifacts/launch-audit/database-fixes-verification.json`
- `artifacts/launch-audit/database-deployment.json`
- `artifacts/launch-audit/live-api-verification.json`
- `artifacts/launch-audit/sentry-event-proof.jpg`
- `artifacts/launch-audit/signing-verification.json`
- `artifacts/launch-audit/release-package-verification.json`
- `artifacts/launch-audit/packaged-legal-verification.json`

Laporan audit awal tetap dipertahankan sebagai rekaman temuan awal, bukan status penyelesaian terbaru.
