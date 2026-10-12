# Tampilan Playlist kami — 12 Oktober 2026

Permintaan: isi halaman Playlist kami mengikuti playlist pribadi, dengan kontrol
tambahan untuk anggota dan undangan kolaborator.

## Perubahan

- Playlist kolaboratif memakai `CollectionDetailHeader` yang dipakai playlist
  pribadi: ukuran sampul, judul, gradasi, Kembali, Ubah, menu, Putar dan Acak.
- Baris pemilik ditambah tombol + dan avatar anggota untuk membuka panel Orang.
- Privasi dan tombol Tambahkan ke playlist ini memakai komponen playlist pribadi.
- Baris lagu memakai `SongFullWidthItems`, termasuk indikator lagu diputar,
  thumbnail, menu dan gestur tambah antrean. Tombol hapus dan pindahkan tidak
  lagi memenuhi setiap baris; tindakan itu berada di menu lagu.
- Ubah detail tetap khusus pemilik. Anggota kolaboratif dapat menambah,
  menghapus dan mengurutkan lagu; pengunjung tidak memperoleh hak mengedit.
- Putar/jeda mengikuti antrean workspace yang aktif. Acak mengacak antrean
  playback tanpa mengubah urutan lagu yang tersimpan.
- Tampilan Blend tetap mengikuti alur campuran otomatis. Tidak ada perubahan
  struktur server, konfigurasi domain atau kebijakan undangan pada revisi ini.

## Pemeriksaan otomatis

35 tes lulus, nol kegagalan/error, dan build debug berhasil. Tiga tes baru
membandingkan komponen kolaboratif dengan header pribadi, memeriksa tombol
sebenarnya, lebar 320/390 dp, teks 150%, status menunggu dan hak pengunjung,
serta baris lagu dengan menu. Render tersimpan di
`artifacts/playlist-unified-20261012/`.

## APK dan pemeriksaan perangkat

APK `Gratify-unified-playlist-debug-arm64.apk` dipasang dengan pembaruan pada
Gratify Dev (`com.tan.gratify.next.dev`, 2.1.3-dev/64). APK Gratify utama tetap
terpisah. SHA-256:
`c70c9f56c2e5d268b3d053be8c700fb6f79ec294bee5a9bd8789b2af59d4091e`.

Pada vivo I2218/Android 16, tampilan kolaboratif diperiksa dengan playlist No.20
dan No.25. Panel anggota, editor (dibatalkan), menu playlist, serta halaman
tambah lagu dibuka. Playlist pribadi No.1 tetap memiliki empat lagu semula.
Tidak ada lagu, playlist, foto, anggota atau undangan yang dibuat/dihapus dalam
pemeriksaan ini. Lima koleksi yang ditemukan setelah pemasangan dipertahankan.

Pesan undangan kedaluwarsa sempat muncul pada No.20 selama pemeriksaan awal.
Pesan tidak muncul pada uji ulang terkontrol No.20 dan No.25 setelah polling
otomatis dan buka/tutup panel anggota. Delapan pemeriksaan perangkat beserta
ringkasan log proses dicatat dalam `verification.json`. Penyebab kemunculan
awal belum terbukti; uji ulang yang lulus tidak diklaim sebagai perbaikan penyebabnya.

Playlist kolaboratif pada perangkat masih kosong, sehingga interaksi baris lagu
diverifikasi melalui tes UI, bukan playback workspace dengan lagu di perangkat.
Revisi ini belum merupakan APK rilis publik maupun audit RAM baru.
