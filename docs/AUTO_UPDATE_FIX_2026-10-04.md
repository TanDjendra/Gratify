# Perbaikan updater Gratify — 4 Oktober 2026

Persetujuan pengguna: perbaiki melalui GitHub resmi, tanpa build APK atau publikasi. Pengguna menyatakan keystore APK GitHub lama sudah tidak ada. Perubahan tidak mengganti package, tidak membuat keystore, tidak mengubah nomor versi, dan tidak menerbitkan release.

## Perilaku aplikasi

- Cek otomatis saat aplikasi dibuka atau kembali aktif dibatasi setiap 24 jam setelah sukses; kegagalan sementara dapat dicoba lagi setelah 15 menit. Cek manual tetap bisa dilakukan. Permintaan bersamaan tidak menumpuk, memiliki batas waktu, dan selalu melepas status Memeriksa setelah gagal/dibatalkan. Waktu pemeriksaan sukses terpisah dari percobaan.
- Unduhan memiliki progres, status menunggu koneksi, retry dan batal. Tugas unduhan dan identitas APK disimpan, bukan hanya dalam memori Activity. Membuka kembali aplikasi memulihkan status dari DownloadManager.
- Receiver unduhan dan WorkManager memeriksa hasil saat proses aplikasi tidak aktif. Notifikasi Siap dipasang membuka kembali status update. Jika izin notifikasi ditolak, status tetap dapat dibuka dari Pengaturan → Tentang & dukungan → Periksa pembaruan.
- Pemeriksaan berkas mencakup HTTPS repo resmi, SHA-256, ukuran, package, versionCode, versionName, channel Full/FOSS dan sertifikat yang sama. APK dengan package/signer/channel berbeda ditolak tanpa menghapus data atau melonggarkan verifikasi.
- Pemasangan dilakukan hanya dari tombol pengguna. Worker tidak membuka installer di latar belakang. Jika izin pemasangan belum diberikan, aplikasi memandu ke pengaturan Android dan menyediakan tombol Pasang setelah kembali.
- Semua dialog bisa ditunda. Menekan Nanti tidak membatalkan unduhan atau mengunci navigasi/musik offline. Pemutaran dan sesi akun tidak bergantung pada keberhasilan pembaruan.
- Pembatalan membersihkan hanya pekerjaan/berkas update yang terkait. Setelah versi yang lebih baru terpasang, pekerjaan, APK sementara dan notifikasi dibersihkan. Berkas siap-pasang yang dihapus sistem dari cache dapat disalin lagi dari unduhan yang sudah selesai.
- Pengaturan membuka pekerjaan yang sedang aktif agar pengguna tidak memulai unduhan ganda. Dialog yang sudah ditunda tidak dipaksa terbuka lagi hanya karena Activity berputar; cek manual atas rilis yang sama tetap bisa membuka dialog kembali.
- Desktop membuka halaman rilis resmi untuk tindakan pengguna, sehingga tidak lagi menawarkan tombol APK yang hanya memanggil implementasi Android kosong.

Android force-stop membatasi pekerjaan/broadcast sesuai perilaku sistem; saat aplikasi dibuka kembali, pemulihan tetap dicoba. Keberhasilan lengkap pada perangkat nyata masih memerlukan uji setelah build diizinkan.

## Publikasi berikutnya

Sumber: GitHub Releases `TanDjendra/Gratify`. APK umum menggunakan jalur stabil Full universal. Pemilihan aset tidak bergantung pada urutan daftar. Jalur debug `.dev` tetap terpisah. FOSS tidak diarahkan diam-diam ke APK Full; updater menolak varian yang berbeda. Endpoint F-Droid Gratify masih menghasilkan 404 pada audit.

Rilis baru sebaiknya memiliki dua aset pada tag yang sama:

1. `Gratify.apk`: release Full universal, ditandatangani keystore release yang telah disepakati.
2. `gratify-update.json`: versionCode, versionName, packageName, channel, apkName, SHA-256 dan ukuran. Aplikasi membandingkan metadata dengan aset GitHub dan APK yang benar-benar diunduh.

Rilis lama tanpa JSON masih bisa dibaca jika aset APK memiliki digest GitHub; APK tetap harus lulus semua pemeriksaan saat pemasangan. Rilis baru yang menggunakan updater ini harus menyertakan marker channel pada manifest APK. Minimum versi dari field GitHub nonstandar tidak lagi mengunci seluruh aplikasi.

Script `scripts/prepare-update-release.py` menyiapkan dua berkas dari APK yang sudah dibuat dan ditandatangani. Script tidak build, tidak menandatangani dan tidak upload. Script menolak kode versi yang tidak naik, signer salah, package debug, prerelease, APK satu ABI, marker FOSS yang diganti nama menjadi Full, dan direktori keluaran yang sudah berisi hasil rilis. Hash dihitung dengan membaca bertahap.

Contoh untuk pengelola setelah build berikutnya diizinkan:

```powershell
$env:JAVA_HOME = 'D:/Tan/APK/JDK'
python scripts/prepare-update-release.py `
  --apk 'D:/Tan/Gratify/androidApp/build/outputs/apk/release/Gratify-full-universal-release.apk' `
  --aapt2 'C:/Users/LENOVO/AppData/Local/Android/Sdk/build-tools/37.0.0/aapt2.exe' `
  --apksigner 'C:/Users/LENOVO/AppData/Local/Android/Sdk/build-tools/37.0.0/apksigner.bat' `
  --expected-certificate '<SHA-256 sertifikat untuk jalur rilis yang dipilih>' `
  --previous-version-code 61 `
  --output 'D:/Tan/Gratify/artifacts/release-next'
```

Nomor versi masih `2.1.0 / 61`. Build publik berikutnya perlu kode lebih tinggi dari seluruh build yang pernah dibagikan, serta nama/tag baru. Contoh `2.1.1 / 62` hanya berlaku bila belum pernah ada code lebih besar. Script penandatangan sekarang meneruskan pilihan `--full/--foss` ke konfigurasi Gradle sehingga nama berkas tidak sekadar berubah sementara isi masih varian lain.

CI saat ini menyimpan hasil sebagai Actions artifacts. Ini tidak otomatis membuat GitHub Release. Siapkan draft, periksa signer dan migrasi, unggah aset, lalu publikasikan setelah persetujuan. Tautan APK pada situs juga masih menunjuk `v2.1.0`; perubahan situs/publikasi menunggu rilis yang benar-benar tersedia.

## Keystore lama hilang

APK GitHub lama memakai sertifikat `abaa0340…ecd942c2`; APK release lokal memakai `efefe1b8…a51c316a`. Sertifikat publik tidak dapat dipakai untuk menghasilkan kembali private key. Updater yang lebih baik tidak dapat menggantikan syarat tanda tangan Android. Perubahan ini tidak dapat dikirim sebagai pembaruan biasa ke APK lama menggunakan kunci lokal berbeda.

Solusi untuk kelompok pengguna APK lama adalah migrasi terencana ke jalur rilis baru. Sebelum berpindah, cadangan harus diekspor dari aplikasi lama ke lokasi di luar penyimpanan privat aplikasinya. Pilihan backup unduhan perlu diaktifkan bila tersedia. Kode backup saat ini dapat menyertakan database pustaka, preferensi, database ExoPlayer dan folder unduhan ketika `backupDownloaded` aktif; dukungan dan keberhasilan restore dari APK GitHub Juli belum diuji langsung.

Jangan menjanjikan pemulihan seluruh lagu unduhan hanya lewat login/sinkronisasi cloud. Jangan meminta uninstall sebelum ZIP cadangan berhasil dibuat, berada di lokasi yang tetap tersedia sesudah uninstall, dan prosedur restore diuji. Pengguna debug `.dev` dan release juga memakai penyimpanan berbeda. Saat ini tidak ada perubahan package atau migrasi otomatis yang dilakukan.

Untuk pembaruan normal di jalur baru, keystore release yang dipilih harus disimpan dan dicadangkan dengan aman, lalu dipakai konsisten. Pada tahap rilis publik berikutnya, pemilik perlu menetapkan jalur migrasi pengguna lama sebelum menyebarkan APK.

## Pengujian

Hasil final dan jumlah test terdapat di `artifacts/auto-update-audit-2026-10-03/implementation-verification.json`.

- Test logika: pemilihan APK/digest, metadata yang tidak konsisten, versi/sertifikat/package/channel, kegagalan jaringan, timeout, pemeriksaan bersamaan, cooldown, pembatalan dan cek manual berulang.
- Test UI merender komponen dialog yang dipakai aplikasi dan memeriksa bahwa Nanti tidak menjalankan batal/pasang serta bahwa signer tidak kompatibel tidak menawarkan bypass pemasangan.
- Test alat persiapan rilis memakai keluaran alat sintetis; pemeriksaan tambahan dengan APK release lokal nyata membuktikan build code 61 ditolak ketika baseline adalah 61.
- Kotlin Android dan manifest diperiksa tanpa menjalankan assemble/bundle/packaging APK. APK debug yang sudah ada tetap berukuran dan berhash sama.
- Belum diuji pada HP: install-over-update, mati proses/force-stop saat unduh/verifikasi, kembali dari izin pemasangan, ruang penuh, dan restore cadangan APK GitHub lama. Tidak ada klaim penerimaan update end-to-end sebelum pengujian perangkat tersebut.

Untuk distribusi Play kelak, updater APK dan REQUEST_INSTALL_PACKAGES perlu dipisahkan dari build Play dan diganti mekanisme pembaruan Play. [Kebijakan resmi Google Play](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en). Distribusi APK lewat situs/server tetap merupakan jalur Android yang didukung. [Dokumentasi distribusi alternatif](https://developer.android.com/distribute/marketing-tools/alternative-distribution).
