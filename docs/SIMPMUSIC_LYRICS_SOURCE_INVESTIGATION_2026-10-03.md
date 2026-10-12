# Mengapa SimpMusic bisa menampilkan lirik utuh dan berjalan

3 Oktober 2026. Repo SimpMusic dan modul core sudah di-clone dan diperiksa bersama riwayat perubahannya.

Temuan yang dapat direproduksi: **penyedia yang sama mengirim batas kata berbeda untuk ID YouTube yang berbeda, walaupun judul lagunya sama-sama Mangu**. Renderer asli SimpMusic menampilkan kata utuh ketika menerima data yang memang mempunyai timestamp per kata utuh. Data bertimestamp per suku kata masuk ke renderer sebagai potongan terpisah.

## Repo yang diperiksa

- Clone aplikasi: `D:/Tan/Gratify-upstream-study/SimpMusic`, origin `https://github.com/maxrave-dev/SimpMusic.git`, cabang `dev` pada commit `62e0073ba818d899b22c75909deb9b034516d5db`.
- Clone core: `D:/Tan/Gratify-upstream-study/core`, origin `https://github.com/maxrave-dev/core.git`, commit `d90b53fbad07ab73cecbfe89b33b533fb9ca78ed`, sesuai submodule aplikasi.
- Clone memakai filter blob dan sparse checkout kode yang diperlukan. Riwayat commit/tag tersedia; APK dan aset yang tidak diperlukan tidak dibangun.
- Tag aplikasi `v2.2.0` mengarah ke `bdf28a835acdd2cc6310cea4a763abc68640ae24`; core-nya `0b9ce7b71a8b0e7b5d275351fa1e30611e59683e`. Parser layanan lirik pada core release dan dev mempunyai blob Git yang sama: `51f68870a238d5066d4da88e04e6326d856aaf05`.
- Parser rich-sync aplikasi juga tidak berubah antara tag `v2.2.0` dan dev. Riwayat parser layanan yang diperiksa tidak menunjukkan penggabungan otomatis suku kata menjadi kata.

## Alur dari kode

1. [Pilihan penyedia dalam SharedViewModel](https://github.com/maxrave-dev/SimpMusic/blob/62e0073ba818d899b22c75909deb9b034516d5db/composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt) menentukan sumber. Default layanan utama adalah SimpMusic; Better Lyrics, LRCLIB, dan YouTube merupakan pilihan lain.
2. [Repository](https://github.com/maxrave-dev/core/blob/d90b53fbad07ab73cecbfe89b33b533fb9ca78ed/data/src/commonMain/kotlin/com/maxrave/data/repository/LyricsCanvasRepositoryImpl.kt) mengambil hasil utama pertama menggunakan `getLyrics(videoId)`.
3. [Pemetaan respons](https://github.com/maxrave-dev/core/blob/d90b53fbad07ab73cecbfe89b33b533fb9ca78ed/data/src/commonMain/kotlin/com/maxrave/data/mapping/Mapping.kt) memprioritaskan rich-sync, kemudian synced, lalu plain.
4. [Parser layanan](https://github.com/maxrave-dev/core/blob/d90b53fbad07ab73cecbfe89b33b533fb9ca78ed/service/lyricsService/src/commonMain/kotlin/org/simpmusic/lyrics/parser/LrcTextParser.kt) meneruskan teks rich-sync. Parser aplikasi membuat satu `WordTiming` dari teks di antara dua timestamp.
5. [Renderer](https://github.com/maxrave-dev/SimpMusic/blob/62e0073ba818d899b22c75909deb9b034516d5db/composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/LyricsView.kt) menempatkan setiap potongan pada FlowRow dengan jarak 4 dp. `AnimatedWord` menggerakkan sorotan dan efek karakter berdasarkan durasi potongan tersebut. Ketika potongannya kata utuh, animasi karakter berjalan di dalam kata utuh.

Tidak ada tahap pada jalur ini yang mencocokkan rich-sync dengan plain/synced untuk memperbaiki batas kata. Tahap tersebut merupakan tambahan di Gratify.

## Respons langsung untuk empat versi Mangu

Pemeriksaan menggunakan klien produksi Gratify yang merupakan port klien SimpMusic, endpoint `/v1/{videoId}`, header library yang sama, dan parser core asli yang diisolasi sebagai fixture pengujian. Respons diambil langsung pada tanggal audit.

| ID YouTube | Timestamp potongan pada baris pengguna | Awal baris | Teks setelah parser asli |
|---|---:|---:|---|
| `fUdfDKyrbjk` | 4 | 24,850 detik | `Suatu malam Adam bercerita` |
| `McIPitKTfw4` | 11 | 22,960 detik | `Su a tu ma lam A dam ber ce ri ta` |
| `JENpTmMQBQY` | 11 | 22,960 detik | `Su a tu ma lam A dam ber ce ri ta` |
| `WNGbZRb3czc` | 11 | 22,960 detik | `Su a tu ma lam A dam ber ce ri ta` |

YouTube mengidentifikasi [fUdfDKyrbjk](https://www.youtube.com/watch?v=fUdfDKyrbjk) dan [McIPitKTfw4](https://www.youtube.com/watch?v=McIPitKTfw4) sebagai dua entri audio Mangu dari kanal resmi Fourtwnty. Hasil berbeda tersebut bukan hanya perbedaan antara video lirik dan audio: dua entri audio pun menerima respons berbeda.

Timestamp versi pertama untuk baris pengguna:

`<00:24.85>Suatu <00:25.92>malam <00:27.50>Adam <00:28.57>bercerita`

Renderer asli berhasil menampilkan dan menganimasikan empat kata tersebut tanpa perbaikan tambahan. Pratinjau komponen dari uji JVM:

![Renderer asli dengan data empat kata](D:/Tan/Gratify/artifacts/simpmusic-source-investigation-2026-10-03/upstream-audio-whole-words.png)

Perbedaan awal baris adalah 1,890 detik. Ini menjadi alasan untuk mempertahankan timing dari ID yang benar-benar diputar: lirik dari ID lain tidak boleh dianggap otomatis cocok hanya karena judulnya sama.

Gratify yang telah diperbaiki menghasilkan batas kata utuh pada keempat respons, dengan mempertahankan semua timestamp asli masing-masing. Perbaikan lanjutan setelah investigasi ini meminjam hanya batas kata dari referensi dan mempertahankan huruf/kapitalisasi rich-sync asli. Pada entri `fUdfDKyrbjk`, `Adam` sekarang tetap memakai huruf kapital walaupun referensi synced menulis `adam`.

## Kesimpulan dan batas bukti

Kini ada penjelasan yang dibuktikan oleh kode dan respons nyata tentang bagaimana SimpMusic dapat tampil rapi: ia bisa menerima versi lirik yang potongannya sudah berupa kata utuh. Penjelasan sebelumnya yang hanya berfokus pada potensi masalah renderer belum memperhitungkan perbedaan antar-ID untuk judul yang sama.

**Belum dapat dipastikan bahwa inilah perbedaan pada HP pengguna**, karena ID yang diputar, penyedia, dan cache di SimpMusic pengguna belum diketahui. Jika kedua aplikasi memainkan ID berbeda, temuan ini langsung relevan; jika ID dan penyedianya sama, langkah berikutnya ialah memeriksa cache dan respons aktual pada kedua aplikasi.

Hasil ini melengkapi [laporan perbandingan performa](D:/Tan/Gratify/docs/LYRICS_PERFORMANCE_COMPARISON_2026-10-03.md). Tidak mengubah hasil pengukuran sebelumnya: perbaikan teks di Gratify dilakukan saat menerima respons, mempertahankan timing, dan tidak menambah pekerjaan penataan kata pada setiap frame.

**14 pemeriksaan lulus**, tidak gagal atau dilewati: permintaan empat ID memakai klien produksi, parser asli, pemetaan Gratify, preservasi timing, renderer asli dengan data kata utuh, serta pengujian batas kata dan dua lebar layar.

Bukti: `artifacts/simpmusic-source-investigation-2026-10-03/live-video-variants.json`, `validation.json`, `verification-final.log`, dan pratinjau. Tidak menyimpan lirik lengkap atau metadata pribadi kontributor.

Pekerjaan ini hanya menambah pengujian dan laporan. Tidak mengubah kode produksi, menukar sumber audio/lirik, membuat APK debug, memasang APK, atau menjalankan aplikasi pada HP.

Setelah pengguna meminta perbaikan diterapkan, pemrosesan produksi diperbarui agar mempertahankan huruf asli sebagaimana dijelaskan di atas. Validasi lanjutan **43 pemeriksaan lulus**, dicatat pada [laporan perbaikan akhir](D:/Tan/Gratify/docs/LYRICS_WORD_BOUNDARIES_FINAL_2026-10-03.md). APK tetap tidak dibuat atau dipasang.
