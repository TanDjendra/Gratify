# Laporan optimasi pemutar dan lirik Gratify

Tanggal: 2 Oktober 2026. Gaya Apple Music dipertahankan.

## Hasil utama

Pada pengujian emulator dengan kondisi yang sama, CPU lirik per baris
berubah dari 11,51% menjadi
8,19% (turun 28,8%);
lirik per kata dari 26,48% menjadi
22,99% (turun 13,2%).
Saat pause, ketiga putaran menghasilkan nol frame baru selama total 36 detik,
dengan CPU proses rata-rata 0,22%.

Persentase CPU merupakan **setara satu inti**, bukan penggunaan seluruh
CPU empat inti. Hasil ini adalah pengamatan pada build debug dan emulator;
pengaruh terhadap baterai dan HP fisik belum diukur.

## Perubahan yang diterapkan

- Pembaruan waktu ditempatkan dekat kontrol progress dan baris lirik aktif,
  sehingga bagian statis tidak ikut menerima pembaruan clock setiap 100 ms.
- Baris tidak aktif, lirik biasa, dan lirik tanpa timing tidak menerima
  timestamp baru untuk animasi per kata.
- Lirik per kata menggunakan satu animasi waktu untuk baris aktif.
  Pergantian kata tidak membuat ulang animasi waktunya.
- Sorotan putih digambar memakai hasil tata teks yang sudah ada. Tidak ada
  Text kedua yang perlu dibuat dan diukur ulang untuk setiap sorotan.
- Blur/opacity baris memakai pembacaan animasi pada graphics layer,
  sehingga animasi fokus tidak mengharuskan teks dikomposisi/diukur ulang.
- Latar artwork dipisahkan menjadi layer statis; objek gradasi memakai
  drawWithCache. Parsing teks dan style disimpan selama datanya sama.
- Timing, seek maju/mundur, pause/resume, terjemahan, tata letak, kontrol,
  dan gaya Apple Music tetap memakai kontrak pemutar Gratify.

## Perbandingan CPU sebelum dan sesudah

Rata-rata gabungan tiga putaran, masing-masing 12 detik per skenario:

| Kondisi | Sebelum, satu inti | Sesudah, satu inti | Perubahan teramati |
| --- | ---: | ---: | ---: |
| Now Playing | 7,06% | 6,15% | Turun 12,9% |
| Lirik per baris | 11,51% | 8,19% | Turun 28,8% |
| Lirik per kata | 26,48% | 22,99% | Turun 13,2% |
| Lirik pause | 0,21% | 0,22% | Sekitar 0,2%; nol frame |
| Lirik dengan seek mundur | 23,11% | 22,21% | Turun 3,9% |
| Lirik tanpa timing | 6,95% | 5,68% | Turun 18,3% |

Angka relatif di atas bukan estimasi statistik untuk seluruh perangkat.
Pengujian tidak melakukan decode audio, pemutaran video, Canvas bergerak,
atau akses jaringan. CPU yang tercatat mencakup proses aplikasi uji.
Selisih CPU pause 0,01 poin persentase terlalu kecil untuk ditafsirkan
sebagai perubahan performa yang berarti.

## Frame dan kelancaran

P95 adalah waktu total frame di bawahnya terdapat 95% sampel. Persentase
deadline miss dihitung memakai TOTAL_DURATION >= DEADLINE menurut
[Android FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics).

| Kondisi | P95 sebelum, ms | P95 sesudah, ms | Deadline miss sebelum, % | Deadline miss sesudah, % |
| --- | ---: | ---: | ---: | ---: |
| Now Playing | 19,09 | 18,83 | 100,00 | 100,00 |
| Lirik per baris | 19,66 | 18,87 | 65,82 | 64,85 |
| Lirik per kata | 20,14 | 19,78 | 1,46 | 1,46 |
| Lirik pause | — | — | — | — |
| Lirik dengan seek mundur | 18,96 | 18,91 | 1,03 | 0,89 |
| Lirik tanpa timing | 18,79 | 18,76 | 100,00 | 100,00 |

Tidak ada frame baru saat pause, sehingga P95/rate-nya tidak berlaku.
Deadline emulator berganti sekitar 16,67/33,33 ms tergantung cadence.
Persentase ini tidak sama dengan FPS atau persentase gambar yang hilang
di layar. Baseline Now Playing juga menunjukkan banyak deadline miss.
Hasil tersebut harus diperiksa lagi pada HP sebelum menyatakan semua
perangkat akan terasa mulus. Tidak ada callback metrics terbuang.

## Pembanding renderer lama

Renderer lama dibiarkan sebagai kontrol pada kedua pengukuran. Semua
renderer dalam tabel ini memakai kotak fullscreen dan latar datar yang sama.
Perubahan pada CPU kontrol dapat menunjukkan variasi runtime/host; selisih
kecil pada hasil utama perlu dibaca dengan konteks ini.

| Renderer terpisah | CPU sebelum | CPU sesudah | Deadline miss sebelum, % | Deadline miss sesudah, % |
| --- | ---: | ---: | ---: | ---: |
| Renderer lama, per baris | 3,32% | 3,20% | 4,32 | 3,31 |
| Apple Music, per baris | 5,55% | 3,54% | 7,69 | 7,69 |
| Renderer lama, per kata | 20,91% | 22,52% | 0,37 | 0,37 |
| Apple Music, per kata | 20,71% | 19,30% | 1,46 | 1,27 |

## Memori, pengujian fungsi, dan batasan

- PSS seluruh proses pada akhir tiap window: sebelum
  202,2–225,7 MiB; sesudah
  201,2–220,9 MiB. Ini bukan memori
  lirik saja, bukan memori puncak, dan tidak membuktikan kebocoran memori.
- 55 tes JVM lulus, 0 kegagalan dan 0 error.
  Tes tambahan memeriksa seek dalam kata yang sama, resume, pause setelah
  resume, perpindahan kata, dan seek kembali melewati batas kata melalui
  hasil gambar renderer produksi. Kontrol, tab, queue, teks besar, layar
  mendatar, dan kondisi lirik kosong juga tetap diperiksa.
- assembleDebug dan lintDebug berhasil. Lint: 0 error, 116 warning.
- Tidak ditemukan crash selama 30 window pengukuran versi akhir.
- HP fisik, Android lama, suhu, baterai, playback audio sebenarnya,
  pengujian jangka panjang, dan APK rilis dengan optimasi belum diuji.
- Paket yang dibangun merupakan APK debug untuk pengujian, bukan paket
  final yang dibagikan kepada pengguna.

## Cara pengukuran

Emulator Android 17/API 37, x86_64, page size
16 KB, empat inti, layar 1080×2400/density 420. GPU host NVIDIA RTX 5050
Laptop melalui Android Emulator OpenGL ES Translator. Sampul fixture
1024×1024, 80 baris dengan delapan kata per baris, timeline 100 ms.

Masing-masing dari 10 skenario dijalankan tiga kali: pemanasan 3 detik,
pengukuran 12 detik. Urutan dibalik pada putaran kedua. Screenshot empat
skenario hanya diambil pada putaran pertama, sama seperti baseline.
Semua metadata penting metode/lingkungan diverifikasi tetap sama.
Host uji memanggil AppleMusicPlayerContent dan LyricsView produksi;
alur akun, navigasi, dialog dan sesi playback aplikasi lengkap tidak
termasuk dalam pengukuran ini.

Percobaan awal menurunkan CPU tetapi menaikkan deadline miss lirik per kata
dari 1,46% menjadi
4,30%. Hasilnya tetap disimpan di
artifacts/apple-performance-optimized. Versi akhir memakai teks yang tetap
terpasang dan clock per baris; hasil akhirnya adalah tabel di atas.

Panduan implementasi:
[defer state reads](https://developer.android.com/develop/ui/compose/performance/bestpractices),
[graphics modifiers dan drawWithCache](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers),
[menggambar TextLayoutResult](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/drawscope/DrawScope).

## Bukti

- Baseline: artifacts/apple-performance/raw-results.json dan summary.json.
- Versi akhir: artifacts/apple-performance-final/raw-results.json dan summary.json.
- Metadata: artifacts/apple-performance-final/environment.json.
- Log build/tes: artifacts/apple-performance-final/verification.log.
- Log runtime: artifacts/apple-performance-final/runtime.log.
- Screenshot Android: 1-now_playing.png, 1-lyrics_line.png,
  1-lyrics_rich.png, 1-lyrics_paused.png dalam folder versi akhir.
- Snapshot sumber sebelum perubahan tersedia di
  artifacts/apple-performance-optimized/before-source.
- SHA-256 APK uji akhir: C53A67F4E84D7A88A4571FC346DED15494114A66DB5EBCD162AB21C550E7AB38.

Pengulangan setelah assembleDebug:

```text
python scripts/measure-player-performance.py --serial emulator-5554 --output artifacts/apple-performance-final
```

Alat perbandingan deadline/GPU memerlukan Android 12/API 31 atau lebih baru.
Pada perangkat fisik, script memasang APK debug dengan application ID
com.tan.gratify.dev dan mengganti instalasi debug yang sama.
