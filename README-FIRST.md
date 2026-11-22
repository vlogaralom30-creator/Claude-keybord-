# BanglaKeys — Voice Command Assistant (2-in-1 addendum)

এটা আপনার BanglaKeys কিবোর্ড অ্যাপের **ভেতরেই** বসবে — একই অ্যাপ, একই package
(`com.banglakeys.app`)। কিবোর্ড আগের মতোই থাকবে; সাথে যোগ হবে একটা Voice Assistant, যেটা
ফোন না ছুঁয়ে "Hey Bot, open TikTok" / "up" / "like" / "call ammi" ইত্যাদি কমান্ড চালাবে।

> শুধু **Android**। iPhone-এ অন্য অ্যাপকে এভাবে কন্ট্রোল করতে দেয় না।

## এই zip-এ কী কী আছে

**নতুন ১০টা Kotlin ফাইল** (`app/src/main/java/com/banglakeys/app/`) — সোজা কপি করে বসান:

| ফাইল | কাজ |
|---|---|
| `BanglaVocab.kt` | বাংলা/Banglish/English শব্দভান্ডার — নতুন শব্দ শেখাতে শুধু এটা এডিট করুন |
| `AssistantConfig.kt` | Wake word, contact-এর ডাকনাম, up/down-এর দিক — সব সেটিং |
| `AssistantSetup.kt` | Permission / Accessibility চালু আছে কিনা চেক |
| `AssistantAccessibilityService.kt` | ফোন চালানোর ইঞ্জিন: swipe, tap, double tap, back, screen-এ বাটন খোঁজা |
| `AssistantListenerService.kt` | সারাক্ষণ শোনা service, "Hey Bot" ধরে |
| `CommandParser.kt` | বলা কথা -> কমান্ড (বাংলা + English + Banglish) |
| `CommandExecutor.kt` | কমান্ড চালায় (like, pause, YouTube search, call...) |
| `AppResolver.kt` | "tiktok" -> সঠিক app খুঁজে খোলে |
| `ContactLookup.kt` | "ammi" -> contact-এর নম্বর |
| `AssistantActivity.kt` | Setup screen (আলাদা app icon, XML লাগে না) |

**নতুন ২টা resource ফাইল** (`app/src/main/res/`):
```
xml/assistant_accessibility_config.xml
values/assistant_strings.xml
```

**Docs:**
- `SETUP-VOCAB-UPDATE.md` — **শব্দভান্ডার আপডেট**: কী নতুন, কীভাবে merge, কীভাবে টেস্ট (AI-কে এটাই দিন)
- `SETUP-ASSISTANT.md` — **কোথায় কী বদলাতে হবে** (Manifest, Layouts.kt, KeyboardView.kt, BanglaIME.kt)
- `COMMANDS.md` — কী বললে কী হয়, আর নতুন কমান্ড কীভাবে বানাবেন
- `AI-MERGE-PROMPT.md` — AI দিয়ে merge করালে যে prompt দেবেন

## Merge করার ক্রম (৫ ধাপ)

1. `.kt` ফাইলগুলো আর `res/` এর ২টা ফাইল একই path-এ কপি করুন।
2. `SETUP-ASSISTANT.md` অনুযায়ী `AndroidManifest.xml` এ permission + ৩টা entry বসান।
3. (ঐচ্ছিক, কিন্তু মজার) কিবোর্ডে 🤖 বাটন: `Layouts.kt`, `KeyboardView.kt`, `BanglaIME.kt` এ ছোট patch।
4. Build করুন। ফোনে **"BanglaKeys Assistant"** নামে নতুন icon আসবে — সেটা খুলে ৩টা permission/সেটিং দিন।
5. Assistant চালু করে বলুন: **"Hey Bot, open TikTok"**।

কোনো নতুন Gradle dependency লাগে না।

> ⚠️ কোডটা আমি এখানে compile/ফোনে চালিয়ে টেস্ট করতে পারিনি। Build-এ error এলে error লেখাটা
> হুবহু পাঠান, ঠিক করে দেব।
