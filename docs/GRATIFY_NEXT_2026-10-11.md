# Gratify Next — kandidat 2.1.2 (11 Oktober 2026)

Gratify Next dibuat sebagai aplikasi Android terpisah agar kunci rilis baru dapat digunakan tanpa menghapus Gratify lama. APK sudah dibangun dan dipasang pada ponsel pengguna. Kandidat ini mewarisi perbaikan RAM/pemutaran dari audit 2.1.1; statusnya beta terbatas, bukan pernyataan bahwa performanya sudah memenuhi seluruh kelas perangkat.

## Identitas dan kunci

| Atribut | Gratify lama | Gratify Next |
| --- | --- | --- |
| ID rilis | `com.tan.gratify` | `com.tan.gratify.next` |
| ID debug | `com.tan.gratify.dev` | `com.tan.gratify.next.dev` |
| Versi kandidat | 2.1.1 / code 62 | 2.1.2 / code 63 |
| UID pada ponsel | 10546 | 10547 |
| Direktori data | `/data/user/0/com.tan.gratify` | `/data/user/0/com.tan.gratify.next` |

Keystore yang berhasil digunakan: `D:/Tan/script/Gratify/gratify.jks`. Sidik jari SHA-256 sertifikat baru: `efefe1b8bf7c6502aeeb60db9a45b430dc50040e11c6f76eb22e2308a51c316a`. Sertifikat publik Gratify 2.1.0 berbeda, sehingga APK Next tidak menjadi pembaruan langsung aplikasi lama. Simpan salinan keystore ini dan passwordnya secara terpisah; password tidak ditulis dalam laporan atau perintah signing.

Pemasangan Next tidak menjalankan uninstall, clear data, atau penggantian APK lama. UID, versi, waktu instalasi/pembaruan, dan nilai signer aplikasi lama tetap sama sebelum/sesudah pemasangan. Namespace kelas tetap `com.tan.gratify`, sehingga komponen utama Next adalah `com.tan.gratify.next/com.tan.gratify.MainActivity`; identitas paket dan data tetap terpisah.

## Integrasi yang diselesaikan

- Dua aplikasi Next terdaftar di proyek Firebase `gratify-auth`; konfigurasi resmi yang diunduh mencakup empat aplikasi lama/baru. Resource Google Services untuk rilis dan debug berhasil diproses. App ID Firebase rilis Next: `1:1072882393306:android:12d260b65ff36309cf05e3`.
- Supabase menambahkan empat URL eksplisit: callback login dan recovery untuk Next rilis/debug. Site URL serta dua URL lama dipertahankan. Auth SDK, penerima intent, dan reset password memakai ID paket aplikasi yang terpasang.
- Fungsi `youtube-library` kini mengikat callback aplikasi ke state OAuth yang terenkripsi. Hanya empat scheme aplikasi yang diterima; callback lama tetap kompatibel. Kode live sebelum perubahan cocok dengan baseline lokal, dan kode setelah deploy/reload cocok dengan sumber yang lulus tes.
- Migrasi `202610110001_gratify_next_push_identity.sql` menerima ID Next pada constraint perangkat dan RPC pendaftaran. Auth ownership, RLS, dan grants dipertahankan. Verifikasi live: RLS aktif, anonymous tidak dapat mendaftar, authenticated dapat mendaftar. Satu perangkat Next terdaftar dan aktif setelah login; token perangkat dan pesan pribadi tidak diambil untuk bukti.
- Ketiga shortcut launcher Next mengarah ke paket Next, dengan kelas activity yang benar. Callback login Next hanya memiliki satu receiver: Next. Receiver legacy milik aplikasi lama/debug tetap seperti sebelumnya.
- Pemeriksa pembaruan menerima metadata dengan ID paket aplikasi yang terpasang. Next menolak APK/metadata legacy dan rilis tanpa metadata. F-Droid belum memiliki listing Next. Skrip persiapan rilis menerima `--expected-package com.tan.gratify.next` dan mencegah delta antaridentitas.

Login diselesaikan pengguna pada ponsel. Profil cloud serta playlist publik akun yang sama terlihat di Next. Hal ini tidak berarti database lokal, unduhan, atau semua pengaturan aplikasi lama berpindah otomatis. Gratify lama tetap tersedia untuk melakukan backup/export bila dibutuhkan.

## APK dan validasi

| APK | Ukuran byte | SHA-256 |
| --- | ---: | --- |
| Next arm64-v8a | 27,935,630 | `42238767fb0b792526b4044e99b56e6d016ca5e78c2ff8df8afdfc41eab9287b` |
| Next universal / `release-next/Gratify.apk` | 54,967,480 | `e4158414ca69ce4a6c32e3dda76b958cfd5b5a0fb5b2f56124a37e5a488006e1` |

Kedua APK memakai sertifikat rilis baru, ID `com.tan.gratify.next`, code 63, nama Gratify Next, minSdk 26 dan targetSdk 36. Validasi zipalign 16 KB lulus. Sebanyak 45 library native 64-bit diperiksa untuk alignment ELF 16 KB. Isi non-signature kedua APK cocok dengan build unsigned final; universal memuat 2.405 entri non-signature. APK arm64 produksi tersebut dipakai untuk pengujian di ponsel.

Build penuh dan build ulang dengan konfigurasi Firebase terakhir berhasil. Lint: 0 error/fatal, 65 warning. Pemeriksaan terkait identitas dan kompatibilitas semuanya lulus: 25 tes Kotlin, 31 tes backend YouTube, 12 tes persiapan rilis, serta 40 pemeriksaan database lokal pada dua bentuk schema profil. Uji database memakai PostgreSQL terisolasi; tidak mengirim pesan/notifikasi kepada pengguna produksi. Mapping R8 disimpan bersama bukti kandidat.

APK universal dan `gratify-update.json` disiapkan di `artifacts/app-successor-20261011/release-next/`. Tidak ada APK, release GitHub, listing store, atau perubahan Git yang dipublikasikan. Sampai rilis Next diterbitkan dengan metadata yang sesuai, updater tidak akan menawarkan APK Gratify lama sebagai pembaruan Next.

## Hasil perangkat

Ponsel: vivo I2218, Android 16/API 36, RAM sekitar 12 GB. APK arm64 produksi yang dipasang telah diuji untuk identitas, startup, login, shortcut, pendaftaran perangkat notifikasi, dan playback. Pendaftaran token tidak sama dengan uji penerimaan pesan push; tidak ada notifikasi produksi yang dikirim dalam pemeriksaan ini.

| Pemeriksaan | Hasil |
| --- | --- |
| Timer lagu Mutiara | UI 02:01 → 02:12; posisi media 123.072 → 132.099 ms |
| Seek dan reset | Posisi 173.083 ms setelah seek, kemudian 23.259 ms setelah reset |
| Buka/tutup pemutar penuh | 10 siklus, hierarki terbuka/tertutup diperiksa, PID tetap 27933 |
| RAM foreground | Baseline 265,22 MiB; siklus 5: 259,00; siklus 10: 263,47 |
| Playback background | 5 menit; 10 checkpoint berstatus PLAYING, posisi bertambah 228.380 ms selama 226,91 detik |
| RAM background per menit | 166,01; 161,40; 162,61; 161,40; 161,17 MiB |
| Pergantian lagu otomatis | Mutiara selesai, beralih ke Dirantai Digelangi Rindu; timer baru 02:12 → 02:23 dan posisi media bertambah |
| Pause | Posisi media tetap 145.179 ms pada dua pembacaan berselang 5 detik; UI tetap 02:25 |
| Penutup | RAM foreground paused 252,50 MiB, PID tetap; buffer crash PID ini kosong |

Angka RAM menggunakan TOTAL PSS termasuk SwapPSS, tanpa penjumlahan swap kedua kali. Tidak terlihat pertumbuhan terus-menerus pada siklus ini. Pengujian singkat dan satu perangkat ini tidak membuktikan bebas kebocoran sepanjang hari atau performa setara pada perangkat RAM 2–4 GB.

Skrip awal berhenti pada assertion posisi setelah kembali dari background karena mengasumsikan lagu tetap sama. Saat itu Mutiara sudah selesai secara alami, sehingga posisi lagu berikutnya kembali ke nol. Log kegagalan skrip tetap disimpan; pemeriksaan lanjutan memverifikasi pergantian lagu, kemajuan timer lagu baru, dan pause. `runtime-smoke-result.json` mencatat hasil gabungan yang lulus serta assertion awal yang gagal. Aplikasi ditinggalkan dalam keadaan pause.

## Batas kesiapan rilis

Mengganti ID paket dan kunci menyelesaikan jalur pemasangan/update baru. Langkah ini tidak memperbaiki jank tambahan. Dua puluh sumber playback/UI yang diperbaiki pada audit sebelumnya masih memiliki hash yang sama; hasil audit 2.1.1 merupakan bukti warisan yang terpisah dari smoke APK Next.

Angka **66,05%** berasal dari **142 dari 215 frame** yang melewati deadline pada sampel pemutar aktif sekitar 33,5 detik. Itu dapat terasa sebagai animasi tersendat atau slider kurang mulus. Angka tersebut bukan penggunaan RAM 66%, bukan persentase error seluruh aplikasi, dan tidak mengukur seluruh sesi harian. Timer, seek dan audio diperiksa secara terpisah. Tidak ada pengukuran frame baru dalam perubahan identitas ini.

Audit panjang sebelumnya tidak menunjukkan pertumbuhan memori terus-menerus pada workload yang diuji, tetapi belum membuktikan bebas kebocoran sepanjang hari. Playback fisik pada RAM 2–4 GB belum diuji. [Laporan audit 2.1.1](RELEASE_READINESS_FIX_2026-10-11.md) memuat pengukuran 15 menit, lirik, CPU, dan batas pengujiannya.

Google Cloud OAuth Overview untuk proyek `gratify-auth` menunjukkan aplikasi sedang dalam proses verifikasi (under verification). Screenshot disimpan sebagai `oauth-under-verification.png`. Review eksternal ini belum selesai; sukses login akun pemilik tidak membuktikan semua akun publik dapat mengakses koleksi YouTube. Persyaratan review scope dijelaskan dalam [dokumentasi resmi Google](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification). Tidak ada pengajuan legal/verifikasi, perubahan billing, atau publikasi branding yang dilakukan otomatis.

Kesimpulan kandidat: jalur aplikasi baru dan signing siap untuk beta terbatas. Rilis stabil menyeluruh masih memerlukan perbaikan/validasi frame, cakupan perangkat RAM rendah, dan penyelesaian status OAuth publik.

## Bukti

Bukti kandidat baru berada di `artifacts/app-successor-20261011/`; hasil audit lama dipertahankan di foldernya semula. File `coexistence-validation.json`, `successor-checks.json`, `release-validation.json`, metadata APK, hasil runtime, patch sumber dan snapshot hash dapat diperiksa secara terpisah. Konfigurasi Google Services dan backup konfigurasi ditempatkan terpisah dari patch sumber; jangan membagikan seluruh folder artefak sebagai paket distribusi.
