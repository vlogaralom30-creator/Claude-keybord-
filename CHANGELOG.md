# Changelog

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
