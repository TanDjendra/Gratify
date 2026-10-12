"""Summarize the local diagnostics, retaining measurement limits in the report."""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts/lyrics-performance-2026-10-11"


def main():
    report = json.loads((OUT / "android-probe.json").read_text())
    assert report["complete"], "Wait for the probe to complete before reporting final results"
    cache = json.loads((OUT / "http-cache-retention.json").read_text())
    stress = json.loads((OUT / "cancellation-stress.json").read_text())
    rows = []
    for scenario in ["line_playing", "line_paused", "rich_as_lines"]:
        samples = [row for row in report["ui"] if row["scenario"] == scenario]
        cpu = sum(row["cpu_ms"] for row in samples) * 100 / sum(row["duration_ms"] for row in samples)
        frames = sum(row["frames"] for row in samples)
        misses = sum(row["deadline_misses"] for row in samples)
        rows.append({
            "scenario": scenario, "runs": len(samples), "process_cpu_one_core_percent": cpu,
            "frames_collected": frames, "deadline_misses": misses,
            "pss_mb_min": min(row["before"]["pss_kb"] for row in samples) / 1024,
            "pss_mb_max": max(row["after"]["pss_kb"] for row in samples) / 1024,
        })
    baseline = report["retention"][0]
    after60 = next(row for row in report["retention"] if row["label"] == "disposed_60")
    final = report["retention"][-1]
    summary = {
        "android_complete": True, "scope": report["scope"], "processing": report["processing"],
        "ui": rows, "retention_baseline": baseline, "retention_after_60_cycles": after60,
        "retention_final": final, "disposed_idle_cpu_one_core_percent": report["disposed_idle_cpu_one_core_percent"],
        "cache_test": cache, "cancellation_stress": stress,
    }
    (OUT / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    processing_table = "\n".join(
        f"| {row['name']} | {row['lines']} | {row['wall_ms']['p50']:.2f} | {row['wall_ms']['p95']:.2f} | {row['thread_cpu_ms_per_operation']:.2f} | {row['process_allocated_bytes_per_operation']/1048576:.3f} |"
        for row in report["processing"]
    )
    ui_table = "\n".join(
        f"| {row['scenario']} | {row['runs']} | {row['process_cpu_one_core_percent']:.2f}% | {row['pss_mb_min']:.1f}–{row['pss_mb_max']:.1f} | {row['deadline_misses']}/{row['frames_collected']} |"
        for row in rows
    )
    content = f"""# Audit performa lirik Gratify — 11 Oktober 2026

Ada pertumbuhan RAM yang terbukti pada cache HTTP tanpa kapasitas maksimum, serta pemrosesan teks yang bisa mengganggu respons tampilan. Uji objek lirik dan pembatalan permintaan tidak menunjukkan penumpukan dalam skenario yang diuji. Audit ini tidak menyatakan seluruh aplikasi bebas kebocoran.

## Cakupan dan lingkungan

- APK debug terbaru dengan alat ukur khusus debug; Android 17/API 37, x86_64, emulator RAM 2 GB, layar 1080×2400, density 420, GPU Google SwiftShader software.
- Memakai `com.tan.gratify.ui.screen.player.simp.component.LyricsView`, komponen produksi saat ini, `animationsEnabled=false`. Bukan renderer Apple lama.
- Fixture sintetis 80/500 baris. Semua baris memakai frasa sama; 500 baris adalah stress test dengan pengulangan ekstrem, bukan gambaran lagu 500 baris yang semuanya berbeda.
- Pengukuran fungsi di ART, 20 pemanasan + 100 sampel per kasus; 3 putaran tampilan × 3 skenario × 15 detik; 60 pergantian/pembuangan komposisi; 2.000 transcript diproses lalu dilepas.
- Uji JVM tambahan memakai resolver/story flow produksi dan konfigurasi HTTP produksi dengan penyedia sintetis/MockEngine.
- Tidak mencakup streaming, decoding audio, layar penuh pemain, jaringan nyata sepanjang sesi, baterai, kondisi panas, release/R8, atau ponsel fisik. Tidak ada perangkat fisik terhubung.
- Tidak ada kode produksi diubah pada audit ini. Penambahan hanya activity/manifest debug, test opt-in, skrip, dan laporan. Tidak ada commit/push.

## Temuan berprioritas

1. **P1 — Cache HTTP respons bertumbuh tanpa batas kapasitas. Terbukti lewat test.** `SimpMusicLyrics.configureSimpMusicLyrics` memasang `HttpCache` tanpa storage terbatas; runtime Ktor 3.5.0 memakai `UnlimitedStorage`. Sesudah 500, 1.000, dan 1.500 URL berbeda, storage tetap menahan 500, 1.000, dan 1.500 respons setelah GC, masing-masing 1,95 / 3,91 / **{cache['retained_body_bytes']/1048576:.2f} MiB isi respons**, belum termasuk objek/header. Respons paling awal masih ada. Test tidak memberikan Cache-Control/Expires, seperti header dua endpoint publik yang diperiksa; pembacaan ulang URL pertama tetap memanggil engine, total {cache['network_calls_including_cached_reread']} panggilan. Retensi RAM ini tidak memberi manfaat cache hit pada kasus itu. Besar pertumbuhan sesi penggunaan nyata belum diukur. Konfigurasi `GratifyLyrics` juga memasang default `HttpCache`, tetapi tidak diuji dinamis dalam test ini. Solusi: hilangkan cache RAM HTTP karena lirik sudah disimpan di database, atau beri batas entry/byte dan eviction termasuk respons kedaluwarsa.

2. **P1 — Pemeriksaan/perbaikan teks berpotensi menahan thread tampilan. Terukur + alur kode terverifikasi.** Fungsi perbaikan 80 baris median 30,43 ms dan 500 baris berulang 251,71 ms. `SharedViewModel` memberi `viewModelScope` kepada `LyricsHandler`; assess/reconcile/guard di handler/resolver berjalan di dispatcher caller, walau pengambilan/mapping repository berjalan di IO. Jadi beban CPU masih dapat kembali ke Main. `decodeHtmlEntities` membuat map dan dua regex setiap panggilan; guard dan rekonsiliasi menormalisasi teks berulang. Jika banyak baris identik, `groupBy(signature)` lalu pencarian timestamp terdekat memindai banyak kandidat untuk setiap baris, mendekati O(n²). Solusi: pindahkan seluruh pemeriksaan/perbaikan ke Default, hitung teks/signature sekali per transcript, simpan regex/map immutable, dan indeks timestamp reference untuk frasa berulang. Angka adalah emulator/fixture ini, bukan waktu pasti di ponsel.

3. **P1 — `runBlocking` di alur lirik bisa membekukan tampilan. Audit kode; kejadian ANR belum direproduksi.** `LyricsHandler.updateLyrics` membaca setting kontribusi dengan `runBlocking`, bahkan sebelum kondisi provider yang tidak memerlukan kontribusi. Cabang token Spotify juga memakai `runBlocking` dan flow `getSpotifyLyrics` tidak memiliki `flowOn(IO)`. Pengambilan token bisa menahan Main dan sulit dihentikan tepat waktu; batas timeout resolver tidak menjadi deadline keras untuk operasi blocking. Solusi: baca setting secara suspend/cached StateFlow, pindahkan pekerjaan token ke IO dan pertahankan structured cancellation.

4. **P2 — Pencarian sumber cadangan bisa lama dan melakukan permintaan tambahan. Audit kode + test timeout.** Ada maksimal {stress['maximum_production_providers']} provider berurutan × 8 detik, nominal hingga 48 detik untuk provider yang kooperatif ketika semuanya timeout, di luar cache/setting/terjemahan. Cache yang aman ditampilkan lebih awal tetapi tetap dipakai untuk refresh jaringan. Cabang SimpMusic/Gratify memulai terjemahan setelah lirik dipilih tanpa memeriksa setting tampilan terjemahan di cabang itu, dengan batas 12 detik. Solusi: budget total lookup, freshness cache yang eksplisit, request terjemahan hanya jika diperlukan. Jangan menjalankan semua provider paralel tanpa batas.

5. **P2 — Loop buffer dapat menumpuk bila start dipanggil berulang. Audit kode; belum direproduksi dengan audio.** `MediaServiceHandlerImpl.startBufferedUpdate` mengganti referensi job tanpa membatalkan job lama, berbeda dengan `startProgressUpdate`. Jika loading=true masuk berulang, stop hanya membatalkan job terakhir; loop sebelumnya tetap bangun setiap 500 ms sampai scope service dilepas. Adapter/player callback berulang perlu diuji dengan pemutaran nyata. Solusi: batalkan job sebelumnya sebelum start, atau buat start idempotent. Ini temuan di jalur media lama, bukan tambahan dari pemeriksaan kualitas lirik.

## Pemrosesan CPU dan alokasi sementara

| Operasi | Baris | Median ms | p95 ms | CPU thread ms/op | Estimasi alokasi proses MiB/op |
| --- | ---: | ---: | ---: | ---: | ---: |
{processing_table}

Alokasi proses mencakup pekerjaan runtime lain selama batch, sehingga bukan ukuran alokasi fungsi yang terisolasi. Alokasi sementara dapat dibersihkan GC; jangan samakan angka ini dengan kebocoran. Cache terkonfirmasi menghindari banyak pemeriksaan statistik dan jauh lebih cepat. Fungsi kualitas tidak dijalankan tiap frame oleh renderer.

## Tampilan Android

| Skenario | Putaran | CPU proses, satu inti | RAM PSS MiB | Frame melewati deadline / sampel |
| --- | ---: | ---: | ---: | ---: |
{ui_table}

CPU adalah persentase waktu satu inti: 100% setara satu inti penuh, bukan persentase semua inti. Angka seluruh proses, bukan fungsi lirik saja. Banyak frame aktif terlambat pada SwiftShader; blur/fokus dan scrolling produksi perlu diprofilkan di perangkat fisik. Ini bukti skenario emulator lambat, bukan estimasi FPS ponsel dan bukan bukti bahwa fix lirik menyebabkan regresi. Tidak ada benchmark versi sebelum fix. Skenario pause menghasilkan nol frame baru setelah settle, dan CPU rendah; tidak terlihat render loop berat saat diam.

## Retensi memori dan pembatalan

- 1.000 cancellation resolver + 1.000 pratinjau diambil dengan `first()` lalu ditutup: **{stress['pending_fetches_after_completion']} fetch tersisa**, **{stress['story_models_alive_after_gc']} dari {stress['tracked_story_models']} model story tertahan** setelah GC. Ini menguji resolver/story flow, bukan seluruh LyricsHandler/Ktor/MediaService.
- 60 komposisi renderer dipasang/dibuang: **{after60['fixture_weak_refs_alive']} model fixture tertahan**. Java heap terpakai baseline {baseline['java_live_bytes']/1048576:.2f} MiB, setelah 60 siklus {after60['java_live_bytes']/1048576:.2f} MiB; native allocation {baseline['native_allocated_bytes']/1048576:.2f} → {after60['native_allocated_bytes']/1048576:.2f} MiB; thread {baseline['threads']} → {after60['threads']}.
- Setelah 2.000 transcript tambahan dan idle: **{final['fixture_weak_refs_alive']} model tertahan**, Java heap {final['java_live_bytes']/1048576:.2f} MiB, native allocation {final['native_allocated_bytes']/1048576:.2f} MiB, thread {final['threads']}; CPU idle setelah UI dibuang **{report['disposed_idle_cpu_one_core_percent']:.2f}%** satu inti.
- PSS baseline {baseline['pss_kb']/1024:.1f} → setelah 60 siklus {after60['pss_kb']/1024:.1f} → akhir {final['pss_kb']/1024:.1f} MiB. PSS bisa tertahan oleh allocator/runtime/graphics cache walaupun objek sudah bebas. Kenaikan PSS saja tidak membuktikan kebocoran.
- Tidak ada crash/OOM pada probe. Tidak ada indikasi model lirik atau thread renderer bertambah tanpa pelepasan dalam workload ini; belum ada heap dominator analysis atau pengujian berjam-jam seluruh aplikasi.

## Bukti dan pengulangan

Data: `android-probe.json`, `summary.json`, `environment.json`, `runtime.log`, `meminfo-final.txt`, `gfxinfo-final.txt`, `cancellation-stress.json`, `http-cache-retention.json`, XML dua test, dan build logs. SHA256 APK probe ada di environment.json. Source activity yang digunakan disalin ke folder ini.

Test JVM opt-in: set `GRATIFY_LYRICS_PERFORMANCE_CHECK=1`, jalankan `:composeApp:jvmTest --tests '*LyricsPerformanceStressTest'` dan `:lyricsService:jvmTest --tests '*LyricsHttpCacheRetentionTest'`. Probe Android: build debug, install ABI yang sesuai, start `com.tan.gratify.LyricsQualityPerformanceActivity`; collector ada di `scripts/measure-lyrics-quality-performance.py` (serial emulator-5556), report generator `scripts/summarize-lyrics-quality-performance.py`.

Rujukan primer: [pengelolaan memori Android](https://developer.android.com/topic/performance/memory-overview), [rendering Android](https://developer.android.com/topic/performance/issues/render), [konfigurasi cache Ktor 3.5.0](https://github.com/ktorio/ktor/blob/3.5.0/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/cache/HttpCache.kt), [storage cache Ktor 3.5.0](https://github.com/ktorio/ktor/blob/3.5.0/ktor-client/ktor-client-core/common/src/io/ktor/client/plugins/cache/storage/UnlimitedCacheStorage.kt).
"""
    (OUT / "README.md").write_text(content, encoding="utf-8")
    print(json.dumps({"ui": rows, "final_retention": final, "idle_cpu_one_core_percent": report["disposed_idle_cpu_one_core_percent"]}, indent=2))


if __name__ == "__main__":
    main()
