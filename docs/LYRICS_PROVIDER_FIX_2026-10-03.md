# Pemeriksaan lirik Gratify dan SimpMusic — 3 Oktober 2026

> Laporan pemeriksaan awal. Integrasi sumber utama kemudian dilanjutkan dengan port
> klien SimpMusic setelah klarifikasi pengguna. Status terbaru ada pada
> [laporan port pengambilan lirik](SIMPMUSIC_LYRICS_FETCH_PORT_2026-10-03.md).

Yang sebelumnya dipindahkan adalah komponen tampilan Apple Music dari SimpMusic. Sumber
utama datanya tetap layanan Gratify di Supabase, sehingga cakupan lagu dan jenis sinkronisasi
bisa berbeda. Efek per kata memerlukan waktu per kata; lirik biasa atau waktu per baris tidak
mempunyai data yang sama.

## Penyedia yang diperiksa

| Penyedia | Penggunaan di Gratify | Hasil pemeriksaan |
|---|---|---|
| SimpMusic Lyrics | Belum diaktifkan | Server mengembalikan 403 untuk identitas Gratify: hanya tersedia untuk klien SimpMusic. Perlu akses resmi dari pemilik layanan. |
| Better Lyrics | Pilihan penyedia dan cadangan otomatis; mengutamakan waktu per kata | Klien produksi berhasil membaca 73 baris RICH_SYNCED pada Bohemian Rhapsody / Queen. Permintaan lain tanpa durasi memperoleh 401 “API key required”; akses tanpa key tidak dijamin untuk semua lagu. |
| LRCLIB | Pilihan penyedia dan cadangan otomatis | Klien produksi berhasil membaca 56 baris LINE_SYNCED pada lagu uji. Satu permintaan judul lain memperoleh 503; kegagalan diteruskan ke cadangan. |
| Spotify | Cadangan jika pengaturan lirik Spotify aktif dan akun sudah tersambung | Memakai alur akun yang sudah ada; tidak diuji menggunakan akun pribadi pada pemeriksaan ini. |
| Transkrip YouTube | Pilihan penyedia dan cadangan terakhir di jaringan | Memakai video ID yang sedang diputar; tersedia hanya jika video mempunyai transkrip yang dapat diambil. |
| Cache perangkat | Ditampilkan lebih awal dan dipertahankan jika jaringan gagal | Cache kosong/error tidak dianggap sebagai lirik. Cache per kata yang valid tidak diturunkan menjadi versi per baris. |

Sumber utama SimpMusic tidak dipasang dengan menyamarkan Gratify sebagai klien SimpMusic.
Tidak ada kontribusi, vote, pesan, atau perubahan layanan eksternal yang dilakukan dalam
pengujian ini.

## Perbaikan aplikasi

- Pengambilan lirik memakai satu pekerjaan yang dapat dibatalkan. Permintaan lagu lama
  dibatalkan saat lagu berganti; hasil dan terjemahan lama tidak menimpa lagu baru.
- Alur default mencoba Gratify, Better Lyrics, Spotify bila tersambung, LRCLIB dan YouTube.
  Penyedia pilihan pengguna tetap didahulukan. Sumber yang gagal, kosong, atau melewati batas
  waktu tidak menghentikan pencarian cadangan.
- Lirik per baris dari sumber default dapat ditampilkan segera, lalu ditingkatkan ke waktu
  per kata jika Better Lyrics menyediakannya. Tidak membuat waktu kata buatan.
- Metadata lagu yang sedang diputar dikirim ke layanan Gratify, tanpa harus menunggu metadata
  tersimpan di database lokal terlebih dahulu.
- LRCLIB tidak lagi menolak seluruh hasil ketika durasi masih nol/tidak diketahui. Hasil kosong
  dilewati, versi tersinkron diprioritaskan, dan batas selisih durasi tetap sepuluh detik jika diketahui.
- Pencarian menghapus label kemasan video seperti “Official Music Video”, sambil mempertahankan
  penanda versi rekaman seperti Live/Remix/Acoustic.
- Tab dan layar lirik penuh membedakan “Mencari lirik” dari “Lirik belum tersedia”, serta
  menyediakan tombol Coba lagi. Tampilan per kata memakai renderer Apple Music SimpMusic yang
  sudah dipindahkan; pilihan Tanpa animasi tetap berlaku.

## Validasi dan batasnya

Hasil akhir, jumlah tes, hash APK dan log tersimpan di
`artifacts/lyrics-provider-audit/validation.json`. Uji jaringan klien produksi tersimpan di
`production-client-network.txt`; uji tersebut tidak memakai kredensial dan tidak menulis konten
ke server. Pengulangan uji jaringan menggunakan `GRATIFY_LYRICS_NETWORK_CHECK=1` untuk
`LyricsProviderLiveTest`; pada suite biasa uji jaringan dilewati agar tidak bergantung layanan luar.

Lagu “Losing You” pengguna belum dapat dibandingkan secara tepat karena artis/video ID tidak
diberikan. Hasil jaringan pada lagu uji tidak dianggap sebagai jaminan cakupan lagu tersebut.
Tidak ada pengukuran baterai atau pengujian pada HP fisik pengguna dalam perubahan ini.

## Acuan resmi

- [Daftar sumber lirik SimpMusic](https://github.com/maxrave-dev/SimpMusic/blob/dev/README.md).
- [Klien layanan lirik dari core SimpMusic](https://github.com/maxrave-dev/core/blob/d90b53fbad07ab73cecbfe89b33b533fb9ca78ed/service/lyricsService/src/commonMain/kotlin/org/simpmusic/lyrics/SimpMusicLyrics.kt).
- [Autentikasi backend Better Lyrics](https://github.com/better-lyrics/cf-api/blob/main/src/providers/BoiduLyricsApi.ts).
- [Dokumentasi LRCLIB](https://lrclib.net/docs).
