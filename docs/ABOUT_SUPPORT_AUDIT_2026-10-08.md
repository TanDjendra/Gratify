# Tentang, bantuan, dan developer — 8 Oktober 2026

Identitas developer kini memiliki pintu masuk yang jelas, **Developer & kredit**. Baris Versi menyalin versi dan nomor build untuk laporan masalah, sehingga pengguna tidak perlu menemukan identitas developer melalui tombol versi.

## Temuan dan perubahan

- Halaman lama memuat logo 150 dp, nama developer berupa teks kecil yang ditautkan, tombol dengan tinggi minimum 1 dp, dan jarak kosong 200 dp di akhir. Tampilan baru memakai tema Gratify yang ada, header jelas, avatar inisial Tan, tombol seluruh baris, target minimum 48–72 dp, konten yang dapat digulir, dan lebar maksimal 760 dp.
- Bagian Tentang & Dukungan menyatukan identitas aplikasi, versi/build, pemeriksaan pembaruan, developer, email bantuan, serta dokumen privasi/ketentuan lokal. Perilaku pemeriksaan/status pembaruan yang sudah ada dipertahankan.
- Versi menyalin informasi publik aplikasi saja. Aksi bantuan membuka draf email ke alamat dukungan yang sudah ada (`supportgratify@gmail.com`), dengan versi/build pada isi draf. Aplikasi tidak mengirim email atau melampirkan log/data akun otomatis.
- Pembaca nama versi melakukan inisialisasi saat dipanggil. Sebelumnya, halaman yang dibuka tanpa menjalankan initializer MainActivity dapat menampilkan versi kosong; kondisi ini ditemukan pada fixture Android dan diberi regresi tersendiri.
- Website dan blog lama mengarah ke alamat yang sama; keduanya digabung menjadi **Situs developer**. Tombol Issue tracker lama mengarah ke profil GitHub, sehingga diganti jalur laporan melalui email. Profil GitHub dan kode sumber Gratify kini dibedakan.
- Identitas Tan, profil `TanDjendra`, Instagram `@calestaan`, dan tautan Saweria yang sudah ada dipertahankan. Tidak menambahkan biografi atau identitas pribadi baru. Donasi diberi keterangan opsional.
- Kredit SimpMusic/maxrave-dev dan kontributor ditampilkan sesuai README proyek. Kode sumber Gratify dan lisensi GPL-3.0 memiliki tautan tersendiri.
- Menu **Pustaka & lisensi** memakai AboutLibraries 14.0.1 yang sudah terpasang untuk membaca metadata yang disertakan dalam APK. Tidak menambah pustaka atau meminta data lisensi melalui jaringan saat memuat daftar. Keadaan memuat, gagal, dan coba lagi tersedia.
- Kegagalan membuka browser/email dan clipboard ditangani pada halaman baru, dengan pesan yang dapat dipulihkan dan alamat yang bisa dipilih/disalin. Istilah utama tersedia dalam bahasa Indonesia dan Inggris. Label dokumen legal juga mengikuti bahasa aplikasi.
- Developer dan bantuan dapat ditemukan melalui pencarian pengaturan. Route lama `CreditDestination` tetap digunakan, lalu route pustaka ditambahkan pada graph produksi dan fixture pemeriksaan Android khusus debug.

## Validasi

Pengujian akhir **32 tes lulus, tanpa kegagalan atau error**: AboutSupportTest 7, AuthRenderTest 4, NavigationRegressionTest 13, SettingsRenderTest 4, dan SettingsValidationTest 4. Cakupan meliputi aksi versi/developer/bantuan, pemulihan kegagalan membuka tautan atau clipboard, metadata pustaka nyata, navigasi, pembaruan, lebar 320/390/1100 dp, serta ukuran teks 150%. Tes nama versi tidak memerlukan initializer Activity.

Build APK debug Android dan kompilasi desktop berhasil, durasi 3 menit 44 detik. Log akhir: `artifacts/about-support-2026-10-08/validation-final.log`; rincian tes: `test-summary.json`. APK universal terbaru dibuat **8 Oktober 2026 pukul 19.38.56 WIB**, versi **2.1.0**, build **61**, di `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`. Rincian ukuran dan SHA-256 seluruh APK tersedia dalam `apk-verification.json`.

APK akhir dipasang dan diperiksa pada emulator Android dengan page size **16 KB (16384 byte)**. Alur Pengaturan → Tentang dan dukungan → Developer & kredit → Pustaka & lisensi berhasil. Nama versi tampil lengkap; ketukan Versi menampilkan konfirmasi penyalinan. Tombol kembali dari daftar pustaka menuju developer, lalu menuju Tentang dan dukungan berhasil. Daftar pustaka menampilkan nama, penulis, versi, dan lisensi dari metadata bawaan. Buffer crash kosong selama pemeriksaan. Bukti: `android-smoke-summary.json`, `android-about.png`, `android-version-copied.png`, `android-developer.png`, `android-developer-bottom.png`, dan `android-libraries.png`, beserta dump UI XML dalam folder artefak yang sama.

Pemeriksaan Android menggunakan fixture debug yang memakai komponen dan navigasi halaman produksi. Perangkat fisik serta alur pembaruan melalui jaringan tidak diuji pada audit ini.

Tautan situs developer dan profil GitHub berhasil dibaca tanpa perubahan akun. [Situs developer](https://tanweb.vercel.app/) dan [profil GitHub](https://github.com/TanDjendra). API komponen pustaka diperiksa pada sumber versi yang dipakai dan artefak lokal; lihat [AboutLibraries 14.0.1](https://github.com/mikepenz/AboutLibraries/tree/14.0.1). Kontak, pembayaran Saweria, dan pengiriman email tidak dijalankan dalam audit ini.
