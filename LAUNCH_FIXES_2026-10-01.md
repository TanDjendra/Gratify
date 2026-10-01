Status terbaru: [penyelesaian dan APK bertanda tangan](LAUNCH_COMPLETION_2026-10-01.md). Angka/build pada laporan berikut merekam tahap sebelumnya.

# Perbaikan audit Gratify — 1 Oktober 2026

Status: **VERIFIKASI KODE LOKAL LULUS; BELUM SIAP RILIS PRODUKSI**.
Laporan ini melengkapi audit awal. Temuan awal dipertahankan sebagai riwayat,
bukan bukti bahwa versi sekarang masih memiliki seluruh masalah yang sama.

## Urutan perbaikan

| Temuan | Perubahan yang disiapkan | Bukti / batas verifikasi |
|---|---|---|
| F01 | Arsip Room per akun, aktivasi akun atomik sebelum akses jaringan, logout mempertahankan koleksi akun | Tes pergantian akun offline, rollback arsip rusak, ekspor hanya akun aktif, migrasi Room 25→26 lulus |
| F02 | Jurnal perubahan tambah/hapus koleksi milik akun, pengakuan berdasarkan revisi, status hapus di server | Tes jurnal Room dan RPC database lokal lulus; sinkronisasi dua perangkat pada Supabase belum diuji |
| F03 | Aturan privasi server, proyeksi profil publik yang menyamarkan aktivitas, pembatasan playlist/follow/lagu playlist | Tes anon dan dua akun di PostgreSQL lokal lulus; migrasi belum diterapkan ke Supabase |
| F04 | ZIP divalidasi di folder sementara, batas ukuran/path, validasi SQLite, penggantian dengan rollback | Tes traversal, batas ukuran, kegagalan penggantian, dan pengaturan rusak lulus; restore juga diperkuat pada desktop, belum diuji lewat UI perangkat |
| F05 | Penyimpanan metadata dan isi playlist dalam RPC atomik, kegagalan tidak dilaporkan berhasil | Tes data lama tetap utuh setelah penggantian gagal lulus di database lokal |
| F06 | Logging HTTP rinci/Curl dinonaktifkan; header sensitif disamarkan, query dan body tidak dicetak | Tes header/query/body Curl lulus; pengujian log perangkat lengkap belum dilakukan |
| F07 | Inisialisasi extractor tidak mengubah pemeriksaan TLS global | Tes TLS setelah init dan pergantian proxy lulus, termasuk sertifikat/hostname tidak sah |
| F08–F09 | Reset password hanya setelah callback recovery terverifikasi; kegagalan menyimpan password signup tetap gagal dan bisa dicoba ulang | Kompilasi; alur email/redirect Supabase nyata belum diuji |
| F10–F11 | Pengaturan cloud melalui setter bertipe; semua riwayat putar dipulihkan; antrean benar-benar direkonstruksi; tanggal UTC/offset dipahami; token Spotify dibersihkan saat berganti akun | Tes round trip pengaturan, zona waktu, dan pembersihan token lulus; tes antrean atomik di database lokal lulus |
| F12 | Pembatalan coroutine diteruskan, emit profil dipisahkan dari penanganan error | Tes firstOrNull pada repository nyata melalui server HTTP lokal lulus; alur layar nyata belum diuji |
| F13 | Endpoint kompatibilitas OpenAI Gemini menggunakan jalur `/v1beta/openai/` | [Dokumentasi Google](https://ai.google.dev/gemini-api/docs/openai); permintaan berbayar belum diuji |
| F14–F15 | Nama ekspor aman termasuk AC/DC dan nama Windows; backup JVM memakai satu stream ZIP dan salinan database konsisten | Tes nama file dan ekspor database lulus; backup melalui pemilih file desktop belum diuji |
| F16 | Follow/profile menunggu keberhasilan server; gambar avatar yang tidak dapat diproses menyebabkan kegagalan terlihat | Kompilasi; uji upload dan kegagalan jaringan nyata belum dilakukan |
| F17 | Penghapusan playlist lokal mencatat pekerjaan cloud yang tahan offline, termasuk pelepasan playlist simpanan pengguna lain | Tes jurnal penghapusan dan RPC database lokal lulus; dua perangkat belum diuji |
| F18–F19 | Metadata opsional keluar dari jalur pemutaran utama; timer akhir lagu mengikuti perubahan/akhir media | Kompilasi; pengukuran waktu mulai audio dan uji timer perangkat belum dilakukan |
| F20 | Tombol More profil kosong dihapus | Pemeriksaan kode |
| F21–F23 | Refresh memakai nomor halaman; cursor waktu memakai timestamp + songId; setVideoId memakai kedua kunci playlist/video | Tes 120 lagu dengan timestamp sama, refresh halaman kedua, dan dua playlist/video sama lulus |
| F24–F25 | Kegagalan unduhan tidak mengirim tanda selesai; retry membuang data parsial dan menghapus error percobaan lama saat berhasil; setiap potongan paralel wajib sesuai Content-Range dan panjang yang diminta | Tes HTTP lokal: kegagalan akhir tidak selesai palsu, retry berikutnya berhasil tanpa error lama, potongan 206 yang kurang panjang ditolak sebelum penggabungan |
| F26–F28 | Cookie mempertahankan padding; parser mood/genre aman pada data kosong; reorder seluruh posisi dalam satu transaksi | Tes cookie dan reorder lulus; parser payload layanan nyata belum diuji |
| F29–F30 | Heartbeat profil mengikuti lifecycle; statistik menghitung waktu audio benar-benar berjalan | Tes penghitung waktu lulus; heartbeat Supabase belum diuji |
| F31–F32 | Cache parsial tidak dianggap file lengkap; scope Discord tetap dan pengiriman berbatas waktu | Kompilasi; playback cache terpotong/reconnect Discord pada perangkat belum diuji |
| F33–F36 | Unicode non-BMP aman; LRC mendukung presisi 1–3 desimal, beberapa timestamp, offset; speed/pitch suspend; markdown mempertahankan label | Tes Unicode, LRC dan markdown lulus; interaksi speed/pitch di perangkat belum diuji |
| L01 | Tombol hapus akun dengan konfirmasi; pemeriksaan RPC sebelum menghapus avatar; RPC hanya menghapus akun terautentikasi dan datanya; arsip akun lain dipertahankan | Tes database dan Room lokal lulus; Auth/Storage Supabase nyata belum diuji |
| L02 | Draf Terms/Privacy dibuka dari signup; dukungan `supportgratify@gmail.com`; pengelola Tan Heradhe Rat Djendra (TanDjendra), developer tunggal di Indonesia | Identitas dan negara dikonfirmasi; alamat, retensi, persetujuan isi dokumen dan URL resmi masih perlu dilengkapi |
| L03 | Modul Sentry versi full dihubungkan, data pribadi event dikurangi; CI menolak DSN kosong | Kompilasi dan packaging versi full lulus; DSN belum tersedia dan penerimaan event pada proyek Sentry belum diuji |
| L04 | CI memerlukan konfigurasi Supabase dan menjalankan tes regresi yang ditambahkan | Pemeriksaan workflow termasuk pemisahan build FOSS/full; GitHub Actions belum dijalankan dari sesi ini |
| L05 | Klaim login Apple yang belum tersedia dihapus dari changelog | Pemeriksaan dokumen |

## Hasil verifikasi kode lokal

- 28 tes regresi JVM pada enam modul lulus, tanpa kegagalan.
- Build Android debug dan release versi full lulus, termasuk optimasi R8.
- Pemeriksaan Android lint selesai: 0 error dan 122 warning. Warning belum seluruhnya ditutup.
- Field downloader extractor tetap tersedia setelah R8 sesuai aturan ProGuard.
- Pengemasan ulang resource lulus; isi Terms/Privacy di kedua APK identik dengan draf di `docs/legal`, termasuk identitas pengelola dan label DRAF.
- APK release masih belum ditandatangani dengan kunci rilis.

Bukti: `artifacts/launch-audit/fixes-final-verification.log` dan
`artifacts/launch-audit/fixes-verification.json`.
Pemeriksaan draf dalam APK: `artifacts/launch-audit/packaged-legal-verification.json`;
log pengemasan: `artifacts/launch-audit/legal-resource-packaging.log`.
Paket release terverifikasi memakai `com.tan.gratify`, versi 2.1.0 (61), target SDK 36.
Tes mencakup dua regresi tambahan yang terlebih dahulu direproduksi: token Spotify
tertinggal setelah pergantian akun dan respons unduhan paralel 206 yang kurang
panjang dianggap selesai. Kedua tes lulus setelah perbaikan.

## Pemeriksaan database

Enam migrasi baru diuji dua kali pada PostgreSQL 17.6 lokal terpisah. Delapan
pemeriksaan lulus: penerapan ulang, rollback metadata/lagu/publikasi playlist,
privasi anon, akses pemilik vs akun lain, tambah/hapus/tambah lagi koleksi,
rollback antrean, hapus playlist, dan hapus hanya akun pemanggil.

Bukti: `artifacts/launch-audit/database-fixes-verification.json`.
Database uji dimatikan setelah pengujian. Tidak ada data Supabase aktif diubah.
`SUPABASE_DB_URL` belum tersedia ketika diperiksa; pelaksana migrasi berhenti
sebelum perubahan bila skema aktif berbeda dari kontrak yang diuji.

## Syarat menutup audit rilis

### Batas yang masih perlu ditangani

Pemetaan playlist lintas perangkat masih memakai ID lokal/nama dari mekanisme
lama. Perubahan transaksi dan jurnal belum membuktikan identitas playlist tetap
konsisten pada dua perangkat. Pengaturan privasi lama yang hanya tersimpan lokal
juga belum otomatis dipindahkan menjadi pilihan server; pilihan tersebut perlu
ditinjau dan diterapkan kembali setelah migrasi. Kedua hal ini tetap menjadi
syarat pengujian/pengembangan sebelum rilis, bukan temuan yang telah ditutup.

Pemeriksaan metadata API Supabase dengan akses anon menerima HTTP 401. Hasil ini
tidak membuktikan tabel hilang ataupun bahwa skema server sudah sesuai; verifikasi
server tetap menunggu akses database yang diperlukan. DSN Sentry belum tersedia.

### Pekerjaan rilis yang tersisa

1. Terapkan migrasi ke Supabase yang benar, setelah memeriksa kontrak tabel aktif.
2. Uji dua akun dan dua perangkat, termasuk kondisi offline dan jaringan gagal.
3. Uji email signup/recovery, redirect allowlist, Storage avatar, dan hapus akun uji.
4. Setujui dokumen legal dan berikan URL resmi; kontak dukungan sudah diperbarui.
5. Verifikasi event Sentry, konfigurasi secret CI, signing rilis, install/upgrade,
   playback background, cache/unduhan, serta seluruh daftar UAT audit awal.

Keberhasilan kompilasi/tes lokal tidak berarti seluruh checklist peluncuran
sudah ditutup. Status akhir per temuan tersimpan di tracker perbaikan.
