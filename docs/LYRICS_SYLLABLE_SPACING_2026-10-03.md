# Perbaikan suku kata lirik — 3 Oktober 2026

Kasus: screenshot Mangu — Fourtwnty menampilkan `Su a tu ma lam A dam ber ce ri ta` dan memindahkan sebagian kata ke baris berikutnya.

## Penyebab yang terverifikasi

Respons [penyedia SimpMusic untuk video JENpTmMQBQY](https://api-lyrics.simpmusic.org/v1/JENpTmMQBQY) menyertakan spasi antara setiap suku kata dalam rich-sync. Respons yang sama menyediakan teks baris tersinkron dan teks biasa dengan batas kata yang benar. Perbaikan terdahulu hanya mempertahankan spasi dari sumber, sehingga spasi tambahan dari penyedia tetap dianggap batas kata.

## Perbaikan

- Cocokkan rich-sync dengan baris synced/plain yang memiliki urutan karakter sama, dengan mengabaikan spasi dan kapitalisasi saat pencocokan. Untuk baris berulang, pilih referensi tersinkron dengan waktu terdekat.
- Pulihkan spasi dari referensi tersebut tanpa mengubah satu pun timestamp suku kata. Teks yang sudah benar dipertahankan; perbaikan dapat dijalankan kembali tanpa mengubah hasilnya.
- Renderer menerima kelompok kata utuh, tetapi tetap menggunakan waktu tiap suku kata untuk glow dan gerakan. Mode standar dan teks share memakai teks hasil perbaikan yang sama.
- Simpan versi pemrosesan pada JSON baris lirik yang sudah ada. JSON cache lama tetap bisa dibaca tanpa perubahan skema database. Cache rich lama tidak lagi menahan hasil line-sync baru; cache rich yang telah diproses tetap diprioritaskan dibanding fallback line-sync.
- Tidak menambah permintaan jaringan: referensi berasal dari respons penyedia yang sama. Jika tidak ada referensi yang cocok, teks dipertahankan tanpa menebak batas kata. Cache lama dapat tetap terlihat ketika offline sampai pengambilan baru berhasil.

## Verifikasi

37 pemeriksaan JVM lulus, tanpa gagal atau dilewati:

| Pemeriksaan | Jumlah |
|---|---:|
| Parser LRC/TTML dan pencocokan referensi | 7 |
| Pemetaan respons dan kompatibilitas JSON cache | 5 |
| Permintaan Mangu menggunakan klien produksi | 1 |
| Batas kata dan waktu suku kata | 6 |
| Rendering komponen aplikasi pada lebar 320 dan 390 | 1 |
| Fallback dan prioritas cache | 8 |
| Share lirik | 9 |

Kasus Mangu menghasilkan empat kata `Suatu malam Adam bercerita`, dengan 11 timestamp suku kata asli dan awal baris 22.960 detik. Pemeriksaan render memastikan suku kata dalam satu kata tidak berpindah ke baris berbeda. Pratinjau komponen tersimpan pada `artifacts/lyrics-spacing/mangu-320.png` dan `mangu-390.png`; ini bukan tangkapan layar Android.

Log akhir: `artifacts/lyrics-spacing/verification-final.log`. Hasil permintaan: `artifacts/lyrics-spacing/provider-check.json`. Tidak menyimpan respons lengkap atau identitas kontributor penyedia.

Sesuai permintaan pengguna, tidak membuat APK debug, memasang APK, atau menjalankan aplikasi di perangkat. SHA-256 APK universal yang sudah ada tetap `817fd0fa7c8f2ef97cd46886ab4d342224792b4066e6350790bf34d05c802126`. Perbaikan akan masuk saat build berikutnya diminta. Kompilasi dan rendering yang diverifikasi pada pekerjaan ini menggunakan target JVM; pengujian Android belum dilakukan.
