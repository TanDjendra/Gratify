# Audit pustaka Gratify — 8 Oktober 2026

Audit memeriksa katalog versi, pemakaian pada checkout aktif, versi penerbit di Maven Central/Google Maven, dan graf dependensi yang benar-benar terpilih untuk Android rilis serta desktop Windows. Perubahan diterapkan pada checkout lokal. Versi aplikasi tetap 2.1.0 (61).

Catatan lanjutan: audit tampilan Pustaka menemukan ketidakcocokan Material3 pada kolom input. Material3 kemudian diselaraskan menjadi multiplatform 1.12.0-alpha03 dan Android 1.5.0-alpha22 sesuai pasangan resmi Compose 1.12.1. Lihat `LIBRARY_SCREEN_AUDIT_2026-10-08.md`; audit OSV terbaru memeriksa 676 koordinat tanpa kecocokan advisori atau batch gagal. Angka/graf di laporan awal berikut tetap mencatat tahap audit sebelumnya.

## Perbaikan keamanan

Baseline memuat 682 koordinat Maven unik. OSV mencocokkan lima pustaka dengan tujuh advisori. Ini merupakan kecocokan versi, bukan bukti bahwa seluruh celah tersebut dapat dieksploitasi melalui Gratify.

| Pustaka | Sebelum | Perbaikan | Advisori |
|---|---|---|---|
| Wire runtime | 6.4.0 | 6.4.5, termasuk varian JVM | [GHSA-9rm7-3qhh-h2mc](https://github.com/square/wire/security/advisories/GHSA-9rm7-3qhh-h2mc) |
| Commons IO | 2.5 | Dihapus dari aplikasi; penyalinan AppImage memakai fungsi bawaan Kotlin | GHSA-78wr-2p64-hpwj, GHSA-gwrp-pvrq-jmwv |
| Commons Lang | 3.8.1 | 3.21.0 | [CVE-2025-48924](https://lists.apache.org/thread/bgv0lpswokgol11tloxnjfzdl7yrc1g1) |
| Java-WebSocket | 1.4.1 | 1.6.0 | [GHSA-gw55-jm4h-x339](https://github.com/TooTallNate/Java-WebSocket/security/advisories/GHSA-gw55-jm4h-x339) |
| jsoup | 1.22.2 | 1.23.2 | GHSA-65r4-943x-97jj, GHSA-pmhh-3w7g-xqp8; [rilis perbaikan](https://github.com/jhy/jsoup/releases/tag/jsoup-1.23.2) |

Wire dan jsoup datang dari BravePipe/PipePipe; Commons Lang dan Java-WebSocket datang dari PipePipe. Kebijakan resolusi pada build utama memastikan pustaka transitif tersebut diperbaiki pada kedua platform. Wire dipertahankan pada keluarga 6.x agar kompatibel dengan pesan protobuf yang telah dibangkitkan dalam extractor.

Probe lokal membuktikan data protobuf dengan panjang `Int.MAX_VALUE` menghasilkan `IllegalArgumentException` pada Wire 6.4.0. Wire 6.4.5 menolaknya dengan `IOException`, dan data normal tetap terbaca. Dua pengujian regresi ditambahkan pada modul extractor.

## Peningkatan versi

20 kelompok versi katalog ditingkatkan; beberapa kelompok mengatur lebih dari satu artefak.

| Kelompok | Sebelum | Sesudah |
|---|---|---|
| Sentry | 8.58.0 | 8.60.0 |
| Compose BOM Android | 2026.05.01 | 2026.09.00 |
| Compose Multiplatform | 1.11.1 | 1.12.1 |
| Compose resources | 1.11.1 | 1.12.1 |
| Lifecycle | 2.11.0-beta01 | 2.11.0 stabil |
| ConstraintLayout Compose | 1.1.1 | 1.1.2 |
| Android Core | 1.19.0 | 1.19.1 |
| Media3 | 1.10.1 | 1.11.1 |
| Room | 2.8.4 | 2.8.5 |
| Paging UI | 3.5.0 | 3.5.2 |
| Paging common | 3.5.0 | 3.5.2 |
| Coil | 3.4.0 | 3.6.3 |
| Compottie | 2.2.1 | 2.3.3 |
| Commons Codec | 1.22.0 | 1.22.1 |
| org.json, desktop | 20231013 | 20260814 |
| Backdrop | 2.0.0 | 2.0.1 |
| Shapes | 1.2.0 | 1.2.1 |
| JNA | 5.19.0 | 5.19.1 |
| Koin | 4.2.1 | 4.2.2 |
| ZXing | 3.5.3 | 3.5.4 |

Versi penerbit dan tautan metadata tiap pustaka tersimpan pada CSV inventaris. [Compose 1.12.1](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.1), [Coil](https://github.com/coil-kt/coil/blob/main/CHANGELOG.md), dan [Compottie 2.3.3](https://github.com/alexzhirkevich/compottie/releases/tag/2.3.3) dipilih bersama untuk menyelaraskan runtime gambar desktop. Graf awal mencampur plugin Compose 1.11.1 dengan runtime 1.12.0-alpha01 dan Skiko 0.148.1. Peningkatan Backdrop turut memajukan runtime ke 1.12.0 dan Skiko 0.150.1; tes ekspor gambar memperlihatkan `NoSuchMethodError` pada `Image.encodeToData` bila pustaka gambar dan plugin belum diselaraskan. Karena itu peningkatan tampilan dilakukan sebagai satu kelompok.

[Media3 1.11.1](https://developer.android.com/jetpack/androidx/releases/media3#1.11.1) membawa perbaikan audio, pergantian renderer, dan sesi. API aplikasi tetap dapat dikompilasi tanpa mengganti pemutar atau menghilangkan crossfade.

## Pengurangan dan penggantian dependensi

- Commons IO tidak digunakan oleh kode aplikasi. Dua deklarasi runtime dihapus; tiga operasi salin pada pengemasan AppImage diganti dengan `copyRecursively`/`copyTo`, termasuk pemeriksaan kegagalan salin.
- EasyPermissions dan Android legacy-support-v4 tidak memiliki pemakai di kode saat ini; deklarasi dan alias dihapus. Izin tetap ditangani lewat API Activity Result yang telah digunakan aplikasi.
- Compottie tetap menangani animasi JSON. Modul dotLottie, jaringan, dan resources tidak dipakai dan dihapus.
- SLF4J Android 1.7.36 dihapus dari `commonMain` Spotify/Kizzy. Versi itu terbawa ke desktop dan diabaikan oleh API SLF4J 2.x. Spotify memakai logger Kermit proyek untuk diagnostik yang sudah dinonaktifkan secara default. [Panduan SLF4J](https://www.slf4j.org/codes.html) menjelaskan ketidakcocokan provider 1.7 dengan API 2.x.
- `kotlin-test` dihapus dari dependensi produksi Spotify/Kizzy. Dependensi pengujian Spotify tetap tersedia pada `commonTest`.
- JNA standar dan JNA JPMS sebelumnya hadir bersamaan dengan versi berbeda. Varian JPMS dari VLCJ disubstitusi ke satu pasangan JNA/JNA Platform 5.19.1; modul transport desktop memakai versi katalog yang sama.
- Alias NewPipe lama, Compottie Lite, WebKit, dan multiplatform-settings-no-arg yang tidak dipakai dihapus. PipePipe memakai katalog yang sama pada Android dan JVM.

Katalog menyusut dari 137 menjadi 126 alias pustaka. Penghapusan alias yang tidak terpakai tidak otomatis berarti pengurangan ukuran APK; pengurangan graf runtime diukur terpisah.

## Keputusan untuk versi yang dipertahankan

| Area | Keputusan dan alasan |
|---|---|
| Supabase 3.0.3 dan kotlinx-datetime 0.6.1 | Memerlukan migrasi bersama. Supabase lama mengacu pada `Clock.System` yang dihapus dalam datetime baru. Pertahankan kompatibilitas login/sesi; peningkatan ke Supabase 3.8.0 dan datetime baru perlu menguji OAuth, pemulihan sesi, sinkronisasi, serta format waktu bersama Ktor. |
| FFmpeg Kit audio 6.0.1 | Sudah memakai [fork maxrave dengan dukungan 16 KB](https://github.com/maxrave-dev/ffmpeg-kit-16KB), bukan artefak upstream yang ditarik. Fork menyatakan fokus pada kompatibilitas halaman memori; pemeliharaan codec tetap perlu ditinjau. Penggantian harus mempertahankan MP3, mux audio/video, dan album art. Audit Maven tidak membuktikan keamanan codec native. |
| Material3 expressive | Versi prarilis dipakai untuk komponen saat ini. Penyelarasan runtime Compose tidak menghilangkan fitur tersebut; tidak menurunkan Material3 ke versi stabil yang lebih tua. |
| AboutLibraries 14.0.1 | Katalog sudah menandai masalah peningkatan versi. Generator lisensi dipertahankan dan data lisensi diperbarui; migrasi ke 15.2.0 perlu memeriksa plugin serta tampilan lisensi. |
| Haze 1.7.2 | Haze 2.x membutuhkan peninjauan migrasi, termasuk pengganti modul material. Tampilan blur sekarang dipertahankan. |
| Firebase BOM 33.1.0 | Peningkatan ke keluarga 35.x ditunda untuk pengujian registrasi token dan pengiriman push pada perangkat fisik. Pengujian pemutaran emulator tidak memvalidasi pengiriman FCM. |
| Pustaka lain yang memiliki rilis lebih baru | Dicatat pada CSV; tidak seluruh rilis baru diperlukan untuk perbaikan yang teridentifikasi. Ktor, OkHttp, SQLite, Calf, Glance, tray, dan Markdown dapat ditingkatkan dalam perubahan terfokus sesuai fungsi yang diuji. |
| AGP, Gradle, Kotlin, KSP | Toolchain proyek dipertahankan. Audit kerentanan otomatis ini memeriksa graf Maven runtime aplikasi, bukan keseluruhan graf plugin build. |

## Verifikasi

Build lengkap berhasil: Android debug dan rilis dengan integrasi penuh, R8 dan vital lint rilis, kompilasi desktop, serta pengujian berikut. Pemeriksaan kompatibilitas Compose/Skiko tidak lagi memberikan peringatan ketidakcocokan.

| Area | Lulus | Dilewati |
|---|---:|---:|
| Media3 | 13 | 0 |
| Pemutar desktop | 2 | 0 |
| Data/Room | 83 | 4 |
| Extractor, TLS, unduhan, protobuf | 7 | 0 |
| Lirik | 20 | 0 |
| Ktor extension | 1 | 0 |
| UI pemutar/koleksi dan sesi akun, pilihan tes | 40 | 0 |
| Total | 166 | 4 |

Empat tes data memang ditandai dilewati oleh suite; tidak dihitung sebagai lulus. Suite aplikasi yang dijalankan adalah `AccountSessionRecoveryTest`, `SessionPersistenceAuditTest`, `PlayerSheetTransitionTest`, `CollectionDetailRenderTest`, dan `AppleMusicPlayerTest`.

Pada APK terbaru yang hash-nya cocok dengan APK terpasang, 17 pemeriksaan pemutaran lulus pada emulator Android 17/API 37 dengan halaman memori 16 KB. Ini mencakup metadata, tombol sistem, pemutaran di latar belakang, crossfade, audio focus, stop-on-exit, dan pembuatan ulang layanan. Seluruh 30 pustaka native 64-bit APK universal memiliki alignment ELF minimal 16 KB.

Tiga pembukaan proses dari keadaan berhenti setelah build selesai mencatat 5758/4546/4194 ms, semuanya `Status: ok`. Buffer crash dan event ANR tidak menunjukkan kejadian pada aplikasi. Hasil emulator software renderer ini bukan pembandingan kinerja perangkat fisik.

Probe Windows berhasil memuat VLC 3.0.23 dengan JNA 5.19.1, membuat dan melepas instance VLC, serta memanggil API Windows dari JNA Platform. Instalasi desktop lengkap tidak dibuat ulang.

Graf akhir memuat 530 entri modul Android dan 384 desktop, dengan 675 koordinat unik. OSV tidak menemukan kecocokan advisori dan seluruh batch kueri berhasil. Ini dibandingkan dengan 682 koordinat unik serta tujuh advisori baseline. Katalog akhir berisi 126 alias yang dipakai, dan data lisensi diperbarui.

APK rilis masih unsigned, belum dipublikasikan. Mapping Sentry baru memiliki UUID `495678fc-5d27-3566-a559-6b540372d195`; upload mapping tidak diverifikasi karena token upload tidak tersedia pada sesi lokal.

Bukti pada `artifacts/library-audit-2026-10-08/`:

- `verification.json`: ringkasan hasil, hash APK uji dan rilis, pengujian, serta startup.
- `final/audit.json` dan `final/catalog.csv`: graf terpilih, hasil OSV, sumber versi penerbit, serta inventaris.
- `before/audit-workspace.json`: katalog awal dengan pemakaian dibatasi ke checkout aktif; `resolved-before-with-origins.json` menyimpan sumber dependensi transitif.
- `aligned-build-and-tests-final.log` dan `test-results.json`: build serta hasil suite.
- `android-media-final/verification.json`, `native-alignment.json`, dan `vlc-native-probe.log`: pemeriksaan runtime/native.
- `wire-probe-6.4.0.log` dan `wire-probe-6.4.5.log`: reproduksi decoder sebelum dan setelah patch.

## Menjalankan audit kembali

Memerlukan Python 3.11+ dan akses Maven/Google Maven/OSV. Tidak mengirimkan kode sumber, rahasia konfigurasi, atau data aplikasi; kueri OSV hanya berisi koordinat Maven publik dan versi.

```text
./gradlew auditResolvedLibraries -I scripts/resolve-libraries.init.gradle -PisFullBuild=true
python scripts/audit-libraries.py --resolved build/reports/library-audit/resolved.json --outdir build/reports/library-audit --fail-on-findings
```

Alur `launch-verification.yml` menjalankan pemeriksaan yang sama dan menyimpan hasilnya. Gate gagal bila ada kecocokan advisori atau batch OSV tidak berhasil diperiksa. Inventaris mengecualikan checkout lain, direktori build, dan artefak audit. Data versi terbaru dapat berubah; rujuk waktu pemindaian pada JSON hasil.

Pemindaian OSV tanpa temuan tidak menjamin bebas celah: basis data dapat belum memiliki advisori, dan binari FFmpeg/VLC/Skia maupun kode fork khusus membutuhkan tinjauan terpisah. iOS, Linux, macOS, pengiriman FCM, dan pengemasan installer desktop tidak diverifikasi pada host Windows dalam audit ini.
