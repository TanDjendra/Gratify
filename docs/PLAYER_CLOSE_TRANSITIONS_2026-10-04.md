# Perbaikan transisi tutup Now Playing dan lirik

Tanggal: 4 Oktober 2026

## Temuan

- Now Playing menunggu `sheetState.hide()` sebelum dilepas, tetapi tombol tutup dan navigasi dari lirik fullscreen langsung memanggil callback yang menghapus panel.
- Now Playing memakai lapisan hitam penuh di belakang sheet; lirik memakai scrim bawaan Material. Perbedaan ini membuat layar sebelumnya muncul secara berbeda saat panel ditutup.
- Panel player di tablet landscape mengecilkan lebar selama transisi keluar, sehingga tata letak di dalam player ikut berubah.

## Perbaikan

- `PlayerModalSheet` dipakai bersama oleh Now Playing dan lirik fullscreen. Keduanya memakai sheet penuh, bentuk, warna permukaan, scrim, dan gerakan Material dari tema Gratify yang sama.
- Tombol tutup menunggu animasi hide selesai. Isi player tetap terpasang selama panel bergerak keluar.
- Callback dari Back, tap di luar panel, dan swipe diterima setelah Material menyembunyikan sheet; tidak ditambahkan animasi kedua.
- `PlayerSheetDismissal` menahan permintaan tutup berulang dan memastikan callback pelepasan hanya berjalan sekali. Jika animasi terputus atau hide belum benar-benar selesai, panel tetap bisa ditutup lagi.
- Menu pada lirik fullscreen menggunakan callback penutupan yang sama ketika membuka halaman lain.
- Tablet landscape memakai geser ke kanan dan fade selama 240 ms, tanpa mengecilkan isi player selama animasi.
- Penutupan panel tidak mengirim perintah stop/pause ke pemutar musik.

## Verifikasi

`BUILD SUCCESSFUL` untuk pengujian JVM dan `:androidApp:compileReleaseKotlin` dengan konfigurasi Full.

| Pengujian | Jumlah | Hasil |
| --- | ---: | --- |
| PlayerSheetTransitionTest | 6 | Lulus |
| AppleMusicPlayerTest | 10 | Lulus |

Pengujian baru memeriksa callback setelah hide, permintaan berulang, animasi yang dibatalkan, hide yang belum selesai, dan isi panel yang tetap terpasang hingga animasi penutupan selesai pada ukuran 390×844 serta 900×420. Pengujian player yang sudah ada memeriksa kontrol playback, pergantian tab, seek, lyrics fullscreen, mode standar, love, dan tampilan kata.

Bukti tersimpan di `artifacts/player-close-transitions-2026-10-04/verification.log` dan `test-results.json`.

## Batas verifikasi

Belum diuji langsung di perangkat Android, termasuk gestur predictive Back dan kelancaran frame di HP. Tidak ada APK yang dibuild, ditandatangani, dipasang, atau diterbitkan; APK yang sudah tersedia belum berisi perbaikan ini.

## Acuan implementasi

[Kode resmi ModalBottomSheet Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ModalBottomSheet.kt) digunakan untuk memeriksa urutan hide dan callback dismissal. Tidak ada dependency baru.
