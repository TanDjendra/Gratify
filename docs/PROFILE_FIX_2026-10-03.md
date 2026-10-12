# Perbaikan profil dan alur melihat akun

Tanggal: 3 Oktober 2026. Perbaikan aplikasi berdasarkan audit profil telah diterapkan dan diverifikasi. **Aturan server untuk catatan Friends belum berubah**, karena pilihan penonton catatan belum dijawab. Tidak ada perubahan Supabase aktif dalam pekerjaan ini.

## Perubahan yang sudah diterapkan

| Temuan audit | Hasil perbaikan |
| --- | --- |
| F02 — loading, akun tidak ada, dan koneksi gagal bercampur | Identitas dibaca sekali. Loading berakhir setelah identitas selesai; akun yang tidak ada tetap tidak ditemukan. Error memiliki tombol coba lagi dan kembali. Data aktivitas dibersihkan ketika memuat ulang atau berpindah akun/profil. |
| F03 — QR/tautan tidak konsisten | Berbagi dan QR memakai HTTPS Gratify. Pembaca bersama memvalidasi domain, rute, dan UUID, serta menerima QR lama. Intent profil ditangani di aplikasi dan desktop. “Salin link” benar-benar menyalin ke clipboard. Tamu mendapat penjelasan saat ingin membagikan profil/QR. |
| F04 — playlist dianggap riwayat pemutaran | Lagu pertama playlist tidak lagi menjadi aktivitas palsu. Aktivitas hanya tampil bila data pemutaran valid, masih baru, dan diizinkan oleh pemilik. |
| F05 — hitungan dan daftar relasi menyesatkan | Hitungan menggunakan permintaan HEAD tanpa mengunduh daftar profil. Untuk bukan pemilik, angka dijelaskan sebagai relasi “terlihat”, sesuai aturan privasi yang sudah ada. Tiap tab punya loading, error, retry, dan halaman lanjutan sendiri. |
| F06 — arti artis dan penghapusan artis terakhir | Label menjadi “Artis yang diikuti”. Profil mengamati perubahan daftar; daftar kosong ikut disinkronkan. Sinkronisasi dibatasi pemilik Pustaka yang aktif. Pembaca sekali pakai di halaman lain dipertahankan. |
| F07 — playlist hanya bisa disimpan; error hilang | Ketukan playlist membuka detail. Tombol tambah menjadi aksi simpan terpisah. Sheet menampilkan proses/error, tetap terbuka saat gagal, dan mencegah ketukan ganda. |
| F08 — follow tamu tanpa arahan | Tamu diarahkan ke login Gratify di alur utama. Profil tujuan disimpan sementara untuk dibuka setelah login; dibersihkan jika kembali sebagai tamu atau melewati batas waktu. Tombol follow menampilkan proses dan kegagalan. |
| F09 — pencarian akun tidak membedakan error dan kosong | Error diteruskan ke UI. Pencarian akun memiliki halaman 40 hasil, retry tanpa kehilangan hasil lama, dan pembatalan halaman lama saat query berubah. Pencarian Friends yang sudah ada tidak dibatasi diam-diam ke 40 hasil. |
| F10 — permintaan dan penulisan tambahan | Identitas dan aktivitas memakai snapshot yang sama. Membaca profil sendiri tidak meng-upsert snapshot profil yang baru dibaca. Hitungan tidak memuat profil pengikut satu per satu. |

Header profil sendiri dan orang lain menggunakan pembagian lebar yang fleksibel, statistik terpisah per baris, dan tinggi mengikuti isi. Nama panjang tetap memakai elipsis dua baris. Switch privasi memakai nilai awal aman dan tidak dapat diubah sebelum pengaturan pemilik selesai dimuat; proses simpan mencegah ketukan ulang. Pratinjau profil diberi label pratinjau, menggantikan tombol Ikuti tanpa aksi.

## Verifikasi

- **64 pengujian lulus, 0 gagal**: 50 pengujian aplikasi dan 14 pengujian data. Meliputi error/akun tidak ada, loading, aktivitas privat/lama, pembatalan permintaan lama, cache pemilik, follow tamu/pending/gagal, paginasi dan retry, pemilik Pustaka, pembaruan daftar artis, kegagalan simpan, tautan/QR, tujuan setelah login, dan regresi yang sudah ada.
- HTTP lokal memverifikasi metode HEAD untuk hitungan, kegagalan profil/relasi/pencarian, query paginasi, pemilihan kolom identitas saja, dan kompatibilitas pencarian Friends. Token serta akun pengujian bersifat sintetis.
- Render header diperiksa pada lebar 320/360 dan ukuran huruf 150–200%, serta desktop. Tombol kembali dan statistik tidak bertabrakan atau melebar keluar layar. Layar error/retry juga diperiksa pada lebar 320 dengan huruf 150%.
- APK debug Android berhasil dibangun. Lint: **0 error, 0 fatal, 117 warning** yang masih menjadi utang pemeriksaan proyek.
- Tidak mengikuti akun nyata, mengirim pesan, mengunggah story, atau mengubah Supabase aktif. APK belum dipasang pada perangkat dalam pekerjaan ini.
- Pengujian tautan Android saat aplikasi tertutup/terbuka, alur login Google/email lengkap, dan melihat profil dengan dua akun nyata masih perlu pemeriksaan perangkat. Pengujian saat ini membuktikan logika, query, kompilasi, dan render; bukan seluruh interaksi perangkat nyata.

Bukti: [hasil verifikasi](/D:/Tan/Gratify/artifacts/profile-fix/validation.json), [log](/D:/Tan/Gratify/artifacts/profile-fix/verification.log), dan XML di `artifacts/profile-fix/test-results/`.

Contoh huruf 200%: [header 360](/D:/Tan/Gratify/artifacts/profile-fix/profile-header-360.png). Contoh error: [layar 320](/D:/Tan/Gratify/artifacts/profile-fix/profile-error-320.png).

APK: [debug universal](/D:/Tan/Gratify/androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk). SHA-256 dan ukuran tercatat dalam hasil verifikasi.

## Keputusan yang masih diperlukan

**F01 — privasi catatan Friends:** pilih hanya teman atau pengguna bisa memilih teman/publik; keduanya dengan masa berlaku 24 jam di server. Saat ini catatan masih tersedia lewat proyeksi profil publik sesuai hasil audit sebelumnya. Perbaikan UI aplikasi ini belum memperbaiki aturan akses catatan di server.

Username unik, tombol Pesan/Story di profil, serta blokir/laporkan merupakan perluasan fitur yang belum diterapkan karena memerlukan keputusan terlebih dahulu.
