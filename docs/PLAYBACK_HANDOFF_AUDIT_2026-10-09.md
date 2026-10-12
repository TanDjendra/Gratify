# Pemutaran berhenti saat lagu 6 → 7

**APK audit ini digantikan oleh APK pada `SUPABASE_TLS_FIX_2026-10-09.md`.** Aturan domain debug untuk server pemutaran memicu ketidakcocokan TLS pada klien Supabase yang memilih CIO di Android. Regresi itu direproduksi dan diperbaiki pada audit TLS, dengan perbaikan playback dalam dokumen ini tetap disertakan.

Pengguna mengonfirmasi masalah muncul pada APK `Gratify-debug-playlist-20261009.apk` (SHA-256 `F58F694D31B24DB0B5C2B76E4CE08943B0BABF007B95103E77C2FD67F5FF746E`), tepat ketika berpindah dari lagu keenam ke ketujuh pada iQOO Z9 5G melalui Wi-Fi. Kartu layar kunci menampilkan Spotify tanpa judul. Screenshot tidak membuktikan bahwa Spotify mengambil audio focus; perangkat asli belum terhubung untuk mengambil log kejadian.

## Temuan dan perubahan

- Sesi Media3 menggunakan pemutar pembungkus yang berganti ExoPlayer di setiap lagu. Pemutar preload memang dijeda. Sebelumnya, sesi membaca `playWhenReady=false` dari preload pada saat dipromosikan, meskipun pengguna masih ingin melanjutkan antrean. Pembungkus kini membaca niat pemutaran dari adapter antrean.
- Penekanan status akhir sebelumnya hanya mencakup ENDED. Selama pergantian dan pemulihan sumber, status READY dari preload yang dijeda atau IDLE dari pemutar gagal juga harus dilaporkan sebagai BUFFERING. Penekanan dibatasi pada transisi dengan niat pemutaran aktif; pause, stop, error terminal, dan release mengakhirinya.
- Getter saja tidak cukup: listener sesi Media3 menyimpan nilai dari callback individual. Callback status, niat play, dan `isPlaying` kini melewati jembatan yang konsisten dengan getter pembungkus. Error sementara ditahan selama retry; error terminal tetap diteruskan. Listener tetap dipindahkan dan dilepas saat pergantian delegate.
- Perubahan transisi mengirim ulang status dan niat pemutaran agar sesi tidak menyimpan keadaan dari delegate lama. Play manual menetapkan niat pemutaran sebelum memanggil native `play()`, karena native dapat mengirim callback secara langsung.
- Pemutar incoming mulai memutar sebelum pemutar outgoing dihentikan dan dilepas. Mode wake jaringan ExoPlayer dipertahankan. Penjaga CPU tambahan hanya menjembatani sela ENDED/IDLE, termasuk jeda retry 500 ms. Penjaga tidak reference-counted, memiliki timeout 30 detik, dan dilepas setelah incoming mengambil alih, pause, stop, error terminal, atau release. Penjaga tidak dapat melewati semua pembatasan jaringan/OEM.

Implementasi berada pada `DelegatingForwardingPlayer.kt` dan `CrossfadeExoPlayerAdapter.kt`. Tidak ada perubahan pustaka atau backend untuk perbaikan ini.

## Verifikasi

Semua bukti berada di `artifacts/playback-handoff-2026-10-09`.

- Dua tes regresi awal gagal pada implementasi sebelum perbaikan (`before-fix.log`) dan lulus sesudahnya. Tes tambahan memeriksa nilai callback sesi, penghormatan pause, dan pelepasan pemilik transisi tanpa acquire berulang.
- 24 tes Media3 lulus, tanpa kegagalan/error (`unit-results.json`).
- 12 pemeriksaan Android untuk handoff lulus (`handoff-guard-results.json`). `retry-gap-power.txt` mencatat PARTIAL_WAKE_LOCK `Gratify:PlaybackHandoff` benar-benar sedang dipegang saat retry, bukan hanya riwayat acquire. Pause, pemulihan, dan penutupan melepaskannya; layar tetap asleep.
- 10 pemeriksaan retry Android lulus (`retry-results.json`): batas dua retry, play setelah error, pause/close membatalkan retry, serta pemulihan incoming crossfade.
- Uji HTTP 10 lagu dengan layar mati lulus pada mode biasa dan crossfade, termasuk BUFFERING lagu ketujuh setelah preload dibuat IDLE. Sesi tetap PLAYING/BUFFERING sepanjang antrean; layanan foreground tetap aktif selama penantian; layar tetap asleep hingga lagu kesepuluh. Menghapus task menghentikan musik dan melepaskan layanan. Tidak ada FATAL EXCEPTION pada log uji. Bukti final: `network-normal-results.json`, `network-crossfade-results.json`, serta log/snapshot dengan nama yang sama.

Pada percobaan crossfade pertama, antrean mencapai lagu kesepuluh tetapi syarat jumlah sampel buffering tidak terpenuhi: preload dibuat IDLE terlalu awal, lalu dibuat lagi ketika fade selesai. Fixture diperbaiki untuk membuat preload IDLE setelah posisi lagu keenam melewati 35 detik. Pengujian crossfade diulang pada APK yang sama dan seluruh pemeriksaan lulus. Kegagalan kontrol uji awal disimpan terpisah dalam `network-initial-results.json`.

Fixture jaringan hanya tersedia di debug. Fixture mengganti data source dalam factory produksi dengan HTTP loopback, sehingga menggunakan adapter, sesi, layanan, fokus, serta konfigurasi wake produksi. Server menahan setiap permintaan lagu ketujuh selama 12 detik. Ketika lagu keenam diputar, preload dibuat IDLE agar lagu ketujuh perlu dimuat lagi. Audio uji berdurasi 60 detik per lagu dan diputar 4x. Fixture tidak menggunakan resolver URL penyedia musik atau Wi-Fi perangkat asli. Konfigurasi debug mengizinkan HTTP hanya untuk `127.0.0.1`; konfigurasi jaringan release tetap HTTPS-only.

## APK akhir

`artifacts/playback-handoff-2026-10-09/Gratify-debug-playback-handoff-20261009.apk`, dibuat 9 Oktober 2026 pukul 09:47:57 WIB. Versi `2.1.0-dev` (61), paket `com.tan.gratify.dev`, ukuran 104.588.805 byte. SHA-256: `BC85CA6089631EA8FA266EDB35EAE7A741A3E6ED0F33A0CBEA193B647F26848E`. APK ini memuat penambahan lagu playlist, kapsul Search, dan perbaikan sebelumnya. APK yang diuji sama dengan salinan ini; metadata tersimpan di `apk.json`.

## Batas kesimpulan

Temuan status sesi dan celah wake pada ENDED/IDLE dapat dibuktikan dari implementasi dan pengujian terkontrol. Belum ada log dari kejadian asli pada iQOO, sehingga perubahan ini belum membuktikan bahwa seluruh penyebab berhenti saat lagu 6 → 7 telah hilang. Pengujian paling lanjut adalah minimal 10 lagu streaming Wi-Fi dengan layar mati pada perangkat tersebut menggunakan APK baru.

Media3 [menyimpan status dari callback individual](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionImpl.java). ExoPlayer [melepas penjaga wake/Wi-Fi di ENDED dan IDLE](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java). Kebijakan foreground sesi [mempertimbangkan niat play serta READY/BUFFERING dan memiliki timeout keterlibatan pengguna](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaNotificationManager.java), sehingga celah status singkat tidak otomatis membuktikan bahwa layanan langsung dihentikan Android.
