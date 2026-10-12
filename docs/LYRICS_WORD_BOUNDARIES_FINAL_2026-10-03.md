# Perbaikan akhir batas kata lirik — 3 Oktober 2026

Lirik dengan timestamp per suku kata dirapikan menggunakan teks synced/plain yang cocok dari respons untuk versi lagu yang sama. Potongan tetap mengikuti timing aslinya; kata dapat dibungkus sebagai satu kelompok sehingga tidak terpisah di tengah kata. Lirik yang sudah berupa kata utuh tetap dipertahankan.

Perbaikan lanjutan memastikan referensi hanya menentukan batas kata: huruf dan kapitalisasi tetap berasal dari rich-sync asli. Contohnya, `Adam` tetap `Adam` meskipun teks referensi penyedia menulis `adam`. Pola pemrosesan spasi juga dipakai ulang sehingga tidak membuat regex baru untuk setiap baris.

Perubahan tidak menukar ID lagu, memindahkan timing dari versi audio lain, atau menambah permintaan jaringan aplikasi. Efek berjalan/glow dan waktu suku kata tetap dipakai. Mode standar dan teks share menerima batas kata yang sama. Jika urutan karakter referensi tidak cocok atau referensi tidak ada, aplikasi mempertahankan teks sumber tanpa menebak penggabungan kata.

Cache lama tetap bisa dipakai ketika offline; hasil baru yang berhasil diambil dapat menggantikannya. Perubahan akan terlihat pada APK setelah pengguna meminta build berikutnya, bukan pada APK lama yang sudah terpasang.

## Verifikasi akhir

**43 pemeriksaan JVM lulus**, tidak gagal atau dilewati:

| Pemeriksaan | Jumlah |
|---|---:|
| Parser LRC/TTML dan pencocokan referensi | 7 |
| Pemetaan respons dan kompatibilitas cache | 6 |
| Empat versi Mangu dari penyedia langsung | 1 |
| Pemrosesan respons penuh dan benchmark | 1 |
| Batas kata, kapitalisasi, idempotensi, dan teks share | 8 |
| Render Gratify pada dua lebar layar | 1 |
| Renderer asli SimpMusic dengan data kata utuh | 1 |
| Cache dan urutan fallback | 8 |
| Player: animasi, loading, tab, seek dan pause/resume | 10 |

Keempat ID `JENpTmMQBQY`, `fUdfDKyrbjk`, `McIPitKTfw4`, dan `WNGbZRb3czc` menghasilkan kata utuh setelah pemrosesan dan mempertahankan semua waktu aslinya. Perbedaan kapitalisasi pada referensi `fUdfDKyrbjk` kini tidak mengubah rich-sync asli.

Pengukuran ulang pemrosesan 33 baris Mangu pada komputer: median **1,047 ms**, P95 **2,270 ms**, maksimum **3,351 ms**, 300 sampel setelah 50 pasangan pemanasan. Ini pekerjaan sekali saat respons diterima, bukan biaya setiap frame. Angka tersebut tidak mengukur FPS/baterai pada Android.

Bukti terbaru tersimpan pada `artifacts/lyrics-boundaries-final-2026-10-03/`: `verification.log`, `validation.json`, `processing.json`, dan `live-video-variants.json`. Pengukuran render sebelumnya dijelaskan pada [laporan performa](D:/Tan/Gratify/docs/LYRICS_PERFORMANCE_COMPARISON_2026-10-03.md); algoritma render tidak diubah dalam perbaikan kapitalisasi ini.

Tidak membuat APK debug, memasang APK, atau menjalankan aplikasi di perangkat. APK universal lama tetap memiliki SHA-256 `817fd0fa7c8f2ef97cd46886ab4d342224792b4066e6350790bf34d05c802126`.

## Perbaikan tambahan parser dan fallback

Penanda penyanyi seperti `v1:` dan `v12:` kini dihapus jika berada tepat sebelum timestamp kata, mengikuti format penyedia SimpMusic. Penanda itu sebelumnya dapat menghalangi pencocokan dengan referensi kata utuh. Teks literal serupa di dalam lirik tetap dipertahankan.

Respons rich-sync kosong, rusak, atau hanya berisi timestamp kini tidak menahan lirik synced/plain yang valid dalam respons yang sama. Cache dan hasil penyedia yang hanya berisi timestamp juga tidak dianggap sebagai lirik yang dapat dibaca, sehingga urutan fallback yang sudah ada dapat dilanjutkan. Tidak ada penyedia baru atau perubahan waktu lagu dalam perbaikan ini.

Verifikasi terbaru: **62 pemeriksaan JVM lulus**, tanpa kegagalan, error, atau pemeriksaan yang dilewati:

| Pemeriksaan | Jumlah |
|---|---:|
| Parser LRC/TTML, batas kata, dan penanda penyanyi | 9 |
| Pemetaan respons, cache, dan fallback dalam respons | 9 |
| Empat versi Mangu dari penyedia langsung | 1 |
| Cache dan urutan fallback penyedia | 10 |
| Batas kata dan teks yang dirender | 8 |
| Render Gratify pada dua lebar layar | 1 |
| Player: animasi, loading, seek dan pause/resume | 10 |
| Share lirik | 9 |
| Pratinjau lirik story | 5 |

Bukti pemeriksaan terbaru: `artifacts/lyrics-parser-fallback-final-2026-10-03/verification.log`, `validation.json`, dan `live-video-variants.json`. Hasil ini mencakup pengujian di komputer dan permintaan langsung ke penyedia, belum pengujian aplikasi di perangkat Android. APK lama tidak berubah; belum membuat atau memasang APK debug.
