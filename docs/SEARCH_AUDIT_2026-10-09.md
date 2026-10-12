# Audit Search — 9 Oktober 2026

Audit ini mencakup pencarian musik dan pengguna, saran, riwayat, filter, hasil lanjutan, serta tampilan pencarian. Perubahan audit sebelumnya tetap dipertahankan.

## Masalah dan perbaikan

- **Hasil lanjutan hilang:** ViewModel sebelumnya mengambil emisi pertama saja. Sekarang setiap kategori menerima halaman pertama segera, lalu memperbarui hasil secara bertahap sampai dua halaman lanjutan. Hasil dideduplikasi berdasarkan identitas, cursor berulang dihentikan, dan salinan hasil sebelumnya tidak ikut berubah.
- **Kegagalan jaringan tampak seperti hasil kosong:** kegagalan awal sekarang mempunyai tampilan gagal dan tombol coba lagi. Kegagalan halaman lanjutan mempertahankan hasil yang sudah tersedia, dengan pemberitahuan. Pencarian memiliki batas waktu dan pembatalan tetap diteruskan.
- **Hasil dari kata/filter lama:** perubahan kata, filter, dan akun membatalkan permintaan sebelumnya. Hanya permintaan terbaru boleh memperbarui layar. Hasil gabungan juga menghapus duplikasi playlist yang muncul di beberapa kategori.
- **Riwayat tidak langsung berubah:** riwayat sekarang mengamati database secara langsung, menampilkan yang terbaru lebih dahulu, memindahkan kata yang dicari ulang ke urutan pertama, dan menyimpan maksimal 30 entri. Ada tindakan mencari kembali, mengedit kata, menghapus satu entri, dan konfirmasi sebelum menghapus seluruh riwayat.
- **Riwayat antar akun:** penyimpanan dan penghapusan memeriksa pemilik database dalam transaksi. Pergantian akun mengosongkan hasil/saran/riwayat layar, sementara snapshot akun mempertahankan urutan riwayat ketika dipulihkan.
- **Saran terlambat/stale:** saran memakai jeda 300 ms, batas waktu 5 detik, pembatalan dan pemeriksaan permintaan terbaru. Saran kosong/duplikat dibuang, maksimal delapan saran ditampilkan, dan pencarian tetap dapat dijalankan saat layanan saran gagal.
- **Navigasi saran:** video memakai tindakan pemutaran video; podcast membuka halaman podcast.
- **Pencarian pengguna:** kata kosong atau hanya `@`, termasuk dengan spasi, tidak mengirim permintaan. Awalan `@` mengarah ke pengguna saat filter Semua/Pengguna dipilih. Filter musik yang dipilih secara eksplisit tetap dihormati. Pagination pengguna tetap 40 entri, dengan retry pada halaman yang gagal.
- **Ketahanan parser:** baris dari kategori yang tidak sesuai tidak lagi menggagalkan semua baris yang valid dalam halaman.
- **Playlist publik:** status memuat/gagal/kosong dibedakan dan kegagalan dapat dicoba kembali.

## Tampilan dan alur

Kolom pencarian sekarang memiliki label, tombol mencari yang terlihat, tombol mengosongkan kata, dan tindakan Search pada keyboard. Keyboard dan tombol menerapkan aturan input yang sama: kata kosong/`@` saja pada filter Semua/Pengguna tidak dijalankan atau disimpan ke riwayat. Tinggi kolom dapat mengikuti ukuran teks. Filter tetap bisa digeser. Tombol utama dan tindakan riwayat mempunyai area sentuh minimal 48 dp. Riwayat dan saran memakai satu daftar yang bisa digulir; tampilan memuat, gagal, belum mencari, dan tidak ada hasil dibedakan. Hasil kosong pada kategori musik menyediakan tindakan mencari di semua musik. Label baru tersedia dalam bahasa Inggris dan Indonesia, termasuk alias locale `in`.

## Batas verifikasi

Pengujian alur menggunakan repository terkontrol dan database Room/SQLite nyata. Preview Android menggunakan komponen produksi dengan kata pencarian contoh, tanpa menulis akun atau memanggil jaringan. Hasil layanan YouTube Music/Supabase pada akun pengguna serta pemutaran musik langsung belum diverifikasi dalam audit ini. Jumlah hasil adalah hasil yang berhasil dimuat; pencarian musik masih dibatasi maksimal tiga halaman per kategori, bukan gulir tanpa batas.

## Bukti

**52 tes lulus, 0 kegagalan, 0 error:** 10 pengujian model Search, 5 pengujian tampilan/interaksi, 4 pagination pengguna, 1 isolasi pemilih artis, 6 hasil progresif, 4 database riwayat, 2 parser, 7 snapshot akun, dan 13 regresi navigasi. Pengujian tampilan memakai lebar 320/390/1100 piksel, termasuk teks 150%, tindakan keyboard, hapus/edit riwayat, konfirmasi/batal, retry, dan kondisi kosong/gagal.

Build Android debug dan kompilasi desktop berhasil. APK universal: `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`, versi **2.1.0-dev (61)**, dibuat **9 Oktober 2026 pukul 00.39.44 WIB**, ukuran **104.588.712 byte**. SHA-256 dan metadata tersimpan di `apk.json`. Pengujian akhir juga memastikan tindakan Search pada keyboard tidak menjalankan input kosong atau `@` saja.

**16 pemeriksaan Android langsung berhasil**, termasuk edit/hapus satu entri, konfirmasi/batal/hapus seluruh riwayat, pencarian dari riwayat, tombol submit, perpindahan filter, mencari di semua musik, gagal/retry, filter Pengguna, serta teks 150% dan dialognya. Emulator memakai Android API 37 dengan halaman memori 16 KB; buffer crash kosong. Bukti ini berasal dari fixture debug dengan komponen produksi dan kata contoh. APK akhir setelah perbaikan keyboard juga berhasil dipasang ulang dan dibuka, dengan buffer crash kosong; identitas APK dicatat dalam `android-final-install.json`. Ukuran teks dikembalikan dan emulator audit dihentikan setelah pemeriksaan.

Log, hasil pengujian, screenshot, dan metadata APK disimpan di `artifacts/search-2026-10-09/`. `final-verification.log` mencatat build akhir; hasil XML dan `test-summary.json` menyimpan ringkasan pengujian. `android-smoke-summary.json` menyimpan hasil pemeriksaan Android.
