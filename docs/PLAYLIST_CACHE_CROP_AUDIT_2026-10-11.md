# Audit playlist, cache, privasi, dan crop foto Gratify

Revisi sumber lanjutan 12 Oktober (crop tanpa slider, pemilihan foto profil, dan daftar Search publik) tercatat di [revisi foto dan privasi](PHOTO_PRIVACY_REVISION_2026-10-12.md). Revisi lanjutan belum dimasukkan ke APK karena pengguna meminta menunda pembuatan APK. Hasil APK di laporan ini merujuk versi yang sudah terpasang pada 11 Oktober.

Permintaan: cache foto Search dan playlist yang pernah dimuat, revisi alur playlist dengan referensi Spotify, playlist privat boleh dipajang hanya untuk pemilik, dan crop sebelum upload foto sampul/profil. Identitas aplikasi tetap `com.tan.gratify.next`, label Gratify. Akun/data aplikasi dan server tidak direset dalam pekerjaan ini.

## Temuan dan perubahan

| Temuan | Perilaku setelah perbaikan |
| --- | --- |
| URL foto kategori Search hanya berada di RAM sehingga harus dicari ulang ketika halaman/proses dibuka kembali. | URL sampul kategori disimpan di cache disk dengan umur tujuh hari. Metadata kategori bertahan sehari. Permintaan kategori tetap dideduplikasi dan dibatasi dua bersamaan. |
| Metadata playlist YouTube dimuat ulang walau baru dibuka. | Halaman awal playlist tersimpan sepuluh menit, dibedakan menurut akun, cookie YouTube yang dihash, bahasa, wilayah, ID playlist, dan format tampilan. Cache kedaluwarsa mencoba pembaruan; kegagalan jaringan boleh memakai salinan maksimal tujuh hari. |
| Gambar sampul upload mendapat URL tanda tangan baru; token baru membuat gambar yang sama memiliki identitas cache berbeda. | Identitas cache mengacu pada objek gambar yang tetap dan identitas akun pembaca. Foto baru memakai objek baru, jadi perubahan sampul tidak tertahan oleh cache foto lama. Izin akses server tetap diperiksa saat mendapatkan URL. |
| Foto kosong/gagal menampilkan animasi loading terus-menerus. | Placeholder gambar tetap tampil ketika foto tidak tersedia. |
| Centang di layar tambah lagu justru bisa menghapus lagu. | Tombol plus menambahkan satu kali. Centang yang dinonaktifkan menandai lagu sudah ada. Penghapusan memiliki tombol minus tersendiri dalam mode Edit lagu. |
| Pengurutan hanya muncul setelah memilih urutan khusus. | Edit lagu dapat dibuka dari semua jenis urutan. Mode edit memakai urutan khusus, menampilkan tombol hapus dan pegangan geser, serta penjelasan bahwa perubahan tersimpan otomatis. Sort dinonaktifkan selama edit untuk menjaga posisi konsisten. |
| Kunci baris lagu berubah setiap urutan berubah. | Identitas baris mengikuti ID lagu sehingga gambar/baris tidak dibangun ulang semata karena posisinya berganti. |
| Tombol Selesai tidak ada jika belum menambahkan lagu. | Selesai tersedia saat playlist siap; tombol kembali/Selesai ditahan saat penambahan masih berlangsung. |
| Foto sampul langsung diunggah, tetapi tombol Simpan hanya mengubah nama dan nonaktif untuk perubahan foto saja. | Foto yang dipotong menjadi draf dengan pratinjau. Simpan berlaku untuk nama atau sampul. Upload disiapkan dulu; nama dan sampul kemudian disimpan dalam satu transaksi lokal dengan pemeriksaan pemilik dan antrean sinkronisasi. Batal tidak mengubah playlist. |
| Memajang playlist otomatis menjadikannya publik; server menolak/melepas pajangan privat. | Pinning mempertahankan status publik/privat. Pemilik dapat melihat playlist privat yang dipajang; pengunjung dan pratinjau profil publik tidak menampilkannya. Label privat menjelaskan “Hanya Anda”. |
| Profil yang memakai respons server mengabaikan playlist privat tanpa ID playlist publik. | Playlist privat tetap dipertahankan untuk pemilik. Navigasi memakai playlist lokal berdasarkan identitas sinkronisasi bila tersedia. |
| Picker meminta izin menulis foto padahal hanya membutuhkan baca. | Picker dokumen memakai izin baca sementara, memproses foto saat izin masih berlaku, lalu menyimpan hasil crop di cache aplikasi. |
| Foto panjang langsung diperkecil sehingga bagian yang diinginkan tidak bisa dipilih. | Foto melewati layar crop: geser, pinch/slider zoom, atur ulang, batal, dan gunakan foto. Sampul memakai pratinjau persegi; avatar memakai pratinjau lingkaran dengan hasil persegi untuk komponen avatar yang memotong lingkaran. |

Cache metadata dibatasi 64 entri/8 MiB, satu entri maksimal 512 KiB. Cache bitmap Coil tetap dibatasi 64 MiB RAM dan 512 MiB disk di cache aplikasi. Draf crop dibatasi 64 file/16 MiB dan membersihkan file berumur lebih dari sehari. Sistem dapat menghapus cache untuk menyediakan ruang; cache tidak menjamin semua konten selalu tersedia tanpa jaringan. Playlist Supabase milik orang lain tetap dibaca melalui izin server, tidak diganti cache metadata YouTube.

## Alur yang digunakan

1. Buat playlist → isi nama → Buat → halaman playlist. Playlist baru tetap privat.
2. Tambah lagu → cari lagu atau pilih rekomendasi, riwayat, dan lagu disukai → plus → centang setelah penambahan berhasil → Selesai. Gagal menambah dapat dicoba lagi.
3. Edit detail → nama dan/atau pilih foto → crop → lihat pratinjau → Simpan. Kegagalan upload mempertahankan data lama dan draf untuk dicoba lagi.
4. Edit lagu → tombol minus untuk hapus atau tahan/geser untuk urutan → Selesai. Perubahan lagu tersimpan langsung, dengan pemeriksaan akun pemilik.
5. Privasi playlist dan pajangan profil merupakan pengaturan terpisah. Memajang atau melepas tidak menerbitkan playlist. Playlist publik tetap memerlukan tindakan publikasi yang jelas.

Referensi konsep: [Spotify — membuat dan mengedit playlist](https://support.spotify.com/ie/article/create-playlists/), [mengganti sampul](https://support.spotify.com/sc-en/article/add-playlist-cover/), [privasi dan akses playlist](https://support.spotify.com/by-en/article/playlist-privacy-and-access/). Branding, sumber lagu, dan pengaturan privat Gratify tetap mengikuti aplikasi ini.

## Verifikasi

- Migrasi `20261011152525_private_profile_playlist_pins.sql` diterapkan pada Supabase proyek Gratify. Fungsi baca profil tetap SECURITY INVOKER; role anonim tidak diberi izin mutasi pin.
- PostgreSQL terisolasi: 10 pemeriksaan untuk skema TEXT dan 10 untuk UUID. Privat terlihat untuk pemilik, tetap tersembunyi untuk pengunjung, track privat tidak terbuka, akun lain tidak dapat mengganti pin, dan perubahan publik menjadi privat mempertahankan pin pemilik.
- Uji transaksi langsung di server: pin privat baru tidak menerbitkan playlist, pemilik melihatnya, pengunjung tidak melihat atau dapat mengubahnya, unpin menjaga privasi. Seluruh fixture di-rollback.
- Codec crop Android diuji di ponsel dengan gambar sintetis 2000×6000: decode dibatasi sisi terpanjang 2048, posisi crop benar, output JPEG 512×512, delapan orientasi EXIF, latar putih untuk alpha, serta penolakan foto tidak tersedia. Jalur ImageDecoder dan jalur decoder Android lama sama-sama diuji pada perangkat Android 16 yang tersedia.
- Uji cache disk: instans baru tidak mengambil ulang data segar, cache kedaluwarsa diperbarui walau konsumen hanya mengambil hasil pertama, fallback offline memiliki batas umur, pergantian akun membatalkan respons, berkas rusak tidak merusak pemuatan, dan kapasitas dibatasi.
- Transaksi detail playlist: nama/sampul disimpan bersama; kegagalan update sampul membatalkan nama dan antrean perubahan; akun lain ditolak.
- Seluruh pengujian JVM: 610 kasus, **603 lulus, 7 dilewati, tanpa kegagalan/error**. Setelah revisi teks akhir, sembilan pengujian render alur/manajemen playlist dijalankan kembali dan lulus. Salinan laporan tersimpan di `artifacts/playlist-cache-crop-20261011/full-jvm-reports` dan `final-ui-test-reports`.
- Build rilis dan lint selesai berhasil. Lint mencatat **0 error dan 65 warning**, termasuk versi dependensi/toolchain, target API, resource dan ikon. Dua warning trust manager berasal dari dependensi PipePipe; jalur inisialisasi aman aplikasi dan pengujian TLS sebelumnya tetap dipertahankan. Hasil ini tidak berarti seluruh warning telah dihapus.
- Uji nyata pada ponsel: nama → buat playlist privat → tambah dua lagu → centang nonaktif untuk lagu yang sudah ada → Selesai. Pengguna mengonfirmasi drag berhasil; urutan baru tetap sama setelah playlist dibuka kembali. Tombol minus berhasil menghapus satu lagu dari playlist uji.
- Crop sampul gambar sintetis panjang diuji dengan geser posisi. Batal mempertahankan sampul sebelumnya; perubahan sampul saja mengaktifkan Simpan dan berhasil tersimpan setelah konfirmasi. Crop avatar menampilkan mask lingkaran, zoom, reset dan pratinjau draf. Foto profil pengguna tidak disimpan ulang dalam pengujian ini; keberhasilan upload avatar akhir tidak diklaim dari uji draf.
- Pin playlist privat diuji pada profil pemilik dan pratinjau publik: pemilik melihat label “Dipajang • Privat • Hanya Anda”; pratinjau publik tidak menampilkan playlist tersebut. Nilai server tetap privat dan dipajang.
- Enam siklus buka/batal crop dalam PID yang sama mencatat total PSS termasuk swap sekitar **157–182 MiB saat crop aktif**, kemudian **139–159 MiB setelah ditutup**. Tidak terlihat kenaikan terus-menerus pada rangkaian pendek ini; pengukuran ini tidak membuktikan bebas seluruh kebocoran RAM.
- Pada satu dari enam siklus otomatis, pembatalan crop juga menutup editor profil sehingga editor perlu dibuka kembali. Penyebab belum terisolasi antara interaksi perangkat/lifecycle dan perilaku dialog. Data profil tidak tersimpan atau berubah. Konsistensi pembatalan editor masih perlu diperiksa kembali dengan reproduksi yang terkontrol.
- Playlist sementara `GRATIFY_AUDIT_20261011` sudah dihapus lewat aplikasi. Pemeriksaan server setelahnya memastikan tidak ada playlist uji dalam tabel cloud/shared, dan dua playlist pengguna (`playlist 1`, `playlist 2`) tetap ada. Sampul sintetis uji yang tertinggal juga dihapus melalui Storage API setelah identitas gambar diverifikasi; tidak ada sampul pengguna yang dihapus. Bukti: `cleanup-verification.json` dan `fixture-cover-cleanup.json`.

## APK hasil pekerjaan

Gratify **2.1.2 (kode 63)** memakai ID `com.tan.gratify.next` dan kunci baru yang sudah digunakan aplikasi ini. APK arm64 dan universal berhasil diverifikasi; 45 pustaka native arm64 memenuhi alignment 16 KB. APK arm64 final sudah terpasang di ponsel dan hash file terpasang cocok dengan hasil build bertanda tangan. UID aplikasi tetap sama, login dan playlist pengguna dipertahankan. Pemeriksaan log yang direkam tidak menemukan fatal exception aplikasi selama pengujian; cakupannya terbatas pada sesi tersebut.

- [APK universal](../artifacts/playlist-cache-crop-20261011/Gratify.apk), SHA-256 `d7fa256245eb3ea8f5882a4fe49395fbf299538bce5488e9ad279a90a8191ef2`.
- [APK arm64](../artifacts/playlist-cache-crop-20261011/Gratify-full-2.1.2-arm64-v8a-NEW-KEY.apk), SHA-256 `ad4edb21e337b8ff89f503ad5fa40984a43abc597310bddc44f798dc2a88d29c`.
- Bukti: `release-apks.json`, `release-validation.json`, `installed-verification.json`, `full-jvm-verification.json`, `lint-verification.json`, dan `crop-cycle-memory.json` dalam folder artefak pekerjaan ini.

## Temuan tersisa dan pembersihan akhir

Penghapusan playlist belum otomatis membersihkan objek sampul dari Storage. Hal ini ditemukan dari sampul playlist uji yang masih ada setelah playlist dihapus. Sampul uji sudah dibersihkan secara terpisah, tetapi mekanisme pembersihan otomatis belum diperbaiki dalam APK ini. File sisa memakai kuota Storage; temuan ini bukan kebocoran RAM. Pembersihan selanjutnya harus memakai Storage API dan membedakan gambar yang masih dirujuk playlist cloud/shared atau perubahan lokal yang belum tersinkron; menghapus baris `storage.objects` langsung atau menghapus semua file yang tampak tidak dirujuk berisiko menghilangkan foto yang masih diperlukan.

Pemeriksaan akhir dilakukan pada 12 Oktober 2026. Setelah pengguna mengizinkan kembali USB debugging, gambar sintetis `Download/GRATIFY-AUDIT-CROP-20261011.png` dihapus dari ponsel. Pemeriksaan memastikan file dan entri MediaStore sudah tidak ada; berkas Download lainnya tidak disentuh. Bukti: `device-fixture-cleanup.json`. Pengujian aplikasi dan verifikasi pemasangan final sebelumnya sudah selesai dan buktinya tersimpan.

## Batas pengujian

Crop/picker baru difokuskan pada Android. Picker desktop sebelumnya memang berupa stub dan belum dikembangkan menjadi fitur crop desktop. Perangkat fisik yang tersedia memiliki RAM 12 GB; jalur decoder lama bukan pengganti pengujian pada ponsel Android lama atau RAM 2–4 GB. Pekerjaan ini tidak membuktikan semua hambatan rilis publik terdahulu telah selesai, termasuk verifikasi OAuth publik dan jank pemutar yang sebelumnya dilaporkan.

Advisor Supabase juga masih melaporkan konfigurasi yang sudah ada sebelum perubahan ini: view `public_profiles` memakai proyeksi kolom dengan masking privasi dan security barrier, beberapa fungsi/triggers, serta pengaturan keamanan Auth. Hasil advisor tidak dinyatakan bersih hanya karena uji pin privat berhasil.
