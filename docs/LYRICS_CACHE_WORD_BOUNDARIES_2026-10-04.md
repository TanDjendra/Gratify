# Perbaikan lirik terpisah dan pemeriksaan SimpMusic — 4 Oktober 2026

Permintaan: periksa/ambil kode SimpMusic untuk mengatasi suku kata yang terlihat sebagai kata terpisah. Tidak membuat atau memasang APK pada pekerjaan ini.

## Temuan

Klien penyedia dan renderer Apple Music Gratify sudah merupakan port SimpMusic. Salinan repo studi digunakan kembali dan sumber HEAD terbaru diambil tanpa mengubah checkout:

- App: `339759a87a7e0af1011e09f290b178740685fffa` dari `https://github.com/maxrave-dev/SimpMusic.git`.
- Core: `faf6f81f47aff737f4ffe896536086f557e961fa` dari `https://github.com/maxrave-dev/core.git`.

Renderer lirik yang diperiksa tidak berubah dari acuan sebelumnya pada jalur terkait. Parser TTML di core terbaru masih menulis `wordParts.append("<$beginFormatted>$word ")`: tiap span mendapat spasi, padahal span bisa berupa suku kata. Gratify telah mempertahankan whitespace asli TTML dan mengelompokkan suku kata untuk wrapping. Menyalin parser itu secara literal akan menghilangkan perlindungan ini.

**Celah baru yang ditemukan:** mapping Gratify memberi penanda versi teks 1 kepada setiap respons RICH_SYNCED, termasuk ketika tidak ada teks referensi yang cocok untuk memeriksa batas kata. Cache tersebut kemudian dianggap sudah diformat dan dapat menahan teks utuh dari fallback. Resolver juga berhenti pada rich-sync pertama sehingga referensi kata utuh tidak pernah dicari untuk respons yang belum terverifikasi. Keluhan dari APK pengguna belum direproduksi langsung di HP; temuan ini merupakan jalur kesalahan yang direproduksi dengan tes.

## Perbaikan

- Versi format cache dinaikkan menjadi 2. Penanda v1 tidak lagi menjadi bukti bahwa batas kata sudah benar; cache tetap tersedia ketika offline dan tidak dihapus.
- Respons rich-sync mendapat penanda terverifikasi ketika pencocokan synced/plain dalam respons berhasil. TTML yang diparse dari whitespace asli dokumen mempertahankan penanda parser; tidak ada spasi yang ditambahkan per timestamp.
- Jika rich-sync belum terverifikasi, resolver dapat mencari referensi dari penyedia fallback yang sudah ada. Referensi diterima hanya ketika urutan karakter sama. Yang diambil hanya batas/spasi kata; huruf, kapitalisasi, waktu baris, dan semua timestamp potongan tetap dari rich-sync sumber.
- Hasil rich-sync yang sudah terverifikasi berhenti segera, sehingga tidak menambah permintaan fallback untuk kasus tersebut. Jika tidak ada referensi yang cocok, teks sumber tetap dipertahankan tanpa penggabungan berbasis tebakan.
- Cache lama dapat diperbaiki menggunakan referensi baru dan disimpan kembali oleh jalur pemutar. Pratinjau story menggunakan aturan cache yang sama agar cache lama tidak menahan perbaikan teks.
- Efek glow, wipe, gerakan karakter, blur, mode standar, dan UI yang diambil dari SimpMusic tetap memakai renderer yang sama. Perbaikan ada pada provenance teks, cache, dan resolver.

## Verifikasi

**83 pemeriksaan lulus**, tanpa failure, error atau skip. Kompilasi kode Android Full release berhasil; tidak menjalankan assemble, membuat APK, atau memasang APK.

| Cakupan | Pemeriksaan |
| --- | ---: |
| Parser/pencarian/klien lyrics | 20 |
| Mapping respons dan empat varian Mangu langsung dari penyedia | 11 |
| Resolver fallback, cache, dan loader story | 19 |
| Batas kata dan render pada dua lebar layar | 9 |
| Player/loading/pause/seek/mode animasi | 10 |
| Share dan pratinjau lirik story | 14 |

Respons langsung untuk `JENpTmMQBQY`, `fUdfDKyrbjk`, `McIPitKTfw4`, dan `WNGbZRb3czc` diperiksa ulang. Tiga respons memiliki 11 potongan pada frasa Mangu, satu memiliki empat kata utuh. Pemetaan Gratify menghasilkan teks utuh dan mempertahankan timestamp masing-masing versi. Tidak menukar video ID atau mengambil timing versi lain.

Kasus baru mencakup respons tanpa referensi yang tidak boleh diberi penanda terverifikasi, cache v1 yang tidak boleh menahan perbaikan, penggabungan dari referensi dengan waktu baris berbeda tanpa memindahkan timing, referensi yang tidak cocok, penghentian request setelah hasil terverifikasi, dan perbaikan cache story.

Bukti: `artifacts/lyrics-cache-repair-2026-10-04/verification.log`, `final-verification.log`, `test-results.json`, `live-video-variants.json`, serta gambar renderer `artifacts/lyrics-spacing/mangu-320.png` dan `mangu-390.png`.

## Batas praktis

Ini berlaku pada jalur lirik secara umum, bukan daftar khusus satu lagu. Hasil kata utuh masih bergantung pada whitespace sumber yang benar atau tersedianya referensi dengan karakter identik. Cache lama yang sudah berisi spasi salah dan tidak mempunyai referensi tidak dapat dipulihkan dengan aman ketika sepenuhnya offline. Pengujian baru dilakukan melalui JVM, Compose dan penyedia langsung; aplikasi dengan kode baru belum diuji di HP.

APK debug tetap SHA-256 `5fe15632656c5971b4e82c6b07c4c26204dd942a7a9c200d6dc0769df9bd179b`; APK release lama tetap `bccc1576d15a2e4ab45e15c43a9c84e7e1fa43caefc28546f64b9eeb2d848557`. Perubahan baru terlihat setelah pengguna meminta build dan memasang APK berikutnya.

Sumber pembanding: [parser core SimpMusic pada commit yang diperiksa](https://github.com/maxrave-dev/core/blob/faf6f81f47aff737f4ffe896536086f557e961fa/service/lyricsService/src/commonMain/kotlin/org/simpmusic/lyrics/parser/LrcTextParser.kt), [repo aplikasi SimpMusic](https://github.com/maxrave-dev/SimpMusic/tree/339759a87a7e0af1011e09f290b178740685fffa).
