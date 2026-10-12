# Perbaikan Friends dan pratinjau story

Pemeriksaan dashboard Supabase Gratify menemukan bahwa tabel `music_stories`,
`direct_conversations`, dan `direct_messages` belum ada. Sembilan fungsi layanan
Friends juga belum ada, sementara prasyarat profil dan hubungan pengguna sudah tersedia.
Itulah penyebab utama story dan pesan antar akun belum bisa digunakan.

Migrasi `supabase/migrations/202610030001_friends_stories_messages.sql` sudah diuji,
dilengkapi pemberitahuan pemuatan ulang skema API, dan disiapkan pada editor Supabase.
Migrasi sudah diterapkan ke Supabase Gratify setelah persetujuan pengguna. Dashboard
menampilkan `Success. No rows returned.` Pemeriksaan setelah aktivasi memastikan tiga
tabel tersedia dan dilindungi RLS, sembilan fungsi dapat dijalankan oleh akun yang
masuk, dan akun anonim tidak dapat membaca tabel atau menjalankan fungsi Friends.
Editor dicocokkan dengan seluruh berkas lokal sebelum eksekusi.

## Pratinjau yang ditambahkan

- Pilihan potongan 8, 15, dan 30 detik tetap tersedia dengan batas mulai/akhir sesuai durasi lagu.
- Tombol **Pratinjau musik & lirik** membuka potongan yang sebenarnya akan dibagikan.
- Penghitung waktu menampilkan detik berjalan dibandingkan durasi potongan, misalnya `0:02 / 0:15`; setelah selesai tetap menunjukkan durasi penuh.
- Editor menampilkan lirik dari rentang potongan yang dipilih. Pratinjau dan story yang dibuka menyorot baris sesuai posisi lagu asli: titik mulai potongan ditambah waktu pemutaran.
- Penanda waktu per kata tidak ditampilkan sebagai teks mentah. Teks tanpa sinkronisasi diberi keterangan bahwa penanda waktu belum tersedia.
- Jika lirik gagal ditemukan, tersedia tombol muat ulang dan musik tetap dapat diputar/dibagikan.
- Lirik memakai penyedia Gratify dan cadangan yang sudah terpasang, dengan cache lebih dahulu dan permintaan yang dapat dibatalkan saat pratinjau ditutup atau lagu berubah.
- Durasi lagu yang sedang diputar tetapi belum termuat dilengkapi sebelum tombol Bagikan aktif.

Pengambilan lirik story terpisah dari state lirik dan antrean pemutar utama. Story
tetap menyimpan ID lagu dan rentang waktu, tanpa menyimpan URL audio sementara atau
salinan seluruh lirik. Saat dilihat, lirik diambil dari penyedia/cache untuk lagu tersebut.

## Pemulihan error Friends

Aplikasi sekarang membedakan layanan story/pesan yang belum aktif dari kegagalan
koneksi biasa. Polling inbox dihentikan sementara saat fungsi backend belum tersedia;
tombol perbarui mencoba kembali setelah layanan diaktifkan. Pesan gagal tetap memakai
draft dan identitas pengiriman yang sama seperti implementasi sebelumnya.

## Verifikasi

- **23 pengujian lulus**: tampilan Friends/editor/viewer, potongan lirik, batas penghitung detik, cache/cadangan/pembatalan, serta pesan kegagalan layanan.
- **46 pemeriksaan database lulus**, masing-masing 23 untuk ID profil text dan UUID, termasuk privasi story, permintaan pesan, pencegahan pemalsuan pengirim, dan batas durasi.
- Build Android debug dan lint berhasil. APK baru berada di `androidApp/build/outputs/apk/debug/`.
- Bukti tampilan memakai komponen produksi dengan data uji lokal pada lebar 360/390 dan font hingga 1,5. Bukan akun atau story produksi buatan.
- Pengujian antar dua akun nyata belum dilakukan. Status aktivasi dan hasil terbaru tercatat dalam `artifacts/friends-social/preview-validation.json`.

Bukti: `story-preview-lyrics.png`, `story-editor-lyrics-360.png`, `activation-success.jpg`,
dan `activation-postconditions.jpg` di direktori `artifacts/friends-social/`.
