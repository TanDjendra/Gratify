# Redesign logo Gratify — 2 Oktober 2026

Logo baru berupa huruf **G** dengan batang audio, hijau `#1ED760`, dan latar
ikon gelap `#101411`. Dibuat menggunakan alat **imagegen bawaan**, kemudian
dikonversi menjadi aset native untuk tiap platform. PNG asli dipertahankan.

## Hasil

- Android: ikon launcher/adaptive/tema, lima kepadatan layar, splash sistem,
  splash aplikasi, login, notifikasi pemutar/unduhan/push, dan widget.
- Aplikasi bersama: dialog, profil/about, loading, serta sampul pengganti.
- Desktop: logo dalam aplikasi, ikon jendela/tray/mini player, dan ikon paket
  Windows. Aset Linux PNG dan macOS ICNS juga diperbarui; paket kedua platform
  tersebut belum dibangun.
- Ikon Play Store dan fastlane diperbarui dalam repositori.

File desain dan prompt ada di `assets/branding`; konversi dapat diulang dengan
`artifacts/branding/generate-platform-icons.py` (Pillow).

## Verifikasi

- Dua build rilis berhasil: paket lengkap, lalu penyesuaian splash Android.
- 28 pengujian Compose JVM dan 2 pengujian media JVM lulus, tanpa kegagalan.
- Aset bersama di APK dan JAR Windows cocok dengan file sumber.
- Kontur native launcher, notifikasi, dan splash ada di resource APK terkompilasi.
- Tujuh ukuran gambar ikon Windows ditemukan di resource EXE.
- APK ditandatangani dan diperiksa dengan sertifikat Gratify yang sudah ada.
- Paket APK, pustaka 64 bit dengan alignment 16 KB, runtime Windows, dan VLC
  berhasil diperiksa. Laporan lint yang dibaca pemeriksa paket berasal dari
  audit sebelumnya; lint lengkap tidak diulang untuk perubahan logo ini.

APK: `androidApp/build/outputs/apk/release/Gratify-release-signed.apk`.
SHA-256: `bccc1576d15a2e4ab45e15c43a9c84e7e1fa43caefc28546f64b9eeb2d848557`.

Paket Windows: `desktopApp/build/compose/binaries/main-release/app/Gratify`.
Seluruh folder diperlukan untuk menjalankan `Gratify.exe`.

Rincian tersedia di `artifacts/branding/assets-verification.json`,
`artifacts/branding/release-verification.json`, dan
`artifacts/desktop-redesign/verification.json`.

Pemasangan pada HP pengguna dan UAT desktop lengkap belum diuji pada paket ini.
Untuk memakai logo baru di Android, pasang APK baru. Versi aplikasi yang sudah
terpasang tidak berubah hanya karena file sumber diperbarui.
