# Pengukuran performa pemutar dan lirik

Pengujian selesai pada 2 Oktober 2026, 19:33 WIB. Sepuluh skenario dijalankan
tiga kali menggunakan komponen produksi Gratify pada emulator Android.
Tidak ditemukan crash selama pengujian. Animasi lirik berhenti saat pause.
Ada keterlambatan frame pada layar pemutar; hasil ini belum membuktikan
kelancaran APK rilis pada HP fisik.

## Lingkungan dan metode

- Android 17/API 37, emulator x86_64 dengan page size 16 KB, 4 inti CPU,
  memori sistem sekitar 3,8 GiB, layar 1080×2400 dan density 420.
- GPU host: NVIDIA GeForce RTX 5050 Laptop, melalui Android Emulator OpenGL
  ES Translator. Hasil tidak mewakili GPU HP kelas rendah.
- APK debug Gratify yang berisi perubahan Apple Music terbaru. Aktivitas
  `PlayerPerformanceActivity` hanya tersedia pada build debug.
- Lagu dan sampul merupakan fixture lokal: sampul 1024×1024, 80 baris lirik,
  delapan kata tiap baris, durasi 320 detik. Tidak ada permintaan lirik jaringan.
- Timeline sintetis diperbarui setiap 100 ms, sesuai interval progress di
  `MediaServiceHandlerImpl.startProgressUpdate`. Kontrol UI membaca StateFlow
  yang sama seperti komponen produksi. Decode audio, ExoPlayer, buffering,
  video, Canvas bergerak, dan akses jaringan tidak termasuk pengukuran ini.
- Setiap skenario memakai 3 detik pemanasan, kemudian 12 detik pengukuran.
  Urutan skenario dibalik pada putaran kedua untuk mengurangi bias urutan.
  Total 30 window pengukuran, sekitar 7,5 menit ditambah startup.
- Frame diambil melalui `Window.OnFrameMetricsAvailableListener`. Sampel
  first-draw diabaikan; timestamp vsync membatasi sampel pada window ukur.
  Tidak ada callback metrics yang terbuang pada seluruh window.
- CPU memakai selisih `Process.getElapsedCpuTime` dibagi durasi window.
  Persentase merupakan **setara satu inti**, bukan persentase seluruh CPU
  empat inti. Aktivitas latar aplikasi turut tercatat.
- Memori adalah PSS seluruh proses dari `Debug.MemoryInfo`, diambil setelah
  window selesai. Ini bukan memori lirik saja atau penggunaan memori puncak.
- Screenshot hanya diambil pada empat skenario putaran pertama. Putaran
  kedua dan ketiga tidak menjalankan screenshot.

Frame dianggap melewati deadline bila `TOTAL_DURATION >= DEADLINE` menurut
[kontrak Android FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics).
Deadline bervariasi antara sekitar 16,67 dan 33,33 ms pada emulator ini.
Angka tersebut bukan persentase frame yang dibuang oleh layar atau pengukuran
FPS. Layar yang hanya berubah mengikuti timeline memang menghasilkan lebih
sedikit frame daripada animasi per kata yang terus berjalan.

## Hasil gabungan tiga putaran

P95 berarti 95% sampel memiliki waktu total frame di bawah angka tersebut.

| Skenario komponen pemutar lengkap | CPU rata-rata, satu inti | Frame terukur | P95 total frame | Melewati deadline | PSS proses, rentang |
| --- | ---: | ---: | ---: | ---: | ---: |
| Now Playing | 7,06% | 355 | 19,09 ms | 100,00% | 202,2–218,6 MiB |
| Lirik per baris | 11,51% | 512 | 19,66 ms | 65,82% | 210,4–220,8 MiB |
| Lirik per kata | 26,48% | 2126 | 20,14 ms | 1,46% | 219,3–222,4 MiB |
| Lirik per kata, pause | 0,21% | 0 | Tidak ada frame baru | Tidak berlaku | 211,6–224,8 MiB |
| Lirik per kata, seek mundur tiap 2 detik | 23,11% | 2135 | 18,96 ms | 1,03% | 211,8–225,1 MiB |
| Lirik tanpa timing, lagu berjalan | 6,95% | 356 | 18,79 ms | 100,00% | 211,4–221,5 MiB |

Angka deadline yang tinggi pada skenario tanpa animasi kontinu juga terlihat
pada baseline Now Playing. Karena deadline/cadence berbeda, jangan menafsirkan
kolom ini sebagai bukti bahwa lirik per kata lebih ringan daripada lirik biasa.
CPU menunjukkan beban lirik per kata lebih besar. Beberapa frame memiliki
waktu jauh lebih tinggi daripada P95, termasuk maksimum 85,77 ms pada lirik
per baris; nilai per putaran dan semua sampel tersedia pada data mentah.

## Perbandingan renderer lirik dengan tampilan lama

Keempat kasus berikut memakai LyricsView produksi dalam kotak fullscreen
dan latar datar yang sama. Transport dan background pemutar tidak disertakan.
Tata letak/ukuran huruf merupakan bagian masing-masing renderer; ini bukan
perbandingan yang hanya mengaktifkan satu efek blur.

| Renderer terpisah | CPU rata-rata, satu inti | P95 total frame | Melewati deadline |
| --- | ---: | ---: | ---: |
| Lama, per baris | 3,32% | 18,64 ms | 4,32% |
| Apple Music, per baris | 5,55% | 19,04 ms | 7,69% |
| Lama, per kata | 20,91% | 18,87 ms | 0,37% |
| Apple Music, per kata | 20,71% | 19,07 ms | 1,46% |

Pada lirik per baris, Apple Music menambah sekitar 2,23 poin persentase
CPU setara satu inti. Pada lirik per kata, selisih CPU rata-rata antara
renderer terlalu kecil untuk menyatakan satu renderer lebih efisien.
Apple Music mencatat lebih banyak deadline miss pada kedua perbandingan.

Putaran pertama lebih berat karena runtime belum sepenuhnya hangat:
CPU Now Playing 10,89% lalu 4,87%/5,41%; lirik per kata 31,31% lalu
23,16%/24,98%. Tiga putaran ini adalah diagnostik, bukan estimasi statistik
untuk seluruh perangkat.

## Kesimpulan dan batasan

- Pause menghentikan render animasi pada ketiga putaran: nol frame baru
  selama total 36 detik pengukuran, dengan CPU proses sekitar 0,21%.
- Lirik per kata membutuhkan lebih banyak CPU daripada lirik per baris
  pada tampilan pemutar lengkap. Gaya Apple Music menambah beban renderer
  per baris dibandingkan renderer lama.
- Ada keterlambatan frame; terutama perbedaan deadline/cadence pada layar
  lengkap memerlukan pemeriksaan lanjutan pada HP sebelum menilai kualitas
  gerak yang dirasakan pengguna.
- Seluruh sampel PSS berada sekitar 202,2–225,7 MiB. Tes singkat ini tidak
  membuktikan ada/tidaknya kebocoran memori jangka panjang.
- Konsumsi baterai, suhu, pengalaman playback audio sebenarnya, perangkat
  Android lama, HP kelas rendah, serta APK rilis dengan optimasi belum diuji.
- Tidak ada optimasi tampilan produksi yang diterapkan melalui pengujian ini.
  Kandidat tindak lanjut untuk persetujuan pengguna: mengurangi pembaruan
  baris yang tidak aktif dan memeriksa biaya background/blur, kemudian
  membandingkan hasilnya pada perangkat yang sama.

## Bukti dan pengulangan

- Lingkungan: `artifacts/apple-performance/environment.json`.
- Data lengkap: `artifacts/apple-performance/raw-results.json`.
- Ringkasan: `artifacts/apple-performance/summary.json`.
- Log runtime: `artifacts/apple-performance/runtime.log`.
- Log build: `artifacts/apple-performance/build.log` (assembleDebug berhasil).
- Screenshot komponen Android: `1-now_playing.png`, `1-lyrics_line.png`,
  `1-lyrics_rich.png`, dan `1-lyrics_paused.png` dalam folder yang sama.
- Gfxinfo dan meminfo pada akhir pengujian tersedia sebagai bukti tambahan.

APK uji x86_64 SHA-256:
`063B87233B969A07A46E512E025F192E835015DB2E1335FA724BFD1090A2FBEC`.
APK final-refresh yang dibuat sebelumnya bukan paket pengujian ini.

Setelah assembleDebug dan emulator/perangkat tersedia, jalankan:

```text
python scripts/measure-player-performance.py --serial emulator-5554
```

Script membaca ABI perangkat, memasang APK debug yang sesuai, menjalankan
probe, dan menyimpan data. Pada HP fisik, instalasi mengganti Gratify debug
dengan application ID yang sama; hasil perlu dibandingkan dengan konfigurasi
uji yang dicatat script.
