# Mode lirik standar per baris

Keputusan terbaru pada 8 Oktober 2026 menjadikan per baris sebagai satu-satunya mode tampilan. Opsi animasi kata di bawah sudah dinonaktifkan; lihat [perubahan terbaru](LYRICS_LINE_ONLY_2026-10-08.md).

Pilihan yang sebelumnya berlabel "Tanpa animasi" kini berlabel "Standar · per baris".
Nilai preferensi yang tersimpan tetap sama: false memilih mode standar dan true
memilih animasi kata SimpMusic. Tidak diperlukan pengaturan ulang.

Mode standar menampilkan kata utuh dan menyorot baris aktif, dengan perpindahan
fokus serta gulir halus per baris. Baris di sekitarnya memiliki blur dan redup
berdasarkan jarak dari baris aktif. Animasi suku kata, glow kata, dan efek gelombang
kata tetap dimatikan. Lirik tanpa penanda waktu tetap terbaca tanpa fokus waktu buatan.
Perubahan berlaku pada tab lirik, layar penuh, dan renderer lirik lama Gratify.

17 pengujian lulus, termasuk gambar yang tidak berubah saat waktu bergerak dalam
baris yang sama, perubahan fokus saat masuk baris berikutnya, tampilan standar
pada tab/layar penuh, serta regresi spasi dan berbagi lirik. Build debug dan lint
berhasil. Hasil: `artifacts/simp-exact/standard-lyrics-validation.json`.
