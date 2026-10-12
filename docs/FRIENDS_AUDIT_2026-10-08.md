# Audit Friends — 8 Oktober 2026

## Konsep

Friends menjadi ruang berbagi musik dengan tiga tujuan yang jelas:

- **Aktivitas** sebagai halaman awal: bagikan potongan lagu, catatan 24 jam, story teman, musik yang sedang dibagikan, dan daftar teman.
- **Pesan**: percakapan, kontak teman, serta permintaan pesan yang terpisah.
- **Jelajahi**: story musik publik dengan judul lagu, artis, pemilik, durasi potongan, dan caption.

Hubungan pertemanan tetap saling mengikuti. Editor story, potongan 8/15/30 detik, pratinjau lirik/audio, percakapan, dan penerimaan permintaan pesan tetap menggunakan alur yang sudah ada. Tidak ada migrasi backend atau pustaka baru.

## Temuan dan perbaikan

1. Halaman awal sebelumnya berpusat pada inbox. Musik teman sekarang langsung terlihat dan memiliki aksi profil, putar, dan pesan yang terpisah.
2. Lagu pertama dari playlist publik sebelumnya dipakai sebagai pengganti aktivitas mendengarkan. Pengambilan tersebut dihapus: playlist publik bukan bukti lagu yang sedang diputar.
3. Aktivitas sekarang menghormati `showRecentArtists`, memerlukan metadata pemutaran lengkap, dan hanya aktif untuk waktu valid hingga 10 menit. Aktivitas diperbarui selama halaman aktif; tampilan juga mengevaluasi ulang umur aktivitas setiap 30 detik.
4. Daftar mengikuti/pengikut dibaca dengan hasil kegagalan yang eksplisit dan pagination. Gangguan koneksi tidak lagi berubah menjadi daftar kosong yang terlihat sah; data yang sudah diketahui dipertahankan dengan opsi mencoba lagi.
5. Catatan sebelumnya dapat terlihat tersimpan ketika repository mengembalikan kegagalan. Dialog kini menunggu hasil penyimpanan, mempertahankan isi saat gagal, menampilkan kesalahan, dan mencegah simpan ganda. Pembacaan profil yang tertunda tidak boleh menimpa perubahan catatan yang lebih baru.
6. Aktivitas dan penyimpanan dijaga berdasarkan akun. Draft, dialog, dan state navigasi dibersihkan saat ganti akun/keluar. Penyimpanan akun lama tidak dapat menutup editor akun berikutnya.
7. Jelajahi kini membuka tepat story yang dipilih dan memfilter audience publik. Story yang kedaluwarsa tidak ditampilkan. Tab yang dipulihkan setelah aplikasi dibuat ulang meminta feed yang sesuai dengan tab tersebut.
8. Header mengikuti pola halaman utama Gratify. Konten desktop dibatasi 760 dp; tab dapat digeser pada layar kecil/tulisan besar. Ruang bawah menggunakan padding pemutar/navigasi dari halaman utama.
9. Label baru tersedia dalam bahasa Indonesia dan Inggris. Artwork yang belum tersedia memakai ikon musik yang netral.

## Validasi

- **27 pengujian Friends lulus**: 6 pengujian aktivitas/privasi/pagination/kegagalan/catatan/pergantian akun, 7 state navigasi, 2 penanganan service, dan 12 render/interaksi.
- Render diuji pada lebar 320 dp dengan font 150%, 360/390 dp, serta desktop 900/1100 dp. Pengujian memeriksa pemisahan aksi profil/putar/pesan, kondisi masuk/loading/error, batas editor catatan, story tepat yang diklik, audience publik, serta kontak terakhir yang dapat digulir melewati ruang pemutar/navigasi.
- `:androidApp:assembleDebug` dan `:desktopApp:compileKotlinJvm` berhasil bersama seluruh pengujian terarah; hasil final ada di [log validasi](../artifacts/friends-audit-2026-10-08/validation-final.log).
- Smoke test Android 17/API 37 dengan halaman memori 16 KB: fixture komponen produksi dibuka dalam bahasa Indonesia, lalu tab Pesan, Permintaan, dan Jelajahi diperiksa.
- Halaman Friends sebenarnya juga dibuka melalui navigasi utama pada APK final. Kondisi belum memiliki teman dan ruang navigasi bawah tampil benar; tidak ditemukan crash pada pemeriksaan ini. [Tangkapan halaman aktual](../artifacts/friends-audit-2026-10-08/android-production-empty.png).
- Pratinjau: [Aktivitas Android](../artifacts/friends-audit-2026-10-08/android-activity-id.png), [Pesan](../artifacts/friends-audit-2026-10-08/android-messages-id.png), [Permintaan](../artifacts/friends-audit-2026-10-08/android-requests-id.png), [Jelajahi](../artifacts/friends-audit-2026-10-08/android-explore-id.png), [font besar](../artifacts/friends-audit-2026-10-08/activity-320.png), [desktop](../artifacts/friends-audit-2026-10-08/activity-1100.png), dan [sebelumnya](../artifacts/friends-audit-2026-10-08/friends-before.png).

Semua data teman, pesan, dan story dalam fixture adalah sintetis. Pengujian ini tidak mengirim pesan, mempublikasikan story, atau mengubah akun produksi.

Perubahan masih di workspace lokal; belum dirilis. Pengiriman dua akun nyata, notifikasi produksi, dan sinkronisasi lintas perangkat memerlukan pemeriksaan pada rilis yang memuat perubahan ini.
