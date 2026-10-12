# Launch fixes, 2026-10-01

## Integrasi resmi YouTube untuk uji terbatas

`202610020002_youtube_oauth_connections.sql` diterapkan pada 4 Oktober 2026
ke proyek `bnabldxsqpvkyqpjcsdv` setelah persetujuan aktivasi uji terbatas.
Migrasi ini menyediakan penyimpanan token terenkripsi yang hanya dapat diakses
backend serta callback OAuth sekali pakai. Aktivasi memerlukan akun pengelola
proyek Google Cloud 1072882393306, API YouTube, redirect URI, dan secret Edge
Function. Konfigurasi tersebut sudah terpasang; OAuth masih Testing untuk
`supportgratify@gmail.com`. Enam pemeriksaan RLS/grant database aktif lulus.
Rincian ada di [panduan aktivasi](../../docs/YOUTUBE_OAUTH_ACTIVATION.md) dan
[laporan pemeriksaan](../../docs/YOUTUBE_ACTIVATION_CHECK_2026-10-04.md).

Urutan perbaikan database yang disiapkan: `supabase_user_data_rls_hotfix.sql`,
`supabase_social_rls_hotfix.sql`, lalu tujuh migrasi `20261001…` secara berurutan.
Paket kompatibilitas UUID sudah diterapkan pada proyek Gratify aktif
`bnabldxsqpvkyqpjcsdv` melalui SQL Editor admin. Bukti berada di
`artifacts/launch-audit/database-deployment.json`.
Untuk skema UUID aktif, gunakan paket `supabase-launch-fixes-uuid.sql` yang
mempertahankan tipe PK/FK asli; jangan menjalankan file TEXT mentah satu per satu.
Migrasi 008 untuk avatar merupakan tahap terpisah dan belum diterapkan.
Jangan menjalankan ulang setup atau hotfix policy setelah migrasi privasi, karena
policy publik lama dapat membuka kembali data yang disembunyikan.

Pemeriksaan lokal: `artifacts/launch-audit/database-fixes-verification.json`.
Tiga belas skenario diuji pada PostgreSQL terpisah dengan kontrak tabel dari
`supabase_setup.sql`, bukan pada Supabase aktif.

Pelaksana: `artifacts/launch-audit/apply-database-fixes.py`.
Tanpa opsi hanya memvalidasi sintaks. `--apply` membaca `SUPABASE_DB_URL` dari
lingkungan atau `local.properties`, memeriksa kontrak skema, lalu menerapkan
hotfix dan migrasi dalam satu transaksi. Credential tidak dicetak.
Koneksi memverifikasi sertifikat dan nama host dengan `verify-full`.
`SUPABASE_SSL_ROOT_CERT` dapat menunjuk CA proyek yang diunduh dari dashboard.
Skema lama dengan `user_id UUID`, tanggal/kolom berbeda, atau settings JSONB
memerlukan migrasi kompatibilitas berdasarkan hasil inspeksi; pelaksana berhenti
sebelum perubahan jika kontrak berbeda.

Uji setelah penerapan: dua akun nyata, privasi dari anon/akun lain, sinkronisasi
dua perangkat termasuk offline, kegagalan simpan playlist, pemulihan password,
serta hapus akun uji. Auth/PostgREST dua akun dan SQL/RLS aktif telah diuji;
13 akun asli dipertahankan. Migrasi 008 telah diterapkan pada 2 Oktober 2026
untuk hak avatar milik sendiri (nama auth UID + .jpg), dengan batas 5 MB
JPEG/PNG/WebP. Enam pemeriksaan Storage API lulus: upload, upsert, batas
path/MIME/ukuran, isolasi pemilik, anon ditolak, dan penghapusan origin.
URL publik yang sebelumnya diminta dapat masih dilayani dari cache setelah
objek origin dihapus. Bukti: avatar-storage-deployment.json dan
live-storage-verification.json dalam artifacts/launch-audit.
Pengiriman email pemulihan dan UAT dua perangkat belum dibuktikan.

Migrasi 007 memberi setiap playlist identitas tetap. Judul dan ID numerik lokal
tidak lagi digunakan untuk pencocokan, penghapusan, atau deduplikasi.
Semua baris lama dipertahankan; hubungan cloud/shared historis yang ambigu
tidak ditebak. RPC lama memakai ID numerik dicabut dari authenticated, sehingga
versi aplikasi lama perlu diperbarui bersama penerapan server. Tombstone
mencegah perangkat lama menerbitkan ulang playlist yang telah dihapus.

RPC identitas baru memakai SECURITY DEFINER dengan search_path kosong dan pemeriksaan auth.uid pada setiap jalur. Mutasi REST langsung pada empat tabel playlist dicabut, termasuk grant per kolom, agar klien lama tidak dapat melewati transaksi. Pembacaan tetap mengikuti RLS.

Dua RPC snapshot baca memakai SECURITY INVOKER dan RLS. Setiap RPC mengembalikan
satu nilai JSON berurutan agar playlist dengan lebih dari 1.000 lagu tidak
terpotong oleh batas baris PostgREST. Tes menggunakan 1.025 lagu.

Migrasi `202610020001_profile_playlist_pins.sql` diterapkan pada 2 Oktober 2026
ke proyek aktif `bnabldxsqpvkyqpjcsdv`, setelah persetujuan pengguna. Migrasi
memisahkan status publik dari pilihan dipajang di profil. Unpin mempertahankan
tautan publik; profil yang disembunyikan menyembunyikan daftar profil, sedangkan
playlist privat tetap dilindungi. Backfill pilihan lama hanya berjalan pertama kali.
Verifikasi aktif mengonfirmasi kolom, tiga RPC, trigger, grant, policy cloud/shared,
serta nol playlist privat yang masih dipin. Dua puluh pemeriksaan regresi lokal
lulus pada skema TEXT dan UUID. Bukti: `artifacts/music-quality/profile-pin-verification.json`,
`live-migration-verification.json`, dan `supabase-pin-migration-proof.jpg`.

Migrasi `202610030002_playlist_saves_covers.sql` menyiapkan sampul privat yang
mengikuti status publik playlist, RPC love/Simpan idempotent dengan pemeriksaan
identitas akun, hitungan unik dari pengguna lain, pemulihan simpan lintas perangkat,
serta pemeliharaan ID dan simpan saat sumber diubah privat. Jalur INSERT simpan APK
lama tetap tersedia dengan RLS yang memeriksa publik/non-pemilik. Lima puluh
pemeriksaan lokal pada skema TEXT dan UUID lulus. Status aktivasi dicatat dalam
`docs/PLAYLIST_AUDIT_FIX_2026-10-03.md` dan artefak playlist-audit.

Migrasi playlist di atas telah diterapkan pada 3 Oktober 2026 ke proyek aktif
`bnabldxsqpvkyqpjcsdv`, sesuai permintaan perbaikan dan pilihan pengguna mengenai
love/Simpan serta privasi sampul. Verifikasi aktif mengonfirmasi dua RPC baru,
grant, bucket privat, tiga policy sampul, identitas tetap saat privat, dan nol
selisih hitungan. Bukti tersimpan di `artifacts/playlist-audit-2026-10-03`.
