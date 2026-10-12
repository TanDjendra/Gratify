# Penyesuaian kapsul kategori Search

Tombol kategori memakai bentuk kapsul penuh, warna hijau untuk pilihan aktif, dan latar abu-abu tanpa garis tepi untuk kategori lainnya. Tipografi, warna, dan bentuk mengikuti tampilan Gratify yang sudah digunakan di beranda. Area tekan minimal 48 dp dan jarak antarkategori 8 dp.

Baris filter mempertahankan batas 16 dp di kedua sisi saat digeser. Label tetap satu baris dan menyesuaikan ruang layar; label yang terlalu panjang memakai elipsis. Nama kategori lengkap tetap tersedia untuk pembaca layar.

Kategori aktif dibawa masuk ke area yang terlihat ketika pilihan berubah, termasuk ketika mode Pengguna dibuka dari alur lain. Pengguna tetap dapat menggeser baris secara manual. Seluruh sembilan kategori dan perilaku pencarian tetap menggunakan alur yang sudah ada.

Enam tes tampilan otomatis dan 26 pemeriksaan Android lulus, tanpa kegagalan atau crash. Pemeriksaan mencakup pilihan aktif, perpindahan ke Lagu, keterlihatan kategori Pengguna/Featured, area tekan 48 dp, dan ukuran teks 1.0/1.5 di Android. Tes tampilan juga memeriksa layar 320 dp pada ukuran teks 1.5/2.0 dan layar lebar 1100 dp. Bukti tersimpan di `artifacts/search-capsules-2026-10-09`; ukuran teks emulator dikembalikan ke bawaan dan emulator dihentikan setelah verifikasi.

APK debug dibuat 9 Oktober 2026 pukul 02:00:24 WIB, versi `2.1.0-dev` (61), paket `com.tan.gratify.dev`. Berkas `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`, ukuran 104.588.712 byte, SHA-256 `A64185371004F65221DC02826D65F1BF2A7E9119B479CC596C1E062FC596CEED`. APK ini juga memuat perbaikan lifecycle dan pemulihan playback dari audit sebelumnya.

Pengujian native pertama setelah pemasangan APK menangkap launcher, sehingga pemeriksaan label belum dapat berjalan. Peluncuran ulang fixture berhasil; seluruh 26 pemeriksaan dijalankan ulang sampai selesai.
