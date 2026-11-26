# Performance record (baseline) — তুলনার জন্য

লগ: `files/fx_perf_log.jsonl` (প্রতি লাইন একটা JSON, ~৬৪KB সীমা)
দেখতে: `adb shell run-as com.banglakeys.app cat files/fx_perf_log.jsonl`

| event | মানে |
|---|---|
| `baseline` | effects **বন্ধ** অবস্থায় প্রতি ৯০০ frame-এর সারসংক্ষেপ |
| `with_fx` | effects **চালু** অবস্থায় একই সারসংক্ষেপ |
| `auto_disable` | guard effects বন্ধ করেছে (note-এ কারণ) |

ক্ষেত্র: `avg_draw_ms`, `p95_draw_ms`, `avg_lat_ms`, `p95_lat_ms`, `samples`, `fx_level`, `device`, `sdk`।

## কীভাবে তুলনা করবেন
1. Settings → Key effects **বন্ধ** → ২০০+ key টাইপ করুন → `baseline` লাইন পাবেন।
2. Key effects **চালু** → একই ভাবে টাইপ → `with_fx` লাইন।
3. `with_fx.p95_draw_ms` যদি `baseline`-এর **দ্বিগুণের বেশি** বা ১২ms-এর উপরে হয় → Light-এ রাখুন বা বন্ধ করুন।

## লক্ষ্যমাত্রা (সুস্থ অবস্থা)
avg draw < 7ms · p95 draw < 12ms · p95 touch→draw latency < 45ms (৬০Hz-এ ১ frame ≈ 16.7ms)।
সীমা: `PerfRecorder.AVG_DRAW_LIMIT_MS` ইত্যাদি। দ্রষ্টব্য: এটা CPU-draw সময়; GPU সময় নয় — সন্দেহ হলে
`adb shell dumpsys gfxinfo <pkg>` দিয়ে jank% মিলিয়ে দেখুন।

## ইতিহাস (হাতে লিখে রাখুন)
| তারিখ | ডিভাইস | বিল্ড | fx level | avg | p95 | মন্তব্য |
|---|---|---|---|---|---|---|
| | | v3.1 (effects ছাড়া) | 0 | | | baseline |
| | | v3.2 | 1 | | | |
