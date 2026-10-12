# Aktivasi koneksi YouTube Gratify, 4 Oktober 2026

Pesan “belum diaktifkan oleh pengelola” sebelumnya benar: endpoint server
`youtube-library` masih HTTP 404, tabel OAuth belum terpasang, dan secret
integrasi belum tersedia. Tombol Periksa kembali hanya memeriksa ulang server;
menekan tombol itu tidak mengaktifkan Google Cloud/Supabase.

Setelah pengguna menyetujui aktivasi uji terbatas, konfigurasi server berikut
dipasang pada proyek Google `gratify-auth` dan Supabase `bnabldxsqpvkyqpjcsdv`.

## Hasil aktivasi

| Bagian | Hasil |
| --- | --- |
| YouTube Data API v3 | Enabled |
| Izin YouTube | `https://www.googleapis.com/auth/youtube.readonly` |
| OAuth audience | External, Testing |
| Tester | `supportgratify@gmail.com` (1 test user) |
| Client | Gratify Login yang sudah dipakai Supabase; client/secret tidak diganti |
| Callback | Callback login Supabase dan callback function YouTube terdaftar |
| Migrasi | `202610020002_youtube_oauth_connections.sql` diterapkan |
| Function | `youtube-library` terpasang; kode kedua file sama dengan sumber lokal |
| Secret | Client ID, client secret, kunci AES-GCM tersimpan langsung di Edge Secrets |
| Login Google umum | Consent YouTube otomatis dinonaktifkan selama Testing |
| APK | Tidak dibangun, ditandatangani, dipasang, atau dipublikasikan |

Kunci enkripsi dibuat dari 32 byte acak kriptografis di server dan dipindahkan
langsung ke Edge Secrets. Tidak ada nilai secret dalam laporan, repo, atau APK.
Gateway legacy JWT function dinonaktifkan agar callback Google GET tersedia.
Setiap POST tetap memverifikasi JWT pengguna melalui Supabase `auth.getUser()`.

## Verifikasi

- **23/23 pengujian backend lulus**: isolasi akun/token, scope/client/subject
  Google, enkripsi, refresh, pagination, pencabutan koneksi, nonce sekali pakai,
  serta koneksi manual ketika consent login umum dinonaktifkan.
- **6/6 pemeriksaan database aktif lulus**: kedua tabel ada, RLS aktif, role
  anon/authenticated tidak memiliki akses tabel/RPC, service_role memiliki
  hak tabel dan RPC yang diperlukan.
- GET `?readiness=1`: **HTTP 200**, `{ "configured": true, "enabled": false }`.
- POST status tanpa login: **HTTP 401 AUTHENTICATION_REQUIRED**.
- POST status dengan token palsu: **HTTP 401 AUTHENTICATION_REQUIRED**.
- Callback dengan state palsu: **HTTP 409 CONNECTION_REQUIRED**; tidak menerima
  koneksi atau menyimpan grant. Seluruh respons memakai `Cache-Control: no-store`.

`configured=true` menunjukkan konfigurasi server tersedia. `enabled=false`
menjaga login Google pengguna umum tetap memakai izin identitas biasa: pengguna
di luar daftar tester belum boleh memberikan izin YouTube dalam mode Testing.
Koneksi manual tester tidak bergantung pada flag consent login ini. Jangan
mengubah `YOUTUBE_LOGIN_CONSENT_ENABLED` menjadi true untuk login umum sebelum
status publik/verifikasi Google dan pengujian akun nyata selesai.

## Cara uji pada perangkat

1. Di Gratify, buka pengaturan koneksi YouTube atau bagian YouTube di Library.
2. Tekan **Periksa kembali** bila status lama masih tampil.
3. Pilih **Hubungkan Google** dan gunakan `supportgratify@gmail.com`.
4. Berikan izin baca koleksi YouTube sekali, lalu kembali ke Gratify.
5. Uji playlist milik akun, video disukai, pagination, restart, dan pemutusan
   koneksi. Untuk akun Gratify yang login Google, identitas Google koneksi harus
   sama dengan identitas login. Akun Gratify email dapat menghubungkan tester.

**Belum dibuktikan:** persetujuan Google, penukaran grant, dan impor koleksi pada
akun nyata melalui APK. Kesiapan endpoint tidak membuktikan ketiga langkah itu.
OAuth tetap Testing; aktivasi ini belum membuka akses YouTube bagi semua pengguna.
Token Testing dapat perlu persetujuan ulang sesuai kebijakan Google.

## Pemantauan

Automation heartbeat `pantau-koneksi-youtube-gratify` aktif setiap jam untuk
memeriksa endpoint publik. Kondisi normal adalah configured=true; enabled=false
disengaja selama Testing. Pemantauan hanya memberi kabar jika ada perubahan
penting/gangguan/pemulihan, tidak mengubah server dan tidak mengakses token.
Pemantauan berjalan mengikuti ketersediaan aplikasi/scheduler Codex lokal.

## Bukti

- Sebelum aktivasi: `artifacts/youtube-activation-audit-2026-10-04/live-status.json`.
- Sesudah aktivasi: `artifacts/youtube-activation-audit-2026-10-04/after-activation.json`.
- Pengujian: `artifacts/youtube-activation-audit-2026-10-04/backend-tests.log`.
- Gambar: `google-testing-proof.jpg`, `server-secrets-proof.jpg` di folder bukti.

Panduan resmi: [status Testing dan kesiapan produksi Google OAuth](https://developers.google.com/identity/protocols/oauth2/production-readiness/overview)
dan [autentikasi YouTube Data API](https://developers.google.com/youtube/v3/guides/authentication).
