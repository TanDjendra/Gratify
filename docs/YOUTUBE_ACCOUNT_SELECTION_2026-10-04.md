# Pemilihan akun saat memberi izin YouTube

Gratify sudah mengetahui identitas Google melalui sesi Supabase. Sebelumnya,
identitas itu hanya dipakai untuk memverifikasi grant dan callback, sementara
URL permintaan izin YouTube tidak memuat `login_hint`. Akibatnya Google dapat
menampilkan pemilihan akun lagi, meskipun pengguna sudah login Gratify.

Perbaikan server menambahkan `login_hint` menggunakan Google `sub` dari user
yang diverifikasi Supabase. Untuk pengguna email dengan koneksi YouTube lama,
petunjuk menggunakan Google ID koneksi sebelumnya. Pengguna email yang belum
memiliki koneksi Google tetap memilih akun pada koneksi pertama. Setelah
disconnect, petunjuk koneksi lama tidak digunakan kembali.

Petunjuk hanya membantu Google memilih sesi browser. Ia tidak menggantikan
persetujuan `youtube.readonly` atau pemeriksaan identitas callback. Body aplikasi
tidak boleh menentukan hint, email, user ID, maupun identitas akun Google.
Pemilihan akun lain tetap ditolak untuk pengguna Gratify dengan identitas Google.

26 pengujian backend lulus, termasuk petunjuk dari identitas terverifikasi,
penolakan petunjuk palsu, koneksi ulang akun email, dan callback akun yang salah.
Log: `artifacts/youtube-activation-audit-2026-10-04/account-hint-tests.log`.
Perubahan diterapkan ke function `youtube-library` aktif pada 4 Oktober 2026.
Sumber editor setelah deploy cocok dengan sumber lokal. Pemeriksaan endpoint
kesiapan lulus HTTP 200 dan authorize tanpa login tetap ditolak HTTP 401.
Perubahan hanya di server; tidak memerlukan APK baru. Bukti deploy berada pada
`artifacts/youtube-activation-audit-2026-10-04/account-hint-deployed-proof.jpg`.

Pengujian browser pengguna nyata masih diperlukan: hint tidak menjamin Google
melewati pemilihan akun bila sesi browser tidak tersedia. Status Google Testing
dan pembatasan tester tetap berlaku, sehingga perubahan ini tidak menyelesaikan
error 403 akun yang belum masuk daftar tester.

Referensi: [Google OAuth login_hint](https://developers.google.com/identity/protocols/oauth2/web-server#creatingclient).
