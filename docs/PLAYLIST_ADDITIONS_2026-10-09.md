# Penambahan lagu ke playlist dan pembaruan publik

Halaman playlist milik sendiri memiliki tombol **Tambah lagu** berukuran minimal 48 dp di bawah bagian informasi playlist. Tombol membuka pemilih lagu untuk playlist yang sama. Playlist tersimpan milik pengguna lain dan playlist yang tidak tersedia tidak menampilkan tombol ini.

Pemilih menampilkan nama dan jumlah lagu saat ini, pencarian, lagu terakhir didengarkan, serta tab untuk menyusun pilihan baru. Lagu yang sudah ada ditandai dan tidak dapat dipilih ulang. Penambahan tidak mengubah nama atau sampul, dan pilihan baru ditempatkan setelah lagu lama. Setelah berhasil, pemilih ditutup, halaman playlist yang sama ditampilkan kembali, jumlah lagu diperbarui, dan paging dimuat ulang. Kegagalan menyimpan mempertahankan pilihan; keluar dengan pilihan yang belum disimpan meminta konfirmasi.

Penyimpanan menggunakan transaksi Room yang memeriksa pemilik aktif dan menolak playlist tersimpan milik orang lain. Lagu, pasangan lagu/playlist, urutan, daftar ID, dan antrean pembaruan cloud ditulis bersama. Duplikat disaring lagi dalam transaksi untuk menangani perubahan daftar sejak pemilih dibuka. Perpindahan akun menghapus pilihan dan mencegah penulisan ke akun berikutnya.

Penambahan dari menu lagu, saran lagu, penambahan banyak lagu, dan pemilih baru mencatat pembaruan persisten. Layanan sinkronisasi aplikasi mencoba mengirim perubahan setelah penyimpanan lokal, tanpa bergantung pada umur halaman pemilih. Gangguan koneksi mempertahankan pembaruan untuk sinkronisasi berkala berikutnya, atau ketika aplikasi berjalan kembali. Pembaruan hanya dikirim untuk playlist yang sudah ada di cloud. Draft lokal tidak diterbitkan otomatis.

Server memakai identitas playlist yang sama dan parameter visibilitas kosong untuk mempertahankan status publik/privat. RPC yang sudah ada memperbarui cadangan cloud dan isi playlist publik dalam satu transaksi. Tidak ada migrasi server atau penambahan pustaka. Pengguna lain melihat isi baru setelah membaca ulang playlist dan sinkronisasi berhasil; perubahan ini tidak menambahkan langganan realtime pada layar publik.

## Verifikasi

Hasil tes, gambar tampilan, log build, dan identitas APK disimpan di `artifacts/playlist-additions-2026-10-09`. Pengujian sinkronisasi memakai database Room asli serta server HTTP sintetis dengan repository produksi. Cakupannya meliputi penambahan melalui menu lagu/pemilih, identitas dan visibilitas yang tetap, urutan, duplikat, kegagalan upload lalu retry, larangan publikasi draft, pemilik salah, playlist tersimpan, serta rollback transaksi. Pengujian ini tidak menulis ke akun produksi dan tidak membuktikan pembacaan lintas dua perangkat nyata.

Fixture Android memakai screen dan ViewModel pemilih produksi dengan repository sintetis. Keberadaan tombol, navigasi kembali ke playlist yang sama, dan pemuatan ulang diperiksa melalui pengujian komponen/navigasi produksi. Fixture tidak termasuk APK release.

Hasil akhir: **47 tes otomatis dan 19 pemeriksaan Android lulus**, tanpa kegagalan, tes dilewati, atau crash dalam buffer pengujian. Tampilan diuji pada 390×900, 320×560 dengan teks 150%, dan desktop 1100×900. Android diperiksa pada ukuran teks 1.0/1.5, termasuk penandaan lagu lama, memilih lagu baru, kembali setelah berhasil, dan mempertahankan pilihan saat gagal. Ukuran teks dikembalikan ke bawaan dan emulator dihentikan setelah verifikasi.

Build debug berhasil. APK `2.1.0-dev` (61), paket `com.tan.gratify.dev`, dibuat **9 Oktober 2026 pukul 09:17:10 WIB**. Salinan untuk pengguna: `artifacts/playlist-additions-2026-10-09/Gratify-debug-playlist-20261009.apk`, ukuran 104.588.712 byte, SHA-256 `F58F694D31B24DB0B5C2B76E4CE08943B0BABF007B95103E77C2FD67F5FF746E`. Salinan memiliki hash yang sama dengan APK hasil build yang diuji. Build ini juga mempertahankan perubahan kapsul Search dan perbaikan playback sebelumnya.
