# Gratify baru: nama dan reset data (11 Oktober 2026)

Nama tampilan aplikasi baru kini **Gratify**, dengan ID tetap `com.tan.gratify.next`. Ini mempertahankan identitas baru dan kunci signing yang bekerja. Firebase/Supabase callback dan shortcut tetap cocok dengan ID tersebut. Nama launcher berlaku di seluruh locale APK.

APK full produksi telah dibangun, ditandatangani, diperiksa, dan dipasang melalui pembaruan paket Next yang sudah ada. Data lokal paket baru kemudian dikosongkan dengan `pm clear com.tan.gratify.next` sesuai permintaan untuk mulai baru. Startup memunculkan dialog izin Android bernama Gratify; pilihan izin diserahkan kepada pengguna. Pada pemeriksaan rename awal, identitas, versi, dan waktu instalasi paket lama diperiksa tetap sama. Proses ini tidak menjalankan uninstall atau clear data paket lama. Pada pemeriksaan perangkat setelah reset server, paket lama maupun paket baru tidak lagi ditemukan; APK baru kemudian dipasang kembali dan data lokalnya dikosongkan. Hash APK pemasangan terakhir sudah diperiksa sesuai kandidat terbaru.

## APK terbaru

- Universal: `artifacts/gratify-reset-20261011/release-gratify/Gratify.apk`, SHA-256 `6bcfa46418b4aed89430de3a8c031d25469deb4baffbbd8dc06de10cdea1d55a`, 54.967.480 byte.
- Arm64 yang dipasang: `Gratify-full-2.1.2-arm64-v8a-NEW-KEY.apk`, SHA-256 `291d85a908253c120b3c5e15e1ec3d229a1cd392e7aa8c00549ffdbfa6453a62`, 27.935.630 byte.
- Versi 2.1.2 / code 63; sertifikat SHA-256 `efefe1b8bf7c6502aeeb60db9a45b430dc50040e11c6f76eb22e2308a51c316a`.
- Lint 0 error/fatal dan 65 warning. Zipalign 16 KB dan 45 library native 64-bit lulus; payload signed cocok dengan build unsigned. Hash APK terpasang cocok dengan APK arm64 yang diberikan.
- APK kandidat sebelumnya bernama Gratify Next dipertahankan sebagai bukti pengujian terdahulu. Gunakan folder `gratify-reset-20261011` untuk nama terbaru. Tidak ada APK/release yang dipublikasikan.

## Reset server: selesai dan terverifikasi

Setelah pengguna memberikan konfirmasi akhir, seluruh akun dan data pengguna pada proyek Supabase `bnabldxsqpvkyqpjcsdv` direset. Cadangan privat memuat 17 akun dan 608 baris data aplikasi tepat sebelum penghapusan; jumlah tersebut mencakup perubahan yang masuk sejak inventaris awal. Transaksi reset memperbarui snapshot Auth/aplikasi di bawah lock sebelum mengosongkan tabel, lalu memastikan hasilnya nol sebelum COMMIT.

| Pemeriksaan tepat setelah COMMIT reset | Hasil |
| --- | ---: |
| Akun Auth | 0 |
| Sesi login | 0 |
| Refresh token | 0 |
| Total baris pada 25 tabel aplikasi | 0 |
| File Storage | 0 |
| Tabel aplikasi yang tetap tersedia | 25 |
| Bucket Storage yang tetap tersedia | 3 |
| Riwayat migrasi Auth / Storage | 82 / 73, tetap seperti sebelum reset |

Data playlist, likes, riwayat, queue/settings, follows, profil/privacy, story, pesan, perangkat push, antrean notifikasi dan koneksi/percobaan OAuth YouTube dikosongkan. Tabel, fungsi, trigger, policy/RLS, konfigurasi penyedia login, Firebase, callback dan bucket tetap tersedia. Data lokal aplikasi baru dikosongkan kembali setelah reset server dan aplikasi dibuka pada startup baru. Pengguna perlu membuat akun kembali; login Google dapat membuat akun baru melalui penyedia yang sama. Setelah pemasangan baru, pemeriksaan berikutnya menemukan 1 akun dengan ID yang berbeda dari seluruh 17 akun lama, 1 sesi baru dan 1 file baru. Likes, playlist cloud dan pesan masih kosong pada pemeriksaan tersebut. Perbandingan ID akun serta bucket/path file terhadap cadangan memastikan 0 akun lama dan 0 file lama tersisa. Data baru yang masuk setelah reset dipertahankan; tidak ada reset berulang terhadap akun yang baru dibuat.

### Cadangan dan bukti

- Schema privat `gratify_reset_backup_20261011`: salinan 60 tabel auth/public/storage beserta manifest. RLS aktif pada tabel cadangan; PUBLIC, anon, authenticated dan service_role tidak mendapat akses schema/tabel. Verifikasi sebelum reset menunjukkan tidak ada tabel cadangan yang bisa dibaca client aplikasi.
- Enam file Storage telah dicadangkan dalam `storage-backup/avatars.zip` dan `storage-backup/playlist-covers.zip`; masing-masing berisi 3 file nonkosong, integritas ZIP dan SHA-256 dicatat dalam manifest. File asli dihapus melalui kontrol Storage resmi, bukan dengan SQL langsung ke metadata objek.
- `reset-submitted.sql` cocok dengan rancangan yang direview. Eksekusi berhasil mengembalikan 0 akun, 0 profil dan 0 file; pemeriksaan terpisah kemudian memeriksa seluruh tabel aplikasi serta sesi/refresh token.
- `reset-result.json`, `reset-final-verification-ui.txt`, `backup-verified-ui.txt` dan `reset-complete.png` merekam hasil. Backup disimpan terpisah dari folder APK rilis; jangan membagikan folder artefak/backup sebagai paket distribusi.

Kontrol browser dipulihkan melalui tab baru. Reset diselesaikan lewat dashboard Supabase; pemasangan plugin tambahan tidak diperlukan. Aplikasi lama di ponsel dihentikan sementara agar tidak melakukan sync selama reset; tidak ada perintah uninstall/clear data paket lama yang dijalankan oleh proses ini. Akun/data cloud yang dipakai aplikasi lama ikut direset karena servernya sama. Data offline perangkat lain tidak dapat dihapus dari server.

Cadangan sengaja dipertahankan untuk pemulihan manual bila diperlukan; data pengguna lama tidak tersedia pada tabel/bucket aktif. Token akses JWT yang sudah diterbitkan dapat tetap valid sampai expiry meskipun akun/refresh token telah dihapus; reset tidak dilaporkan sebagai pencabutan instan seluruh JWT. Tidak ada akun uji yang dibuat oleh skrip audit. Akun dan file baru yang terdaftar setelah reset merupakan keadaan berikutnya, terpisah dari bukti 0 data tepat setelah transaksi reset.

## Batas kesiapan

Perubahan ini hanya mengubah nama resource aplikasi dan mengosongkan data lokal paket baru. Pengujian playback kandidat sebelum rename terdapat dalam [laporan sebelumnya](GRATIFY_NEXT_2026-10-11.md). Tidak ada pengukuran RAM/frame panjang baru setelah rename. Jank 66,05% dari sampel audit lama, kekurangan uji perangkat fisik RAM 2–4 GB, serta review OAuth Google yang masih berjalan tetap menjadi batas rilis stabil.
