# Changelog

## v3.3 — প্রভাত (Ridmik fixed) Bangla layout
- নতুন `Layouts.probhat()` — স্ক্রিনশট অনুযায়ী Ridmik-এর "প্রভাত": বাংলা অক্ষরের key, Shift-অক্ষর হিন্ট, Bengali সংখ্যা সারি (Latin হিন্টসহ), স্পেসবারে "◀ প্রভাত ▶"।
- `Key.shiftLabel`; Shift চাপলে বড় অক্ষর, দীর্ঘ চাপে একই অক্ষর। Phonetic layout অপরিবর্তিত।
- বদলানো: Settings toggle, বা কিবোর্ডে 🌐 চেপে ধরুন।
- ⚠️ `.` key (নুক্তা ় / Shift ঁ) স্ক্রিনশট থেকে অনুমান — `Layouts.PROBHAT_KEY_DOT` মিলিয়ে নিন।
- ফেরত: `docs/fx-backup/Layouts.kt.orig`, `BanglaIME.kt.pre-probhat`।

## v3.2.1 — যাচাই + গোছানো
- Review: effects/translate/secret কোডের symbol, brace, import যাচাই (কম্পাইলার ছাড়া static) — কোনো dangling reference নেই।
- আপলোডে থাকা উন্নতি স্বীকৃত: Translator (NoRoute→OFFLINE, null-safe parse), PerfRecorder (Locale.US, stale touch ignore), KeyEffects.includeDirty (popup dirty rect), IME (secret Roman phonetic per-segment, translate request-id), Secret (glyph run, key warm-up)।
- গোছানো: পুরনো `Claude-keybord--main/` → `docs/archive/`; `docs/INDEX.md`; README map; versionName 3.2 / versionCode 32 (পরে update-checker-এর জন্য দরকার)।

## v3.2 — Key effects (animation), typing-safe
- নতুন: `KeyEffects` (RIPPLE/GLOW/FLASH + popup pop), `EffectsConfig`, `PerfRecorder`; theme-ভিত্তিক effect; Settings toggle।
- Typing path অপরিবর্তিত (শুধু overlay), default Light, allocation-মুক্ত draw, auto-guard + perf লগ।
- Rollback ও baseline: `docs/FX-ROLLBACK.md`, `docs/PERF-BASELINE.md`, ব্যাকআপ `docs/fx-backup/`।

## v3.1 — Translate ফিক্স + উন্নতি
- **Bug:** MyMemory কোটা-শেষ/৫০০-অক্ষর warning HTTP 200-এ আসত, সেটাই অনুবাদ হিসেবে বসত; ব্যর্থ হলে IME-তে কিছুই দেখাত না।
- **Translator.kt পুনর্লিখন:** Google gtx (প্রধান, auto-detect, ভালো বাংলা) → MyMemory (যাচাইসহ) fallback; লম্বা লেখা বাক্য ধরে ≤৪০০ অক্ষরে ভাগ; `|` URL-encode; error body পড়া; LRU cache; স্পষ্ট কারণ (offline/quota/timeout/server)।
- **IME:** direction লেখা দেখে নিজে (বাংলা থাকলে →English); চলতি লেখা → সিলেকশন → কার্সরের আগের লাইন; ডাবল-ট্যাপ guard; "অনুবাদ হচ্ছে…" ও ব্যর্থতার Toast।
- **Test:** `TranslatorTest.kt`।
- ⚠️ gtx একটা অনানুষ্ঠানিক endpoint — বন্ধ হলে MyMemory-তে নিজে নেমে যায়। স্থায়ী সমাধান: Gemini key থাকলে সেটা provider করা / ML Kit on-device।

## v3 — Voice + Secret ফিক্স (এই audit)
### Voice
- **Manifest:** `<queries>`-এ `android.speech.RecognitionService` যোগ — Android 11+ এ ছাড়া recognizer পাওয়া/bind হত না ("মাইক চালু কিন্তু কিছু শোনে না")।
- **`VoiceInput.kt` পুনর্লিখন:** continuous restart, প্রতি session শেষে recognizer destroy (leak → BUSY/CLIENT error), BUSY/CLIENT retry, bn-BD→bn-IN→en-US fallback, stop() watchdog, `onVoiceReady`, `onVoiceEnd` সবসময় একবার।
- **IME:** voice error হলে assistant চিরতরে "paused" থেকে যেত → ঠিক; IME destroy-তেও unpause; Secret mode-এ voice conflict বন্ধ।
- **Assistant service:** mic permission ছাড়া mic-type foreground service শুরু করলে Android 14+ crash → guard + try/catch; silence-timeout extras ও partial-এ awake window বাড়ানো (পুরনো FIXES.md-এ লেখা ছিল কিন্তু কোডে হারিয়ে গিয়েছিল); error backoff; PTT-তে খালি result-এ feedback; recognizer create/start try/catch।
### Secret Message
- **নতুন:** random code বানানো, **📤 Share** (`BKEY1:` invite + fingerprint), **📥 Import** (clipboard থেকে auto-detect) — `SecretInvite.kt`।
- কিবোর্ডে contact বাছা (আগে সবসময় প্রথম contact), বাংলা phonetic রূপান্তর (আগে কাঁচা "ami" encrypt হত), clipboard থেকে decode।
- Secret mode অন্য field-এ আটকে লেখা গিলে ফেলত → reset; commit ব্যর্থ হলে clipboard fallback।
- Auto-detect: একই glyph বারবার ভারী PBKDF2 main thread-এ চালাত → background thread + cache; glyph সরে গেলে state reset।
- **Security:** `secret_prefs`/`assistant_prefs` (Gemini key সহ) cloud backup ও device-transfer থেকে বাদ।
### Docs
- ২২টা .md → `README.md` + `docs/` (সক্রিয়) + `docs/archive/` (merge ইতিহাস); `PLAN.md` হালনাগাদ; ডুপ্লিকেট `SETUP-VOCAB-UPDATE` মুছে ফেলা।
### Tests
- `SecretFlowTest.kt` (invite round-trip, fingerprint, encrypt→glyph→decode সব style)।

> ⚠️ Compile/ফোনে চালিয়ে দেখা যায়নি। Build error এলে হুবহু পাঠান।
