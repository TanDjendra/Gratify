# Gratify 2.1.3 — pemeriksaan APK dan ponsel

Tanggal: 12 Oktober 2026. Perangkat: vivo I2218, Android 16, RAM 12 GB.
Paket pengujian: `com.tan.gratify.next.dev`, versi `2.1.3-dev`, kode 64.
Artefak dan bukti: `artifacts/playlist-workspaces-20261012/device/`.

## Temuan yang diperbaiki selama uji

1. Tombol `+` pada kepala Pustaka masih langsung membuka playlist pribadi.
   Sekarang tombol tersebut membuka pilihan Playlist, Playlist kolaboratif,
   Blend, dan Folder. Menu diperiksa kembali pada APK debug terbaru.
2. Setelah penghapusan folder, navigasi dapat kembali ke folder yang sudah
   dihapus. Penghapusan sekarang membuka akar Pustaka. Penghapusan subfolder
   dan folder induk diuji kembali melalui layar aplikasi; keduanya berhasil.
3. Pemutaran playlist bersama memakai mode satu lagu sehingga rekomendasi
   mengganti antrean yang telah disiapkan. Pemanggil sekarang memakai mode
   playlist dan indeks lagu yang dipilih. Pada APK debug terbaru, Keeping Tabs
   diikuti Backburner ketika tombol Next ditekan, sesuai urutan playlist.

## Pemeriksaan melalui aplikasi

| Alur | Hasil |
| --- | --- |
| Menu empat pilihan melalui `+` | Lulus pada debug terbaru |
| Membuat playlist kolaboratif | Lulus; privat, satu pemilik |
| Pilih foto, geser crop, ekspor persegi | Lulus; tidak ada bar zoom |
| Simpan sampul ke Storage dan buka kembali | Lulus; JPEG 10.355 byte, tetap tampil setelah aplikasi dibuka ulang |
| Playlist publik tampil di Search lalu dijadikan privat | Lulus; hilang dari pencarian publik dan tetap dapat dibuka pemilik |
| Link undangan domain gratify.web.id | Lulus format dan pratinjau dalam debug dengan paket tujuan eksplisit |
| App Links pada APK rilis | Android melaporkan gratify.web.id verified; link dibuka tanpa menentukan paket dan masuk ke Gratify rilis; token uji tidak valid ditolak |
| Blend dengan persetujuan pengguna | Lulus; satu anggota mendapat petunjuk mengundang teman |
| Folder bertingkat dan pemindahan playlist | Lulus; pilihan tujuan mencegah pemindahan induk ke turunannya |
| Hapus folder mempertahankan isi | Lulus; isi berpindah ke induk/akar |
| Penghapusan folder kembali ke Pustaka | Lulus pada debug terbaru |
| Tambah dua lagu dan ubah urutan | Lulus melalui aplikasi |
| Play, seek, dan Next | Lulus; MediaSession bermain tanpa error, seek berpindah, Next sesuai antrean pada debug terbaru |

Undangan hanya dipratinjau oleh pemilik; tidak ada pesan yang dikirim ke pengguna
lain. Uji memakai playlist/folder khusus berawalan `GRATIFY_..._20261012`.
Playlist pribadi pengguna dan sesi login dipertahankan.

## Pemeriksaan otomatis

- Build debug terbaru dan 22 tes JVM terpilih lulus, tanpa kegagalan/error:
  ViewModel 9, tampilan playlist 5, jalur folder 3, link 2, tampilan Koleksi 3.
- Codec crop produksi diuji pada ponsel: decode terbatas, ekspor JPEG 512 persegi,
  delapan orientasi EXIF pada decoder modern/legacy, latar putih untuk gambar
  transparan, dan penolakan input yang tidak tersedia. Seluruhnya lulus.
- Sebelum perbaikan navigasi/antrean, sepuluh siklus buka-tutup sampul pada proses
  yang sama lulus. PSS siklus 1/5/10: 359,95 / 365,48 / 350,90 MiB. Uji ini
  tidak menunjukkan pertumbuhan berkelanjutan; pengulangan pada APK debug akhir
  dicatat di `device/cycle-summary.json`: PSS siklus 1/5/10 adalah
  347,73 / 356,30 / 338,17 MiB, proses tetap sama dan sampul tetap tampil.
  Log proses debug akhir tidak memuat crash, ANR, atau OutOfMemoryError.
- Pemeriksaan server sebelumnya: 68 pemeriksaan database lokal, 10 pemeriksaan
  produksi dasar, dan 9 pemeriksaan ekstensi produksi dalam transaksi rollback.
  Ini melengkapi uji ponsel; bukan pengganti pengujian dua pengguna nyata.

## Batas hasil

Hasil RAM hanya berlaku untuk skenario singkat pada perangkat 12 GB. Perangkat
2–4 GB, pemutaran panjang, dan semua kemungkinan kebocoran memori belum teruji.
Gerakan pinch dua jari belum dikonfirmasi pengguna; geser crop dan hasil ekspor
telah diperiksa. Foto profil asli pengguna tidak diganti dalam pengujian ini.
Penerimaan undangan oleh dua akun nyata dan kualitas campuran Blend dua pengguna
masih perlu uji pemakaian bersama. Temuan server lama pada laporan implementasi
tetap merupakan pekerjaan audit terpisah.

## Hasil akhir rilis

Build rilis 2.1.3/kode 64 lulus, termasuk pemeriksaan lint vital. APK arm64 dan
universal ditandatangani dengan sertifikat rilis yang sama dengan aplikasi
terpasang, diperiksa tanda tangan dan alignment 16 KiB, serta dipastikan tidak
debuggable. APK arm64 berhasil memperbarui versi 2.1.2 menjadi 2.1.3 tanpa
menghapus data/login. Menu empat pilihan, playlist privat, sampul khusus, dan
urutan lagu dibuka pada rilis. Log proses rilis tidak memuat crash selama uji.

Android melaporkan `gratify.web.id: verified`. Membuka link undangan melalui
Intent browser tanpa menentukan paket masuk ke aplikasi rilis dan menampilkan
penolakan untuk token uji tidak valid. Penerimaan link oleh akun teman nyata
masih termasuk batas hasil di atas.

Semua playlist/folder/undangan uji dan objek sampul uji di server telah terhapus
melalui aplikasi. Gambar sintetis dan entri MediaStore-nya di ponsel juga
dibersihkan. Playlist pengguna `cihuy` tetap terlihat di Pustaka. Debug tetap
terpasang berdampingan; data aplikasinya tidak dikosongkan.

Status, hash APK, hasil RAM, dan pembersihan dicatat pada
`artifacts/playlist-workspaces-20261012/device-verification.json`.
APK tidak dipublikasikan ke situs/Play Store melalui pekerjaan ini.
