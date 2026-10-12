# Implementasi sampul, penamaan, dan Blend

12 Oktober 2026. Tiga tambahan permintaan pengguna sudah diterapkan pada sumber
aplikasi dan server Supabase. APK baru belum dibangun atau dipasang pada ponsel.
Ini bukan penilaian kelayakan rilis keseluruhan aplikasi.

## Perilaku yang berubah

### Sampul otomatis

Jika tidak ada sampul upload, empat lagu teratas menjadi kolase 2 × 2. Playlist
dengan satu sampai tiga lagu memakai gambar lagu pertama. Playlist kosong memakai
placeholder. Urutan mengikuti urutan lagu tersimpan; sampul upload tetap
diprioritaskan dan menghapusnya mengembalikan sampul otomatis.

Komponen bersama digunakan oleh thumbnail detail, kartu Pustaka, folder, profil,
dan hasil pencarian. Kartu workspace menerima empat lagu pertama dari server.
Sampul playlist publik/profil diambil dalam satu permintaan terbatas, bukan satu
permintaan per kartu. Gambar memakai kunci cache URL stabil, tanpa transisi silang,
dan memiliki fallback ketika gambar gagal dimuat. Pembacaan artwork publik tetap
memeriksa visibilitas playlist; playlist privat tidak membocorkan artwork ke
pengguna lain melalui endpoint ini.

### Nama awal

Saran nama memilih nomor positif terkecil yang belum dipakai dalam pola terkait.
Contoh: jika No.1 dan No.3 ada, saran berikutnya No.2. Membuka menu atau membatalkan
tidak menulis penghitung. Pola pribadi dan kolaboratif diperiksa masing-masing;
folder dan Blend tidak menghabiskan nomor. Nama lama dengan spasi seperti
“Playlist-ku No. 1” dihitung sebagai nomor 1. Nama yang sudah ada tidak diubah.

Saran menggabungkan nama lokal dan nama akun di server. Saat membuat workspace
kolaboratif dengan nama otomatis, server memeriksa kembali benturan dalam transaksi
dan menjaga pengulangan permintaan tetap menghasilkan identitas yang sama.
Pembuatan pribadi saat offline masih bergantung pada koleksi lokal; ini belum
menjamin nama unik lintas perangkat ketika keduanya membuat playlist saat offline.

### Blend

Memilih Blend membuka pengenalan dan persetujuan. Menekan Undang setelah setuju
menyiapkan draft undangan. Membuat link atau membuka pratinjau belum mengaktifkan
Blend. Blend baru tampil di Pustaka setelah sedikitnya dua anggota menyetujui dan
bergabung. Draft tidak ikut dihitung dalam isi folder. Jika anggota tinggal satu,
Blend kembali menjadi kondisi menunggu teman dan tidak tampil sebagai campuran
aktif.

Draft masih menggunakan baris workspace dan keanggotaan pemilik di server untuk
menjaga identitas undangan; tidak dibuat tabel draft terpisah. Draft kedaluwarsa
dapat tetap tersimpan secara privat. Baris Blend lama tidak dihapus atau diubah
namanya oleh migrasi ini.

Lagu disusun otomatis dari favorit dan aktivitas mendengarkan yang disetujui
anggota. Tambah, hapus, dan susun lagu manual tetap ditolak oleh UI dan server.
Pekerjaan server berjalan setiap jam untuk memeriksa campuran yang sudah waktunya
diperbarui setelah satu hari. Pembacaan Pustaka juga memeriksa hasil yang perlu
disegarkan; campuran yang belum punya data dapat mencoba kembali ketika selera
tersedia. Fungsi pekerja tidak dapat dijalankan langsung oleh pengguna aplikasi.

Perilaku ini mengacu pada [dokumentasi Blend Spotify](https://support.spotify.com/id-id/article/social-recommendations-in-playlists/):
undang teman, campuran dari aktivitas anggota, dan pembaruan harian. Algoritma
pemilihan lagu Gratify merupakan pendekatan sendiri, bukan algoritma internal
Spotify.

## Verifikasi

- 71 tes aplikasi unik lulus, termasuk komponen sampul, prioritas upload,
  persetujuan Blend, penamaan, dan regresi lembar/modal serta editor profil.
- 106 pemeriksaan database lokal terisolasi lulus: privasi artwork, nama,
  undangan, keanggotaan, folder, dan pekerja penyegaran Blend.
- 28 pemeriksaan server nyata lulus dalam transaksi yang dibatalkan seluruhnya.
  Akun, pesan dan playlist sintetis tidak dipertahankan; pemeriksaan akhir
  menemukan nol akun uji tersisa.
- Jadwal server aktif dan izin pekerja sudah diperiksa. Eksekusi cron terjadwal
  belum diamati; fungsi penyegar diuji pada database terisolasi.
- Tes UI dilakukan melalui render JVM dengan URL gambar sintetis. Belum dilakukan
  pemeriksaan foto nyata, gesture, atau perilaku Android pada ponsel untuk revisi
  ini. APK belum dibangun.

Ringkasan terstruktur: `artifacts/playlist-refinements-20261012/verification.json`.
Log aplikasi: `application-tests-final.log`, `artwork-tests.log`, dan
`naming-tests-final.log` dalam direktori yang sama. Uji akhir penamaan mengulang
17 tes dari himpunan 71; tidak dihitung dua kali. Pemeriksaan database akhir:
`database-tests-final.log` dan `database-verification.json`.

Migrasi yang sudah diterapkan:

- `supabase/migrations/20261012150447_playlist_artwork_blend_lifecycle.sql`
- `supabase/migrations/20261012152634_playlist_default_name_spacing.sql`

## Pekerjaan terpisah yang masih terbuka

Catatan berikut merupakan status saat revisi tiga poin selesai. Penyatuan data
dan rute kemudian diterapkan pada 13 Oktober 2026; lihat
[laporan berikutnya](UNIFIED_PLAYLIST_COLLABORATION_IMPLEMENTATION_2026-10-13.md).

Penyatuan penuh data dan rute playlist pribadi dengan kolaboratif sebagaimana
audit konsep sebelumnya belum diselesaikan dalam revisi tiga poin ini. Playlist
pribadi dan workspace kolaboratif masih memakai penyimpanan yang berbeda; laporan
ini tidak menyatakan bahwa tombol undang pada semua playlist pribadi sudah
mengubahnya menjadi playlist yang sama dengan akses kolaborator.

Pemeriksaan advisor server juga masih melaporkan temuan lama di luar perubahan
ini, antara lain [view security definer](https://supabase.com/docs/guides/database/database-linter?lint=0010_security_definer_view)
dan [search path fungsi lama](https://supabase.com/docs/guides/database/database-linter?lint=0011_function_search_path_mutable).
Objek lama tersebut tidak diubah oleh revisi ini; hasil tes di atas tidak berarti
seluruh server sudah bebas temuan keamanan.
