# Login Google — pemeriksaan 2 Oktober 2026

## Temuan

- Halaman Google yang dilaporkan pengguna tetap menampilkan pemuatan pada pemilih akun. Tidak ada pesan penolakan provider yang terlihat. Pemeriksaan lanjutan menemukan callback desktop belum diizinkan oleh Supabase; pengguna mengonfirmasi berhasil masuk setelah alamat callback ditambahkan.
- Alur desktop memakai callback HTTP pada port localhost acak. Port dari percobaan yang dilaporkan sudah tidak mendengarkan ketika diperiksa; tab tersebut tidak dapat menyelesaikan percobaan lama.
- Supabase Auth 3.0.3 menutup server callback desktop setelah satu menit secara bawaan. `SignUpViewModel` sebelumnya hanya memasang status Loading, tanpa mengakhiri status tersebut ketika callback habis waktunya. Tombol Google tetap dapat memulai percobaan lain.

Referensi implementasi SDK: [konfigurasi desktop](https://github.com/supabase-community/supabase-kt/blob/3.0.3/Auth/src/desktopMain/kotlin/io/github/jan/supabase/auth/AuthConfig.kt), [server callback](https://github.com/supabase-community/supabase-kt/blob/3.0.3/Auth/src/desktopMain/kotlin/io/github/jan/supabase/auth/server/HttpCallbackServer.kt).

## Perbaikan

- Satu percobaan Google aktif pada satu waktu; klik berulang ditolak.
- Callback desktop dan penantian aplikasi diberi batas lima menit. Callback selesai tanpa sesi menghasilkan pesan untuk mencoba ulang.
- Android tetap menunggu sesi setelah browser dibuka, karena fungsi SDK di Android kembali sebelum login selesai.
- Status menunggu terlihat, pilihan login lain dinonaktifkan selama percobaan, dan tombol Batalkan menghentikan percobaan.
- Meninggalkan layar membatalkan penantian. Kesalahan provider tidak menampilkan URL, token, atau rincian rahasia.
- Halaman sukses callback desktop memakai nama Gratify tanpa aset eksternal.
- Pada percobaan berikutnya, pengguna mengonfirmasi tetap macet setelah Lanjutkan Google. Konfigurasi Supabase ternyata hanya memuat callback Android. Setelah persetujuan pemilik, `http://localhost:*` ditambahkan dan dikonfirmasi tersimpan pada proyek Gratify (total dua URL). Alamat localhost dibatasi pada komputer pengguna; callback desktop memakai port acak. Callback transport juga diperiksa dan merespons HTTP 200.

## Verifikasi dan batas

Enam pengujian regresi khusus Google lulus: klik ganda/pembatalan/retry, callback desktop tanpa sesi, penantian callback mobile, timeout, redaksi kesalahan, dan callback desktop berhasil. Total pengujian modul Compose: 27, kegagalan 0.

Build desktop dan Android full berhasil (15 menit 51 detik); APK ditandatangani dengan sertifikat asli. Hash paket Android baru: `959a1dc90b56ac6e9a0a26e805ae75c1ba79586b15dbe8912c882ab1dae109f4`. Bukti paket/config ada pada `artifacts/launch-audit/google-login-verification.json`. Pengguna memulai percobaan Google baru dan mengonfirmasi berhasil masuk Gratify setelah perubahan konfigurasi. Bukti login nyata berasal dari konfirmasi pengguna; pemeriksaan layar native tidak diotomatisasi. Pengujian controller mencatat jalur gagal/retry secara terpisah. Bukti UAT cloud Android sebelumnya tetap terikat ke APK dengan SHA-256 `5f04ebe76538c8537bc1e2db8b75d23377ecd33ea746813ea61c5b4d527e06c8`.
