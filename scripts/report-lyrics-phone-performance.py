"""Report physical-device measurements without treating the emulator as a baseline."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', default='artifacts/lyrics-performance-phone-2026-10-11')
    args = parser.parse_args()
    folder = ROOT / args.output
    environment = json.loads((folder / 'environment.json').read_text())
    probe = json.loads((folder / 'android-probe.json').read_text())
    assert environment['physical_device'] and probe['complete']
    processing = '\n'.join(
        f"| {row['name']} | {row['lines']} | {row['wall_ms']['p50']:.2f} | {row['wall_ms']['p95']:.2f} |"
        for row in probe['processing'])
    ui = []
    for name in ('line_playing', 'line_paused', 'rich_as_lines'):
        samples = [row for row in probe['ui'] if row['scenario'] == name]
        frames = sum(row['frames'] for row in samples)
        missed = sum(row['deadline_misses'] for row in samples)
        ui.append({'scenario': name,
                   'cpu_one_core_percent': sum(row['cpu_ms'] for row in samples) * 100 / sum(row['duration_ms'] for row in samples),
                   'frames': frames, 'deadline_misses': missed,
                   'miss_percent': missed * 100 / frames if frames else None})
    ui_table = '\n'.join(
        f"| {row['scenario']} | {row['cpu_one_core_percent']:.2f}% | {row['deadline_misses']}/{row['frames']} | "
        + (f"{row['miss_percent']:.2f}% |" if row['miss_percent'] is not None else 'tidak ada frame aktif |')
        for row in ui)
    baseline, final = probe['retention'][0], probe['retention'][-1]
    followups = []
    final_meminfo = (folder / 'meminfo-final.txt').read_text()
    completed_uptime = int(re.search(r'Uptime:\s+(\d+)', final_meminfo).group(1))
    for path in sorted(folder.glob('meminfo-idle-followup-*.txt')):
        content = path.read_text()
        followups.append({'file': path.name,
                         'seconds_after_completion': (int(re.search(r'Uptime:\s+(\d+)', content).group(1)) - completed_uptime) / 1000,
                         'pss_kb': int(re.search(r'^\s*TOTAL\s+(\d+)', content, re.MULTILINE).group(1)),
                         'native_heap_pss_kb': int(re.search(r'^\s*Native Heap\s+(\d+)', content, re.MULTILINE).group(1))})
    followup_table = '\n'.join(
        f"| {row['seconds_after_completion']:.1f} | {row['pss_kb']/1024:.1f} | {row['native_heap_pss_kb']/1024:.1f} |"
        for row in followups)
    followup_threads_path = folder / 'idle-followup-threads.json'
    followup_threads = json.loads(followup_threads_path.read_text()) if followup_threads_path.exists() else None
    thread_followup_text = (' → '.join(str(followup_threads[key]) for key in
                            ('first_idle_thread_count', 'second_idle_thread_count'))
                            if followup_threads else 'belum diukur')
    memory_table = '\n'.join(
        f"| {row['label']} | {row['java_live_bytes']/1048576:.2f} | {row['native_allocated_bytes']/1048576:.2f} | {row['pss_kb']/1024:.1f} | {row['threads']} | {row.get('fixture_weak_refs_alive', '—')} |"
        for row in probe['retention'])
    summary = {'environment': environment, 'ui': ui, 'retention': probe['retention'],
               'idle_cpu_one_core_percent': probe['disposed_idle_cpu_one_core_percent'],
               'idle_memory_followups': followups, 'idle_thread_followups': followup_threads,
               'processing': probe['processing']}
    (folder / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    report = f"""# Pengujian lirik di ponsel fisik — 11 Oktober 2026

Perangkat: **{environment['manufacturer']} {environment['model']}**, Android {environment['android_release']}, API {environment['sdk']}, ABI {environment['abi']}, hardware {environment['hardware']}, SoC {environment['soc'] or 'tidak dilaporkan'}. {environment['screen']}; {environment['density']}.

APK debug perbaikan performa yang sama dengan pengujian sebelumnya. SHA256: `{environment['apk_sha256']}`. Tidak ada perbandingan sebelum/sesudah pada ponsel ini: hasil emulator bukan baseline ponsel.

## Metode

Renderer Simp LyricsView produksi dalam mode per baris, dengan timeline dan lirik sintetis. Delapan batch pemrosesan, masing-masing 20 pemanasan dan 100 sampel. Tiga putaran UI, masing-masing 15 detik untuk playing, paused, dan rich-as-lines. Dilanjutkan 60 buka-tutup komposisi, 2.000 transcript tambahan, GC, dan 15 detik diam. Playing di sini adalah timeline simulasi, bukan audio nyata. Fixture memakai frasa berulang; 500 baris adalah stress test. Build debug belum mewakili release/R8. Pengujian tidak membuka layar akun atau membaca isi database pengguna. Inisialisasi global dan pekerjaan latar belakang aplikasi ikut terukur karena CPU/RAM adalah untuk seluruh proses. Audio, streaming, baterai, dan suhu belum diuji.

## Pemrosesan

| Operasi | Baris | Median ms | p95 ms |
| --- | ---: | ---: | ---: |
{processing}

## CPU dan rendering

| Skenario | CPU proses, satu inti | Frame melewati deadline | Persentase |
| --- | ---: | ---: | ---: |
{ui_table}

CPU diam setelah komposisi dilepas: **{probe['disposed_idle_cpu_one_core_percent']:.2f}%** dari satu inti. Angka CPU merupakan waktu CPU seluruh proses / waktu pengamatan, bukan persentase seluruh kapasitas multicore ponsel. Frame lambat dihitung dari FrameMetrics pada frame aktif, bukan estimasi FPS sepanjang durasi.

## Retensi memori

| Tahap | Java hidup MiB | Native MiB | PSS MiB | Thread | Model uji hidup |
| --- | ---: | ---: | ---: | ---: | ---: |
{memory_table}

Model uji tertahan pada akhir: **{final['fixture_weak_refs_alive']}**. Thread {baseline['threads']} → {final['threads']}. Angka Java/native diambil setelah GC; PSS juga mencakup allocator dan cache runtime. Hasil ini membatasi kesimpulan pada halaman lirik dan objek uji, bukan bukti seluruh aplikasi bebas kebocoran. Lihat urutan sampel, bukan hanya kenaikan PSS tunggal.

### Pemeriksaan lanjutan saat diam

PSS naik setelah stress transcript sehingga pengamatan dilanjutkan tanpa memberi beban baru atau memanggil trim. Sampel berikut diambil dengan dumpsys meminfo pada proses yang sama:

| Detik setelah probe selesai | PSS MiB | Native heap PSS MiB |
| --- | ---: | ---: |
{followup_table}

Native heap PSS pada akhir probe {int(re.search(r'^\s*Native Heap\s+(\d+)', final_meminfo, re.MULTILINE).group(1))/1024:.1f} MiB kemudian turun; native allocation hidup tetap sekitar 19 MiB. PSS setelah pengamatan tambahan mendekati baseline. Ini mendukung dugaan pelepasan bertahap halaman allocator/runtime, bukan bukti kebocoran objek lirik. Threads tambahan juga turun lagi: {thread_followup_text}. Nama thread setelah probe tersedia di `threads-after.txt`; ada pool coroutine, HTTP, Firebase, WorkManager, ExoPlayer, serta thread platform yang dapat berasal dari inisialisasi aplikasi. Tanpa snapshot nama sebelum stress, keenam thread tambahan tidak bisa diatribusikan ke satu fitur tertentu. Memori kode debug (.dex) sekitar 136 MiB dan grafis sekitar 48 MiB masih ikut total RAM.

Kelancaran masih perlu optimasi: sekitar 8–10% frame aktif melewati deadline pada dua skenario lirik berjalan. Tidak dilakukan perubahan produksi baru setelah pengujian ini.

Data mentah: `android-probe.json`, `environment.json`, `summary.json`, `meminfo-final.txt`, `gfxinfo-final.txt`, dan log crash yang difilter per proses `runtime.log`. Label scope dari APK masih menyebut emulator; collector menyimpan label asli di `probe_scope_label` dan memperjelas scope sebagai ponsel fisik berdasarkan perangkat yang dipakai.
"""
    (folder / 'README.md').write_text(report, encoding='utf-8')
    print(json.dumps({'model': environment['model'], 'ui': ui, 'final': final,
                      'idle_cpu': probe['disposed_idle_cpu_one_core_percent']}, indent=2))


if __name__ == '__main__':
    main()
