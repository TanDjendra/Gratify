# Perbaikan tombol hati dan spasi lirik

Tombol favorit pada pemutar Apple Music memakai ikon hati, termasuk header lirik
dan antrean. Area sentuh tombol YouTube, hati, dan menu berukuran 48 dp dengan ikon
24 dp yang sejajar di tengah. Komponen hati lama juga menggunakan renderer yang sama.
Status suka tetap memakai tindakan `ToggleLike` dan penyimpanan yang sudah ada.

Efek bintang dan kilauan warna-warni diganti dengan denyut 360 ms dan lingkaran
hijau Gratify yang tetap berada dalam batas tombol. Efek hanya berjalan saat pengguna
menyukai lagu, tidak ketika status lagu dimuat. Warna ikon yang aktif memakai aksen aplikasi.

Lirik terpecah karena setiap penanda waktu dianggap sebagai batas kata: renderer
menambahkan celah pada setiap fragmen, mode statis mengganti penanda dengan spasi,
dan parser TTML menambahkan spasi setelah setiap span. Sekarang batas kata mengikuti
spasi sumber. Fragmen dalam satu kata tidak diberi celah dan berpindah baris bersama,
sedangkan waktu setiap suku kata tetap dipertahankan. Rentang highlight pada lirik
ringkas dan mode tanpa animasi mengikuti teks yang sama. Contoh regresi:
`aku bersamamu kali ini`, bukan `a ku ber sa ma mu kali i ni`.

19 pengujian lulus: 9 pemutar/tampilan, 3 batas kata rich sync, 4 berbagi lirik,
dan 3 parser LRC/TTML. Build Android debug dan lint berhasil; lint memiliki
0 error dan 117 peringatan. Pengujian menggunakan renderer produksi pada JVM;
pengujian langsung di ponsel belum dilakukan pada perubahan ini.

Hasil tercatat di `artifacts/simp-exact/heart-lyrics-validation.json` dan
`heart-lyrics-verification.log`. Bukti tampilan: `gratify-heart-music.png`,
`gratify-heart-lyrics.png`, `gratify-heart-queue.png`, dan `gratify-syllable-spacing.png`.
