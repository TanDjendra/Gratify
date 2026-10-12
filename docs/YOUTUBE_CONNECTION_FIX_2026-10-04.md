# Perbaikan koneksi YouTube, 4 Oktober 2026

Callback Android pada 08.48.40 WIB menerima kode Google dan scope
`youtube.readonly`, kemudian merespons HTTP 409 `CONNECTION_REQUIRED`.
Pemeriksaan kredensial server membuktikan Google merespons HTTP 401
`invalid_client`.

Nilai `GOOGLE_OAUTH_CLIENT_SECRET` ternyata teks literal `<redacted>`.
Digest pada dashboard Supabase sama persis dengan SHA-256 teks tersebut:
`3a2d1afa44d101200100d332f52384e523e7e881b7db0008da228cd94cebdfe1`.
Kolom provider Google Supabase menyamarkan secret; teks penyamaran tidak
dapat dipakai sebagai kredensial. Google Cloud juga tidak lagi menampilkan
secret yang dibuat sebelumnya.

## Perubahan aktif

- Dengan persetujuan pengguna, satu secret tambahan dibuat pada OAuth client
  **Gratify Login** dalam proyek `gratify-auth`, lalu disimpan langsung sebagai
  `GOOGLE_OAUTH_CLIENT_SECRET` pada Supabase `bnabldxsqpvkyqpjcsdv`.
- Secret Google lama tetap aktif untuk login Supabase. Client ID, callback,
  scope baca saja, kunci enkripsi, dan flag izin login otomatis tidak diubah.
- Error Google `invalid_client`/`unauthorized_client` kini menghasilkan
  `CONFIGURATION_REQUIRED` (503). Refresh yang gagal karena konfigurasi
  tidak menandai grant pengguna sebagai dicabut.
- Setelah grant tersimpan, callback Android menggunakan HTTP 303 menuju
  `com.tan.gratify://youtube-connected`. Kode, state, dan token tidak masuk
  tautan pengembalian. Desktop menerima instruksi teks yang dapat dibaca.
- Probe diagnosis sementara hanya menerima kredensial service-role, kemudian
  dihapus pada deploy akhir. Tidak ada secret baru dalam repo, APK, atau laporan.

## Verifikasi

- Sebelum pembaruan secret: probe dengan kode diagnosis sengaja tidak valid
  menghasilkan HTTP 401 `invalid_client` dari Google.
- Sesudah pembaruan: probe yang sama menghasilkan HTTP 400 `invalid_grant`.
  Ini menunjukkan kredensial client diterima; kode diagnosis memang bukan
  kode izin pengguna yang sah. Ini **bukan** bukti impor koleksi nyata.
- **29/29 pengujian backend lulus**, termasuk callback Android dengan tujuan
  tetap, penyimpanan sebelum redirect, isolasi akun, refresh, dan nonce.
- Kode service pada editor server cocok dengan sumber lokal yang diuji.
- GET readiness tetap HTTP 200: `configured=true`, `enabled=false`.
- GET ke probe yang dihapus merespons HTTP 405 pada handler normal.
- Tidak ada perubahan APK atau konfigurasi login Google Supabase.

Ulangi Hubungkan Google dari aplikasi dengan percobaan izin baru. Jangan
memakai ulang URL callback sebelumnya. Pengambilan playlist/video disukai
akun pengguna masih perlu dibuktikan setelah percobaan baru tersebut.

## Bukti dan rujukan

- `artifacts/youtube-connection-fix-2026-10-04/backend-tests.log`
- `artifacts/youtube-connection-fix-2026-10-04/secret-updated-proof.jpg`
- `artifacts/youtube-connection-fix-2026-10-04/google-existing-client.jpg`
- [Supabase: GET HTML pada domain bersama diubah menjadi teks biasa](https://supabase.com/docs/guides/functions/http-methods)
- [Google: perubahan akses client secret](https://support.google.com/cloud/answer/15549257#client-secret-hashing)
