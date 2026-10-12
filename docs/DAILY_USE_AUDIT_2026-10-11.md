# Audit kesiapan penggunaan harian Gratify — 11 Oktober 2026

**Kesimpulan: dapat dipakai untuk mencoba playback, tetapi belum direkomendasikan sebagai rilis stabil harian.** RAM pada workload ini tidak menunjukkan kebocoran progresif; ada bug nyata pada progress player di sesi warm serta ruang optimasi CPU. Tidak ditemukan crash/OOM/ANR pada bukti proses yang ditangkap. Ini penilaian versi Android pada satu ponsel, bukan sertifikasi semua perangkat atau versi desktop.

## Bukti paling penting

- **20 siklus** buka player → lirik → cover → tutup berhasil diverifikasi dari hierarchy UI, tanpa restart proses di antara siklus. Activity tetap 1; window sempat 3 segera setelah penutupan lalu kembali **1** selama idle.
- Total footprint termasuk swap pada baseline dan checkpoint siklus 5/10/15/20/idle terakhir: **190.4 → 200.2 → 201.8 → 202.6 → 196.4 → 182.7 MiB**. Setelah idle terakhir resident PSS **166.1 MiB**, total termasuk swap **182.7 MiB**. Penurunan terjadi pada PID yang sama, bukan karena restart. Ini mendukung pelepasan/plateau pada skenario ini, bukan pembuktian bebas Activity/native leak.
- Paused player **2,97% satu inti CPU**, lirik paused **3,74%**. Setelah penutupan dan idle, **0 frame** baru dalam 65,8 detik. Paused player/lirik masing-masing mencatat 19/30 frame pada window yang dimulai dekat transisi pause/tab; tidak ada render kontinu 60 FPS seperti temuan lama.
- Player playing **92,23% satu inti**; background playing **34,12%**. CPU mencakup semua thread proses. Angka tersebut tidak sama dengan persen seluruh CPU perangkat dan tidak mengukur konsumsi baterai.
- Fresh tests **643 total: 635 lulus, 0 gagal, 8 skip**. Assemble release dan lint release lulus; lint tetap memiliki **65 warning** dan **0 error/fatal**.

## Bug progress player — R2 / P2, terkonfirmasi pada proses warm

Saat lagu Keras Kepala dimainkan, UI terus menampilkan **00:54 / -04:31**, sementara MediaSession **PLAYING**, speed 1 dan error null, maju dari **200.259 ms (3:20)** ke **212.286 ms (3:32)**. Bar progress juga tetap di posisi yang sama. Screenshot diperiksa langsung; ini tidak hanya keterbatasan UI Automator. Play/pause, 20 kali buka-tutup, dan background/return tidak memulihkan nilai tersebut.

Bukti: [timer-verification.json](D:/Tan/Gratify/artifacts/daily-use-audit-20261011/timer-verification.json), [screenshot pertama](D:/Tan/Gratify/artifacts/daily-use-audit-20261011/timer-playing-1.png), [screenshot kedua](D:/Tan/Gratify/artifacts/daily-use-audit-20261011/timer-playing-2.png), dump MediaSession dan XML bernama `timer-playing-*`.

**Batas reproduksi:** PID 17126 sudah hidup sebelum audit dan kondisi stale tampak sejak awal. Pemicu awal belum diisolasi. Untuk kontrol, aplikasi kemudian di-force-stop satu kali dan dibuka kembali (PID 19327); lagu baru Mutiara menunjukkan UI **00:19 → 00:32**, MediaSession **17.904 → 32.926 ms**, dan progress bergerak. Kontrol tersebut memakai lagu berbeda; belum membuktikan restart akan memulihkan semua lagu/sesi. Bukti: [timer-cold-verification.json](D:/Tan/Gratify/artifacts/daily-use-audit-20261011/timer-cold-verification.json). Jangan menganggap restart sebagai perbaikan permanen.

Jalur yang perlu ditrace: `MediaServiceHandlerImpl.startProgressUpdate` (baris 720), distribusi `simpleMediaState` → `SharedViewModel.observeMediaState` (446), dan `AppleMusicPlayerContent` (88–92). Penyebab spesifik belum dipastikan. Prioritaskan regresi service stop/resume/Activity close-reopen dalam proses yang sama dan sesi playback panjang; test unit yang lulus saat ini tidak menangkap kondisi tersebut. Sinkronisasi lirik juga perlu diuji ulang terhadap posisi audio setelah perbaikannya.

## Pengukuran RAM dan CPU

| Skenario | Resident PSS MiB | Total incl. swap MiB | CPU satu inti | Window |
|---|---:|---:|---:|---:|
| Beranda, musik dijeda | 151.1–164.4 | 178.5–192.4 | 2.84% | 33.1 s |
| Player terbuka, musik berjalan | 206.6–209.2 | 225.0–227.8 | 92.23% | 33.7 s |
| Player terbuka, PAUSED terverifikasi | 200.8–209.0 | 219.2–227.4 | 2.97% | 33.0 s |
| Lirik terbuka, PAUSED terverifikasi | 206.5–216.9 | 224.3–234.4 | 3.74% | 32.9 s |
| Setelah 20 siklus, player ditutup | 166.1–175.7 | 182.7–192.3 | 2.00% | 65.8 s |
| Background, musik berjalan | 96.5–113.8 | 136.1–145.2 | 34.12% | 44.5 s |
| Background, musik dijeda | 93.0–102.8 | 136.8–137.9 | 6.56% | 70.0 s |

Seluruh window RAM/CPU di tabel memakai PID warm 17126 yang juga menunjukkan bug timeline. Angka menggambarkan workload sesi bermasalah yang benar-benar ditemui pengguna, bukan baseline CPU universal untuk sesi normal. Kontrol cold hanya menguji fungsi timer; profiling cold dengan timeline sehat belum diulang.

Firmware ponsel ini melaporkan `TOTAL PSS = jumlah kategori resident PSS + SwapPss`. Seluruh **73 snapshot unik** diverifikasi dari tabel meminfo mentah, toleransi pembulatan 8 KiB. Resident pada tabel adalah total dikurangi SwapPss; RSS tidak diperlakukan sebagai RAM privat. `memory-corrected.csv` dan `pss-validation.json` menyimpan perhitungan. Heap Java/native Alloc juga disimpan terpisah; penurunan resident akibat swap saja tidak dianggap bukti pelepasan objek.

Graphics player/lirik sekitar **99–104 MiB**, beranda sekitar **60 MiB**, background sekitar **14–18 MiB**. RAM graphics yang tinggi pada player merupakan penggunaan resource, bukan otomatis kebocoran. Cache Coil dibatasi **64 MiB RAM** dan **512 MiB disk** di `GratifyApplication.kt:129–138`; ini tidak membatasi seluruh heap aplikasi atau seluruh penyimpanan musik.

Player playing: **6/2.051 frame janky (0,29%)**, p95 **22 ms**. Persentase jank dari frame aktif bukan persentase waktu aplikasi lag. Percentile `4950 ms` yang dicetak gfxinfo ketika jumlah frame 0 merupakan sentinel; summary menuliskannya null dan tidak menganggapnya freeze 4,95 detik. CPU background masih layak diprofiling walaupun tidak ada frame baru.

## Temuan tambahan dan risiko yang masih perlu diuji

### R1 — P2: Player terbuka saat playback memakai 92.23% satu inti CPU

Status bukti: **runtime observation**.

Masih ada ruang optimasi CPU/render. Battery drain belum diukur dan penyebab belum diatribusikan tanpa trace.

Tindak lanjut: Ambil CPU/Perfetto trace untuk memisahkan animasi, blur, timeline, decoder/DSP dan preload; ulangi di HP RAM 4 GB.

### S1 — P2: Seluruh pesan yang dimuat dipertahankan hingga chat ditutup

Status bukti: **source-confirmed scalability risk, not runtime leak**. Sumber: [composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/FriendsSocialViewModel.kt:205](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/FriendsSocialViewModel.kt:205).

loadLatest menggabung, distinct dan sort seluruh pesan; loadOlder di baris 222 menambah halaman tanpa batas ukuran. Polling tiap 5 detik; closeChat membersihkan pesan dan LifecycleStartEffect menghentikan polling saat stop.

Tindak lanjut: Gunakan window pesan/paging dan merge berdasarkan ID; uji fixture 5k/50k pesan. Chat pengguna tidak diuji pada audit ini.

### S2 — P2: Polling follower/social tiap 60 detik tanpa gate foreground

Status bukti: **source-confirmed background workload, not battery drain proof**. Sumber: [composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SharedViewModel.kt:715](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SharedViewModel.kt:715).

SharedViewModel singleton memakai viewModelScope. FollowerNotificationSync mengambil sampai 5000 follower, halaman 100, dan memeriksa insertNotificationOnce setiap follower, timeout 25 detik. Notification polling mempunyai kegunaan produk, tetapi belum memakai delta/backoff berbasis perubahan.

Tindak lanjut: Gunakan realtime atau cursor delta, backoff jaringan, dan jadwal background sesuai kebutuhan notifikasi; ukur wakelock/network/battery pada soak lebih lama.

### S3 — P2: Dua player preload dengan buffer 4x durasi default

Status bukti: **source-confirmed memory budget risk, not unbounded leak**. Sumber: [core/media/media3/src/main/java/com/tan/media3/exoplayer/CrossfadeExoPlayerAdapter.kt:439](D:/Tan/Gratify/core/media/media3/src/main/java/com/tan/media3/exoplayer/CrossfadeExoPlayerAdapter.kt:439).

maxPrecacheCount=2, precacheEnabled=true. Semua player dibuat dengan min/max buffer 4x durasi default. Pruning, cleanup gagal preload dan stop cleanup tersedia. Durasi buffer 4x tidak berarti RAM otomatis 4x.

Tindak lanjut: Tetapkan budget preload berdasarkan RAM/perangkat, bedakan current/next player, dan uji skip/shuffle/crossfade/offline stress.

### S4 — P2: Pembacaan DataStore sinkron di jalur UI/startup

Status bukti: **source-confirmed blocking path, not observed ANR**. Sumber: [composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SharedViewModel.kt:293](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SharedViewModel.kt:293).

initGuideFlags memakai runBlocking; getLocation (623-625), getString/putString dan shouldStopMusicService juga sinkron. MainActivity mengakses shared view model dalam onCreate. I/O lambat berpotensi menahan main thread; ANR belum ditemukan pada audit ini.

Tindak lanjut: Expose state asynchronous dengan nilai awal; pindahkan I/O keluar dari main thread dan uji startup/storage lambat.

### S5 — P3: Map deduplikasi notifikasi download tidak menangani penghapusan download

Status bukti: **small source-confirmed retention risk, not OOM proof**. Sumber: [core/media/media3/src/main/java/com/tan/media3/service/download/MusicDownloadService.kt:54](D:/Tan/Gratify/core/media/media3/src/main/java/com/tan/media3/service/download/MusicDownloadService.kt:54).

terminalStates menyimpan request ID completed/failed. Entry dihapus saat nonterminal, tetapi listener tidak override onDownloadRemoved. Siklus download-hapus dengan ID berbeda dapat menambah map sepanjang proses hidup. Nilai kecil, bukan bukti OOM.

Tindak lanjut: Hapus entry pada onDownloadRemoved dan beri batas atau kebijakan kedaluwarsa. Download stress tidak dilakukan.


## Perangkat, artifact, dan verifikasi

- Ponsel **vivo I2218**, Android 16/API 36, arm64, MemTotal 11.565.408 KiB (~11,0 GiB OS, kelas 12 GB), viewport 1080×2400. Sampel workload warm 11:13:44 WIB–11:27:19 WIB; ada probe tambahan timer dan kontrol cold setelahnya. Bukan uji 24 jam. USB charging, status thermal LIGHT/1; kondisi ini membatasi interpretasi baterai/panas.
- Package **com.tan.gratify**, **2.1.0 / versionCode 61**, release minified diagnostik, sertifikat **Android Debug**. APK installed SHA-256 `1b09c1bbdcda285355624b7f6da7214308a84fc36cf8951b2df1bf91e5433ea5`, cocok dengan artifact perbaikan sebelumnya. **Bukan APK final untuk distribusi.**
- Seluruh **2.366 entry ZIP di luar metadata signature** identik dengan output release arm64 terbaru; assembleRelease UP-TO-DATE mengonfirmasi gate source saat ini. Bukti `payload-match.json`, `installed-identity.json`, `installed-signature.txt`. Signature produksi, OAuth/FCM berbasis sertifikat, upgrade dan update channel final belum diuji di sini.
- Working tree memang sudah banyak berubah sebelum audit, HEAD `2d774fbe2d8f42eec6a08d1e4081f7be5e919dc8`. Audit menambahkan probe, bukti dan laporan; tidak memperbaiki source aplikasi atau melakukan reset/stash/commit.
- Perbaikan sampled image decode, weak Activity provider dan gate animasi crossfade terdapat pada source sekarang. Enam source guard yang tersedia lulus (4 retensi Activity + 2 budget animasi). Itu bukan heap/GC test atau retest upload gambar besar di ponsel.
- Log crash PID warm/cold kosong; exit-info warm hanya mencatat force-stop/update lama. Force-stop kontrol audit disengaja. Tidak mengekstrapolasi log terbatas ini menjadi jaminan crash-free.

Pengujian pertama menjalankan lintRelease, composeApp/data/domain/lyricsService jvmTest dan media3 testDebugUnitTest; build sukses, test tasks saat itu UP-TO-DATE. Kemudian seluruh **task test** dipaksa fresh lewat `fresh-tests.init.gradle` (compiled outputs tetap digunakan), selesai exit 0 dalam **6 menit 36 detik**, dengan hasil berikut:

| Modul | Total | Lulus | Gagal | Skip |
|---|---:|---:|---:|---:|
| composeApp | 458 | 456 | 0 | 2 |
| data | 125 | 120 | 0 | 5 |
| domain | 13 | 13 | 0 | 0 |
| lyricsService | 23 | 22 | 0 | 1 |
| media3 | 24 | 24 | 0 | 0 |

XML hasil fresh disalin ke `artifacts/daily-use-audit-20261011/test-results`; rincian test yang skip ada di XML. Skip tidak dihitung sebagai pass. Assemble release terpisah exit 0, output UP-TO-DATE. Lint warning belum seluruhnya ditriase sebagai bug harian/keamanan.

## Koreksi pengukuran dan batas audit

- Window awal `paused-player` **dikecualikan**: keadaan berubah NONE → PLAYING dan pengukuran pertama sempat tumpang tindih dengan aksi play. Itu bukan data paused. `playing-player` mencakup buffering/startup; angka steady memakai `playing-warm`. File asli dipertahankan.
- Percobaan harness siklus pertama berhenti karena tab Lyrics tersimpan dari sesi sebelumnya. Harness diperbaiki untuk memeriksa tab yang aktif, lalu 20 siklus penuh berhasil. Label baseline sempat dipakai ulang; summary menggunakan sampel terakhir yang cocok dengan dump mentah dan mencatat deduplikasi. Tidak mengubah data untuk menyembunyikan leak.
- `timer-recheck-paused` ternyata masih layar Cari setelah transisi launch; tidak digunakan sebagai bukti timer. Bukti timer final memakai node miniplayer yang diverifikasi dan screenshot player langsung.
- Pengukuran baru tidak membuat perbandingan A/B dengan angka lama seperti 7,2% background: state playback, cache, swap dan thermal harus sepadan sebelum menghitung delta.
- Tidak clear data, uninstall, install APK, mengubah akun/playlist/profil, mengirim pesan, atau mengubah setting global jaringan/thermal. Playback normal dapat menulis history/now-playing/sync pada akun. Force-stop kontrol dan pemilihan lagu baru dilakukan; akhir audit playback **PAUSED**, layar Home Android, lagu terakhir Mutiara. Data aplikasi tidak dihapus.
- Belum diuji: RAM 2–4 GB, Android versi lain, long soak/battery tanpa charger, screen-off/Doze, heap roots/GC setelah Activity recreation, chat 5k/50k, banyak download, foto besar aktual, offline/recovery network, crossfade/shuffle/skip stress, login/account isolation, keamanan backend, dan runtime desktop.

Grafik RAM yang melandai tidak cukup untuk meniadakan semua leak. Pemeriksaan lanjutan perlu heap dump retained references dan workload panjang; lihat [panduan heap dump Android](https://developer.android.com/studio/profile/capture-heap-dump) dan [interpretasi dumpsys](https://developer.android.com/tools/dumpsys).

## Urutan tindak lanjut

1. Isolasi dan perbaiki progress player pada sesi warm; verifikasi close/reopen service, pause/resume dan background/return tanpa restart proses.
2. Trace CPU player/background dengan fitur audio yang sama, lalu uji perangkat RAM 4 GB dan baterai tanpa charger.
3. Bounded paging chat, polling incremental/backoff, budget preload dan jalur DataStore asynchronous.
4. Jalankan soak 2–4 jam dan lifecycle heap test, kemudian verifikasi exact APK signed produksi dan upgrade yang akan dikirim ke pengguna.

Direktori bukti: [daily-use-audit-20261011](D:/Tan/Gratify/artifacts/daily-use-audit-20261011). Ringkasan terstruktur: `performance-summary.json`, `findings.json`, `test-summary.json`, `lint-summary.json`, `timer-verification.json`, `timer-cold-verification.json`, `payload-match.json`.
