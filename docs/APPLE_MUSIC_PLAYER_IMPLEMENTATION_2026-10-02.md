# Now Playing dan lirik bergaya Apple Music

Pilihan pengguna: hanya gaya Apple Music. Implementasi memakai Compose dan
pemutar Gratify yang sudah ada. Referensi yang diperiksa adalah
[SimpMusic commit 62e0073](https://github.com/maxrave-dev/SimpMusic/tree/62e0073ba818d899b22c75909deb9b034516d5db),
khususnya `NowPlayingContentAppleMusic`, `NowPlayingContentState`, dan
`AppleMusicLyricsLines`. Atribusi GPL-3.0 tetap dipertahankan.

## Perilaku

- Pemutar yang diperluas memakai artwork besar, latar buram dan gradasi yang
  mengikuti warna sampul, kontrol putih, serta tab pemutar, lirik, dan antrean.
- Ketiga tab memakai sesi pemutaran dan timeline yang sama. Pergantian tab
  tidak memulai ulang lagu. Tab terakhir diingat selama sesi aplikasi, termasuk
  ketika panel ditutup dan dibuka kembali.
- Artwork tetap dapat digeser untuk mengganti lagu. Repeat One menonaktifkan
  gesture tersebut. Antrean dapat dipilih, dan tombol kelola membuka editor
  antrean yang sudah ada. Menu lagu, favorit, informasi, halaman artis, video,
  Canvas, serta layar penuh tetap terhubung ke fungsi aplikasi.
- Lirik memakai teks besar dengan fokus yang berpindah antarbaris. Baris aktif
  tajam, sementara baris di sekitarnya meredup dan melembut. Timing per kata
  menggerakkan sorotan dan glow jika sumber menyediakan `RICH_SYNCED`.
  `LINE_SYNCED` memakai fokus per baris; lirik tanpa timing tetap dapat dibaca
  dan tidak diberi timing buatan.
- Pause membatalkan gerak sorotan per kata. Seek mundur menghitung ulang sorotan
  dari posisi baru. Mengetuk lirik tersinkron tetap melakukan seek.
- Lirik fullscreen menggunakan renderer Apple Music yang sama. Terjemahan tetap
  mengikuti pemetaan waktu yang sudah ada. Tombol vote hanya muncul jika
  lirik/terjemahan mempunyai record Gratify Lyrics yang bisa dinilai.
- Warna juga diperbarui ketika tab lirik/antrean langsung dibuka, tanpa perlu
  mengunjungi tab artwork. Tab lirik tidak dipaksa pindah ketika lagu berikutnya
  masih mengambil lirik atau tidak memiliki lirik.
- Blur menggunakan kemampuan platform yang tersedia. Pada Android lama yang
  tidak mendukung blur Compose, opacity dan penekanan teks tetap berfungsi.

## Validasi

Perintah akhir:

```text
gradlew :composeApp:jvmTest :composeApp:compileAndroidMain :androidApp:lintDebug
```

- 54 tes aplikasi lulus, 0 kegagalan/error.
- Kompilasi Android berhasil; lint 0 error, 115 warning yang juga ada pada
  pemeriksaan sebelum perubahan ini.
- Tiga tes baru mencakup pemisahan aksi tab/pemutaran, seek, pemilihan dan
  pengelolaan antrean, timing kata, seek mundur, render lirik paused yang tetap
  diam, teks besar, empty state, serta batas video pada layar mendatar.
- Render produksi diuji pada 390×844, 360×640 dengan font 1,5×, dan 900×420.
  Screenshot memakai sampul/lagu fixture untuk verifikasi komponen.
- Log: `artifacts/simpmusic-apple-reference/verification.log`.
- Pratinjau: `artifacts/apple-music-player/now-playing.png`, `lyrics.png`,
  `queue.png`, `rich-lyrics.png`, `main-large-text.png`, dan `video-landscape.png`.

Validasi awal di atas dilakukan tanpa merakit APK baru. Pengujian lanjutan
pada 2 Oktober 2026 memakai APK debug dan emulator Android dengan komponen
produksi serta timeline sintetis. Hasil dan batasannya dicatat dalam
[laporan performa](APPLE_MUSIC_PERFORMANCE_2026-10-02.md). APK final untuk
dibagikan dan pengukuran pada HP fisik belum dilakukan untuk perubahan ini.

Optimasi lanjutan mengurangi pembaruan pada baris tidak aktif, memakai satu
clock animasi per baris aktif, menggambar sorotan dari TextLayoutResult yang
tersimpan, dan memisahkan/cache layer latar. Validasi terbaru: 55 tes JVM
lulus, assembleDebug berhasil, serta lint 0 error dan 116 warning. Perbandingan
30 window sebelum/sesudah tersedia pada
[laporan optimasi](APPLE_MUSIC_OPTIMIZATION_REPORT_2026-10-02.md).
