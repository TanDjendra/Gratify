# Pemutar dan lirik SimpMusic di Gratify — 2 Oktober 2026

Now Playing menggunakan satu tampilan selama panel masuk, buffering, siap diputar, dan playback. Penyebab kedipan lama adalah syarat `isExpanded`: selama animasi sheet, statusnya belum Expanded sehingga renderer lama sempat tampil. Pemilihan tampilan kini hanya membedakan pemutar biasa dan PiP.

## Acuan dan adaptasi

Acuan: repo resmi maxrave-dev/SimpMusic, cabang dev pada commit `62e0073ba818d899b22c75909deb9b034516d5db`. Gaya Apple Music mengikuti pilihan pengguna sebelumnya. Sampul lebar melebur ke mesh warna sampul; tab lirik/antrian kembali ke sampul saat diketuk ulang. Baris lirik besar, fokus kedalaman, travelling glow, gerakan karakter, dan penekanan nada panjang diadaptasi dari kode upstream. Kontrol pemutaran, antrean, video, informasi lagu, dan backend Gratify tetap memakai kontrak aplikasi yang ada. Ini adaptasi untuk Gratify, bukan penggantian seluruh aplikasi/dependensi SimpMusic.

Sumber kode mesh: SimpMusic/BitChord (GPL-3.0). Sumber efek dan kartu lirik: SimpMusic (GPL-3.0). Atribusi dipertahankan dalam file sumber dan snapshot acuan disimpan di `artifacts/simp-restoration/`.

## Pilihan animasi

- Pada tab Lirik atau layar lirik penuh, tekan tombol animasi untuk berpindah antara Animasi SimpMusic dan Tanpa animasi.
- Pilihan yang sama tersedia pada Pengaturan → Pemutaran → Lirik → Animasi lirik.
- Nilai disimpan di DataStore; berlaku pada pemutar dan lirik penuh, serta bertahan setelah aplikasi ditutup.
- Tanpa animasi menggunakan teks utuh, penanda baris aktif, serta scroll langsung. Tidak membuat animasi huruf, word wipe, glow, blur fokus, maupun gerakan latar lirik penuh. Ketukan untuk seek tetap bekerja pada lirik tersinkron.
- Pause dan seek membatalkan serta mengatur ulang word wipe, termasuk seek mundur dalam kata yang sama.

## Bagikan lirik

Tekan ikon Bagikan pada tab Lirik / lirik penuh, atau tahan sebuah baris. Pilih maksimal tiga baris berurutan → Pratinjau kartu → pilih warna → Bagikan gambar. Kartu memuat judul, artis, sampul jika sudah tersedia, lirik tanpa timestamp, dan identitas Gratify. Android membuka chooser sistem untuk PNG melalui FileProvider, tanpa izin penyimpanan. Desktop menyalin gambar ke clipboard untuk ditempel ke aplikasi lain. Tombol Simpan gambar menulis PNG ke lokasi yang dipilih lewat penyimpan berkas sistem pada Android; Desktop menyimpan berkas PNG bernama unik pada folder pengguna. Tidak meminta izin penyimpanan umum.

## Penyedia lirik SimpMusic

[README resmi](https://github.com/maxrave-dev/SimpMusic/blob/dev/README.md) menyebut SimpMusic Lyrics sebagai sumber utama dan LRCLIB sebagai alternatif, juga Spotify (perlu login) dan transkrip YouTube. [Kode pengambilan lirik pada commit acuan](https://github.com/maxrave-dev/SimpMusic/blob/62e0073ba818d899b22c75909deb9b034516d5db/composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt) turut menyediakan Better Lyrics. Cache offline dan terjemahan AI merupakan alur terpisah. Efek per kata memerlukan timestamp per kata (RICH_SYNCED); lirik biasa tidak dapat menghasilkan timing kata hanya dengan mengganti tampilan.

Gratify saat ini memakai layanan lirik Gratify, LRCLIB, Better Lyrics, transkrip YouTube, serta alur Spotify yang sudah ada. Penelitian sumber SimpMusic tidak mengubah endpoint server atau mengalihkan kontribusi lirik pengguna ke layanan eksternal.

## Validasi

- 61 tes composeApp dan 31 tes data lulus: total 92 tanpa kegagalan.
- Setelah penyesuaian aksesibilitas, 10 tes pemutar/berbagi diulang dan lulus.
- Uji mencakup layout loading → siap → diputar, pause/resume, seek mundur dalam kata yang sama, mode tanpa animasi saat waktu lagu bergerak, batas tiga baris berurutan, pembersihan timestamp, kontras kartu, dan preferensi setelah DataStore ditutup/dibuka ulang.
- Build APK debug dan lint berhasil; lint 0 error dengan 116 peringatan yang sudah ada.
- Emulator Android API 37: pemutar produksi diuji lewat fixture debug dengan playback/lirik/sampul sintetis. Berhasil memilih tiga baris, membuka pratinjau dan chooser PNG sistem, serta menyimpan PNG lewat penyimpan dokumen. Gambar keluaran 974×721 diperiksa secara visual. Tidak mengirim gambar ke penerima eksternal. Aksesibilitas lirik animasi dibaca per kata utuh.
- Catatan hasil: `artifacts/simp-restoration/validation.json`; bukti lengkap dan log pemeriksaan berada pada folder yang sama.
- APK untuk uji: `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`.

 Angka optimasi pada laporan sebelumnya berlaku untuk renderer sebelumnya; efek karakter upstream yang dipulihkan dalam perubahan ini belum memiliki benchmark CPU/baterai pada HP fisik.
