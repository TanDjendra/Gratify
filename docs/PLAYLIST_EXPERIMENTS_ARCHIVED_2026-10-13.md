# Arsip kolaboratif, Blend, dan folder

Pemilik meminta rilis tanpa tiga fitur ini karena belum stabil. Mulai versi
2.1.4 (kode 65), `PlaylistFeaturePolicy.experimentalEnabled` bernilai `false`
untuk semua build. Arsip ini mempertahankan sumber fitur dan data server agar
pengembangan dapat dilanjutkan.

## Versi tanpa fitur eksperimen

- Menu “+” hanya menawarkan Playlist. Pembuatan memakai alur playlist biasa.
- Tombol undang dan daftar anggota tidak ditampilkan di halaman playlist.
- Folder dan Blend tidak ditampilkan di Pustaka. Playlist di dalam folder lama
  tetap terlihat di koleksi utama; penempatan folder di server tidak dihapus.
- Link undangan dan undangan lama dalam chat tidak menawarkan tindakan gabung.
- Rute pembuatan kolaboratif/Blend/folder dan folder lama menampilkan halaman
  fitur belum tersedia. Detail playlist biasa yang sebelumnya sudah disatukan
  tetap bisa dibuka, diedit, dimainkan, diunduh, dan diubah privasinya.
- Permintaan undang, gabung, promote, pengelolaan anggota, folder, dan penyegaran
  Blend diblokir dalam ViewModel. Pustaka tidak mengaktifkan polling fitur arsip,
  dan pencarian tidak meminta daftar workspace eksperimen tambahan.
- Profil, chat biasa, crop foto, cache artwork, sampul otomatis, privasi playlist,
  pemutaran, pencarian, unduhan, dan playlist biasa dipertahankan.

Tidak ada penghapusan akun, playlist, undangan, folder, ataupun migrasi server
mundur. Pembatasan ini berlaku pada aplikasi baru. Backend tetap menyimpan
implementasi fitur; aplikasi lama yang sebelumnya memiliki fitur tersebut tidak
dinonaktifkan secara global melalui perubahan ini.

## Melanjutkan pengembangan

Sumber utama berada di:

- `core/domain/.../utils/PlaylistFeaturePolicy.kt`
- `composeApp/.../ui/screen/library/PlaylistWorkspaceScreen.kt`
- `composeApp/.../ui/screen/library/WorkspacePresentation.kt`
- `composeApp/.../ui/screen/library/UnifiedPlaylistScreen.kt`
- `composeApp/.../viewModel/PlaylistWorkspaceViewModel.kt`
- migrasi workspace, invitation lifecycle, dan canonical playlist pada Supabase.

Salinan sumber dan tes fitur tersedia di
`artifacts/stable-release-20261013/playlist-experiments-source.zip`. Arsip tidak
memuat keystore, kata sandi, konfigurasi lokal, atau data pengguna.

Tes fitur eksperimen tetap dijalankan dengan parameter `experimentalEnabled=true`
atau `experimentalFeaturesEnabled=true` sehingga implementasinya tetap dapat
dikembangkan. Produksi memakai nilai default `false`. Untuk mengaktifkan kembali,
ubah kebijakan pusat lalu tinjau kembali alur rute/link, chat, pencarian, Pustaka,
perizinan server, dan uji dua akun sebelum menyiapkan rilis berikutnya.

## Bukti rilis

Build debug dan rilis versi 2.1.4 / kode 65 berhasil. Pemeriksaan terpilih
menjalankan 83 tes aplikasi dan 20 tes penyimpanan lokal; seluruh 103 tes lulus.
Log hasil akhir berada di `build-and-tests-final.log` (percobaan sebelumnya
tercatat terpisah). Lint vital rilis, optimasi R8, pemeriksaan tanda tangan,
identitas paket, dan alignment ZIP/native 64-bit 16 KB juga berhasil.

APK rilis memakai sertifikat successor Gratify yang sudah ada, paket
`com.tan.gratify.next`, label Gratify, dan tidak debuggable:

- `artifacts/stable-release-20261013/Gratify-2.1.4-release-arm64-v8a.apk`
- `artifacts/stable-release-20261013/Gratify-2.1.4-release-universal.apk`

Debug 2.1.4 terpasang dengan pembaruan yang mempertahankan data pada ponsel.
Menu “+” secara nyata hanya menawarkan Playlist; pilihan tersebut membuka
“Beri nama playlist-mu”, nilai awal “Playlist-ku No.1”, dan bisa dibatalkan tanpa
membuat koleksi. Pustaka kosong pada akun uji karena pemeriksaan baca saja
menemukan nol playlist biasa/cloud dan satu Blend yang diarsipkan; data Blend
tetap tersimpan. Tidak ada fatal exception yang ditemukan pada log proses
debug selama pemeriksaan ini. APK rilis bertanda tangan belum dipasang atau
diuji pemutaran lama di perangkat pada putaran ini. Percobaan pembukaan debug
pertama melaporkan timeout, meskipun proses dan UI kemudian aktif.

Log build, hasil tes, APK bertanda tangan, hash, pemeriksaan paket/debuggable,
kesesuaian sertifikat, dan alignment native disimpan di
`artifacts/stable-release-20261013/`. APK hanya disiapkan secara lokal; belum
diterbitkan ke situs atau toko aplikasi. Hasil build tidak berarti seluruh
aplikasi telah diaudit ulang untuk kelayakan rilis.
