# Audit lembar berulang — 12 Oktober 2026

## Penyebab yang direproduksi

Pada lembar anggota/undangan, callback `confirmValueChange` menangkap seluruh
`PlaylistWorkspaceState`. Callback berubah saat loading, link siap, pencabutan
selesai, atau polling menghasilkan state baru. Material3 memasukkan callback
tersebut ke input `rememberSaveable`: identitas callback yang berubah membuat
`SheetState` baru, berawal dari Hidden. Efek `LaunchedEffect(sheetState)` kemudian
menjalankan animasi tampil lagi. Jadi satu lembar dapat terlihat muncul berturut-turut.

Tes `changingBusyCallbackReproducesMaterialSheetReset` mereproduksi pergantian
identitas tersebut menggunakan Material3 yang dipakai proyek
(`material3-desktop:1.12.0-alpha03`). Bytecode dependensi lokal juga diperiksa.
Rujukan primer: [SheetDefaults.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SheetDefaults.kt)
dan [ModalBottomSheet.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ModalBottomSheet.kt).

## Perbaikan dan cakupan

`rememberBusyModalSheetState` mempertahankan identitas callback dan SheetState,
sementara `rememberUpdatedState` menyediakan status proses terbaru untuk menolak
penutupan saat menyimpan. Sesudah proses selesai, tutup, swipe dan Kembali dapat
digunakan lagi. Perlindungan ditempatkan pada transisi lembar, bukan hanya callback
setelah animasi penutupan telanjur selesai.

| Lembar | Temuan/perbaikan |
|---|---|
| Anggota dan undangan kolaboratif/Blend | Callback berubah karena keseluruhan state; dibuat stabil |
| Editor playlist bersama | Callback berubah karena busy/pemilih foto; dibuat stabil |
| Pemilih sampul | Callback berubah karena busy; dibuat stabil |
| Editor playlist pribadi | Callback berubah karena saving; dibuat stabil |
| Menu lagu workspace | Penutupan ditolak sejak transisi saat proses berjalan |
| Menu playlist/folder workspace | Perlindungan penutupan diselaraskan dengan busy |
| Pindahkan ke folder | Perlindungan penutupan selama pemindahan |
| Tambah lagu/playlist ke folder | Perlindungan penutupan selama penyimpanan |
| Tambahkan playlist ke profil sendiri | Perlindungan saat penyimpanan; state dipisahkan dari menu playlist yang dipajang |
| Simpan playlist dari profil orang lain | Perlindungan saat penyimpanan |
| Bagikan/simpan gambar lirik | Perlindungan saat ekspor/share |

Pencarian pola dilakukan pada kode UI commonMain, termasuk lembar player, pustaka,
profil, teman dan lirik. Lembar yang memiliki callback tetap tanpa kondisi proses
tidak diubah semata-mata karena menggunakan ModalBottomSheet. Lembar sampul yang
dibuka dari editor tetap merupakan alur bertingkat yang disengaja.

Perintah membuat link saat membuka panel langsung ditandai sudah dipakai pada
induk halaman. Bila detail sementara gagal dimuat lalu pulih, panel tidak
mengulang perintah itu atau membuat link baru setelah pencabutan. Skenario
gagal-baca/pulih tersebut disertakan dalam tes panel anggota.

## Temuan tambahan pada feedback

Refresh otomatis sebelumnya mempertahankan error pembacaan lama sekalipun data
berikutnya berhasil dimuat. Perbaikan hanya membersihkan error dari pembacaan
yang sudah pulih; error kegagalan menyimpan tetap dipertahankan.

Pemetaan error sebelumnya menganggap semua pesan yang mengandung `expired`
sebagai undangan kedaluwarsa. Sekarang hanya error undangan yang diberi pesan itu;
JWT kedaluwarsa diberi pesan sesi akun. Ini memperbaiki klasifikasi, bukan klaim
bahwa penyebab setiap error yang pernah tampil sudah diketahui.

## Bukti verifikasi

Artefak revisi berada di `artifacts/sheet-stability-20261012/`.
Tes mencakup identitas/visibilitas lembar saat busy dan hasil berubah, pencegahan
penutupan selama busy, penutupan setelah selesai, undangan awal tepat satu kali,
pencabutan tepat satu kali, tidak membuat link baru saat polling, pemulihan error
pembacaan, serta mempertahankan kegagalan menyimpan. Tes UI playlist, profil dan
lirik yang terkait ikut dijalankan.

Pengujian ponsel menggunakan playlist sintetis `GRATIFY_SHEET_AUDIT_20261012`.
Tidak ada undangan dikirim ke pengguna nyata; koleksi pengguna tidak dipakai
untuk menguji pencabutan. Hasil akhir build, tes dan cleanup dicatat dalam
`verification.json`.

### Hasil akhir

- Build debug terakhir berhasil dan terpasang sebagai Gratify Dev
  (`com.tan.gratify.next.dev`, versi 2.1.3-dev/64, arm64-v8a).
- Delapan suite pengujian: **50 tes lulus**, tanpa kegagalan atau error.
- Enam pemeriksaan perangkat lulus: undangan siap, pencabutan kembali ke anggota,
  tetap tercabut sesudah polling, tidak ada error pembacaan lama, pemilih sampul
  kembali ke editor, dan pembersihan playlist uji dengan koleksi awal tetap utuh.
- Playlist `GRATIFY_SHEET_AUDIT_20261012` sudah dihapus melalui aplikasi.
  Lima koleksi pengguna sebelum pengujian tetap ada. Tidak ada undangan yang
  dikirim kepada pengguna nyata.
- Log proses terakhir tidak mencatat `FATAL EXCEPTION`, `OutOfMemoryError`, atau
  ANR selama pemeriksaan. Ini bukan audit kebocoran RAM atau uji perangkat RAM rendah.

APK debug: `artifacts/sheet-stability-20261012/Gratify-sheet-stability-debug-arm64.apk`.
SHA-256: `5e6bc8fe11f2468cd11d718729ae51a890813a91dcbf19c10648e12eef510602`.

Pemulihan setelah gagal-baca diuji dengan gateway simulasi dan ViewModel/UI
produksi; jaringan seluruh ponsel tidak dimatikan. Seluruh 11 lembar memiliki
perlindungan yang sama dan tes UI terkait dijalankan, tetapi tidak semuanya
diuji satu per satu di ponsel. APK rilis utama, struktur database, dan konfigurasi
server tidak diubah pada revisi ini.
