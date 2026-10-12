# Berbagi lirik ke Instagram, WhatsApp, dan Story Gratify

Fitur berbagi lirik sekarang menyediakan pilihan tujuan pada halaman pratinjau kartu. Pengguna memilih maksimal tiga baris berurutan, menyesuaikan warna kartu, lalu memilih Instagram, WhatsApp, Story Gratify, aplikasi lainnya, atau simpan gambar.

Instagram dan WhatsApp menerima gambar PNG kartu lirik melalui alur berbagi aplikasi masing-masing. Ini bukan unggahan otomatis atau video dengan audio. WhatsApp Business juga didukung sebagai pilihan cadangan. Jika aplikasi tujuan belum terpasang, pesan menjelaskan pilihan simpan gambar atau aplikasi lain. PNG menggunakan FileProvider dan izin baca sementara, mengikuti [panduan berbagi berkas Android](https://developer.android.com/training/secure-file-sharing/share-file).

Story Gratify membuka editor musik yang sudah ada. Lagu, teks lirik pilihan sebagai catatan, dan titik mulai dikirim ke editor. Lirik tersinkron memakai waktu baris pertama yang dipilih; lirik tanpa sinkron memakai posisi pemutaran. Durasi awal dipilih dari 8, 15, atau 30 detik dan dibatasi durasi lagu. Pengguna tetap dapat mengubah potongan, melihat pratinjau musik dan lirik, serta memilih teman atau publik sebelum menekan Bagikan. Story tetap berlaku 24 jam.

Batas catatan story tetap 200 karakter. Pilihan terlalu panjang ditolak dengan penjelasan; teks tidak dipotong diam-diam. Pilihan tersebut tetap bisa dibagikan sebagai kartu gambar ke aplikasi lain. Durasi lagu yang belum diketahui dimuat dahulu tanpa menghapus catatan, waktu awal, atau pilihan penonton. Tidak memerlukan migrasi Supabase tambahan.

## Validasi

- 26 pengujian lulus: 9 berbagi lirik, 7 Friends/story, dan 10 player Apple Music.
- Pengujian mencakup pemilihan tujuan, layar 320 px dengan ukuran huruf 150%, batas teks, waktu lirik, pembatasan potongan di akhir lagu, serta pemuatan durasi yang mempertahankan isi draft.
- Android debug APK dan pemeriksaan lint berhasil. Bukti dan hash APK tercatat di `artifacts/simp-exact/share-destinations-validation.json`.
- Manifest APK memuat visibilitas paket Instagram, WhatsApp, dan WhatsApp Business.
- Instagram dan WhatsApp tidak tersedia di perangkat uji terhubung, sehingga pengiriman nyata belum diuji. Tidak ada pesan atau story nyata yang dipublikasikan saat pengujian.

APK: `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`.
