# Launch fixes, 2026-10-01

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
13 akun asli dipertahankan. Storage/avatar ditemukan gagal unggah pemilik
karena tidak ada policy pada storage.objects. Migrasi 008 disiapkan untuk
hak avatar milik sendiri (nama auth UID + .jpg) dengan batas 5 MB JPEG/PNG/WebP;
penerapan melalui dashboard menunggu konfirmasi pemberian akses.
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
