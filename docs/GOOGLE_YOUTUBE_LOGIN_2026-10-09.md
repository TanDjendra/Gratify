# Login Google dengan pilihan koneksi YouTube — 9 Oktober 2026

Halaman login sekarang menyediakan pilihan `Sekalian hubungkan YouTube`, tidak dicentang secara otomatis. Bila dipilih, tombol menjadi `Masuk & hubungkan YouTube`. Satu permintaan login Google meminta izin YouTube baca saja, offline access dan consent Google. Bila tidak dipilih, login tidak menambahkan scope YouTube atau memaksa consent/offline access.

Pilihan ini berasal dari tindakan pengguna per percobaan login, terpisah dari flag server untuk permintaan otomatis ke semua pengguna. Tidak ada perubahan konfigurasi Google Cloud, secret, callback atau deploy server. Scope YouTube masih belum terverifikasi oleh Google; peringatan OAuth belum hilang.

Setelah login Google berhasil, Gratify memuat koneksi koleksi lewat repository resmi lalu masuk ke beranda tanpa halaman koneksi tambahan. Akun baru tetap diminta menyimpan profil, dengan tujuan navigasi yang mempertahankan keputusan untuk melewati koneksi tambahan. Login email/OTP mempertahankan alur sebelumnya. Koneksi dan pemutusan akses tetap tersedia dari Pustaka/pengaturan.

Pemuatan koneksi YouTube dibatasi sepuluh detik. Izin yang ditolak atau backend koleksi gagal tidak membatalkan sesi Gratify yang sudah berhasil. Status/retry koneksi tersedia di Pustaka. Tombol login dan pilihan YouTube dinonaktifkan selama login atau penyelesaian profil akun berlangsung. Klik berulang tidak mengganti izin pada percobaan yang sedang aktif.

## Validasi

29 pengujian JVM lulus tanpa kegagalan: 7 audit ViewModel autentikasi, 6 controller login Google, 3 URL OAuth/navigasi, 5 render autentikasi, 4 aturan autentikasi, dan 4 state koleksi YouTube. Pengujian mencakup URL OAuth yang dihasilkan SDK Supabase, pilihan scope dasar/gabungan, penyimpanan parameter navigasi profil, tombol pilihan aksesibel pada lebar 390 dan 320 dengan teks besar, serta regresi controller login dan autentikasi. XML hasil dan dua screenshot tersimpan di `artifacts/google-verification-2026-10-09/`.

Login Google dan koleksi akun nyata belum direkam dalam sesi implementasi ini. Pengujian lokal tidak menggantikan video consent dan koleksi akun uji yang diperlukan untuk pengajuan Google.

Build Android full/debug berhasil. APK `artifacts/google-verification-2026-10-09/Gratify-debug-google-youtube-20261009.apk` memiliki SHA-256 `20E6B808DDFB228B6F5C86DDB46518EEF6192AB8273B4E29D92C3359EDEFB03E`; tanda tangan APK v2 terverifikasi. APK ini melanjutkan perbaikan TLS dan playback sebelumnya. Tidak ada klaim bahwa login/koleksi Google akun nyata telah diuji pada HP dalam sesi ini.

Rujukan untuk consent yang dipilih pengguna dan penanganan izin granular: https://developers.google.com/identity/protocols/oauth2/resources/granular-permissions
