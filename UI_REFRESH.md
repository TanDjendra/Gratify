# Pembaruan UI Gratify

Seluruh aplikasi memakai tema gelap, aksen hijau, tipografi Poppins, serta ukuran komponen yang konsisten. Referensi arah tampilan: [Spotify](https://newsroom.spotify.com/2026-04-16/new-tablet-app-experience/). Identitas aplikasi tetap Gratify.

## Struktur

- `DesignTokens.kt`: warna dan bentuk bersama.
- `Theme.kt` dan `Typo.kt`: tema untuk Android dan desktop, termasuk autentikasi, detail musik, pemutar, dialog, dan pengaturan.
- `ScreenHeader.kt`, `ChipGroup.kt`, `BottomNavScreen.kt`: komponen dan tujuan navigasi bersama.
- `AppNavigationGraph.kt`: satu sumber untuk navigasi utama; data beranda dibagikan kepada halaman pencarian.
- `MiniPlayer.kt`: kontrol pemutaran dengan pengumpulan progres yang terpisah dari informasi lagu.
- `HomeMoodFilters.kt` dan `HomeSectionHeading.kt`: filter dan judul bagian yang sama untuk beranda produksi dan pratinjau.
- `PlaybackControls.kt`: kontrol putar/jeda, lagu sebelumnya/berikutnya, acak, dan ulang; status aktif serta tombol utama mengikuti aksen aplikasi.
- `ArtworkIconButton.kt`: tombol di atas sampul dengan latar gelap agar tetap terbaca tanpa menangkap atau memburamkan gambar.

## Penerapan acuan pratinjau ke aplikasi

| Bagian | Penyelarasan |
| --- | --- |
| Beranda | Header, filter suasana, pilihan cepat, judul bagian, kartu rekomendasi, dan pemutar mini memakai komponen bersama dengan pratinjau. Data produksi tetap berasal dari repositori musik. |
| Pencarian dan koleksi | Filter, daftar, bentuk sampul, metadata, dialog, dan warna kategori mengikuti palet yang sama. |
| Album, artis, playlist, podcast | Warna permukaan, bentuk sampul, kontrol di atas gambar, tombol tindakan, dan daftar lagu diselaraskan. |
| Profil, teman, daftar pengikut | Warna netral, aksi mengikuti, status daring, permukaan dialog, dan tampilan avatar mengikuti token bersama. |
| Pemutar dan lirik | Tema bersama serta kontrol pemutaran yang sama; fungsi video, antrean, volume, dan pilihan latar tetap tersambung ke pemutaran. |
| Login dan verifikasi | Form, OTP, tombol, ukuran huruf, serta ruang untuk keyboard memakai tampilan gelap dan aksen aplikasi. |
| Pengaturan, notifikasi, analitik, kredit | Tipografi dan warna mengikuti tema bersama; bar halaman memakai latar yang terbaca. |
| Dialog dan menu lagu | Permukaan serta warna teks disatukan; favorit memakai hijau. Judul panjang memakai elipsis. |

Tema Android saat aplikasi mulai dibuka juga memakai latar gelap dan logo Gratify, sehingga tidak berpindah dari layar awal putih ke tampilan aplikasi gelap.

## Pemuatan dan interaksi

- Perubahan preferensi beranda digabung untuk menghindari beberapa pemuatan awal yang bersaing.
- Konten utama tampil sebelum rekomendasi artis selesai; bagian tambahan memiliki batas waktu sendiri.
- Konten yang sudah tampil dipertahankan selama refresh. Permintaan pagination dibatasi satu pada satu waktu.
- Pencarian lama dibatalkan saat kueri/filter berubah. Hasil kategori yang sudah siap dapat tampil terlebih dahulu.
- Saran pencarian ditunda 300 ms dan dibatalkan saat teks berubah. Riwayat memakai satu pengamat.
- Efek blur pada shell dan header serta animasi teks daftar dikurangi. Dialog promosi otomatis di beranda dihapus.
- Form akun dapat digulir dan menyediakan ruang untuk keyboard, termasuk verifikasi OTP.
- Penyimpanan sesi dan verifikasi OAuth ditentukan secara eksplisit untuk setiap platform. Modul aplikasi tetap tersedia saat activity dibuat ulang; splash menunggu pemulihan sesi sebelum memilih tujuan.
- Dialog pengaturan memakai label dari Compose; pembacaan preferensi pencarian dan penyimpanan notifikasi tidak lagi memblokir thread antarmuka.

## Pemeriksaan visual

`DesignPreviewActivity` hanya tersedia dalam build debug, menggunakan komponen aplikasi dengan data contoh tanpa masuk ke akun. Activity ini membantu memeriksa header, filter, bar navigasi, item pustaka, serta kontrol pemutar. Activity ini tidak disertakan pada build release.

Pratinjau beranda memakai `QuickPicksContent` yang juga digunakan oleh `HomeScreen`, serta kartu rekomendasi `HomeItemContentPlaylist` dan `HomeItemSong`. Nama lagu, playlist, dan sampul merupakan data contoh khusus debug. Daftar vertikal `LibraryListItem` hanya menggambarkan halaman koleksi, bukan beranda.

```powershell
adb shell am start -n com.tan.gratify.dev/com.tan.gratify.DesignPreviewActivity
```

Untuk membuka shell aplikasi produksi melalui activity debug (tanpa memalsukan data akun), gunakan tambahan `--ez live true`. Keadaan akun kosong dan jaringan mengikuti lingkungan pengujian. Mode ini hanya tersedia pada build debug; akses akun dalam aplikasi release tetap mengikuti alur login.

Pemeriksaan jaringan, masuk ke akun, dan kelancaran pemutaran pada ponsel nyata tetap perlu dilakukan dengan akun serta perangkat pengguna. Perubahan UI ini tidak menerapkan hotfix SQL pada Supabase aktif.

## Pemeriksaan awal — 30 September 2026

- Build APK debug, kompilasi JVM, dan lint Android berhasil.
- Tiga pengujian JVM untuk pemeriksaan versi lulus; pengujian ini tidak mengukur performa UI.
- Lint: 0 error, 122 peringatan yang masih perlu ditinjau terpisah.
- Emulator Android 37: aplikasi asli berhasil dibuka, form email dapat digulir saat keyboard muncul, serta perubahan ukuran huruf 1,3× berhasil tanpa kegagalan activity.
- Pratinjau dengan data contoh: empat tab dan tombol putar/jeda berhasil; tidak ada `FATAL EXCEPTION` pada pemeriksaan terakhir.
- Gambar dan ringkasan pemeriksaan tersedia di `artifacts/ui-refresh`. Kelancaran desktop belum diuji secara visual, dan performa ponsel nyata belum diukur.

APK universal untuk pengujian: `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`.

## Verifikasi penyelarasan — 1 Oktober 2026

- Build terakhir: `:composeApp:jvmTest :androidApp:assembleDebug :androidApp:lintDebug` berhasil (3 menit 2 detik).
- Tiga pengujian JVM lulus; lint memiliki 0 error dan 121 peringatan. Pengujian JVM ini memeriksa versi aplikasi, bukan performa UI.
- Shell aplikasi asli melalui mode debug `live`: beranda memuat lagu, sampul, dan playlist komunitas dari repositori musik. Gambar `live-home-ready.png` menggunakan data tersebut, bukan data contoh pratinjau.
- Navigasi beranda, pencarian, pustaka, teman, drawer, pengaturan, dan profil telah diperiksa pada emulator Android 37.
- Pustaka tidak lagi menampilkan pemilik `null`, memiliki filter Semua, serta mode daftar/grid dengan ukuran kolom adaptif.
- Beranda dan pengaturan diperiksa pada skala huruf 1,3×. Judul panjang menggunakan elipsis dan bagian daftar menyesuaikan tinggi teks.
- Beranda diperiksa pada viewport ponsel 720×1600/density 320 dan tablet 1920×1200/density 240. Tablet memakai navigasi samping dan beberapa kolom pilihan cepat.
- Tidak ada entri crash Android pada pemeriksaan terakhir. Efek blur dan animasi teks saat menjelajah dikurangi; kelancaran pada ponsel pengguna tetap perlu diukur.
- Bukti visual: `live-home-ready.png`, `live-library.png`, `live-library-grid.png`, `live-search.png`, `live-friends.png`, `live-settings.png`, `live-profile.png`, `live-home-large-font.png`, `live-settings-large-font.png`, dan `live-tablet-home.png` di `artifacts/ui-refresh`.

Pemutaran dengan akun pengguna, sinkronisasi data pribadi, dan tampilan desktop saat dijalankan belum diverifikasi. Hotfix Supabase aktif tetap belum diterapkan.
