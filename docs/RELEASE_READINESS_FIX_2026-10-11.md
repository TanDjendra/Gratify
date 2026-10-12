# Gratify 2.1.1 — perbaikan kesiapan rilis

Audit dan perbaikan 11 Oktober 2026. Kandidat `com.tan.gratify`, versi 2.1.1, kode 62, kanal full. APK belum diterbitkan. Bukti disimpan di `artifacts/release-readiness-fix-20261011/`; audit awal tetap tersedia di `docs/DAILY_USE_AUDIT_2026-10-11.md`.

## Keputusan kandidat

Build bertanda tangan tersedia untuk evaluasi dan beta terbatas. Perbaikan timer, lifecycle, buffer dan batas koleksi telah diverifikasi, tetapi kandidat belum memenuhi dasar untuk klaim rilis stabil pada seluruh perangkat. Pengukuran pemutar aktif masih menunjukkan 66,05% frame janky, belum ada uji pemutaran pada ponsel 2–4 GB nyata, dan kunci baru tidak menyediakan pembaruan langsung dari APK publik lama yang diperiksa. Pengujian perangkat akhir di bawah menentukan batas bukti RAM dan regresi pemutaran.

APK universal terakhir: `artifacts/release-readiness-fix-20261011/release-final/Gratify.apk`, SHA-256 `958031d41ed63f1df61eab1a645bb5a258edc05b2031d24601280bc3655f1089`. Alternatif arm64: `Gratify-full-2.1.1-arm64-v8a-NEW-KEY.apk`, SHA-256 `007ebddcfdbd32a2b5f45336c1890c880def5c9559fae1772a808a71506a5cb9`. Metadata full versi 2.1.1/kode 62 tersedia di folder `release-final/` yang sama. Folder hasil antara bukan APK akhir.

## Masalah yang ditemukan dan diperbaiki

- Timer pemutar dapat berhenti setelah pause/resume atau penghentian layanan, sementara MediaSession dan audio terus berjalan. Pada baseline, layar tetap 00:37 ketika posisi audio berpindah dari 43.578 ke 79.622 ms. Pembaruan posisi sekarang memiliki satu job yang dapat dimulai kembali; transisi READY/PLAYING/PAUSED memastikan job aktif, termasuk saat state tidak berubah. Loop juga dihidupkan kembali setelah pertukaran pemain crossfade.
- Adapter pemutar dan handler layanan sebelumnya memakai scope bersama. Keduanya kini memiliki child SupervisorJob, sehingga pelepasan komponen membatalkan pekerjaannya sendiri tanpa membatalkan scope induk yang masih dibutuhkan.
- Buffer setiap pemain sebelumnya memakai durasi beberapa kali default. Target sekarang 15–45 detik; perangkat low-RAM atau memoryClass <=192 MiB memakai satu preload dan target allocator 4 MiB per pemain, perangkat lain dua preload dan 8 MiB. Ini bukan batas keras seluruh RAM aplikasi: codec, sampul, GPU, cache, dan pemain transisi memiliki penggunaan tambahan.
- Jalur audio PCM ketika filter crossfade dimatikan sebelumnya menyalin byte satu per satu. Sekarang menggunakan penyalinan buffer sekaligus. Tes memastikan rentang PCM dan penggunaan kembali buffer tetap menghasilkan byte identik.
- Polling progres dikurangi dari 100 menjadi 200 ms. Discord RPC tidak lagi dibangun/dibaca saat tidak aktif; pembaruan posisi RPC dilakukan tiap lima detik, dengan perubahan state/lagu tetap langsung dikirim.
- Riwayat status terminal unduhan dibatasi 256 ID; reset/retry dan penghapusan tetap bekerja. Daftar pesan chat dibatasi 500 pesan tanpa menghapus riwayat di server. Saat menelusuri pesan lama, polling menjaga posisi halaman; tombol kembali ke pesan terbaru dan pemuatan sesudah kirim mencegah pengguna terkunci di halaman lama.
- Polling notifikasi sosial mengikuti lifecycle foreground. Startup preference/migrasi locale tidak lagi memakai pembacaan DataStore yang memblokir main thread pada jalur yang diubah.
- Pemutar utama mengumpulkan timeline sendiri. Presenter luar kini hanya mengumpulkannya untuk PiP/video; pembaruan posisi tidak lagi menggerakkan kontrol legacy tersembunyi. Permintaan gambar sampul, background dan halaman antrean digunakan kembali selama URL/konfigurasinya sama. Nilai slider diturunkan langsung dari timeline ketika tidak diseret, tanpa komposisi tambahan untuk menyinkronkan state. Callback kontrol digunakan kembali. Lapisan backdrop untuk tombol khusus desktop kini hanya dibuat di desktop, sehingga Android tidak merekam lapisan satu layar yang tidak dipakai.
- Pengukuran frame masih menunjukkan beban tinggi setelah langkah di atas. Pengumpulan timeline Apple Music kemudian dipisahkan dari pembacaan nilainya: state pemutar digunakan kembali, sedangkan getter yang dibaca kontrol waktu/slider/lirik mengambil snapshot Compose terbaru. Pembaruan posisi tidak lagi membuat state baru untuk seluruh artwork, backdrop dan antrean. Indeks lirik diturunkan dari timestamp yang sudah diparse dan hanya perlu menyusun ulang konsumennya. Pemakai renderer lain yang memberikan snapshot biasa tetap memakai nilai konstruktor semula.
- Bar progres kini menggambar bagian aktif dalam geometri tetap, menggantikan child yang lebarnya berubah pada tiap posisi. Tampilan, target sentuh, drag dan callback accessibility tetap memakai Slider. Miniplayer ponsel menghentikan komposisi/koleksi progres ketika tertutup pemutar penuh; ketika pemutar penuh ditutup, koleksi dimulai lagi dari nilai StateFlow terkini. Miniplayer tablet yang terlihat bersama panel pemutar tetap aktif.
- Persiapan metadata rilis kini menerima format namespace XML dari Build Tools 37 sambil tetap menolak kanal FOSS yang diganti nama menjadi full. Penandatangan shell menerima jalur keystore dari lingkungan dan meneruskan password melalui lingkungan, tanpa password di argumen proses.

## Verifikasi build dan pengujian

Suite penuh dijalankan ulang dan hasil akhirnya digabung dengan pengujian modul media serta Compose yang terkena perubahan: 655 kasus unik, 647 lulus, 8 dilewati, 0 gagal. Pengulangan empat suite UI setelah isolasi renderer terakhir lulus 27 kasus, termasuk tes baru yang memastikan waktu berubah 00:02 → 00:12 → 00:00 dan seek melalui accessibility memakai nilai slider terkini. Tes lain mencakup loop posisi yang dapat dimulai ulang, scope induk tetap hidup, anggaran buffer, 10.000 status unduhan, 50.000 pesan, penelusuran pesan lama, dan integritas PCM. Delapan skip berasal dari suite yang sudah ada; bukan delapan kasus yang dianggap lulus.

Hasil pengujian Python persiapan rilis: 11 lulus. Enam pemeriksaan struktur untuk retensi Activity dan anggaran animasi juga lulus. Build Android minified release dan lint diverifikasi kembali setelah perubahan renderer. Lint memiliki 0 error dan 65 warning; warning tersebut belum semuanya dibereskan oleh audit performa ini.

Satu pengulangan tambahan seluruh paket tes UI desktop terhenti pada `PlaylistBuilderRenderTest` yang menunggu dispatcher Swing; worker uji tersebut dihentikan dan run itu tidak dihitung sebagai lulus. Suite penuh sebelumnya dan pengulangan akhir empat suite Compose yang terkena perubahan lulus. Log terbaru `build-draw-and-hidden-player-verified.log` mencatat BUILD SUCCESSFUL dalam 8 menit 22 detik, termasuk assembleRelease, lintRelease dan pengujian Compose tersebut. Percobaan sebelumnya gagal mengompilasi helper tes baru karena memanggil fungsi Compose di luar composition; helper tes sudah diperbaiki sebelum run yang berhasil. Build bar progres pertama dihentikan untuk memasukkan penghentian progres miniplayer tersembunyi; build yang dihentikan tidak dihitung lulus.

## Tanda tangan dan distribusi

Keystore baru ditemukan di `D:/Tan/script/Gratify/gratify.jks` dan berhasil dibuka dengan kredensial lokal. Sertifikat SHA-256: `efefe1b8bf7c6502aeeb60db9a45b430dc50040e11c6f76eb22e2308a51c316a`.

APK publik v2.1.0 yang diperiksa memakai sertifikat `abaa03408221a11ec378893bce65c95af6bc2b5425a6117507590487ecd942c2`. APK kunci baru dapat dipasang baru, tetapi tidak dapat memperbarui instalasi dengan sertifikat lama itu secara langsung. Tidak dibuat delta lintas kunci, tidak diunggah rilis, dan tidak dihapus data pengguna untuk mengatasi perbedaan tanda tangan.

Perangkat pemilik sebelumnya menjalankan APK diagnostik bertanda tangan Android Debug. Pengujian pembaruan pada perangkat itu memakai APK diagnostik baru agar data tetap ada. APK produksi ditandatangani keystore baru; perbandingan isi ZIP memastikan semua payload selain tanda tangan sama dengan APK diagnostik arm64. Validasi tambahan memeriksa zipalign 16 KB dan segmen ELF pustaka native 64-bit. Mapping R8 disimpan untuk investigasi crash; upload mapping Sentry lokal tidak dijalankan.

## Batas hasil

Perangkat fisik yang tersedia: vivo I2218, Android 16/API 36, arm64, kelas RAM 12 GB. Emulator API 37/Android 17, x86_64, halaman memori 16 KB, meminta 2 GB tetapi sistem emulator menaikkannya menjadi 4 GB (MemTotal 4.009.376 KiB). Heap growth limit 192 MB. Pemeriksaan emulator mencakup pemasangan APK produksi kandidat renderer kedua, layar welcome/login, dan 10 kali kembali dari background, tanpa login akun atau pemutaran. Crash buffer kosong. Pengukuran ini tidak menggantikan perangkat 2–4 GB nyata maupun membuktikan pemutaran pada RAM 2 GB. TOTAL PSS pada vivo ini sudah memasukkan swap PSS; laporan tidak menjumlahkannya dua kali. CPU dinyatakan sebagai persentase satu core, bukan seluruh CPU ponsel.

Sesi uji terbatas tidak membuktikan bebas seluruh kebocoran memori atau hemat baterai seharian. Ponsel terhubung USB, sehingga tidak tersedia pengukuran konsumsi baterai pemakaian biasa. Jalur chat/unduhan dibuktikan melalui tes regresi tanpa mengirim pesan kepada akun pengguna lain. Status dokumen hukum, layanan backend, dan bukti rilis sebelumnya tetap mengikuti `docs/RELEASE_SETUP.md`; perbaikan performa ini tidak otomatis mengesahkan semua aspek rilis publik.

## Hasil perangkat

Kandidat diagnostik arm64 sebelum isolasi timeline terakhir dipasang melalui `adb install -r` dan tetap memakai data pengguna. PID sesi siang adalah 23365. Timer awal maju 00:26 → 00:39; sesudah pause/resume maju 01:20 → 01:33 dan cocok dengan posisi MediaSession. Dua penghentian dan pembukaan ulang Activity/layanan dalam proses yang sama juga lulus: 01:42 → 01:55 dan 02:04 → 02:17. Kandidat awal sebelumnya telah lulus lima siklus recreation tambahan.

Crossfade aktif benar-benar diamati pada kandidat renderer kedua, sebelum dua perubahan terakhir yang hanya menyentuh presentasi. Pause saat transisi menahan posisi lagu masuk pada 3.804 ms selama lima detik; resume memajukan timer 00:05 → 00:18. Pengaturan sementara durasi 20 detik dikembalikan ke kondisi awal: crossfade mati, durasi lima detik. Ini tidak dihitung sebagai uji transisi ulang atas payload akhir.

Pada pemutar sebelum isolasi timeline terakhir, jendela 33,92 detik melaporkan CPU 55,13% satu core dan 339/410 frame janky (82,68%; p50 30 ms, p90 38 ms, p99 42 ms). Saat pause selama 33,48 detik, CPU terukur 3,11% dan tidak ada frame baru. Pengukuran CPU meliputi overhead sampling. Angka ini menjadi alasan perbaikan renderer tambahan; timer yang berjalan tidak membuktikan performa animasi yang baik. Baseline dengan timer beku tidak dipakai untuk menghitung persentase peningkatan CPU.

Percobaan sesi panjang pertama terputus pada menit ke-9 ketika ADB kehilangan perangkat. Hasil 20 siklus pemutar dan sampel sebelum koneksi hilang disimpan di `soak-interrupted-adb-disconnect/`; percobaan itu tidak dihitung sebagai 15 menit lulus. Setelah pengguna menghubungkan dan mengizinkan kembali USB debugging, APK terpasang masih versi 2.1.1/kode 62. Exit-info mencatat proses siang dihentikan karena REMOVE TASK oleh pengguna; proses idle berikutnya dihentikan sistem karena TOO MANY EMPTY PROCS, tanpa crash buffer PID tersebut. Seek pada proses baru memindahkan audio ke posisi sekitar 35% dan timer terus maju 02:56 → 03:09. Pengulangan sesi panjang memakai proses baru PID 28928.

APK akhir dipasang tanpa menghapus data; PID 7823. Dua siklus penghentian dan pembukaan kembali Activity/layanan lulus dengan proses yang sama. Seek memindahkan lagu Mutiara dari sekitar 00:26 ke 02:54, sesuai target slider 35%, kemudian timer terus maju 02:57 → 03:10 dan cocok dengan MediaSession. Bukti: `regression-reopen-final-draw.log`, `reopen-result.json`, dan `seek-final-draw.log`.

Percobaan soak pertama atas APK akhir berhenti setelah tiga siklus ketika satu ketukan miniplayer tidak membuka panel. Proses tetap hidup dan memutar lagu berikutnya, crash buffer PID aktif kosong, dan panel berhasil dibuka saat dicoba ulang. Skrip kemudian menunggu animasi tutup dua detik dan memverifikasi setiap percobaan ulang dari hierarchy baru. Percobaan terhenti disimpan di `final-soak-partial-tap/` dan tidak dihitung sebagai uji panjang yang lulus. Ini belum cukup untuk menyatakan bahwa ketukan yang terlewat pasti berasal dari skrip saja.

Pengulangan sesi panjang pada kandidat sebelum isolasi timeline selesai selama 924,85 detik (15 menit plus pemeriksaan penutup), PID 28928 tetap sama. Dua puluh siklus pemutar/fallback lirik lulus, dan empat pemeriksaan timer foreground lulus. Sampel background menit 6–15 mencakup pergantian lagu otomatis; RAM turun kembali setelah siklus UI, dengan 145,63 MiB TOTAL termasuk 41,94 MiB swap pada menit terakhir. Kandidat ini tidak menunjukkan kenaikan memori terus-menerus pada workload tersebut. Seluruh 20 siklus pada lagu uji ini memakai fallback karena lirik tidak tersedia; tidak diklaim sebagai 20 siklus lirik yang berhasil dimuat. Bukti dipertahankan di `intermediate-renderer-before-timeline-isolation/`.

Perbandingan lagu Mutiara dan layar yang sama setelah perbaikan bertahap:

| Jendela pemutar aktif | Durasi | CPU satu core | Frame dirender | Frame janky | p50 / p99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Sebelum isolasi timeline | 33,81 dtk | 56,34% | 409 | 335 (81,91%) | 31 / 44 ms |
| Isolasi timeline | 33,88 dtk | 55,87% | 410 | 321 (78,29%) | 21 / 34 ms |
| APK akhir: progres digambar, miniplayer tersembunyi berhenti | 33,50 dtk | 52,92% | 215 | 142 (66,05%) | 20 / 36 ms |

Jumlah frame yang dirender turun setelah progres digambar tanpa relayout dan koleksi miniplayer tersembunyi dihentikan. CPU pemutar aktif masih tinggi dan frame janky masih banyak. Saat pause, APK akhir menghasilkan nol frame selama 33,20 detik dan CPU sampling 3,22%; percentile frame tidak tersedia bila nol frame dirender. Perbandingan ini merupakan pengukuran berurutan satu ponsel, dengan overhead profiling, bukan eksperimen yang mengontrol seluruh perubahan suhu/cache. Bukti: `window-renderer-before-isolation.json`, `window-renderer-after-isolation.json`, `window-renderer-after-draw.json`, dan pasangan `gfx-*.json` masing-masing.

### RAM dan pemutaran pada APK akhir

Sesi terakhir selesai selama 924.95 detik, dengan PID 7823 tetap sama. Dua puluh siklus buka/tutup pemutar lulus; 0 siklus benar-benar membuka lirik, sisanya menguji fallback. Pemutaran tetap PLAYING pada semua checkpoint background, termasuk pergantian lagu otomatis. 4 dari 4 pemeriksaan foreground memiliki timer UI yang dapat diperiksa dan lulus; pemeriksaan tanpa durasi hanya membuktikan kemajuan MediaSession.

| Checkpoint | TOTAL termasuk swap | Resident PSS | Swap PSS | Activity / view root |
| --- | ---: | ---: | ---: | ---: |
| soak-cycle-baseline | 238.44 MiB | 236.77 MiB | 1.67 MiB | 1 / 2 |
| soak-cycle-5 | 205.93 MiB | 204.27 MiB | 1.66 MiB | 1 / 5 |
| soak-cycle-10 | 205.95 MiB | 204.28 MiB | 1.66 MiB | 1 / 3 |
| soak-cycle-15 | 214.17 MiB | 212.54 MiB | 1.64 MiB | 1 / 2 |
| soak-cycle-20 | 204.13 MiB | 202.50 MiB | 1.64 MiB | 1 / 3 |
| soak-minute-07 | 148.97 MiB | 116.17 MiB | 32.80 MiB | 1 / 2 |
| soak-minute-08 | 139.94 MiB | 99.39 MiB | 40.54 MiB | 1 / 2 |
| soak-minute-09 | 139.88 MiB | 100.12 MiB | 39.76 MiB | 1 / 2 |
| soak-minute-10 | 152.94 MiB | 108.52 MiB | 44.42 MiB | 1 / 2 |
| soak-minute-11 | 140.87 MiB | 101.22 MiB | 39.65 MiB | 1 / 2 |
| soak-minute-12 | 139.86 MiB | 99.76 MiB | 40.10 MiB | 1 / 2 |
| soak-minute-13 | 149.01 MiB | 105.07 MiB | 43.94 MiB | 1 / 2 |
| soak-minute-14 | 142.79 MiB | 102.27 MiB | 40.52 MiB | 1 / 2 |
| soak-minute-15 | 141.85 MiB | 101.15 MiB | 40.70 MiB | 1 / 2 |

Pemeriksaan tambahan pada Keras Kepala/Meiska memverifikasi lima siklus panel lirik yang benar-benar dimuat, dengan playback dipause. TOTAL termasuk swap: baseline 197.24 MiB, setelah lima siklus 213.29 MiB, setelah tenang 66.49 detik 190.35 MiB. Activity tetap satu dan view root kembali menjadi satu; CPU sampling saat tenang 1.91% satu core, dengan nol frame baru. Sumber lagu ini tidak melaporkan durasi dan UI menampilkan LIVE; sesi lirik ini tidak dihitung sebagai pemeriksaan timer UI. Bukti: `lyrics-cycles-final-draw.log`, `lyrics-cycles-final-result.json` dan `fixed-cycle-*-lyrics.xml`.

Sampel tidak menunjukkan pertumbuhan memori terus-menerus pada workload ini; RAM turun kembali setelah siklus panel. Ini bukti sesi terbatas, bukan pembuktian bebas kebocoran sepanjang hari atau pada semua perangkat. Crash buffer proses aktif tidak memuat FATAL EXCEPTION/Fatal signal. Riwayat exit proses lama disimpan terpisah dan tidak dianggap crash APK akhir.

Bukti akhir: `soak-final-draw-verified.log`, `soak-result.json`, `device-summary-final.json`, `memory-samples.jsonl`, `runtime-final-result.json` dan `crash-final-process.txt`. Build dan APK tidak berubah selama sesi akhir. Kandidat bertanda tangan siap untuk peninjauan/beta terbatas; performa frame, cakupan perangkat RAM rendah dan jalur pembaruan tanda tangan tetap perlu diselesaikan sebelum rilis stabil untuk pengguna lama maupun seluruh kelas perangkat.
