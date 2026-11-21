# BanglaKeys Assistant — Gemini Upgrade Kit

এই kit দিয়ে Google AI Studio-র **Gemini agent** নিজেই তোমার BanglaKeys অ্যাপের Assistant upgrade
করতে পারবে: নতুন command, mode/role, controls (settings), permission flow — সব কোডে বসিয়ে দেবে।

## আমি তোমার source-এ যা পেয়েছি (analysis সারাংশ)

- অ্যাপটা **AI Studio Build** এ বানানো: Kotlin 2.4, AGP 9.1, Compose BOM, `compileSdk 36`, `minSdk 24`.
- `namespace = "com.example"` কিন্তু বেশিরভাগ কোড `com.banglakeys.app` package-এ। তাই Manifest-এ
  **সবসময় পুরো class নাম** (`com.banglakeys.app.XYZ`) লাগে, `.XYZ` লিখলে ভেঙে যাবে।
- **Merge আগেই হয়ে গেছে** ✅: ৯টা Assistant ফাইল, Manifest (permission + ২টা service + Activity),
  `KeyType.ASSISTANT` (🤖 key), `KeyboardView.onAssistant()`, `BanglaIME.onAssistant()` + mic-conflict
  `setPaused()`, Compose screen-এ Assistant badge ও বাটন, strings।
- APK বানায় **GitHub Actions** (`.github/workflows/build-apk.yml` -> `BanglaKeys.apk`)।
- AI Studio-র preview-তে Accessibility/mic service/gesture **টেস্ট হয় না**; আসল ফোনে APK লাগবে।

তাই এখন কাজ merge নয় — **Assistant-কে শক্তিশালী করা (upgrade)**।

## Kit-এ কী আছে

| ফাইল | কী কাজে লাগবে |
|---|---|
| `01-GEMINI-MASTER-PROMPT.md` | **প্রথমে এটা পুরোটা Gemini-কে paste করো।** প্রজেক্টের নিয়ম, গঠন, কাজের ধাপ |
| `02-ARCHITECTURE-MAP.md` | কোন ফাইল কী করে + "কোথায় কী edit করব" রেসিপি (command, app, permission, mode...) |
| `03-UPGRADE-TASKS.md` | একটা একটা করে paste করার ready task prompt (T0–T10) |
| `04-PERMISSIONS-GUIDE.md` | সব permission-এর তালিকা, কীভাবে যোগ করতে হয়, তোমাকে ফোনে কোথায় চাপ দিতে হবে |
| `05-TEST-AND-DEBUG.md` | ফোনে টেস্ট checklist + bug হলে Gemini-কে কী পাঠাবে |

## ব্যবহার করার ক্রম

1. AI Studio-তে তোমার BanglaKeys project খোলো।
2. `01-GEMINI-MASTER-PROMPT.md` এর পুরো লেখা chat-এ paste করে পাঠাও। Gemini প্রথমে প্রজেক্ট পড়ে
   সারাংশ দেবে — কোনো কোড বদলাবে না।
3. `03-UPGRADE-TASKS.md` থেকে **একটা task** (প্রথমে T0) paste করো। শেষ হলে build দেখো।
4. Task শেষ হলে GitHub-এ push -> Actions থেকে APK নামাও -> ফোনে `05-TEST-AND-DEBUG.md` অনুযায়ী টেস্ট।
5. ঠিক থাকলে পরের task। একসাথে ২-৩টার বেশি task দিও না (ভুল বাড়ে)।
6. দরকার মতো `02` আর `04` ফাইলও Gemini-কে paste করে দিতে পারো ("এই নিয়ম মেনে করো")।

## জরুরি সত্যি কথা (permission নিয়ে)

Gemini **কোডে** permission declare করতে, runtime request flow লিখতে, Setup screen-এ ✅/❌ status
দেখাতে পারবে। কিন্তু **Accessibility ON, "Display over other apps", Restricted settings,
Battery unrestricted, মাইক/কন্টাক্ট/কল allow** — এগুলো Android নিরাপত্তার কারণে **শুধু তুমি ফোনে
নিজে চাপলেই** হয়। কোনো AI বা অ্যাপ নিজে নিজে এগুলো দিতে পারে না। `04-PERMISSIONS-GUIDE.md`-তে
কোথায় কী চাপবে লেখা আছে, আর Gemini-কে বলা আছে Setup screen এমনভাবে বানাতে যাতে প্রতিটা ধাপ এক tap-এ
সঠিক settings পেজ খোলে।

## প্রথমবার কী কী হাতে করতে হবে (ফোনে, একবারই)

1. APK install -> Settings > Keyboard-এ BanglaKeys চালু করে default করো।
2. BanglaKeys Assistant অ্যাপ খুলে ধাপ ১-৩ (Accessibility, permission, overlay)।
3. Android 13+: App info > ⋮ > **Allow restricted settings** (Accessibility ON করার আগে)।
4. Battery > BanglaKeys > **Unrestricted**।
