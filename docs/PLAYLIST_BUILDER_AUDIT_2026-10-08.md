# Audit dan konsep baru pembuat playlist — 8 Oktober 2026

## Hasil

Pustaka langsung membuka satu ruang penyusun playlist. Pengguna bisa memilih lagu dahulu atau memberi nama dahulu. Tab **Pilih lagu** berisi pencarian dan lagu terakhir didengarkan; tab **Susun** berisi semua pilihan beserta kontrol naik, turun, dan hapus. Nama tetap dapat diedit. Lagu pertama menentukan sampul, dan sampul itu ikut disimpan bersama urutan lagu. Playlist kosong dengan nama tetap dapat dibuat.

## Perubahan konsep dan alur

- Form nama terpisah dihapus. Kedua tombol buat di Pustaka membuka editor yang sama, dengan perlindungan terhadap navigasi ganda.
- Pratinjau identitas menggunakan nama draft dan gambar lagu pertama yang dipilih, dengan ikon musik saat gambar tidak tersedia.
- Pencarian tidak lagi terdesak oleh daftar pilihan. Tab tetap terlihat ketika daftar digulir. Tab Susun menampilkan posisi lagu, jumlah pilihan, dan kontrol urutan dengan target ketuk 48 dp serta label untuk pembaca layar. Batas awal/akhir tidak dapat digeser melewati daftar.
- Tab Susun kosong memberikan jalan kembali ke pemilihan lagu. Nama kosong memberikan petunjuk bahwa lagu boleh dipilih terlebih dahulu; nama tetap diperlukan sebelum menyimpan.
- Urutan yang diedit dipulihkan bersama nama, query, dan pilihan melalui SavedStateHandle. Penyimpanan menggunakan snapshot dengan urutan yang sama.
- Tombol simpan mengikuti keyboard, konten desktop dibatasi 760 dp, dan semua teks baru tersedia dalam bahasa Indonesia dan Inggris.

## Perbaikan audit yang dipertahankan

Pencarian dari input memakai jeda 300 ms, membatalkan query lama, menolak respons yang tidak sesuai, dan membedakan loading, kosong, gagal, serta coba lagi. Riwayat hanya tampil saat pencarian kosong. ID kosong dan duplikat disaring.

Nama, pencarian, pilihan, dan urutan terkunci selama menyimpan. Kegagalan mempertahankan draft. Nama dibatasi 100 karakter dan dirapikan sebelum disimpan. Meninggalkan draft meminta konfirmasi.

Draft terikat pemilik Pustaka. Pergantian akun membersihkan draft dan meminta kembali ke Pustaka. Pemilik yang ditangkap sebelum simpan diperiksa kembali di transaksi database. Penyimpanan yang selesai saat akses akun sementara loading ditahan sampai akun yang sama siap, tanpa membuat penyimpanan kedua.

Transaksi lokal menyimpan playlist, lagu, dan urutannya bersama serta melakukan rollback bila penulisan gagal. Sesudah sukses, detail playlist terbuka dan tombol kembali menuju Pustaka. Tidak ada perubahan skema database atau pustaka baru.

## Bukti validasi

- **41 pengujian terarah lulus**: 6 audit ViewModel penyusun, 3 pemulihan draft, 4 render penyusun, 3 render Pustaka, 13 navigasi, 5 identitas/transaksi playlist, dan 7 akses playlist.
- Kasus konsep baru mencakup pemilihan sebelum nama, validasi nama sebelum simpan, pemulihan urutan setelah draft dibuat ulang, penyimpanan urutan serta sampul lagu pertama, batas kontrol urutan, dan pembekuan urutan saat simpan. Navigasi ganda dari Pustaka tidak menambah editor kedua.
- Render serta interaksi diuji pada 390×900, 320×560 dengan tulisan 150%, dan desktop 1100×900. Daftar dapat digulir melewati area simpan; tab tetap tersedia saat menggulir.
- Build Android debug dan kompilasi desktop berhasil. [Log validasi](../artifacts/playlist-composer-2026-10-08/validation-final.log).
- Smoke test Android 17/API 37 dengan halaman memori 16 KB berhasil menggunakan screen dan ViewModel produksi: pilih dua lagu tanpa nama, ubah urutan, beri nama, tombol simpan terjangkau di atas keyboard, lalu navigasi ke hasil simpan. Kegagalan sintetis mempertahankan nama dan pilihan. Buffer crash tidak berisi laporan crash selama pemeriksaan.
- Pratinjau: [susunan Android](../artifacts/playlist-composer-2026-10-08/android-composer-id.png), [keyboard](../artifacts/playlist-composer-2026-10-08/android-keyboard-id.png), [gagal simpan](../artifacts/playlist-composer-2026-10-08/android-save-error-id.png), [layar kecil dengan teks besar](../artifacts/playlist-composer-2026-10-08/arrange-320-560.png), [desktop](../artifacts/playlist-composer-2026-10-08/arrange-1100-900.png).
- Keempat varian APK debug diperbarui **8 Oktober 2026, 14.19 WIB**. Keberadaan sumber daya konsep baru di setiap APK dan SHA-256 dicatat pada [verifikasi APK](../artifacts/playlist-composer-2026-10-08/apk-verification.json).

Bukti audit sebelumnya tetap berada di `artifacts/playlist-builder-audit-2026-10-08`.

Fixture Android menggunakan screen dan ViewModel produksi dengan repository serta lagu sintetis. Playlist contoh tidak disimpan ke akun produksi. Sinkronisasi cloud lintas perangkat pada rilis yang memuat perubahan ini belum diuji dalam audit ini.
