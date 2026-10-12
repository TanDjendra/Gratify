# Perbaikan thumbnail playlist profil — 11 Oktober 2026

Masalah yang dilaporkan: foto playlist yang dipajang di profil berkedip, termasuk sampul upload dan sampul bawaan.

## Penyebab di kode

`ProfileScreen` membuat objek Flow playlist baru setiap kali terjadi recomposition. Pengumpulan Flow tersebut dimulai ulang oleh `collectAsStateWithLifecycle`.

Pembacaan playlist lokal juga bergantung pada Flow email akun yang memancarkan nilai yang sama saat preferensi lain berubah. Tanpa `distinctUntilChanged`, `flatMapLatest` membatalkan lalu membuka kembali pengamatan database walaupun pemiliknya tetap sama.

Repository sebelumnya selalu memancarkan `thumbnail = null` untuk sampul privat, lalu memancarkan URL bertanda tangan. Kombinasi pemuatan ulang dengan dua emisi ini membuat foto berganti ke placeholder berulang kali.

## Perbaikan

- Ingat Flow playlist sepanjang masa hidup halaman profil.
- Mulai ulang pengamatan database hanya ketika email pemilik berubah.
- Abaikan invalidasi database yang menghasilkan daftar identik.
- Gunakan URL sampul yang masih ada dalam cache sejak emisi pertama.
- Pertahankan gambar untuk objek dan pemilik yang sama selama pembaruan URL; judul dan status lokal tetap dapat tampil segera tanpa menunggu jaringan.
- Batasi ingatan gambar pada daftar aktif per kolektor. Pergantian pemilik atau objek sampul tidak meminjam gambar sebelumnya. Jika resolusi akses ditolak, gambar dibersihkan.

Tidak ada perubahan skema, akun, data produksi, kebijakan Storage, atau konfigurasi autentikasi dalam perbaikan ini. Penandatanganan tetap menggunakan API dan durasi yang sudah dipakai aplikasi.

## Validasi

21 uji JVM lolos, tanpa kegagalan, error, atau skip: 6 uji aliran artwork, 1 uji pergantian akun/pembaruan preferensi, 7 uji akses playlist/Storage, dan 7 uji pemisahan pustaka akun.

Uji artwork meliputi sampul upload dalam cache, perubahan metadata saat signing belum selesai, invalidasi identik, pergantian pemilik atau file, sampul sistem, serta penolakan akses. Signing Storage diuji lewat Supabase SDK terhadap server HTTP lokal; tidak membuat data pengujian pada server produksi.

Build pertama gabungan gagal karena inferensi tipe Flow pada fixture uji akun. Tipe fixture diperjelas; pengujian berikutnya seluruhnya lolos. Log kedua menjadi acuan build akhir.

Saat pemeriksaan ponsel, akun baru masih memiliki 0 koleksi dan tidak ada playlist yang dipajang. Karena itu, uji thumbnail kedua jenis dilakukan secara terkontrol; belum ada pembandingan visual langsung untuk sampul playlist di ponsel setelah reset akun.

Build akhir `assembleRelease` selesai dalam 7 menit 43 detik. Lint: 0 error, 0 fatal, 65 warning yang juga terdapat pada kandidat sebelumnya. Snapshot 7 berkas sumber tetap cocok dengan hash yang dicatat saat validasi.

APK tetap bernama **Gratify**, package `com.tan.gratify.next`, versi kandidat yang belum dipublikasikan 2.1.2 (63). APK arm64 dan universal ditandatangani dengan sertifikat baru yang sama, diverifikasi oleh apksigner, dan lolos pemeriksaan zipalign/native alignment 16 KiB. Payload cocok dengan build unsigned akhir.

APK arm64 berhasil dipasang melalui `adb install -r`, tanpa menghapus data. SHA-256 APK yang dibaca kembali dari ponsel cocok dengan artefak bertanda tangan. Sesudah pembaruan, halaman profil terbuka, identitas profil yang sama tampil, dan login tetap tersimpan. Sampel RAM profil 136,62 MiB termasuk swap; ini hanya sampel, bukan uji kebocoran RAM jangka panjang. Tidak ada playlist yang dipajang pada saat pemeriksaan ini.

- APK universal: `artifacts/profile-thumbnail-fix-20261011/Gratify.apk`
- SHA-256 universal: `47d6d0e81776753bd9d4f86f8d847fccaee5f535739f55b538293e18c98c3f79`
- SHA-256 arm64 terpasang: `8c529c71d3f71b00c057eb8bf0c0a271721fef4540c841ccbaef3615850a3d94`

Bukti, laporan validasi, log, dan snapshot sumber berada di `artifacts/profile-thumbnail-fix-20261011/`. Tidak ada APK yang dipublikasikan. Gradle daemon dihentikan setelah build.
