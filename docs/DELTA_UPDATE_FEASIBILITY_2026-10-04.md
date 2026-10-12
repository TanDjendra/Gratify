# Uji kelayakan delta update Gratify — 4 Oktober 2026

Status: percobaan lokal selesai. Updater produksi tidak diubah. Tidak melakukan build aplikasi, penandatanganan, publikasi, atau pemasangan APK. Percobaan merekonstruksi salinan sementara APK yang sudah ada untuk memeriksa hasil, lalu membandingkan hash.

## Hasil terukur

Membandingkan dua APK release yang sudah tersedia: APK GitHub `v2.1.0/Gratify.apk` dan APK release lokal yang ditandatangani pada 2 Oktober. Algoritma BSDIFF4 melalui `bsdiff4 1.2.6` dipasang hanya di direktori percobaan, bukan dependency aplikasi atau paket Python global.

| Ukuran/perilaku | Hasil |
| --- | --- |
| APK sumber GitHub | 52.105.071 byte / 52,11 MB |
| APK tujuan lokal | 52.868.453 byte / 52,87 MB |
| Patch yang perlu diunduh | 10.307.820 byte / 10,31 MB |
| Penghematan dibanding APK tujuan lengkap | 42.560.633 byte / 80,50% |
| Pembuatan patch di komputer | 25,14 detik |
| Penerapan patch di komputer | 0,61 detik |
| Hasil rekonstruksi | Identik: SHA-256 `bccc1576d15a2e4ab45e15c43a9c84e7e1fa43caefc28546f64b9eeb2d848557` |

MB pada tabel berarti 1.000.000 byte. Waktu penerapan mengukur fungsi file_patch beserta pembacaan/penulisan berkas, bukan proses instalasi Android atau verifikasi akhir.

Kesimpulan percobaan: penghematan unduhan memungkinkan. Ukuran patch tidak sama dengan tambahan ukuran aplikasi. Dalam pasangan ini, pertumbuhan APK hanya sekitar 0,76 MB, sedangkan patch berukuran 10,31 MB karena perubahan isi dan kompresi memengaruhi banyak byte. Setiap pasangan rilis harus diukur; tidak ada janji bahwa tambahan fitur 3 MB menghasilkan patch persis 3 MB.

## Batas hasil ini

- Ini uji Windows/Python, bukan pengujian pada HP atau implementasi Android.
- Pembuatan patch mencapai puncak working set proses sekitar 519,36 MiB; penerapan mencapai sekitar 223,16 MiB. Angka mencakup proses Python/library. Pembuatan patch nantinya dilakukan di komputer pengelola/server, bukan di HP pengguna. Applier Android sebaiknya membaca bertahap dari berkas dan diuji batas RAM-nya, tidak menyalin pendekatan seluruh-buffer Python begitu saja.
- Kedua APK memakai versionCode 61 dan sertifikat berbeda. Pasangan ini tidak bisa dipasang sebagai pembaruan di tempat. Keberhasilan rekonstruksi biner tidak mengatasi syarat versionCode, channel atau tanda tangan Android.
- Keystore APK GitHub lama telah dinyatakan hilang. Pengguna APK tersebut tetap memerlukan migrasi terencana sebelum mengikuti jalur update baru.
- Aplikasi yang sudah terpasang harus memiliki dukungan delta sebelum bisa memilih dan menerapkan patch. Updater yang kini disiapkan masih memakai APK lengkap.
- Tidak ada klaim penghematan yang sama untuk setiap rilis, kestabilan di Android, atau instalasi otomatis setelah unduhan.

## Rekomendasi implementasi untuk dipilih pengguna

Tambahkan pilihan pembaruan hemat kuota dengan alur:

1. Cocokkan APK terpasang dengan versi, package, channel, ukuran, dan hash sumber patch. Android split APK atau sumber yang tidak persis cocok menggunakan jalur APK lengkap.
2. Pilih patch hanya bila tersedia dan lebih kecil secara berarti daripada APK lengkap. Manifest memuat hash APK lama, hash/ukuran patch, serta hash/ukuran APK hasil.
3. Unduh patch dengan progres dan pemulihan seperti updater sekarang.
4. Rekonstruksi ke berkas sementara terpisah memakai applier yang membaca bertahap, dengan batas ukuran keluaran, pemeriksaan ruang disk, timeout dan pembatalan. APK terpasang tidak dimodifikasi.
5. Periksa SHA-256 APK hasil, package, channel, versi dan signer menggunakan verifier yang sudah ada. Berkas patch tidak dieksekusi sebagai kode langsung dalam proses Gratify.
6. Sediakan tombol Pasang dan konfirmasi installer Android seperti sekarang.
7. Bila sumber tidak cocok, patch rusak atau penerapan gagal, tawarkan APK lengkap beserta ukurannya. Jangan otomatis menghabiskan kuota tambahan tanpa pilihan pengguna.

Rilis membutuhkan APK lengkap sebagai cadangan serta patch untuk versi sumber yang dipilih, misalnya satu atau dua rilis sebelumnya. Arsip APK lama harus dipertahankan untuk membuat patch. Dukungan pembuatan/penyiapan patch dapat disiapkan tanpa publikasi, tetapi pengujian kompatibilitas pada HP memerlukan dua build rilis yang benar-benar bisa saling memperbarui, dengan package/channel/signer yang kompatibel dan code versi meningkat.

Pada 4 Oktober, pengguna kemudian mengizinkan audit menyeluruh dan pemasangan jika hasilnya positif. Dukungan telah ditambahkan setelah audit dan uji applier streaming. Lihat [audit dan implementasi lanjutan](DELTA_UPDATE_AUDIT_2026-10-04.md). Angka Python di laporan ini tetap merupakan eksperimen awal, bukan profil memori implementasi Android baru.

## Bukti dan referensi

- `artifacts/delta-feasibility-2026-10-04/result.json`: hasil ukuran, waktu, working set, hash dan batas eksperimen.
- `artifacts/delta-feasibility-2026-10-04/benchmark.py`: percobaan dengan batas waktu dan validasi hash sumber/tujuan.
- `artifacts/delta-feasibility-2026-10-04/experiment-only.bsdiff`: patch percobaan, bukan aset rilis yang boleh dipasang.
- [bsdiff4: format dan fungsi diff/patch](https://github.com/ilanschnell/bsdiff4).
- [Google archive-patcher: rekonstruksi arsip/APK dan pengaruh kompresi](https://github.com/google/archive-patcher/blob/master/README.md). Repo telah diarsipkan; audit ini tidak memilihnya sebagai dependency produksi.
- [Syarat penandatanganan pembaruan Android](https://developer.android.com/studio/publish/app-signing).
