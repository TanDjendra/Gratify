# Gratify: tampilan Apple Music dari SimpMusic

Acuan dikonfirmasi pengguna: **Apple Music dari SimpMusic**.
Versi sumber: [`62e0073ba818d899b22c75909deb9b034516d5db`](https://github.com/maxrave-dev/SimpMusic/tree/62e0073ba818d899b22c75909deb9b034516d5db).

## Perubahan

Renderer adaptasi sebelumnya diganti dengan komponen presentasi dari sumber SimpMusic.
Pemutar menggunakan sampul penuh dengan fade ke mesh warna sampul, grabber, baris judul,
potongan lirik, progress, transport, pengatur volume perangkat, serta dock Lyrics / Output +
Listen Together / Queue. Ukuran, jarak, tipografi dan ikon berasal dari sumber tersebut;
berkas font Poppins Medium cocok byte demi byte dengan upstream.

Tab lirik menggunakan header ringkas, posisi baris aktif di dekat bagian atas, blur berdasarkan
jarak, animasi kata/karakter, titik instrumental untuk jeda panjang, caption penyedia di akhir
list, tombol share/fullscreen, serta kontrol yang menghilang otomatis setelah tidak disentuh.
Halaman lirik penuh menggunakan layout portrait/landscape upstream dan tombol favorit aslinya.

Loading tetap memakai renderer yang sama sejak bottom sheet mulai terbuka. Kontrol Play/Pause
mengikuti perilaku upstream: tetap tersedia saat buffering. Tidak ada pergantian ke renderer lama.

Mode tanpa animasi tetap tersimpan melalui DataStore. Ia menonaktifkan wipe kata, blur/fokus yang
bergerak, transisi potongan lirik dan gerakan latar halaman lirik penuh. Sorotan baris tetap mengikuti
waktu lagu. Pause membekukan animasi kata; seek menyesuaikan ulang sorotan, termasuk saat mundur di
kata yang sama. Lirik tanpa timestamp yang bermakna tetap dapat dibaca tanpa menganggap baris
terakhir sebagai baris aktif.

Berbagi maksimal tiga baris berturut-turut dan export PNG Gratify tetap memakai adapter platform
yang sudah ada. Tombol seek, favorit, YouTube likes (bila akun YouTube tersambung), antrean,
info, menu lagu dan tambah playlist terhubung ke pemutar Gratify.

## Batas kesamaan dan kelengkapan

Ini merupakan port presentasi player/lirik, bukan penyalinan seluruh aplikasi atau backend
SimpMusic. Konten sampul/lirik, bahasa, ukuran layar dan skala font mengikuti perangkat/datanya.
Identitas aplikasi dan kartu share tetap Gratify.

Gratify belum menyediakan routing output/Cast, Listen Together, pengaturan jeda lirik dan
romanisasi. Tombol output/kelompok menjelaskan bahwa fungsi belum tersedia; tidak menampilkan
perangkat atau sesi palsu. Row romanisasi tidak diisi dan offset lirik bernilai nol. Poster video
opsional animated artwork upstream tidak ada pada model canvas Gratify; sampul album tetap
menjadi fallback. Fitur tersebut membutuhkan integrasi tersendiri, bukan perubahan tata letak.

## Validasi

- Seluruh suite: **62 pemeriksaan Compose + 31 pemeriksaan data, 93 lulus**.
- Tujuh pemeriksaan player mencakup tab, antrean, seek, loading, pause/resume, seek mundur,
  mode tanpa animasi, berbagai ukuran layar/skala font, video, dan lirik penuh portrait/landscape.
- Build debug dan lint Android berhasil pada pemeriksaan lengkap.
- Bukti pengujian disimpan di `artifacts/simp-exact/full-suite-results/`, log `verification.log`,
  dan `test-summary.json`. Pemeriksaan akhir tombol favorit memakai `verification-final.log`.
- Pemeriksaan akhir tujuh tes player lulus kembali pada `verification-last-player.log`.
- Android: volume perangkat berubah dari 5 ke 11 melalui slider; mode tanpa animasi dan layar
  lirik penuh berhasil dibuka; batas tiga baris berbagi bekerja; PNG masuk ke chooser Android.
  Tidak ada penerima dipilih. Buffer crash Android kosong selama pemeriksaan.
- Ikon sistem terlihat putih pada player gelap. Pengaturan ikon sebelumnya dipulihkan saat
  player ditutup. Build/lint terakhir tercatat pada `verification-system-bars.log`.
- Detail emulator, bukti gambar/XML, batas pengujian serta ukuran/hash APK akhir tercatat pada
  `artifacts/simp-exact/validation.json`.

Pengujian memakai lirik/sampul lokal dalam aktivitas debug untuk memisahkan kegagalan tampilan
dari login/jaringan. Ini tidak mengukur baterai, performa pada HP pengguna, atau kecepatan penyedia
lirik. Angka performa laporan renderer sebelumnya tidak dianggap sebagai hasil port ini.

Volume menggunakan [`AudioManager.setStreamVolume`](https://developer.android.com/reference/android/media/AudioManager#setStreamVolume(int,%20int,%20int)),
sesuai komponen upstream. Pembatasan volume perangkat tetap mengikuti sistem Android.

Kode port dan catatan adapter tersedia di `composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/player/simp/README.md`.
