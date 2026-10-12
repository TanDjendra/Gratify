# Audit dan perbaikan playlist — 3 Oktober 2026

Kode aplikasi selesai diperbaiki dan migrasi sudah diterapkan ke Supabase aktif. **37 pemeriksaan JVM dan 50 pemeriksaan PostgreSQL lulus**, kompilasi Android berhasil. Belum membuat APK baru atau melakukan uji langsung di HP.

Pilihan pengguna: satu tombol **love/Simpan**, satu hitungan untuk tiap akun; melepas dari Pustaka mengurangi hitungan. Thumbnail mengikuti status playlist: publik dapat dibaca semua pengguna, privat hanya pemilik.

## Temuan dan hasil

| Temuan | Sebelum | Perbaikan |
|---|---|---|
| P01 — Sampul berupa file lokal | `content://` atau alamat file perangkat disalin ke server; pengguna lain tidak bisa menampilkan gambarnya. | Gambar diproses menjadi JPEG, diunggah ke bucket khusus berdasarkan pemilik dan identitas playlist. URL objek disimpan, akses tampilan memakai tautan bertanda tangan. Sampul lokal lama dicoba diunggah ketika pemilik mempublikasikannya. |
| P02 — Izin gambar | Belum ada penyimpanan dan aturan baca untuk sampul playlist. | Bucket `playlist-covers` tetap privat. Pemilik membaca sampulnya; pengguna lain hanya membaca sampul yang benar-benar dipilih oleh playlist publik. Pengguna lain tidak dapat menulis sampul milik pemilik. |
| P03 — ID cloud dan ID publik | Halaman membuat playlist semu dari ID cloud lalu memakai ID yang salah untuk pencatatan simpan. | Tautan cloud diselesaikan ke ID shared yang sebenarnya sebelum memuat lagu atau menyimpan playlist. |
| P04 — Simpan parsial dan jumlah perkiraan | Playlist ditulis lokal terlebih dahulu; server gagal tetapi UI bisa tetap menganggapnya tersimpan. Jumlah dinaikkan/diturunkan dengan perkiraan `+1/-1`. | Server mengonfirmasi simpan terlebih dahulu, kemudian transaksi lokal memeriksa pemilik akun. UI mengambil jumlah aktual dari server. Kegagalan lokal setelah konfirmasi server dapat dipulihkan lewat pembacaan simpan server. |
| P05 — Validasi jumlah simpan | Identitas akun, status publik, dan larangan menghitung pemilik belum konsisten. | RPC mengunci baris playlist, memeriksa akun yang diharapkan, menolak simpan sumber privat dan simpan playlist sendiri, serta memakai pasangan unik playlist/akun. Hitungan lama dihitung ulang dari simpan pengguna lain tanpa menghapus catatan simpan. |
| P06 — Privat lalu publik kembali | Mengubah status menjadi privat menghapus baris shared dan seluruh catatan simpan terkait. | Mengubah menjadi privat mempertahankan ID dan simpan di balik aturan akses. Publikasi ulang memakai ID yang sama. Penghapusan permanen playlist tetap memakai jalur penghapusan terpisah. |
| P07 — Pemulihan simpan | Pemulihan cloud hanya mengambil playlist buatan akun; simpan pengguna lain tidak dipulihkan. | Pemulihan mengambil simpan akun, membuat salinan lokal secara idempotent, dan memperbarui judul, sampul, pembuat, serta urutan lagunya. Status like dan berkas unduhan lagu tidak dihapus. Jika daftar lagu berubah, label unduhan penuh direset agar tidak mengklaim lagu baru sudah diunduh. |
| P08 — Penghapusan offline | Antrean penghapusan lama dapat menimpa tindakan simpan ulang. Pesan sukses penghapusan hanya memeriksa antrean playlist milik sendiri. | Simpan ulang membatalkan niat penghapusan lama dalam transaksi lokal. Pemulihan tidak membangkitkan playlist dengan niat penghapusan yang masih aktif. Status penghapusan juga memeriksa antrean simpan. |
| P09 — Ketidakselarasan tampilan | Profil memakai ikon tambah, halaman simpan berbeda, jumlah simpan tidak konsisten terlihat pada detail lokal/profil. | Playlist publik memakai love/Simpan, profil menandai playlist tersimpan, detail Pustaka menyediakan pelepasan simpan, dan profil/detail menampilkan jumlah aktual. Pemilik tidak diberi tombol simpan playlist sendiri. |
| P10 — Sumber tidak pulih | Playlist yang pernah tidak tersedia tidak diperiksa lagi. | Sumber diperiksa kembali saat detail dimuat dan status tidak tersedia dihapus bila sumber terlihat lagi. |
| P11 — Perubahan judul dan label tindakan | Judul lokal tidak meminta pembaruan cloud; pembaruan metadata gagal tidak memiliki antrean; tombol hapus di detail publik sebenarnya hanya membuat playlist privat. | Judul dan sampul memiliki antrean lokal yang tahan restart. Sinkronisasi mencoba ulang pada playlist yang sudah memiliki cloud tanpa mengubah publik/privat, serta hanya mengakui revisi yang benar-benar berhasil. Tindakan di detail publik diberi label **Sembunyikan dari publik** sesuai hasilnya. |
| P12 — Sampul dapat memperlambat Pustaka | Pemrosesan gambar berpotensi menunda akses daftar lokal, terutama tanpa koneksi. | Daftar lokal ditampilkan sebelum pengambilan gambar. Permintaan sampul dibatasi empat sekaligus, cache dipisahkan per akun, dan status refresh sesi gagal tidak memicu pengambilan sampul online. |

## Verifikasi

`artifacts/playlist-audit-2026-10-03/verification.json` mencatat 37 tes aplikasi, nol gagal, nol error, nol dilewati:

- Tujuh tes jaringan memakai Supabase SDK nyata dan server HTTP lokal: penyelesaian ID, validasi akun, RPC simpan/lepas, penolakan server, cache URL sampul, penolakan sampul privat, serta mempertahankan urutan playlist saat mengambil banyak sampul.
- Enam tes SQLite nyata: pembaruan sumber tanpa duplikat, mempertahankan like lagu, antrean penghapusan, simpan ulang, isolasi akun, serta antrean revisi metadata dan larangan mengedit sumber pengguna lain.
- Tujuh regresi penyimpanan pustaka per akun.
- Tujuh belas regresi profil sebelumnya.

`scripts/verify-playlist-saves-covers.py` menjalankan 25 pemeriksaan pada masing-masing skema TEXT dan UUID, termasuk 16 permintaan simpan serentak dari satu akun, akses anonim/pemilik/pengguna lain, privat/publik ulang, penghapusan permanen, penerapan ulang migrasi, dan jalur simpan idempotent APK lama.

Log kompilasi dan tes: `artifacts/playlist-audit-2026-10-03/verification.log`.

## Aktivasi server

Migrasi: `supabase/migrations/202610030002_playlist_saves_covers.sql`.

Pemeriksaan baca pada proyek aktif `bnabldxsqpvkyqpjcsdv` mengonfirmasi tipe UUID untuk ID playlist, tipe TEXT untuk user ID simpan, trigger `sync_shared_playlist_add_count`, dan aturan avatar yang terpisah. Sebelum aktivasi, RPC simpan/pemulihan baru dan bucket sampul belum tersedia. Salinan migrasi dalam editor Supabase cocok dengan berkas yang diuji, fingerprint FNV-1a `82133dea`.

**Status: diterapkan pada 3 Oktober 2026 ke Supabase aktif `bnabldxsqpvkyqpjcsdv`.** Editor mengonfirmasi transaksi berhasil. Verifikasi baca mengonfirmasi RPC simpan/pemulihan tersedia, authenticated dapat menjalankan simpan sedangkan anon tidak, bucket sampul privat dengan batas 5 MiB dan JPEG, tiga aturan akses sampul terpasang, fungsi privat mempertahankan ID, dan **nol selisih jumlah simpan**. Bukti: `live-verification.json`, `supabase-migration-success.jpg`, dan `supabase-verification.jpg` di direktori artefak playlist-audit.

API INSERT simpan APK lama tetap dipertahankan dengan aturan publik/non-pemilik yang lebih ketat. Tampilan love, pengunggahan sampul, pemulihan simpan, dan antrean metadata di aplikasi memerlukan APK baru dari kode ini.

## Batas yang masih perlu diketahui

- Belum menguji unggah gambar dan dua akun nyata dari HP ke server aktif. Tes aturan penyimpanan dilakukan di PostgreSQL lokal; tes penandatanganan URL memakai Supabase SDK dengan server HTTP lokal.
- Tautan tampilan gambar berlaku maksimal lima menit. Tautan yang sudah terbit atau gambar yang sudah di-cache tidak dapat ditarik kembali seketika saat playlist berubah menjadi privat; permintaan akses baru mengikuti status terbaru.
- Gambar lama yang hanya tersimpan pada perangkat pengguna tidak bisa diambil dari server. Jika izin file lamanya sudah hilang, pengguna perlu memilih gambar tersebut lagi.
- Pemulihan dan pembaruan daftar lagu sumber terjadi melalui pemulihan cloud akun; belum berupa pembaruan realtime. Kegagalan jaringan tidak menghapus salinan lokal.
- Simpan baru memerlukan koneksi untuk konfirmasi server. Pelepasan simpan dari Pustaka dapat diantrikan saat offline. Berkas lagu yang sudah diunduh tetap disimpan.
- Perubahan judul/sampul disimpan dalam antrean lokal dan dicoba lagi melalui sinkronisasi akun yang aktif (interval berkala lima menit atau pemulihan koneksi). Tindakan menerbitkan playlist baru tetap perlu konfirmasi keberhasilan online; antrean tidak mempublikasikan draft lokal secara otomatis.
- Bucket memakai nama file baru untuk setiap versi sampul. Pembersihan objek versi lama belum ditambahkan; akses pengguna lain hanya diberikan pada sampul yang sedang dipakai playlist publik.

Tidak ada APK baru yang dibuat atau dipasang pada pekerjaan ini. APK debug lama belum berisi perubahan aplikasi di atas.
