# Perbaikan kualitas Gratify — 2 Oktober 2026

Perubahan mengikuti sepuluh permintaan pengguna, memakai warna, tipografi,
komponen, dan bentuk Gratify. Referensi pola penelusuran musik berasal dari
[Spotify Home](https://newsroom.spotify.com/2023-03-08/new-home-page-scroll-clips-previews/).

## Perilaku profil dan pengaturan

- Pengaturan “yang bisa dilihat orang” membatasi isi bagi pengunjung. Pemilik
  tetap melihat bagian dan isi profilnya. Mode pratinjau menunjukkan placeholder
  saat isi disembunyikan; judul bagian tetap ada.
- Playlist profil dipetakan menggunakan identitas sinkronisasi tetap, bukan ID
  numerik perangkat. Penyimpanan menunggu hasil server; kegagalan tidak lagi
  menghilangkan pilihan diam-diam. Navigasi profil publik memakai ID shared yang benar.
- Status publik terpisah dari pin profil, sesuai rekomendasi yang disetujui pengguna.
  Menghapus pin mempertahankan status publik dan tautannya. Publikasi dan pin
  baru memakai transaksi atomik; playlist privat tidak bisa dipin.
- Foto Google tidak diimpor sebagai avatar Gratify. Tampilan dan sinkronisasi
  hanya memakai URL foto yang diunggah ke penyimpanan avatar aplikasi; foto
  aplikasi yang sudah tersimpan dapat dipulihkan saat masuk kembali.
- Filter, urutan, dan pilihan grid/daftar Library disimpan di DataStore.

## Tampilan dan rekomendasi

- Home memakai pilihan cepat yang responsif, hierarki judul yang jelas, dan
  jarak antarbagian yang konsisten. Teks besar pada ponsel memakai satu kolom.
- Home dan halaman jelajah Search memuat rekomendasi tambahan saat mendekati
  bagian bawah. Setelah cursor utama habis, sumber diperluas melalui mood,
  lagu dalam playlist/album, lalu lagu terkait. Identitas yang sama tidak
  digandakan; permintaan bersamaan dan pengulangan otomatis saat error dicegah.
- Feed mengikuti ketersediaan katalog layanan. Jika seluruh sumber habis,
  aplikasi menampilkan keadaan akhir yang jujur; jika jaringan gagal, tersedia
  tombol coba lagi. Tidak ada jaminan katalog eksternal benar-benar tak terbatas.
- Friends menampilkan kartu catatan, kelompok teman yang mendengarkan dan
  teman lainnya, keadaan kosong, serta pemulihan error. Tombol lagu dan profil
  memiliki tindakan terpisah.
- Now Playing memakai artwork, latar gelap, tipografi dan jarak Gratify; tombol
  informasi/antrean memiliki label aksesibilitas dan area sentuh 48 dp.
- Lyrics memakai hierarki baris aktif/nonaktif yang lebih jelas, spacing yang
  konsisten, terjemahan beraksen, dan parsing waktu seek yang aman.
- Menu tindakan dan antrean memakai permukaan netral, sudut dan tinggi baris
  yang seragam. Popup informasi lagu memakai cover, judul/artis, dan metadata
  berstruktur; deskripsi dan tindakan yang sudah ada tetap tersedia.

## Supabase aktif

`202610020001_profile_playlist_pins.sql` diterapkan ke proyek
`bnabldxsqpvkyqpjcsdv` setelah persetujuan eksplisit pengguna.
Verifikasi SQL aktif mengonfirmasi kolom, RPC pin, RPC sinkronisasi atomik,
RPC baca profil, trigger, grant, policy publik/owner, dan nol pin pada playlist
privat. Bukti tersimpan di `artifacts/music-quality/live-migration-verification.json`
dan `supabase-pin-migration-proof.jpg`.

## Validasi

- Gradle: `:composeApp:jvmTest :data:jvmTest :androidApp:assembleDebug :androidApp:lintDebug`.
- Hasil akhir: build berhasil, 69 tes aplikasi lulus (39 Compose + 30 data),
  nol kegagalan, dan lint nol error. Terdapat 115 peringatan lint yang tercatat
  di laporan build.
- Regresi mencakup pemulihan ID playlist, penolakan avatar Google, deduplikasi,
  pemilihan sumber playlist/album saat feed tidak berisi lagu, prioritas lagu
  terkait, serta penyimpanan Library setelah DataStore ditutup dan dibuka ulang.
- Dua puluh pemeriksaan PostgreSQL terisolasi (10 TEXT + 10 UUID) mencakup
  transaksi, privasi, tautan publik, pembatasan pemilik, rollback, dan idempotensi.
- Render komponen produksi pada lebar 390/900 dan skala font 1,3; kontrol
  Friends terpisah dan tindakan retry diuji.
- Emulator Android: Home/Search dengan data layanan nyata, Library setelah
  force-stop, Friends, pemutaran lagu dengan waktu yang bergerak, menu tindakan,
  popup informasi, dan lirik sinkron.
- Scroll panjang pada APK terakhir membuktikan rekomendasi berlanjut sampai
  lagu dari koleksi dan rantai lagu terkait; kegagalan sementara pulih melalui
  tombol coba lagi. Bukti: `runtime-verification.json` dan `discovery-android.png`.
- Log build: `artifacts/ui-refresh/final-verification.log`. Screenshot dan
  XML pemeriksaan ada di `artifacts/music-quality`.

Pengujian dengan dua perangkat fisik dan alur unggah avatar akun pengguna
belum dilakukan dalam pekerjaan ini. APK yang disediakan adalah debug untuk
pengecekan, dengan application ID `com.tan.gratify.dev`.
