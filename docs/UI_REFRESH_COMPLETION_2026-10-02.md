# Penyelesaian pembaruan Gratify — 2 Oktober 2026

APK uji terbaru menggabungkan perbaikan profil, penelusuran musik, Library,
player, lirik, menu tindakan, serta perubahan lanjutan pada beranda dan Friends.

- Kartu lirik dan lirik layar penuh mengikuti palet sampul lagu. Warna terang
  digelapkan secukupnya agar teks tetap terbaca; terjemahan memakai warna dari
  palet yang sama. Artwork kosong memakai permukaan netral.
- Suasana hati dan genre memakai rak dua baris dengan ukuran kartu konsisten.
  Lebar kartu menyesuaikan layar serta ukuran teks. Semua kategori tetap dapat
  dijangkau dengan menggeser rak. Playlist tangga lagu memakai sampul persegi
  dan caption sejajar; artis teratas memakai foto bulat serta nomor peringkat.
- Friends memakai catatan ringkas, deretan avatar yang sedang mendengarkan,
  dan satu daftar aktivitas dengan pemisah sederhana. Tombol profil dan lagu
  terpisah. Catatan 24 jam, editor, tombol pencarian dan muat ulang, serta
  keadaan kosong, loading, dan pemulihan kegagalan tetap tersedia.
- Tata letak utama menyediakan ruang sesuai tinggi navigasi dan mini player
  sebelum menampilkan halaman. Ruang tersebut dihitung sekali, sehingga
  halaman detail yang tidak menerapkan padding sendiri juga terlindungi.
  Jarak bawah tetap pada profil dan hasil pencarian diganti jarak konten biasa.

Pemeriksaan lokal: 81 tes aplikasi (51 Compose dan 30 data), 22 tes layanan
YouTube, 20 pemeriksaan migrasi pin playlist, serta 16 pemeriksaan penyimpanan
OAuth lulus. Build Android dan lint berhasil; hasil lengkap serta pemeriksaan
Android tersimpan di `artifacts/final-refresh/verification.json`.

Migrasi pin playlist `202610020001_profile_playlist_pins.sql` telah diterapkan
ke Supabase aktif pada pekerjaan sebelumnya. Status publik playlist tetap
terpisah dari pilihan ditampilkan di profil.

Integrasi YouTube resmi **siap di kode, belum aktif di produksi**. Akses ke
proyek Google Cloud 1072882393306 belum tersedia. Migrasi OAuth kedua, Edge
Function, API, redirect, consent, dan secret memerlukan aktivasi sesuai
`docs/YOUTUBE_OAUTH_ACTIVATION.md`. Login Google biasa tetap tersedia selama
backend integrasi belum diaktifkan.

APK yang dibagikan adalah debug untuk pengecekan, dengan application ID
`com.tan.gratify.dev`. Pengujian dua perangkat fisik dan consent YouTube sungguhan
belum dilakukan.
