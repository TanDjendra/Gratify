# Gratify Desktop — pembaruan antarmuka 2 Oktober 2026

## Desktop dan APK

Gratify Desktop adalah aplikasi Windows tersendiri. APK adalah aplikasi Android.
Keduanya memakai fitur, akun Supabase, model data, dan sebagian komponen Compose yang sama.
Pembaruan ini memasang kerangka navigasi khusus desktop; tata letak Android tetap memakai jalurnya sendiri.
Pada tahap redesign desktop, APK tidak dibangun ulang. Paket berikutnya dengan logo baru dicatat di `artifacts/branding/release-verification.json`.

## Perubahan

- Caption Windows gelap dengan tombol perkecil, perbesar/pulihkan, tutup ke tray, dan area tarik jendela.
- Toolbar bersama: kembali, Home, pencarian, Friends, notifikasi, serta menu profil/pengaturan/riwayat.
- Koleksi di kiri: favorit, unduhan, playlist lokal, pencarian koleksi, buat playlist, pilihan aktif, dan perluas/perkecil panel.
- Konten halaman terpisah dari panel dan pemutar. Home, Search, Library, Friends, profil, pengaturan, playlist, dan album menggunakan kerangka yang sama.
- Header album/playlist horizontal, sampul lebih proporsional, tombol putar utama di kiri, serta baris lagu dengan durasi dan kolom album jika ruang cukup.
- Panel kanan netral: Now Playing, Queue, Lyrics. Antrean memakai daftar virtual dan klik lagu memanggil indeks antrean asli.
- Detail lagu dan menu lagu mempertahankan penambahan ke playlist, unduhan, pengatur waktu tidur, penyedia lirik, dan fitur menu yang sudah ada. Pengelolaan antrean lengkap tersedia dari menu panel antrean.
- Pemutar sepanjang jendela: lagu/sampul, suka, putar/jeda, sebelumnya/berikutnya, acak, ulang, slider progres, volume, bisu, lirik, antrean, dan mini player pada jendela yang cukup lebar.
- Timeline dikumpulkan di pemutar dan lirik, bukan seluruh kerangka; koleksi dan antrean menggunakan LazyColumn.
- Ctrl+K membuka pencarian; Alt+Left kembali. Tombol memakai semantik aksesibilitas dan tooltip.
- Slider desktop dikonversi dari fraksi 0–1 ke persen 0–100 sesuai kontrak mesin pemutar.
- Jalur audio VLC menambahkan opsi per media `:no-video`, termasuk saat URL audio memuat gambar atau stream video. Mode video eksplisit tetap memakai permukaan video yang ditanam dalam aplikasi.

Referensi susunan: [Spotify Desktop, Your Library dan Now Playing](https://newsroom.spotify.com/2023-06-20/spotify-desktop-experience-redesign-your-library-now-playing-views-customize/).

## Pratinjau dan pemeriksaan

`composeApp/src/jvmTest/kotlin/com/tan/gratify/ui/DesktopWorkspaceRenderTest.kt` merender komponen produksi dengan data demonstrasi lokal dan nama lagu fiktif.
Gambar di `artifacts/desktop-redesign` bukan tangkapan akun pengguna dan bukan gambar hasil generasi AI.
Ukuran 1440×900, 1040×800, dan 900×600 memeriksa posisi pemutar, aksi putar/jeda, seek 75%, panel antrean, klik lagu antrean, tutup panel, buat playlist, dan buka playlist.
Render Android dalam pengujian koleksi secara eksplisit memakai mode Android.
`VlcPlaybackOptionsTest` memeriksa opsi audio dan video; ini tidak membuktikan keluaran suara atau pengujian video manual.

Hasil final build, jumlah pengujian, hash paket, dan batas verifikasi dicatat di `artifacts/desktop-redesign/verification.json` setelah build selesai.

## Menjalankan

Build Windows ada di `desktopApp/build/compose/binaries/main-release/app/Gratify/Gratify.exe`.
Seluruh folder `Gratify` diperlukan karena memuat runtime Java, pustaka aplikasi, dan VLC; jangan memindahkan EXE sendirian.
Versi yang sedang berjalan perlu dibuka ulang untuk memakai perubahan ini.
Aplikasi audit memakai direktori dan preferensi uji terisolasi; sesi autentikasi dalam memori perlu login ulang setelah aplikasi uji dibuka ulang.

Pembaruan UI ini tidak menyatakan bahwa seluruh UAT peluncuran, Sentry desktop, atau finalisasi dokumen legal telah selesai.
