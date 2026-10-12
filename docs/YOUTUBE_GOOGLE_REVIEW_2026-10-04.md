# Status akses publik YouTube Gratify — 4 Oktober 2026

## Selesai

- Domain https://gratify.web.id aktif, HTTPS aktif, situs dibuka untuk umum atas persetujuan pemilik.
- Dua A record Sites, dua TXT verifikasi hosting dan satu TXT Google disimpan di IDwebhost. Nameserver tetap ns1.idwebhost.id dan ns2.idwebhost.id; layanan DNS domain aktif.
- Kepemilikan domain diverifikasi di Google Search Console oleh supportgratify@gmail.com.
- Privacy resmi disetujui pemilik dan berlaku mulai 4 Oktober 2026: https://gratify.web.id/privacy/. Salinan Markdown resource aplikasi identik dengan sumber. Terms masih DRAF dan tidak dipasang sebagai Terms resmi Google.
- Tautan pembaca dokumen di source aplikasi diarahkan ke domain resmi; label DRAF Privacy di UI dihapus, label Terms tetap DRAF. Perubahan lokal ini baru masuk aplikasi pada build berikutnya.
- Google Cloud gratify-auth: halaman utama dan privasi tersimpan, domain gratify.web.id ditambahkan tanpa mengganti OAuth client atau callback Supabase.
- Audience External, publishing status **In production**. Akun Google lain dapat mencoba koneksi tanpa dimasukkan ke daftar tester, sepanjang batas dan kebijakan Google mengizinkan.
- Branding otomatis diverifikasi Google dan dipublikasikan. UI menyatakan: “Your branding has been verified and is being shown to users.”

## Batas saat ini

Izin youtube.readonly belum diverifikasi. Google masih menunjukkan batas 100 pengguna sepanjang umur proyek untuk izin sensitif yang belum disetujui, dan pengguna dapat melihat peringatan aplikasi belum diverifikasi. Produksi tidak sama dengan persetujuan izin YouTube tanpa batas.

Permintaan otomatis izin YouTube pada login Google tetap dinonaktifkan (`YOUTUBE_LOGIN_CONSENT_ENABLED=false`). Koneksi resmi bersifat opsional melalui fitur koneksi koleksi; login Gratify biasa tetap menggunakan izin dasar. Halaman Google Data Access memperingatkan agar izin yang belum diverifikasi tidak dipaksakan pada seluruh trafik login produksi. Tidak ada perubahan scope tambahan atau akses tulis.

Status endpoint setelah publikasi: `configured=true`, `enabled=false`. Angka `enabled` mengendalikan izin otomatis saat login, bukan apakah koneksi manual tersedia. Impor koleksi oleh akun pengguna melalui aplikasi belum dibuktikan dalam sesi ini.

## Pengajuan yang belum bisa dikirim

Formulir Google meminta **scope justification dan demo video**. Justifikasi di bawah sudah diisi (912/1000 karakter) tetapi belum tersimpan: tombol Save tetap nonaktif karena tautan YouTube belum diisi. Jangan menganggap draf formulir sebagai pengajuan terkirim. Tab Data Access dipertahankan agar dapat dilanjutkan setelah video tersedia.

### Justifikasi yang disiapkan

Gratify offers an optional read-only YouTube collection connection. After explicit consent, users can view and import metadata from their own YouTube playlists and liked videos into their Gratify library. We call playlists.list with mine=true, playlistItems.list, and videos.list with myRating=like. Public unauthenticated data cannot return the signed-in user's private collection or likes, so youtube.readonly is the least-privileged scope sufficient for this feature. No YouTube collection is created, modified or deleted. Google identity is verified to bind the connection to the correct Gratify account. OAuth tokens are encrypted on our Supabase server and removed when the user disconnects or deletes the account. Users can also revoke access in their Google Account. Google data is not sold or used for personalized ads or AI training. Basic Gratify sign-in remains available without this optional scope.

## Panduan video demo

Gunakan akun uji milik pengelola dan versi Gratify yang sudah menyediakan koneksi resmi YouTube. Rekam alur nyata; slideshow atau rekaman API tidak menggantikan tampilan aplikasi dan consent.

1. Tampilkan Gratify dan bagian yang menyediakan koneksi resmi YouTube. Perlihatkan bahwa login Gratify sudah aktif dan koneksi koleksi masih opsional.
2. Mulai koneksi Google/YouTube. Tampilkan layar consent, nama aplikasi, izin yang diminta, dan identitas OAuth client/proyek yang relevan sesuai kebutuhan reviewer. Jika muncul layar aplikasi belum diverifikasi, Google meminta agar layar tersebut terlihat dalam rekaman.
3. Setelah consent, kembali ke Gratify dan tampilkan playlist milik akun uji serta lagu/video disukai yang dibaca dari YouTube. Gunakan koleksi contoh yang boleh terlihat oleh reviewer. Buka satu playlist dan tunjukkan metadata isinya; tunjukkan impor ke Pustaka Gratify jika tersedia.
4. Tampilkan cara memutuskan koneksi dari Gratify dan tautan kebijakan privasi resmi. Jelaskan singkat bahwa aplikasi membaca koleksi, tidak mengubah atau menghapus playlist YouTube.
5. Video harus mencakup OAuth client yang digunakan aplikasi dalam proyek tersebut. Jika Google meminta seluruh client yang terdaftar, periksa daftar Clients dan jelaskan client yang tidak digunakan alur ini; jangan mengarang rekaman untuk client lain.
6. Jangan merekam kata sandi, OTP, token, client secret atau kunci server. Berhenti/matikan perekaman saat memasukkan rahasia, lalu lanjutkan pada layar consent. Gunakan data uji agar rekaman tidak menampilkan pesan, koleksi privat atau data pengguna lain.
7. Unggah video ke YouTube dengan akses **Tidak publik / Unlisted** bila reviewer dapat membukanya, lalu sediakan tautannya. Pengunggahan belum dilakukan atau diotorisasi dalam sesi ini. Tautan harus menampilkan alur nyata yang selesai, bukan video contoh dari aplikasi lain.

Setelah video tersedia: isi YouTube link di Data Access, simpan, buka Verification Center, periksa ringkasan dan kirim pengajuan. Persetujuan izin baru dianggap selesai ketika Google menunjukkan status disetujui; jangan aktifkan otomatis izin YouTube untuk semua login hanya karena status aplikasi sudah produksi.

Tidak ada APK yang dibangun, ditandatangani, dipasang atau diterbitkan dalam pekerjaan domain ini.

## Rujukan resmi

- https://developers.google.com/identity/protocols/oauth2/production-readiness/overview
- https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification
- https://idwebhost.com/blog/name-server-manage-dns/

## Bukti

Direktori `artifacts/domain-activation-2026-10-04/`: DNS IDwebhost, verifikasi domain Google, status In production, branding terverifikasi, halaman Privacy resmi, formulir yang memerlukan video, serta status JSON dan hasil DNS publik.
