# Privacy Policy Gratify

Status: Kebijakan resmi, disetujui pengelola pada 4 Oktober 2026.
Berlaku mulai: 4 Oktober 2026
Kontak dukungan: supportgratify@gmail.com

## Pengelola aplikasi
Gratify dikelola oleh Tan Heradhe Rat Djendra (TanDjendra), developer tunggal yang beroperasi di Indonesia. Pertanyaan dan permintaan terkait data dapat dikirim ke supportgratify@gmail.com.

## Data untuk akun dan sinkronisasi
Pendaftaran/login menggunakan email dan layanan autentikasi Supabase. Kata sandi diproses oleh penyedia autentikasi. Aplikasi menyimpan sesi login agar Anda dapat kembali masuk. Profil dapat berisi nama tampilan dan avatar. Fitur cloud menyimpan playlist, lagu disukai, artis diikuti, album disimpan, riwayat dengar, antrean dan pengaturan yang disinkronkan agar dapat dipulihkan pada perangkat Anda.

## Profil dan aktivitas sosial
Nama, avatar, catatan, relasi follow, playlist publik, artis pilihan dan informasi lagu yang sedang diputar digunakan untuk fitur sosial. Pilihan privasi mengatur visibilitas relasi, playlist dan aktivitas/artis pada profil. Status publik playlist dan pilihan untuk memajangnya di profil merupakan pengaturan terpisah. Izin melihat sampul unggahan mengikuti status playlist.

Story musik menyimpan pilihan lagu, posisi awal, durasi cuplikan, caption, dan pilihan audiens teman atau publik. Story tidak ditampilkan setelah masa berlakunya 24 jam; berakhirnya tampilan bukan janji penghapusan langsung dari database atau cadangan. Pesan menyimpan isi pesan, peserta percakapan dan status baca. Akses percakapan dibatasi untuk pesertanya melalui backend; fitur ini belum menyediakan enkripsi end-to-end. Informasi yang telah dilihat atau disalin orang lain tidak dapat ditarik kembali dari perangkat mereka.

## Data lokal dan izin perangkat
Database lokal menyimpan perpustakaan, antrean, riwayat, statistik dengar dan arsip privat akun yang pernah digunakan pada perangkat ini. Izin gambar/kamera digunakan ketika Anda memilih avatar atau memindai QR; akses penyimpanan digunakan untuk download, backup, restore dan ekspor yang Anda minta. Anda dapat mengubah izin melalui pengaturan perangkat.

## Integrasi opsional
Koneksi resmi YouTube bersifat opsional dan menggunakan persetujuan Google OAuth dengan izin baca saja (youtube.readonly). Gratify menggunakan identitas akun Google yang telah diverifikasi untuk mengaitkan koneksi dengan akun Gratify, serta membaca metadata playlist milik Anda dan video yang disukai agar koleksi dapat ditampilkan atau diimpor atas permintaan Anda. Koneksi ini tidak memberi Gratify izin untuk mengubah atau menghapus koleksi YouTube Anda. Login Gratify dan izin akses YouTube merupakan dua hal terpisah. Foto akun Google tidak otomatis menggantikan avatar yang Anda unggah di Gratify.

Token akses dan token pembaruan untuk koneksi resmi YouTube disimpan terenkripsi di server Supabase dan dihubungkan dengan akun Gratify Anda. Sesi autentikasi tetap dapat disimpan pada perangkat untuk menjalankan login. Tombol putuskan koneksi menghapus koneksi dan token server Gratify; Anda juga dapat mencabut izin aplikasi di pengaturan koneksi pihak ketiga Akun Google, https://myaccount.google.com/connections. Memutuskan koneksi di Gratify tidak otomatis mencabut izin di Google atau menghapus playlist yang sudah Anda impor ke Gratify. Data Google/YouTube digunakan untuk fitur koneksi koleksi tersebut, bukan untuk penjualan data, iklan personal atau pelatihan model AI. Anda dapat meminta penghapusan melalui aplikasi atau kontak dukungan.

Penggunaan informasi yang diterima dari Google API akan mengikuti Google API Services User Data Policy, termasuk persyaratan Limited Use: https://developers.google.com/terms/api-services-user-data-policy.

Integrasi Spotify/Discord atau metode koneksi YouTube lain yang Anda aktifkan dapat menyimpan cookie/token pada perangkat dan mengirim permintaan ke layanan tersebut. Terjemahan AI yang diaktifkan mengirim lirik dan bahasa tujuan kepada penyedia yang Anda pilih. Data tersebut mengikuti pengaturan dan kebijakan penyedia terkait. Jangan memasukkan informasi pribadi ke lirik atau endpoint AI yang tidak Anda percayai.

## Notifikasi
Jika notifikasi diaktifkan, Gratify menggunakan Firebase Cloud Messaging untuk mengirim pemberitahuan pesan dan story. Token pengiriman perangkat dan pengaturan notifikasi disimpan untuk mengarahkan pemberitahuan ke akun/perangkat yang sesuai. Server push mengirim identitas peristiwa; aplikasi mengambil rincian yang berhak diakses oleh akun tersebut. Tampilan notifikasi dapat terlihat di layar kunci sesuai pengaturan perangkat. Anda dapat menonaktifkan notifikasi melalui pengaturan aplikasi atau perangkat.

## Laporan gangguan
Build dengan crash reporting yang dikonfigurasi menggunakan Sentry untuk laporan error teknis. Konfigurasi membatasi data pribadi, isi permintaan, screenshot, hierarki tampilan dan breadcrumb. Pesan exception disamarkan. Build tanpa konfigurasi Sentry tidak mengirim laporan tersebut melalui SDK Sentry. Infrastruktur layanan tetap dapat memproses informasi teknis koneksi, seperti alamat IP, untuk menjalankan layanannya.

## Penyimpanan, backup dan penghapusan
Data aktif cloud dipertahankan untuk menjalankan akun dan sinkronisasi selama diperlukan untuk fitur yang Anda gunakan. Penghapusan akun menghapus data aktif yang terkait dengan akun dari aplikasi/backend setelah operasi berhasil. Jika operasi gagal, penghapusan belum selesai dan perlu dicoba kembali. Backup dan log penyedia infrastruktur mengikuti pengaturan serta kebijakan retensi penyedia. Gratify tidak menjanjikan penghapusan seketika dari seluruh cadangan atau cache penyedia. Backup yang Anda ekspor berada di lokasi pilihan Anda dan dapat berisi data akun/sesi: simpan secara privat. Penghapusan akun tidak menghapus otomatis salinan yang sudah Anda ekspor atau disimpan pengguna lain.

Proyek Supabase Gratify menggunakan region Tokyo. Endpoint penerimaan laporan gangguan Sentry Android menggunakan layanan US; ini tidak membuktikan seluruh lokasi pemrosesan penyedia. Google/Firebase dan layanan pihak ketiga dapat memproses data di luar Indonesia sesuai infrastruktur dan kebijakan mereka.

## Permintaan terkait data
Untuk pertanyaan, perbaikan data, salinan data, penarikan pilihan penggunaan data opsional atau penghapusan, hubungi supportgratify@gmail.com. Identitas pemohon perlu diperiksa melalui akun/email yang terkait sebelum data diubah atau diberikan. Jangan kirim kata sandi atau token melalui email.

## Perubahan kebijakan
Perubahan kebijakan akan dipublikasikan pada halaman ini beserta tanggal berlakunya. Perubahan material yang memengaruhi penggunaan data akan diberitahukan melalui kanal aplikasi yang tersedia.
