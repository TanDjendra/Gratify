# Playlist biasa dengan akses kolaborator

Implementasi dimulai 12 Oktober dan diselesaikan 13 Oktober 2026. Permintaan:
kolaboratif merupakan akses tambahan pada playlist yang sama, bukan playlist
baru atau salinan playlist pribadi.

## Perilaku akhir

- Saat online, pilihan Playlist dan Playlist kolaboratif memakai formulir dan
  halaman detail yang sama. Pintasan kolaboratif membuka undangan setelah dibuat.
- Playlist pribadi yang sudah ada menampilkan tombol **+** di samping pemilik.
  Menekannya menyinkronkan isi terakhir lalu mengaktifkan akses anggota pada
  identitas server yang sama. Nama, sampul, urutan lagu, ID lokal, pin profil,
  bookmark publik, dan penempatan folder dipertahankan.
- Undangan tidak mengubah status publik/privat. Pemilik mengatur nama, sampul,
  privasi, anggota, dan penghapusan. Anggota mengelola lagu pada playlist yang sama.
- Edit urutan lagu tersedia pada halaman bersama, termasuk geser, tombol urutan
  yang dapat diakses, dan pemeriksaan revisi ketika anggota lain mengubah playlist.
- Pemutaran, antrean, dan kontrol unduhan menggunakan cache playlist yang sama.
  Lagu baru dari anggota membatalkan status unduhan lengkap; pengubahan urutan
  saja mempertahankannya.
- Penerima yang sebelumnya menyimpan playlist publik bergabung menggunakan cache
  dan folder yang sama. Pembaca publik tetap memiliki alur simpan ke Pustaka.
- Data cache lokal dan tampilan publik/profil adalah turunan dari satu sumber
  playlist. Penulisan snapshot lama ditolak agar tidak menimpa perubahan anggota.
- Blend tetap menggunakan playlist otomatis dan persetujuan anggota; tidak
  memperoleh editor lagu manual dari perubahan ini.

## Implementasi

`personal_links` mempertahankan ID cloud dan `client_sync_id` asal ketika
playlist pribadi memperoleh anggota. Operasi tambah, hapus, urutkan, metadata,
anggota, dan privasi menulis sumber workspace; proyeksi cloud/shared diperbarui
dari sumber tersebut. Endpoint dan penulisan langsung lama memiliki pelindung
terhadap pembaruan proyeksi secara independen.

Room diperbarui dari versi 28 ke 29 dengan migrasi otomatis dan kolom
`canonical_playlist_id`. Cache memakai ID lokal lama, mempertahankan waktu
penambahan lagu dan informasi unduhan, serta tidak menghasilkan antrean
penghapusan server ketika akses anggota dicabut.

Migrasi server yang diterapkan:

- `supabase/migrations/20261012153836_unified_playlist_collaboration.sql`
- `supabase/migrations/20261012155607_canonical_saved_playlist_identity.sql`

Implementasi utama ada pada `UnifiedPlaylistScreen.kt`,
`PlaylistWorkspaceScreen.kt`, `PlaylistWorkspaceViewModel.kt`,
`CanonicalPlaylistOperations.kt`, `LocalPlaylistRepositoryImpl.kt`,
`SocialRepositoryImpl.kt`, dan `DatabaseDao.kt`.

## Verifikasi

Rincian hasil akhir disimpan di
`artifacts/unified-playlist-20261012/verification.json`.

- Aplikasi: 72 tes lulus; penyimpanan lokal: 20 tes lulus. Tidak ada kegagalan.
- Kompilasi sumber Android berhasil (`:composeApp:compileAndroidMain`).
- PostgreSQL terisolasi: 127 pemeriksaan lulus, termasuk izin anggota, identitas,
  privasi, penolakan snapshot lama, folder, nama, artwork, undangan, dan Blend.
- Server nyata: 29 pemeriksaan penyatuan playlist dan 28 pemeriksaan undangan/
  Blend sebelumnya lulus dalam transaksi yang dibatalkan sepenuhnya. Akun,
  playlist, pesan, dan objek storage sintetis tidak disimpan.
- Pemeriksaan setelah transaksi: nol akun sintetis tersisa; semua playlist
  kolaboratif memiliki proyeksi; fungsi proyeksi tidak bisa dipanggil klien.
- Tes Room mencakup migrasi 28→29, ID stabil, cache anggota, pemulihan bookmark,
  penolakan penulis lama, pencabutan akses, dan status unduhan.
- Tes UI mencakup halaman personal/kolaboratif, edit urutan dan konflik revisi,
  undangan berulang, tampilan kecil, nama, folder/Pustaka, profil, dan pencarian.

## Batas verifikasi

APK baru belum dikemas atau dipasang untuk revisi ini. Pengujian interaksi pada
ponsel belum dilakukan; kompilasi Android dan tes otomatis tidak menggantikan
uji perangkat. Migrasi server sudah aktif, sehingga aplikasi lama tidak dapat
menulis snapshot independen pada playlist yang telah disatukan.

Playlist lokal yang belum disatukan masih menggunakan penyimpanan lokal untuk
penggunaan tanpa akun/offline. Playlist dengan anggota memerlukan koneksi untuk
memuat detail dan memastikan perubahan di server; revisi ini tidak menyediakan
editor kolaboratif offline. Riwayat/cache unduhan tetap dipertahankan. Perubahan
anggota tidak otomatis disalin ke playlist YouTube eksternal.

Advisor server masih melaporkan temuan lama, antara lain view security definer,
search path fungsi lama, dan opsi keamanan autentikasi. Tabel pemetaan privat
baru memiliki RLS tanpa kebijakan klien dan seluruh hak akses tabel dicabut;
informasi “RLS enabled no policy” untuk tabel itu merupakan pembatasan yang
disengaja. Laporan ini bukan pernyataan kelayakan rilis seluruh aplikasi.

## Build debug setelah permintaan berikutnya

Pada 13 Oktober 2026, `:androidApp:assembleDebug` berhasil. APK arm64 tersedia di
`artifacts/unified-playlist-debug-20261013/Gratify-playlist-unified-debug-arm64.apk`.
Identitas `com.tan.gratify.next.dev`, nama Gratify Dev, versi 2.1.3-dev/64,
flag debuggable, ABI arm64-v8a, dan tanda tangan APK v2 sudah diperiksa.
Ukuran 74,4 MiB; SHA-256 dan varian universal tercatat dalam `verification.json`
di folder yang sama. Build ini belum dipasang atau diuji pada ponsel. Pernyataan
belum ada APK pada bagian sebelumnya adalah status sebelum permintaan build ini.

Setelah pengguna meminta pemasangan, APK ini berhasil dipasang sebagai pembaruan
Gratify Dev pada vivo I2218 dengan data aplikasi dipertahankan. Pemeriksaan awal
`am start -W` menghasilkan Status ok dan MainActivity berjalan. Pemeriksaan ini
mengonfirmasi pemasangan/peluncuran, belum merupakan uji seluruh fitur di ponsel.
