# BanglaKeys — বাংলা কিবোর্ড + Voice Assistant + Secret Message

Android অ্যাপ (Kotlin, `com.banglakeys.app`): বাংলা/English কিবোর্ড, voice typing, "Hey Bot" voice assistant,
আর AES-GCM দিয়ে এনক্রিপ্ট করা Secret Message।

## ফিচার
| ফিচার | কোথায় | বিস্তারিত |
|---|---|---|
| Phonetic বাংলা কিবোর্ড, suggestions, notes, clipboard | `BanglaIME.kt`, `KeyboardView.kt`, `Phonetic.kt` | — |
| 🎤 Voice typing (continuous) | `VoiceInput.kt` | [VOICE-TROUBLESHOOTING](docs/VOICE-TROUBLESHOOTING.md) |
| 🤖 Voice Assistant ("Hey Bot …") | `AssistantListenerService.kt` + `Command*.kt` | [COMMANDS](docs/COMMANDS.md), [APP-FLOWS](docs/APP-FLOWS.md) |
| 🔐 Secret Message + code পাঠানো | `Secret*.kt`, `GlyphAlphabet.kt` | [SECRET-MESSAGE](docs/SECRET-MESSAGE.md) |
| ✨ Key effects (ripple/glow, typing-safe) | `KeyEffects.kt`, `EffectsConfig.kt`, `PerfRecorder.kt` | [FX-README](docs/FX-README.md) |
| 🌐 Translate (Google → MyMemory fallback) | `Translator.kt` | CHANGELOG v3.1 |
| Gemini fallback (ঐচ্ছিক) | `GeminiCommandInterpreter.kt` | [SETUP-GEMINI](docs/SETUP-GEMINI.md) |

## দ্রুত শুরু
1. Build: GitHub Actions → **Build APK** → Artifacts → `BanglaKeys-apk`। (ফোনে install করতে Unknown sources allow)
2. Settings → Keyboard-এ BanglaKeys চালু করুন।
3. **BanglaKeys Assistant** অ্যাপ খুলে Mic, Accessibility, Overlay দিন ([PERMISSIONS](docs/PERMISSIONS.md))।
4. Battery → BanglaKeys → **Unrestricted** (না দিলে Android background-এ মাইক বন্ধ করে দেয়)।

## ডকুমেন্ট ম্যাপ
- `docs/INDEX.md` — সব ডকের তালিকা
- `docs/ARCHITECTURE.md` — কোন ফাইলের কাজ কী, কোথায় edit করবেন
- `docs/PERMISSIONS.md` — প্রতিটা permission কেন লাগে
- `docs/TEST-AND-DEBUG.md` — ফোনে টেস্ট (`adb logcat -s BKAssistant`)
- `docs/PLAN.md` — ✅/✗ অবস্থা, `CHANGELOG.md` — কী কী ঠিক করা হয়েছে
- `docs/archive/` — পুরনো merge/setup নির্দেশ (সব কোড এখন repo-তে merge করা; শুধু ইতিহাস)

## নিয়ম
- কোনো passphrase/API key code বা GitHub-এ commit করবেন না (`.env` ও `.gitignore` দেখুন)।
- কোড এই পরিবেশে compile/ফোনে চালিয়ে দেখা যায়নি — build error এলে হুবহু error পাঠান।
