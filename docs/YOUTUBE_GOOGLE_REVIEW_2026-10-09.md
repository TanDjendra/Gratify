# Persiapan verifikasi izin YouTube — 9 Oktober 2026

## Status aktual dari Google Cloud

Pemeriksaan dilakukan setelah pemilik masuk ke akun developer `supportgratify@gmail.com`. Proyek `gratify-auth` (1072882393306) berhasil diakses. Akun Chrome yang sebelumnya aktif tidak mempunyai akses; tidak ada perubahan IAM atau permintaan akses yang dikirim.

- Branding: terverifikasi dan sudah ditampilkan kepada pengguna.
- Nama aplikasi: Gratify; support dan kontak developer: supportgratify@gmail.com.
- Homepage: https://gratify.web.id; privacy: https://gratify.web.id/privacy/. Keduanya dibuka melalui browser dan dapat diakses publik. Homepage memuat tautan privacy; privacy menjelaskan izin baca YouTube, enkripsi token, pemutusan koneksi, dan Limited Use.
- Terms resmi dan logo belum diisi. Tidak ada perubahan branding dalam sesi ini; halaman Terms yang ada masih draf.
- Scope sensitif: `youtube.readonly`, belum diverifikasi. Non-sensitive: `userinfo.email`, `userinfo.profile`, `openid`. Tidak ada scope restricted.
- Clients: satu Web application, bernama `Gratify Login`. Video harus memperlihatkan client yang dipakai alur aplikasi; tidak perlu mengarang alur native client lain yang tidak terdaftar.
- Verification Center menyatakan data access belum terverifikasi. Halaman Prepare for verification menampilkan error bahwa scope justification dan demo video belum tersedia. Ini bukan status menunggu keputusan reviewer.

## Pekerjaan yang dilakukan

Draf justifikasi baru sepanjang 911 karakter diisi di Data Access. Teks tersedia di `artifacts/google-verification-2026-10-09/scope-justification.txt`. Tombol Save tetap nonaktif karena video kosong, sehingga draf **belum tersimpan ke konfigurasi proyek** dan **pengajuan belum dikirim**. Tab dipertahankan untuk melanjutkan setelah video tersedia; salinan lokal menghindari kehilangan draf jika tab ditutup.

Justifikasi baru menggantikan bahan 4 Oktober yang menyebut impor. Source `YouTubePlaylistScreen.kt` saat ini menampilkan koleksi dan memungkinkan pemutaran, tanpa tombol impor metadata. Pengajuan sekarang menjelaskan pembacaan dan tampilan metadata; tidak menjanjikan fitur impor yang belum tersedia.

Panduan rekaman dan narasi Inggris disiapkan di `artifacts/google-verification-2026-10-09/demo-guide.md`. Belum ada video OAuth nyata di direktori kerja dan tidak ada perangkat Android tersambung melalui ADB. Screenshot consent dari pengguna tidak menunjukkan seluruh fitur setelah izin, sehingga tidak menggantikan demo.

## Pemeriksaan klaim justifikasi

- `supabase/functions/youtube-library/service.ts`: scope baca saja, `playlists` dengan `mine=true`, `playlistItems`, `videos` dengan `myRating=like`; pembacaan metadata memakai GET. Identitas Google dan audience diperiksa sebelum menyimpan koneksi.
- `service.ts`: token disegel dengan AES-GCM dan owner sebagai additional data.
- `supabase/functions/youtube-library/index.ts`: disconnect memanggil RPC `disconnect_youtube_oauth`.
- `supabase/migrations/202610020002_youtube_oauth_connections.sql`: RPC menghapus percobaan/koneksi; foreign key koneksi ke auth.users memakai ON DELETE CASCADE. Migrasi tercatat diterapkan 4 Oktober; tidak ada mutasi database dalam sesi ini.
- Privacy publik membedakan penghapusan token Gratify dan pencabutan grant Google. Disconnect tidak otomatis mencabut grant Google. Tidak ada klaim pencabutan otomatis pada draf.

## Yang diperlukan untuk melanjutkan

Rekaman aplikasi nyata: login Google jika digunakan, masuk ke koneksi YouTube, consent berbahasa Inggris (nama Gratify, client ID pada address bar, izin baca), berhasil kembali ke Gratify, menampilkan playlist akun uji dan video disukai, lalu memperlihatkan pemutusan koneksi dan privacy. Rahasia login tidak direkam. Gunakan akun/data uji milik pengelola.

Unggah video yang sudah ditinjau ke YouTube Studio sebagai Unlisted, isi URL di Data Access, simpan, periksa ringkasan Verification Center, kemudian kirim pengajuan. Persetujuan Google baru dapat dinyatakan selesai ketika status data access disetujui. Izin YouTube tetap opsional; tidak ada scope tambahan, rotasi secret, pengubahan callback, build APK, atau deploy dalam pekerjaan ini.

## Bukti dan rujukan

- `artifacts/google-verification-2026-10-09/demo-required.jpg`: formulir proyek Gratify, field video kosong, Save nonaktif.
- https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification

Status ini supersedes bagian persiapan dan justifikasi di laporan 4 Oktober, tanpa mengubah catatan historis aktivasi domain.
