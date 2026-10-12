# Lirik per baris — 8 Oktober 2026

Keputusan pengguna: memakai lirik per baris karena tampilan berjalan per kata/suku kata belum stabil. Mode standar yang sudah ada digunakan untuk pemutar dan layar penuh.

## Perubahan

- Tampilan utama, tab lirik, layar penuh, dan renderer lama memakai mode per baris sebagai bawaan. Tab serta layar penuh memaksa renderer per baris, termasuk jika state lama masih berisi pilihan animasi.
- Preferensi `lyrics_motion = animated` lama tidak lagi mengaktifkan efek kata, termasuk setelah pengaturan dipulihkan. Nilai awal ViewModel juga false sehingga animasi tidak muncul sesaat ketika preferensi dibaca. Setter lama dipertahankan untuk kompatibilitas dan hanya menyimpan `no_animation`.
- Tombol animasi di tab lirik dihapus. Pengaturan kini menampilkan informasi **Tampilan lirik → Standar · per baris**, tanpa sakelar. Label sinkronisasi per baris juga digunakan untuk sumber yang membawa timestamp kata.
- Strip lirik pada halaman pemutar menampilkan baris utuh tanpa sweep kata. Penanda waktu kata disembunyikan dari teks. Gulir teks panjang dan perpindahan baris tetap tersedia.
- Sorotan/fokus baris aktif, gulir otomatis, ketuk baris untuk berpindah waktu, terjemahan, penyesuaian offset, dan berbagi lirik tetap memakai alur yang ada. Lirik tanpa penanda waktu tetap terbaca.
- Loader tidak lagi mencari timestamp kata tambahan setelah mendapat lirik per baris yang terbaca. Perbaikan spasi cache lama dan fallback penyedia masih tersedia; data lirik tersimpan tidak dihapus.

## Validasi

**42 pengujian terarah lulus**, tanpa failure/error: 11 pemutar dan render, 15 fallback lirik, 9 berbagi lirik, 2 warna lirik, 4 tampilan pengaturan, dan 1 preferensi lirik. Pemeriksaan mencakup tampilan yang tidak berubah ketika waktu melewati batas kata dalam baris yang sama, perpindahan fokus saat baris berikutnya, tab/layar penuh/strip dengan flag animasi lama, pilihan tersimpan setelah aplikasi dibuka ulang, permintaan fallback, berbagi lirik, warna, dan pengaturan. [Ringkasan tes](../artifacts/lyrics-line-only-2026-10-08/test-summary.json).

Build Android debug dan kompilasi desktop berhasil dalam 4 menit 38 detik. Empat APK diperbarui pada 8 Oktober 2026 pukul **19.08 WIB**. [Log build](../artifacts/lyrics-line-only-2026-10-08/validation.log) dan [hash APK](../artifacts/lyrics-line-only-2026-10-08/apk-verification.json).

Pratinjau yang dirender dari komponen produksi dan diperiksa secara visual: [tab lirik](../artifacts/lyrics-line-only-2026-10-08/standard-player.png), [layar penuh](../artifacts/lyrics-line-only-2026-10-08/standard-fullscreen.png), dan [strip lirik di pemutar](../artifacts/lyrics-line-only-2026-10-08/line-only-compact.png). Pemeriksaan ini memakai render JVM; APK hasil perubahan ini belum dipasang ulang di emulator.

Perubahan ini menyederhanakan tampilan; ketepatan waktu baris tetap mengikuti data penyedia dan offset pengguna. Stabilitas pada perangkat fisik belum diuji dalam pekerjaan ini.
