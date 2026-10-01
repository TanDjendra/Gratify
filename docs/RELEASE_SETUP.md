# Melengkapi akses dan rilis Gratify

Semua nilai rahasia di bawah disimpan di `D:\Tan\Gratify\local.properties` atau environment lokal. File itu sudah diabaikan Git. Jangan memasukkan akses administrator database ke BuildKonfig, APK, repository, atau chat.

## 1. Supabase

1. Buka dashboard proyek Gratify yang benar.
2. Pilih **Connect → Connection string → Session pooler**. Mode ini cocok untuk laptop dengan koneksi IPv4. Direct connection juga dapat dipakai jika jaringan mendukung IPv6.
3. Salin URL PostgreSQL, lalu ganti `[YOUR-PASSWORD]` dengan kata sandi database. Ini berbeda dari kata sandi akun Supabase.
4. Tambahkan `SUPABASE_DB_URL=<URL PostgreSQL lengkap>` ke `local.properties`, tanpa tanda kutip. Karakter khusus kata sandi di dalam URL harus di-encode sebagai bagian URL. Jangan memakai backslash Windows pada URL.
5. Beri tahu Codex bahwa nilainya sudah tersimpan. Pelaksana memeriksa kontrak tabel terlebih dahulu dan berhenti tanpa perubahan bila kontrak berbeda.

Pelaksana memverifikasi sertifikat dan nama host server (`verify-full`). Bila proyek memakai CA Supabase khusus, unduh sertifikat dari **Database Settings**, lalu isi `SUPABASE_SSL_ROOT_CERT=D:/lokasi/sertifikat-supabase.crt`. Jangan menonaktifkan verifikasi sertifikat. [Panduan SSL resmi Supabase](https://supabase.com/docs/guides/platform/ssl-enforcement).

[Panduan resmi koneksi Supabase](https://supabase.com/docs/guides/database/connecting-to-postgres).

## Status penerapan — 1 Oktober 2026

Perbaikan sudah diterapkan pada proyek Gratify `bnabldxsqpvkyqpjcsdv` melalui SQL Editor admin. Skema aktif memakai UUID; paket yang sesuai adalah `artifacts/launch-audit/supabase-launch-fixes-uuid.sql`. Paket TEXT `supabase-launch-fixes.sql` tidak sesuai untuk proyek aktif ini. Jangan menjalankan paket TEXT atau potongan migrasi secara terpisah pada skema UUID.

Laporan `database-deployment.json` mencatat 14 RPC, tidak ada kebijakan terbuka yang diperiksa, dan 13 akun asli dipertahankan. Pengujian SQL/RLS dan login Auth/PostgREST dua akun sementara lulus. Avatar/Storage, pengiriman email pemulihan dan sinkronisasi dua perangkat belum dibuktikan. Klien lama perlu diperbarui karena jalur mutasi lama sudah dibatasi.

## 2. Sentry untuk versi full

1. Buat atau buka proyek Android/Kotlin Gratify di Sentry.
2. Buka **Project Settings → Client Keys (DSN)** dan salin DSN proyek.
3. Tambahkan `SENTRY_DSN=<DSN proyek>` ke `local.properties`.
4. Secret `SENTRY_AUTH_TOKEN` sudah ada di GitHub; keberadaan secret belum membuktikan token masih valid untuk organisasi `calestaan`. Workflow Launch verification memeriksa upload mapping pada build penuh. DSN Android sudah dikonfigurasi secara privat.

[Panduan Android Sentry](https://docs.sentry.io/platforms/android/). Event uji dan redaksi data pada Sentry aktif tetap perlu diperiksa. Versi FOSS tidak memakai Sentry.

## 3. Penandatanganan APK

Keystore yang diberikan pemilik: `D:\Tan\script\Gratify\gratify.jks`.

Tambahkan berikut ke `local.properties`, menggunakan nilai keystore asli:

```properties
KEYSTORE_PATH=D:/Tan/script/Gratify/gratify.jks
KEYSTORE_PASSWORD=<kata sandi keystore>
KEY_ALIAS=<alias kunci yang sudah ada>
KEY_PASSWORD=<kata sandi kunci>
```

Jangan membuat keystore pengganti untuk pembaruan aplikasi yang sudah dibagikan. Jika kata sandi kunci sama dengan kata sandi keystore, kedua nilai tetap diisi. Pelaksana `artifacts/launch-audit/sign-release.py` membaca rahasia tanpa menaruh kata sandi pada argumen proses, menandatangani APK hasil build, lalu memeriksa tanda tangan dan alignment. Hasilnya tersedia hanya setelah `--apply` berhasil. Nilai yang sudah disimpan di `D:\Tan\script\Gratify\local.properties` juga dibaca khusus untuk tiga pengaturan penandatanganan. Keystore asli tidak diubah. Pembaruan di atas aplikasi terpasang perlu diuji dengan kunci yang sama.

## 4. Dokumen resmi

Draf Terms/Privacy sudah mencantumkan Tan Heradhe Rat Djendra (TanDjendra), Indonesia, dan `supportgratify@gmail.com`. Tinjau `docs/legal/REVIEW.md`, lengkapi fakta operasional yang masih kosong, lalu tetapkan URL resmi. Lokasi komponen signup tidak menggantikan URL kebijakan resmi. Label draf hanya boleh diubah setelah isi disetujui dan resource aplikasi diperbarui sesuai dokumen.

## 5. Uji perangkat dan server

Gunakan dua akun uji dan dua perangkat/emulator yang diizinkan:

- Masuk, ganti akun saat offline, keluar, dan pastikan playlist/favorit akun lain tidak terlihat.
- Buat dua playlist berjudul sama; backup keduanya; pulihkan pada perangkat lain yang sudah punya playlist dengan ID lokal sama.
- Ganti nama, simpan ulang, bagikan, sembunyikan, dan hapus satu playlist; playlist lainnya tetap ada. Perangkat lama tidak boleh memunculkan kembali playlist yang dihapus.
- Ubah ketiga pilihan privasi. Akun kedua dan akses anonim tidak boleh membaca data yang disembunyikan.
- Uji jaringan putus saat simpan dan saat migrasi privasi; pilihan lama harus tetap dapat dicoba ulang.
- Uji pemulihan password, hapus akun beserta avatar, dan pastikan akun kedua tetap utuh.
- Uji playback, perpindahan halaman, album/playlist panjang, unduhan gagal/retry, cache parsial, timer, speed/pitch, notifikasi dan widget.
- Pasang APK bertanda tangan di atas versi yang sudah dibagikan; periksa data tetap ada, startup, crash, ANR dan frame saat scrolling.

Laporan lokal tidak menggantikan pengujian Auth, Storage, email, perangkat dan CI nyata.
