# Perbaikan navigasi Gratify — 3 Oktober 2026

Seluruh **11 temuan audit navigasi sudah ditangani dalam sumber aplikasi**. Navigasi tab sekarang memakai aturan yang sama, halaman hasil tidak menghapus asal pengguna, dan pekerjaan yang belum selesai lebih terlindungi.

**Validasi: 60 pemeriksaan JVM lulus, 0 kegagalan/error/skipped; kompilasi Android berhasil.** Pengujian mencakup controller navigasi asli, render komponen, dispatcher Back, state draft, definisi pencarian Koin, dan regresi sesi offline. Belum membuat APK baru atau menjalankan perjalanan ini di HP.

## Hasil untuk pengguna

- Berpindah dari tab lain kembali ke posisi terakhir tab tersebut. Mengetuk tab yang sudah aktif dari halaman detail kembali ke halaman utama tab; mengetuk lagi di halaman utama menggulir ke atas. Penanda tab mengikuti riwayat aktif di ponsel, tablet, dan desktop.
- Membuat playlist dari Library → menyimpan → kembali sekarang menuju Library. Pilihan lagu dipertahankan ketika entry dibuat ulang; ketukan simpan berulang atau pemulihan hasil yang sudah tersimpan tidak membuat playlist kedua. Meninggalkan pilihan yang belum disimpan meminta konfirmasi; tombol kembali dibatasi selama penyimpanan berlangsung.
- Membuka artis dari player Apple Music menutup player sebelum membuka detail. Aksi fullscreen video memakai aturan penutupan yang sama.
- Tombol sosial yang membutuhkan koneksi Gratify tidak membuka izin YouTube. Saat identitas cloud/koneksi belum tersedia, pengguna mendapat penjelasan dan pilihan mencoba lagi; akses unduhan tetap mengikuti sesi offline yang sudah diperbaiki sebelumnya.
- Temukan teman langsung meminta mode pencarian pengguna. Pemilih artis memiliki pencarian tersendiri dan tombol keluar yang selalu terlihat.
- Membuka profil dari percakapan mempertahankan percakapan asal. Draft pesan, penanda pesan terakhir, dan posisi list disimpan untuk dipulihkan ketika kembali.
- Editor story menyimpan perubahan lagu, detik awal, durasi 8/15/30 detik, caption, dan audience. Setelah ditutup, tersedia **Lanjutkan draft story** dan **Hapus draft**. Pilihan lirik baru meminta persetujuan sebelum mengganti draft yang berbeda. Draft dibersihkan setelah publikasi sukses atau penghapusan yang disetujui.
- Tab inbox Friends dipertahankan. State story/chat dibatasi pada akun lokal pemiliknya dan dibersihkan ketika akun berubah.
- Back pada kategori Pengaturan kembali ke daftar kategori; pada lapisan edit/pratinjau/QR profil menutup lapisan tersebut dahulu. Handler menyerahkan Back pertama ke keyboard ketika inset keyboard masih terlihat. Panel player tablet terdaftar setelah NavHost agar memperoleh prioritas kembali; drawer tetap memperoleh prioritas ketika terbuka.

## Penyelesaian per temuan

| Temuan | Perubahan |
|---|---|
| N01 — login Friends salah tujuan | Panggilan sosial menggunakan status akun lokal dan identitas cloud. Pemulihan koneksi ditangani Main, sementara logout/akun tidak sah tetap mengikuti coordinator sesi. Tidak ada lagi navigasi login YouTube dari tindakan Friends. |
| N02 — artis tertutup player | Aksi player memakai `navigateFromPlayer`: tutup player, lalu buka detail dengan singleTop. |
| N03 — onboarding pada host salah | Host internal hanya mendaftarkan halaman pengelolaan YouTube; graph onboarding lengkap tetap di host utama yang memiliki MainDestination. |
| N04 — Library hilang setelah membuat playlist | `finishPlaylistCreation` mengganti entry editor saja, mempertahankan halaman asal. |
| N05 — penanda tab keliru | Bottom bar, rail, dan sidebar desktop menentukan pemilik tab dari seluruh riwayat aktif, termasuk detail yang dipulihkan. |
| N06 — tombol lintas tab berbeda aturan | `openMainTab` dipakai bottom bar/rail/desktop, pencarian dari Library, Temukan teman, dan berbagi lirik ke story. Tujuan khusus bisa meminta halaman utama tab tanpa menggandakan root. |
| N07 — tombol kembali tidak konsisten | Handler lapisan diterapkan pada kategori Pengaturan, edit/pratinjau/QR profil, editor playlist, pencarian yang terbuka, dan panel player tablet. Dialog/bottom sheet tetap memakai mekanisme Back modalnya. |
| N08 — pemilih artis mengubah Search | ViewModel pemilih artis memiliki qualifier dan scope entry tersendiri; pencarian utama tetap menyimpan state sendiri. |
| N09 — profil memutus konteks chat | Profil dibuka tanpa menutup state percakapan. Dialog chat ditampilkan hanya ketika Friends menjadi tujuan aktif; konteks pesan dan posisi disimpan. |
| N10 — draft hilang | SavedStateHandle dipakai untuk playlist dan state navigasi Friends; perubahan story disimpan langsung dari tindakan editor. Pemulihan tetap memeriksa pemilik akun, dan draft baru tidak menimpa draft lama diam-diam. |
| N11 — pemilih artis tanpa jalan keluar terlihat | Tombol tutup selalu tersedia; Selesai juga tersedia tanpa mengharuskan pengguna memilih artis lebih dahulu. |

Visibility bottom bar/rail sekarang ditentukan dari tujuan, sehingga tidak lagi ditulis bersamaan oleh efek route dan lifecycle halaman YouTube. Arah transisi kembali pada host utama dibuat berlawanan dengan transisi maju. Halaman koneksi YouTube di dalam aplikasi memakai label **Kembali**.

## Bukti pemeriksaan

| Pemeriksaan | Jumlah | Yang diverifikasi |
|---|---:|---|
| NavigationRegressionTest | 12 | Riwayat Library, restore detail Search, reselect tab, tidak ada root duplikat, permintaan pencarian akun, graph YouTube internal dan onboarding utama, Back detail, penanda bottom bar produksi, penutupan player sebelum navigasi, dan aturan akun sosial. |
| LayerBackHandlerTest | 1 | Dispatcher Back asli: lapisan teratas ditutup sebelum halaman di bawah; gesture yang dibatalkan tidak menjalankan aksi kembali. |
| FriendsNavigationStateTest | 6 | Pemulihan draft/tab/chat, isolasi pemilik akun, pembersihan draft sukses, data rusak, serta penggantian draft yang memerlukan persetujuan. |
| PlaylistDraftStateTest | 3 | Pemulihan pilihan tanpa duplikasi, perlindungan simpan berulang/setelah restore, dan state rusak. |
| SearchPickerIsolationTest | 1 | Definisi Koin produksi benar-benar membuat pencarian pemilih terpisah dan tidak menimpa hasil pencarian utama. |
| BottomSafeContentTest | 1 | Ruang bawah konten mengikuti perubahan tinggi bar pada dua ukuran render. |
| ProfileLinksTest | 4 | Tautan/QR profil valid, normalisasi desktop, dan penolakan tautan salah. |
| FriendsSocialRenderTest | 7 | Inbox, chat, editor/viewer story, durasi/preview, draft dari share lirik, dan keadaan permintaan pesan. |
| StoryLyricsPreviewTest | 5 | Batas potongan, waktu lirik, rich markup, lirik tanpa waktu, dan durasi selesai. |
| SearchUsersPagingTest | 4 | Pagination akun, batas query, kegagalan, dan respons yang sudah tidak relevan. |
| AccountSessionRecoveryTest | 16 | Perbaikan navigasi tidak merusak pemulihan akun/offline atau pemisahan koneksi YouTube dari akun Gratify. |
| **Total** | **60** | **0 kegagalan, error, atau skipped.** |

[Log validasi](D:/Tan/Gratify/artifacts/navigation-fix-2026-10-03/verification.log), [rekap hasil](D:/Tan/Gratify/artifacts/navigation-fix-2026-10-03/validation.json), dan [laporan audit awal](D:/Tan/Gratify/docs/NAVIGATION_UX_AUDIT_2026-10-03.md).

## Batas praktis

Uji controller memakai fixture graph dengan jenis tujuan produksi dan helper produksi; graph autentikasi/YouTube yang diuji memakai fungsi registrasi produksi. Ini tidak menjalankan semua layar aplikasi sebagai satu perjalanan Android. Uji draft mensimulasikan pemulihan SavedStateHandle, bukan penghentian proses oleh Android. Uji Back memakai dispatcher asli pada JVM, bukan gesture layar HP.

Sesudah ada permintaan build dan test perangkat, perlu memastikan Back gesture/3 tombol, keyboard, rotasi, penghentian proses, cold/warm deep link, perpindahan cepat, dan tampilan tablet bekerja dalam APK. Tidak ada perubahan server/migrasi Supabase pada pekerjaan ini.

APK debug yang ada tetap keluaran **3 Oktober 2026 pukul 13:59:51**, berukuran **98.392.989 byte**. APK tersebut belum berisi perbaikan navigasi ini atau perbaikan sesi offline terakhir; kompilasi sumber Android tidak menghasilkan APK pengganti.
