# Audit fungsi internal Gratify — 1 Oktober 2026

## Penilaian dan hasil

Audit ini memperluas [audit seluruh fitur](D:/Tan/Gratify/AUDIT_FITUR_2026-10-01.md) dengan inventaris fungsi dan penelusuran kontrak internal, error, pagination, coroutine, database, download, serta parsing.

**Hasil tambahan: 16 temuan, terdiri dari 15 P2 dan 1 P3.** Tidak ada P1 baru pada tahap ini. Digabung dengan audit fitur: **36 temuan — 7 P1, 27 P2, 2 P3**, semuanya masih terbuka; source aplikasi belum diperbaiki dalam sesi audit.

**Belum layak disebut 100% benar atau siap rilis.** Selain risiko akun/privasi/sync dari audit sebelumnya, fungsi internal masih dapat menghilangkan baris dari tampilan playlist, mengambil token item playlist yang salah, menyatakan download gagal sebagai selesai, dan mencatat durasi mendengar yang keliru.

### Arti “seluruh fungsi” dalam hasil ini

Seluruh file sumber dalam scope dipindai untuk inventaris deklarasi dan pola berisiko. Fungsi yang berisiko kemudian ditelusuri secara manual bersama pemanggilnya dan, bila memungkinkan, direproduksi terisolasi.

**Tidak semua 3.395 deklarasi telah dieksekusi atau direview semantik satu per satu.** Angka tersebut juga berisi interface, expect/actual, override dan tes. Inventaris bukan sertifikasi bahwa setiap fungsi bekerja. Status pemeriksaan per deklarasi tersedia di CSV; fungsi tanpa penelusuran manual ditandai sebagai inventaris/pemindaian saja.

## Inventaris

| Ukuran | Hasil |
|---|---:|
| File sumber dipindai | 757 |
| Kotlin | 723 file |
| Java | 30 file |
| SQL | 4 file |
| Deklarasi fungsi bernama terdeteksi | sekitar 3.395 |
| Token terkait blocking | 142 |
| Catch Exception/Throwable | 276 |
| runCatching | 147 |
| TODO()/NotImplementedError() | 10 |
| Force unwrap !! | 81 |
| Pemanggilan operasi destruktif terpilih | 43 |

Angka pola di atas **bukan jumlah bug**. Beberapa blocking diperlukan pada adapter sinkron ExoPlayer; catch yang meneruskan cancellation juga dapat benar. Penelusuran konteks diperlukan sebelum menetapkan temuan.

Scope: composeApp/src, androidApp/src, desktopApp/src, core, crashlytics, crashlytics-empty, supabase, dan SQL pada root. File generated/build, dependency eksternal, skills, artefak audit dan credential lokal tidak dimasukkan ke inventaris. Dependency TLS telah diperiksa terpisah pada temuan F07 sebelumnya.

Metode adalah pemindaian leksikal, bukan AST compiler. Konstruktor, init block, accessor, lambda anonim, beberapa signature kompleks dan metode Java tanpa modifier dapat tidak tercatat sebagai fungsi bernama. Pola pada badan source tetap dipindai. Nilai secret tidak dimasukkan ke CSV.

Artefak:

- [functions.csv — fungsi, lokasi, modul, status review](D:/Tan/Gratify/artifacts/function-audit/functions.csv)
- [files.csv — file, hash dan jumlah pola](D:/Tan/Gratify/artifacts/function-audit/files.csv)
- [ringkasan inventaris](D:/Tan/Gratify/artifacts/function-audit/inventory-summary.json)
- [pemetaan temuan ke fungsi](D:/Tan/Gratify/artifacts/function-audit/review-map.json)
- [script inventaris](D:/Tan/Gratify/artifacts/function-audit/inventory.py)

## Kedalaman penelusuran

| Area | Fungsi/alur yang ditelusuri | Hasil |
|---|---|---|
| Auth/session/cloud | Login/logout, backup akun keluar, prune, settings/history/queue, follow, share | F01–F12/F16–F17 dari audit fitur tetap berlaku |
| Room/playlist/paging | load, getRefreshKey, query offset/time, getSetVideoId, reorder | F21–F23, F28 |
| Download ekspor | download, parallelDownload, singleThreadedDownload, consumer progress | F24–F25; F14 sebelumnya tetap berlaku |
| Cache offline | resolver download dan player, format expiry, pemakaian upstream | F31; player resolver sudah memiliki penanganan yang lebih lengkap |
| Parser dan utilitas | Cookie, mood/genre, HTML entity, LRC, Markdown | F26–F27, F33–F34, F36 |
| Playback | Timeline transition, listening event, volume normalization, sleep, queue save/restore | F30, F35; F18–F19 sebelumnya tetap berlaku |
| Social presence | observeNowPlayingState, ProfileHandler, heartbeat dan expiry Friends | F29 |
| Coroutine/lifecycle | Scope Discord, close, sendActivity, datastore setter di UI | F32, F35 |
| AI/Spotify/lirik/Canvas | Endpoint, token, parser mapping, pembatalan dan media ID guard | F06/F13 tetap berlaku; F33–F34 tambahan |
| Updater/Android Auto/platform | Handler updater, callback library/search, migrasi database JVM/iOS dan stub | Perlu integrasi nyata; catatan di bagian batas/lanjutan |
| UI Compose | Handler aksi terkait temuan; inventaris komponen lainnya | Sebagian telah diuji di sesi UI; bukan pengujian seluruh composable |

## Temuan tambahan

### F21 — P2 — Refresh playlist memakai indeks baris sebagai nomor halaman

- **Fungsi/bukti:** [paging/LocalPlaylistPagingSource](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/paging/LocalPlaylistPagingSource.kt:17) mengembalikan anchorPosition sebagai refresh key. load memperlakukannya sebagai currentPage; [db/datasource/LocalDataSource](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/db/datasource/LocalDataSource.kt:411) mengalikan offset dengan 50.
- **Pemicu/dampak:** refresh sesudah scroll/reorder dapat melompat ke data jauh di belakang, bahkan menghasilkan daftar kosong. Probe anchor 73 menghasilkan OFFSET 3650 pada playlist 120 baris; kontrol OFFSET 50 masih menghasilkan 50 baris.
- **Perbaikan:** hitung page key dari closestPageToPosition/prevKey/nextKey dan samakan ukuran page dengan query. [Kontrak PagingSource Android](https://developer.android.com/topic/libraries/architecture/paging/v3-paged-data).
- **Uji penerimaan:** scroll playlist 120+ lagu, refresh/reorder di tengah dan akhir; anchor tetap berada dekat lagu yang sama tanpa blank list.

### F22 — P2 — Pagination waktu melewatkan item dengan timestamp sama

- **Fungsi/bukti:** [paging/LocalPlaylistPagingSource](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/paging/LocalPlaylistPagingSource.kt:68) memakai timestamp saja sebagai cursor. [query time-based](D:/Tan/Gratify/core/data/src/commonMain/kotlin/DatabaseDao.kt:633) memakai >/< cutPoint, tetapi mengurutkan position. getNewestPlaylistPairSong juga berdasarkan position.
- **Pemicu/dampak:** item dengan waktu yang sama di batas halaman hilang dari tampilan. Setelah reorder, urutan position juga dapat tidak konsisten dengan cursor waktu. Probe 120 item bertimestamp sama hanya menghasilkan 1 item pada initial NewerFirst.
- **Perbaikan:** pilih urutan waktu yang konsisten dan cursor gabungan timestamp + ID unik sebagai tie breaker; jangan memakai position sebagai wakil “terbaru”.
- **Uji penerimaan:** seluruh 120 item tetap dapat dimuat pada OlderFirst/NewerFirst, termasuk timestamp sama dan playlist yang telah diurutkan ulang.

### F23 — P2 — Token item YouTube diambil tanpa identitas playlist

- **Fungsi/bukti:** [SetVideoIdEntity](D:/Tan/Gratify/core/domain/src/commonMain/kotlin/com/tan/domain/data/entities/SetVideoIdEntity.kt:5) mempunyai primary key videoId + youtubePlaylistId. [getSetVideoId](D:/Tan/Gratify/core/data/src/commonMain/kotlin/DatabaseDao.kt:598) hanya memfilter videoId. [repository/LocalPlaylistRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/LocalPlaylistRepositoryImpl.kt:601) dan baris 907/921/928 memakai hasilnya untuk delete/reorder.
- **Pemicu/dampak:** lagu sama pada playlist A dan B dapat memakai setVideoId milik A saat operasi di B; request ditolak atau tidak memperbarui item yang dimaksud. Probe SQL dua playlist mengambil token A ketika operasi bermaksud B.
- **Perbaikan:** bawa playlist ID sampai DAO dan filter kedua kolom; pastikan semua writer setVideoId mengisi youtubePlaylistId.
- **Uji penerimaan:** lagu sama pada dua playlist YouTube dapat dihapus/dipindah independen tanpa token tertukar.

### F24 — P2 — Hasil download gagal dibaca sebagai selesai

- **Fungsi/bukti:** [Ytmusic](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/Ytmusic.kt:908) dan baris 923 mengirim Triple(true, 0f, 0) saat onComplete gagal. [YouTube](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/YouTube.kt:2039) hanya membaca boolean pertama; true dianggap selesai, progress diubah menjadi 1f/isDone dan tahap konversi dilanjutkan.
- **Pemicu/dampak:** kegagalan transfer dapat memunculkan status selesai sementara dan memulai konversi file parsial/tidak ada. Jalur video juga dapat mencoba merge ketika transfer gagal. Model state mereproduksi ketidakcocokan producer/consumer; belum memutus download nyata.
- **Perbaikan:** gunakan hasil bertipe Progress/Completed/Failed/Cancelled dan propagasikan exception; konversi hanya setelah transfer sukses terverifikasi.
- **Uji penerimaan:** network/storage gagal tidak pernah menghasilkan isDone sukses dan tidak menjalankan merge/konversi.

### F25 — P2 — Retry download berhenti setelah kegagalan body pertama

- **Fungsi/bukti:** [Ytmusic](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/Ytmusic.kt:1067) melewati iterasi ketika jobDone == 1. [Ytmusic](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/Ytmusic.kt:1114) menetapkan jobDone = 1 saat body gagal, sehingga percobaan berikutnya tidak dijalankan.
- **Pemicu/dampak:** kegagalan sementara saat membaca body tidak mendapatkan retry yang diminta oleh maxRetries. Selain itu, kegagalan HEAD yang kemudian berhasil tidak selalu membersihkan lastException. Probe state menunjukkan hanya 1 dari 4 percobaan yang dimaksud dijalankan.
- **Perbaikan:** pisahkan state selesai suatu attempt dari keberhasilan keseluruhan; reset error per attempt, teruskan cancellation, dan verifikasi panjang/hasil final.
- **Uji penerimaan:** body percobaan pertama gagal, kedua berhasil; download dinyatakan berhasil dan jumlah percobaan sesuai konfigurasi.

### F26 — P2 — Parser cookie memotong nilai dan membutuhkan spasi tertentu

- **Fungsi/bukti:** [utils/Utils](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/utils/Utils.kt:25) menggunakan split("; ") dan split("="), lalu hanya mengambil dua bagian. [Ytmusic](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/Ytmusic.kt:127) memakainya dalam setter cookie; cookieMap digunakan untuk hash Authorization.
- **Pemicu/dampak:** nilai padded seperti synthetic== menjadi synthetic; separator semicolon tanpa spasi membuat cookie berikutnya tidak terpisah. Ini dapat menghasilkan sesi/hash autentikasi salah. Kedua kasus direproduksi dengan fungsi source asli dan cookie sintetis.
- **Perbaikan:** split berdasarkan semicolon lalu trim; split nama/nilai dengan limit = 2 dan tangani fragmen tidak sah.
- **Uji penerimaan:** cookie padded, spasi opsional, nilai kosong dan fragmen tidak sah tidak merusak nilai atau menjatuhkan setter.

### F27 — P2 — Error parser mood/genre dibuang tanpa hasil error

- **Fungsi/bukti:** [repository/HomeRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/HomeRepositoryImpl.kt:288) dan getGenreData membungkus operasi dalam runCatching tanpa menangani hasil kegagalan luar. [parser/MoodsGenresParser](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/parser/MoodsGenresParser.kt:14) memakai get(0) pada list nullable yang dapat kosong.
- **Pemicu/dampak:** respons berisi list kosong memicu exception parser; repository dapat selesai tanpa emit Success maupun Error. UI kehilangan penyebab kegagalan, dan data lama/null dapat tertinggal. Probe mereproduksi pola Flow kosong setelah exception; bukan respons YouTube nyata.
- **Perbaikan:** parser menggunakan firstOrNull/getOrNull; repository menangani exception dan cancellation secara eksplisit; ViewModel menyediakan error/retry dan finally untuk loading.
- **Uji penerimaan:** respons null, array kosong dan schema berubah menghasilkan error yang terlihat dan dapat dicoba ulang, bukan penyelesaian diam-diam.

### F28 — P2 — Drag playlist lokal menukar dua posisi, bukan memindahkan item

- **Fungsi/bukti:** [LocalPlaylistViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/LocalPlaylistViewModel.kt:1233) cabang unsynced hanya mengubah posisi from dan to. [drag selesai](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/component/RememberDragDropState.kt:145) mengirim pasangan asal/tujuan akhir. Cabang synced justru menggeser rentang.
- **Pemicu/dampak:** drag A dari posisi 0 ke 3 pada ABCD menghasilkan DBCA, sementara operasi move menghasilkan BCDA. Probe menjalankan query UPDATE DAO yang sama; dua update terpisah juga dapat meninggalkan posisi tidak konsisten bila gagal di tengah.
- **Perbaikan:** gunakan operasi move yang menggeser rentang dan satu transaksi Room, dengan validasi indeks dan posisi unik.
- **Uji penerimaan:** drag naik/turun melewati beberapa lagu memberi urutan yang sama untuk playlist lokal dan synced; kegagalan transaksi tidak meninggalkan duplikasi posisi.

### F29 — P2 — Heartbeat sosial tersedia tetapi tidak diaktifkan

- **Fungsi/bukti:** [mediaservice/ProfileSyncManager](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/mediaservice/ProfileSyncManager.kt:64) memiliki heartbeat 2 menit, tetapi tidak ditemukan pemanggilan startSync pada source scope. Jalur aktif [SharedViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SharedViewModel.kt:336) memanggil [handler/ProfileHandler](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/handler/ProfileHandler.kt:22) pada perubahan identitas track. [FriendsActivityViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/FriendsActivityViewModel.kt:74) menganggap status expired setelah 10 menit.
- **Pemicu/dampak:** mendengar podcast/track panjang tanpa perubahan track dapat terlihat Offline setelah 10 menit walaupun musik masih berjalan. Ini kesimpulan dari alur source; belum diuji dengan dua akun nyata.
- **Perbaikan:** hubungkan satu service presence dengan lifecycle playback/auth, heartbeat, pause/resume, timeout dan retry; hindari dua publisher yang bersaing.
- **Uji penerimaan:** track 15+ menit tetap tampil aktif di akun teman; pause/logout menghentikan presence dan heartbeat.

### F30 — P2 — Durasi mendengar dihitung dari posisi, bukan waktu yang didengar

- **Fungsi/bukti:** [MediaServiceHandlerImpl](D:/Tan/Gratify/core/data/src/androidMain/kotlin/com/tan/data/mediaservice/MediaServiceHandlerImpl.kt:2352) meneruskan progress ke mayBeTrackingListeningLocal; [MediaServiceHandlerImpl](D:/Tan/Gratify/core/data/src/androidMain/kotlin/com/tan/data/mediaservice/MediaServiceHandlerImpl.kt:2393) menggunakan currentPositionMillis/duration untuk menentukan durasi event.
- **Pemicu/dampak:** seek mendekati akhir lagu lalu next dapat mencatat hampir/seluruh durasi sebagai didengar walau pengguna baru mendengar sebentar. Pada model lagu 60 detik, posisi 55 detik mencatat 60 detik. Replay/seek mundur dan playback speed juga tidak dimodelkan sebagai waktu mendengar aktual.
- **Perbaikan:** akumulasi interval saat benar-benar playing dengan clock monotonic dan reset/checkpoint per track; bedakan durasi media yang dilalui dari waktu mendengar.
- **Uji penerimaan:** dengar 2 detik → seek ke 55 detik → next pada lagu 60 detik tidak mencatat 60 detik; pause, speed dan replay sesuai metrik yang dipilih.

### F31 — P2 — Resolver download dapat mengembalikan URI ID saat cache hanya sebagian

- **Fungsi/bukti:** [resolver download](D:/Tan/Gratify/core/media/media3/src/main/java/com/tan/media3/service/download/DownloadUtils.kt:67) memeriksa cache dengan length = 1 bila panjang belum diketahui, lalu mengembalikan dataSpec asli sebelum resolusi URL.
- **Pemicu/dampak:** awal span ada di playerCache tetapi file belum penuh; request berikutnya memerlukan upstream dengan URI berupa videoId. CacheDataSource menggunakan upstream ketika cache tidak memenuhi seluruh request. Ini penelusuran source, belum reproduksi download perangkat. [Kontrak CacheDataSource](https://developer.android.com/reference/androidx/media3/datasource/cache/CacheDataSource).
- **Perbaikan:** pastikan URI jaringan valid untuk cache parsial atau buktikan seluruh range tersedia sebelum early return; samakan penanganan format/error dengan resolver player.
- **Uji penerimaan:** putar sebagian lagu → download penuh → airplane mode; download tidak gagal pada cache boundary dan seluruh lagu dapat diputar offline.

### F32 — P2 — CoroutineScope Discord menghasilkan Job baru setiap pembacaan

- **Fungsi/bukti:** [Discord scope](D:/Tan/Gratify/core/service/kizzy/src/commonMain/kotlin/com/my/kizzy/gateway/DiscordWebSocket.kt:78) memakai getter SupervisorJob() baru. close() pada baris 297 memanggil this.cancel(); sendActivity pada baris 309 menunggu koneksi tanpa deadline.
- **Pemicu/dampak:** scope.cancel() membatalkan Job baru, bukan seluruh job yang telah diluncurkan. reconnectionJob/heartbeatJob memang dibatalkan eksplisit, tetapi itu tidak memperbaiki kontrak scope bagi task lain. Probe API coroutine yang sama menunjukkan child tetap aktif sesudah cancel.
- **Perbaikan:** simpan satu parent Job/CoroutineContext; cancel seluruh lifecycle yang dimiliki; beri deadline/error pada penantian koneksi dan lepaskan HTTP client sesuai lifecycle.
- **Uji penerimaan:** close saat connect/send sedang berjalan membuat semua task terkait selesai tanpa publisher tertinggal; token salah/offline menghasilkan error dalam waktu terbatas.

### F33 — P2 — HTML entity emoji dapat melempar exception

- **Fungsi/bukti:** [decodeHtmlEntities](D:/Tan/Gratify/core/domain/src/commonMain/kotlin/com/tan/domain/extension/AllExt.kt:176) memakai Char(codePoint) untuk entity hex/decimal.
- **Pemicu/dampak:** code point valid di luar BMP, misalnya `&#128512;`, tidak muat dalam satu Char dan memicu exception. Parser lirik memanggil decoder ini. Probe source asli gagal untuk entity tersebut; kontrol `&#65;` menghasilkan A.
- **Perbaikan:** encode Unicode scalar ke satu/dua UTF-16 code unit, validasi rentang/surrogate, dan pertahankan entity tidak sah tanpa crash.
- **Uji penerimaan:** entity emoji decimal/hex, BMP dan nilai tidak sah tidak menjatuhkan parser; emoji tampil utuh.

### F34 — P2 — Parser LRC mengabaikan timestamp millisecond

- **Fungsi/bukti:** [parseSyncedLyrics](D:/Tan/Gratify/core/service/lyricsService/src/commonMain/kotlin/com/tan/gratify/lyrics/parser/LrcTextParser.kt:6) mensyaratkan tepat dua digit pecahan waktu. [pemanggil LRCLIB](D:/Tan/Gratify/core/service/lyricsService/src/commonMain/kotlin/com/tan/gratify/lyrics/GratifyLyricsClient.kt:137) dan mapping response memakai fungsi ini.
- **Pemicu/dampak:** [00:01.500]synthetic lyric menghasilkan nol baris, sedangkan [00:01.50] diterima. Lagu dapat dinyatakan memiliki synced lyrics tetapi tampil kosong; fallback plain tidak otomatis dijalankan ketika hasil parse kosong.
- **Perbaikan:** dukung precision yang dibutuhkan, normalisasi waktu, multi-tag/offset bila relevan, dan fallback/error jika tidak ada baris sah.
- **Uji penerimaan:** input 2/3 digit precision memberikan waktu yang sama untuk 1.500 detik; malformed/empty input menghasilkan fallback yang jelas.

### F35 — P2 — Setter datastore memblokir thread UI

- **Fungsi/bukti:** [dataStore/DataStoreManagerImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/dataStore/DataStoreManagerImpl.kt:959) setPlaybackSpeed dan baris 972 setPitch memakai runBlocking saat edit DataStore. [NowPlayingBottomSheetViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/NowPlayingBottomSheetViewModel.kt:218) meluncurkan handler di viewModelScope tanpa dispatcher IO lalu memanggil kedua setter pada baris 360. Constructor Mood dan media handler juga melakukan blocking read.
- **Pemicu/dampak:** thread UI tertahan selama operasi persistence selesai, terutama pada I/O lambat/kontensi. Ini alasan kode yang dapat menyebabkan jank; audit belum mengukur ANR atau frame time pada telepon nyata. runBlocking memang memblokir thread pemanggil. [Dokumentasi coroutine Kotlin](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html).
- **Perbaikan:** setter suspend, edit yang dikoordinasikan tanpa blocking, cache state yang diperlukan callback sinkron, dan inisialisasi asynchronous.
- **Uji penerimaan:** ubah speed/pitch cepat saat storage lambat; UI tetap merespons dan nilai akhir tersimpan. Ukur frame time, bukan hanya build lulus.

### F36 — P3 — Fungsi stripMarkdown tidak menghapus link/image

- **Fungsi/bukti:** [extension/StringExt](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/extension/StringExt.kt:15) memakai pola regex berisi fragmen literal begin:math/display/text untuk image/link.
- **Pemicu/dampak:** probe source asli mempertahankan [title](https://example.invalid). Tidak ditemukan pemanggil fungsi ini dalam scope source, sehingga belum ada dampak pada alur produksi yang terbukti.
- **Perbaikan:** hapus utilitas bila tidak dibutuhkan atau gunakan parser/pola Markdown yang benar sesuai hasil yang diinginkan.
- **Uji penerimaan:** link/image dan teks biasa diproses sesuai kontrak, tanpa menghilangkan isi yang seharusnya dipertahankan.

## Verifikasi terisolasi

Ada **15 pemeriksaan baru**: 4 SQLite/algoritma dan 11 Kotlin, termasuk 1 kontrol Unicode yang berhasil. Runner berhasil dengan exit code 0.

- SQLite menggunakan query yang diambil dari DatabaseDao asli, dengan database :memory: berisi data sintetis.
- Kotlin mengompilasi fungsi asli parseCookieString, decodeHtmlEntities, parseSyncedLyrics dan stripMarkdown yang diekstrak tanpa mengubah badan fungsi. Model Lyrics asli ikut dikompilasi.
- Pola Flow, state download/retry, statistik posisi dan scope coroutine diuji terisolasi; ini tidak menjalankan service/repository aplikasi lengkap.
- Probe tidak mengakses credential, jaringan, akun, file database aplikasi, atau perangkat Android.

Bukti: [hasil SQL](D:/Tan/Gratify/artifacts/function-audit/sql-probe-results.json), [hasil Kotlin](D:/Tan/Gratify/artifacts/function-audit/kotlin-probe-results.json), [probe Kotlin](D:/Tan/Gratify/artifacts/function-audit/FunctionAuditProbes.kt), [SQL dan extraction](D:/Tan/Gratify/artifacts/function-audit/probes.py), [runner](D:/Tan/Gratify/artifacts/function-audit/run-probes.ps1).

```powershell
python artifacts/function-audit/inventory.py
powershell -NoProfile -File artifacts/function-audit/run-probes.ps1
```

Build/lint aplikasi dari audit fitur pada hari yang sama tetap menjadi bukti compile: build berhasil, 3 tes VersionManager lulus, 0 lint error, 121 warning. **Build tersebut tidak diulang sebagai uji seluruh fungsi.** Tidak ada perubahan source aplikasi setelahnya dalam sesi audit ini.

## Kandidat yang tidak dihitung sebagai temuan baru

- runBlocking pada resolver ExoPlayer: adapter API sinkron memang memerlukan bridging; tidak otomatis disamakan dengan blocking UI.
- first() pada aksi radio Artist/Podcast: pemeriksaan list tidak kosong sudah ada.
- Lyrics/Canvas late response: guard media ID/cancellation sudah ada pada jalur yang ditinjau; tidak ditetapkan sebagai bug baru tanpa reproduksi tambahan.
- AnalyticsRepository: flow query finite; tidak ada bukti collector selalu menggantung pada fungsi yang ditinjau.
- Root Android Auto, paging hasil search, overlapping request, serta return onSetMediaItems: perlu uji MediaBrowser/controller nyata sebelum menyimpulkan seluruh Android Auto rusak.
- Migrasi JVM/iOS: builder tidak mendaftarkan migrasi manual Android 5→6, 10→11, 12→13. Upgrade database lama pada target tersebut perlu diuji; target ini belum rilis publik.
- Stub iOS mencakup extractor, Brotli/HMAC/TOTP; country/language juga perlu diperiksa. Inventaris menunjukkan platform belum lengkap, bukan jaminan fungsi iOS.
- Proxy download, content range/ukuran chunk, validasi archive sebelum overwrite, HTTP client lifecycle, resource release, dan stabilitas crossfade: perlu pengujian fault injection lebih lanjut; tidak dianggap lulus karena belum ada finding.

## Urutan tindak lanjut

1. Selesaikan tujuh P1 audit fitur, terutama isolasi akun dan penghapusan lintas perangkat.
2. Benahi kontrak hasil download, retry dan resolver cache (F24–F25/F31).
3. Benahi pagination, identitas item YouTube dan transaksi reorder (F21–F23/F28).
4. Benahi parser/cookie/lirik dan lifecycle coroutine (F26–F27/F32–F35).
5. Benahi presence dan akurasi analytics (F29–F30).
6. Tambahkan tes integrasi untuk akun, jaringan, storage, service Android, dan performa UI pada perangkat nyata; lalu tandai status setiap fungsi yang benar-benar diuji.

**Perubahan sesi ini hanya pada laporan dan artefak audit.** Semua temuan merupakan pekerjaan perbaikan yang masih harus dilakukan.
