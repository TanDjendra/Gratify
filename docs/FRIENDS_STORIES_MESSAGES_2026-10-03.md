# Friends: story musik dan percakapan

> Perbaikan terbaru, diagnosis Supabase aktif, dan pratinjau detik/lirik tersedia pada
> [laporan perbaikan Friends](FRIENDS_PREVIEW_FIX_2026-10-03.md).

Permintaan pengguna: halaman Friends mengikuti gambar referensi inbox, story musik dengan
durasi 8/15/30 detik, serta pesan dua arah. Pilihan yang dikonfirmasi: story bisa dibagikan
ke teman atau publik, berlaku 24 jam; pesan non-teman masuk Permintaan dan harus diterima
sebelum percakapan berlanjut.

## Tampilan dan penggunaan

Nama akun berada di tengah header, dengan menu profil dan tombol pesan baru. Deretan avatar
di bagian atas menampilkan story, catatan dan aktivitas musik yang memang tersedia. Warna,
font, permukaan dan tombol memakai sistem desain Gratify. Ring hijau menunjukkan story aktif;
status online atau pesan tidak dibuat-buat.

- **Utama**: percakapan yang diterima, permintaan yang dikirim sendiri, dan teman yang bisa disapa.
- **Permintaan**: pesan pertama dari pengguna non-teman; penerima dapat menerima atau menolak.
- **Publik**: story publik dari pengguna Gratify. Konten teman tetap menggunakan mutual follow.
- Avatar pada baris chat membuka profil; bagian baris lainnya membuka percakapan.
- Catatan singkat sebelumnya tetap tersedia melalui balon catatan sendiri.
- Tombol perbarui di depan tab memuat ulang story, teman dan inbox. Pilihan tab tetap
  dipertahankan saat aplikasi kembali aktif, dan direset saat berganti akun.

Tombol tambah story membuka editor. Pengguna dapat memilih lagu yang sedang diputar atau
mencari lagu Gratify, memilih 8/15/30 detik, menggeser titik mulai, mendengarkan pratinjau,
menulis catatan maksimal 200 karakter, lalu memilih Teman atau Publik. Durasi yang lebih panjang
dari lagu dinonaktifkan. Metadata tambahan diambil jika hasil pencarian belum memiliki durasi.

Story menyimpan ID lagu, metadata, titik mulai dan durasi, bukan URL stream sementara atau
salinan file audio. Saat dibuka, aplikasi memperoleh stream baru melalui resolver Gratify.
Pemutar potongan terpisah dari antrean utama, berhenti pada akhir bagian yang dipilih, dan
berhenti ketika aplikasi tidak terlihat. Lagu utama dijeda selama pratinjau/story; antrean
dan posisinya dipertahankan. Pemutar utama dapat dilanjutkan saat story ditutup jika masih
lagu/akun yang sama dan aplikasi sedang aktif.

Layar chat menyediakan pesan teks maksimal 2.000 karakter, riwayat yang bisa dimuat bertahap,
penanda belum dibaca, dan tanda Dilihat berdasarkan pesan yang benar-benar tampil. Refresh
berlangsung setiap lima detik selama halaman Friends aktif. Pesan yang gagal mempertahankan
draft; pengiriman ulang memakai ID yang sama agar tidak menghasilkan duplikat. Fitur ini belum
mencakup notifikasi push, pesan suara, lampiran, atau pemanggilan video.

## Penyimpanan dan aturan akses

Migrasi: `supabase/migrations/202610030001_friends_stories_messages.sql`.
Tiga tabel baru: `music_stories`, `direct_conversations`, `direct_messages`.

Semua akses memerlukan sesi Gratify. Story teman hanya dapat dibaca pemilik dan mutual friend;
story publik dapat dilihat pengguna Gratify. Kedaluwarsa memakai waktu server dan berlaku juga
pada pembacaan langsung. Pemilik dapat menghapus story. Baris yang kedaluwarsa tidak ditampilkan;
penghapusan fisik berkala belum dijadwalkan.

Percakapan hanya dapat dibaca kedua pesertanya. Pengguna tidak mendapat izin menulis tabel
pesan secara langsung; RPC mengisi pengirim dari sesi autentikasi, membatasi panjang pesan dan
menegakkan status permintaan. Satu pesan pembuka diperbolehkan selama permintaan belum diterima.
Permintaan yang ditolak tidak dapat dibuka kembali oleh pengirim. Sepasang pengguna mempunyai
satu percakapan dengan identitas tetap. Riwayat memakai cursor pesan dan batas 50 pesan per halaman.
Penghapusan akun menghapus story dan percakapan milik akun melalui foreign-key cascade.

Migrasi tidak mengubah login, playlist, data library atau renderer player/lirik.

## Validasi

Hasil akhir pemeriksaan, status aktivasi Supabase, hash APK dan bukti tampilan tercatat di
`artifacts/friends-social/validation.json`.

Status aktivasi: akun pengelola sudah masuk ke dashboard Supabase Gratify. Migrasi telah
disiapkan di editor dan dicocokkan dengan berkas lokal; penerapan ke proyek aktif menunggu
persetujuan khusus pengguna. Sebelum penerapan, story dan chat antar akun belum tersedia online.

- 97 tes aplikasi/data lulus (66 Compose, 31 data), tanpa kegagalan.
- 46 pemeriksaan SQL lulus (23 untuk masing-masing variasi ID text dan UUID).
- Pemutar Android diuji menggunakan audio lokal: potongan 8, 15 dan 30 detik berakhir pada
  posisi 8.000, 15.000 dan 30.000 ms, termasuk ketika batas akhir tepat di akhir lagu.
- Build Android debug dan lint berhasil. Pengujian kirim/terima antara dua akun nyata
  belum dilakukan; uji akses dan alur pesan memakai akun uji di PostgreSQL lokal.

- Uji SQL memakai PostgreSQL lokal terisolasi, dalam variasi ID profil text dan UUID.
  Laporan: `database-verification.json`; pengulang: `scripts/verify-friends-social.py`.
- Uji UI memakai komponen produksi dengan data contoh lokal, pada lebar 360/390/900 dan skala
  font sampai 1,5. Meliputi story kedaluwarsa, target profil/chat, filter permintaan, batas potongan,
  tombol kirim dan pemulihan error. Tidak mengganti backend produksi dengan data contoh.
- Aktivitas Android debug dapat memeriksa durasi pemutar native melalui audio uji lokal.
  Audio ini hanya dibundel pada debug dan bukan lagu pengguna.
- Tes lokal tidak mengukur latensi penyedia musik, performa baterai HP, atau notifikasi latar.

Clipping Android mengikuti [konfigurasi Media3](https://developer.android.com/reference/androidx/media3/common/MediaItem.ClippingConfiguration.Builder).
