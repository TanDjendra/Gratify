# Audit dan implementasi pembaruan delta Gratify

Tanggal: 4 Oktober 2026. Otorisasi pengguna: audit dari segala sisi, pasang bila hasilnya positif. Batasan tetap: tidak membuat APK baru, memasang APK pada perangkat, atau memublikasikan rilis.

## Keputusan

**Layak ditambahkan sebagai jalur opsional yang dipilih otomatis hanya ketika syaratnya terpenuhi. Dukungan telah dipasang di kode.** APK lengkap tetap menjadi jalur dasar. Aktivasi di perangkat memerlukan rilis yang membawa updater ini; pengujian pemasangan antar dua rilis kompatibel masih wajib sebelum publikasi.

Percobaan biner menunjukkan penghematan sekitar 80,5%. Mesin Android/JVM yang baru membaca bertahap berhasil merekonstruksi berkas 52,9 MB dengan heap JVM dibatasi 16 MiB. Hasil ini mendukung desain yang jauh lebih hemat memori daripada eksperimen Python awal, tetapi tidak membuktikan penggunaan RAM seluruh aplikasi, konsumsi baterai, atau kelancaran audio di HP.

## Hasil terukur

| Pengukuran | Hasil |
| --- | --- |
| APK sumber | 52.105.071 byte |
| APK tujuan yang sudah ada | 52.868.453 byte |
| Patch `gratify-delta-v1` | 10.297.754 byte |
| Penghematan unduhan | 80,5219% |
| Konversi patch percobaan ke format streaming | 1,084 detik di desktop |
| Rekonstruksi + SHA-256 dengan kode applier produksi | 321 ms di desktop |
| Batas heap pada proses applier | 16.777.216 byte / 16 MiB; proses berhasil |
| Hash APK hasil | Sama persis dengan APK tujuan |

Batas heap merupakan batas pengujian proses JVM kecil, **bukan pengukuran puncak RAM Gratify**. Dua APK eksperimen memiliki versionCode sama dan signer berbeda, sehingga tidak boleh dipakai untuk update yang dipasang. Patch diberi nama `experiment-only`, tidak dimasukkan ke manifest rilis, dan tidak diterbitkan. Berkas sumber unduhan dan APK rekonstruksi sementara dibuang setelah pemeriksaan; APK release/debug asli tidak diubah.

## Penilaian dari berbagai sisi

| Aspek | Temuan dan keputusan |
| --- | --- |
| Kuota | Pilih patch hanya jika sekurangnya 30% lebih kecil dari APK lengkap. Ukur setiap pasangan rilis; tidak menjanjikan setiap update hanya 3 MB. |
| Kecepatan | Transfer lebih kecil dapat mempercepat unduhan. Ada tambahan pembacaan/hash APK lama dan rekonstruksi. Kecepatan nyata tetap bergantung jaringan, CPU, dan penyimpanan HP. |
| RAM | Dua buffer kerja 64 KiB, buffer input/output dan gzip; tidak memuat APK penuh. Uji rekonstruksi nyata berhasil dengan heap 16 MiB. Tidak menambahkan library/native binary ke Android. |
| CPU/baterai | Pembuatan BSDiff yang berat hanya berjalan di komputer penyiap rilis. HP menerapkan instruksi sederhana. Penerapan memiliki batas waktu kooperatif 120 detik dan pemeriksaan pembatalan setiap blok. Dampak termal/audio masih perlu diukur di HP. |
| Respons aplikasi | Hash, penyalinan, dan penerapan berjalan di dispatcher IO. Satu mutex mencegah pekerjaan updater bertabrakan. Menutup dialog tidak menghentikan musik atau membuka pemasang otomatis. |
| Ruang disk | Delta tetap menghasilkan APK lengkap di cache. Pemeriksaan awal menyediakan ukuran APK hasil + dua salinan patch + cadangan 16 MiB. Jalur penuh menyediakan dua APK + cadangan. Installer Android juga dapat memerlukan ruang tambahan dan mempunyai pemeriksaan sendiri. |
| Keamanan unduhan | Patch berasal dari aset rilis GitHub resmi yang sama, ukuran dan SHA-256 cocok. Sumber dicocokkan berdasarkan code, ukuran, hash dan tidak memakai split APK. Hash sumber diperiksa kembali sebelum penerapan. |
| Keamanan parser | Header berversi, ukuran keluaran maksimum 300 MiB, maksimal satu juta instruksi, rentang offset dibatasi, panjang tidak boleh negatif/overflow, setiap instruksi harus menghasilkan data. CRC gzip, kelengkapan dan data tambahan diperiksa; output parsial dihapus saat gagal. Patch tidak dijalankan sebagai kode. |
| Keamanan APK hasil | SHA-256 APK hasil harus cocok dengan APK lengkap resmi. Verifier juga memeriksa package, channel, versionCode meningkat, versionName, dan signer yang sama. Pemasang Android tetap melakukan pemeriksaan dan konfirmasi akhir. |
| Pemulihan | Identitas patch terpilih dan DownloadManager ID disimpan. Unduhan yang masih berjalan tetap dikelola Android. Jika proses berhenti saat penerapan, patch selesai diunduh dapat diterapkan kembali; rekonstruksi parsial tidak dilanjutkan. Lifecycle/process death telah ditinjau melalui kode, belum diuji di perangkat. |
| Kegagalan | Patch yang tidak cocok/tidak tersedia memakai APK lengkap sebelum transfer dimulai. Patch yang sudah diunduh tetapi gagal menawarkan tombol **Unduh APK lengkap**, beserta ukuran APK. Tidak mengunduh cadangan secara otomatis. |
| Data pengguna | Hanya berkas updater dihapus. APK terpasang, database, sesi, playlist, dan lagu unduhan tidak ditulis oleh applier. Pembaruan kompatibel tetap mengikuti pemasang Android biasa. |
| Kompatibilitas | Split APK, versi sumber yang tidak diarsipkan, hash berbeda, atau format tidak didukung menggunakan APK lengkap. Jalur GitHub ini mengikuti dukungan Full universal yang sudah ada; tidak menambahkan distribusi baru untuk FOSS/iOS. |
| Pengelolaan rilis | Penyiap rilis boleh menerima maksimal dua APK sumber lama. Sumber harus lolos pemeriksaan package/channel/ABI/signer dan lebih tua. Generator memiliki timeout 300 detik per sumber; hasil rekonstruksi diverifikasi sebelum metadata dibuat. Patch yang hemat kurang dari 30% dilewati. APK lengkap selalu disediakan. |
| Metadata/rilis rusak | Aset patch yang hilang, hash/ukuran/format tidak valid atau entri yang tidak lengkap diabaikan. Metadata inti APK lengkap yang rusak tetap ditolak. JSON rusak atau tipe field yang salah dapat membuat pemeriksaan rilis gagal sampai penerbit memperbaikinya; tidak menebak metadata keamanan. |
| Dependency/lisensi | Android memakai IO dan gzip bawaan platform. `bsdiff4==1.2.6` berlisensi BSD hanya menjadi alat pada komputer penyiap rilis. Repo Google archive-patcher yang telah diarsipkan tidak dijadikan dependency aplikasi. |
| Skalabilitas | Maksimal dua patch tiap rilis membatasi waktu persiapan dan jumlah aset. Pengguna yang melewati beberapa versi tetap memperoleh APK lengkap. Rilis bertanda prerelease/draft tidak masuk jalur stabil. |
| Keystore hilang | Delta tidak mengatasi signer APK GitHub lama yang berbeda. Pengguna APK lama tetap membutuhkan migrasi dan cadangan data sebelum mengikuti jalur penandatanganan baru. |
| Instalasi | Tidak ada pemasangan diam-diam. Setelah patch direkonstruksi dan diverifikasi, pengguna menekan Pasang dan menyetujui pemasang Android. |

## Perubahan yang dipasang

- Model dan manifest menerima daftar patch sumber, terpisah dari metadata APK lengkap.
- Selektor GitHub memeriksa aset patch dan mempertahankan URL APK lengkap sebagai cadangan.
- Updater Android mencocokkan APK terpasang, menampilkan tahap persiapan/penerapan, menyimpan patch terpilih untuk pemulihan, dan memverifikasi APK hasil melalui verifier yang sudah ada.
- Dialog membedakan ukuran transfer hemat kuota dari ukuran APK akhir. Kegagalan patch menyediakan unduhan penuh hanya setelah pengguna memilih.
- `StreamingDelta.kt` adalah kode yang sama yang dikompilasi untuk Android dan JVM; pengujian JVM memakai implementasi produksi, bukan salinan algoritma.
- `scripts/gratify_delta.py` membuat dan memverifikasi patch di komputer penerbit. `scripts/prepare-update-release.py --base-apk ...` menyiapkan aset dan manifest tanpa build, sign, atau upload.

## Format dan proses persiapan

Format `gratify-delta-v1`: header 32 byte berupa magic ASCII `GRTFDLT1`, ukuran sumber, ukuran hasil, dan jumlah instruksi (integer 64-bit big-endian). Isi berupa satu stream gzip. Setiap instruksi memuat offset sumber absolut, panjang diff, panjang extra, lalu byte diff dan extra. Byte diff dijumlahkan modulo 256 dengan byte sumber; posisi di luar sumber dianggap nol sebagaimana BSDiff. Converter mengubah kontrol BSDIFF40 menjadi bentuk ini sehingga Android tidak membutuhkan decompressor bzip2 atau JNI.

Contoh persiapan **untuk rilis mendatang yang sudah dibangun dan ditandatangani**, bukan perintah yang dijalankan pada APK lama:

```text
python scripts/prepare-update-release.py --apk <APK-Full-baru> --aapt2 <aapt2> --apksigner <apksigner> --expected-certificate <fingerprint-publik> --previous-version-code <code-terakhir> --base-apk <APK-Full-lama-yang-kompatibel> --output <folder-baru>
```

Pasang `bsdiff4==1.2.6` pada environment Python khusus komputer penerbit bila memakai `--base-apk`. Pertahankan arsip APK distribusi yang persis sama, keystore yang sama, dan naikkan versionCode setiap rilis. Unggah APK lengkap, `gratify-update.json`, serta patch yang dihasilkan ke tag stabil GitHub yang sama hanya setelah pengujian perangkat. Format lama tanpa `deltas` tetap diterima. Rilis pertama yang membawa dukungan ini masih memerlukan APK lengkap; rilis sesudahnya dapat menyediakan delta untuk baseline tersebut.

## Verifikasi dan batas yang masih tersisa

Pengujian otomatis: 52 pemeriksaan JVM (parser, pemilihan sumber, metadata, UI, identitas APK dan updater lama), 14 pemeriksaan Python (converter/generator dan persiapan rilis). Kompilasi kode Android Full release dan pemrosesan manifest berhasil. Pengujian UI memakai dialog Compose nyata; tombol cadangan tidak memulai transfer sebelum diklik. Rekonstruksi berkas nyata dengan heap 16 MiB dan perbandingan SHA-256 berhasil.

Belum dilakukan: pengujian end-to-end pada HP Android rendah RAM, pemutaran audio selama penerapan, baterai/termal, jaringan terputus, proses mati saat transfer/penerapan, cache dibersihkan, ruang habis saat menulis, serta pemasangan dua rilis dengan signer sama dan code meningkat. Hal tersebut memerlukan build perangkat yang belum diizinkan. Sebelum publikasi, uji kondisi tersebut dan hindari rollout jika ada kehilangan sesi/data, audio tersendat, atau pemulihan gagal. Saat ini tidak ada rilis/patch baru yang tersedia bagi pengguna.

Bukti: `artifacts/delta-feasibility-2026-10-04/streaming-result.json`, `heap-limited-benchmark.log`, `final-tests.log`, `final-verification.log`, `python-delta-tests.log`, `release-preparation-tests.log`, `implementation-verification.json`, dan gambar `artifacts/auto-update-audit-2026-10-03/update-delta-fallback.png`.

## Referensi utama

- Android memerlukan kontinuitas penandatanganan untuk pembaruan: [Android app signing](https://developer.android.com/studio/publish/app-signing).
- API gzip yang dipakai aplikasi tersedia di platform: [Android GZIPInputStream](https://developer.android.com/reference/java/util/zip/GZIPInputStream).
- Algoritma/formatted diff dan library penerbit: [bsdiff4](https://github.com/ilanschnell/bsdiff4).
- Alternatif archive-aware yang tidak dipilih karena repo diarsipkan: [Google archive-patcher](https://github.com/google/archive-patcher).
