# Audit pembaruan Gratify — 3 Oktober 2026

Status saat audit awal: audit selesai tanpa perubahan kode aplikasi atau publikasi. Pengguna kemudian menyetujui perbaikan tanpa build/publikasi dan menyatakan keystore rilis GitHub lama sudah tidak ada. Hasil implementasi dicatat terpisah di [laporan perbaikan 4 Oktober](AUTO_UPDATE_FIX_2026-10-04.md).

## Kesimpulan

Gratify dapat didistribusikan dan diperbarui melalui APK resmi di luar Play Store. Saat ini sudah ada pemeriksaan rilis GitHub dan pengunduh APK, tetapi alurnya belum dapat diandalkan saat aplikasi ditutup, izin pemasangan belum diberikan, atau varian APK berbeda. Kendala paling mendesak adalah kesinambungan tanda tangan dan nomor versi.

Pemeriksaan anonim menunjukkan repo GitHub tersedia untuk publik. Rilis terbaru adalah `v2.1.0`, diterbitkan `2026-07-26T02:22:45Z`, dengan satu aset `Gratify.apk`. Rilis tersebut dan release lokal sama-sama memakai package `com.tan.gratify`, versionName `2.1.0`, versionCode `61`, tetapi tanda tangan mereka berbeda. Perubahan lokal terbaru belum akan terdeteksi sebagai pembaruan oleh pengguna rilis itu.

## Bukti identitas APK

| APK | Package | Versi/code | SHA-256 sertifikat penanda tangan |
| --- | --- | --- | --- |
| GitHub `v2.1.0/Gratify.apk` | `com.tan.gratify` | `2.1.0 / 61` | `abaa03408221a11ec378893bce65c95af6bc2b5425a6117507590487ecd942c2` |
| Release lokal `Gratify-release-signed.apk` | `com.tan.gratify` | `2.1.0 / 61` | `efefe1b8bf7c6502aeeb60db9a45b430dc50040e11c6f76eb22e2308a51c316a` |
| Debug yang sebelumnya dibagikan | `com.tan.gratify.dev` | `2.1.0-dev / 61` | Terpisah dari jalur release |

Hash berkas APK GitHub cocok dengan digest yang dipublikasikan GitHub: `0433940b5254e0768fb8349329b08ac2b38aea86fffb3862dd3fefa47c24dee2`. Ukuran 52.105.071 byte. APK hanya diunduh untuk inspeksi, tidak dipasang. Pemeriksaan langsung dengan apksigner dan aapt2 juga dilakukan pada release lokal, bukan hanya membaca laporan sebelumnya.

Verifier Gratify saat ini menuntut sertifikat saat ini sama persis. Dengan kode tersebut, APK dengan sertifikat lain akan ditolak meskipun versionCode dinaikkan. Sistem Android juga mensyaratkan identitas penanda tangan yang kompatibel; rotasi resmi memerlukan bukti lineage yang sah. Tidak ada bukti rotasi atau keberadaan keystore rilis GitHub asli yang terkonfirmasi dalam audit ini. Menonaktifkan verifikasi bukan solusi.

## Temuan dan prioritas

| Prioritas | Temuan | Dampak | Perbaikan yang diusulkan |
| --- | --- | --- | --- |
| P1 | Sertifikat rilis GitHub dan lokal berbeda; kode versi sama | Tidak ada pemberitahuan versi baru, dan update dengan signer berbeda ditolak | Tentukan kelompok APK yang sudah digunakan, gunakan keystore asal masing-masing jalur, naikkan versionCode dan versionName. Jangan mengganti keystore secara diam-diam |
| P1 | `downloadId` dan receiver hanya disimpan dalam memori | DownloadManager dapat menyelesaikan unduhan setelah proses aplikasi mati, tetapi verifikasi/pemasangan tidak dipulihkan | Simpan pekerjaan unduhan, versi dan berkas; periksa status saat aplikasi dibuka, gunakan pekerjaan latar yang terjadwal dan notifikasi Siap dipasang |
| P1 | Pengambil rilis memilih APK pertama berdasarkan akhiran nama | Saat rilis berisi ABI atau Full/FOSS berbeda, pengguna bisa mendapat berkas salah | Metadata eksplisit package/channel/ABI, pilih universal Full resmi untuk jalur umum; pisahkan debug dan FOSS |
| P1 bersyarat | Dialog wajib tidak dapat dibatalkan dan tidak memiliki keadaan unduhan/gagal | Untuk versi di bawah minimum, aplikasi termasuk unduhan offline dapat terhalang saat instalasi gagal; tombol bisa memulai unduhan berulang | Pembaruan biasa bisa ditunda; minimum kompatibilitas hanya membatasi fitur server yang benar-benar tidak kompatibel, tetap sediakan musik lokal dan bantuan/retry |
| P2 | Tidak memeriksa `canRequestPackageInstalls()` atau melanjutkan setelah kembali dari pengaturan | Pengguna bergantung pada dialog sistem; berkas yang siap dipasang tidak dipulihkan setelah proses mati | Jelaskan izin, buka pengaturan sumber Gratify, lanjutkan dengan tombol Pasang ketika kembali, simpan status siap-pasang |
| P2 | Tidak ada progres dalam aplikasi, retry/cancel atau pencegahan unduhan ganda | Tidak jelas apakah update sedang berjalan; unduhan berulang memboroskan data dan penyimpanan | Satu pekerjaan aktif, progres/status yang konsisten, retry dengan berkas yang sudah selesai |
| P2 | Receiver didaftarkan setelah enqueue | Penyelesaian sangat cepat berpotensi terjadi sebelum receiver terpasang | Pengamatan status yang persisten, pemeriksaan langsung sesudah pendaftaran/startup |
| P2 | `isCheckingUpdate` tidak dipulihkan melalui finally; tidak ada batas waktu khusus updater atau pengaman pemeriksaan bersamaan | Channel tak dikenal atau exception dapat meninggalkan status Memeriksa; permintaan bisa bertumpuk | finally, timeout, single-flight, kesalahan yang jelas, normalisasi versi |
| P2 | `CheckForUpdateAt` dicatat sebelum request dan tidak menjadi jeda pengecekan | Setiap pembuatan Activity bisa memanggil API; waktu yang ditampilkan berarti percobaan, bukan keberhasilan | Pisahkan lastAttempt/lastSuccess, cache/ETag, jeda pemeriksaan otomatis; pemeriksaan manual tetap tersedia |
| P2 | Metadata tidak membawa versionCode/hash/ukuran APK; Asset memiliki size tetapi tidak diteruskan | Tidak dapat mengikat hasil unduhan ke versi yang dijanjikan atau memverifikasi digest rilis | Manifest HTTPS berisi versionCode, versionName, package, channel, URL, sha256, size dan notes. Tetap periksa tanda tangan paket; hash bukan pengganti tanda tangan |
| P2 | APK disalin ke cache, salinan asal tetap disimpan; tidak ada pembersihan sukses | Ruang disk sekitar dua APK untuk tiap unduhan dan dapat terus bertambah | Cek ruang tersedia, bersihkan berkas setelah hasil pemasangan terkonfirmasi atau update dibatalkan |
| P2 | Semua kegagalan diberi pesan gagal diverifikasi | Gangguan jaringan, ruang penuh, izin dan signature sulit dibedakan pengguna | Pesan spesifik dengan tindakan Coba lagi / Izinkan pemasangan / Laporkan |
| P2 | Workflow membangun APK sebagai Actions artifacts tanpa langkah publikasi Release | Build CI sukses tidak otomatis tersedia di endpoint updater | Siapkan release bertag dan metadata; publikasi dilakukan setelah persetujuan dan pemeriksaan signer, versi, hash, hasil uji |
| P2 | Endpoint F-Droid package Gratify menghasilkan HTTP 404; jalur kode juga menghasilkan URL APK kosong | Jalur F-Droid belum merupakan jalur update aktif | Gunakan GitHub resmi dulu; F-Droid hanya ditawarkan jika benar-benar tersedia dan kompatibilitas signer dipastikan |
| P2 lintas platform | Implementasi downloadAndInstallApk JVM merupakan no-op, tetapi state update dipakai bersama | Dialog unduh Android di desktop berpotensi tidak melakukan apa pun | Pisahkan sumber/aset dan tindakan update sesuai platform |

Sumber kode: `UpdateManager.android.kt`, `UpdateHandler.kt`, `UpdateRepositoryImpl.kt`, `UpdateData.kt`, `VersionManager.kt`, `MainScreen.kt`, `SharedViewModel.kt`, `MainActivity.kt`, `Ytmusic.kt`, `.github/workflows/android-release.yml`, `androidApp/build.gradle.kts`, `gradle/libs.versions.toml`. Pengaturan saat ini menampilkan cek otomatis dan manual; jalur F-Droid tersedia di lapisan data tetapi bukan pilihan channel yang terlihat pada halaman pengaturan yang diperiksa.

Kontrol yang sudah baik: unduhan wajib HTTPS; paket harus sama, versionCode harus lebih tinggi, sertifikat harus sama; FileProvider mengirim URI dengan izin baca sementara. Audit tidak menemukan pembenaran untuk menghapus kontrol ini. Isi APK tetap diperiksa oleh installer Android; audit ini tidak mengklaim APK sembarang dapat mengganti Gratify.

## Solusi selama belum masuk Play Store

Rekomendasi: pertahankan GitHub Releases resmi yang sudah bisa diakses publik, gunakan satu jalur stabil APK release universal bertanda tangan, dan metadata update yang dibaca aplikasi serta situs unduhan. Tidak perlu membuat repositori baru. Situs saat ini menautkan langsung `v2.1.0/Gratify.apk`, sehingga tautan harus ikut diperbarui atau memakai sumber metadata stabil.

Alur pengguna yang diusulkan:

1. Aplikasi mengecek pembaruan ketika online dengan cache dan jeda; kegagalan cek tidak mengeluarkan pengguna dari akun atau menghalangi lagu unduhan.
2. Pesan versi baru berisi perubahan, ukuran dan tombol Unduh / Nanti.
3. Unduhan memiliki progres yang dapat dipulihkan setelah aplikasi ditutup.
4. APK dicek hash, package, versi dan signer. Jika izin belum ada, Gratify memandu ke pengaturan dan menyimpan keadaan siap-pasang.
5. Pengguna menekan Pasang dan menyetujui installer sistem. Untuk jalur ini, pemasangan bukan instalasi diam-diam.
6. Sesudah update, aplikasi memeriksa migrasi berhasil dan membersihkan APK sementara; akun, preferensi, playlist dan unduhan tetap memakai penyimpanan aplikasi yang sama.

Normalnya update yang kompatibel tidak memerlukan uninstall. Namun debug `.dev` dan release adalah aplikasi berbeda: perpindahan antar keduanya bukan update di tempat dan tidak otomatis membawa seluruh data lokal. Jangan meminta pengguna menghapus APK lama sebagai solusi signature mismatch. Bila keystore asal tidak tersedia, perlu rencana ekspor/impor dan pemulihan yang nyata sebelum mengganti jalur; login/sinkronisasi saja tidak menjamin semua file unduhan lokal ikut pindah.

Database saat ini versi 28 dan memiliki migrasi otomatis/manual; builder yang diperiksa tidak menggunakan fallback destruktif. Ini mendukung pemeliharaan data, tetapi semua jalur dari APK yang sudah dibagikan belum dibuktikan dengan pemasangan di perangkat. Keberadaan migrasi bukan jaminan universal bahwa setiap upgrade lama berhasil.

Alur pengelola: naikkan versi, build release dengan keystore yang disepakati, verifikasi APK, uji upgrade di atas versi lama, siapkan draft release, unggah aset, lalu publikasikan metadata terakhir setelah aset tersedia. Sukses build tidak berarti update sudah dirilis. Minimum versi hanya dinaikkan bila ada kebutuhan kompatibilitas nyata. Kunci penandatangan tidak ditaruh dalam APK, sumber publik atau chat.

## Persiapan bila nanti masuk Play Store

Versi Google Play perlu memakai mekanisme pembaruan Google Play dan menghilangkan izin/updater APK untuk distribusi Play. Kebijakan Google Play melarang penggunaan REQUEST_INSTALL_PACKAGES untuk self-update. Ini tidak menjelaskan seluruh alasan Gratify belum bisa diunggah: tidak ada keputusan penolakan Play Console yang diberikan untuk diaudit.

Saat menyiapkan Play App Signing, identitas penanda tangan lintas distribusi harus direncanakan dengan keystore yang kompatibel agar pengguna bisa berpindah saluran. Jangan menganggap upload key otomatis sama dengan app signing key yang dipakai pada APK dari Play.

Per 3 Oktober 2026, dokumentasi Android juga mencatat perlindungan developer verification mulai 30 September 2026 untuk pemasangan dari participating stores di Indonesia dan tiga negara lain, pada perangkat tersertifikasi Android 7+. Tersedia Android Developer Console bagi distribusi di luar Play. Cakupan resmi ini tidak berarti seluruh pemasangan APK langsung dari situs sudah otomatis diblokir; pendaftaran developer/package perlu masuk rencana distribusi, dan bukan kewajiban menerbitkan aplikasi di Play Store.

## Verifikasi dan batas audit

- Tiga test VersionManager lulus pada JVM: versi baru, versi sama/lama, versi rusak. APK Android tidak dibuild.
- Lima endpoint diperiksa secara anonim: repo GitHub 200, latest release 200, daftar release 200, tautan APK 200, F-Droid 404.
- APK publik diunduh untuk inspeksi; digest cocok, signature dan package/version diperiksa. Release lokal juga diperiksa langsung.
- Workflow ditelusuri: Actions artifacts tersedia dalam definisi; tidak ada langkah pembuatan/publikasi GitHub Release pada workflow Android yang diperiksa.
- Tidak ada uji install-over-update, pemulihan setelah process death, izin sumber tidak dikenal, ruang penuh, atau migrasi seluruh versi lama pada HP. Itu harus menjadi kriteria penerimaan implementasi.
- Audit tidak mengakses atau menampilkan private key/password keystore, tidak menerbitkan rilis, tidak mengubah repository visibility dan tidak mengunggah APK.

Bukti: `artifacts/auto-update-audit-2026-10-03/public-endpoints.json`, `published-apk-download.json`, `published-apk-signature.txt`, `published-apk-badging.txt`, `local-release-signature.txt`, `local-release-badging.txt`, `version-tests.log`, `verification.json`.

## Referensi resmi

- [Distribusi APK melalui situs/server](https://developer.android.com/distribute/marketing-tools/alternative-distribution)
- [Penandatanganan APK dan kesinambungan kunci](https://developer.android.com/studio/publish/app-signing)
- [Izin pemasangan sumber aplikasi](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())
- [GitHub API latest release](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)
- [Kebijakan REQUEST_INSTALL_PACKAGES Google Play](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en)
- [Android developer verification dan cakupan rollout](https://developer.android.com/developer-verification)
- [Pendaftaran distribusi di luar Google Play](https://developer.android.com/developer-verification/guides/android-developer-console)
