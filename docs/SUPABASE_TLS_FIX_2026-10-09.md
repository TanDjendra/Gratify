# Error sertifikat Supabase pada APK playback

Laporan pengguna: `2.1.0-dev`, Vivo I2218, Android 16 (SDK 36), 9 Oktober 2026 pukul 10:20 WIB. Permintaan GET `/auth/v1/user` gagal dengan pesan `Domain specific configurations require that hostname aware checkServerTrusted(X509Certificate[], String, String) is used`.

## Penyebab yang dibuktikan

APK playback sebelumnya menambahkan konfigurasi jaringan debug dengan satu aturan domain untuk HTTP loopback, khusus server uji pemutaran. Supabase belum memilih engine secara eksplisit. Dependensi CIO berada di source set common pada `composeApp` dan `ktorExt`, sehingga tersedia dan terpilih otomatis di Android. CIO melakukan pemeriksaan sertifikat tanpa hostname, yang ditolak Android apabila ada konfigurasi per domain. Aturan loopback tersebut memicu masalah pada HTTPS Supabase juga: ini perubahan konfigurasi aplikasi, bukan bug khusus merek Vivo/iQOO.

Pengujian sebelum perbaikan menggunakan klien Supabase produksi dari Koin, pada emulator API 37/halaman 16 KB. GET user dengan token dummy menghasilkan pesan yang persis sama, dan klien default melaporkan `CIOEngine` (`artifacts/supabase-tls-2026-10-09/before-fix-logcat.txt`). Permintaan bersifat baca saja, tanpa login, logout, pembuatan akun, atau pengiriman OTP.

## Perbaikan

- `SupabaseModule` secara eksplisit memakai engine platform dari `ktorExt`: OkHttp di Android/JVM dan Darwin di iOS. Supabase tidak lagi bergantung pada urutan penemuan engine dari classpath.
- Dependensi CIO pada `composeApp` dan `ktorExt` dipindahkan dari common ke JVM. Klien Android yang masih memakai `HttpClient()` juga otomatis memilih OkHttp. Koneksi penyedia musik/lirik yang sebelumnya sudah memilih engine platform tetap memakai jalurnya.
- Tidak ada trust manager permissive, hostname verifier khusus, pelemahan sertifikat, atau perubahan backend. Aturan HTTPS aplikasi tetap berlaku; pengecualian HTTP loopback tetap hanya pada debug.

## Verifikasi

Build APK debug dan kompilasi JVM berhasil. Lima tes persistensi sesi SDK dan 24 tes Media3 lulus. Pada APK akhir, tujuh pemeriksaan koneksi Android lulus (`native-results.json`):

- SDK melakukan GET user dan menerima HTTP 403 untuk token dummy: TLS telah selesai dan server menolak token sebagaimana diharapkan. Ini membuktikan koneksi, bukan login dengan akun sungguhan.
- Klien default melakukan GET health Supabase dengan hasil HTTP 200, memakai `OkHttpEngine`.
- Sertifikat self-signed dari host uji publik tetap ditolak.
- Sesi tidak berubah selama probe, pesan error konfigurasi domain tidak muncul, dan log tidak memuat FATAL EXCEPTION.

Dua belas pemeriksaan playback Android juga lulus pada APK akhir: penjaga transisi aktif saat retry, pause membatalkan retry dan melepas penjaga, pemulihan berlangsung saat layar asleep, serta penutupan task menghentikan layanan. Pengujian ulang 10 lagu HTTP tidak dilakukan pada perubahan ini; hasil 10 lagu normal/crossfade pada audit playback sebelumnya tetap merupakan bukti dari APK sebelumnya. Log dan hasil tersimpan di folder audit TLS ini.

Perangkat fisik pengguna belum terhubung. Login sungguhan, OTP, dan sinkronisasi akun sungguhan tidak dijalankan pada audit transport ini. Tidak ada klaim pengujian langsung pada Vivo I2218.

## APK pengganti

`artifacts/supabase-tls-2026-10-09/Gratify-debug-tls-fix-20261009.apk`, versi `2.1.0-dev` (61), paket `com.tan.gratify.dev`, dibuat 9 Oktober 2026 pukul 10:27:08 WIB. Ukuran 104.588.805 byte. SHA-256: `426668E5D09737197747BF4ECF333CE608D03642C895DDCE7939DF64E4E18BAE`.

APK yang dipasang di emulator memiliki hash yang sama dengan salinan ini (`installed-apk-sha256.txt`). Perbaikan playback, Search, dan penambahan lagu playlist sebelumnya tetap disertakan. APK ini menggantikan APK `Gratify-debug-playback-handoff-20261009.apk` yang memicu regresi TLS.

Rujukan implementasi: [Android RootTrustManager](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/security/net/config/RootTrustManager.java) mensyaratkan pemeriksaan dengan hostname saat konfigurasi per domain tersedia; [SupabaseClientBuilder 3.0.3](https://github.com/supabase-community/supabase-kt/blob/3.0.3/Supabase/src/commonMain/kotlin/io/github/jan/supabase/SupabaseClientBuilder.kt) mendukung engine eksplisit melalui `httpEngine`. Host sertifikat negatif tersedia pada [badssl](https://badssl.com/).
