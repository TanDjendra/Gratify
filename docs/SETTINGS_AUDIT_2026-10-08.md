# Audit Settings — 8 Oktober 2026

Settings disusun sebagai pusat pengaturan dengan pencarian yang membuka bagian terkait. Pengaturan lirik dan terjemahan dipisahkan dari pemutaran; proksi dipindahkan ke Koneksi; unduhan dan offline mendapat nama yang sesuai dengan fitur yang benar-benar tersedia.

Halaman utama berfokus pada kebutuhan penggunaan musik. Keluar dan hapus akun berada di bagian Akun, ditampilkan ketika akun Gratify siap, dan tetap memerlukan konfirmasi. Cadangan cloud tidak aktif ketika belum masuk; ekspor dan pemulihan lokal tetap tersedia.

## Temuan dan perubahan

- Getter preferensi sebelumnya membuat pengamatan baru setiap kali setter dipanggil atau Settings dibuka kembali. Pengamatan sekarang dibatasi satu untuk setiap preferensi, tetap mengikuti perubahan, dapat dicoba kembali setelah kegagalan, dan berhenti bersama ViewModel.
- Dialog penyedia AI sebelumnya menentukan pilihan aktif dari sumber lirik. Pilihan kini memakai penyedia AI yang benar.
- Kunci API pernah masuk ke log. Pencatatan nilai itu dihapus. Input kunci API dan kata sandi proksi disamarkan; kunci AI bisa dihapus dengan mengosongkan input, yang juga menonaktifkan terjemahan AI.
- Dialog pilihan, pilihan berganda, dan input menggunakan kontrol yang dapat dibatalkan, label semantik yang jelas, teks yang dapat membungkus, dan area bergulir. Sakelar dapat diubah dari seluruh baris; baris nonaktif tidak menulis perubahan.
- Konfigurasi proksi membatasi port ke 1–65535. Alamat API harus berupa HTTP/HTTPS. Header khusus harus berupa objek JSON dengan nilai teks tanpa baris baru.
- Dua opsi layanan pemutar yang bertentangan saling menyesuaikan saat diaktifkan: mempertahankan layanan dan menghentikannya saat aplikasi ditutup.
- Pembersihan unduhan sebelumnya menunggu flow yang tidak selesai. Pengambilan snapshot pertama memperbaiki kelanjutan operasi tersebut.
- Pencarian menyimpan query saat membuka bagian dan kembali. Bagian tujuan membuka posisi yang relevan; halaman kategori memakai posisi gulir sendiri. Konten desktop dibatasi 760 dp, tombol kembali mempunyai label dan target 48 dp, dan tinggi header diukur untuk teks besar.
- Uji Android bahasa Indonesia menemukan pencarian “crossfade” tidak cocok dengan label terjemahan “Memudar”. Indeks pencarian kini menerima nama umum crossfade, sponsor block, API key, backup/cadangan, dan restore/pemulihan selain label kategori dan fitur yang diterjemahkan.
- Diagram pemakaian seluruh perangkat dihapus dari Settings. Informasi penyimpanan berfokus pada cache, unduhan, dan cadangan Gratify; pembukaan Settings tidak lagi meminta perhitungan statistik perangkat yang tidak dibutuhkan.
- Deskripsi kategori, keadaan pembaruan, tindakan akun, dan konfigurasi lanjutan tersedia dalam bahasa Indonesia serta Inggris. Klaim fitur Spotify/Discord dan kualitas khusus Wi-Fi pada deskripsi kategori lama dihapus karena tidak sesuai dengan pilihan yang tersedia.

- Kegagalan menulis preferensi ditampilkan tanpa merusak aplikasi atau mengaku perubahan tersimpan. Pengguna dapat mengulangi perubahan. Ekspor lokal tetap tersedia; pemulihan lokal sekarang menjelaskan bahwa data saat ini bisa diganti sebelum pemilih berkas dibuka. Penghapusan unduhan menjelaskan dampaknya. Penghapusan berkas cache berjalan di luar jalur antarmuka.

## Validasi

- **47 pengujian terarah lulus**: 4 render Settings, 4 validasi konfigurasi/pencarian, 3 pengamatan preferensi, 3 audit ViewModel Settings, 16 pemulihan sesi akun, 13 navigasi, 3 penyimpanan pengaturan cloud, dan 1 pemulihan preferensi animasi lirik.
- Render dan interaksi mencakup 390 px, 320 px dengan tulisan 150%, serta desktop 1100 px. Pencarian crossfade dan penyedia AI menuju kategori/bagian produksi yang sesuai; hasil kosong dapat dibersihkan.
- Pengujian memastikan pengamatan tidak berlipat, kegagalan baca dapat dicoba lagi, pembatalan tidak dianggap gagal, penghapusan kunci menonaktifkan terjemahan AI, dua mode layanan saling menyesuaikan, dan kegagalan tulis tidak mengaku perubahan berhasil disimpan.
- Dialog input rahasia memiliki penanda password; input invalid menonaktifkan konfirmasi. Membatalkan perubahan pilihan tidak menerapkannya. Sakelar baris memakai satu aksi dan penanda nonaktif.
- Build Android debug dan kompilasi desktop berhasil.
- APK universal, arm64, armv7, dan x86_64 diperbarui pada 8 Oktober 2026 pukul **15.20 WIB**. [Hash dan verifikasi paket](../artifacts/settings-audit-2026-10-08/apk-verification.json).
- Uji Android virtual API 37 dengan halaman memori 16 KB memakai screen Settings produksi dalam bahasa Indonesia. Pencarian “crossfade” menampilkan “Memudar (BETA)”, membuka Pemutaran dengan opsi tersebut terlihat, dan tombol kembali mempertahankan query serta hasil. Dialog kualitas menampilkan pilihan aktif; pembatalan mengembalikan layar dengan kualitas semula. Buffer crash setelah pemeriksaan kosong.
- Emulator mengalami dialog “System UI isn't responding” saat cold boot. Pemeriksaan aplikasi dilakukan setelah dialog sistem ditutup; hasil ini belum menggantikan uji pada ponsel fisik.
- Bukti tampilan: [halaman utama](../artifacts/settings-audit-2026-10-08/android-home-id.png), [pencarian bahasa Indonesia](../artifacts/settings-audit-2026-10-08/android-search-id.png), [bagian Pemutaran](../artifacts/settings-audit-2026-10-08/android-crossfade-id.png), dan [dialog kualitas](../artifacts/settings-audit-2026-10-08/android-dialog-id.png).
- [Ringkasan pengujian](../artifacts/settings-audit-2026-10-08/test-summary.json) dan [log validasi](../artifacts/settings-audit-2026-10-08/validation-final.log).

Audit Android menggunakan screen produksi untuk pencarian dan navigasi. Operasi akun, penghapusan cache/unduhan, dan pemulihan data tidak dijalankan terhadap data akun nyata. Pengujian perubahan preferensi menggunakan data sintetis.
