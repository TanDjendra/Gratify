Status terbaru: [penyelesaian dan APK bertanda tangan](LAUNCH_COMPLETION_2026-10-01.md). Angka/build pada laporan berikut merekam tahap sebelumnya.

# Pembaruan UI detail playlist dan album

Tanggal: 1 Oktober 2026

## Cakupan

- Detail album.
- Playlist online dan radio.
- Playlist lokal, termasuk salinan playlist bersama.
- Playlist bersama dari fitur sosial.
- Koleksi lagu disukai, unduhan, sering diputar, dan lagu teratas.

## Tampilan dan interaksi

Semua detail koleksi musik menggunakan komponen header yang sama: sampul persegi,
judul yang mudah dibaca, pembuat/artis dan jumlah lagu, kontrol simpan/download/menu,
shuffle, serta tombol putar hijau. Latar beralih dari warna sampul ke warna gelap
Gratify. Layar lebar menempatkan sampul di samping informasi; layar sempit memakai
susunan vertikal. Judul ringkas dan tombol kembali tampil saat header telah digulir.

Daftar lagu mempertahankan menu lagu dan tambah antrean. Baris lagu yang sedang
diputar ditandai hijau; album menggunakan nomor lagu, sementara playlist memakai
thumbnail. Tombol detail mempunyai label aksesibilitas dan target sentuh yang jelas.

Efek blur/glass dan hero sampul memenuhi layar diganti dengan header yang lebih
ringkas. Permintaan sampul utama dibatasi pada ukuran 512 piksel. Saran lagu pada
playlist lokal dimuat ketika pengguna membukanya. Pemuatan album sebelumnya
dibatalkan saat pengguna membuka album lain.

Halaman memiliki skeleton loading, pesan gagal memuat dengan tombol coba lagi,
pesan koleksi kosong, dan pesan pencarian tanpa hasil. Tombol putar/shuffle
dinonaktifkan pada koleksi kosong atau playlist lokal yang tidak tersedia.
Playlist bersama menampilkan konfirmasi sebelum penghapusan, status putar aktif,
serta menu per lagu. Tautan ke artis/pembuat dan menu playlist tetap tersedia.

Rujukan arah visual: [kontrol dan daftar lagu Spotify](https://newsroom.spotify.com/2020-02-27/3-icons-to-know-in-spotify-mobiles-refreshing-new-look/)
dan [panduan desain Spotify](https://developer.spotify.com/documentation/design).
Gratify tetap menggunakan identitas dan komponen aplikasinya sendiri.

## Verifikasi

Hasil akhir: **build Android debug versi full berhasil; kompilasi JVM berhasil;
9 tes Compose lulus, tanpa kegagalan**, termasuk tiga tes render/interaksi baru.
Pemeriksaan whitespace perubahan juga lulus. Ringkasan dan checksum APK berada
di `artifacts/playlist-redesign/verification.json`.

Hasil akhir kompilasi dan tes dicatat di
`artifacts/launch-audit/collection-detail-build.log`.

Tes render memakai komponen produksi dan data contoh tanpa akun pribadi maupun
layanan jaringan. Pemeriksaan meliputi tombol putar/jeda dan simpan, layout ponsel,
judul panjang pada desktop, serta tombol putar nonaktif pada layar sempit saat
koleksi kosong. Tes lama di modul Compose juga dijalankan.

Pratinjau hasil render:

- `artifacts/playlist-redesign/playlist-mobile.png`
- `artifacts/playlist-redesign/album-desktop.png` (header album pada layar lebar)
- `artifacts/playlist-redesign/playlist-empty.png`

Pratinjau tersebut merupakan render komponen aplikasi dengan data contoh, bukan
tangkapan layar dari perangkat Android. Pengukuran kelancaran di HP, pemutaran
dengan layanan nyata, dan pengujian playlist panjang pada perangkat belum dilakukan.
APK debug terbaru berada di `androidApp/build/outputs/apk/debug/` setelah build
berhasil. APK release dari audit sebelumnya belum dibangun ulang untuk desain ini.
