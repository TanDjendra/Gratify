from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "artifacts/launch-audit"
# This generator records the original local-only milestone. Preserve the later
# live-service/device report instead of replacing it with historical pending flags.
if (OUT / "database-deployment.json").is_file() and json.loads((OUT / "database-deployment.json").read_text()).get("status") == "APPLIED":
    raise SystemExit("Live deployment exists; update LAUNCH_COMPLETION directly from current evidence")
verification = json.loads((OUT / "fixes-verification.json").read_text())
package = json.loads((OUT / "release-package-verification.json").read_text())
assert verification["full_build_successful"] and verification["failures"] == 0
assert verification["release_signed"] and package["status"] == "PASS"
signing = verification["signing"]
lint = verification["lint"]
counts = package["lint_issues_by_id"]
version_notices = sum(counts.get(name, 0) for name in ("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion"))
table = "\n".join(f"| `{name}` | {count} |" for name, count in sorted(counts.items()))
report = f"""# Penyelesaian perbaikan Gratify — 1 Oktober 2026

## Status terbaru

Perbaikan kode lokal berikut telah diterapkan dan diverifikasi. APK terbaru sudah ditandatangani memakai keystore pemilik. **Rilis produksi belum dinyatakan 100% selesai:** penerapan Supabase, konfigurasi dan event Sentry, dokumen resmi, CI serta pengujian perangkat masih memerlukan verifikasi nyata.

## Perubahan pada tahap ini

- Playlist memperoleh identitas tetap (`sync_id` / `client_sync_id`). ID numerik perangkat dan judul tidak digunakan untuk menyamakan atau menghapus playlist lintas perangkat.
- Room naik ke versi 27 dengan migrasi otomatis. Pemulihan menggunakan ID hasil insert dalam transaksi, bukan memilih ID terbesar setelah insert. Restore ulang tidak membuat salinan baru untuk identitas yang sama. Kegagalan tidak meninggalkan playlist setengah terisi.
- Playlist berjudul sama tetap terpisah. Pembersihan lama yang menghapus berdasarkan judul dihentikan. Semua data historis dipertahankan; salinan lama yang hubungannya ambigu perlu ditinjau pengguna, termasuk antrean hapus lama yang hanya menyimpan ID numerik. Aplikasi tidak menyatakan cadangan itu sudah terhapus.
- Publikasi cloud/shared, penyembunyian dan penghapusan berjalan lewat transaksi server. Mutasi REST lama pada empat tabel playlist dibatasi. Tombstone mencegah playlist yang sudah dihapus diterbitkan ulang oleh perangkat lama dan diterapkan saat pemulihan lokal.
- Snapshot lagu cloud/shared dibaca lengkap dan berurutan sebagai satu nilai JSON. Pengujian menggunakan 1.025 lagu, termasuk pemeriksaan bahwa snapshot tidak melewati pembatasan privasi.
- Pilihan privasi lama dengan pemilik akun yang diketahui disimpan per akun sebelum cache dibersihkan. Perubahan yang belum terkirim tetap tersedia untuk retry. Pilihan akun A tidak diterapkan ke B. Data tanpa pilihan privasi eksplisit memakai nilai awal privat. Pilihan lama tanpa identitas pemilik tidak diatribusikan ke akun lain secara otomatis.
- Profil menolak hasil akun lama setelah pergantian akun; permintaan profil sebelumnya dibatalkan saat membuka profil berikutnya. Status publik playlist mengikuti hasil transaksi server.
- Seluruh inisialisasi PipePipe di aplikasi, termasuk `NewPipeUtils` Android/JVM, memakai adapter TLS yang aman. Tes memakai koneksi HTTPS nyata dan memastikan sertifikat tidak dipercaya serta hostname salah ditolak.
- Izin penyimpanan lama yang tidak digunakan dihapus, guard Android di bawah minSdk dihapus, URI memakai KTX, resource qualifier yang tidak diperlukan dirapikan, dan widget diberi ukuran minimum untuk Android lama serta konfigurasi sel untuk API 31+.
- Penandatanganan membaca rahasia dari pengaturan lokal pemilik, memakai password melalui environment proses, memeriksa tanda tangan dan alignment, serta tidak mengubah keystore.

## Hasil verifikasi

| Pemeriksaan | Hasil |
|---|---|
| Tes JVM lintas enam modul | **{verification['tests']} lulus; {verification['failures']} gagal** |
| Tes komponen UI di dalam jumlah tersebut | 9 lulus |
| PostgreSQL lokal terpisah | **{len(verification['database']['checks'])} pemeriksaan lulus** |
| Build debug dan release full | Berhasil |
| Android lint debug | **{lint['errors']} error; {lint['warnings']} warning** |
| APK rilis | Paket `com.tan.gratify`; bukan debuggable; tanda tangan diverifikasi |
| Pustaka native 64-bit | {package['elf_64bit_libraries_checked']} ELF diperiksa; seluruh segmen LOAD memenuhi alignment 16 KB |
| Resource Terms/Privacy | Sama dengan draf terbaru, termasuk identitas pengelola dan dukungan |
| Uji Supabase aktif / Auth / Storage | Belum dilakukan |
| Uji instalasi/pembaruan, ANR/FPS dan perangkat 16 KB | Belum dilakukan |

APK: `androidApp/build/outputs/apk/release/Gratify-release-signed.apk`.

SHA-256 APK: `{signing['sha256']}`.

Jumlah warning mencakup {version_notices} pemberitahuan versi dependensi/plugin. Versi yang dibatasi untuk kompatibilitas tidak diperbarui secara massal. Dua warning trust manager berasal dari kode dalam dependensi PipePipe; jalur aplikasi yang memanggil initializer tersebut sudah diganti dan tes penolakan TLS lulus. Laporan R8 untuk build rilis juga mengonfirmasi penghapusan ketiga overload `NewPipe.init`, `trustEveryone`, dan dua kelas anonimnya yang tidak dipakai. Warning ChromeOS diperiksa melalui paket APK yang benar-benar mencantumkan x86_64. Perubahan versi dependensi tetap perlu penilaian kompatibilitas dan pengujian tersendiri.

| Jenis lint | Jumlah |
|---|---|
{table}

Alignment ELF dan tanda tangan adalah pemeriksaan paket, bukan bukti bahwa semua fungsi sudah berjalan pada perangkat nyata. [Rujukan Android untuk alignment 16 KB](https://developer.android.com/guide/practices/page-sizes).

## Pekerjaan yang masih membutuhkan layanan/perangkat

1. Terapkan tujuh migrasi pada proyek Supabase Gratify yang benar. `SUPABASE_DB_URL` belum tersedia. Alternatif SQL Editor tersedia dalam `artifacts/launch-audit/supabase-launch-fixes.sql`, dengan pemeriksaan kontrak dan satu transaksi; paket itu telah diuji lokal. Jika memakai koneksi langsung, sertifikat dan hostname diverifikasi (`verify-full`).
2. Setelah penerapan, uji dua akun serta Auth, Storage/avatar, email pemulihan, privasi dan dua perangkat termasuk kondisi offline. Klien lama perlu diperbarui bersama migrasi karena jalur tulis lama ditutup.
3. Isi `SENTRY_DSN` untuk versi full, lalu periksa event uji dan redaksi pada proyek Sentry. APK full ini belum mempunyai DSN produksi.
4. Tinjau dan setujui isi Terms/Privacy serta lengkapi fakta operasional dan URL resmi. Lokasi `SignUpScreen.kt` yang diberikan pemilik merupakan lokasi kode, bukan URL kebijakan. Label draf masih dipertahankan.
5. Jalankan CI dari perubahan terbaru dan uji APK bertanda tangan sebagai pembaruan atas versi yang telah dibagikan. Build lokal tidak dianggap hasil CI.

Petunjuk rinci ada di `docs/RELEASE_SETUP.md`; dokumen legal ada di `docs/legal/REVIEW.md`.

## Bukti

- `artifacts/launch-audit/completion-final-build.log`
- `artifacts/launch-audit/fixes-verification.json`
- `artifacts/launch-audit/database-fixes-verification.json`
- `artifacts/launch-audit/signing-verification.json`
- `artifacts/launch-audit/release-package-verification.json`
- `artifacts/launch-audit/packaged-legal-verification.json`

Laporan audit awal tetap dipertahankan sebagai rekaman temuan awal, bukan status penyelesaian terbaru.
"""
(ROOT / "LAUNCH_COMPLETION_2026-10-01.md").write_text(report, encoding="utf-8")
for name in ("LAUNCH_FIXES_2026-10-01.md", "UI_PLAYLIST_ALBUM_2026-10-01.md"):
    path = ROOT / name
    prefix = "Status terbaru: [penyelesaian dan APK bertanda tangan](LAUNCH_COMPLETION_2026-10-01.md). Angka/build pada laporan berikut merekam tahap sebelumnya.\n\n"
    original = path.read_text(encoding="utf-8")
    if not original.startswith("Status terbaru:"):
        path.write_text(prefix + original, encoding="utf-8")
tracker_path = OUT / "bug-tracker.json"
tracker = json.loads(tracker_path.read_text())
for bug in tracker:
    bug["repair_report"] = "LAUNCH_COMPLETION_2026-10-01.md"
tracker_path.write_text(json.dumps(tracker, indent=2, ensure_ascii=False), encoding="utf-8")
print("Completion report written; external release gates remain explicit")
