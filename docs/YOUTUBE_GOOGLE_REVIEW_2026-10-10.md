# Verifikasi izin YouTube — 10 Oktober 2026

Pengguna meminta melanjutkan pengajuan verifikasi Google memakai `C:\Users\LENOVO\Downloads\WhatsApp Video 2026-10-10 at 03.31.32.mp4`, lalu secara eksplisit menyetujui pembuatan channel Gratify Music (@GratifyMusic), penerimaan Persyaratan Layanan YouTube, dan unggahan Unlisted.

## Pemeriksaan langsung

- Akun developer `supportgratify@gmail.com` dapat mengakses proyek `gratify-auth`.
- Verification Center: branding terverifikasi dan ditampilkan kepada pengguna; data access belum terverifikasi.
- Prepare for verification: scope justification dan demo video belum tersedia. Confirm nonaktif.
- Draf justifikasi 911 karakter dari `artifacts/google-verification-2026-10-09/scope-justification.txt` diisi kembali pada Data Access. Save tetap nonaktif karena tautan YouTube belum tersedia. Draf belum tersimpan di konfigurasi proyek; tab dipertahankan untuk melanjutkan.
- Akun developer belum memiliki channel YouTube. Setelah persetujuan pengguna, tombol Buat channel diklik sekali untuk nama Gratify Music dan handle GratifyMusic. YouTube menolak: “Anda memasukkan terlalu banyak nama yang tidak dapat digunakan. Coba lagi setelah 24 jam.” Tidak ada konfirmasi keberhasilan pembuatan channel, unggahan, atau publikasi video. Tidak ada percobaan untuk menghindari pembatasan.
- Bukti UI: `artifacts/google-verification-2026-10-10/youtube-channel-blocked.jpg`.

## Pemeriksaan video asli

Durasi 100,799 detik; 720 × 1600, dengan audio. Pemeriksaan visual dilakukan melalui sampel frame, termasuk urutan login/consent dan akhir rekaman.

- Awal menunjukkan pilihan koneksi YouTube pada login, lalu pemilihan akun Google.
- Consent asli menampilkan nama Gratify dan izin baca akun YouTube; warning aplikasi belum diverifikasi tetap terlihat.
- Alur login dan consent memakai bahasa Indonesia. Address bar hanya menampilkan URL yang dipendekkan; client ID tidak terbaca.
- Setelah kembali ke APK, Pustaka sempat mengalami kegagalan pemuatan; playlist kemudian tampil. Isi playlist serta Video yang disukai terlihat.
- Bagian akhir menunjukkan status terhubung dan dialog pemutusan. Setelah dialog tertutup, teks masih menyatakan Google terhubung. Keberhasilan pemutusan belum dibuktikan.
- Kebijakan privasi tidak dibuka dalam rekaman.

Google secara eksplisit meminta alur OAuth/login dalam bahasa Inggris, nama aplikasi, client ID yang terlihat pada address bar, dan demonstrasi penggunaan izin. Rekaman ini membuktikan fitur koleksi, tetapi belum memenuhi seluruh persyaratan rekaman tersebut. Pemutusan dan privacy merupakan tambahan dalam panduan proyek, bukan syarat video eksplisit pada halaman resmi yang diperiksa.

## Langkah melanjutkan

1. Sediakan channel YouTube yang dapat dipakai. Pada akun developer, UI meminta menunggu 24 jam sebelum mencoba lagi. Alternatif: pengguna mengunggah video ke channel lain miliknya dan memberikan tautan Unlisted.
2. Tambahkan rekaman OAuth asli berbahasa Inggris dengan client ID terlihat. Jangan mengganti tampilan consent dengan terjemahan buatan. Bagian koleksi dari video sekarang dapat dipakai sebagai bukti fitur.
3. Unggah video Unlisted; masukkan tautannya di Data Access bersama justifikasi, simpan, periksa ringkasan, lalu kirim pengajuan. Sampai langkah ini benar-benar selesai, status tetap belum diajukan.

Rujukan diperiksa 10 Oktober 2026: https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification

## Pemeriksaan lanjutan pada permintaan pengguna

Pembuatan channel pada akun Gratify dicoba kembali satu kali setelah pengguna meminta melanjutkan. YouTube tetap menampilkan pembatasan yang sama: coba lagi setelah 24 jam. Draf Google masih berisi 911 karakter; tautan video kosong dan Save tetap nonaktif.

Daftar akun YouTube menunjukkan channel Tan Heradhe pada akun lain yang sudah masuk. Pengguna secara eksplisit memilih tetap memakai akun Gratify, sehingga tidak ada unggahan atau perubahan pada channel Tan Heradhe. Bahan judul, deskripsi yang akurat, akun tujuan, sumber video, serta status pengajuan disimpan di `artifacts/google-verification-2026-10-10/upload-details.txt`. Tidak ada pengajuan yang dikirim.

## Channel tersedia; pemilihan file manual

Saat pengguna kembali meminta melanjutkan, YouTube Studio berhasil membuka channel Gratify Music (@GratifyMusic), ID `UCjteM6-5F1w8tr6TF9H_6-Q`. Menu Ganti akun menunjukkan akun channel adalah `supportgratify@gmail.com`. Keberhasilan channel ini diperiksa langsung; tidak diasumsikan dari percobaan yang ditolak sebelumnya.

Dialog Upload video dibuka dan file asli dipilih melalui alur file chooser. Pengiriman file oleh alat gagal karena ekstensi Chrome belum mengaktifkan Allow access to file URLs. Pengguna memilih melakukan pemilihan file secara manual di YouTube Studio. Justifikasi 911 karakter dipulihkan pada tab Google yang baru, tetapi belum disimpan karena belum ada URL video. Unggahan dan pengajuan belum terkonfirmasi berhasil.

## Unggahan diterima dan bahan Google tersimpan

Pengguna memilih file secara manual; YouTube Studio menampilkan file `demo Gratify.mp4` dan pesan Upload selesai. ID video `kcglA6fl0gs`, tautan https://www.youtube.com/watch?v=kcglA6fl0gs . Judul diisi menjadi Gratify — YouTube read-only OAuth verification demo; deskripsi menjelaskan koneksi opsional baca saja serta homepage/privacy. Audiens ditetapkan bukan untuk anak-anak. Visibilitas Tidak publik dipilih dan Simpan diklik. YouTube menyatakan video harus selesai diproses dalam SD sebelum tersedia sebagai Unlisted; pada pemeriksaan berikutnya status masih Memproses/Tertunda. Pengaturan Unlisted telah diminta, tetapi ketersediaan publik melalui tautan belum dikonfirmasi.

Data Access Google berhasil menyimpan justifikasi dan tautan video; muncul pesan Data access changes saved! Ringkasan Prepare for verification sekarang menampilkan justifikasi dan tautan serta tombol Confirm aktif, tanpa error field yang hilang. Pada titik catatan ini, pengajuan akhir belum dikirim.

## Status terakhir: video Unlisted siap, deklarasi persyaratan belum dicentang

Pemrosesan YouTube selesai. Daftar konten menampilkan video `kcglA6fl0gs` sebagai Tidak publik, durasi 1 menit 41 detik. Halaman watch berhasil memainkan video; screenshot memperlihatkan alur Pilih akun dalam bahasa Indonesia dari rekaman yang sama. Video bisa diakses melalui https://www.youtube.com/watch?v=kcglA6fl0gs . Bukti tersimpan di `artifacts/google-verification-2026-10-10/youtube-uploaded-unlisted.jpg`.

Confirm pada Prepare for verification membuka Verification Questionnaire. Empat klasifikasi aplikasi diisi No: personal-only, internal-only, development/testing/staging-only, dan Gmail SMTP WordPress-only. Tombol final Submit for verification masih nonaktif karena deklarasi belum dicentang.

Deklarasi pertama berbunyi: “I acknowledge that I have read through the verification requirements for submission ... and my application meets all requirements.” Rekaman masih belum memenuhi syarat eksplisit OAuth/sign-in dalam bahasa Inggris dan client ID terlihat. Deklarasi tidak dicentang karena tidak dapat menyatakan pemenuhan seluruh persyaratan berdasarkan bukti yang ada. Deklarasi CASA juga belum dicentang; proyek hanya meminta scope sensitif youtube.readonly, bukan restricted scope. Tidak ada pengajuan akhir yang dikirim. Bukti dialog final: `artifacts/google-verification-2026-10-10/google-final-questionnaire.jpg`.

Perubahan yang sudah berhasil: video Unlisted pada akun Gratify, metadata video, serta justifikasi dan URL yang tersimpan di Google. Yang diperlukan untuk selesai: rekaman OAuth asli dalam bahasa Inggris dengan client ID terlihat, unggahan/perbaruan tautan demo, kemudian penyelesaian deklarasi yang benar dan tombol Submit for verification.

## Demo 2: bahasa Inggris sudah benar; client ID belum terlihat

Pengguna menyediakan `C:\Users\LENOVO\Downloads\demo 2.mp4` (11.944.396 byte, 80,670479 detik, 720 × 1600). Sampel setiap satu detik dan sampel rapat pada bagian address bar diperiksa langsung.

- Login, pemilihan akun, warning, dan consent Google sekarang berbahasa Inggris. Nama Gratify dan izin View your YouTube account terlihat, termasuk rincian baca video/playlist/aktivitas.
- Login kembali ke APK berhasil. Pustaka menampilkan playlist dan entri Video yang disukai. Rekaman pertama masih tersedia untuk bukti isi koleksi.
- Sekitar detik 24 dan 43 pengguna mengetuk address bar. Chrome menampilkan kartu halaman saat ini dengan ikon bagikan, salin, dan pensil. Ikon pensil belum ditekan; URL tetap dipendekkan. Tidak ada client ID terbaca dalam rekaman.
- Pada pemeriksaan browser terbaru, formulir masih Prepare for verification, scope sensitif belum diverifikasi, dan URL masih video pertama `kcglA6fl0gs`. Tidak ada unggahan demo 2 atau pengajuan akhir yang dilakukan pada pemeriksaan ini.

Satu bukti yang masih perlu ditambahkan adalah rekaman singkat halaman consent dengan URL asli dibuka melalui ikon pensil, lalu bagian `client_id=...apps.googleusercontent.com` ditampilkan. Rekaman demo 2 dapat dipertahankan; tidak perlu merekam ulang seluruh demonstrasi. Bukti inspeksi ada pada `artifacts/google-verification-2026-10-10/demo-2-*.jpg`.

## Demo 3 dan video gabungan siap unggah

Pengguna menyediakan `C:\Users\LENOVO\Downloads\demo 3.mp4` (10.176.976 byte, 82,6595 detik), lalu meminta continue. Rekaman menunjukkan OAuth/sign-in asli berbahasa Inggris, nama Gratify, izin baca akun YouTube, URL asli yang digeser untuk memperlihatkan `client_id=1072882393306-066inpjhs2njrliu61rulg5oq28c7v9m.apps.googleusercontent.com`, lalu kembali ke APK. Client ID terlihat bertahap saat address bar digeser; tidak ada consent buatan.

File rekaman WhatsApp sebelumnya sekarang bernama `C:\Users\LENOVO\Downloads\demo Gratify.mp4` dengan ukuran yang sama 12.271.188 byte. Bagian detik 46–86 memperlihatkan daftar playlist, isi playlist, video yang disukai, dan kembali ke Pustaka. Bagian ini diperiksa langsung dan ditambahkan setelah demo 3 sebagai bukti fitur dalam rekaman terpisah.

Video final: `C:\Users\LENOVO\Downloads\Gratify verification final.mp4`. Gabungan dua rekaman asli; tidak mengubah tampilan izin atau client ID. Publikasi perlu menyebut bahwa demonstrasi fitur berasal dari rekaman terpisah. Pengaturan rekaman final: H.264 720 × 1600 30 fps, AAC stereo, kualitas CRF 18.

YouTube Studio Gratify Music dibuka kembali. Pemilihan file otomatis dicoba sekali, tetapi ditolak oleh ekstensi Chrome karena Allow access to file URLs belum aktif. Pengguna sebelumnya memilih pemilihan manual, sehingga dialog Upload video dipertahankan untuk memilih file final tersebut. File final belum berhasil diunggah, URL Google masih video pertama, dan pengajuan akhir belum dikirim.

## Pengajuan berhasil terkirim — data access under review

Pada kelanjutan setelah pengguna bertanya “terus?”, YouTube Studio memperlihatkan file `Gratify verification final.mp4` sudah dipilih dan pemrosesan/pemeriksaan selesai tanpa masalah. Durasi sekitar 2:03, ID video `Ely-_0XoYGI`, akun channel Gratify Music yang sama.

- Judul diisi `Gratify — YouTube read-only OAuth verification demo`.
- Deskripsi menjelaskan OAuth Inggris/client ID mulai 0:00 dan bukti fitur dari rekaman terpisah mulai 1:23, koneksi opsional baca saja, serta homepage/privacy.
- Audiens yang awalnya dipilih sebagai made for kids dikoreksi menjadi bukan untuk anak-anak. Visibilitas Tidak publik dipilih, Simpan diklik, dan muncul Video dipublikasikan.
- Video final berhasil dimainkan melalui `https://www.youtube.com/watch?v=Ely-_0XoYGI`.
- Data Access Google mengganti URL demo dengan video final; muncul `Data access changes saved!`. Justifikasi 911 karakter tetap tersimpan.
- Additional info 527 karakter mengungkapkan gabungan dua rekaman asli, penunjuk waktu, satu scope sensitif youtube.readonly, tanpa restricted scopes, serta koneksi opsional.
- Verification Questionnaire diisi No untuk empat pengecualian. Persyaratan resmi yang ditautkan dibaca; dua acknowledgment dicentang setelah bukti OAuth Inggris/client ID dan fitur koleksi tersedia. Pernyataan CASA bersifat kondisional jika meminta restricted scopes, bukan klaim sudah menjalani CASA.
- `Submit for verification` diklik satu kali. UI berpindah ke Verification Center dan secara eksplisit menyatakan `Your app's data access is under review.` Branding tetap terverifikasi dan ditampilkan kepada pengguna.

Status final: pengajuan telah terkirim, menunggu peninjauan Google; belum dinyatakan disetujui. Bukti: `artifacts/google-verification-2026-10-10/google-submitted-under-review.jpg`. Pantau balasan Google di alamat kontak `supportgratify@gmail.com`; tidak ada monitor otomatis yang dibuat.
