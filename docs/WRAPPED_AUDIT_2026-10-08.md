# Audit Wrapped Gratify — 8 Oktober 2026

Wrapped diperbarui dari daftar statistik menjadi perjalanan musik dengan 7 halaman yang dibaca manual, pilihan tahun, playlist hingga 50 lagu teratas, dan kartu gambar yang dapat dibagikan. Tampilan memakai warna dan tipografi Gratify, dengan motif piringan musik. Perubahan berada pada aplikasi dan database lokal; tidak ada deployment backend atau pengiriman pesan kepada pengguna lain.

## Temuan dan perbaikan

- **Rekap sulit dibaca sebagai cerita.** Alur baru: pembuka → waktu dengar → lagu → artis → kebiasaan → hari paling aktif → kartu rekap. Ada penanda halaman, sebelumnya/lanjut, tutup cerita, dan kembali ke rekap. Halaman tidak berpindah otomatis. Konten dapat digulir; tombol navigasi disusun vertikal pada layar sempit dengan teks besar.
- **Tahun dan sesi pengguna membutuhkan pembatasan yang jelas.** Riwayat diambil dengan satu kueri untuk 1 Januari inklusif sampai 1 Januari tahun berikutnya eksklusif. Hasil tahun/akun sebelumnya tidak dapat mengganti permintaan yang lebih baru. Data, tindakan playlist, dan cerita dibersihkan saat akun memuat ulang. Pemilik database diperiksa sebelum penerbitan hasil dan penyimpanan playlist. SQLite menyimpan tanggal sebagai milidetik epoch; daftar tahun memakai konversi epoch yang sesuai, bukan potongan teks tanggal.
- **Identitas lagu tidak boleh bergantung pada judul.** Lagu dengan judul sama tetap dihitung terpisah menggunakan ID. Seri peringkat ditentukan secara konsisten. Metadata dibaca dalam kelompok maksimal 200 ID; perhitungan peringkat berjalan di luar thread antarmuka. Hilangnya detail lagu tidak menghapus waktu dengar dari ringkasan.
- **Pemutaran sebelumnya dicatat setelah 20% durasi lagu.** Android dan desktop kini memakai minimal 30 detik waktu dengar nyata, termasuk lagu yang metadata durasinya belum tersedia. Penghitung waktu yang sudah ada memakai waktu audio bermain, sehingga jeda dan perpindahan posisi tidak menambah waktu dengar. Pencatatan tetap mengikuti pilihan pengguna di Pengaturan.
- **Kegagalan belum memberi alur pemulihan yang lengkap.** Pemuatan memiliki batas waktu dan tombol coba lagi. Riwayat kosong, pencatatan mati, detail tidak lengkap, kegagalan playlist, dan kegagalan berbagi memiliki pesan yang jelas. Mengaktifkan riwayat tetap merupakan pilihan pengguna; Wrapped tidak menyalakannya otomatis.
- **Belum ada hasil yang bisa disimpan atau dibagikan.** Playlist menyimpan maksimal 50 lagu teratas yang detailnya tersedia ke pustaka akun aktif. Ketukan ganda dikunci saat menyimpan; playlist dengan judul dan urutan lagu sama digunakan kembali, sedangkan playlist pengguna yang berbeda tidak ditimpa. Setelah selesai, tombol membuka playlist yang sebenarnya. Berbagi hanya mengekspor kartu ringkasan, tanpa tombol navigasi atau identitas akun. Android membuka pemilih aplikasi; desktop menyalin gambar untuk ditempel. Status siap tidak menyatakan gambar telah terkirim kepada orang lain.
- **Teks layar hanya tersedia dalam satu bahasa.** Teks Wrapped baru tersedia dalam bahasa Inggris dan Indonesia, termasuk penjelasan perhitungan dan status kegagalan.

## Definisi data

- Waktu dengar = jumlah detik valid yang tersimpan; menit dibulatkan ke bawah. Waktu kurang dari satu menit ditampilkan sebagai detik.
- Pemutaran = sesi tersimpan dengan minimal 30 detik. Lagu diurutkan menurut pemutaran, lalu waktu, lalu ID.
- Artis diurutkan menurut waktu dengar. Setiap artis kolaborasi mendapat kredit waktu lagu; kredit ganda untuk artis yang sama dalam satu lagu tidak dijumlahkan dua kali. Total waktu artis kolaborasi dapat melampaui waktu dengar pribadi.
- Hari aktif, hari terlama, rentetan hari, bulan, dan kebiasaan waktu memakai timestamp sesi tersimpan. Empat rentang waktu adalah 00–06, 06–12, 12–18, dan 18–24. Timestamp merupakan waktu pencatatan sesi, bukan catatan per detik sepanjang lagu.
- Rekaman kosong, durasi dengar nol/negatif, rekaman lebih dari 24 jam, rekaman masa depan, dan ID rekaman duplikat tidak masuk ringkasan.
- Tahun berjalan diberi label **rekap sementara**. Tahun lampau dibaca dari riwayat yang masih tersedia; hasil belum dibekukan menjadi arsip server permanen.

## Hubungan dengan Spotify Wrapped dan batas sistem

Konsep cerita musik, peringkat, waktu dengar, dan hasil berbagi mengikuti pola [pengalaman Wrapped resmi](https://newsroom.spotify.com/2025-12-03/2025-wrapped-user-experience/). Pemisahan lagu berdasarkan pemutaran dan artis berdasarkan waktu juga mengacu pada [penjelasan metodologi Spotify](https://newsroom.spotify.com/2025-12-05/wrapped-methodology-explained/), dengan rumus Gratify dijelaskan sendiri di aplikasi.

Data Gratify saat ini berasal dari riwayat lokal akun aktif pada perangkat ini. Snapshot pustaka menjaga riwayat antar-akun, tetapi bukan agregasi riwayat lintas perangkat di server. Pencatatan lama dengan aturan 20% bisa tidak lengkap; riwayat yang dimatikan, dihapus, atau belum disimpan tidak dapat direkonstruksi. Sesi disimpan ketika pemain berganti lagu; sesi yang masih berjalan atau aplikasi ditutup paksa sebelum penyimpanan dapat belum muncul. Nama/detail artis memakai metadata lagu yang tersedia.

Sistem yang setara untuk lintas perangkat membutuhkan pencatatan server dengan identitas sesi yang unik, sinkronisasi yang tahan pengiriman ulang, aturan izin/retensi, agregasi per tahun, serta snapshot tahunan yang dibekukan. Persentil seperti “top 1% pendengar”, genre, atau usia selera membutuhkan data pembanding dan metadata tambahan; fitur ini tidak ditampilkan dengan angka buatan.

## Verifikasi

**47 tes relevan lolos**, terdiri dari 4 tes domain (ambang pencatatan dan penghitung waktu), 4 tes database lokal, 7 tes ringkasan, 12 tes model Wrapped, 7 tes tampilan, dan 13 tes navigasi yang sudah ada. Pengujian mencakup batas tahun, konversi epoch, 1201 rekaman dengan timestamp sama, snapshot antar-akun, 451 lagu dalam kelompok metadata, pemutaran singkat, data rusak/masa depan/duplikat, judul sama, kolaborasi, urutan permintaan, gagal/coba lagi, simpan ganda, penolakan tulis saat pemilik akun berubah, dan playlist yang tetap tersedia meskipun metadata 55 lagu berperingkat lebih tinggi hilang.

Tampilan produksi dirender pada lebar 320 px dengan teks 150%, 390 px, dan 1100 px. Ketujuh halaman, tombol besar, navigasi manual, kembali/tutup, pemuatan ulang akun, riwayat kosong, kegagalan, pemilihan tahun, kartu ekspor, dan tombol berbagi yang dapat dicapai lewat gulir diuji. `share-card.png` merupakan PNG yang dihasilkan oleh jalur penangkapan kartu produksi, bukan gambar ilustrasi. Statistik dan nama pada bukti visual adalah data contoh sintetis.

Build Android debug dan kompilasi desktop **berhasil**. Build terakhir tercatat di `playlist-metadata-verification.log`; perbaikan navigasi/ukuran teks dan tes tampilannya di `final-build.log`. Hasil XML masing-masing suite disimpan di `test-results/`; `test-summary.json` mencatat 47 tes, 0 kegagalan, dan 0 error.

APK universal terbaru: `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`, versi **2.1.0-dev (61)**, dibuat **8 Oktober 2026 pukul 23.54.11 WIB**, ukuran **104.588.277 byte**. SHA-256 dan waktu build disimpan dalam `apk.json`. APK terakhir berhasil dipasang ulang dan dibuka di emulator Android API 37 dengan ukuran halaman memori 16 KB.

**17 pemeriksaan Android langsung berhasil**, diringkas dalam `android-smoke-summary.json`: ketujuh halaman, judul pembuka tunggal, lagu kelima lewat gulir, sebelumnya, tombol kembali Android, pemilihan tahun lampau tanpa label sementara, gagal/coba lagi/buka playlist, gagal berbagi/coba lagi sampai pemilih aplikasi Android, riwayat kosong dan tindakan pengaturan, kegagalan pemuatan dan pemulihan, teks 150% dengan tombol berbagi yang dapat dicapai, ekspor PNG, pemasangan APK terakhir, serta buffer crash kosong (**0 karakter**). Tidak ada penerima dipilih atau pesan dikirim. PNG dari jalur berbagi Android disalin untuk diperiksa: `android-share-card.png`, **954 × 1254 px**, berisi kartu rekap lengkap tanpa navigasi. Emulator audit dihentikan dan ukuran teks dikembalikan setelah pemeriksaan.

Pemeriksaan Android menggunakan komponen produksi pada fixture debug dengan statistik sintetis. Kegagalan simpan/berbagi dan tujuan pengaturan/playlist pada fixture dikendalikan oleh callback contoh; pemilih berbagi serta ekspor gambar memakai implementasi Android sebenarnya. Perhitungan, panggilan penyimpanan, perlindungan akun, dan pemulihan diuji terpisah pada model; kueri dan snapshot riwayat diuji dengan Room/SQLite lokal. Alur ini tidak membuktikan rekap akun produksi yang sedang masuk atau agregasi server lintas perangkat. Bukti berada di `artifacts/wrapped-2026-10-08/`.
