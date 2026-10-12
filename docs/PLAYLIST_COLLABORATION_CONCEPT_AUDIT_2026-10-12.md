# Satu playlist dengan akses kolaborator

## Kesimpulan audit

Permintaan pengguna adalah satu halaman dan satu identitas playlist. Kolaborasi
merupakan pemberian akses kepada anggota pada playlist tersebut. Penyamaan
tampilan sebelumnya belum menyatukan alur atau penyimpanan Gratify.

Spotify mendokumentasikan bahwa playlist yang sudah ada dapat dijadikan
kolaboratif. Teman yang mendapat akses dapat menambah, menghapus, dan mengurutkan
lagu. Foto anggota di bawah nama playlist membuka pengelolaan anggota; undangan
dibuat melalui link yang berlaku tujuh hari.

Sumber: [Spotify Support — Collaborative playlists](https://support.spotify.com/us/article/collaborative-playlists/).
Rujukan tombol penambahan pengguna dan avatar pada header:
[Spotify Newsroom — How To Make a Collaborative Playlist](https://newsroom.spotify.com/2020-09-29/how-to-make-a-collaborative-playlist/).
Artikel Newsroom berasal dari 2020; dukungan Spotify digunakan untuk aturan
undangan terkini. Tampilan yang diinginkan mengikuti screenshot pengguna.

## Kondisi kode Gratify yang diperiksa

| Bagian | Temuan |
|---|---|
| Menu buat di `LibraryScreen.kt` | `personal` membuka builder biasa; opsi lain membuka `PlaylistWorkspaceDestination` |
| `LocalPlaylistScreen.kt` | Header playlist biasa belum memiliki baris avatar dan aksi undang; lagu, antrean, unduhan dan edit memakai `LocalPlaylistViewModel` |
| `PlaylistWorkspaceScreen.kt` | Playlist kolaboratif dibuat dan dibuka melalui route serta ViewModel tersendiri; memiliki panel anggota/undangan |
| `WorkspacePresentation.kt` | Header sudah memakai komponen visual yang sama, tetapi baris avatar/+ hanya tersedia pada workspace |
| Penyimpanan | Playlist biasa menggunakan playlist lokal serta sinkronisasi `cloud_playlists`; publikasi/salinan baca memakai `shared_playlists`. Workspace memakai `gratify_workspace_private.playlists`, `members`, dan `items` |
| API workspace | `create` menerima `collaborative`/`blend`; undangan menunjuk playlist workspace. Belum ditemukan operasi yang menghubungkan playlist biasa menjadi playlist yang dapat diedit anggota pada identitas yang sama |
| Folder | Referensi `local:`, `shared:`, dan `workspace:` masih dipisahkan |

File migrasi yang diperiksa: `20261012043526_playlist_workspaces.sql` dan
`20261012053750_workspace_covers_nested_folders.sql`. Ini audit kode/migrasi lokal;
tidak ada pembacaan atau perubahan data server pada audit konsep ini.

## Alur yang sesuai permintaan

1. Buat playlist, isi nama, lalu buka halaman playlist yang sama untuk tambah
   lagu, putar, urutkan, ubah sampul, dan ubah detail.
2. Header playlist milik pengguna menampilkan tombol + di sebelah foto pemilik.
   Tombol + membuka pembuatan undangan; avatar membuka daftar anggota.
3. Pemilik membagikan undangan melalui link atau chat Gratify. Link berbagi untuk
   mendengar tidak otomatis memberikan hak edit.
4. Penerima login bila diperlukan, melihat undangan dan bergabung. Server memberi
   akses ke identitas dan isi playlist yang sama. Playlist tidak disalin atau
   dibuat ulang. Pengelolaan lagu tetap pada halaman playlist yang sama.
5. Pemilik mengatur privasi, detail dan anggota. Kolaborator mengelola lagu sesuai
   haknya. Cabut undangan membatalkan link; menghapus anggota membatalkan akses
   orang tersebut. Kedua tindakan perlu dibedakan dalam UI dan server.
6. Playlist privat tetap privat untuk pemilik dan anggota yang diberi akses.
   Mengundang tidak menjadikan playlist publik atau memasukkannya ke pencarian
   publik. Akses kolaborator dan visibilitas publik merupakan pengaturan berbeda.

Opsi “Playlist kolaboratif” pada menu + dapat dipertahankan mengikuti referensi.
Opsi itu adalah pintasan membuat playlist melalui alur yang sama dengan nama
awal “Playlist kami No.N”, kemudian mengarahkan pengguna ke undangan. Opsi
“Playlist” memakai nama awal “Playlist-ku No.N”. Nama yang sudah dipilih pemilik
tidak otomatis diganti ketika mengundang atau saat anggota bergabung.

Tombol + menunjukkan bahwa pemilik bisa mengundang. Menekan tombol menyiapkan
undangan; hak edit orang lain diberikan setelah undangan diterima, bukan kepada
setiap orang yang dapat melihat playlist.

Blend tetap memiliki proses penyusunan lagu otomatis berdasarkan selera anggota;
penyatuan playlist biasa/kolaboratif tidak mengubahnya menjadi Blend.

## Pekerjaan implementasi yang diperlukan

- Gunakan halaman, editor, pemilih lagu dan perintah playlist yang sama pada kedua
  pintasan pembuatan. Tambahkan baris pemilik/anggota serta aksi undang pada
  playlist milik pengguna yang dapat diedit.
- Tentukan satu identitas playlist yang dipertahankan saat akses kolaborator
  diberikan. Hubungkan penyimpanan lokal/offline dengan data server yang memberi
  hak edit anggota; hindari dua salinan yang masing-masing bisa berubah.
- Pertahankan lagu dan urutannya, nama, deskripsi, sampul, privasi, tautan profil,
  penempatan folder, unduhan dan antrean pada transisi tersebut. Playlist lokal
  yang belum tersinkron harus berhasil disinkronkan sebelum link siap ditampilkan.
- Terapkan pembatasan pemilik/kolaborator pada server, serta penanganan konflik
  perubahan bersamaan. Menu anggota dan status loading memakai perlindungan
  lembar yang sudah diperbaiki pada revisi sebelumnya.
- Uji playlist biasa → undang → terima di akun lain → edit kedua akun → cabut
  link/hapus anggota. Periksa tidak ada duplikat koleksi, perubahan privasi,
  kehilangan lagu, atau perpindahan ke halaman playlist berbeda.

Audit ini menetapkan koreksi konsep dan mencatat perbedaan implementasi saat ini.
Kode aplikasi, schema server, APK dan data pengguna tidak diubah pada audit ini.

## Tambahan permintaan pengguna: sampul, nama, dan Blend

Dicatat pada 12 Oktober 2026. Bagian ini merupakan daftar revisi yang perlu
diimplementasikan, bukan laporan bahwa perbaikannya sudah terpasang.

### Sampul otomatis dari lagu teratas

- Bila pengguna tidak memilih sampul, playlist mengambil gambar dari urutan lagu
  yang tersimpan. Dengan empat lagu atau lebih, gunakan empat lagu teratas sebagai
  kolase 2 × 2. Dengan satu sampai tiga lagu, gunakan gambar lagu pertama memenuhi
  sampul. Hanya playlist tanpa lagu memakai placeholder.
- Sampul upload tetap diprioritaskan. Menghapus sampul upload mengembalikan sampul
  otomatis. Mengubah urutan, menambah atau menghapus lagu memperbarui sampul
  otomatis tanpa mengganti sampul pilihan pengguna.
- Halaman detail, Pustaka, folder, profil dan pencarian menggunakan aturan dan
  komponen yang sama. Kegagalan satu gambar perlu fallback yang tidak membuat
  seluruh sampul hilang atau berkedip.

Temuan kode: `WorkspaceCover` sudah membentuk kolase dari `items.take(4)` pada
detail workspace; `LocalPlaylistScreen` juga memakai `PlaylistCollageThumbnail`.
Namun kartu workspace di `LibraryScreen` hanya menerima `p.coverUrl`, yang kosong
bila tidak ada sampul upload. Karena itu keberadaan kolase di detail belum
menjamin sampul otomatis muncul di semua tempat.

### Nomor nama awal tidak melompat karena menu atau pembatalan

Temuan terkonfirmasi: `PlaylistWorkspaceViewModel.nextName` membaca dan langsung
menulis `playlist_creation_sequence_<owner>` saat opsi menu dipilih. Penghitung
tersebut dipakai untuk playlist pribadi, kolaboratif, Blend, dan folder. Membuka
lalu membatalkan pembuatan tetap menghabiskan nomor; membuat folder juga
menghabiskan nomor meskipun nama folder tidak memakai angka.

Aturan revisi yang dicatat:

- Tentukan saran nama dari playlist yang benar-benar ada, bukan jumlah klik menu.
  Pilih nomor positif terkecil yang belum dipakai pada pola nama terkait. Bila
  “Playlist-ku No.1” ada dan “Playlist-ku No.2” belum ada, sarankan No.2.
- Nama awal “Playlist-ku No.N” dan “Playlist kami No.N” diperiksa berdasarkan pola
  masing-masing; Blend dan folder tidak menghabiskan nomor keduanya.
- Membatalkan atau gagal menyimpan tidak menghabiskan nomor. Periksa ulang
  benturan saat penyimpanan berhasil, termasuk sinkronisasi/perangkat lain.
- Jangan mengubah atau menomori ulang nama playlist yang sudah ada. Nama buatan
  pengguna tetap dipertahankan. Nomor nama bukan identitas database playlist.
- Pintasan kolaboratif tetap menghasilkan playlist yang sama sebagaimana konsep
  penyatuan sebelumnya; mengundang ke playlist yang sudah ada tidak mengganti
  namanya dengan “Playlist kami”.

### Blend: undangan dahulu, campuran otomatis setelah bergabung

Dokumentasi resmi Spotify menjelaskan Blend sebagai campuran dari aktivitas
mendengarkan anggota yang diperbarui harian. Alurnya memilih Blend, membuat dan
mengirim undangan, lalu menemukan Blend setelah teman bergabung. Anggota dapat
mengundang teman lagi; identitas anggota terlihat pada campuran.

Sumber: [Spotify Support — Rekomendasi sosial di playlist](https://support.spotify.com/id-id/article/social-recommendations-in-playlists/).
Dokumentasi itu menjelaskan perilaku pengguna, bukan algoritma rekomendasi internal
atau kapan baris database Spotify dibuat. Pemisahan draft undangan dari playlist
aktif di bawah adalah rancangan Gratify untuk memenuhi permintaan pengguna.

Alur revisi:

1. Memilih Blend membuka pengenalan dan persetujuan penggunaan data selera. Belum
   membuat playlist aktif atau kartu Blend kosong di Pustaka.
2. Menekan Undang menyiapkan draft undangan/link, yang bisa disalin atau dikirim
   lewat chat Gratify. Membuka lembar bagikan tidak boleh dianggap sebagai bukti
   link sudah dikirim atau teman sudah bergabung.
3. Setelah setidaknya satu teman login, menyetujui, dan bergabung, aktifkan satu
   Blend untuk pemilik dan anggota tersebut secara idempotent. Semua melihat
   campuran yang sama. Draft yang dibatalkan/kedaluwarsa tidak meninggalkan
   playlist kosong aktif.
4. Sistem memilih lagu dari data selera yang disetujui anggota. Tidak menyediakan
   tambah, hapus, atau susun lagu manual seperti playlist kolaboratif. Jika data
   belum cukup, tampilkan status menunggu data dan petunjuk mendengarkan/menyukai
   lagu, tanpa menawarkan penambahan manual.
5. Campuran diperbarui harian dan menyesuaikan perubahan anggota. Pengelolaan
   undangan, keluar dan privasi tetap tersedia. Kondisi anggota tinggal satu
   orang harus menampilkan status menunggu teman, bukan campuran bersama aktif.

Temuan implementasi lokal:

- `PlaylistWorkspaceScreen` saat ini memanggil `model.create("blend", ...)` ketika
  menekan Undang, lalu membuka panel undangan. Baris playlist dan anggota pemilik
  sudah dibuat sebelum penerima bergabung; ini harus diubah ke draft undangan.
- UI hanya menawarkan editor lagu pada `kind == "collaborative"`. API workspace
  juga menolak mutasi lagu manual untuk Blend. Pembatasan ini sudah ada dan perlu
  dipertahankan pada penyatuan halaman playlist.
- `refresh_blend` baru menyusun lagu bila ada sedikitnya dua anggota yang setuju.
  Kandidat berasal dari favorit dan riwayat dengar tersinkron. Ini pendekatan
  Gratify, bukan penerapan algoritma internal Spotify.
- Penyegaran saat ini diminta ketika halaman dibuka dan dibatasi interval satu
  hari. Itu belum menjamin pembaruan setiap hari ketika aplikasi tidak dibuka;
  mekanisme server atau penyegaran saat dibaca perlu ditentukan dan diuji.

Verifikasi implementasi mendatang: uji sampul pada 0/1/3/4/lebih dari 4 lagu,
perubahan urutan dan reset sampul; nama setelah batal/gagal/folder/penghapusan;
Blend tanpa undang, undang tanpa penerima, penerima bergabung, penerimaan link
berulang, data selera belum cukup, pergantian hari, dan anggota keluar.

Pada pencatatan tambahan ini tidak dilakukan build, instalasi APK, perubahan kode
aplikasi, atau perubahan data server.

## Pembaruan setelah permintaan “terapkan”

Tiga tambahan mengenai sampul otomatis, nomor nama awal, dan siklus undangan Blend
sudah diterapkan pada kode dan server. Lihat
[laporan implementasi](PLAYLIST_REFINEMENTS_IMPLEMENTATION_2026-10-12.md) untuk
perilaku akhir, bukti uji, dan batasan. Temuan kode di bagian sebelumnya merupakan
catatan sebelum revisi. Penyatuan penuh data/rute playlist pribadi dan kolaboratif
diselesaikan pada revisi berikutnya, 13 Oktober 2026. Lihat
[hasil penyatuan playlist](UNIFIED_PLAYLIST_COLLABORATION_IMPLEMENTATION_2026-10-13.md).
APK baru untuk revisi tersebut belum dibangun.
