"""Compare the two recorded probes without extending claims to physical phones."""
from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BEFORE = ROOT / 'artifacts/lyrics-performance-2026-10-11'
AFTER = ROOT / 'artifacts/lyrics-performance-fixed-2026-10-11'


def main():
    before = json.loads((BEFORE / 'android-probe.json').read_text())
    after = json.loads((AFTER / 'android-probe.json').read_text())
    assert before['complete'] and after['complete']
    previous = {(row['name'], row['lines']): row for row in before['processing']}
    comparison = []
    for row in after['processing']:
        old = previous[row['name'], row['lines']]
        comparison.append({'name': row['name'], 'lines': row['lines'],
                           'before_median_ms': old['wall_ms']['p50'], 'after_median_ms': row['wall_ms']['p50'],
                           'before_p95_ms': old['wall_ms']['p95'], 'after_p95_ms': row['wall_ms']['p95'],
                           'before_process_allocated_bytes_per_operation': old['process_allocated_bytes_per_operation'],
                           'after_process_allocated_bytes_per_operation': row['process_allocated_bytes_per_operation']})
    ui = []
    for scenario in ('line_playing', 'line_paused', 'rich_as_lines'):
        samples = [row for row in after['ui'] if row['scenario'] == scenario]
        ui.append({'scenario': scenario,
                   'cpu_one_core_percent': sum(row['cpu_ms'] for row in samples) * 100 / sum(row['duration_ms'] for row in samples),
                   'frames': sum(row['frames'] for row in samples), 'misses': sum(row['deadline_misses'] for row in samples),
                   'pss_mb_min': min(row['before']['pss_kb'] for row in samples) / 1024,
                   'pss_mb_max': max(row['after']['pss_kb'] for row in samples) / 1024})
    baseline, final = after['retention'][0], after['retention'][-1]
    cache = json.loads((AFTER / 'http-cache-retention.json').read_text())
    stress = json.loads((AFTER / 'cancellation-stress.json').read_text())
    suites = []
    for file in (AFTER / 'test-results').rglob('TEST-*.xml'):
        root = ET.parse(file).getroot()
        suites.append({'name': root.attrib['name'], **{key: int(root.attrib[key]) for key in ('tests', 'failures', 'errors', 'skipped')}})
    tests = {'suites': suites, 'totals': {key: sum(row[key] for row in suites) for key in ('tests', 'failures', 'errors', 'skipped')}}
    assert tests['totals']['failures'] == tests['totals']['errors'] == tests['totals']['skipped'] == 0
    (AFTER / 'test-summary.json').write_text(json.dumps(tests, indent=2), encoding='utf-8')
    apk = AFTER / 'Gratify-debug-performance-fixed-20261011.apk'
    checksum = hashlib.sha256(apk.read_bytes()).hexdigest().upper()
    summary = {'processing_comparison': comparison, 'ui_after': ui, 'retention_after': after['retention'],
               'disposed_idle_cpu_one_core_percent': after['disposed_idle_cpu_one_core_percent'],
               'cache_test': cache, 'cancellation_stress': stress, 'tests': tests['totals'], 'universal_apk_sha256': checksum}
    (AFTER / 'comparison.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    table = '\n'.join(f"| {row['name']} | {row['lines']} | {row['before_median_ms']:.2f} → {row['after_median_ms']:.2f} | {row['before_p95_ms']:.2f} → {row['after_p95_ms']:.2f} |" for row in comparison)
    ui_table = '\n'.join(f"| {row['scenario']} | {row['cpu_one_core_percent']:.2f}% | {row['pss_mb_min']:.1f}–{row['pss_mb_max']:.1f} | {row['misses']}/{row['frames']} |" for row in ui)
    text = f"""# Perbaikan performa lirik — 11 Oktober 2026

Cache HTTP yang sebelumnya menahan isi respons tanpa batas telah dihapus dari SimpMusic/LRCLIB/BetterLyrics dan Gratify lyrics client. Cache lirik di database tetap tersedia. Pemeriksaan/perbaikan teks kini berjalan di Default, token Spotify berjalan suspend di IO, dan loop buffer membatalkan job sebelumnya saat dimulai kembali.

## Perubahan

- HTML entity map dan regex digunakan bersama; teks tanpa entity memakai jalur cepat.
- Statistik fragmentasi menghitung teks berulang sekali per transcript. Tidak ada cache global teks lagu.
- Referensi frasa berulang diindeks dan dicari berdasarkan timestamp dengan binary search. Ties mempertahankan urutan sumber pembanding semula. Hasil perbaikan whitespace yang sama dipakai ulang dalam satu operasi; waktu, huruf, kapitalisasi dan tanda baca tetap mengikuti sumber asli.
- Resolver, lookup handler, dan guard update berjalan di Default. `generation` memakai StateFlow untuk pembacaan lintas thread.
- `runBlocking` di handler lirik dan token Spotify dihapus. Flow Spotify memakai IO dan meneruskan CancellationException.
- Batas total lookup 20 detik, dengan batas per provider tetap 8 detik. Jika enrichment timeout, kandidat aman yang sudah ditemukan tetap dikembalikan. Ini membatasi waktu tunggu dengan tradeoff sumber urutan akhir bisa belum dicoba pada jaringan sangat lambat; tombol coba lagi tetap tersedia.
- Normalisasi teks renderer diingat selama teks baris tidak berubah. Mode per baris tetap digunakan, tanpa animasi kata.
- Setting **Terjemahan lirik** kini tersedia. Default tanpa opt-in tidak memulai request terjemahan; pengguna AI yang sudah opt-in tetap bisa menerjemahkan. Mematikan sakelar juga mematikan AI translation dan menghapus terjemahan di tampilan. Guard menolak respons terjemahan terlambat setelah setting dimatikan. Menyalakan terjemahan memuat ulang lirik lagu aktif. YouTube caption API masih dapat menyertakan terjemahan dalam request caption utamanya.
- `startBufferedUpdate` membatalkan job sebelumnya; `stopBufferedUpdate` membersihkan referensinya. Lifecycle service/scope tetap memiliki cleanup semula.

## Pengukuran pemrosesan, sebelum → sesudah

| Operasi | Baris | Median ms | p95 ms |
| --- | ---: | ---: | ---: |
{table}

Metode sama: ART, 20 pemanasan dan 100 sampel tiap operasi, Android 17/API37 x86_64, emulator 2GB, 1080×2400, SwiftShader software, APK debug. Fixture semua baris berulang; 500 baris merupakan stress test, bukan generalisasi seluruh lagu. Tidak ada interval kepercayaan atau perbandingan lintas banyak perangkat; angka yang sudah kecil dapat dipengaruhi JIT/GC/startup emulator. Pemeriksaan cache terkonfirmasi 80 baris tidak membaik pada run ini; data lengkap tetap ditampilkan. Alokasi di JSON merupakan estimasi seluruh proses selama batch, bukan alokasi fungsi yang terisolasi.

## RAM, CPU diam, dan pelepasan objek

- Test produksi dengan 1.500 URL respons sintetis: **cache HTTP tidak terpasang**, sehingga **0 byte isi respons ditahan oleh storage HTTP**; sebelumnya 6.144.000 byte. Ini bukan klaim seluruh heap proses nol atau pengukuran trafik server nyata. Request tanpa cache header tetap memanggil engine pada reread, seperti sebelumnya.
- 1.000 cancellation resolver + 1.000 penutupan story: **{stress['pending_fetches_after_completion']} fetch tersisa**, **{stress['story_models_alive_after_gc']} model story tertahan** setelah GC.
- 60 siklus pemasangan/pembuangan komposisi dan 2.000 transcript tambahan: **{final['fixture_weak_refs_alive']} model uji tertahan**, thread {baseline['threads']} → {final['threads']}.
- Java heap setelah GC: {baseline['java_live_bytes']/1048576:.2f} → {final['java_live_bytes']/1048576:.2f} MiB. Native allocation: {baseline['native_allocated_bytes']/1048576:.2f} → {final['native_allocated_bytes']/1048576:.2f} MiB. PSS: {baseline['pss_kb']/1024:.1f} → {final['pss_kb']/1024:.1f} MiB. PSS allocator/cache runtime bisa bertahan walau objek sudah dilepas; hasil ini tidak membuktikan seluruh aplikasi bebas leak.
- CPU setelah UI dibuang: **{after['disposed_idle_cpu_one_core_percent']:.2f}%** dari satu inti.

| Renderer setelah perbaikan | CPU proses, satu inti | PSS MiB | Frame lewat deadline / sampel |
| --- | ---: | ---: | ---: |
{ui_table}

Renderer masih menunjukkan frame lambat saat scrolling/fokus di GPU software. Perbaikan ini mengurangi biaya pemrosesan teks dan menghapus retensi cache; bukan klaim semua masalah rendering selesai. Audio, streaming, layar pemain penuh, baterai, suhu, APK release/R8, dan ponsel fisik belum diukur. Loop buffer diverifikasi pada kode dan kompilasi Android, belum melalui stress playback nyata.

## Verifikasi dan APK

**{tests['totals']['tests']} test lolos**, 0 failure/error/skip. Termasuk regresi fragmentasi, HTML Unicode, nearest timestamp/ties, total timeout yang tetap mempertahankan lirik aman, dispatcher, cancel/close stress, cache HTTP, dan handler terjemahan mati/response terlambat. Production GET untuk “33x” tetap menemukan 65 baris utuh, target 52.260 ms, cache aman.

- Build final sukses: `verified-build.log`. Test handler tambahan: `handler-tests.log`. XML dan `test-summary.json` disimpan di sini.
- APK universal debug: `Gratify-debug-performance-fixed-20261011.apk`, app ID `com.tan.gratify.dev`, versi `2.1.0-dev`, versionCode61. Signature v2 terverifikasi; bukan release publik.
- SHA256 universal APK: `{checksum}`.
- APK x86_64 yang dipakai emulator memiliki hash sendiri di `environment.json`; source produksi sama, native ABI berbeda.
- Data pengukuran: `android-probe.json`, `comparison.json`, `runtime.log`, `meminfo-final.txt`, `gfxinfo-final.txt`, `http-cache-retention.json`, `cancellation-stress.json`, `live-33x.txt`.
- Tidak ada commit/push Git untuk perbaikan ini; perubahan workspace pengguna yang lain dipertahankan.

Audit sebelum perbaikan: [laporan sebelumnya](../lyrics-performance-2026-10-11/README.md). Penjelasan PSS/GC: [Android Developers](https://developer.android.com/topic/performance/memory-overview). Implementasi cache sebelumnya: [Ktor 3.5.0](https://github.com/ktorio/ktor/blob/3.5.0/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/cache/HttpCache.kt).
"""
    (AFTER / 'README.md').write_text(text, encoding='utf-8')
    print(json.dumps({'tests': tests['totals'], 'processing': comparison, 'final_retention': final,
                      'idle_cpu': after['disposed_idle_cpu_one_core_percent']}, indent=2))


if __name__ == '__main__':
    main()
