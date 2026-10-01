# Audit fitur Gratify — 1 Oktober 2026

## Penilaian

**Belum siap dinyatakan selesai atau siap rilis publik.** Struktur fitur utama tersedia dan build berhasil, tetapi masih ada masalah yang dapat mencampur data akun, menghapus perubahan perangkat lain, menampilkan kontrol privasi yang tidak diterapkan di server, serta melemahkan validasi TLS.

Hasil: **20 temuan — 7 P1, 12 P2, 1 P3.** Tidak ada P0 yang terbukti dalam audit ini.

- **P1:** prioritas tinggi sebelum rilis; keamanan, privasi, atau integritas data.
- **P2:** fitur tidak bekerja lengkap, hasil keliru, atau kegagalan tidak ditangani.
- **P3:** gangguan kecil pada interaksi antarmuka.

Penilaian ini berdasarkan source saat audit, dependensi yang dipakai build, build/lint, dan probe terisolasi. Ini bukan pernyataan bahwa seluruh fitur telah diuji melalui akun dan perangkat nyata.

## Verifikasi dan batas audit

| Pemeriksaan | Hasil |
|---|---|
| Android debug APK | `assembleDebug` berhasil |
| Tes JVM yang tersedia | 3 tes VersionManager lulus; 0 gagal |
| Android lint | 0 error, 121 warning; termasuk 2 warning trust manager dari dependensi |
| Probe API/algoritma | 7 pemeriksaan: 6 mengonfirmasi pola bermasalah, 1 kontrol Flow berhasil |
| UI Android | Bukti pengujian sebelumnya tersedia untuk Home, Search, Library, Friends, Settings, Profile, font besar dan tablet |
| Login nyata, reset email, dua akun, dua perangkat | Belum diuji menyeluruh pada audit ini |
| Streaming/audio nyata, download/offline, Android Auto, Discord, Spotify | Belum diuji menyeluruh pada audit ini |
| Supabase aktif | Policy, bucket, dan hotfix aktif belum diverifikasi; SQL lokal tidak membuktikan keadaan server |
| Desktop/iOS | Build JVM berhasil; desktop punya keterbatasan, iOS belum lengkap |

Perintah verifikasi:

```powershell
.\gradlew.bat :composeApp:jvmTest :androidApp:assembleDebug :androidApp:lintDebug --no-daemon --console=plain --max-workers=1
powershell -NoProfile -File artifacts/feature-audit/run-probes.ps1
```

Bukti: [build.log](D:/Tan/Gratify/artifacts/feature-audit/build.log), [hasil tes](D:/Tan/Gratify/artifacts/feature-audit/jvm-tests.xml), [lint XML](D:/Tan/Gratify/artifacts/feature-audit/lint-results-debug.xml), [hasil probe](D:/Tan/Gratify/artifacts/feature-audit/probe-results.json), [source probe](D:/Tan/Gratify/artifacts/feature-audit/FeatureAuditProbes.kt), [runner](D:/Tan/Gratify/artifacts/feature-audit/run-probes.ps1). Runner menggunakan versi dependensi yang sudah ada di cache Gradle.

Probe tidak mengakses akun, jaringan, atau penyimpanan aplikasi. Probe path hanya menghitung lokasi; probe sinkronisasi memakai model himpunan; probe TLS memeriksa implementasi dependency tanpa mengubah default TLS proses. Tidak dilakukan eksploitasi atau perubahan data Supabase.

## Cakupan seluruh kelompok fitur

Semua kelompok berikut ditelusuri melalui layar, ViewModel, repository/service terkait. “Ditinjau” berarti audit source; bukan otomatis lulus uji penggunaan nyata.

| Kelompok | Status dan hasil |
|---|---|
| Startup, navigasi, deep link | UI startup sebelumnya berjalan; recovery belum tersambung lengkap (F08) |
| Login email, Google, OTP, daftar akun | Risiko pergantian akun dan signup (F01, F09); integrasi akun nyata perlu uji |
| Buat profil dan avatar | Penyimpanan lokal ada; kegagalan cloud dapat dianggap sukses (F16) |
| Home, filter, rekomendasi, continuation | Ditinjau; cancellation dan timeout tersedia; Home dengan data nyata pernah ditampilkan |
| Search, kategori, saran, riwayat | Ditinjau; debounce/cancellation tersedia; bagian sosial terkena F12 |
| Mood dan charts | Ditinjau; pengujian slow network/loading/error masih diperlukan |
| Album, artis, favorit, detail lagu | Ditinjau; sinkronisasi favorit berisiko F02 |
| Podcast dan episode | Ditinjau; fallback cache tersedia; playback dan jaringan perlu uji nyata |
| Library, list/grid, pin, liked, saved, recent | Bukti UI tersedia; penghapusan playlist bermasalah (F17) |
| Playlist lokal, tambah lagu, urutan, sinkron YouTube | Ditinjau; delete dan cloud replacement perlu F05/F17 |
| Cloud library dan pergantian akun | Masalah integritas data (F01, F02) |
| Cloud pengaturan, riwayat, antrean | Belum pulih lengkap (F10, F11) |
| Playlist publik, berbagi, simpan | Penggantian track tidak atomik dan bisa melaporkan sukses palsu (F05) |
| Player, seek, shuffle, repeat, queue, video, background | Media3 tersambung; metadata tambahan dapat menunda playback (F18); perlu uji audio nyata |
| Sleep timer, crossfade, AutoMix, normalisasi, equalizer, SponsorBlock | Ditinjau; timer akhir lagu salah saat kondisi playback berubah (F19); efek audio perlu uji nyata |
| Lirik, terjemahan AI, Canvas | Ditinjau; endpoint Gemini dan logging token bermasalah (F13, F06) |
| Download, cache offline, ekspor audio/video | Ditinjau; ekspor gagal pada nama tertentu (F14); resume/offline/storage penuh perlu uji |
| Profil publik, privasi, follower/following | Masalah privasi, Flow, optimistic update, tombol kosong (F03, F12, F16, F20) |
| Friends, aktivitas, status online, catatan | Ditinjau; kegagalan follow perlu penanganan (F16); heartbeat dan expiry perlu uji lintas akun |
| Analytics dan scrobble | Ditinjau; pemulihan riwayat belum lengkap (F11); akurasi lintas sesi perlu uji |
| Notifikasi dan follow back | Ditinjau; pembacaan follower dengan firstOrNull terkena F12 |
| Settings, bahasa, proxy, akun eksternal | Ditinjau; cloud settings dan logging bermasalah (F10, F06); layanan eksternal perlu uji |
| Backup manual/otomatis dan restore | Restore Android berisiko pada OS lama (F04); backup desktop rusak (F15) |
| Updater, MediaSession, Bluetooth, Android Auto, PIP, platform lain | Validasi APK HTTPS/package/version/signature tersedia; tes versi lulus; instalasi update, integrasi perangkat, dan platform lain belum diuji lengkap |

## Temuan prioritas tinggi

### F01 — P1 — Data akun lama dapat masuk ke akun baru

- **Bukti:** [UserDataSyncManager](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/sync/UserDataSyncManager.kt:152) melakukan backup akun lama setelah sesi akun baru aktif; ketika backup gagal, database lokal dipertahankan tetapi download data akun baru tetap berjalan. Sync berkala tetap dimulai pada baris 199. [UserDataSyncManager](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/sync/UserDataSyncManager.kt:69) memakai pengguna sesi aktif ketika upload. Logout di [SettingsViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SettingsViewModel.kt:1455) juga dapat mempertahankan data saat backup gagal.
- **Pemicu/dampak:** akun A memiliki data lokal, lalu pindah ke B saat backup A gagal. Data lokal A dan B bercampur; upload berikutnya dapat membawa data A ke B. Policy kepemilikan yang benar akan menolak penulisan data A menggunakan sesi B; komentar source yang mengandalkan policy permisif tidak cocok dengan hotfix lokal. Lihat [panduan RLS Supabase](https://supabase.com/docs/guides/database/postgres/row-level-security).
- **Perbaikan:** pisahkan penyimpanan per akun atau karantina data akun keluar, backup sebelum pergantian sesi, dan blokir sync ketika kepemilikan database belum cocok. Jangan melemahkan RLS untuk mengatasi alur ini.
- **Uji penerimaan:** login A → gagal backup/offline → login B; B tidak melihat data A dan tidak mengunggah data A sebagai milik B.

### F02 — P1 — Perangkat lama dapat menghapus tambahan dari perangkat lain

- **Bukti:** [UserDataSyncRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserDataSyncRepositoryImpl.kt:59) menggunakan penanda pernah syncDown, lalu menghapus `cloudKeys - localKeys`. [UserDataSyncManager](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/sync/UserDataSyncManager.kt:43) menjalankan upload berkala tanpa merge terbaru terlebih dahulu.
- **Pemicu/dampak:** A menambahkan lagu; B punya snapshot lokal lebih lama dan pernah syncDown. Upload B menganggap tambahan A sebagai penghapusan. Probe himpunan mereproduksi keputusan penghapusan tersebut; tidak dilakukan penghapusan cloud nyata.
- **Perbaikan:** kirim operasi perubahan dengan versi/waktu dan tombstone penghapusan eksplisit; jangan menafsirkan semua item yang absen di snapshot sebagai delete.
- **Uji penerimaan:** dua perangkat melakukan penambahan dan penghapusan secara bersamaan; tambahan baru tetap ada dan hanya penghapusan yang disengaja diterapkan.

### F03 — P1 — Pengaturan privasi profil tidak diterapkan pada pembaca lain

- **Bukti:** [kontrol privasi](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/home/ProfileScreen.kt:1157) berlabel “Yang bisa dilihat orang lain”, tetapi menulis preference lokal. [pembacaan lokal](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/home/ProfileScreen.kt:227) menyembunyikan tampilan sendiri; [UserProfileViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:109) membaca profil/follower/playlist tanpa flag tersebut. Entity UserProfile tidak menyimpan flag server.
- **Pemicu/dampak:** mematikan visibilitas memberi kesan bahwa pengguna lain tidak dapat melihat data, padahal jalur profil publik tidak menerapkan pilihan itu. SQL lokal juga menyediakan pembacaan profil/follow publik; keadaan server aktif belum diuji.
- **Perbaikan:** simpan pilihan privasi di server dan terapkan pada query/view/RLS atau proyeksi data publik; samakan tampilan pemilik dan pengunjung.
- **Uji penerimaan:** akun B membaca profil A setelah setiap pilihan dimatikan; data terkait tidak muncul melalui UI maupun API publik.

### F04 — P1 — Restore ZIP Android tidak memvalidasi batas direktori

- **Bukti:** [restoreFolder](D:/Tan/Gratify/composeApp/src/androidMain/kotlin/com/tan/gratify/viewModel/SettingsViewModel.android.kt:250) menggabungkan nama entry dengan folder download tanpa pemeriksaan canonical path. Entry `download/../datastore/filename` lolos prefix download dan secara perhitungan keluar dari folder tujuan.
- **Pemicu/dampak:** pengguna mengimpor ZIP buatan pihak lain pada Android yang tidak memiliki validasi ZIP bawaan; file dalam ruang aplikasi dapat tertimpa. Android 14+ untuk target 34+ menolak entry traversal secara bawaan, sehingga temuan ini terutama relevan untuk API 26–33 yang masih didukung. Tidak dilakukan overwrite nyata atau uji eksekusi kode. [Risiko ZIP](https://developer.android.com/privacy-and-security/risks/zip-path-traversal), [perubahan Android 14](https://developer.android.com/about/versions/14/behavior-changes-14#zip-path-traversal).
- **Perbaikan:** validasi canonical path terhadap root dengan batas separator, allowlist isi archive, validasi di lokasi sementara sebelum mengganti file aplikasi, dan rollback saat gagal.
- **Uji penerimaan:** entry traversal/absolut ditolak pada API 26–33 dan 34+; backup sah tetap pulih; restore gagal tidak menghapus data lama.

### F05 — P1 — Penggantian playlist cloud tidak atomik dan bisa sukses palsu

- **Bukti:** [SharedPlaylistRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/SharedPlaylistRepositoryImpl.kt:104) menghapus track lama; kegagalan insert pada baris 137 ditelan dan baris 142 tetap mengembalikan sukses. [SocialRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/SocialRepositoryImpl.kt:127) juga mengganti isi melalui delete dan insert terpisah.
- **Pemicu/dampak:** jaringan/RLS/insert gagal setelah delete; playlist kehilangan isi lama, sementara UI dapat menyatakan berhasil.
- **Perbaikan:** gunakan transaksi RPC atau replacement berversi yang atomik; propagasikan kegagalan; pertahankan isi lama sampai penggantian terverifikasi.
- **Uji penerimaan:** paksa insert gagal setelah update dimulai; track sebelumnya tetap ada dan UI menampilkan gagal/retry.

### F06 — P1 — Token dan cookie dapat masuk ke log tanpa redaksi

- **Bukti:** [Ytmusic logging](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/Ytmusic.kt:159) memasang CurlLogger/Logging ALL; [redactHeaders](D:/Tan/Gratify/core/service/ktorExt/src/commonMain/kotlin/com/tan/ktorext/curl/CurlLoggerPlugin.kt:29) default kosong. [LyricsCanvasRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/LyricsCanvasRepositoryImpl.kt:133) dan baris 277 mencetak token Spotify; [cookie logging](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/YouTube.kt:1172) mencetak cookie.
- **Pemicu/dampak:** penggunaan layanan berautentikasi memasukkan credential ke logcat/stdout atau log yang diekspor. Akses log diperlukan; audit tidak menyatakan log dapat dibaca semua aplikasi. Tidak menggunakan token pengguna dalam probe.
- **Perbaikan:** hapus logging secret eksplisit; redaksi Cookie/Authorization/API key dan body sensitif; matikan logging rinci di release. [Dokumentasi logging Ktor](https://ktor.io/docs/client-logging.html).
- **Uji penerimaan:** jalankan login/playback dengan token sintetis; pencarian log tidak menemukan nilai secret pada debug maupun release.

### F07 — P1 — Inisialisasi extractor melemahkan default TLS proses

- **Bukti:** [Extractor Android](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/androidMain/kotlin/com/tan/kotlinytmusicscraper/extractor/Extractor.android.kt:24) memanggil NewPipe.init. Bytecode dependency `extractor:c3139c584d` menunjukkan init memanggil trustEveryone, lalu mengganti default hostname verifier dan SSLSocketFactory HttpsURLConnection. [bytecode init/TLS](D:/Tan/Gratify/artifacts/feature-audit/newpipe-bytecode.txt), [bytecode trust manager](D:/Tan/Gratify/artifacts/feature-audit/newpipe-trust-bytecode.txt).
- **Pemicu/dampak:** kode yang menggunakan default HttpsURLConnection setelah init dapat menerima sertifikat/hostname tidak sah. Probe implementasi dependency menerima chain kosong dan hostname sintetis. Ini tidak membuktikan seluruh koneksi OkHttp/Supabase terpengaruh; klien tersebut memiliki konfigurasi tersendiri. Tidak dilakukan MITM jaringan. [Risiko trust manager Android](https://developer.android.com/privacy-and-security/risks/unsafe-trustmanager).
- **Perbaikan:** ganti atau patch dependency agar trustEveryone tidak dijalankan; pastikan tidak ada penggantian default TLS yang permisif.
- **Uji penerimaan:** setelah init extractor, HttpsURLConnection menolak sertifikat self-signed dan hostname salah; host sah tetap berfungsi.

## Temuan prioritas menengah

### F08 — P2 — Form password baru tidak tersambung dengan recovery

- **Bukti:** [ForgotPasswordViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/ForgotPasswordViewModel.kt:125) mendefinisikan markRecoveryReady tanpa pemanggil; [form recovery](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/login/ForgotPasswordScreen.kt:174) mensyaratkan PasswordResetReady. Handler deep link di MainActivity hanya meneruskan ke SDK; tidak ditemukan jalur yang mengaktifkan form tersebut.
- **Dampak:** membuka email recovery dapat menghasilkan sesi autentikasi tanpa membawa pengguna ke form password baru.
- **Perbaikan/uji:** tangani event recovery dan navigasi cold/warm launch secara eksplisit; email reset harus membuka form, menyimpan password baru, lalu memungkinkan login ulang. Belum mengirim email recovery nyata.

### F09 — P2 — Signup dapat selesai walau penetapan password gagal

- **Bukti:** [SignUpViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SignUpViewModel.kt:276) menangkap kegagalan updateUser password tanpa meneruskannya; pemanggil pada baris 177 terus menuju Authenticated.
- **Dampak:** OTP berhasil, tetapi password yang dimasukkan pengguna tidak tersimpan dan UI menyatakan selesai.
- **Perbaikan/uji:** jadikan update password tahap wajib dengan error/retry; injeksikan kegagalan sesudah OTP dan pastikan tidak ada status signup sukses palsu.

### F10 — P2 — Cloud settings memakai key dan tipe yang tidak cocok

- **Bukti:** [UserDataSyncRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserDataSyncRepositoryImpl.kt:304) menulis key camelCase; [UserDataSyncRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserDataSyncRepositoryImpl.kt:424) memulihkan semuanya melalui putString. [DataStoreManagerImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/dataStore/DataStoreManagerImpl.kt:1434) memakai key snake_case; volume/speed/pitch memiliki tipe tersendiri.
- **Dampak:** beberapa pengaturan tidak dibaca/dipulihkan oleh flow yang sebenarnya. Tidak semua key salah: quality dan language perlu dipertahankan sesuai kontrak yang benar.
- **Perbaikan/uji:** gunakan DTO bertipe dan setter resmi dengan migrasi/allowlist; ubah quality, download quality, normalisasi, shuffle, volume, speed dan pitch di A, pulihkan di B, lalu verifikasi nilai yang dipakai player.

### F11 — P2 — Pemulihan riwayat dan antrean belum lengkap

- **Bukti:** [UserDataSyncRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserDataSyncRepositoryImpl.kt:255) mengambil riwayat dari liked songs, bukan seluruh event pemutaran. Restore pada baris 384 hanya memasukkan metadata lagu; [UserDataSyncRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserDataSyncRepositoryImpl.kt:415) hanya mengembalikan jumlah antrean tanpa memulihkan item.
- **Dampak:** lagu yang pernah diputar tetapi tidak disukai dapat hilang dari backup riwayat; waktu/jumlah pemutaran dan urutan antrean tidak pulih sesuai klaim sync.
- **Perbaikan/uji:** sinkronkan event/statistik serta antrean lengkap dalam transaksi lokal; pulihkan instalasi baru dengan lagu unliked yang pernah dimainkan dan antrean terurut.

### F12 — P2 — Flow sosial gagal saat dikonsumsi dengan firstOrNull

- **Bukti:** [UserRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserRepositoryImpl.kt:87) melakukan emit di dalam try lalu emit fallback dari catch. firstOrNull membatalkan upstream sesudah nilai pertama; pembatalan tertangkap lalu emit kedua melanggar exception transparency. [NotificationViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/NotificationViewModel.kt:124) dan SharedViewModel memakai firstOrNull untuk follower.
- **Dampak:** jalur follower/notifikasi tertentu melempar IllegalStateException. Probe pola yang sama gagal; kontrol emit di luar catch berhasil. Collector biasa tidak otomatis mengalami kegagalan yang sama.
- **Perbaikan/uji:** pisahkan operasi yang dapat gagal dari emit, gunakan penanganan flow yang benar dan teruskan cancellation; firstOrNull pada follower/following kosong maupun berisi harus berhasil.

### F13 — P2 — Endpoint Gemini salah untuk payload OpenAI

- **Bukti:** [AiService base URL](D:/Tan/Gratify/core/service/aiService/src/commonMain/kotlin/com/tan/gratify/aiservice/AiService.kt:52) memakai /v1beta/; baris 109 menambahkan chat/completions. Endpoint kompatibilitas yang didokumentasikan memakai /v1beta/openai/chat/completions. [Dokumentasi Google](https://ai.google.dev/gemini-api/docs/openai).
- **Dampak:** host Gemini default mengirim permintaan ke endpoint yang keliru. Tidak melakukan permintaan berbayar atau memakai API key pengguna.
- **Perbaikan/uji:** adapter Gemini atau base URL kompatibilitas yang benar; tes URL/payload dan validasi model yang tersedia ketika konfigurasi disimpan.

### F14 — P2 — Ekspor lagu gagal pada nama yang mengandung slash

- **Bukti:** [handler/DownloadHandler](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/handler/DownloadHandler.kt:171) membentuk nama dari judul/artis tetapi regex tidak menghapus /. Nama artis sah AC/DC menghasilkan subpath; FileOutputStream thumbnail gagal dan error dilempar sebagai RuntimeException.
- **Dampak:** ekspor nama tersebut gagal dan coroutine dapat memicu crash, tergantung handler exception. Probe memastikan slash tetap berada dalam nama; tidak menulis ke folder download.
- **Perbaikan/uji:** sanitasi kedua separator, gunakan API penyimpanan sesuai Android, dan tampilkan error yang dapat dicoba ulang; uji AC/DC, judul berslash, storage penuh, dan izin gagal.

### F15 — P2 — Backup desktop memakai ZipOutputStream bertingkat

- **Bukti:** [backup JVM](D:/Tan/Gratify/composeApp/src/jvmMain/kotlin/com/tan/gratify/viewModel/SettingsViewModel.jvm.kt:84) membuat ZIP kedua di atas ZIP pertama yang belum memiliki entry.
- **Dampak:** pola API yang sama menghasilkan ZipException “no current ZIP entry”. Temuan ini berlaku pada desktop, bukan implementasi backup Android.
- **Perbaikan/uji:** gunakan satu ZipOutputStream dengan lifecycle entry yang benar; archive desktop harus dapat dibuka dan memulihkan database serta settings.

### F16 — P2 — Follow dan penyimpanan profil dapat sukses palsu saat jaringan gagal

- **Bukti:** [UserProfileViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:207) mengubah status/count/cache sebelum request, tanpa rollback atau error pada Result.failure. [FriendsActivityViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/FriendsActivityViewModel.kt:137) juga mengabaikan kegagalan; [CreateProfileViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/CreateProfileViewModel.kt:99) dapat menandai saveSuccess setelah kegagalan cloud.
- **Dampak:** UI menampilkan follow/profil tersimpan yang tidak sesuai keadaan server.
- **Perbaikan/uji:** pending state, rollback atau antrean retry, dan pesan status lokal/cloud yang jelas; uji offline/RLS denied lalu retry, tanpa count atau status sukses palsu.

### F17 — P2 — Delete playlist dari Library tidak menghapus backup cloud

- **Bukti:** [delete Library](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/library/LibraryScreen.kt:310) memanggil [LibraryViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/LibraryViewModel.kt:302) yang hanya menghapus lokal; [LocalPlaylistViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/LocalPlaylistViewModel.kt:457) memiliki jalur cleanup cloud terpisah.
- **Dampak:** playlist yang telah di-backup dapat muncul lagi saat login/syncDown; UI delete Library juga tidak memeriksa kegagalan repository dengan memadai.
- **Perbaikan/uji:** satu use case delete untuk semua layar, dengan tombstone/retry cloud dan cleanup share; playlist yang dihapus dari Library tidak muncul kembali setelah login atau tetap publik tanpa sengaja.

### F18 — P2 — Metadata Tidal tambahan berada di jalur kritis playback

- **Bukti:** [StreamRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/StreamRepositoryImpl.kt:176) menunggu lookup metadata sebelum menyimpan format dan mengirim URL stream. [lookup Tidal](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/YouTube.kt:1927) melakukan request token lalu pencarian; tidak ditemukan timeout khusus lookup.
- **Dampak:** layanan metadata lambat dapat menunda mulai musik walau URL stream tersedia. Belum melakukan benchmark atau membuktikan ANR.
- **Perbaikan/uji:** kirim stream lebih dahulu, ambil metadata di background dengan cache/timeout singkat dan hanya bila diperlukan; simulasi Tidal hang/gagal harus tidak menghambat playback.

### F19 — P2 — Sleep timer “akhir lagu” memakai waktu dinding

- **Bukti:** [sleepStart](D:/Tan/Gratify/core/data/src/androidMain/kotlin/com/tan/data/mediaservice/MediaServiceHandlerImpl.kt:949) menghitung duration-position satu kali lalu delay(remaining).
- **Dampak:** pause, seek, perubahan kecepatan, atau pergantian lagu membuat timer berhenti pada waktu yang salah. Timer menit biasa tidak termasuk temuan ini.
- **Perbaikan/uji:** kaitkan mode akhir lagu dengan identitas track dan event timeline/ended; uji pause/resume, seek, speed 2x, dan next track.

## Temuan kecil

### F20 — P3 — Tombol More profil publik tidak menjalankan aksi

- **Bukti:** [More profil](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/social/UserProfileScreen.kt:347) menampilkan IconButton aktif dengan onClick TODO.
- **Dampak:** pengguna menekan tombol yang tidak merespons.
- **Perbaikan/uji:** sediakan menu yang tersedia atau sembunyikan/nonaktifkan tombol; setiap kontrol aktif harus menjalankan aksi dengan label aksesibilitas.

## Hal yang perlu diverifikasi sebelum menyatakan semua fitur selesai

1. **Supabase aktif:** terapkan/verifikasi policy ownership dan pembacaan publik, hotfix user-data/social, serta bucket avatar. Gunakan dua akun dan anon untuk memastikan SELECT/INSERT/UPDATE/DELETE sesuai hak akses. Tidak adanya akses admin pada audit ini berarti status server masih belum diketahui.
2. **Uji account recovery:** email OTP/reset, Google callback, token expired, logout offline, pergantian akun, gagal upload avatar.
3. **Uji dua perangkat:** konflik add/delete playlist/favorit, antrean, statistik, setting dan restart aplikasi.
4. **Playback nyata:** jaringan lambat/putus, background/screen off, Bluetooth, Android Auto, video/PIP, efek audio dan lirik. Ini juga diperlukan untuk menentukan penyebab UI terasa macet.
5. **Download/restore nyata:** resume, cache eviction, offline, storage penuh, ZIP tidak sah, Android API lama dan baru.
6. **Integrasi eksternal:** Spotify, Discord, provider lirik/AI, YouTube dan updater; layanan dapat berubah di luar kode.
7. **Skala data:** query cloud tanpa pagination perlu diuji melebihi batas row API. Supabase mendokumentasikan default 1.000 row yang dapat dikonfigurasi; nilai proyek aktif belum diketahui. [Referensi select Kotlin](https://supabase.com/docs/reference/kotlin/select).
8. **Isi backup:** archive menyalin preference/database yang dapat berisi cookie/token. Evaluasi pengecualian secret atau enkripsi dan kebijakan restore sesi; belum dibuat/diinspeksi backup akun nyata.
9. **Konsistensi UI:** lanjutkan pemeriksaan teks hardcoded, area sentuh kecil, empty/error/loading state, TalkBack, font besar, dan respons tablet pada layar yang belum memiliki bukti runtime.

## Urutan perbaikan yang disarankan

1. Isolasi akun dan konflik sinkronisasi (F01–F02).
2. Privasi server, logging secret, dependency TLS, restore ZIP (F03–F04, F06–F07).
3. Transaksi playlist dan konsistensi delete/follow (F05, F16–F17).
4. Recovery, signup, settings/history/queue dan Flow sosial (F08–F12).
5. AI, ekspor, desktop backup, startup playback, sleep timer dan tombol kosong (F13–F15, F18–F20).
6. Jalankan uji integrasi di atas, lalu audit ulang berdasarkan hasil aktual.

**Perubahan pada sesi audit:** hanya laporan dan artefak verifikasi. Source aplikasi tidak diperbaiki pada tahap ini. Perubahan UI/security dari sesi sebelumnya dipertahankan.
