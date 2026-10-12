# Playlist bersama, Blend, folder, dan undangan Gratify

Implementasi 12 Oktober 2026. Domain undangan: **https://gratify.web.id**.
APK 2.1.3 sudah dibuat dan dipasang setelah pengguna mengizinkan build; hasilnya
tercatat pada laporan uji ponsel. Audit lanjutan konsep Blend ada di
[BLEND_CONCEPT_AUDIT_2026-10-12.md](BLEND_CONCEPT_AUDIT_2026-10-12.md).

## Alur yang diterapkan

- Tombol `+` Koleksi membuka pilihan Playlist, Playlist kolaboratif, Blend, dan Folder.
- Playlist biasa memakai alur nama → playlist yang sudah ada. Usulan nama personal
  `Playlist-ku No.N`, kolaboratif `Playlist kami No.N`, Blend `Blend kami No.N`.
- Kolaboratif dimulai privat. Pemilik membuka tombol anggota di samping avatar,
  membuat link umum atau mencari nama pengguna untuk undangan chat.
- Link memakai `/app/invite?token=…`. Browser menampilkan tombol buka aplikasi;
  aplikasi menampilkan pratinjau lalu meminta penerima memilih **Gabung**.
  Membuka link atau menerima pesan tidak otomatis membuat orang menjadi anggota.
- Undangan berlaku tujuh hari, dapat dicabut; undangan chat terikat akun penerima.
  Maksimal 50 anggota kolaboratif dan 10 anggota Blend, termasuk pemilik.
- Anggota kolaboratif bisa menambah, menghapus dan mengurutkan lagu. Pemilik mengatur
  nama, deskripsi, publik/privat, undangan, pengeluaran anggota, dan penghapusan.
  **Batasi hanya saya** membuat privat, mengeluarkan anggota lain, dan mencabut link.
- Menjadikan privat menghapus playlist dari penemuan Search, tetapi anggota undangan
  tetap mempunyai akses. Pengeluaran anggota juga mencabut undangan lama agar orang
  yang dikeluarkan tidak bisa langsung memakai ulang link.
- Blend memakai persetujuan setiap anggota, favorit dan ringkasan jumlah dengar
  yang tersinkron. Dua anggota dibutuhkan sebelum membentuk hasil. Kandidat dibatasi
  per artis/anggota, digabung tanpa duplikasi, maksimal 50 lagu. Hasil disimpan bersama
  dan diperbarui saat dibuka setelah satu hari, atau saat anggota berubah.
  Ini algoritma Gratify, bukan algoritma atau persentase kecocokan Spotify.
- Keluar dari Blend menghapus hasil lama sebelum perhitungan berikutnya sehingga
  kontribusi anggota yang keluar tidak tertinggal.
- Folder mengatur koleksi pemilik dengan subfolder hingga lima tingkat. Playlist lokal
  yang tersinkron, playlist tersimpan, dan playlist bersama dapat dipindahkan ke dalamnya.
  Folder dapat diubah nama dan dipindahkan; pilihan tujuan mengecualikan dirinya sendiri
  dan semua turunannya. Server juga menolak siklus dan batas kedalaman yang terlampaui,
  termasuk dua pemindahan bersamaan. Menghapus folder memindahkan playlist dan subfolder
  ke induknya, atau Koleksi jika folder berada di akar; isi tidak ikut dihapus.
- Folder menampilkan jalur induk dan menyediakan pembuatan subfolder, kolaboratif,
  atau Blend langsung di dalamnya. Playlist biasa dibuat dari Koleksi lalu dapat
  dipindahkan ke folder melalui Tambahkan playlist.
- Pemilik playlist kolaboratif dan Blend dapat memilih foto sampul, memakai crop
  persegi dengan gerakan dua jari, atau kembali ke sampul otomatis. Halaman detail
  memakai foto pilihan atau sampul dari lagu. Editor menampilkan tahap mengunggah dan
  memperbarui; kegagalan mempertahankan pilihan agar dapat dicoba lagi. Editor dapat
  digulir pada layar kecil dengan teks besar.
- Pemutaran memakai antrean seluruh playlist. Perubahan mempunyai indikator proses,
  keberhasilan setelah konfirmasi server, dan kegagalan beserta muat ulang.

## Penyimpanan dan keamanan

Migrasi `20261012043526_playlist_workspaces.sql`,
`20261012050732_playlist_workspace_indexes.sql`, dan
`20261012053750_workspace_covers_nested_folders.sql` sudah diterapkan ke server.
Data pengguna sebelumnya tidak direset.

Playlist bersama mempunyai catatan kanonis terpisah dari snapshot playlist pribadi,
sehingga sinkronisasi APK lama tidak menimpa perubahan anggota. Mutasi dilindungi
transaksi dan identitas operasi; perubahan urutan memeriksa revisi terbaru. Semua
tabel baru mempunyai RLS dan akses tabel langsung ditolak untuk anon/authenticated.
RPC memeriksa identitas dan kepemilikan/keanggotaan untuk setiap tindakan.
Sampul bersama disimpan di bucket privat `workspace-covers`, JPEG maksimal 5 MiB.
Hanya pemilik dapat mengunggah dan mengganti sampul; anggota dapat membaca sampul
privat dan pengunjung dapat membaca sampul playlist publik. Mengubah menjadi privat
atau mengeluarkan anggota menghentikan penerbitan link foto baru untuk pihak tersebut.
Link foto yang sudah diterbitkan berlaku maksimal dua menit; pencabutan akses tidak
dapat menghapus salinan foto yang sudah diterima perangkat.
Unggahan memakai nama file baru, sehingga cache tidak terus menampilkan foto lama.
Cache link dibatasi 128 entri dan dibersihkan saat akun berganti. Perubahan metadata
harus dikonfirmasi server sebelum foto lama dibersihkan lewat Storage API; foto yang
masih aktif tidak dapat dihapus. Unggahan yang sudah selesai digunakan kembali ketika
percobaan menyimpan metadata gagal. Pembersihan foto lama bersifat best effort;
unggahan yang dibatalkan atau kehilangan konfirmasi dapat menyisakan file tanpa rujukan,
sehingga pembersihan berkala tetap menjadi pekerjaan operasional berikutnya.
Token undangan acak hanya disimpan sebagai SHA-256 di tabel privat. URI lengkap tidak
dicatat oleh perute MainScreen. Link yang menunggu login disimpan di state Activity
agar tetap tersedia setelah rekreasi; token dikonsumsi sebelum navigasi undangan.

Chat memakai jenis pesan server `playlist_invite`/`blend_invite`; teks biasa tidak
dianggap undangan resmi. Percobaan ulang pengiriman memakai identitas operasi yang
sama sehingga kehilangan konfirmasi jaringan tidak menggandakan pesan.

Situs versi 9 sudah dipublikasikan melalui Sites. Halaman undangan tidak mengambil
data server atau menukarkan token; tidak memakai pelacak, aset pihak ketiga, atau
penyimpanan browser. Query dihapus dari alamat/history setelah tombol siap.
`/.well-known/assetlinks.json` mengaitkan domain dengan `com.tan.gratify.next` dan
sertifikat rilis baru. Browser dan permintaan Android tanpa cookie mendapat HTTP 200
dan MIME JSON yang benar. Halaman memberi tahu bahwa APK terbit saat ini belum
menyertakan fitur ini. URL penerbitan Sites:
https://gratify-music.nendrasaridiar8468.chatgpt.site.

## Verifikasi dan batas hasil

- 68 pemeriksaan PostgreSQL lokal lulus, termasuk transaksi dua editor bersamaan,
  revisi urutan, privasi, batas anggota, undangan dicabut/kedaluwarsa/salah akun,
  persetujuan Blend, akses setelah keluar, sampul privat/publik, penggantian sampul,
  kedalaman dan siklus folder, pemindahan folder bersamaan, serta penghapusan yang
  mempertahankan subfolder dan playlist.
- 10 pemeriksaan pada server produksi lulus dalam transaksi yang di-rollback,
  termasuk fungsi chat yang sebenarnya dan campuran favorit dua akun. Tidak ada
  akun, playlist, folder, atau pesan uji tersisa.
- Tambahan 9 pemeriksaan ekstensi pada server produksi lulus dalam transaksi yang
  di-rollback: hierarki, siklus, pemindahan isi saat folder dihapus, metadata sampul,
  izin anggota/pihak luar, serta izin pembersihan sampul otomatis. Ini menguji metadata
  Storage dan izin database; belum menguji unggahan byte foto melalui ponsel.
  Tidak ada akun uji ekstensi atau objek sampul uji tersisa; bucket tetap privat.
- Pada implementasi awal, 21 tes JVM lulus: parser domain, perubahan akun, pratinjau, hak akses dicabut,
  percobaan ulang chat, menu dan persetujuan pada layar kecil dengan teks besar,
  serta regresi profil/chat. Kompilasi sumber Android lulus.
- Pemeriksaan akhir ekstensi: 22 tes JVM lulus (9 perilaku ViewModel, 5 layar playlist,
  3 jalur folder, 2 link, dan 3 tampilan Koleksi). Termasuk percobaan ulang sampul tanpa
  unggah ganda, pergantian akun saat unggah, kembali ke sampul otomatis, gangguan link
  foto sementara, dan layar 320 × 560 dengan skala teks 1,5. Kompilasi sumber Android
  dan pemeriksaan `androidApp:compileDebugKotlin` lulus; tidak menghasilkan APK.
- Halaman browser diuji pada 320 piksel: tombol terlihat tanpa scroll horizontal.
  Token kosong, salah format, duplikat, dan injeksi ditolak.
- Advisor server: seluruh foreign key fitur baru mempunyai indeks. Informasi
  [RLS tanpa policy](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy)
  pada tabel privat memang disengaja karena akses langsung ditolak. Indeks baru yang
  belum dipakai belum mempunyai trafik produksi.
- Temuan server lama, termasuk view `public_profiles`, search_path beberapa fungsi,
  hak eksekusi fungsi SECURITY DEFINER publik, pengaturan password/MFA, foreign key
  tanpa indeks pada tabel lama, dan policy ganda, masih memerlukan audit terpisah.
  Tidak ada foreign key tanpa indeks atau peringatan keamanan baru pada schema
  workspace; policy Storage diperiksa lewat uji izin. Rujukan:
  [view security](https://supabase.com/docs/guides/database/database-linter?lint=0010_security_definer_view),
  [search_path](https://supabase.com/docs/guides/database/database-linter?lint=0011_function_search_path_mutable),
  [password](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).

**Pembaruan setelah izin build:** APK debug 2.1.3-dev/kode 64 sudah dibangun dan
diuji pada vivo I2218 Android 16 dengan RAM 12 GB. Menu, sampul unggahan, privasi
pencarian, folder bertingkat, pratinjau undangan, Blend satu anggota, urutan dan
pemutaran lagu diperiksa melalui aplikasi. Tiga temuan pada menu `+`, navigasi
penghapusan, dan antrean lagu telah diperbaiki dan diuji kembali. Sepuluh siklus
buka-tutup sampul pada debug akhir lulus tanpa crash atau pertumbuhan PSS
berkelanjutan. Lihat [laporan uji ponsel](PLAYLIST_WORKSPACES_DEVICE_QA_2026-10-12.md)
untuk bukti dan batas hasil. Pengujian dua pengguna nyata, perangkat RAM 2–4 GB,
dan pemakaian panjang tetap diperlukan sebelum klaim siap rilis publik.
Sampul khusus playlist bersama dan folder bertingkat sudah ditambahkan di kode. Tampilan Koleksi untuk
playlist bersama tanpa sampul pilihan masih memakai ikon musik; kolase otomatis
tersedia pada halaman detail tanpa membuat permintaan detail untuk setiap tile.

Artefak: `artifacts/playlist-workspaces-20261012/` berisi hasil tes, gambar tampilan,
uji produksi rollback, pemeriksaan domain, dan arsip website.
