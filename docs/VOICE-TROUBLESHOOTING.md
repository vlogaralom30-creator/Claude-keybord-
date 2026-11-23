# 🎤 Voice typing ও 🤖 Assistant — সমস্যা সমাধান

## Voice typing (কিবোর্ডের 🎤)
- এক চাপে চালু, **আবার চাপলে বন্ধ**। মাঝে চুপ করে থাকলেও নিজে নতুন session শুরু করে। টানা ~৩ বার কিছু না শুনলে মাইক নিজে বন্ধ হয় (ব্যাটারি বাঁচাতে)।
- "🎤 বলুন…" দেখার **পরে** বলুন (আগে বললে ধরে না)।
- বাংলা না পেলে নিজে `bn-BD → bn-IN → en-US` চেষ্টা করে।

| লক্ষণ | কারণ / সমাধান |
|---|---|
| "voice typing পাওয়া যাচ্ছে না" | Google app / **Speech Services by Google** install+update করুন; Settings → Default digital assistant/voice input। (Manifest-এ `RecognitionService` query যোগ করা হয়েছে — Android 11+ এ না থাকলে recognizer খুঁজে পেত না) |
| "ইন্টারনেট লাগবে" | Google recognizer সাধারণত online; Speech Services-এ বাংলা offline ভাষা ডাউনলোড করুন |
| "মাইক এখন অন্য কাজে ব্যস্ত" | অন্য অ্যাপ (call/recorder) মাইক ধরে আছে |
| Assistant-এর মাইক আটকে যেত | ঠিক করা হয়েছে: voice typing শেষ/error/IME বন্ধ — সব পথে assistant আবার চালু হয় |

## Assistant ("Hey Bot")
- Mode: **Continuous** (সবসময় শোনে) বা **Push-to-Talk** (শান্ত, বাটন চাপলে একবার)।
- Continuous-এ Android recognizer প্রতি session-এ ছোট beep দিতে পারে — কোড থেকে বন্ধ করা যায় না। beep চাইলে না হলে PTT ব্যবহার করুন।
- একটানা error হলে retry-র বিরতি ধীরে ধীরে বাড়ে (মাইক বারবার on/off হয় না)।
- "Hey Bot ... (থেমে) open TikTok" — ২.৫ সেকেন্ড পর্যন্ত বিরতি সহ্য করে; কথা বলার সময় awake window নিজে বাড়ে।
- Debug: `adb logcat -s BKAssistant`
