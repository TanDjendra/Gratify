# Audit pemutaran, penutupan aplikasi, dan pintasan pengaturan

Permintaan: musik berhenti setelah aplikasi dihapus dari daftar terbaru atau ditutup, telusuri macet setelah 4–5 lagu saat layar mati, dan tambahkan pengaturan di kanan atas beranda. Perangkat yang dilaporkan: iQOO Z9 5G, Wi-Fi.

## Temuan dan perbaikan

- Layanan musik sebelumnya sengaja mempertahankan pemutaran setelah task dihapus. Sekarang `onTaskRemoved` menghentikan pemutaran, melepas sesi dan koneksi System UI, serta mengakhiri layanan. Penutupan Activity yang benar-benar selesai memakai jalur penghentian yang sama; pergantian konfigurasi, Home, dan layar mati tetap mempertahankan pemutaran.
- Menghentikan layanan lewat `Context.stopService` saja dapat menyisakan layanan yang masih terikat dengan pengontrol sistem. Jalur penghentian sekarang membersihkan sesi pada instance layanan aktif melalui referensi lemah, tanpa mempertahankan context layanan setelah dihancurkan.
- Pemutar lagu berikutnya yang gagal saat dimuat di awal dapat dipakai kembali dalam keadaan IDLE/error. Jalur pergantian biasa dan crossfade sekarang menolak dan melepas preload tersebut, lalu menyiapkan sumber baru.
- Preload sebelumnya mengikuti indeks berurutan walaupun shuffle aktif, dan dapat menahan pemain yang tidak lagi akan dipakai. Sekarang preload mengikuti urutan antrean sebenarnya, dibatasi dua lagu berikutnya, dan pemain yang sudah tidak dibutuhkan dilepas. Repeat satu lagu tidak membuat preload tambahan.
- Pencarian alamat stream tidak memiliki batas waktu menyeluruh. Sekarang semua tahap resolusi memiliki tenggat 30 detik; kegagalan kembali ke jalur error ExoPlayer dan percobaan ulang yang dibatasi.
- Percobaan ulang sumber sebelumnya berjalan tanpa kepemilikan job dan penghitungnya direset setiap READY. Sekarang batas dua percobaan ulang per pemuatan dipatuhi, job dibatalkan pada jeda/stop/pindah lagu, dan identitas pemain serta indeks diperiksa sebelum reload. Posisi pemulihan diambil dari pemain yang benar-benar gagal. Tombol play dapat memulai pemulihan setelah error.
- Error pemain incoming saat crossfade sekarang masuk ke penanganan error dan retry, bukan diabaikan sebagai pemain nonaktif. Crossfade yang sudah mengantre juga memeriksa apakah pengguna masih ingin memutar musik.
- Penutupan aplikasi membatalkan pekerjaan pemuatan antrean, pemantauan progres/buffer, pemuatan format/segmen, dan sleep timer, tanpa membuang antrean tersimpan atau scope aplikasi yang dibutuhkan saat dibuka kembali.
- Beranda memiliki tombol pengaturan berukuran 48 dp di kanan atas, dengan label aksesibilitas dan navigasi single-top. Dua sakelar lama yang saling bertentangan tentang mempertahankan layanan diganti penjelasan perilaku penutupan dalam bahasa Indonesia/Inggris. Preferensi lama tetap dapat dibaca dari cadangan tetapi tidak mengubah aturan penutupan baru.

## Verifikasi

Hasil akhir tersimpan di `artifacts/playback-lifecycle-2026-10-09`:

- 48 tes otomatis lolos: 20 tes Media3 dan 28 tes tampilan/navigasi/pengaturan. Tidak ada kegagalan, error, atau tes dilewati.
- 53 pemeriksaan Android lolos pada emulator API 37 dengan halaman memori 16 KB; buffer crash kosong.
- Sepuluh lagu berturut-turut diputar secara alami saat layar mati pada mode biasa dan crossfade, masing-masing pada kecepatan 4x. Preload lagu berikutnya dibuat IDLE ketika lagu kelima diputar; antrean tetap mencapai lagu kesepuluh dan layar tetap dalam keadaan asleep. Layanan foreground aktif selama pemutaran dan hilang setelah task dihapus.
- Error sumber disuntikkan ke listener produksi untuk memeriksa batas dua retry meskipun setiap reload mencapai READY, pemulihan melalui tombol play, pembatalan retry saat pause/penutupan, dan error pemain incoming saat crossfade. Pengujian ini menguji penanganan error terkontrol, bukan koneksi Wi-Fi eksternal.
- Tombol pengaturan Android berukuran 48 dp membuka layar pengaturan produksi dan kembali ke beranda, pada ukuran teks 1.0 dan 1.5. Penjelasan penutupan juga diperiksa langsung pada halaman pemutaran.
- Build APK debug dan kompilasi JVM berhasil. APK yang diuji identik dengan hasil build akhir (SHA-256 di bawah); emulator dihentikan dan ukuran teks dikembalikan ke bawaan setelah audit.

APK: `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`, versi `2.1.0-dev` (61), paket `com.tan.gratify.dev`, dibuat 9 Oktober 2026 pukul 01:04:55 WIB. Ukuran 104.588.712 byte. SHA-256: `F401C3E90BBBE32237A5E75BEBF41131CF17347E2732C6EF72F4410457CEFAF4`.

Fixture debug memakai adapter, sesi, dan layanan produksi. Audio lokal berdurasi 60 detik diputar melalui cache produksi, sehingga pemeriksaan layar mati menguji lifecycle dan pergantian pemain, termasuk simulasi preload gagal, tanpa bergantung pada penyedia stream eksternal. Fixture tidak masuk APK release.

## Batas pembuktian

Masalah preload dan retry ditemukan langsung dalam kode dan diuji dengan kegagalan yang terkontrol. Pengujian emulator tidak membuktikan bahwa seluruh penyebab macet pada iQOO Z9 5G melalui Wi-Fi telah hilang. Perangkat tersebut belum terhubung dalam sesi ini; pengujian beberapa lagu streaming dengan layar mati pada perangkat asli masih diperlukan. Audio player sudah memakai mode wake jaringan ExoPlayer; perbaikannya mempertahankan konfigurasi tersebut.

Aturan layanan mengikuti [panduan pemutaran latar belakang Android](https://developer.android.com/media/media3/session/background-playback). Perilaku jaringan saat perangkat idle dijelaskan dalam [dokumentasi Doze dan App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby); tidak ada bukti langsung dalam sesi ini bahwa pengaturan hemat daya iQOO merupakan penyebab utama.

## Tindak lanjut screenshot layar kunci

Pengguna melaporkan musik berhenti dan kartu layar kunci menampilkan Spotify tanpa judul lagu, dengan tombol play dalam keadaan jeda. Pengguna mengonfirmasi masih memakai APK sebelum perbaikan playback dan lagu tetap tersedia serta dapat diputar lagi ketika Gratify dibuka. Screenshot tidak membuktikan bahwa Spotify mengambil audio focus atau menjelaskan penyebab penghentian Gratify. Tidak ada perangkat fisik terhubung untuk mengambil log kejadian tersebut.

APK debug hasil penyesuaian kapsul Search juga memuat perbaikan playback ini: dibuat 9 Oktober 2026 pukul 02:00:24 WIB, SHA-256 `A64185371004F65221DC02826D65F1BF2A7E9119B479CC596C1E062FC596CEED`. Pengujian streaming Wi-Fi dengan layar mati pada HP asli tetap diperlukan setelah memasang APK tersebut. Pemeriksaan Search tidak dihitung sebagai pengujian ulang playback.

## Kejadian berulang pada APK playlist

Pengguna kemudian mengonfirmasi memakai APK playlist terbaru (dibuat pukul 09:17 WIB) dan musik masih berhenti saat berpindah dari lagu keenam ke ketujuh. Karena itu, hasil audit awal di atas tidak dianggap menyelesaikan kejadian pada perangkat asli. Audit lanjutan atas niat pemutaran sesi, callback delegate, dan penjaga CPU sementara, beserta pengujian HTTP, dicatat dalam `PLAYBACK_HANDOFF_AUDIT_2026-10-09.md`.
