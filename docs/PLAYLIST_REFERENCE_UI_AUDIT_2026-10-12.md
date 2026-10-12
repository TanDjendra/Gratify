# Audit UI/UX fitur koleksi baru — referensi Spotify

Revisi 12 Oktober 2026. Referensi: delapan foto Spotify yang dikirim pengguna,
serta [alur Blend resmi](https://support.spotify.com/id-id/article/social-recommendations-in-playlists/).
Fokus revisi adalah fungsi nyata dan susunan layar yang ditampilkan pada foto.

## Temuan dan hasil perubahan

| Referensi | Temuan Gratify | Revisi |
| --- | --- | --- |
| Menu +, foto 5 | Ikon kecil dan susunan seperti daftar pengaturan | Ikon bulat besar, judul tebal, empat pilihan dan deskripsi ringkas dalam lembar bawah |
| Nama playlist, foto 3 | Form nama standar di bagian atas | Form terpusat dengan gradient gelap, nama default terseleksi, Batal dan Buat; digunakan pribadi, kolaboratif, dan folder |
| Detail playlist, foto 4/6 | Sampul, judul dan tombol terpisah secara vertikal | Sampul di kiri, judul/Ubah/avatar di kanan; pada layar kecil atau teks besar susunan menjadi vertikal; tambah lagu menjadi tindakan utama |
| Nama & detail, foto 1 | Dialog kecil yang membatasi ruang foto/form | Lembar bawah dengan Batalkan/Nama & detail/Simpan; pratinjau foto di samping nama/deskripsi; status upload dan kegagalan tetap terlihat |
| Sampul, foto 2 | Pilihan foto tanpa halaman pratinjau besar | Pemilih sampul bersama menampilkan pratinjau persegi, Ubah gambar sampul, dan Gunakan sampul otomatis sebelum kembali ke editor |
| Daftar orang, foto 6 | Pembuatan link dan pencarian teman bercampur dalam satu daftar | Daftar Orang dengan Tambah kolaborator/Undang teman; langkah undangan terpisah dengan kartu playlist, bagikan link, atau Chat Gratify |
| Bagikan undangan, foto 7 | Hanya tombol link teks | Kartu undangan dengan judul/sampul dan merek Gratify; penjelasan hak kolaborator, umur link, penerima chat dan status kirim |
| Buat Blend, foto 8 | Nama → Buat seperti playlist biasa | Tampilan Undang teman ke Blend, avatar bertumpuk, penjelasan campuran, persetujuan data, dan Undang; pembuatan membuka langkah undangan langsung |
| Blend menunggu teman | Detail playlist kosong | Tampilan menunggu teman dengan tindakan Undang; ketika data belum cukup ditampilkan penjelasan sinkronisasi |
| Lagu dalam Blend | Asal pilihan tidak terlihat | Teks Dari selera [anggota] berdasarkan added_by yang tersimpan, tanpa mengklaim semua anggota yang menyukai lagu tersebut |
| Folder | Banyak tombol pengaturan di bagian atas | Jalur folder dan tindakan tambah/buat tetap jelas; ganti nama/pindah/hapus diletakkan pada Pilihan lainnya |

Editor playlist pribadi juga memakai lembar bawah dengan foto dan nama, serta
mempertahankan validasi nama, simpan sampul saja, draft gagal, batal dan status
penyimpanan. Model playlist pribadi saat ini tidak mempunyai deskripsi setara
workspace; revisi tidak menampilkan field deskripsi yang tidak tersimpan.

## Perilaku dan privasi

Blend otomatis tetap terpisah dari playlist kolaboratif manual. Nama Blend dibuat
otomatis dan dapat diubah kemudian. Membuka link masih meminta pratinjau/Gabung;
persetujuan ringkasan selera tidak dianggap diberikan hanya karena link dibuka.
Anggota Blend dengan persetujuan sekarang boleh membuat link dan undangan chat,
tetapi tidak mendapat hak pemilik untuk mengubah metadata, mengeluarkan anggota,
mencabut semua undangan, membatasi, atau menghapus playlist. Anggota yang keluar,
orang luar, dan anggota tanpa persetujuan ditolak oleh server.

Migrasi: `20261012100915_blend_member_invitations.sql`. Fungsi internal tetap
tidak dapat dieksekusi langsung oleh anon/authenticated; akses melalui RPC yang
memeriksa akun, keanggotaan, persetujuan dan token. Empat belas pemeriksaan produksi
dijalankan dalam transaksi rollback; akun dan pesan uji tidak tersisa.

Batas 10 peserta, masa link tujuh hari, target chat, privasi pencarian dan batas
folder lima tingkat dipertahankan. Menghapus folder mempertahankan isi. Tombol
Undang dan Simpan tetap terjangkau pada layar kecil dengan teks besar.
Form nama pada ruang keyboard kecil memakai tindakan Batal/Buat yang tetap
terlihat, sementara isi form dapat digulir. Editor menolak perubahan sheet menjadi
tersembunyi selama proses menyimpan; ketukan di luar lembar tidak menghilangkan
status penyimpanan atau draft.
Seluruh baris persetujuan Blend dapat diketuk, dengan satu kontrol checkbox untuk
pembaca layar. Sinkronisasi field nama tidak lagi memakai coroutine tertunda
yang dapat mengembalikan nilai lama saat pengetikan cepat; teks dan posisi kursor
diuji setelah perubahan berturut-turut tanpa jeda.
Pratinjau sampul langsung membuka lembar penuh dan melewati keadaan setengah
terbuka, sehingga Kembali menutup pratinjau alih-alih mengecilkannya dan
menyembunyikan tindakan foto.

## Batas kesetaraan dengan Spotify

- Mesin Blend Gratify menggunakan favorit dan ringkasan dengar yang tersinkron,
  bukan algoritma personalisasi Spotify. Pembaruan dihitung saat detail dibuka
  setelah sehari atau hasil diinvalidasi oleh perubahan anggota.
- Belum ada rekomendasi baru di luar kandidat favorit/riwayat, skor kecocokan,
  story selera atau editor desain sampul bebas. UI tidak mengisi skor fiktif atau
  menambahkan tombol yang belum mempunyai fungsi.
- Undangan yang diterbitkan selalu merupakan undangan anggota resmi. Switch untuk
  mengubah undangan kolaborator menjadi link pendengar biasa pada foto Spotify
  belum disalin karena server Gratify belum menyediakan jenis undangan pendengar.
  Pengaturan publik/privat tetap tersedia dan tidak berubah karena membagikan link.
- Pilihan aplikasi eksternal mengikuti panel berbagi Android; merek/ikon layanan
  Spotify tidak digunakan sebagai fitur palsu dalam Gratify.

## Verifikasi

81 pemeriksaan database lokal dan 14 pemeriksaan hak undangan pada produksi lulus.
46 tes aplikasi lulus, termasuk render pada lebar 320/390 dp, teks 150%, ruang
keyboard terbatas, status menyimpan/gagal, pemilih sampul, nama, privasi,
alur lagu, folder, link dan izin workspace.
Pratinjau sampul juga diperiksa pada layar kecil agar tindakan pilihan foto
tetap terjangkau.
Advisor workspace hanya melaporkan informasi RLS tanpa policy pada tujuh tabel
privat; akses tabel langsung memang ditolak. Tidak ada temuan keamanan workspace
baru dari perubahan fungsi ini. Temuan server lama berada di luar revisi UI.
Rujukan [RLS tanpa policy](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy).

Hasil render, tes aplikasi dan pemeriksaan debug akhir disimpan di
`artifacts/playlist-reference-20261012/`. Laporan QA versi rilis 2.1.3 sebelumnya
tetap merupakan hasil APK lama; revisi UI ini harus dinilai melalui artefak baru.

## Hasil debug di ponsel

APK akhir berhasil dipasang sebagai **Gratify Dev** (`com.tan.gratify.next.dev`,
2.1.3-dev/64) pada vivo I2218, Android 16, RAM 12 GB. Empat belas pemeriksaan
antarmuka lulus: menu empat pilihan, nama panjang yang dimasukkan cepat dan
tersimpan utuh, editor, daftar orang, kartu undangan, ketukan label persetujuan,
undangan Blend yang terbuka langsung, Blend satu anggota, tambah playlist ke
folder, penghapusan folder yang mempertahankan playlist, pratinjau sampul penuh,
satu Kembali ke editor, serta playlist pribadi semula yang tetap berada di Pustaka.
Log proses debug akhir tidak memuat baris crash/OOM/ANR yang diperiksa.

Lima workspace uji dan satu folder uji sudah dihapus melalui aplikasi. Pemeriksaan
server membuktikan nol sisa record workspace, anggota, undangan, operasi, folder
dan penempatan milik fixture tersebut. Tidak ada penggantian avatar pengguna atau
pengiriman undangan kepada pengguna nyata dalam uji ini.

APK: `artifacts/playlist-reference-20261012/Gratify-ui-reference-debug-arm64.apk`
(73.8 MB; SHA-256
`5513bb55de487086eeb1cfb1bffefde145403c0dec898a6e30b81a291e4ed2f9`).
Ringkasan terverifikasi: `artifacts/playlist-reference-20261012/verification.json`.

Penerimaan undangan di dua akun Android nyata, perangkat fisik RAM 2–4 GB, serta
gerakan crop dua jari secara manual belum diverifikasi pada revisi ini. Pembaruan
APK rilis publik tidak dilakukan; APK debug merupakan pratinjau revisi UI.
