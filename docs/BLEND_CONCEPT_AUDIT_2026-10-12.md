# Audit konsep Blend Gratify

Audit 12 Oktober 2026 terhadap kode yang masuk APK 2.1.3. Audit ini membandingkan
perilaku yang didokumentasikan Spotify dengan implementasi Gratify; tidak
menganggap algoritma rekomendasi internal Spotify tersedia untuk disalin.

**Status revisi berikutnya:** temuan di bawah adalah kondisi APK rilis 2.1.3
sebelum revisi. Layar undangan pertama, keadaan menunggu teman, penanda asal selera
lagu dan hak anggota Blend untuk mengundang sudah diperbaiki pada revisi debug.
Hasil dan batas implementasinya berada di
[audit UI referensi](PLAYLIST_REFERENCE_UI_AUDIT_2026-10-12.md).

## Konsep yang harus dipertahankan

Blend merupakan campuran yang disusun otomatis dari selera/aktivitas mendengarkan
anggota. Alur ponsel Spotify dimulai dari + → Blend → Undang → bagikan link,
dengan pembaruan harian dan kemampuan anggota mengundang teman mereka.
Rujukan: [Spotify Support Indonesia](https://support.spotify.com/id-id/article/social-recommendations-in-playlists/).

Playlist kolaboratif memberi anggota kendali menambah, menghapus, dan mengurutkan
lagu secara manual. Rujukan: [Spotify Support](https://support.spotify.com/us/article/collaborative-playlists/).

Spotify menjelaskan campuran lagu dan rekomendasi setelah undangan diterima,
identitas peserta yang terkait dengan pilihan lagu, serta kartu kecocokan selera.
Rujukan: [pengumuman Blend Spotify](https://newsroom.spotify.com/2022-03-30/discover-and-listen-to-music-with-even-more-friends-and-family-plus-some-of-your-favorite-artists-with-spotifys-newest-blend-update/).

## Hasil pemeriksaan Gratify

| Bagian | Implementasi saat ini | Penilaian |
| --- | --- | --- |
| Langkah awal | Form nama, persetujuan, Buat, lalu halaman detail | Terlalu menyerupai pembuatan playlist biasa; perlu layar Undang teman yang jelas |
| Penyusunan isi | Server mengambil favorit dan jumlah dengar tersinkron; minimal dua anggota dengan persetujuan | Dasar campuran otomatis sudah ada |
| Rekomendasi | Memilih dari lagu favorit/riwayat anggota yang sudah ada | Belum memasukkan penemuan lagu di luar kandidat tersebut |
| Pengubahan lagu | UI dan server membatasi tambah/hapus/urut manual untuk kolaboratif | Pemisahan konsep ini sudah benar |
| Pembaruan | Permintaan detail memicu refresh; server menghitung ulang jika lewat satu hari, atau keanggotaan menginvalidasi hasil | Pembaruan saat dibuka, bukan pekerjaan harian yang berjalan mandiri |
| Undangan anggota | Hanya pemilik membuat/mengirim undangan resmi | Berbeda dari perilaku anggota Blend Spotify yang boleh mengundang teman |
| Informasi selera | Data lagu mempunyai satu added_by; layar hanya menampilkan judul/artis | Belum ada penanda siapa terkait lagu, kecocokan, atau story |
| Data belum cukup | Petunjuk umum untuk menyukai/mendengar lagu dan menunggu sinkronisasi | Perlu penjelasan kesiapan data tiap anggota serta status penyusunan |

Algoritma saat ini membatasi kandidat per anggota/artis, memberi bobot favorit
lebih tinggi daripada jumlah dengar, menghapus duplikasi, dan menghasilkan maksimal
50 lagu. Ini campuran sederhana berbasis data Gratify, bukan mesin personalisasi
Spotify. Satu lagu yang disukai beberapa orang saat ini menyimpan satu identitas
asal saja; data tersebut belum cukup untuk mengklaim semua pemilik selera lagu itu.

Kode yang diperiksa:

- `composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/library/PlaylistWorkspaceScreen.kt`
- `composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/PlaylistWorkspaceViewModel.kt`
- `supabase/migrations/20261012043526_playlist_workspaces.sql`

## Arah revisi

1. + → Blend membuka penjelasan singkat dan tombol Undang teman. Nama dibentuk
   otomatis dan dapat diubah kemudian. Persetujuan data tetap jelas.
2. Penerima link/chat melihat pratinjau dan memilih Gabung. Membuka link tidak
   langsung menukar persetujuan atau menambahkan anggota.
3. Satu anggota mendapat layar menunggu teman; dua atau lebih mendapat status
   pengumpulan selera/penyusunan, atau alasan data belum cukup. Undangan dan
   persetujuan adalah proses anggota, bukan pemilihan lagu manual.
4. Hasil menampilkan campuran otomatis, peserta terkait tiap lagu jika datanya
   memang tersedia, waktu pembaruan, dan tindakan Undang/Keluar.
5. Pisahkan fondasi campuran dari tambahan rekomendasi baru, kecocokan dan kartu
   berbagi. Skor harus berasal dari perhitungan yang dijelaskan dan data memadai;
   jangan mengisi persentase buatan agar menyerupai Spotify.
6. Uji dengan dua akun nyata serta dataset selera berbeda/serupa/kosong,
   sinkronisasi tertunda, anggota masuk/keluar, pembaruan harian, dan izin undangan.

Kelulusan build, menu, Blend satu anggota, atau tes database sebelumnya tidak
membuktikan kesetaraan pengalaman Blend dengan Spotify. Pada tahap audit awal tidak ada
perubahan aplikasi, migrasi produksi, atau APK baru; hasilnya adalah temuan dan
spesifikasi koreksi konsep.
