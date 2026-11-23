# BanglaKeys Assistant — Fixes (v2)

আপনার ২টা সমস্যা আর request অনুযায়ী এই zip-এ **৪টা ফাইলের updated ভার্সন** আছে
(আগের `BanglaKeys-voice-assistant.zip`-এর ঠিক সেই ৪টার জায়গায় বসবে, বাকি ৫টা ফাইল
(`AssistantConfig.kt`, `AssistantSetup.kt`, `AssistantAccessibilityService.kt`,
`AssistantActivity.kt`, `ContactLookup.kt`) অপরিবর্তিত, আবার কপি করার দরকার নেই):

```
app/src/main/java/com/banglakeys/app/AssistantListenerService.kt   -> cut-off fix
app/src/main/java/com/banglakeys/app/CommandParser.kt              -> "exit app" কমান্ড
app/src/main/java/com/banglakeys/app/AppResolver.kt                 -> clarify-before-guess
app/src/main/java/com/banglakeys/app/CommandExecutor.kt             -> ^ same
```

---

## সমস্যা ১: "বুঝার আগেই mic বন্ধ হয়ে যায়"

**কারণ:** recognizer ডিফল্ট silence-timeout ব্যবহার করছিল, যা অনেক ফোনে মাত্র
আধা সেকেন্ড চুপ থাকলেই "কথা শেষ" ধরে নিয়ে কেটে দেয় — তাই "Hey Bot... (একটু থেমে)
open TikTok" বললে মাঝপথেই কেটে যেত।

**Fix:** `AssistantListenerService.kt`-এ তিনটা extra যোগ করা হয়েছে যা recognizer-কে
বেশি সময় দেয় (২-২.৫ সেকেন্ড চুপ থাকলেই শুধু "শেষ" ধরবে, আর পুরো session অন্তত ১৫
সেকেন্ড চলবে)। সাথে partial result দিয়ে কথা বলতে থাকা অবস্থায় awake window live
refresh হয়, যাতে মাঝে একটু থামলেও "ঘুমিয়ে" না যায়।

**যেটা পুরোপুরি ঠিক করা যায়নি:** Android-এর নিজস্ব speech recognizer শোনা শুরু/শেষের
সময় একটা ছোট beep বাজায় — এটা recognizer app-এর নিজস্ব আচরণ, কোডের মধ্য থেকে বন্ধ
করা যায় না। সম্পূর্ণ নিঃশব্দ "always listening" চাইলে একটা আলাদা wake-word-engine
library লাগবে (Picovoice-এর মতো), যেটা ইচ্ছাকৃতভাবে এখানে যোগ করা হয়নি (কোনো external
dependency ছাড়া রাখার জন্য, আপনার আগের প্রজেক্টের style মেনে)। তবে এখন session অনেক
বেশিক্ষণ চলবে বলে restart (আর তাই beep) আগের চেয়ে অনেক কম ঘন ঘন হবে — চেষ্টা করে দেখুন,
তাও বেশি মনে হলে জানাবেন, একটা push-to-talk মোডও বানিয়ে দিতে পারি (সবসময় শোনার বদলে
শুধু 🤖 চাপলে একবার শোনা)।

## সমস্যা ২: নতুন command + আন্দাজ না করে জিজ্ঞাসা করা

- **"exit app" / "close app" / "ber hon" / "অ্যাপ বন্ধ করো"** বললে এখন home স্ক্রিনে
  চলে যাবে (Android-এ অন্য app সরাসরি force-close করা যায় না, তাই home-এ ফেরাটাই
  বাস্তবসম্মত "বের হওয়া")।
- **"reel" / "shorts" / "video"** শুধু এটুকু বললে এখন আর আন্দাজ করে একটা app খুলবে না —
  উদাহরণ: *"\"reel\" বললে কোনটা বোঝাচ্ছেন — TikTok / Instagram Reels / Facebook
  Reels? নির্দিষ্ট নাম বলুন।"* এই রকম toast দেখাবে। স্পষ্ট নাম বললে ("open tiktok",
  "open instagram") আগের মতোই সরাসরি খুলবে।
- আরও ambiguous শব্দ যোগ করতে চাইলে `AppResolver.kt`-এর `AMBIGUOUS` ম্যাপে একই
  প্যাটার্নে একটা লাইন বসালেই হবে।

---

## সমস্যা ৩: কিবোর্ডের নিচের row-এ বেশি আইকন, 🎤 হাতের নাগালে না

স্ক্রিনশট দেখে বোঝা গেল row 4-এ এখন ১০টা key (`?123`, 🌐, `,`, space, `.`, 📝, `Tr`,
🎤, 🤖, `⏎`) — একদম ঠাসাঠাসি, আর 🎤 মাঝখানে না থেকে শেষের দিকে। Fix: মূল row-এ শুধু
**৭টা** key রাখুন (space-এর ঠিক পাশে 🎤), আর 📝/Tr/🤖 — যেগুলো কম ব্যবহার হয় — `?123`
layout-এর (symbols) উপরের row-এ সরান।

এটা `Layouts.kt`-এ (আপনার মূল কিবোর্ড অ্যাপের ফাইল, এখানে দেওয়া হয়নি) নিজে বসান:

**Find** (আপনার row 4, exact লেখা একটু ভিন্ন হতে পারে — 🎤/📝/Tr/🤖 যেখানে আছে সেই লাইনগুলো):
```kotlin
                Key("📝", KeyType.NOTEPAD, 1f),
                Key("Tr", KeyType.TRANSLATE, 1f),
                Key("🎤", KeyType.MIC, 1f),
                Key("🤖", KeyType.ASSISTANT, 1f),
                Key("⏎", KeyType.ENTER, 1.5f),
```

**Replace with:**
```kotlin
                Key("🎤", KeyType.MIC, 1.2f),
                Key("⏎", KeyType.ENTER, 1.5f),
```

(এখন `,` আর `space`-এর মাঝে 🎤 বসবে না — 🎤 থাকবে `.` আর `⏎`-এর মাঝে, space-এর ঠিক
পরের key, তাই থাম্ব স্বাভাবিকভাবেই সহজে পৌঁছাবে।)

তারপর `Layouts.kt`-এর **symbols layout**-এর প্রথম row-এ (যেখানে `?123` চাপলে যাওয়া
হয়) এই ৩টা key যোগ করুন, একদম শুরুতে বা শেষে:
```kotlin
                Key("📝", KeyType.NOTEPAD, 1f),
                Key("Tr", KeyType.TRANSLATE, 1f),
                Key("🤖", KeyType.ASSISTANT, 1f),
```

এতে letter-row হালকা থাকবে, আর notepad/translate/assistant — যেগুলো মাঝে মাঝে লাগে —
এক চাপ দূরে (`?123` → ওই row) থাকবে, মুছে যায়নি।

---

## পরের ধাপ

1. এই ৪টা ফাইল আগের assistant zip-এর একই path-এ বসিয়ে replace করুন।
2. `Layouts.kt`-এ উপরের row-fix বসান।
3. Build করে ফোনে টেস্ট করুন। Build error এলে error লেখাটা হুবহু পাঠান।
