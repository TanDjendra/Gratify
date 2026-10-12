# Integrasi resmi YouTube — aktif untuk uji terbatas

Pilihan yang disetujui: akses baca saja melalui Google OAuth untuk playlist milik
pengguna dan video yang disukai. Akses YouTube Music pribadi melalui cookie
WebView tidak digunakan oleh alur Library yang baru.

## Status 4 Oktober 2026

- YouTube Data API v3 aktif pada proyek `gratify-auth` (1072882393306).
- Scope baca saja `youtube.readonly` tersimpan; `supportgratify@gmail.com`
  terdaftar sebagai tester. OAuth masih **Testing**, belum dipublikasikan.
- Dua callback sudah terdaftar pada client Google yang sama, tanpa mengganti
  client/secret login Supabase.
- Migrasi `202610020002_youtube_oauth_connections.sql` diterapkan ke Supabase
  `bnabldxsqpvkyqpjcsdv`. Enam pemeriksaan hak akses/RLS aktif lulus.
- Function `youtube-library` terpasang. Tiga secret OAuth/enkripsi tersimpan
  langsung di Edge Secrets. Nilai secret tidak disimpan dalam repo/APK/chat.
- `YOUTUBE_LOGIN_CONSENT_ENABLED=false`: login Google biasa tetap memakai scope
  identitas agar pengguna di luar daftar tester tidak terblokir oleh mode Testing.
  Tester menghubungkan YouTube melalui Library/Pengaturan.
- Endpoint kesiapan merespons HTTP 200 dengan `configured=true, enabled=false`.
  `configured` menunjukkan konfigurasi server; `enabled` khusus izin tambahan
  saat login Google umum. Nilai false kedua ini disengaja selama uji terbatas.
- 23 pengujian backend lulus; POST tanpa JWT atau dengan JWT tidak valid ditolak
  HTTP 401. Impor koleksi akun nyata masih memerlukan persetujuan Google dari
  aplikasi dan belum dibuktikan pada tahap aktivasi ini. Tidak ada build APK.
- Laporan: [pemeriksaan aktivasi](YOUTUBE_ACTIVATION_CHECK_2026-10-04.md).

## Riwayat status 2 Oktober 2026 (sebelum aktivasi)

- Kode aplikasi, Edge Function, migrasi, dan pemeriksaan lokal tersedia.
- Migrasi `202610020002_youtube_oauth_connections.sql` **belum diterapkan** ke produksi.
- Edge Function `youtube-library` dan secret integrasinya **belum diaktifkan**.
- Provider Google Supabase yang sudah aktif memakai client ID
  `1072882393306-066inpjhs2njrliu61rulg5oq28c7v9m.apps.googleusercontent.com`.
  Nomor proyek Google yang teridentifikasi adalah **1072882393306**.
- Akun `supportgratify@gmail.com` berhasil membuka proyek **Gratify Auth**
  (`gratify-auth`, nomor **1072882393306**) dan client login di atas.
- Pemeriksaan Google Cloud: status **Testing**, belum ada test user atau scope
  yang terdaftar; YouTube Data API v3 belum ada dalam 22 API aktif. Branding
  sudah memakai nama Gratify serta email support/developer tersebut, tetapi
  halaman utama, kebijakan privasi, dan syarat layanan belum diisi.
- Callback YouTube tambahan disiapkan dalam formulir client, **belum disimpan**.
  Aktivasi izin, tester, migrasi, function, dan secrets masih menunggu persetujuan
  untuk uji terbatas. Client login yang lama tetap dipakai.

## Perilaku aplikasi

- Selama backend belum tersedia atau belum dikonfigurasi, login Google meminta
  scope identitas biasa. Kegagalan pemeriksaan kesiapan tidak memblokir login.
- Setelah backend dikonfigurasi **dan** `YOUTUBE_LOGIN_CONSENT_ENABLED=true`,
  login Google meminta `youtube.readonly` sekaligus
  akses offline. Grant yang tersedia dari callback Supabase dikirim ke backend.
  Pengguna tidak perlu login YouTube lagi apabila grant lengkap berhasil diterima.
- Selama uji terbatas, flag itu tetap false. Koneksi manual tester tetap tersedia.
  Aktifkan flag untuk login umum hanya setelah status publik/verifikasi Google
  siap dan pengujian pengguna nyata selesai.
- Koneksi manual meneruskan `login_hint` dari identitas Google yang sudah
  diverifikasi, atau dari koneksi YouTube sebelumnya untuk akun email. Google
  dapat memilih sesi browser yang sesuai tanpa pemilihan akun ulang. Persetujuan
  izin tambahan tetap diperlukan; Google dapat meminta login bila sesi browser
  sudah berakhir. Akun email yang belum pernah menghubungkan Google memilih
  akun saat pertama kali memberikan izin. Callback tetap memeriksa identitas
  Google dan tidak mempercayai petunjuk akun dari aplikasi.
- Pengguna email dapat menghubungkan Google pada langkah awal setelah profil,
  atau dari Library/Pengaturan. Ini memberi izin Google tanpa mengganti akun
  Gratify atau membutuhkan fitur manual identity linking Supabase.
- Menolak/melewati izin tetap memungkinkan penggunaan Gratify. Akun lama yang
  belum memberikan scope tersebut perlu memberikan izin Google satu kali.
- Library menyediakan playlist milik pengguna serta video yang disukai. Detail
  koleksi memakai API resmi, pagination 50 item, retry pada cursor yang sama,
  serta antrean putar/shuffle untuk video yang telah dimuat.
  Endpoint rating YouTube membatasi koleksi disukai sampai 1.000 video terbaru;
  batas ini ditampilkan pada detail koleksi dan tidak berlaku sebagai batas
  buatan untuk playlist biasa. [Batas resmi](https://developers.google.com/youtube/v3/docs/videos/list#myRating)
- Pergantian akun dan pemutusan koneksi membersihkan metadata koleksi pribadi
  dari layar. Pemutusan YouTube tidak mengeluarkan pengguna dari Gratify.
- Riwayat dan rekomendasi pribadi YouTube Music tidak tersedia melalui integrasi
  ini. Home, Search, pemutaran umum, dan favorit Gratify tetap memakai layanan
  aplikasi yang sudah ada. Foto profil Google tidak dijadikan avatar Gratify.

## Aktivasi setelah akun pembuat ditemukan

1. Buka [proyek Google yang dipakai login](https://console.cloud.google.com/apis/credentials?project=1072882393306)
   dengan akun pemilik/pengelolanya. Cocokkan client ID di atas. Jangan membuat
   client pengganti sebelum memperhitungkan pengguna login Google yang sudah ada.
2. Aktifkan **YouTube Data API v3** di proyek itu. Konfigurasikan consent untuk
   `https://www.googleapis.com/auth/youtube.readonly`. Siapkan tester untuk uji
   terbatas; untuk penggunaan umum, selesaikan verifikasi scope yang diminta
   Google. [Panduan verifikasi](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification)
   dan [status aplikasi OAuth](https://developers.google.com/identity/protocols/oauth2/production-readiness/overview)
   menjelaskan batas pengguna dan umur refresh token dalam mode Testing.
3. Tambahkan authorized redirect URI pada **web OAuth client yang sama**:
   `https://bnabldxsqpvkyqpjcsdv.supabase.co/functions/v1/youtube-library`.
   Pertahankan callback login Supabase yang sudah terdaftar:
   `https://bnabldxsqpvkyqpjcsdv.supabase.co/auth/v1/callback`.
4. Tinjau lalu terapkan migrasi
   `supabase/migrations/202610020002_youtube_oauth_connections.sql`.
5. Deploy function:

   ```powershell
   supabase functions deploy youtube-library --no-verify-jwt --project-ref bnabldxsqpvkyqpjcsdv
   ```

   Gateway tidak memverifikasi JWT karena callback Google memakai GET. Function
   memverifikasi JWT Supabase dengan `auth.getUser()` untuk **setiap POST**.
   GET callback memakai state terenkripsi, PKCE, batas 5 menit, dan nonce sekali
   pakai. GET `?readiness=1` hanya mengembalikan flag konfigurasi server dan
   aktivasi consent tambahan saat login; tidak membocorkan secret/data pengguna.
6. Terakhir, isi secret function melalui Supabase, tanpa mengirim nilainya lewat
   chat atau menyimpannya dalam repository/APK:

   | Nama | Isi |
   | --- | --- |
   | `GOOGLE_OAUTH_CLIENT_ID` | Web client ID yang sama dengan login Supabase |
   | `GOOGLE_OAUTH_CLIENT_SECRET` | Secret milik client tersebut |
   | `YOUTUBE_TOKEN_ENCRYPTION_KEY` | 32 byte acak, dienkode Base64 |
   | `YOUTUBE_LOGIN_CONSENT_ENABLED` | `false` selama Testing; `true` setelah siap untuk login umum |

   Client secret yang sudah tersimpan dalam provider Google tidak otomatis
   tersedia pada Edge Function. Salin langsung melalui konfigurasi layanan.
   Jangan mengganti encryption key tanpa rencana migrasi token yang sudah ada.
7. Uji dengan akun tester: Google login baru; email + hubungkan Google; akun lama
   + beri izin ulang; playlist privat; pagination; video disukai; restart;
   pencabutan izin Google; putuskan koneksi; pindah akun; pembatalan browser.
   Verifikasi login identitas dan playlist tetap bekerja sebelum membuka akses
   untuk pengguna umum.

## Penyimpanan dan batas akses

Token akses/refresh tambahan disimpan AES-GCM di tabel backend, terikat ke user
ID. Role `anon` dan `authenticated` tidak mempunyai akses tabel atau RPC pengelola
token. Aplikasi hanya menerima metadata koleksi; backend memperbarui token.
Nonce callback dan penyimpanan grant diproses atomik sehingga callback yang
terlambat tidak membatalkan pemutusan koneksi. Grant provider dari SDK yang sudah
ditangani ditandai agar session lama tidak menyambungkan kembali YouTube.

Memutuskan koneksi menghapus grant yang disimpan Gratify. Pengguna juga dapat
mencabut izin aplikasi dari pengaturan akun Google; penolakan refresh berikutnya
akan meminta izin ulang. Library tidak membuat/mengubah playlist atau rating di
YouTube. Scope OAuth tidak memberi akses streaming audio pribadi.

Referensi: [OAuth YouTube](https://developers.google.com/youtube/v3/guides/authentication),
[playlist milik pengguna](https://developers.google.com/youtube/v3/docs/playlists/list),
[item playlist](https://developers.google.com/youtube/v3/docs/playlistItems/list),
[video yang disukai](https://developers.google.com/youtube/v3/docs/videos/list),
[provider token Supabase](https://supabase.com/docs/guides/auth/social-login#provider-tokens).

## Pemeriksaan yang dapat diulang

```powershell
node --test supabase/functions/youtube-library/service.test.mts
deno check supabase/functions/youtube-library/index.ts
python scripts/verify-youtube-oauth.py
.\gradlew.bat :composeApp:jvmTest :data:jvmTest :androidApp:assembleDebug :androidApp:lintDebug
```

Hasil dan APK uji ada di `artifacts/youtube-oauth/`. Uji lokal tidak membuktikan
consent Google atau integrasi produksi sudah aktif; uji grant sungguhan baru
dilakukan sesudah akses Google Cloud dan konfigurasi tersedia.
