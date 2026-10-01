from pathlib import Path
from collections import Counter
import re, json, csv, hashlib

ROOT = Path('D:/Tan/Gratify')
OUT = ROOT / 'artifacts/launch-audit'
ATTACHMENT = Path('C:/Users/LENOVO/.codex/attachments/6cc83742-40ef-49cf-b20c-0d0dcc7b4380/Pasted text.txt')
template = ATTACHMENT.read_text(encoding='utf-8-sig')
release = json.loads((OUT / 'release-verification.json').read_text(encoding='utf-8'))
groups = {}
section = None
for line in template.splitlines():
    match = re.match(r'## (\d{2})', line)
    if match:
        section = match[1]
    if line.startswith('# FINAL RELEASE GATE'):
        section = 'gate'
    if line.startswith('* [ ]'):
        groups.setdefault(section, []).append(line[6:])

# Default statuses are deliberately unverified, never inferred passes.
defaults = {
 '01': 'Belum ada matriks requirement yang disetujui dan UAT lengkap; E1, E2.',
 '02': 'Bukti UI mencakup sebagian layar Android; seluruh state/aksi belum diuji; E3.',
 '03': 'Implementasi ada, tetapi skenario akun Supabase nyata belum dijalankan; E1.',
 '04': 'SQL lokal bukan bukti policy/bucket server aktif; perlu dua akun dan guest; E5.',
 '05': 'Kontrak client ditinjau; konfigurasi dan respons server aktif belum diverifikasi; E1, E5.',
 '06': 'DDL/Room ditinjau; schema dan data Supabase aktif belum diperiksa; E2, E5.',
 '07': 'Belum cukup bukti untuk memastikan seluruh jalur aplikasi/dependensi; E1, E4.',
 '08': 'Belum ada dokumentasi privasi dan verifikasi menyeluruh; L02.',
 '09': 'Belum ada pengukuran/profiling dengan target dan ambang penerimaan.',
 '10': 'Belum ada pengujian gangguan jaringan menyeluruh dengan akun/perangkat nyata.',
 '11': 'Belum diuji pada kombinasi perangkat/OS ini; E3.',
 '12': 'Skenario kegagalan ini belum diuji menyeluruh dalam aplikasi nyata.',
 '13': 'Implementasi storage ada; uji akses, batas, dan kegagalan nyata belum lengkap.',
 '14': 'Service FCM ada; belum ada uji pengiriman end to end pada perangkat; E9.',
 '15': 'Konfigurasi layanan produksi belum dapat diverifikasi dari lingkungan lokal.',
 '16': 'Konfigurasi/operasi monitoring server aktif belum tersedia untuk diperiksa.',
 '17': 'Belum ada bukti pemulihan lengkap maupun prosedur operasional yang disetujui.',
 '18': 'Lingkungan server dan credential produksi belum diverifikasi; E5, E6.',
 '19': 'Belum ada verifikasi instalasi atau konfigurasi store produksi; E4, E8.',
 '20': 'Pilihan hasil UAT belum dipilih karena flow belum dijalankan end to end.',
 'gate': 'Bukti belum mencukupi untuk meluluskan gate ini.',
}
overrides = {}
def set_items(group, rows):
    for index, status, reason in rows:
        assert 1 <= index <= len(groups[group])
        overrides[(group, index)] = (status, reason)

set_items('01', [
 (1,'FAIL','Ada recovery/queue cloud yang belum lengkap dan klaim Apple pada changelog tidak sesuai UI; F08, F11, L05.'),
 (2,'PASS','README menjelaskan aplikasi musik Android beta dan status desktop WIP; E7. Bukan bukti semua requirement terpenuhi.'),
 (3,'FAIL','Reset password, pemulihan antrean dan sejumlah aksi belum lengkap; F08, F11, F20.'),
 (4,'PARTIAL','Navigasi dan Home nyata pernah diuji; akun, streaming, sync dan restore belum end to end; E1, E3.'),
 (6,'PARTIAL','Desktop/iOS/F-Droid dijelaskan; tidak semua keterbatasan fitur dicatat pada dokumen produk; E7, E1.'),
 (7,'PARTIAL','Activity data contoh tidak ada dalam manifest release; masih ada aksi TODO tanpa fungsi; E4, F20.'),
])
set_items('02', [
 (1,'PARTIAL','Token/komponen bersama diterapkan; bukti visual hanya layar terpilih Android; E3.'),
 (2,'PASS','Tema memakai tipografi bersama Poppins; bukti layar yang diperiksa tersedia; E3.'),
 (3,'PASS','Palet gelap/hijau dan token warna bersama diterapkan; E3.'),
 (4,'PARTIAL','Ikon layar utama ditinjau; audit semantik seluruh ikon belum lengkap; E3.'),
 (5,'PARTIAL','Nama/logo Gratify tersedia; seluruh variasi aset/store belum dicocokkan; E3, E8.'),
 (6,'UNVERIFIED','Belum ada proofreading semua bahasa, pesan error, dan metadata.'),
 (7,'PARTIAL','Home/Settings font 1,3x dan Home tablet diperiksa; seluruh layar/ukuran belum; E3.'),
 (8,'PARTIAL','Loading dan timeout ada; Mood/Genre bisa berhenti tanpa hasil/error; F27.'),
 (9,'PARTIAL','Empty state Home/Library tersedia; pagination dapat membuat kosong palsu; F21, F22.'),
 (10,'FAIL','Kegagalan beberapa operasi ditelan atau dilaporkan sukses; F05, F09, F24, F27.'),
 (11,'FAIL','Success dapat tampil ketika cloud/download gagal; F05, F16, F24.'),
 (12,'PARTIAL','Komponen OfflineErrorState tersedia; recovery akun/offline belum lulus; F01, F31.'),
 (13,'PARTIAL','Form menyediakan loading/enabled guard; semua state belum diuji; E3.'),
 (14,'FAIL','Tombol More profil publik masih TODO; F20. Ada aksi lain yang salah hasil; F08.'),
 (15,'PARTIAL','Form email/OTP diperiksa secara visual; completion dengan akun nyata belum; E3.'),
 (16,'PARTIAL','Form email dapat digulir saat keyboard terbuka pada emulator; form lain belum seluruhnya; E3.'),
 (17,'PARTIAL','Navigasi tab/drawer/Settings/Profile pernah diperiksa; stack/deep link lengkap belum; E3.'),
 (18,'PARTIAL','Dialog memakai komponen bersama; semua modal/skenario dismiss belum diuji; E3.'),
])
set_items('03', [
 (1,'FAIL','Update password sesudah OTP gagal dapat tetap dianggap authenticated; F09.'),
 (2,'PARTIAL','Email/Google tersedia di client; login produksi belum diuji; E1.'),
 (3,'FAIL','Logout gagal backup dapat mempertahankan data akun lama; F01.'),
 (4,'PARTIAL','Request recovery tersedia; completion melalui email/deep link belum berhasil dibuktikan; F08.'),
 (5,'FAIL','State Ready/form password baru tidak tersambung lengkap; F08.'),
 (6,'FAIL','OTP ada, tetapi kegagalan lanjutan tidak memblokir sukses signup; F09.'),
 (7,'PARTIAL','SDK session manager digunakan; expiry/recovery nyata belum diuji; E1.'),
 (8,'PARTIAL','SDK Auth menangani sesi; refresh token invalid/revoked belum diuji; E1.'),
 (9,'FAIL','Tidak ditemukan alur hapus akun Supabase beserta data; hapus Google account hanya lokal; L01.'),
 (10,'PARTIAL','Error login ditangani pada client; respons akun tidak ada/disabled belum diuji nyata.'),
 (12,'PARTIAL','Tidak ditemukan persist password pengguna oleh alur auth yang ditinjau; SDK/backend dan dump backup belum diaudit lengkap.'),
])
set_items('04', [
 (1,'FAIL','Isolasi aplikasi saat pergantian akun gagal: data A dapat muncul/terunggah sebagai B; F01. RLS server belum diuji.'),
 (2,'PARTIAL','SQL lokal menerapkan ownership auth.uid; enforcement server aktif belum dibuktikan; E5.'),
 (3,'PARTIAL','SQL lokal menerapkan ownership delete; server aktif belum dibuktikan; E5.'),
 (4,'FAIL','Kontrol privasi profil tidak diterapkan pada pembacaan publik; F03. Role private-data server belum diuji.'),
 (5,'N/A','Tidak ada role/admin panel produk; admin Supabase adalah fungsi operasional di luar client.'),
 (6,'PARTIAL','SDK Auth/RLS dirancang untuk endpoint cloud; policy aktif belum diperiksa; E5.'),
 (7,'PARTIAL','USING/WITH CHECK auth.uid ada pada hotfix lokal; belum membuktikan deployment; E5.'),
 (8,'PARTIAL','RLS backend tertulis di SQL, bukan hanya UI; flags privasi tetap lokal; E5, F03.'),
])
set_items('05', [
 (1,'PARTIAL','Endpoint bawaan umumnya HTTPS; URL AI kustom belum dibatasi HTTPS dan TLS bermasalah; F07.'),
 (2,'PARTIAL','Supabase SDK Auth/Bearer digunakan; pengujian guest/token invalid belum; E1, E5.'),
 (3,'FAIL','Privasi sosial dan isolasi akun client gagal; F01, F03. RLS live belum diverifikasi.'),
 (4,'PARTIAL','Validasi form/query sebagian tersedia; seluruh input/batas server belum diuji.'),
 (5,'FAIL','Parser dapat gagal atau tidak mengeluarkan state; F26, F27, F33, F34.'),
 (7,'FAIL','Refresh/cursor playlist dapat melewatkan data; F21, F22. Batas query cloud belum diuji pada data besar.'),
 (8,'PARTIAL','Error client ditangani sebagian; beberapa ditelan; respons server belum diaudit; F05, F12, F27.'),
 (9,'UNVERIFIED','Belum memeriksa respons error server terhadap seluruh skenario malformed/authorization.'),
 (10,'UNVERIFIED','Belum memeriksa kebocoran detail database pada respons server aktif.'),
 (11,'PARTIAL','Query SDK/Room memakai parameter; fuzzing dan ownership server aktif belum; E2, E5.'),
 (12,'PARTIAL','SDK memakai operasi select/insert/update/delete; atomicity replacement gagal; F05.'),
 (13,'N/A','Audit ini mencakup client native Android; tidak ada frontend browser yang diuji. CORS layanan web lain belum dinilai.'),
 (14,'PASS','API SDK Supabase /auth/v1, /rest/v1, /storage/v1 dan endpoint AI memiliki versi; bukan bukti kompatibilitas setiap layanan.'),
])
set_items('06', [
 (1,'PARTIAL','Schema/Room dan SQL tersedia; kesesuaian versi deployment belum diverifikasi; E2, E5.'),
 (2,'PARTIAL','Relasi entity dan SQL ditinjau; data orphan/live belum diuji; E2, E5.'),
 (3,'PARTIAL','FK lokal/cloud dideklarasikan; seluruh cascade dan server aktif belum diuji; E2, E5.'),
 (4,'PARTIAL','Constraint tersedia pada source; atomicity/penggunaan query tetap salah; F05, F23.'),
 (5,'PARTIAL','Key unik dideklarasikan; query set_video_id mengabaikan bagian key; F23.'),
 (6,'PARTIAL','Indeks ada pada schema; EXPLAIN dan index server aktif belum; E2, E5.'),
 (7,'UNVERIFIED','Tidak ada pemeriksaan dataset produksi dan operasi serentak.'),
 (8,'UNVERIFIED','Belum melakukan migrasi database/upgrade APK dari versi sebelumnya pada data nyata.'),
 (9,'PARTIAL','Data pratinjau hanya debug dan activity tidak ada di release; seed server belum diperiksa; E4.'),
 (11,'PARTIAL','Backup lokal/cloud diimplementasikan; kebijakan backup database server belum diketahui; E10.'),
 (12,'FAIL','Restore Android memiliki risiko traversal pada OS lama; backup desktop gagal; uji restore lengkap belum; F04, F15.'),
])
set_items('07', [
 (1,'PARTIAL','Public Supabase/Firebase client key memang masuk build; bukan admin secret. Private token/API key dan log masih berisiko; F06. APK bukan tempat menyimpan admin secret.'),
 (2,'PARTIAL','0 nama file credential sensitif ditemukan dalam git ls-files; belum scan seluruh nilai dan riwayat Git; E6.'),
 (3,'PARTIAL','Tidak ditemukan file password DB yang dilacak pada pemeriksaan nama file; ini bukan scan nilai/history lengkap; E6.'),
 (4,'PARTIAL','Workflow utama memakai GitHub secrets; dev workflow kehilangan Supabase config; E6, L04.'),
 (5,'PARTIAL','Activity preview tidak ada di release; belum ada scan seluruh credential/test-account dalam APK; E4.'),
 (6,'FAIL','Nama ekspor dan path ZIP belum divalidasi lengkap; F04, F14.'),
 (7,'PARTIAL','Room/SDK menggunakan query parameter; belum ada uji injection pada backend live/custom SQL.'),
 (8,'N/A','Tidak ada frontend web produksi dalam cakupan; teks native bukan DOM browser.'),
 (9,'N/A','API akun native memakai Bearer, bukan auth cookie browser; endpoint berbasis cookie/web di luar scope belum dinilai.'),
 (10,'UNVERIFIED','Belum ada uji enforcement upload/avatar pada bucket server aktif.'),
 (11,'UNVERIFIED','Batas ukuran server/upload/arsip belum diverifikasi dengan berkas besar.'),
 (12,'UNVERIFIED','Belum ada uji spoof MIME, ekstensi dan konten berkas pada storage live.'),
 (13,'PASS','Manifest APK release tidak memiliki debuggable; build release default non-debug; activity preview tidak ada; E4.'),
 (14,'FAIL','Logger sensitif dan CurlLogging ALL masih tersedia; penghapusan log release belum aktif; F06.'),
 (15,'FAIL','Token/cookie dicatat eksplisit; F06. Service FCM juga mencatat token; E9.'),
 (16,'PARTIAL','Network security config menolak cleartext dan memakai CA sistem; trustEveryone tetap melemahkan jalur TLS; F07, E11.'),
 (17,'FAIL','Dependency mengubah default HttpsURLConnection dengan trustEveryone; F07. Tidak terbukti semua OkHttp/Supabase ikut terpengaruh.'),
 (18,'PARTIAL','Lint dan bytecode trust manager ditinjau; belum ada scan advisory/CVE seluruh dependensi; E1, E4.'),
 (19,'PARTIAL','Manifest masih memuat WRITE_EXTERNAL_STORAGE tanpa pembatasan OS dan READ_MEDIA_AUDIO; kebutuhan minimum tiap permission belum dibuktikan; E11.'),
])
set_items('08', [
 (1,'PARTIAL','Entity/cloud mencatat profil, follow, library, history dan presence; belum ada inventaris data resmi; E1, E5.'),
 (3,'FAIL','Log sensitif, privasi sosial dan pemisahan akun bermasalah; F01, F03, F06.'),
 (4,'FAIL','Consent menyebut Terms/Privacy sebagai teks tanpa tautan; dokumen/URL kebijakan tidak ditemukan di repo/UI; L02.'),
 (5,'FAIL','Hapus akun beserta data Supabase belum tersedia; L01.'),
 (6,'UNVERIFIED','Retensi akun/history/cloud/log dan pemusnahan backup belum didokumentasikan.'),
 (7,'PARTIAL','README/credits/dependencies menyebut layanan; tujuan pemrosesan data belum dijelaskan sebagai privacy policy; E7, L02.'),
 (8,'PARTIAL','Permission dan dialog sistem ada; rationale semua izin belum ditinjau; E11.'),
 (9,'FAIL','Label privasi tidak sesuai enforcement dan persetujuan tidak membuka policy; F03, L02.'),
])
set_items('09', [
 (1,'PARTIAL','Startup/UI pernah berjalan di emulator; cold-start p50/p95 perangkat nyata belum diukur; E3.'),
 (2,'UNVERIFIED','Ada runBlocking pada setter UI dan penundaan playback; perlu profiling, belum terbukti ANR; F18, F35.'),
 (3,'PARTIAL','Lifecycle scope Discord bermasalah; belum ada heap/leak measurement; F32.'),
 (6,'PARTIAL',f'APK universal {release["apk"]["MiB"]} MiB; ABI-specific sekitar 24–26 MiB; belum ada size-budget/baseline untuk menilai pemborosan; E4.'),
 (10,'PARTIAL','Cache Media3/Room tersedia; resolver partial cache bermasalah; F31.'),
])
set_items('10', [
 (1,'PARTIAL','Home nyata memuat data musik di emulator; auth/audio/cloud belum lengkap; E3.'),
 (3,'FAIL','Gagal/offline saat pindah akun dapat mencampur data; F01. Recovery jaringan umum belum diuji.'),
 (4,'FAIL','Metadata tambahan dan Discord send tidak mempunyai batas yang memadai; F18, F32.'),
 (5,'FAIL','Download gagal dapat menghalangi retry atau membawa lastException lama; F25.'),
 (6,'PARTIAL','Home/search punya cancellation/pagination guard; semua operasi tulis/double tap belum diuji; E3.'),
 (7,'FAIL','Partial cache dapat mengembalikan video ID sebagai URL saat upstream dibutuhkan; F31.'),
 (8,'FAIL','Prune snapshot dan penggantian playlist berisiko hilang data; F01, F02, F05, F10, F11, F17.'),
 (9,'FAIL','Sebagian jalur bisa menunggu tanpa deadline ketat; F18, F32. Belum ada chaos test lengkap.'),
])
set_items('11', [
 (5,'PARTIAL','Emulator ponsel viewport 720x1600 pernah diperiksa; bukan perangkat fisik kelas menengah; E3.'),
 (6,'PARTIAL','Home tablet 1920x1200 pernah diperiksa; seluruh halaman tablet belum; E3.'),
 (7,'PARTIAL','Dua viewport dan font 1,3x pada layar terpilih; belum seluruh aspect ratio; E3.'),
 (8,'PARTIAL','Layar utama ponsel diperiksa portrait; detail/landscape belum lengkap; E3.'),
 (9,'PARTIAL','Home viewport tablet landscape diperiksa; rotation/state dan seluruh layar belum; E3.'),
 (10,'UNVERIFIED','minSdk 26 terkonfirmasi, tetapi belum runtime test API 26; E4. F04 relevan OS lama.'),
 (11,'PARTIAL','Bukti emulator API 37 tersedia; bukan bukti seluruh OS pengguna saat ini; E3.'),
 (12,'PARTIAL','Compile SDK 37/target 36; emulator API 37 diuji terbatas. Penetapan dukungan OS lengkap belum; E3, E4.'),
])
set_items('12', [
 (1,'PARTIAL','Form mempunyai validasi dasar; semua API/parser empty input belum diuji.'),
 (3,'FAIL','Non-BMP HTML entity dapat melempar exception; cookie/LRC/nama slash salah ditangani; F14, F26, F33, F34.'),
 (5,'FAIL','Mood/Genre parse failure/empty bisa menghasilkan tidak ada state; F27.'),
 (9,'FAIL','Delete+insert cloud non-atomik dan kegagalan insert disembunyikan; F05.'),
 (10,'FAIL','Pemulihan akun/cache pada kegagalan jaringan belum aman; F01, F31.'),
 (11,'FAIL','Deadline metadata/Discord tidak memadai; F18, F32.'),
 (12,'FAIL','Upload/sync failure bisa terlihat sukses atau mencampur data; F01, F05, F16.'),
 (13,'FAIL','Terminal failure download dianggap selesai; retry bermasalah; F24, F25.'),
])
set_items('13', [
 (1,'PARTIAL','Avatar/cloud upload terimplementasi; akses/batas/gagal upload live belum diuji; E1.'),
 (2,'FAIL','Status terminal failure dan retry download salah; F24, F25, F31.'),
 (5,'FAIL','Sanitizer ekspor tidak menghapus slash; validasi canonical ZIP kurang pada OS lama; F04, F14.'),
 (6,'PARTIAL','MediaStore/SAF dan izin tersedia; runtime deny/revoke semua OS belum diuji; E10, E11.'),
 (7,'UNVERIFIED','Policy bucket aktif belum diperiksa; backup settings/database perlu threat-model akses; E5, E10.'),
 (8,'PARTIAL','Worker menghapus temp backup setelah menyimpan; crash/full-disk/download cleanup belum diuji; E10.'),
 (9,'PARTIAL','Opsi backup file download tersedia; restore semua aset belum diuji; E10, F04, F15.'),
])
set_items('14', [
 (1,'PARTIAL','Firebase messaging/service ada; tidak ditemukan registrasi token ke server pada onNewToken; pengiriman live belum; E9.'),
 (2,'PARTIAL','POST_NOTIFICATIONS dan request UI tersedia; grant/deny/revoke belum diuji; E11.'),
 (3,'PARTIAL','Service membaca title/body dan menyimpan notifikasi; payload produksi belum diuji; E9.'),
 (4,'PARTIAL','PendingIntent/deep link tersedia; follow back/recovery/live payload belum diuji; F08, F12, E9.'),
])
set_items('15', [
 (2,'N/A','Tidak ditemukan integrasi webhook yang menjadi fitur aplikasi; bukan sertifikasi webhook server lain.'),
 (3,'PARTIAL','Scheme com.tan.gratify/login-callback ada; allowlist OAuth/recovery Supabase aktif belum; E12, F08.'),
 (4,'N/A','Tidak ditemukan alur pembayaran/subscription produk Gratify; biaya penyedia layanan perlu verifikasi operator.'),
 (5,'PARTIAL','Dependensi dapat dibuild dan Home musik berfungsi; koneksi semua penyedia belum diuji; E3, E4.'),
 (6,'FAIL','Fallback/retry dan endpoint Gemini bermasalah; F13, F18, F24, F25, F27, F31.'),
])
set_items('16', [
 (1,'PARTIAL','Logger aplikasi aktif, tetapi belum aman untuk produksi; F06, E9.'),
 (3,'FAIL','Implementasi terpilih crashlytics-empty hanya log; DSN kosong; remote error tracking tidak aktif; L03.'),
 (4,'FAIL','Build full juga memilih crashlyticsEmpty; Sentry auto-init dimatikan; L03.'),
 (10,'FAIL','Token/cookie dan token FCM dapat masuk log; F06, E9.'),
])
set_items('17', [
 (1,'PARTIAL','Backup SQLite lokal/cloud sync ada; backup server Supabase bukan dibuktikan oleh sync; E10, F02.'),
 (2,'PARTIAL','File download bisa disertakan backup; backup bucket server belum diverifikasi; E10.'),
 (3,'PARTIAL','WorkManager harian/mingguan/bulanan tersedia; execution/retention/restore nyata belum; E10.'),
 (4,'PARTIAL','Legacy backup app-specific; modern backup Download/Gratify berupa ZIP, belum ada bukti enkripsi/akses sesuai sensitivitas; E10.'),
 (5,'FAIL','Tidak ada restore end to end yang lulus; ada risiko Android dan backup desktop rusak; F04, F15.'),
 (6,'UNVERIFIED','Belum ditemukan runbook recovery server, owner, RPO/RTO dan bukti drill.'),
 (7,'PARTIAL','README menyediakan email legal; emergency/on-call dan jalur eskalasi belum disepakati; E7.'),
 (8,'UNVERIFIED','Belum ditemukan prosedur rollback APK/database yang diuji bersama backup.'),
])
set_items('18', [
 (1,'PARTIAL','Variant debug dengan suffix .dev tersedia; isolasi backend dev dari produksi belum terbukti; E4, E6.'),
 (2,'UNVERIFIED','Belum ada bukti proyek/backend staging terpisah serta akun penguji.'),
 (3,'PARTIAL','Variant/workflow release tersedia; server, signing dan deploy production belum diverifikasi; E4, E6.'),
 (4,'PARTIAL','SUPABASE_URL lokal tersedia dan workflow utama memeriksa nonempty; kesesuaian proyek prod/allowlist belum; E6.'),
 (5,'UNVERIFIED','Tidak ada akses admin/snapshot schema untuk memastikan database produksi; E5.'),
 (6,'UNVERIFIED','Public key tersedia lokal; secret CI/signing/live service belum diverifikasi; E6.'),
 (7,'PASS','Release APK non-debug berdasarkan manifest/build type; E4.'),
 (8,'UNVERIFIED','Belum memeriksa daftar akun server produksi; contoh UI hanya debug.'),
 (9,'UNVERIFIED','Belum memeriksa/menyetujui pembersihan dataset server; tidak ada data pengguna dihapus.'),
 (10,'FAIL','Dev release workflow tidak menulis Supabase URL/key; default build kosong; L04. Workflow utama punya guard.'),
])
set_items('19', [
 (1,'PASS','APK release: com.tan.gratify; debug berbeda .dev; E4.'),
 (2,'PASS','Label APK release: Gratify; E4.'),
 (3,'PASS','APK release versionName 2.1.0 cocok konfigurasi; E4.'),
 (4,'PASS','APK release versionCode 61 cocok konfigurasi; monotonic terhadap store belum diperiksa; E4.'),
 (5,'PARTIAL','Icon resource ada; tampilan launcher release/adaptive berbagai perangkat belum; E4, E8.'),
 (6,'PARTIAL','Tema/logo gelap splash diperiksa pada debug; startup release belum; E3.'),
 (7,'FAIL','APK lokal unsigned; apksigner verify exit 1. CI signing terdefinisi tetapi hasil CI belum diperiksa; E4, E6.'),
 (8,'PARTIAL','APK lokal tidak memakai debug signer karena unsigned; certificate production belum diperiksa; E4.'),
 (9,'PARTIAL','assembleRelease berhasil menghasilkan APK ABI/universal, tetapi belum signed siap distribusi; AAB tidak dibuat; E4.'),
 (10,'UNVERIFIED','Tidak menginstal APK unsigned; perlu APK signed pada perangkat uji milik proyek.'),
 (11,'UNVERIFIED','Belum menguji upgrade, signature continuity, session, data dan migration dari versi sebelumnya.'),
 (12,'FAIL','Deskripsi/changelog ada tetapi changelog 61 masih menjanjikan Apple login yang sudah dihapus; L05, E8.'),
 (13,'PARTIAL','13 phoneScreenshots tersedia di fastlane; belum dicocokkan dengan UI terbaru dan store upload; E8.'),
 (14,'PARTIAL','Icon store tersedia di fastlane; validasi dimensi/branding dan publikasi belum; E8.'),
 (15,'PARTIAL','featureGraphic.png tersedia; validasi visual/dimensi dan publikasi belum; E8.'),
 (16,'FAIL','Tidak ditemukan policy/URL yang dapat dibuka dari aplikasi/repo; L02.'),
 (17,'UNVERIFIED','Belum ada bukti rating di console store.'),
 (18,'UNVERIFIED','Belum ada deklarasi Data safety yang diverifikasi terhadap data/SDK aktual.'),
 (19,'UNVERIFIED','Kategori store belum diverifikasi pada console.'),
 (20,'PARTIAL','Contact legal tersedia di README; support listing dan operasional belum; E7.'),
])
set_items('gate', [
 (1,'FAIL','Risiko Critical integritas/privasi/TLS masih terbuka; F01, F02, F03, F06, F07. Tidak ada insiden produksi yang diklaim.'),
 (2,'FAIL','Validasi ZIP OS lama dan logging/TLS belum diperbaiki; F04, F06, F07.'),
 (3,'FAIL','Pemisahan akun, OTP dan reset belum lulus; F01, F08, F09.'),
 (4,'FAIL','Privasi profil tidak ditegakkan dan RLS aktif belum diuji; F03, E5.'),
 (5,'FAIL','Atomicity/sync/pagination bermasalah; migration dan backend live belum; F02, F05, F21, F22.'),
 (6,'PARTIAL','Backup client ada; backup server/restore lengkap belum lulus; E10, F04, F15.'),
 (7,'FAIL','Dev release workflow kehilangan config dan server produksi belum dibuktikan; L04, E6.'),
 (8,'FAIL','Log token/cookie masih tersedia; scan secrets lengkap belum; F06, E6.'),
 (9,'UNVERIFIED','Monitoring layanan/server dan alert belum dapat diverifikasi.'),
 (10,'FAIL','Crash/error tracking remote tidak aktif pada wiring build saat ini; L03.'),
 (11,'PARTIAL','Release compile/lint lulus; unsigned, belum install/upgrade/UAT release; E4.'),
 (12,'UNVERIFIED','Runbook rollback/update beserta drill belum tersedia.'),
])

records=[]
def render_item(group, index):
    text=groups[group][index-1]
    status, reason=overrides.get((group,index), ('UNVERIFIED', defaults[group]))
    records.append({'id':f'{group}-{index:02}','section':group,'criterion':text,'status':status,'evidence_or_limit':reason})
    return f'* [{"x" if status == "PASS" else " "}] **{status}** — {text}. {reason}'

def link(label,path,line=None):
    return f'[{label}](D:/Tan/Gratify/{path}{":"+str(line) if line else ""})'

intro='''# Full app launch audit — Gratify

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

'''
evidence=[
 ('E1','Audit fitur: 20 temuan, debug/JVM/lint dan 7 probe','AUDIT_FITUR_2026-10-01.md'),
 ('E2','Audit fungsi: 16 tambahan; 757 file, 15 probe','AUDIT_FUNGSI_2026-10-01.md'),
 ('E3','Pemeriksaan visual/runtime UI sebelumnya','UI_REFRESH.md'),
 ('E4','Verifikasi release baru: ukuran, hash, lint, manifest, unsigned','artifacts/launch-audit/release-verification.json'),
 ('E5','Hotfix ownership lokal; belum bukti policy server','supabase_user_data_rls_hotfix.sql'),
 ('E5 sosial','Policy sosial lokal','supabase_social_rls_hotfix.sql'),
 ('E6','Workflow release utama dengan guard config',' .github/workflows/android-release.yml'.strip()),
 ('E7','Scope, platform, pihak ketiga dan kontak','README.md'),
 ('E8','Metadata dan changelog store','fastlane/metadata/android/en-US/changelogs/61.txt'),
 ('E9','Service FCM: handler dan token hanya dicatat','androidApp/src/main/java/com/tan/gratify/service/MyFirebaseMessagingService.kt'),
 ('E10','Worker backup lokal','androidApp/src/main/java/com/tan/gratify/service/backup/AutoBackupWorker.kt'),
 ('E11','Permission dan config aplikasi','androidApp/src/main/AndroidManifest.xml'),
 ('E12','SDK Supabase/Auth/redirect','composeApp/src/commonMain/kotlin/com/tan/gratify/di/SupabaseModule.kt'),
]
intro+='\n'.join(f'- **{key}:** {link(label,path)}' for key,label,path in evidence)+'\n\n'
intro+='Bukti release tambahan: '+link('build log','artifacts/launch-audit/release-build.log')+', '+link('manifest APK','artifacts/launch-audit/release-manifest.txt')+', '+link('lint release XML','androidApp/build/reports/lint-results-release.xml')+'. Pemeriksaan hash mengonfirmasi **757 file inventaris tetap sama** dengan audit fungsi. Tidak ada akses admin DB (`SUPABASE_DB_URL` belum tersedia); policy aktif, bucket, secret CI dan backup server belum dinilai. Nilai credential tidak dimasukkan laporan. Pemeriksaan nama file Git menemukan 0 file credential sensitif yang dilacak; belum merupakan scan nilai/history secret. Tidak dilakukan load test pada server, instalasi pada perangkat pengguna, atau perubahan data akun.\n\n---\n\n'

role_table='''| Feature | Guest | User | Admin |
|---|---|---|---|
| View public data | Diizinkan untuk data public sesuai SQL | Diizinkan | N/A dalam produk |
| View own private cloud data | Ditolak sesuai SQL | Milik sendiri; live belum diuji | N/A |
| Edit own data | Ditolak sesuai SQL | Milik sendiri; live belum diuji | N/A |
| View other users | Profil/follow/public playlist saja; privasi F03 | Data public saja; privasi F03 | N/A |
| Delete users | Tidak tersedia | Penghapusan akun sendiri belum ada (L01) | Operasional Supabase di luar client; belum diaudit |
| Admin panel | Tidak tersedia | Tidak tersedia | Tidak ada panel admin Gratify |

Matrix adalah kontrak source, bukan hasil pengujian authorization live. F01 dapat membawa data akun lain ke sesi baru meskipun RLS server benar. Perlu token A/B/guest untuk SELECT/INSERT/UPDATE/DELETE dan percobaan ID milik pihak lain, termasuk bucket. Privasi public berbeda dari isolasi private data.
'''
api_table='''Endpoint generik template dipetakan ke API yang benar-benar digunakan oleh SDK Gratify:

| Template → API | Auth | Authorization | Validation | Rate Limit | Status |
|---|---|---|---|---|---|
| /login → /auth/v1 (sign-in/OTP/recovery) | Public client key; password/OTP/session | SDK/backend; live belum diuji | Form sebagian; F08/F09 | Belum diverifikasi | PARTIAL/FAIL flow terkait |
| /users → /rest/v1/profiles, follows | Public/session sesuai operasi | RLS SQL tersedia; live belum diuji | Sebagian | Belum diverifikasi | FAIL privasi F03 |
| /profile → /rest/v1/profiles | Bearer untuk tulis | Ownership SQL; live belum diuji | Sebagian; optimistic failure F16 | Belum diverifikasi | PARTIAL/FAIL |
| /upload → /storage/v1 + cloud table terkait | SDK Storage/Bearer | Bucket live belum diverifikasi | MIME/ukuran/path belum lengkap | Belum diverifikasi | UNVERIFIED |
| /admin | Tidak ada client endpoint | N/A | N/A | N/A | N/A |
| Cloud library → /rest/v1/cloud_* | Bearer | Ownership SQL; live belum diuji | Sync/atomicity F01/F02/F05 | Belum diverifikasi | FAIL integritas client |

Nama tabel tepat dan payload mengikuti repository/SQL E1/E5; baris cloud_* adalah kelompok tabel, bukan URL literal. Endpoint pihak ketiga (YouTube, AI, Spotify, Discord) memerlukan audit token/batas/fallback per penyedia. Hanya API key public/publishable yang boleh dikirim sebagai konfigurasi client; jangan menanam service-role/admin key dalam APK.
'''
service_table='''| Service | Fungsi | Production | Status |
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
'''

body=[]; section=None; index=0; in_table=False; table_kind=None
for line in template.splitlines():
    if line.startswith('# BUG SEVERITY'):
        break
    match=re.match(r'## (\d{2})',line)
    if match:
        section=match[1]; index=0
    if section is None:
        continue
    if line.startswith('| Feature'):
        body.append(role_table); in_table=True; continue
    if line.startswith('| Endpoint'):
        body.append(api_table); in_table=True; continue
    if line.startswith('| Service'):
        body.append(service_table); in_table=True; continue
    if in_table:
        if line.startswith('|'):
            continue
        in_table=False
    if line.startswith('* [ ]'):
        index+=1; body.append(render_item(section,index))
    elif line.startswith('**Catatan:**'):
        body.append('**Catatan:** Penilaian source dan bukti runtime dibedakan; daftar bukti di atas berlaku untuk seluruh checklist.')
    elif section=='20' and line.startswith('**Flow'):
        body.append(line)
        n=int(re.search(r'Flow (\d)',line)[1])
        flow_reasons={1:'F08/F09 menghambat recovery/signup; fitur utama streaming nyata belum diuji.',2:'F02/F05/F17/F28 menghambat cloud CRUD dan urutan; tidak ada UAT akun nyata.',3:'F01 menghambat isolasi saat logout/login dan backup gagal.',4:'F24/F25/F31 serta sync F01/F02 menghambat recovery; tidak ada chaos UAT.',5:'Belum ada signed release/upgrade dari APK sebelumnya dan verifikasi data/migrasi.'}
        body.append('\n**Hasil:** UNVERIFIED untuk UAT runtime; '+flow_reasons[n]+' Kotak PASS/FAIL tidak dipilih karena bukan hasil uji end to end.\n')
    else:
        body.append(line)

gaps='''## Gap launch tambahan L01–L05

### L01 — HIGH — Penghapusan akun dan data belum tersedia

Pencarian auth/repository/UI/SQL tidak menemukan alur hapus akun Supabase atau endpoint penghapusan data pengguna. '''+link('deleteGoogleAccount','core/data/src/commonMain/kotlin/com/tan/data/repository/AccountRepositoryImpl.kt',55)+''' hanya menghapus akun Google lokal, bukan identitas Supabase. Logout juga tidak menghapus akun. Tambahkan alur penghapusan terautentikasi di server, konfirmasi pengguna, pemusnahan data/bucket terkait dan uji token revocation. Uji penerimaan: akun penguji dihapus, login kembali ditolak, data private/bucket hilang sesuai retensi, data pengguna lain tetap utuh.

### L02 — HIGH — Kebijakan privasi/terms tidak dapat dibuka

'''+link('SignUpScreen','composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/login/SignUpScreen.kt',449)+''' menampilkan checkbox dan teks persetujuan pada baris 463 tanpa tautan/navigasi ke dokumen. Pencarian repository/UI tidak menemukan policy/terms atau URL yang dapat dibuka. README legal disclaimer bukan kebijakan privasi. Siapkan dokumen sesuai alur data aktual, tautkan dari signup/Settings, dan verifikasi tautan sebelum persetujuan. Tidak disimpulkan ada/tidak ada dokumen di luar repository yang belum diberikan.

### L03 — HIGH — Wiring full build tidak mengaktifkan crash tracking

'''+link('androidApp Gradle','androidApp/build.gradle.kts',185)+''' selalu memilih crashlyticsEmpty. '''+link('BuildKonfig','composeApp/build.gradle.kts',557)+''' selalu mengisi sentryDsn kosong. '''+link('Crashlytics empty','crashlytics-empty/src/main/java/com/tan/gratify/crashlytics/Crashlytics.kt',8)+''' hanya mencatat log; Sentry auto-init dimatikan di manifest. isFullBuild dibaca tetapi tidak dipakai untuk memilih modul/DSN, meskipun script menawarkan full dengan Sentry. Untuk jalur full, sambungkan konfigurasi dan bukti event uji yang disanitasi. Untuk distribusi tanpa telemetry, dokumentasikan pilihan dan mekanisme laporan crash yang disetujui; jangan menyatakan monitoring remote aktif. Monitoring server tetap UNVERIFIED.

### L04 — HIGH — Workflow release dev kehilangan konfigurasi Supabase

'''+link('android.yml',' .github/workflows/android.yml'.strip(),44)+''' menulis local.properties hanya dengan Sentry lalu membangun APK release. '''+link('BuildKonfig','composeApp/build.gradle.kts',563)+''' default Supabase URL/key ke string kosong. '''+link('SupabaseModule','composeApp/src/commonMain/kotlin/com/tan/gratify/di/SupabaseModule.kt',21)+''' menyerahkan nilai tersebut langsung ke createSupabaseClient. Pada checkout CI bersih tidak ada config lokal, sehingga artefak dev release tidak memiliki konfigurasi auth yang valid. Workflow utama android-release.yml sudah memiliki guard URL/key; gap ini khusus android.yml. Terapkan config/guard konsisten dan verifikasi artefak CI tanpa memublikasikan nilai secret. PR dari fork tanpa secrets harus skip job distribusi atau gagal jelas, bukan membuat APK siap pakai palsu.

### L05 — LOW — Changelog masih menjanjikan Apple login

'''+link('Changelog 61','fastlane/metadata/android/en-US/changelogs/61.txt',1)+''' menyebut email, Google dan Apple; '''+link('LoginLandingScreen','composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/login/LoginLandingScreen.kt',324)+''' mencatat tombol Apple dihapus sesuai permintaan. Selaraskan metadata dan screenshot dengan fitur yang tersedia. Tidak disarankan mengembalikan Apple tanpa kebutuhan produk.

## Bug tracker

Semua F01–F36 masih **OPEN**, source identik dengan snapshot audit fungsi. Severity launch berikut adalah penilaian risiko untuk gate, bukan perubahan prioritas P1/P2/P3 lama. **Critical** berarti potensi pencampuran/hilang data, pelanggaran privasi atau TLS berdasarkan source/probe; bukan klaim insiden produksi atau auth bypass yang telah terjadi. F04 bergantung OS/target; F36 belum punya caller produksi. Assignee belum ditentukan.

| ID | Bug | Severity launch / prioritas lama | Location | Status | Assignee |
|---|---|---|---|---|---|
'''
critical={'F01','F02','F03','F06','F07'}
medium={'F10','F11','F12','F19','F26','F27','F29','F30','F32','F33','F34','F35'}
findings=[]
for path in [ROOT/'AUDIT_FITUR_2026-10-01.md',ROOT/'AUDIT_FUNGSI_2026-10-01.md']:
    text=path.read_text(encoding='utf-8-sig')
    blocks=re.split(r'(?=^### F\d{2} —)',text,flags=re.M)
    for block in blocks:
        m=re.match(r'### (F\d{2}) — (P\d) — ([^\n]+)',block)
        if not m: continue
        ident,priority,title=m.groups()
        source=re.search(r'\[([^\]]+)\]\((D:/[^)]+)\)',block)
        location=f'[{source[1]}]({source[2]})' if source else link(path.name,path.name)
        severity='Critical' if ident in critical else 'Low' if priority=='P3' else 'Medium' if ident in medium else 'High'
        findings.append({'id':ident,'title':title,'priority':priority,'launch_severity':severity,'location':location,'status':'OPEN','assignee':'Belum ditetapkan'})
        gaps+=f'| {ident} | {title} | {severity} / {priority} | {location} | OPEN | — |\n'
for ident,title,severity,location in [
 ('L01','Penghapusan akun/data Supabase belum tersedia','High',link('AccountRepositoryImpl','core/data/src/commonMain/kotlin/com/tan/data/repository/AccountRepositoryImpl.kt',55)),
 ('L02','Consent tanpa tautan policy/terms','High',link('SignUpScreen','composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/login/SignUpScreen.kt',463)),
 ('L03','Full build memakai crashlytics-empty/DSN kosong','High',link('androidApp Gradle','androidApp/build.gradle.kts',185)),
 ('L04','Dev release CI tanpa Supabase config','High',link('android.yml','.github/workflows/android.yml',44)),
 ('L05','Changelog menjanjikan Apple login','Low',link('Changelog 61','fastlane/metadata/android/en-US/changelogs/61.txt',1)),
]:
    findings.append({'id':ident,'title':title,'priority':None,'launch_severity':severity,'location':location,'status':'OPEN','assignee':'Belum ditetapkan'})
    gaps+=f'| {ident} | {title} | {severity} / gap launch | {location} | OPEN | — |\n'
assert len(findings)==41

gate='\n# Final release gate\n\n## Blocker check\n\n'
gate+='\n'.join(render_item('gate',i) for i in range(1,len(groups['gate'])+1))
gate+='''

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
'''

assert len(records)==sum(map(len,groups.values()))==251
counts=Counter(r['status'] for r in records)
summary='### Rekap administrasi checklist\n\n'+', '.join(f'**{k}: {v}**' for k,v in sorted(counts.items()))+'. Sepuluh pilihan UAT termasuk UNVERIFIED; rekap ini bukan skor kesiapan.\n\n'
report=intro+summary+'\n'.join(body)+'\n'+gaps+gate
(ROOT/'LAUNCH_AUDIT_2026-10-01.md').write_text(report,encoding='utf-8')
with (OUT/'checklist.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=list(records[0])); writer.writeheader();writer.writerows(records)
(OUT/'checklist.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(OUT/'bug-tracker.json').write_text(json.dumps(findings,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
summary_data={'date':'2026-10-01','project':'Gratify','version':'2.1.0 (61)','decision':'NEED_FIX_BEFORE_LAUNCH','template_sha256':hashlib.sha256(ATTACHMENT.read_bytes()).hexdigest(),'template_checkboxes':251,'uat_choice_checkboxes':10,'release_gate_checkboxes':12,'status_counts':dict(counts),'existing_open_findings':36,'new_launch_gaps':5,'source_changed':False,'signed_release_tested':False,'live_backend_tested':False}
(OUT/'audit-summary.json').write_text(json.dumps(summary_data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary_data,ensure_ascii=False,indent=2))
