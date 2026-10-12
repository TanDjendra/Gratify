# Revisi crop, pemilihan foto profil, dan visibilitas playlist

Permintaan pengguna: hapus bar zoom dan pakai cubit dua jari; perbaiki pilihan foto untuk mengganti avatar; playlist yang diubah dari publik menjadi privat harus hilang dari Search. **APK baru tidak dibuat atau dipasang dalam pekerjaan ini**, sesuai permintaan pengguna yang masih akan menambahkan revisi. APK sebelumnya tidak memuat perubahan ini.

## Crop dan foto profil

- Bar zoom dan labelnya dihapus. Canvas tetap menangani gerakan geser serta cubit dua jari melalui `detectTransformGestures`; perhitungan zoom dibatasi 1–4× dan posisi crop tetap berada di dalam gambar. Tombol atur ulang, batal, dan gunakan foto tetap tersedia. Petunjuk English/Indonesia diperbarui.
- Picker menandai proses memilih foto/crop sebagai aktif. Handler kembali milik editor profil dinonaktifkan selama proses tersebut supaya pembatalan foto tidak ikut menutup editor di belakangnya. Pembatalan dokumen/crop mengembalikan hasil kosong dan melepas status aktif.
- URI yang dipilih, status picker, visibilitas editor, dan identitas akun yang membuka picker memakai state yang dapat dipulihkan oleh Compose. Draf dalam ViewModel tetap tersedia saat Activity dibuat ulang dengan ViewModel yang sama. Pemulihan penuh draf setelah proses aplikasi dimatikan belum dibuktikan.
- Sebelumnya, callback foto profil hanya menerima hasil apabila `showEditProfile` masih benar. Hasil pilihan dapat diabaikan ketika editor tertutup saat perjalanan ke pemilih foto. Sekarang penerimaan mengacu pada akun yang membuka picker dan pemilik draf; hasil yang diterima memperbarui avatar draf serta membuka kembali editor. Hasil lama tidak boleh masuk ke akun lain setelah pergantian akun.
- Foto yang dipilih tetap berupa draf. Pengguna melihat pratinjau lalu menekan **Simpan perubahan** untuk mengunggahnya. Pembatalan tidak mengganti avatar tersimpan. Pengujian mencakup penggantian foto saja tanpa mengubah nama, upload melalui gateway pengujian, dan pembaruan cache identitas setelah penyimpanan berhasil.

### Klarifikasi: setelah Simpan masih terlihat foto lama

Pengguna menjelaskan bahwa foto baru muncul di editor, tetapi setelah Simpan masih terlihat foto sebelumnya. Tidak ada teks pesan gagal yang dilaporkan. Karena itu, perbaikan penerimaan hasil picker saja belum mencakup masalah ini.

- Halaman profil sebelumnya mengosongkan identitas ketika memuat ulang setelah penyimpanan, sehingga tampilan bisa memakai cache foto lama. Sekarang konfirmasi Simpan meneruskan nama dan URL foto yang benar ke ViewModel profil. Foto baru langsung tampil selama pembacaan ulang; respons lama pada pembacaan itu tidak mengganti identitas yang baru dikonfirmasi. Kegagalan pembacaan ulang mempertahankan foto tersimpan tersebut. Pergantian akun tetap membatalkan penerapan identitas ke akun lain.
- Ditemukan pula bug terpisah: timeout cache lokal setelah server selesai menyimpan diperlakukan sebagai kegagalan Simpan. Uji regresi sebelum perubahan mereproduksi kegagalan ini. Penulisan cache kini dibatasi dua detik, dan timeout setelah konfirmasi server tidak mengubah hasilnya menjadi gagal. Kegagalan sebelum penyimpanan server selesai tetap dilaporkan.
- Error upload foto dibedakan dari error pemrosesan foto. Pilihan foto tetap ada sebagai draf jika upload gagal dan dapat disimpan ulang.
- Uji langsung dengan satu akun sementara mencakup upload pertama, penggantian foto lama pada path yang sama, perubahan metadata Auth, upsert profil, pembacaan kembali profil, serta byte gambar di origin dan URL publik memakai versi `cacheNonce` baru. Kedua versi URL publik mengembalikan gambar yang sesuai pada uji ini; kegagalan CDN tidak direproduksi dan tidak dinyatakan sebagai penyebab pasti. Akun, profil, dan gambar sementara sudah dibersihkan.
- Pengujian SDK Kotlin memakai server loopback untuk memeriksa JPEG, path pemilik, upsert foto lama, respons upload gagal/coba lagi, metadata Auth, payload profil, dan cache identitas. Pengujian ViewModel memeriksa foto baru langsung terlihat, pembacaan lama tidak mengembalikannya, dan kegagalan pembacaan setelah Simpan tidak menggantinya dengan foto lama.

### Indikator pembaruan dan hasil Simpan

- Editor menampilkan status di bawah judul agar tetap terlihat saat keyboard terbuka atau bagian form digulir. Upload foto menunjukkan spinner dan “Mengunggah foto…”, lalu tahap penyimpanan menunjukkan “Memperbarui profil…”. Tombol Simpan tetap menampilkan “Menyimpan…” dan mencegah kiriman ganda.
- Setelah server mengonfirmasi penyimpanan, halaman profil menampilkan “Profil berhasil disimpan.” setelah editor ditutup. Pesan tidak bergantung pada keberhasilan pembacaan ulang profil. Pesan hanya ditampilkan untuk akun pemilik penyimpanan tersebut dan direset ketika memulai edit berikutnya.
- Kegagalan menghentikan spinner, menampilkan penjelasan di bagian status, mempertahankan draf, dan menyediakan tombol “Coba lagi”. Error nama tetap muncul pada kolom nama. Status diumumkan sebagai live region bagi pembaca layar.
- Pengujian mencakup upload yang ditahan, perubahan tahap ke penyimpanan, kiriman ganda, konfirmasi sukses, error upload/coba lagi, serta tampilan pada lebar 320 dp dan ukuran teks 150%. Bukti terbaru tersedia dalam `update-feedback-checks.log` dan `update-feedback-verification.json`; pratinjau status disimpan sebagai `feedback-*.png`. APK tetap tidak dibuat.
- Verifikasi indikator: **19 pengujian lulus** (13 alur penyimpanan dan 6 tampilan editor), tanpa kegagalan/error. Kompilasi sumber Android berhasil. Pratinjau kegagalan sudah diperiksa secara visual; pesan dan tombol coba lagi tetap terlihat pada teks 150%. Hash APK sebelumnya tetap sama.

## Publik → privat → publik

Audit menemukan dua penyebab pada Search: query tabel `shared_playlists` mengembalikan playlist privat milik pemanggil karena pemilik memang boleh membacanya melalui RLS; daftar di ViewModel juga tidak diperbarui saat pengguna kembali dari halaman playlist. Fungsi penyembunyian server sendiri sudah mengganti `cloud_playlists.is_public=false`.

- Ditambahkan `gratify_get_public_playlists()`, memakai **SECURITY INVOKER** dan join pada identitas pemilik/sinkronisasi cloud dengan syarat eksplisit `is_public IS TRUE`. Fungsi ini diterapkan lewat migrasi `20261012030322_public_playlist_discovery.sql`. RLS pemilik untuk data privat tidak dilonggarkan atau dihapus.
- Repository penjelajahan mengambil daftar dari fungsi publik tersebut. Search juga menyaring entri yang ditandai privat.
- Search memperbarui daftar ketika entry navigasinya kembali RESUMED, ketika tab Search dipilih ulang, dan ketika pengguna menekan coba lagi. Selama pemeriksaan, daftar lama dilepas; kegagalan jaringan tidak menghidupkan kembali entri yang mungkin sudah privat.
- Permintaan lama dibatalkan dan memakai nomor generasi. Respons lama yang terlambat tidak boleh mengembalikan daftar publik sebelum perubahan privasi.
- Data playlist, lagu, pin pemilik dan identitas shared dipertahankan. Playlist privat tetap tersedia bagi pemilik, termasuk pajangan privat di profil. Publikasi ulang membuatnya kembali tersedia di Search dengan identitas shared yang sama.

## Verifikasi dan batasnya

- PostgreSQL terisolasi: **36 pemeriksaan lulus**, masing-masing 18 pada skema TEXT dan UUID. Termasuk publik terlihat oleh pemilik/pengunjung/anonim; privat hilang dari penjelajahan untuk semuanya; lagu/pin privat hanya dapat dibaca pemilik; publikasi ulang mempertahankan identitas; akun lain/anonim tidak dapat mengganti visibilitas.
- Uji langsung server memakai fixture dalam transaksi yang di-rollback: publik → privat → publik terverifikasi untuk pemilik dan anonim, pin privat tetap ada, identitas shared tetap sama, dan tidak ada fixture tertinggal.
- Endpoint REST anonim merespons HTTP 200 dengan array dan hanya entri bertanda publik. Pemeriksaan advisor tidak menandai fungsi baru; tujuh pemberitahuan konfigurasi lama masih ada, sehingga keseluruhan server tidak dinyatakan bersih.
- Pemeriksaan awal: **39 pengujian kode/tampilan lulus**. Setelah klarifikasi foto lama, **59 pengujian pada sumber final lulus**, tanpa kegagalan, error atau kasus dilewati: gateway avatar 3, penyimpanan/editor profil 12, tampilan editor 5, profil/privasi 23, Search 12, dan crop 4. Delapan pengujian akses playlist dari tahap sebelumnya tetap tersimpan untuk kode data yang tidak berubah. Kompilasi sumber Android final berhasil. Hasil tersedia di `source-verification.json`, `avatar-display-checks.log`, dan salinan XML dalam `jvm-test-reports`. Tidak ada tugas assemble/package APK atau pemasangan perangkat yang dijalankan; hash APK universal sebelumnya tetap sama.

Bukti tersimpan di `artifacts/photo-privacy-revision-20261012/`. Perubahan UI Android baru belum diuji di ponsel melalui APK baru karena pengguna meminta menunda pembuatan APK. Pengujian codec crop native dari audit 11 Oktober tetap relevan untuk codec yang tidak diubah, tetapi bukan bukti sentuhan dua jari atau upload avatar akhir pada revisi ini.

Referensi akses database: [Supabase — Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security). Pemberitahuan advisor lama dapat ditinjau melalui [Database Advisors](https://supabase.com/docs/guides/database/database-advisors).
