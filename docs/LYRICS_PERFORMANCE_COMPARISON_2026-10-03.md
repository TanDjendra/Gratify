# Perbandingan lirik dan beban pemrosesan — 3 Oktober 2026

Perbaikan batas kata tidak menunjukkan kenaikan beban render yang konsisten dalam pengujian komponen di komputer. Pemrosesan seluruh respons lirik Mangu membutuhkan median sekitar 1,00 ms, sekali ketika respons diterima. Ini belum membuktikan FPS, penggunaan baterai, atau kestabilan aplikasi pada HP fisik.

## Ruang lingkup

- Pembanding memakai parser dan bagian renderer baris aktif asli [SimpMusic revisi 62e0073](https://github.com/maxrave-dev/SimpMusic/tree/62e0073ba818d899b22c75909deb9b034516d5db), serta [parser layanan core revisi d90b53f](https://github.com/maxrave-dev/core/blob/d90b53fbad07ab73cecbfe89b33b533fb9ca78ed/service/lyricsService/src/commonMain/kotlin/org/simpmusic/lyrics/parser/LrcTextParser.kt).
- Salinan pembanding hanya berada di sumber pengujian JVM, dengan adaptasi package/import. Algoritma parser dan render baris aktif tidak diubah. Identitas sumber dicatat pada `upstream-source.json`.
- Perbandingan pemrosesan memakai respons langsung [Mangu, video JENpTmMQBQY](https://api-lyrics.simpmusic.org/v1/JENpTmMQBQY) yang sama, diambil satu kali sebelum pengukuran. Seluruh 33 baris diproses, bukan hanya baris screenshot. Konsol tidak diukur.
- Perbandingan animasi memakai baris screenshot yang sama dengan 11 timestamp asli. Komponen asli menerima data sumber; komponen produksi Gratify menerima teks yang telah diperbaiki. Lebar 390, font 32 sp, warna/glow sama, simulasi pembaruan posisi setiap 50 ms.
- Tidak membandingkan APK SimpMusic yang terpasang pada HP pengguna. Versi aplikasi, penyedia terpilih, video ID, dan cache pada aplikasi tersebut belum dicocokkan.

## Hasil pemrosesan

50 pasangan pemanasan, kemudian 300 sampel untuk masing-masing cara. Urutan pengukuran ditukar setiap pasangan.

| Pemrosesan seluruh 33 baris | Median | P95 | Maksimum |
|---|---:|---:|---:|
| Parser rich-sync core asli dan pemetaan model | 0,056 ms | 0,099 ms | 0,252 ms |
| Pemetaan produksi Gratify dengan referensi dan perbaikan kata | 1,003 ms | 1,370 ms | 2,227 ms |

Selisih median sekitar **0,95 ms per respons** pada komputer ini. Tahap ini berjalan saat menerima lirik di jalur repository IO; tidak dijalankan ulang untuk tiap frame animasi. Pemrosesan referensi memakai teks synced/plain yang sudah berada dalam respons, sehingga tidak menambah permintaan jaringan.

Pengujian memastikan semua awal baris dan semua timestamp potongan lirik dalam respons tetap sama.

## Hasil render baris aktif

Tiga putaran per varian; 60 frame pemanasan dan 300 frame terukur setiap putaran. Urutan varian ditukar pada putaran kedua. Total 900 sampel per varian.

| Komponen | Median panggilan render | P95 | P99 | Maksimum |
|---|---:|---:|---:|---:|
| Baris aktif SimpMusic acuan, data asli | 1,343 ms | 2,528 ms | 3,329 ms | 5,777 ms |
| Baris aktif produksi Gratify, kata diperbaiki | 1,255 ms | 2,308 ms | 3,239 ms | 7,073 ms |

Tidak ada kenaikan beban yang konsisten pada tiga putaran ini. Selisih kecil antarvarian tidak cukup untuk menyimpulkan satu aplikasi selalu lebih cepat. Satu sampel maksimum Gratify lebih tinggi; hasil pengujian tidak menyatakan tidak ada lonjakan sama sekali.

Angka ini mengukur durasi panggilan render offscreen `ImageComposeScene` pada JVM. Pekerjaan coroutine di luar panggilan, jeda buatan, dan encoding PNG tidak termasuk. Angka tersebut **bukan FPS Android atau keseluruhan biaya frame**. Sampel CPU thread tersedia dalam data mentah, tetapi timer CPU Windows terkuantisasi sekitar 15,625 ms sehingga tidak layak ditafsirkan sebagai penggunaan CPU per frame atau baterai.

## Perbedaan teks yang direproduksi

Dengan respons yang sama, parser dan komponen SimpMusic versi acuan menghasilkan potongan `Su a tu ma lam A dam ber ce ri ta`. Gratify menghasilkan `Suatu malam Adam bercerita`, sambil mempertahankan 11 waktu suku kata.

Permintaan Better Lyrics dengan judul, artis, dan durasi lagu yang sama juga berhasil. Pada baris yang cocok, parser TTML core acuan menambahkan spasi setelah setiap span; parser Gratify mempertahankan batas kata dari dokumen TTML sehingga menghasilkan kata utuh.

Ini membuktikan perilaku versi kode dan input yang diuji. Ini tidak membuktikan bahwa APK SimpMusic pengguna menampilkan lirik terpisah. Penyebab perbedaan dengan pengalaman pengguna masih perlu dicocokkan menggunakan versi, penyedia, dan video ID yang dipakai pada HP.

Pratinjau kedua komponen pada waktu yang sama, dari pengujian komputer:

SimpMusic versi acuan:

![Komponen SimpMusic versi acuan](D:/Tan/Gratify/artifacts/lyrics-performance-2026-10-03/simpmusic-original-component.png)

Gratify setelah perbaikan:

![Komponen produksi Gratify](D:/Tan/Gratify/artifacts/lyrics-performance-2026-10-03/gratify-corrected.png)

## Validasi dan batas hasil

**27 pemeriksaan lulus**, tidak gagal atau dilewati: benchmark pemrosesan dan animasi, batas kata, render dua lebar layar, cache/fallback, serta 10 pemeriksaan player untuk loading, tab, seek, pause/resume, dan kontrol.

Bukti berada di `artifacts/lyrics-performance-2026-10-03/`: `processing.json`, `animation.json`, `render-samples.csv`, `validation.json`, dan `verification.log`. Respons lirik lengkap dan identitas kontributor tidak disimpan.

Tidak membuat atau memasang APK debug. APK universal lama masih memiliki SHA-256 `817fd0fa7c8f2ef97cd46886ab4d342224792b4066e6350790bf34d05c802126`. Pemeriksaan ini menambah alat uji saja; tidak mengubah kode produksi atau menambahkan pencarian referensi dari penyedia cadangan.

Pengujian ini tidak mencakup keseluruhan halaman dengan semua efek dan scrolling, decoding audio, GPU Android, penggunaan memori jangka panjang, baterai, atau perangkat fisik. Pengukuran tersebut menunggu pengguna meminta build dan pengujian perangkat.

## Pengukuran ulang setelah perbaikan kapitalisasi

Angka tabel di atas merupakan pengukuran awal. Setelah pemrosesan mempertahankan huruf asli dan memakai pola spasi yang dipakai ulang, pemrosesan penuh diukur kembali: median 1,047 ms, P95 2,270 ms, maksimum 3,351 ms. Hasil terbaru dan 43 pemeriksaan fungsi dicatat pada [laporan perbaikan akhir](D:/Tan/Gratify/docs/LYRICS_WORD_BOUNDARIES_FINAL_2026-10-03.md), dengan snapshot data di `artifacts/lyrics-boundaries-final-2026-10-03/processing.json`. Berkas `processing.json` di folder laporan ini juga diperbarui oleh benchmark manual; angka render sebelumnya tetap berasal dari tiga putaran pengukuran awal.
