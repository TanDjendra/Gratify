# Full app launch audit — Gratify

**Project:** Gratify<br>
**Version:** 2.1.0 (61)<br>
**Tanggal audit:** 1 Oktober 2026<br>
**Auditor:** Codex — pemeriksaan source, artefak dan probe lokal<br>
**Environment:** workspace lokal Windows; build Android debug/release. Staging/production server belum diverifikasi.<br>
**Status:** **NEED FIX BEFORE LAUNCH — release gate belum lulus.**

## Ringkasan keputusan

Gratify belum siap rilis berdasarkan bukti yang tersedia. Audit fitur/fungsi sebelumnya memiliki **36 temuan terbuka: 7 P1, 27 P2, 2 P3**. Pemeriksaan checklist ini menambahkan **5 gap launch L01–L05**; gap ini dicatat terpisah agar tidak mengubah penomoran audit sebelumnya.

Prioritas pertama: isolasi akun dan sinkronisasi data, enforcement privasi, logging token/cookie, TLS dependency, restore ZIP dan penulisan playlist atomik. Penghapusan akun, tautan kebijakan privasi, monitoring, konsistensi config CI dan metadata juga perlu diselesaikan. Tidak ada perubahan source aplikasi atau server selama audit checklist ini.

**Build release + lint berhasil** dalam 5 menit 52 detik: 0 error lint, 121 warning. APK universal 49,40 MiB dengan package/version yang benar; tidak menyertakan activity preview. APK **unsigned**; pemeriksaan signature gagal sebagaimana diharapkan untuk artefak unsigned. Belum diuji instalasi, pembaruan atau UAT release.

### Arti status

| Status | Makna |
|---|---|
| PASS / [x] | Bukti memenuhi pemeriksaan dalam cakupan yang dijelaskan. |
| FAIL | Source/probe menunjukkan cacat atau artefak wajib belum tersedia. Bukan otomatis klaim uji produksi dijalankan. |
| PARTIAL | Implementasi/bukti sebagian tersedia; belum memenuhi seluruh kriteria. |
| UNVERIFIED | Belum cukup bukti/uji/akses; tidak dianggap PASS maupun kegagalan runtime yang terukur. |
| N/A | Tidak berlaku pada produk/cakupan ini; alasan dicatat. |

Ada **251 kotak** pada template, termasuk **10 pilihan PASS/FAIL UAT** dan **12 release gates**. Semua dipetakan ke CSV/JSON. Jumlah kotak bukan persentase kebenaran aplikasi; pilihan UAT bukan dua tes independen. Tidak semua 3.395 deklarasi fungsi telah direview semantik/dieksekusi. Bukti runtime UI menggunakan hasil sesi sebelumnya, bukan pengujian perangkat baru dalam audit ini.

### Daftar bukti

- **E1:** [Audit fitur: 20 temuan, debug/JVM/lint dan 7 probe](D:/Tan/Gratify/AUDIT_FITUR_2026-10-01.md)
- **E2:** [Audit fungsi: 16 tambahan; 757 file, 15 probe](D:/Tan/Gratify/AUDIT_FUNGSI_2026-10-01.md)
- **E3:** [Pemeriksaan visual/runtime UI sebelumnya](D:/Tan/Gratify/UI_REFRESH.md)
- **E4:** [Verifikasi release baru: ukuran, hash, lint, manifest, unsigned](D:/Tan/Gratify/artifacts/launch-audit/release-verification.json)
- **E5:** [Hotfix ownership lokal; belum bukti policy server](D:/Tan/Gratify/supabase_user_data_rls_hotfix.sql)
- **E5 sosial:** [Policy sosial lokal](D:/Tan/Gratify/supabase_social_rls_hotfix.sql)
- **E6:** [Workflow release utama dengan guard config](D:/Tan/Gratify/.github/workflows/android-release.yml)
- **E7:** [Scope, platform, pihak ketiga dan kontak](D:/Tan/Gratify/README.md)
- **E8:** [Metadata dan changelog store](D:/Tan/Gratify/fastlane/metadata/android/en-US/changelogs/61.txt)
- **E9:** [Service FCM: handler dan token hanya dicatat](D:/Tan/Gratify/androidApp/src/main/java/com/tan/gratify/service/MyFirebaseMessagingService.kt)
- **E10:** [Worker backup lokal](D:/Tan/Gratify/androidApp/src/main/java/com/tan/gratify/service/backup/AutoBackupWorker.kt)
- **E11:** [Permission dan config aplikasi](D:/Tan/Gratify/androidApp/src/main/AndroidManifest.xml)
- **E12:** [SDK Supabase/Auth/redirect](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/di/SupabaseModule.kt)

Bukti release tambahan: [build log](D:/Tan/Gratify/artifacts/launch-audit/release-build.log), [manifest APK](D:/Tan/Gratify/artifacts/launch-audit/release-manifest.txt), [lint release XML](D:/Tan/Gratify/androidApp/build/reports/lint-results-release.xml). Pemeriksaan hash mengonfirmasi **757 file inventaris tetap sama** dengan audit fungsi. Tidak ada akses admin DB (`SUPABASE_DB_URL` belum tersedia); policy aktif, bucket, secret CI dan backup server belum dinilai. Nilai credential tidak dimasukkan laporan. Pemeriksaan nama file Git menemukan 0 file credential sensitif yang dilacak; belum merupakan scan nilai/history secret. Tidak dilakukan load test pada server, instalasi pada perangkat pengguna, atau perubahan data akun.

---

### Rekap administrasi checklist

**FAIL: 56**, **N/A: 6**, **PARTIAL: 95**, **PASS: 10**, **UNVERIFIED: 84**. Sepuluh pilihan UAT termasuk UNVERIFIED; rekap ini bukan skor kesiapan.

## 01 — PRODUCT & REQUIREMENTS

* [ ] **FAIL** — Semua fitur yang dijanjikan sudah tersedia. Ada recovery/queue cloud yang belum lengkap dan klaim Apple pada changelog tidak sesuai UI; F08, F11, L05.
* [x] **PASS** — Scope aplikasi sudah jelas. README menjelaskan aplikasi musik Android beta dan status desktop WIP; E7. Bukan bukti semua requirement terpenuhi.
* [ ] **FAIL** — Tidak ada fitur penting yang masih setengah jadi. Reset password, pemulihan antrean dan sejumlah aksi belum lengkap; F08, F11, F20.
* [ ] **PARTIAL** — User flow utama sudah diuji. Navigasi dan Home nyata pernah diuji; akun, streaming, sync dan restore belum end to end; E1, E3.
* [ ] **UNVERIFIED** — Semua requirement client sudah terpenuhi. Belum ada matriks requirement yang disetujui dan UAT lengkap; E1, E2.
* [ ] **PARTIAL** — Fitur yang belum tersedia sudah didokumentasikan. Desktop/iOS/F-Droid dijelaskan; tidak semua keterbatasan fitur dicatat pada dokumen produk; E7, E1.
* [ ] **PARTIAL** — Tidak ada placeholder/test data yang tertinggal. Activity data contoh tidak ada dalam manifest release; masih ada aksi TODO tanpa fungsi; E4, F20.

**Catatan:** Penilaian source dan bukti runtime dibedakan; daftar bukti di atas berlaku untuk seluruh checklist.

---

---

## 02 — UI / UX AUDIT

### Visual

* [ ] **PARTIAL** — Layout konsisten. Token/komponen bersama diterapkan; bukti visual hanya layar terpilih Android; E3.
* [x] **PASS** — Font konsisten. Tema memakai tipografi bersama Poppins; bukti layar yang diperiksa tersedia; E3.
* [x] **PASS** — Warna konsisten. Palet gelap/hijau dan token warna bersama diterapkan; E3.
* [ ] **PARTIAL** — Icon benar. Ikon layar utama ditinjau; audit semantik seluruh ikon belum lengkap; E3.
* [ ] **PARTIAL** — Logo dan branding benar. Nama/logo Gratify tersedia; seluruh variasi aset/store belum dicocokkan; E3, E8.
* [ ] **UNVERIFIED** — Tidak ada typo. Belum ada proofreading semua bahasa, pesan error, dan metadata.
* [ ] **PARTIAL** — Tidak ada elemen yang terpotong. Home/Settings font 1,3x dan Home tablet diperiksa; seluruh layar/ukuran belum; E3.

### State

* [ ] **PARTIAL** — Loading state. Loading dan timeout ada; Mood/Genre bisa berhenti tanpa hasil/error; F27.
* [ ] **PARTIAL** — Empty state. Empty state Home/Library tersedia; pagination dapat membuat kosong palsu; F21, F22.
* [ ] **FAIL** — Error state. Kegagalan beberapa operasi ditelan atau dilaporkan sukses; F05, F09, F24, F27.
* [ ] **FAIL** — Success state. Success dapat tampil ketika cloud/download gagal; F05, F16, F24.
* [ ] **PARTIAL** — Offline/no internet state. Komponen OfflineErrorState tersedia; recovery akun/offline belum lulus; F01, F31.
* [ ] **PARTIAL** — Disabled button/state. Form menyediakan loading/enabled guard; semua state belum diuji; E3.

### Interaction

* [ ] **FAIL** — Semua tombol berfungsi. Tombol More profil publik masih TODO; F20. Ada aksi lain yang salah hasil; F08.
* [ ] **PARTIAL** — Form mudah digunakan. Form email/OTP diperiksa secara visual; completion dengan akun nyata belum; E3.
* [ ] **PARTIAL** — Keyboard tidak menutupi input penting. Form email dapat digulir saat keyboard terbuka pada emulator; form lain belum seluruhnya; E3.
* [ ] **PARTIAL** — Back navigation bekerja. Navigasi tab/drawer/Settings/Profile pernah diperiksa; stack/deep link lengkap belum; E3.
* [ ] **PARTIAL** — Dialog/modal dapat ditutup. Dialog memakai komponen bersama; semua modal/skenario dismiss belum diuji; E3.
* [ ] **UNVERIFIED** — Double-click/tap tidak menyebabkan duplicate action. Bukti UI mencakup sebagian layar Android; seluruh state/aksi belum diuji; E3.

---

## 03 — AUTHENTICATION

* [ ] **FAIL** — Register. Update password sesudah OTP gagal dapat tetap dianggap authenticated; F09.
* [ ] **PARTIAL** — Login. Email/Google tersedia di client; login produksi belum diuji; E1.
* [ ] **FAIL** — Logout. Logout gagal backup dapat mempertahankan data akun lama; F01.
* [ ] **PARTIAL** — Forgot password. Request recovery tersedia; completion melalui email/deep link belum berhasil dibuktikan; F08.
* [ ] **FAIL** — Reset password. State Ready/form password baru tidak tersambung lengkap; F08.
* [ ] **FAIL** — Email/phone verification jika diperlukan. OTP ada, tetapi kegagalan lanjutan tidak memblokir sukses signup; F09.
* [ ] **PARTIAL** — Session expiration. SDK session manager digunakan; expiry/recovery nyata belum diuji; E1.
* [ ] **PARTIAL** — Token refresh. SDK Auth menangani sesi; refresh token invalid/revoked belum diuji; E1.
* [ ] **FAIL** — Account deletion. Tidak ditemukan alur hapus akun Supabase beserta data; hapus Google account hanya lokal; L01.
* [ ] **PARTIAL** — Login gagal ditangani dengan benar. Error login ditangani pada client; respons akun tidak ada/disabled belum diuji nyata.
* [ ] **UNVERIFIED** — Brute-force/rate limiting diperhatikan. Implementasi ada, tetapi skenario akun Supabase nyata belum dijalankan; E1.
* [ ] **PARTIAL** — Password tidak disimpan sebagai plaintext. Tidak ditemukan persist password pengguna oleh alur auth yang ditinjau; SDK/backend dan dump backup belum diaudit lengkap.

### Test

* [ ] **UNVERIFIED** — Password salah. Implementasi ada, tetapi skenario akun Supabase nyata belum dijalankan; E1.
* [ ] **UNVERIFIED** — Email/user tidak ditemukan. Implementasi ada, tetapi skenario akun Supabase nyata belum dijalankan; E1.
* [ ] **UNVERIFIED** — Account disabled. Implementasi ada, tetapi skenario akun Supabase nyata belum dijalankan; E1.
* [ ] **UNVERIFIED** — Token expired. Implementasi ada, tetapi skenario akun Supabase nyata belum dijalankan; E1.
* [ ] **UNVERIFIED** — Token invalid. Implementasi ada, tetapi skenario akun Supabase nyata belum dijalankan; E1.
* [ ] **UNVERIFIED** — Login dari device berbeda. Implementasi ada, tetapi skenario akun Supabase nyata belum dijalankan; E1.

---

## 04 — AUTHORIZATION 🔐

> Pastikan user hanya dapat mengakses resource yang memang menjadi haknya.

* [ ] **FAIL** — User A tidak dapat membaca data User B. Isolasi aplikasi saat pergantian akun gagal: data A dapat muncul/terunggah sebagai B; F01. RLS server belum diuji.
* [ ] **PARTIAL** — User A tidak dapat mengubah data User B. SQL lokal menerapkan ownership auth.uid; enforcement server aktif belum dibuktikan; E5.
* [ ] **PARTIAL** — User A tidak dapat menghapus data User B. SQL lokal menerapkan ownership delete; server aktif belum dibuktikan; E5.
* [ ] **FAIL** — Role permission bekerja. Kontrol privasi profil tidak diterapkan pada pembacaan publik; F03. Role private-data server belum diuji.
* [ ] **N/A** — Admin permission bekerja. Tidak ada role/admin panel produk; admin Supabase adalah fungsi operasional di luar client.
* [ ] **PARTIAL** — Endpoint API memvalidasi authorization. SDK Auth/RLS dirancang untuk endpoint cloud; policy aktif belum diperiksa; E5.
* [ ] **PARTIAL** — ID/resource ownership diverifikasi di backend. USING/WITH CHECK auth.uid ada pada hotfix lokal; belum membuktikan deployment; E5.
* [ ] **PARTIAL** — Hidden UI bukan satu-satunya mekanisme keamanan. RLS backend tertulis di SQL, bukan hanya UI; flags privasi tetap lokal; E5, F03.

### Role Matrix

| Feature | Guest | User | Admin |
|---|---|---|---|
| View public data | Diizinkan untuk data public sesuai SQL | Diizinkan | N/A dalam produk |
| View own private cloud data | Ditolak sesuai SQL | Milik sendiri; live belum diuji | N/A |
| Edit own data | Ditolak sesuai SQL | Milik sendiri; live belum diuji | N/A |
| View other users | Profil/follow/public playlist saja; privasi F03 | Data public saja; privasi F03 | N/A |
| Delete users | Tidak tersedia | Penghapusan akun sendiri belum ada (L01) | Operasional Supabase di luar client; belum diaudit |
| Admin panel | Tidak tersedia | Tidak tersedia | Tidak ada panel admin Gratify |

Matrix adalah kontrak source, bukan hasil pengujian authorization live. F01 dapat membawa data akun lain ke sesi baru meskipun RLS server benar. Perlu token A/B/guest untuk SELECT/INSERT/UPDATE/DELETE dan percobaan ID milik pihak lain, termasuk bucket. Privasi public berbeda dari isolasi private data.


---

## 05 — API AUDIT

* [ ] **PARTIAL** — Semua endpoint menggunakan HTTPS. Endpoint bawaan umumnya HTTPS; URL AI kustom belum dibatasi HTTPS dan TLS bermasalah; F07.
* [ ] **PARTIAL** — Authentication diterapkan. Supabase SDK Auth/Bearer digunakan; pengujian guest/token invalid belum; E1, E5.
* [ ] **FAIL** — Authorization diterapkan. Privasi sosial dan isolasi akun client gagal; F01, F03. RLS live belum diverifikasi.
* [ ] **PARTIAL** — Input validation. Validasi form/query sebagian tersedia; seluruh input/batas server belum diuji.
* [ ] **FAIL** — Output validation. Parser dapat gagal atau tidak mengeluarkan state; F26, F27, F33, F34.
* [ ] **UNVERIFIED** — Rate limiting. Kontrak client ditinjau; konfigurasi dan respons server aktif belum diverifikasi; E1, E5.
* [ ] **FAIL** — Pagination. Refresh/cursor playlist dapat melewatkan data; F21, F22. Batas query cloud belum diuji pada data besar.
* [ ] **PARTIAL** — Error response aman. Error client ditangani sebagian; beberapa ditelan; respons server belum diaudit; F05, F12, F27.
* [ ] **UNVERIFIED** — Tidak membocorkan stack trace. Belum memeriksa respons error server terhadap seluruh skenario malformed/authorization.
* [ ] **UNVERIFIED** — Tidak membocorkan database information. Belum memeriksa kebocoran detail database pada respons server aktif.
* [ ] **PARTIAL** — API tidak menerima parameter berbahaya. Query SDK/Room memakai parameter; fuzzing dan ownership server aktif belum; E2, E5.
* [ ] **PARTIAL** — HTTP methods sesuai kebutuhan. SDK memakai operasi select/insert/update/delete; atomicity replacement gagal; F05.
* [ ] **N/A** — CORS dikonfigurasi dengan benar. Audit ini mencakup client native Android; tidak ada frontend browser yang diuji. CORS layanan web lain belum dinilai.
* [x] **PASS** — API versioning jika diperlukan. API SDK Supabase /auth/v1, /rest/v1, /storage/v1 dan endpoint AI memiliki versi; bukan bukti kompatibilitas setiap layanan.

### Endpoint Checklist

Endpoint generik template dipetakan ke API yang benar-benar digunakan oleh SDK Gratify:

| Template → API | Auth | Authorization | Validation | Rate Limit | Status |
|---|---|---|---|---|---|
| /login → /auth/v1 (sign-in/OTP/recovery) | Public client key; password/OTP/session | SDK/backend; live belum diuji | Form sebagian; F08/F09 | Belum diverifikasi | PARTIAL/FAIL flow terkait |
| /users → /rest/v1/profiles, follows | Public/session sesuai operasi | RLS SQL tersedia; live belum diuji | Sebagian | Belum diverifikasi | FAIL privasi F03 |
| /profile → /rest/v1/profiles | Bearer untuk tulis | Ownership SQL; live belum diuji | Sebagian; optimistic failure F16 | Belum diverifikasi | PARTIAL/FAIL |
| /upload → /storage/v1 + cloud table terkait | SDK Storage/Bearer | Bucket live belum diverifikasi | MIME/ukuran/path belum lengkap | Belum diverifikasi | UNVERIFIED |
| /admin | Tidak ada client endpoint | N/A | N/A | N/A | N/A |
| Cloud library → /rest/v1/cloud_* | Bearer | Ownership SQL; live belum diuji | Sync/atomicity F01/F02/F05 | Belum diverifikasi | FAIL integritas client |

Nama tabel tepat dan payload mengikuti repository/SQL E1/E5; baris cloud_* adalah kelompok tabel, bukan URL literal. Endpoint pihak ketiga (YouTube, AI, Spotify, Discord) memerlukan audit token/batas/fallback per penyedia. Hanya API key public/publishable yang boleh dikirim sebagai konfigurasi client; jangan menanam service-role/admin key dalam APK.


---

## 06 — DATABASE AUDIT

* [ ] **PARTIAL** — Schema sudah final. Schema/Room dan SQL tersedia; kesesuaian versi deployment belum diverifikasi; E2, E5.
* [ ] **PARTIAL** — Relationship benar. Relasi entity dan SQL ditinjau; data orphan/live belum diuji; E2, E5.
* [ ] **PARTIAL** — Foreign key benar. FK lokal/cloud dideklarasikan; seluruh cascade dan server aktif belum diuji; E2, E5.
* [ ] **PARTIAL** — Constraint diterapkan. Constraint tersedia pada source; atomicity/penggunaan query tetap salah; F05, F23.
* [ ] **PARTIAL** — Unique field diterapkan jika diperlukan. Key unik dideklarasikan; query set_video_id mengabaikan bagian key; F23.
* [ ] **PARTIAL** — Index pada query penting. Indeks ada pada schema; EXPLAIN dan index server aktif belum; E2, E5.
* [ ] **UNVERIFIED** — Tidak ada duplicate data. Tidak ada pemeriksaan dataset produksi dan operasi serentak.
* [ ] **UNVERIFIED** — Migration sudah diuji. Belum melakukan migrasi database/upgrade APK dari versi sebelumnya pada data nyata.
* [ ] **PARTIAL** — Seed/test data dipisahkan. Data pratinjau hanya debug dan activity tidak ada di release; seed server belum diperiksa; E4.
* [ ] **UNVERIFIED** — Database production terpisah. DDL/Room ditinjau; schema dan data Supabase aktif belum diperiksa; E2, E5.
* [ ] **PARTIAL** — Backup tersedia. Backup lokal/cloud diimplementasikan; kebijakan backup database server belum diketahui; E10.
* [ ] **FAIL** — Restore backup sudah diuji. Restore Android memiliki risiko traversal pada OS lama; backup desktop gagal; uji restore lengkap belum; F04, F15.

---

## 07 — SECURITY AUDIT

### Secrets

* [ ] **PARTIAL** — API key tidak hardcode di APK. Public Supabase/Firebase client key memang masuk build; bukan admin secret. Private token/API key dan log masih berisiko; F06. APK bukan tempat menyimpan admin secret.
* [ ] **PARTIAL** — Secret tidak masuk Git. 0 nama file credential sensitif ditemukan dalam git ls-files; belum scan seluruh nilai dan riwayat Git; E6.
* [ ] **PARTIAL** — Password database tidak masuk source code. Tidak ditemukan file password DB yang dilacak pada pemeriksaan nama file; ini bukan scan nilai/history lengkap; E6.
* [ ] **PARTIAL** — Production secrets menggunakan environment/secret management. Workflow utama memakai GitHub secrets; dev workflow kehilangan Supabase config; E6, L04.
* [ ] **PARTIAL** — Debug credentials sudah dihapus. Activity preview tidak ada di release; belum ada scan seluruh credential/test-account dalam APK; E4.

### Input Security

* [ ] **FAIL** — Input validation. Nama ekspor dan path ZIP belum divalidasi lengkap; F04, F14.
* [ ] **PARTIAL** — SQL injection protection. Room/SDK menggunakan query parameter; belum ada uji injection pada backend live/custom SQL.
* [ ] **N/A** — XSS protection jika aplikasi web. Tidak ada frontend web produksi dalam cakupan; teks native bukan DOM browser.
* [ ] **N/A** — CSRF protection jika relevan. API akun native memakai Bearer, bukan auth cookie browser; endpoint berbasis cookie/web di luar scope belum dinilai.
* [ ] **UNVERIFIED** — File upload validation. Belum ada uji enforcement upload/avatar pada bucket server aktif.
* [ ] **UNVERIFIED** — File size limit. Batas ukuran server/upload/arsip belum diverifikasi dengan berkas besar.
* [ ] **UNVERIFIED** — MIME/type validation. Belum ada uji spoof MIME, ekstensi dan konten berkas pada storage live.

### Application Security

* [x] **PASS** — Debug mode OFF. Manifest APK release tidak memiliki debuggable; build release default non-debug; activity preview tidak ada; E4.
* [ ] **FAIL** — Production logging aman. Logger sensitif dan CurlLogging ALL masih tersedia; penghapusan log release belum aktif; F06.
* [ ] **FAIL** — Sensitive data tidak muncul di log. Token/cookie dicatat eksplisit; F06. Service FCM juga mencatat token; E9.
* [ ] **PARTIAL** — HTTPS aktif. Network security config menolak cleartext dan memakai CA sistem; trustEveryone tetap melemahkan jalur TLS; F07, E11.
* [ ] **FAIL** — Certificate/TLS configuration diperiksa. Dependency mengubah default HttpsURLConnection dengan trustEveryone; F07. Tidak terbukti semua OkHttp/Supabase ikut terpengaruh.
* [ ] **PARTIAL** — Dependency vulnerability diperiksa. Lint dan bytecode trust manager ditinjau; belum ada scan advisory/CVE seluruh dependensi; E1, E4.
* [ ] **PARTIAL** — Permission aplikasi diminimalkan. Manifest masih memuat WRITE_EXTERNAL_STORAGE tanpa pembatasan OS dan READ_MEDIA_AUDIO; kebutuhan minimum tiap permission belum dibuktikan; E11.

---

## 08 — DATA & PRIVACY

* [ ] **PARTIAL** — Data yang dikumpulkan sudah ditentukan. Entity/cloud mencatat profil, follow, library, history dan presence; belum ada inventaris data resmi; E1, E5.
* [ ] **UNVERIFIED** — Hanya mengumpulkan data yang diperlukan. Belum ada dokumentasi privasi dan verifikasi menyeluruh; L02.
* [ ] **FAIL** — Sensitive data terlindungi. Log sensitif, privasi sosial dan pemisahan akun bermasalah; F01, F03, F06.
* [ ] **FAIL** — Privacy policy tersedia jika diperlukan. Consent menyebut Terms/Privacy sebagai teks tanpa tautan; dokumen/URL kebijakan tidak ditemukan di repo/UI; L02.
* [ ] **FAIL** — Data deletion tersedia jika diperlukan. Hapus akun beserta data Supabase belum tersedia; L01.
* [ ] **UNVERIFIED** — Data retention jelas. Retensi akun/history/cloud/log dan pemusnahan backup belum didokumentasikan.
* [ ] **PARTIAL** — Third-party services terdokumentasi. README/credits/dependencies menyebut layanan; tujuan pemrosesan data belum dijelaskan sebagai privacy policy; E7, L02.
* [ ] **PARTIAL** — Permission device dijelaskan. Permission dan dialog sistem ada; rationale semua izin belum ditinjau; E11.
* [ ] **FAIL** — User mengetahui penggunaan datanya. Label privasi tidak sesuai enforcement dan persetujuan tidak membuka policy; F03, L02.

---

## 09 — PERFORMANCE

### App

* [ ] **PARTIAL** — Startup cepat. Startup/UI pernah berjalan di emulator; cold-start p50/p95 perangkat nyata belum diukur; E3.
* [ ] **UNVERIFIED** — Tidak ada freeze. Ada runBlocking pada setter UI dan penundaan playback; perlu profiling, belum terbukti ANR; F18, F35.
* [ ] **PARTIAL** — Tidak ada memory leak yang diketahui. Lifecycle scope Discord bermasalah; belum ada heap/leak measurement; F32.
* [ ] **UNVERIFIED** — RAM usage diperiksa. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.
* [ ] **UNVERIFIED** — Battery usage diperiksa. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.
* [ ] **PARTIAL** — APK/App Bundle tidak unnecessarily besar. APK universal 49.4 MiB; ABI-specific sekitar 24–26 MiB; belum ada size-budget/baseline untuk menilai pemborosan; E4.

### Backend

* [ ] **UNVERIFIED** — API response time diperiksa. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.
* [ ] **UNVERIFIED** — Slow query diperiksa. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.
* [ ] **UNVERIFIED** — Database index diperiksa. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.
* [ ] **PARTIAL** — Caching digunakan bila diperlukan. Cache Media3/Room tersedia; resolver partial cache bermasalah; F31.
* [ ] **UNVERIFIED** — Concurrent request diuji. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.
* [ ] **UNVERIFIED** — Server resource cukup. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.

### Stress

* [ ] **UNVERIFIED** — Normal load test. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.
* [ ] **UNVERIFIED** — High traffic test. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.
* [ ] **UNVERIFIED** — Concurrent user test. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.
* [ ] **UNVERIFIED** — Rate limit test. Belum ada pengukuran/profiling dengan target dan ambang penerimaan.

---

## 10 — NETWORK & OFFLINE

* [ ] **PARTIAL** — Internet normal. Home nyata memuat data musik di emulator; auth/audio/cloud belum lengkap; E3.
* [ ] **UNVERIFIED** — Internet lambat. Belum ada pengujian gangguan jaringan menyeluruh dengan akun/perangkat nyata.
* [ ] **FAIL** — Internet terputus. Gagal/offline saat pindah akun dapat mencampur data; F01. Recovery jaringan umum belum diuji.
* [ ] **FAIL** — Request timeout. Metadata tambahan dan Discord send tidak mempunyai batas yang memadai; F18, F32.
* [ ] **FAIL** — Retry mechanism. Download gagal dapat menghalangi retry atau membawa lastException lama; F25.
* [ ] **PARTIAL** — Duplicate request protection. Home/search punya cancellation/pagination guard; semua operasi tulis/double tap belum diuji; E3.
* [ ] **FAIL** — Offline state. Partial cache dapat mengembalikan video ID sebagai URL saat upstream dibutuhkan; F31.
* [ ] **FAIL** — Data synchronization. Prune snapshot dan penggantian playlist berisiko hilang data; F01, F02, F05, F10, F11, F17.
* [ ] **FAIL** — API timeout handling. Sebagian jalur bisa menunggu tanpa deadline ketat; F18, F32. Belum ada chaos test lengkap.

---

## 11 — COMPATIBILITY

### Device

* [ ] **UNVERIFIED** — Low-end device. Belum diuji pada kombinasi perangkat/OS ini; E3.
* [ ] **UNVERIFIED** — Mid-range device. Belum diuji pada kombinasi perangkat/OS ini; E3.
* [ ] **UNVERIFIED** — High-end device. Belum diuji pada kombinasi perangkat/OS ini; E3.

### Screen

* [ ] **UNVERIFIED** — Small screen. Belum diuji pada kombinasi perangkat/OS ini; E3.
* [ ] **PARTIAL** — Standard screen. Emulator ponsel viewport 720x1600 pernah diperiksa; bukan perangkat fisik kelas menengah; E3.
* [ ] **PARTIAL** — Large screen. Home tablet 1920x1200 pernah diperiksa; seluruh halaman tablet belum; E3.
* [ ] **PARTIAL** — Different aspect ratio. Dua viewport dan font 1,3x pada layar terpilih; belum seluruh aspect ratio; E3.
* [ ] **PARTIAL** — Portrait. Layar utama ponsel diperiksa portrait; detail/landscape belum lengkap; E3.
* [ ] **PARTIAL** — Landscape jika didukung. Home viewport tablet landscape diperiksa; rotation/state dan seluruh layar belum; E3.

### OS

* [ ] **UNVERIFIED** — Minimum supported version. minSdk 26 terkonfirmasi, tetapi belum runtime test API 26; E4. F04 relevan OS lama.
* [ ] **PARTIAL** — Current OS version. Bukti emulator API 37 tersedia; bukan bukti seluruh OS pengguna saat ini; E3.
* [ ] **PARTIAL** — Latest supported OS. Compile SDK 37/target 36; emulator API 37 diuji terbatas. Penetapan dukungan OS lengkap belum; E3, E4.

---

## 12 — ERROR & EDGE CASE

Uji kondisi yang "tidak normal":

* [ ] **PARTIAL** — Input kosong. Form mempunyai validasi dasar; semua API/parser empty input belum diuji.
* [ ] **UNVERIFIED** — Input terlalu panjang. Skenario kegagalan ini belum diuji menyeluruh dalam aplikasi nyata.
* [ ] **FAIL** — Karakter aneh. Non-BMP HTML entity dapat melempar exception; cookie/LRC/nama slash salah ditangani; F14, F26, F33, F34.
* [ ] **UNVERIFIED** — Data duplicate. Skenario kegagalan ini belum diuji menyeluruh dalam aplikasi nyata.
* [ ] **FAIL** — Data tidak ditemukan. Mood/Genre parse failure/empty bisa menghasilkan tidak ada state; F27.
* [ ] **UNVERIFIED** — User tidak memiliki permission. Skenario kegagalan ini belum diuji menyeluruh dalam aplikasi nyata.
* [ ] **UNVERIFIED** — Token expired. Skenario kegagalan ini belum diuji menyeluruh dalam aplikasi nyata.
* [ ] **UNVERIFIED** — Server mati. Skenario kegagalan ini belum diuji menyeluruh dalam aplikasi nyata.
* [ ] **FAIL** — Database error. Delete+insert cloud non-atomik dan kegagalan insert disembunyikan; F05.
* [ ] **FAIL** — Internet mati. Pemulihan akun/cache pada kegagalan jaringan belum aman; F01, F31.
* [ ] **FAIL** — Request timeout. Deadline metadata/Discord tidak memadai; F18, F32.
* [ ] **FAIL** — Upload gagal. Upload/sync failure bisa terlihat sukses atau mencampur data; F01, F05, F16.
* [ ] **FAIL** — Download gagal. Terminal failure download dianggap selesai; retry bermasalah; F24, F25.
* [ ] **UNVERIFIED** — User menekan tombol berkali-kali. Skenario kegagalan ini belum diuji menyeluruh dalam aplikasi nyata.
* [ ] **UNVERIFIED** — App ditutup saat proses berjalan. Skenario kegagalan ini belum diuji menyeluruh dalam aplikasi nyata.

---

## 13 — FILE & STORAGE

* [ ] **PARTIAL** — Upload file. Avatar/cloud upload terimplementasi; akses/batas/gagal upload live belum diuji; E1.
* [ ] **FAIL** — Download file. Status terminal failure dan retry download salah; F24, F25, F31.
* [ ] **UNVERIFIED** — File type validation. Implementasi storage ada; uji akses, batas, dan kegagalan nyata belum lengkap.
* [ ] **UNVERIFIED** — File size limit. Implementasi storage ada; uji akses, batas, dan kegagalan nyata belum lengkap.
* [ ] **FAIL** — Filename validation. Sanitizer ekspor tidak menghapus slash; validasi canonical ZIP kurang pada OS lama; F04, F14.
* [ ] **PARTIAL** — Storage permission. MediaStore/SAF dan izin tersedia; runtime deny/revoke semua OS belum diuji; E10, E11.
* [ ] **UNVERIFIED** — File tidak dapat diakses user yang tidak berhak. Policy bucket aktif belum diperiksa; backup settings/database perlu threat-model akses; E5, E10.
* [ ] **PARTIAL** — Temporary files dibersihkan. Worker menghapus temp backup setelah menyimpan; crash/full-disk/download cleanup belum diuji; E10.
* [ ] **PARTIAL** — Storage backup jika diperlukan. Opsi backup file download tersedia; restore semua aset belum diuji; E10, F04, F15.

---

## 14 — NOTIFICATION

* [ ] **PARTIAL** — Push notification. Firebase messaging/service ada; tidak ditemukan registrasi token ke server pada onNewToken; pengiriman live belum; E9.
* [ ] **PARTIAL** — Notification permission. POST_NOTIFICATIONS dan request UI tersedia; grant/deny/revoke belum diuji; E11.
* [ ] **PARTIAL** — Notification content benar. Service membaca title/body dan menyimpan notifikasi; payload produksi belum diuji; E9.
* [ ] **PARTIAL** — Deep link benar. PendingIntent/deep link tersedia; follow back/recovery/live payload belum diuji; F08, F12, E9.
* [ ] **UNVERIFIED** — Notification ketika app terbuka. Service FCM ada; belum ada uji pengiriman end to end pada perangkat; E9.
* [ ] **UNVERIFIED** — Notification ketika app background. Service FCM ada; belum ada uji pengiriman end to end pada perangkat; E9.
* [ ] **UNVERIFIED** — Notification ketika app ditutup. Service FCM ada; belum ada uji pengiriman end to end pada perangkat; E9.
* [ ] **UNVERIFIED** — Tidak ada spam notification. Service FCM ada; belum ada uji pengiriman end to end pada perangkat; E9.

---

## 15 — THIRD-PARTY SERVICES

List semua service:

| Service | Fungsi | Production | Status |
|---|---|---|---|
| Supabase Auth/PostgREST/Storage | Akun, profil, sosial, library cloud | Config lokal ada; server/allowlist belum diverifikasi | PARTIAL; F01–F12/F16/F17 |
| YouTube Music + PipePipe/NewPipe | Katalog/stream extraction | Home nyata pernah memuat; playback penuh belum | PARTIAL; TLS F07 |
| Firebase Cloud Messaging | Push notification | Config package cocok; delivery/token registry belum diuji | PARTIAL; E9 |
| Sentry | Crash/error tracking opsi full | Wiring full tetap empty + DSN kosong | FAIL; L03 |
| Spotify | Integrasi metadata/Canvas sesuai fitur | Token/akses pengguna belum diuji | UNVERIFIED; logging F06 |
| Discord | Rich presence | Auth/koneksi produksi belum diuji | PARTIAL; lifecycle F32 |
| Last.fm | Scrobble | Akun/token dan hasil scrobble belum diuji | UNVERIFIED |
| Tidal | Metadata tambahan | Request dapat menunda stream | FAIL deadline F18 |
| LRCLIB/Musixmatch/layanan lirik | Lirik | Semua fallback/provider belum diuji | PARTIAL; F33/F34 |
| Gemini/OpenAI-compatible/custom AI | Terjemahan/AI | Key pengguna; endpoint Gemini salah | FAIL F13; provider lain belum diuji |
| GitHub Releases | Update APK | Validasi client tersedia; upgrade belum diuji | PARTIAL; E1 |

Daftar ini mencakup layanan utama yang terlihat pada source; bukan inventaris seluruh transitif SDK/host dan bukan sertifikasi akun production/billing.


* [ ] **UNVERIFIED** — API key production. Konfigurasi layanan produksi belum dapat diverifikasi dari lingkungan lokal.
* [ ] **N/A** — Webhook production. Tidak ditemukan integrasi webhook yang menjadi fitur aplikasi; bukan sertifikasi webhook server lain.
* [ ] **PARTIAL** — Callback URL production. Scheme com.tan.gratify/login-callback ada; allowlist OAuth/recovery Supabase aktif belum; E12, F08.
* [ ] **N/A** — Billing/subscription production. Tidak ditemukan alur pembayaran/subscription produk Gratify; biaya penyedia layanan perlu verifikasi operator.
* [ ] **PARTIAL** — Third-party dependency aktif. Dependensi dapat dibuild dan Home musik berfungsi; koneksi semua penyedia belum diuji; E3, E4.
* [ ] **FAIL** — Fallback jika service gagal. Fallback/retry dan endpoint Gemini bermasalah; F13, F18, F24, F25, F27, F31.

---

## 16 — LOGGING & MONITORING

* [ ] **PARTIAL** — Application logging. Logger aplikasi aktif, tetapi belum aman untuk produksi; F06, E9.
* [ ] **UNVERIFIED** — Backend logging. Konfigurasi/operasi monitoring server aktif belum tersedia untuk diperiksa.
* [ ] **FAIL** — Error tracking. Implementasi terpilih crashlytics-empty hanya log; DSN kosong; remote error tracking tidak aktif; L03.
* [ ] **FAIL** — Crash monitoring. Build full juga memilih crashlyticsEmpty; Sentry auto-init dimatikan; L03.
* [ ] **UNVERIFIED** — Server monitoring. Konfigurasi/operasi monitoring server aktif belum tersedia untuk diperiksa.
* [ ] **UNVERIFIED** — Database monitoring. Konfigurasi/operasi monitoring server aktif belum tersedia untuk diperiksa.
* [ ] **UNVERIFIED** — Disk monitoring. Konfigurasi/operasi monitoring server aktif belum tersedia untuk diperiksa.
* [ ] **UNVERIFIED** — CPU/RAM monitoring. Konfigurasi/operasi monitoring server aktif belum tersedia untuk diperiksa.
* [ ] **UNVERIFIED** — Alert ketika service down. Konfigurasi/operasi monitoring server aktif belum tersedia untuk diperiksa.
* [ ] **FAIL** — Sensitive information tidak masuk log. Token/cookie dan token FCM dapat masuk log; F06, E9.

---

## 17 — BACKUP & DISASTER RECOVERY

* [ ] **PARTIAL** — Database backup. Backup SQLite lokal/cloud sync ada; backup server Supabase bukan dibuktikan oleh sync; E10, F02.
* [ ] **PARTIAL** — File/storage backup. File download bisa disertakan backup; backup bucket server belum diverifikasi; E10.
* [ ] **PARTIAL** — Backup otomatis. WorkManager harian/mingguan/bulanan tersedia; execution/retention/restore nyata belum; E10.
* [ ] **PARTIAL** — Backup location aman. Legacy backup app-specific; modern backup Download/Gratify berupa ZIP, belum ada bukti enkripsi/akses sesuai sensitivitas; E10.
* [ ] **FAIL** — Restore test berhasil. Tidak ada restore end to end yang lulus; ada risiko Android dan backup desktop rusak; F04, F15.
* [ ] **UNVERIFIED** — Recovery procedure terdokumentasi. Belum ditemukan runbook recovery server, owner, RPO/RTO dan bukti drill.
* [ ] **PARTIAL** — Emergency contact tersedia. README menyediakan email legal; emergency/on-call dan jalur eskalasi belum disepakati; E7.
* [ ] **UNVERIFIED** — Rollback procedure tersedia. Belum ditemukan prosedur rollback APK/database yang diuji bersama backup.

---

## 18 — PRODUCTION CONFIGURATION

### Environment

* [ ] **PARTIAL** — Development. Variant debug dengan suffix .dev tersedia; isolasi backend dev dari produksi belum terbukti; E4, E6.
* [ ] **UNVERIFIED** — Staging. Belum ada bukti proyek/backend staging terpisah serta akun penguji.
* [ ] **PARTIAL** — Production. Variant/workflow release tersedia; server, signing dan deploy production belum diverifikasi; E4, E6.

Pastikan:

* [ ] **PARTIAL** — Production API URL benar. SUPABASE_URL lokal tersedia dan workflow utama memeriksa nonempty; kesesuaian proyek prod/allowlist belum; E6.
* [ ] **UNVERIFIED** — Production database benar. Tidak ada akses admin/snapshot schema untuk memastikan database produksi; E5.
* [ ] **UNVERIFIED** — Production credentials benar. Public key tersedia lokal; secret CI/signing/live service belum diverifikasi; E6.
* [x] **PASS** — Debug mode OFF. Release APK non-debug berdasarkan manifest/build type; E4.
* [ ] **UNVERIFIED** — Test account tidak digunakan production. Belum memeriksa daftar akun server produksi; contoh UI hanya debug.
* [ ] **UNVERIFIED** — Test data dibersihkan. Belum memeriksa/menyetujui pembersihan dataset server; tidak ada data pengguna dihapus.
* [ ] **FAIL** — Environment variables benar. Dev release workflow tidak menulis Supabase URL/key; default build kosong; L04. Workflow utama punya guard.

---

## 19 — APP RELEASE

### Android

* [x] **PASS** — Application ID/package name benar. APK release: com.tan.gratify; debug berbeda .dev; E4.
* [x] **PASS** — App name benar. Label APK release: Gratify; E4.
* [x] **PASS** — Version name benar. APK release versionName 2.1.0 cocok konfigurasi; E4.
* [x] **PASS** — Version code benar. APK release versionCode 61 cocok konfigurasi; monotonic terhadap store belum diperiksa; E4.
* [ ] **PARTIAL** — App icon benar. Icon resource ada; tampilan launcher release/adaptive berbagai perangkat belum; E4, E8.
* [ ] **PARTIAL** — Splash screen benar. Tema/logo gelap splash diperiksa pada debug; startup release belum; E3.
* [ ] **FAIL** — Release signing benar. APK lokal unsigned; apksigner verify exit 1. CI signing terdefinisi tetapi hasil CI belum diperiksa; E4, E6.
* [ ] **PARTIAL** — Debug signing tidak digunakan. APK lokal tidak memakai debug signer karena unsigned; certificate production belum diperiksa; E4.
* [ ] **PARTIAL** — APK/AAB production berhasil dibuat. assembleRelease berhasil menghasilkan APK ABI/universal, tetapi belum signed siap distribusi; AAB tidak dibuat; E4.
* [ ] **UNVERIFIED** — Install dari build production berhasil. Tidak menginstal APK unsigned; perlu APK signed pada perangkat uji milik proyek.
* [ ] **UNVERIFIED** — Update dari versi sebelumnya berhasil. Belum menguji upgrade, signature continuity, session, data dan migration dari versi sebelumnya.

### Store

* [ ] **FAIL** — App description. Deskripsi/changelog ada tetapi changelog 61 masih menjanjikan Apple login yang sudah dihapus; L05, E8.
* [ ] **PARTIAL** — Screenshot. 13 phoneScreenshots tersedia di fastlane; belum dicocokkan dengan UI terbaru dan store upload; E8.
* [ ] **PARTIAL** — App icon. Icon store tersedia di fastlane; validasi dimensi/branding dan publikasi belum; E8.
* [ ] **PARTIAL** — Feature graphic jika diperlukan. featureGraphic.png tersedia; validasi visual/dimensi dan publikasi belum; E8.
* [ ] **FAIL** — Privacy policy. Tidak ditemukan policy/URL yang dapat dibuka dari aplikasi/repo; L02.
* [ ] **UNVERIFIED** — Content rating. Belum ada bukti rating di console store.
* [ ] **UNVERIFIED** — Data safety/privacy declarations. Belum ada deklarasi Data safety yang diverifikasi terhadap data/SDK aktual.
* [ ] **UNVERIFIED** — Store category. Kategori store belum diverifikasi pada console.
* [ ] **PARTIAL** — Support/contact information. Contact legal tersedia di README; support listing dan operasional belum; E7.

---

## 20 — FINAL USER ACCEPTANCE TEST

### Critical Flow

**Flow 1: Register → Login → Main Feature**

**Hasil:** UNVERIFIED untuk UAT runtime; F08/F09 menghambat recovery/signup; fitur utama streaming nyata belum diuji. Kotak PASS/FAIL tidak dipilih karena bukan hasil uji end to end.


* [ ] **UNVERIFIED** — PASS. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.
* [ ] **UNVERIFIED** — FAIL. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.

**Flow 2: User → Create Data → View Data → Edit → Delete**

**Hasil:** UNVERIFIED untuk UAT runtime; F02/F05/F17/F28 menghambat cloud CRUD dan urutan; tidak ada UAT akun nyata. Kotak PASS/FAIL tidak dipilih karena bukan hasil uji end to end.


* [ ] **UNVERIFIED** — PASS. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.
* [ ] **UNVERIFIED** — FAIL. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.

**Flow 3: Logout → Login kembali**

**Hasil:** UNVERIFIED untuk UAT runtime; F01 menghambat isolasi saat logout/login dan backup gagal. Kotak PASS/FAIL tidak dipilih karena bukan hasil uji end to end.


* [ ] **UNVERIFIED** — PASS. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.
* [ ] **UNVERIFIED** — FAIL. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.

**Flow 4: Error/Offline Recovery**

**Hasil:** UNVERIFIED untuk UAT runtime; F24/F25/F31 serta sync F01/F02 menghambat recovery; tidak ada chaos UAT. Kotak PASS/FAIL tidak dipilih karena bukan hasil uji end to end.


* [ ] **UNVERIFIED** — PASS. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.
* [ ] **UNVERIFIED** — FAIL. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.

**Flow 5: Update App**

**Hasil:** UNVERIFIED untuk UAT runtime; Belum ada signed release/upgrade dari APK sebelumnya dan verifikasi data/migrasi. Kotak PASS/FAIL tidak dipilih karena bukan hasil uji end to end.


* [ ] **UNVERIFIED** — PASS. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.
* [ ] **UNVERIFIED** — FAIL. Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.

---

## Gap launch tambahan L01–L05

### L01 — HIGH — Penghapusan akun dan data belum tersedia

Pencarian auth/repository/UI/SQL tidak menemukan alur hapus akun Supabase atau endpoint penghapusan data pengguna. [deleteGoogleAccount](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/AccountRepositoryImpl.kt:55) hanya menghapus akun Google lokal, bukan identitas Supabase. Logout juga tidak menghapus akun. Tambahkan alur penghapusan terautentikasi di server, konfirmasi pengguna, pemusnahan data/bucket terkait dan uji token revocation. Uji penerimaan: akun penguji dihapus, login kembali ditolak, data private/bucket hilang sesuai retensi, data pengguna lain tetap utuh.

### L02 — HIGH — Kebijakan privasi/terms tidak dapat dibuka

[SignUpScreen](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/login/SignUpScreen.kt:449) menampilkan checkbox dan teks persetujuan pada baris 463 tanpa tautan/navigasi ke dokumen. Pencarian repository/UI tidak menemukan policy/terms atau URL yang dapat dibuka. README legal disclaimer bukan kebijakan privasi. Siapkan dokumen sesuai alur data aktual, tautkan dari signup/Settings, dan verifikasi tautan sebelum persetujuan. Tidak disimpulkan ada/tidak ada dokumen di luar repository yang belum diberikan.

### L03 — HIGH — Wiring full build tidak mengaktifkan crash tracking

[androidApp Gradle](D:/Tan/Gratify/androidApp/build.gradle.kts:185) selalu memilih crashlyticsEmpty. [BuildKonfig](D:/Tan/Gratify/composeApp/build.gradle.kts:557) selalu mengisi sentryDsn kosong. [Crashlytics empty](D:/Tan/Gratify/crashlytics-empty/src/main/java/com/tan/gratify/crashlytics/Crashlytics.kt:8) hanya mencatat log; Sentry auto-init dimatikan di manifest. isFullBuild dibaca tetapi tidak dipakai untuk memilih modul/DSN, meskipun script menawarkan full dengan Sentry. Untuk jalur full, sambungkan konfigurasi dan bukti event uji yang disanitasi. Untuk distribusi tanpa telemetry, dokumentasikan pilihan dan mekanisme laporan crash yang disetujui; jangan menyatakan monitoring remote aktif. Monitoring server tetap UNVERIFIED.

### L04 — HIGH — Workflow release dev kehilangan konfigurasi Supabase

[android.yml](D:/Tan/Gratify/.github/workflows/android.yml:44) menulis local.properties hanya dengan Sentry lalu membangun APK release. [BuildKonfig](D:/Tan/Gratify/composeApp/build.gradle.kts:563) default Supabase URL/key ke string kosong. [SupabaseModule](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/di/SupabaseModule.kt:21) menyerahkan nilai tersebut langsung ke createSupabaseClient. Pada checkout CI bersih tidak ada config lokal, sehingga artefak dev release tidak memiliki konfigurasi auth yang valid. Workflow utama android-release.yml sudah memiliki guard URL/key; gap ini khusus android.yml. Terapkan config/guard konsisten dan verifikasi artefak CI tanpa memublikasikan nilai secret. PR dari fork tanpa secrets harus skip job distribusi atau gagal jelas, bukan membuat APK siap pakai palsu.

### L05 — LOW — Changelog masih menjanjikan Apple login

[Changelog 61](D:/Tan/Gratify/fastlane/metadata/android/en-US/changelogs/61.txt:1) menyebut email, Google dan Apple; [LoginLandingScreen](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/login/LoginLandingScreen.kt:324) mencatat tombol Apple dihapus sesuai permintaan. Selaraskan metadata dan screenshot dengan fitur yang tersedia. Tidak disarankan mengembalikan Apple tanpa kebutuhan produk.

## Bug tracker

Semua F01–F36 masih **OPEN**, source identik dengan snapshot audit fungsi. Severity launch berikut adalah penilaian risiko untuk gate, bukan perubahan prioritas P1/P2/P3 lama. **Critical** berarti potensi pencampuran/hilang data, pelanggaran privasi atau TLS berdasarkan source/probe; bukan klaim insiden produksi atau auth bypass yang telah terjadi. F04 bergantung OS/target; F36 belum punya caller produksi. Assignee belum ditentukan.

| ID | Bug | Severity launch / prioritas lama | Location | Status | Assignee |
|---|---|---|---|---|---|
| F01 | Data akun lama dapat masuk ke akun baru | Critical / P1 | [UserDataSyncManager](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/sync/UserDataSyncManager.kt:152) | OPEN | — |
| F02 | Perangkat lama dapat menghapus tambahan dari perangkat lain | Critical / P1 | [UserDataSyncRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserDataSyncRepositoryImpl.kt:59) | OPEN | — |
| F03 | Pengaturan privasi profil tidak diterapkan pada pembaca lain | Critical / P1 | [kontrol privasi](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/home/ProfileScreen.kt:1157) | OPEN | — |
| F04 | Restore ZIP Android tidak memvalidasi batas direktori | High / P1 | [restoreFolder](D:/Tan/Gratify/composeApp/src/androidMain/kotlin/com/tan/gratify/viewModel/SettingsViewModel.android.kt:250) | OPEN | — |
| F05 | Penggantian playlist cloud tidak atomik dan bisa sukses palsu | High / P1 | [SharedPlaylistRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/SharedPlaylistRepositoryImpl.kt:104) | OPEN | — |
| F06 | Token dan cookie dapat masuk ke log tanpa redaksi | Critical / P1 | [Ytmusic logging](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/Ytmusic.kt:159) | OPEN | — |
| F07 | Inisialisasi extractor melemahkan default TLS proses | Critical / P1 | [Extractor Android](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/androidMain/kotlin/com/tan/kotlinytmusicscraper/extractor/Extractor.android.kt:24) | OPEN | — |
| F08 | Form password baru tidak tersambung dengan recovery | High / P2 | [ForgotPasswordViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/ForgotPasswordViewModel.kt:125) | OPEN | — |
| F09 | Signup dapat selesai walau penetapan password gagal | High / P2 | [SignUpViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SignUpViewModel.kt:276) | OPEN | — |
| F10 | Cloud settings memakai key dan tipe yang tidak cocok | Medium / P2 | [UserDataSyncRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserDataSyncRepositoryImpl.kt:304) | OPEN | — |
| F11 | Pemulihan riwayat dan antrean belum lengkap | Medium / P2 | [UserDataSyncRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserDataSyncRepositoryImpl.kt:255) | OPEN | — |
| F12 | Flow sosial gagal saat dikonsumsi dengan firstOrNull | Medium / P2 | [UserRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserRepositoryImpl.kt:87) | OPEN | — |
| F13 | Endpoint Gemini salah untuk payload OpenAI | High / P2 | [AiService base URL](D:/Tan/Gratify/core/service/aiService/src/commonMain/kotlin/com/tan/gratify/aiservice/AiService.kt:52) | OPEN | — |
| F14 | Ekspor lagu gagal pada nama yang mengandung slash | High / P2 | [handler/DownloadHandler](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/handler/DownloadHandler.kt:171) | OPEN | — |
| F15 | Backup desktop memakai ZipOutputStream bertingkat | High / P2 | [backup JVM](D:/Tan/Gratify/composeApp/src/jvmMain/kotlin/com/tan/gratify/viewModel/SettingsViewModel.jvm.kt:84) | OPEN | — |
| F16 | Follow dan penyimpanan profil dapat sukses palsu saat jaringan gagal | High / P2 | [UserProfileViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:207) | OPEN | — |
| F17 | Delete playlist dari Library tidak menghapus backup cloud | High / P2 | [delete Library](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/library/LibraryScreen.kt:310) | OPEN | — |
| F18 | Metadata Tidal tambahan berada di jalur kritis playback | High / P2 | [StreamRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/StreamRepositoryImpl.kt:176) | OPEN | — |
| F19 | Sleep timer “akhir lagu” memakai waktu dinding | Medium / P2 | [sleepStart](D:/Tan/Gratify/core/data/src/androidMain/kotlin/com/tan/data/mediaservice/MediaServiceHandlerImpl.kt:949) | OPEN | — |
| F20 | Tombol More profil publik tidak menjalankan aksi | Low / P3 | [More profil](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/social/UserProfileScreen.kt:347) | OPEN | — |
| F21 | Refresh playlist memakai indeks baris sebagai nomor halaman | High / P2 | [paging/LocalPlaylistPagingSource](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/paging/LocalPlaylistPagingSource.kt:17) | OPEN | — |
| F22 | Pagination waktu melewatkan item dengan timestamp sama | High / P2 | [paging/LocalPlaylistPagingSource](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/paging/LocalPlaylistPagingSource.kt:68) | OPEN | — |
| F23 | Token item YouTube diambil tanpa identitas playlist | High / P2 | [SetVideoIdEntity](D:/Tan/Gratify/core/domain/src/commonMain/kotlin/com/tan/domain/data/entities/SetVideoIdEntity.kt:5) | OPEN | — |
| F24 | Hasil download gagal dibaca sebagai selesai | High / P2 | [Ytmusic](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/Ytmusic.kt:908) | OPEN | — |
| F25 | Retry download berhenti setelah kegagalan body pertama | High / P2 | [Ytmusic](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/Ytmusic.kt:1067) | OPEN | — |
| F26 | Parser cookie memotong nilai dan membutuhkan spasi tertentu | Medium / P2 | [utils/Utils](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/utils/Utils.kt:25) | OPEN | — |
| F27 | Error parser mood/genre dibuang tanpa hasil error | Medium / P2 | [repository/HomeRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/HomeRepositoryImpl.kt:288) | OPEN | — |
| F28 | Drag playlist lokal menukar dua posisi, bukan memindahkan item | High / P2 | [LocalPlaylistViewModel](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/LocalPlaylistViewModel.kt:1233) | OPEN | — |
| F29 | Heartbeat sosial tersedia tetapi tidak diaktifkan | Medium / P2 | [mediaservice/ProfileSyncManager](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/mediaservice/ProfileSyncManager.kt:64) | OPEN | — |
| F30 | Durasi mendengar dihitung dari posisi, bukan waktu yang didengar | Medium / P2 | [MediaServiceHandlerImpl](D:/Tan/Gratify/core/data/src/androidMain/kotlin/com/tan/data/mediaservice/MediaServiceHandlerImpl.kt:2352) | OPEN | — |
| F31 | Resolver download dapat mengembalikan URI ID saat cache hanya sebagian | High / P2 | [resolver download](D:/Tan/Gratify/core/media/media3/src/main/java/com/tan/media3/service/download/DownloadUtils.kt:67) | OPEN | — |
| F32 | CoroutineScope Discord menghasilkan Job baru setiap pembacaan | Medium / P2 | [Discord scope](D:/Tan/Gratify/core/service/kizzy/src/commonMain/kotlin/com/my/kizzy/gateway/DiscordWebSocket.kt:78) | OPEN | — |
| F33 | HTML entity emoji dapat melempar exception | Medium / P2 | [decodeHtmlEntities](D:/Tan/Gratify/core/domain/src/commonMain/kotlin/com/tan/domain/extension/AllExt.kt:176) | OPEN | — |
| F34 | Parser LRC mengabaikan timestamp millisecond | Medium / P2 | [parseSyncedLyrics](D:/Tan/Gratify/core/service/lyricsService/src/commonMain/kotlin/com/tan/gratify/lyrics/parser/LrcTextParser.kt:6) | OPEN | — |
| F35 | Setter datastore memblokir thread UI | Medium / P2 | [dataStore/DataStoreManagerImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/dataStore/DataStoreManagerImpl.kt:959) | OPEN | — |
| F36 | Fungsi stripMarkdown tidak menghapus link/image | Low / P3 | [extension/StringExt](D:/Tan/Gratify/core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/tan/kotlinytmusicscraper/extension/StringExt.kt:15) | OPEN | — |
| L01 | Penghapusan akun/data Supabase belum tersedia | High / gap launch | [AccountRepositoryImpl](D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/AccountRepositoryImpl.kt:55) | OPEN | — |
| L02 | Consent tanpa tautan policy/terms | High / gap launch | [SignUpScreen](D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/login/SignUpScreen.kt:463) | OPEN | — |
| L03 | Full build memakai crashlytics-empty/DSN kosong | High / gap launch | [androidApp Gradle](D:/Tan/Gratify/androidApp/build.gradle.kts:185) | OPEN | — |
| L04 | Dev release CI tanpa Supabase config | High / gap launch | [android.yml](D:/Tan/Gratify/.github/workflows/android.yml:44) | OPEN | — |
| L05 | Changelog menjanjikan Apple login | Low / gap launch | [Changelog 61](D:/Tan/Gratify/fastlane/metadata/android/en-US/changelogs/61.txt:1) | OPEN | — |

# Final release gate

## Blocker check

* [ ] **FAIL** — Tidak ada Critical bug. Risiko Critical integritas/privasi/TLS masih terbuka; F01, F02, F03, F06, F07. Tidak ada insiden produksi yang diklaim.
* [ ] **FAIL** — Tidak ada High security issue. Validasi ZIP OS lama dan logging/TLS belum diperbaiki; F04, F06, F07.
* [ ] **FAIL** — Authentication aman. Pemisahan akun, OTP dan reset belum lulus; F01, F08, F09.
* [ ] **FAIL** — Authorization aman. Privasi profil tidak ditegakkan dan RLS aktif belum diuji; F03, E5.
* [ ] **FAIL** — Database production siap. Atomicity/sync/pagination bermasalah; migration dan backend live belum; F02, F05, F21, F22.
* [ ] **PARTIAL** — Backup tersedia. Backup client ada; backup server/restore lengkap belum lulus; E10, F04, F15.
* [ ] **FAIL** — Production environment benar. Dev release workflow kehilangan config dan server produksi belum dibuktikan; L04, E6.
* [ ] **FAIL** — Secrets aman. Log token/cookie masih tersedia; scan secrets lengkap belum; F06, E6.
* [ ] **UNVERIFIED** — Monitoring aktif. Monitoring layanan/server dan alert belum dapat diverifikasi.
* [ ] **FAIL** — Crash/error tracking aktif. Crash/error tracking remote tidak aktif pada wiring build saat ini; L03.
* [ ] **PARTIAL** — Release build sudah diuji. Release compile/lint lulus; unsigned, belum install/upgrade/UAT release; E4.
* [ ] **UNVERIFIED** — Update/rollback plan tersedia. Runbook rollback/update beserta drill belum tersedia.

## Hasil akhir

| Area | Status | Bukti/limit utama |
|---|---|---|
| Security | FAIL | F03/F04/F06/F07; RLS live belum diuji |
| Functionality | FAIL | Auth/sync/download/playlist/parser memiliki cacat terbuka |
| Performance | UNVERIFIED | Belum profil startup/jank/RAM/battery; F18/F32/F35 perlu pengukuran |
| UI/UX | PARTIAL | Tema dan layar utama diperiksa; semua state/aksi belum, F20 masih kosong |
| Database | FAIL | Integritas sync/atomicity/pagination; migration dan live schema belum |
| Infrastructure | UNVERIFIED | Server monitoring/backup/separation/alerts belum dibuktikan; client crash tracking FAIL L03 |
| Release | PARTIAL; gate tidak lulus | Compile/lint lulus, unsigned; install/upgrade/store belum diverifikasi |

☐ READY TO LAUNCH<br>
☒ **NEED FIX BEFORE LAUNCH**<br>
☐ BLOCKED karena pekerjaan tidak bisa dilanjutkan

**Distribusi rilis ditahan oleh gate.** Pekerjaan perbaikan client masih bisa berjalan; akses admin hanya dibutuhkan untuk verifikasi/perubahan server. Template PASS/FAIL tidak dipaksakan untuk hasil yang belum diuji.

## Urutan pekerjaan menuju keputusan rilis

1. Perbaiki F01–F07 dan tambah uji bermakna: dua akun saat backup gagal, merge dua perangkat, privasi public/private, transaksi playlist, redaksi log, TLS rejection, ZIP canonical boundary lintas OS.
2. Selesaikan auth recovery/OTP, cloud settings/queue, download failure/retry/partial cache, pagination/reorder dan deadline/lifecycle. Prioritaskan alur utama dari F08–F35; tentukan apakah desktop masuk scope launch. F36 adalah utilitas tanpa caller yang terbukti.
3. Selesaikan L01–L05 dan bukti consent, penghapusan akun, config CI dan mekanisme crash yang sesuai distribusi. Tetapkan owner, environment staging/production, retensi, backup server dan runbook restore/rollback.
4. Pada staging, jalankan A/B/guest RLS dan bucket tests, signup/login/reset/expiry, sync lintas perangkat serta restore/negative-network tests. Jangan mengganti hasil server dengan bukti SQL lokal. Akses admin/config staging diperlukan untuk bagian ini; password tidak dikirim melalui chat.
5. Buat signed release dengan certificate yang benar; verifikasi signature, install dan upgrade dari versi sebelumnya. Jalankan UAT lima flow dan benchmark perangkat minimum/representatif, denial permission, background playback dan push. Lengkapi store policy/rating/Data safety serta metadata/screenshot terbaru.
6. Luluskan gate hanya sesudah temuan blocker terselesaikan dan bukti aktual tersimpan. Jangan memakai keberhasilan compile atau jumlah centang sebagai klaim 100% siap.

**Auditor signature:** Codex (audit otomatis + penelusuran source; bukan sign-off pemilik produk)<br>
**Date:** 2026-10-01
