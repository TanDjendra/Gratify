# Audit dan perbaikan notifikasi Gratify — 3 Oktober 2026

Perbaikan aplikasi selesai, kompilasi Android lulus, dan infrastruktur push sudah dipasang pada Supabase Gratify (`bnabldxsqpvkyqpjcsdv`). Firebase menggunakan proyek `gratify-auth` (1072882393306) milik supportgratify@gmail.com, sesuai persetujuan pengguna. APK sebelumnya belum memuat perubahan ini; tidak ada APK baru yang dibuat dalam pekerjaan notifikasi ini.

## Perbaikan aplikasi

- Kotak notifikasi mengamati database secara langsung, sehingga penambahan dan penghapusan langsung tercermin di layar. Identitas baris stabil mencegah aksi geser berpindah ke kartu lain.
- Penerimaan notifikasi dicatat secara atomik. Kartu yang sudah dihapus atau dibersihkan tidak muncul kembali saat sinkronisasi. Catatan ikut dipisahkan per akun, termasuk saat pindah akun dan kembali ke akun lama.
- Kegagalan layanan pengumuman tidak menghentikan sinkronisasi pengikut. Tombol mengikuti kembali menampilkan status server, menolak ketukan ganda, dan dapat dicoba lagi setelah gagal.
- Permintaan izin Android menggunakan callback resmi dan tidak mengulang permintaan pada setiap pembukaan. Pengaturan notifikasi Android dapat dibuka dari kotak notifikasi.
- Notifikasi rilis artis memakai batas waktu, menyimpan acuan lama ketika jaringan gagal, dan mendeteksi rilis pertama setelah daftar sebelumnya kosong.
- Notifikasi unduhan membuka Pustaka; status akhir tidak bertabrakan dengan notifikasi pemutar. Unduhan yang dijeda tidak otomatis dilanjutkan oleh callback.
- Notifikasi pemutar memakai satu penyedia Media3 agar tidak muncul dua notifikasi pemutaran.
- Ketukan push pesan atau story membuka tujuan Friends yang sesuai. Identitas akun diperiksa lagi sebelum membuka konten; story yang berakhir atau tidak bisa diakses menampilkan pemberitahuan.

## Pengiriman Friends

Pesan yang diterima menambah satu peristiwa untuk penerima, termasuk permintaan pesan. Story publik memberi tahu pengikut, sedangkan story untuk teman hanya memberi tahu pengguna yang saling mengikuti. Pemilik story tidak menerima notifikasinya sendiri. Ini tidak menyiarkan story publik ke seluruh pengguna Gratify.

Perangkat mendaftar ulang saat akun siap dan terhubung. Token baru atau pergantian akun memperbarui kepemilikan perangkat. Pengiriman menggunakan antrean dengan batas percobaan, jeda percobaan ulang, dan klaim yang mencegah dua pengirim memproses pekerjaan yang sama bersamaan. Token yang dinyatakan tidak terdaftar oleh Firebase dinonaktifkan.

Push hanya membawa jenis peristiwa, ID peristiwa, dan ID akun penerima. Isi pesan tidak dimasukkan ke payload push. Android mengambil informasi yang diizinkan server sebelum menyimpan dan menampilkan notifikasi. Tampilan pada layar kunci memakai visibilitas privat.

## Konfigurasi produksi

- Firebase: `gratify-auth`, paket `com.tan.gratify` dan `com.tan.gratify.dev`, FCM HTTP v1 aktif. Konfigurasi debug memakai App ID yang ditampilkan Firebase untuk paket debug. Login Google aplikasi tetap melalui Supabase OAuth.
- Akun pengirim: `gratify-push@gratify-auth.iam.gserviceaccount.com`, peran `roles/firebasecloudmessaging.admin`. Kunci JSON disimpan sebagai `FCM_SERVICE_ACCOUNT` di Edge Secrets Supabase, bukan di aplikasi atau repositori.
- Edge Function: `social-push`, dengan `FCM_PROJECT_ID=gratify-auth`. Pemicu harus menyertakan secret internal; permintaan POST tanpa secret ditolak. Verifikasi JWT bawaan digantikan pemeriksaan secret internal pada fungsi ini.
- `GRATIFY_PUSH_DISPATCH_SECRET` disimpan di Edge Secrets dan nilai yang sama di Vault dengan nama `gratify_social_push_dispatch_secret`.
- Migrasi: `supabase/migrations/202610030003_social_push_notifications.sql`, SHA-256 `7B26D1E3664CF804D877C035942D5B767EA3DD30EB6D686D4669F978ED48BDD0`.
- Tabel perangkat dan antrean tidak dapat dibaca oleh aplikasi. Aplikasi hanya dapat membaca peristiwa yang masih diizinkan untuk akun penerima melalui RLS. RPC pengirim hanya dapat dijalankan server.
- Trigger membangunkan pengirim saat peristiwa dibuat; tugas `gratify-social-push-retry` mencoba antrean setiap menit. Kegagalan membangunkan pengirim tidak menggagalkan pengiriman pesan atau penerbitan story; tugas berkala dapat mencoba lagi.

Peran FCM dan mekanisme HTTP v1 mengikuti [dokumentasi IAM Google](https://docs.cloud.google.com/iam/docs/roles-permissions/firebasecloudmessaging) dan [dokumentasi pengiriman Firebase](https://firebase.google.com/docs/cloud-messaging/send/v1-api).

## Verifikasi

67 pemeriksaan lokal lulus: 22 pengujian JVM aplikasi/database lokal, 34 pemeriksaan PostgreSQL (17 dengan ID teks dan 17 dengan UUID), serta 11 pengujian pengirim push. Pengujian pengirim memakai kunci sintetis dan HTTP tiruan; tidak mengirim pesan ke pengguna.

Kompilasi Kotlin Android dan pemrosesan konfigurasi Firebase debug serta release lulus. Migrasi Room 27 ke 28 diuji dengan database lama yang berisi notifikasi, bukan hanya database baru.

Pemeriksaan produksi memastikan RLS aktif pada tiga tabel baru, token/antrean tidak dapat dibaca pengguna atau anonim, dan RPC pengirim dibatasi untuk server. Fungsi readiness mengembalikan `configured: true`; POST tanpa secret ditolak dengan 401. Permintaan terautentikasi dari Supabase berhasil memperoleh akses Firebase dan menjalankan pengirim dengan respons 200 (`sent: 0`, `retry: 0`, `invalid: 0`). Pemicu terpasang dan percobaan ulang aktif. Saat aktivasi, belum ada perangkat terdaftar atau pekerjaan pengiriman, sehingga tidak ada notifikasi yang dikirim ke pengguna dalam verifikasi ini.

## Batas pengujian

Penerimaan notifikasi pada HP saat aplikasi aktif, di latar belakang, dan tertutup belum diuji. Pengguna perlu APK yang berisi perubahan ini dan mengizinkan notifikasi Android. Pembatasan sistem, mode hemat baterai, dan force-stop dapat memengaruhi penerimaan push. Membuat konfigurasi server tidak membuktikan notifikasi sudah sampai di perangkat.

Bukti pengujian berada di `artifacts/notification-audit-2026-10-03/`.
