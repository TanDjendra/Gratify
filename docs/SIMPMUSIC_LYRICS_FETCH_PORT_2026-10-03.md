# Port pengambilan lirik SimpMusic ke Gratify

Gratify sekarang menggunakan port klien pengambilan lirik SimpMusic sebagai sumber utama.
Perubahan ini melengkapi port tampilan Apple Music yang sudah dibuat sebelumnya.
Laporan ini menggantikan kesimpulan integrasi pada `LYRICS_PROVIDER_FIX_2026-10-03.md`.

## Kode yang menjadi acuan

- Core SimpMusic: revisi `d90b53fbad07ab73cecbfe89b33b533fb9ca78ed`.
- [SimpMusicLyrics.kt](https://github.com/maxrave-dev/core/blob/d90b53fbad07ab73cecbfe89b33b533fb9ca78ed/service/lyricsService/src/commonMain/kotlin/org/simpmusic/lyrics/SimpMusicLyrics.kt): endpoint, header, dan parameter pencarian.
- [SimpMusicLyricsClient.kt](https://github.com/maxrave-dev/core/blob/d90b53fbad07ab73cecbfe89b33b533fb9ca78ed/service/lyricsService/src/commonMain/kotlin/org/simpmusic/lyrics/SimpMusicLyricsClient.kt): validasi respons, lirik asli, terjemahan, LRCLIB, dan Better Lyrics.
- Alur pemutar: snapshot SimpMusic `62e0073ba818d899b22c75909deb9b034516d5db`, `SharedViewModel.getLyricsFromFormat` dan fungsi pengambilan lirik terkait.

Port diberi atribusi sumber dan menggunakan lisensi GPL-3.0 yang juga sudah ada di proyek.
Salinan sumber yang diperiksa tersedia di `artifacts/lyrics-provider-audit/`.

## Alur yang dipindahkan

| Sumber | Permintaan | Pemrosesan |
|---|---|---|
| SimpMusic Lyrics | GET `https://api-lyrics.simpmusic.org/v1/{videoId}` | Membaca `BaseResponse<List<LyricsResponse>>`, mengambil hasil pertama seperti upstream, lalu memprioritaskan rich LRC, LRC per baris, dan teks biasa. |
| Terjemahan SimpMusic | GET `/v1/translated/{videoId}/{language}` | Kode bahasa dua huruf, parser LRC, pemeriksaan keselarasan waktu dengan lirik utama. |
| LRCLIB | GET `https://lrclib.net/api/search?q={artist} {track}` | Pencarian gabungan seperti upstream; pemilihan lirik sinkron dengan toleransi durasi 10 detik. Durasi belum diketahui tidak diperlakukan sebagai nol detik. |
| Better Lyrics | GET `https://lyrics-api.boidu.dev/getLyrics?s={track}&a={artist}&d={duration}` | Membaca TTML dan mempertahankan waktu per kata. Durasi hanya dikirim jika sudah diketahui. |
| Spotify | Klien Spotify yang sudah ada | Cadangan hanya ketika fitur aktif dan akun sudah tersambung. |
| YouTube | Klien transkrip yang sudah ada | Mempertahankan video ID dan pasangan transkrip/terjemahan. |

Klien mempertahankan header library upstream `User-Agent: SimpMusicLyrics/1.0`.
Permintaan utama dan terjemahan GET tidak memerlukan HMAC dari fungsi kontribusi upstream.
Uji sebelumnya dengan header Gratify memperoleh 403 dari layanan utama; identitas library
upstream merupakan bagian penting dari protokol yang sekarang dipindahkan. Ini bukan bukti
jaminan akses permanen dari pemilik server. Kegagalan HTTP tetap diteruskan ke cadangan.

Pilihan utama SimpMusic mencoba SimpMusic, lalu Spotify jika tersedia, kemudian LRCLIB.
Better Lyrics dan YouTube yang dipilih pengguna kembali ke SimpMusic jika gagal, seperti upstream.
Gratify mempertahankan tambahan cadangan Better Lyrics, layanan Gratify, dan YouTube;
lirik per baris yang sudah tampil juga dapat diperkaya oleh waktu per kata dari Better Lyrics.
Cache perangkat tampil lebih awal dan tetap tersedia bila jaringan gagal.

## Integrasi aplikasi

- `SimpMusicLyrics.kt` dan `SimpMusicLyricsClient.kt` sekarang merupakan kode produksi di modul layanan lirik.
- Repository dan dependency injection menggunakan klien baru, termasuk pencarian LRCLIB dan Better Lyrics.
- `LyricsProvider.SIMPMUSIC` membedakan sumber asli dari layanan Gratify.
- Pengaturan awal dan nilai lama `gratify` dibaca sebagai `simpmusic`. Pilihan LRCLIB, YouTube, atau Better Lyrics yang sudah disimpan tetap dipakai.
- Setelah permintaan penggantian nama, pengaturan dan menu pemutar menampilkan pilihan **Gratify**; footer sumber utama juga memakai nama Gratify. Klien pengambilan tetap port library SimpMusic yang dijelaskan di atas.
- Waktu `<MM:SS.cc>` per kata diteruskan ke renderer Apple Music tanpa dihapus atau dibuat-buat. Mode tanpa animasi tetap tersedia.
- Satu pekerjaan yang dapat dibatalkan, batas waktu jaringan, dan pemeriksaan lagu aktif tetap dipertahankan; port tidak mengembalikan pekerjaan bersarang dari implementasi upstream.
- ID hasil SimpMusic tidak dipakai untuk vote Gratify. Port ini hanya mengambil data; tidak menyalin fungsi POST kontribusi/vote ke server SimpMusic.

## Verifikasi

Build debug selesai berhasil. **31 pengujian lulus**, tidak ada yang gagal atau dilewati:
12 pengujian layanan, 5 pengujian data/pengaturan/jaringan, dan 14 pengujian aplikasi.
Lint menghasilkan **0 error dan 117 warning**, terutama pemeriksaan versi dependency
dan masalah proyek yang sudah ada. Kotlin daemon sempat memakai kompilasi cadangan;
kompilasi cadangan dan keseluruhan build selesai berhasil.

Uji jaringan menggunakan klien Kotlin produksi pada lagu uji yang sama:

| Sumber | Hasil |
|---|---|
| SimpMusic utama | **RICH_SYNCED, 51 baris; seluruh 51 baris mempunyai waktu per kata** |
| Better Lyrics | RICH_SYNCED, 73 baris |
| LRCLIB melalui query gabungan upstream | LINE_SYNCED, 65 baris |
| Layanan Gratify sebagai pembanding/cadangan | LINE_SYNCED, 56 baris |

Hasil ini menunjukkan perbedaan data yang sebelumnya menyebabkan efek per kata tidak
tersedia ketika hanya memakai sumber utama Gratify. Pengujian renderer Apple Music yang
ada juga lulus, termasuk efek per kata, seek, pause, dan mode tanpa animasi.

APK baru tersedia di `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`
serta versi arm64-v8a, armeabi-v7a, dan x86_64 dalam direktori yang sama.

Hasil akhir pengujian, permintaan jaringan produksi, dan hash APK dicatat pada
`artifacts/lyrics-provider-audit/simpmusic-port-validation.json`.
Hash di berkas tersebut berasal dari build port awal. Build setelah perubahan nama
dicatat terpisah pada `artifacts/lyrics-provider-audit/gratify-name-validation.json`.
Hasil jaringan produksi tersedia pada `simpmusic-production-client-network.txt`
di direktori yang sama.

Pengujian protokol mencakup URL, header, pembacaan respons, terjemahan, kegagalan HTTP,
pencarian cadangan, dan pembatalan. Pengujian aplikasi mencakup konversi waktu per kata,
pilihan penyedia yang tersimpan, urutan cadangan, serta renderer Apple Music dan status lirik.

Lagu uji jaringan adalah **Bohemian Rhapsody — Queen**, ID video `fJ9rUzIMcZQ`.
Versi **Losing You** yang dimaksud pengguna belum diidentifikasi, sehingga laporan ini
tidak mengklaim lagu tersebut sudah diuji. Ketersediaan setiap lagu dan tipe waktu lirik
tetap bergantung pada respons penyedia. Spotify pribadi dan transkrip YouTube tidak
diuji melalui akun pengguna dalam pemeriksaan ini.
