# BanglaKeys — Gemini Voice-Command Setup

## এই zip-এ যা আছে

**২টা একদম নতুন ফাইল:**
```
GeminiConfig.kt              -> key ফোনে local-এ সেভ রাখে (hardcode না)
GeminiCommandInterpreter.kt  -> local parser "বুঝিনি" বললে Gemini-কে জিজ্ঞাসা করে
```

**২টা updated ফাইল** (আপনার `BanglaKeys-voice-assistant-FIX.zip`-এর এই দুটোর
জায়গায় বসবে, বাকি ফাইল অপরিবর্তিত):
```
AssistantListenerService.kt  -> আগের cut-off fix + Gemini fallback যোগ
AssistantActivity.kt         -> settings screen-এ Gemini key বসানোর বক্স যোগ
```

## করণীয়

1. ২টা নতুন ফাইল কপি করে বসান।
2. ২টা updated ফাইল দিয়ে পুরনো দুটো replace করুন।
3. `AndroidManifest.xml`-এ `INTERNET` permission আছে কিনা দেখুন (আগের
   translate-feature setup-এ যোগ করা থাকার কথা; না থাকলে
   `<uses-permission android:name="android.permission.INTERNET" />` যোগ করুন)।
4. Build করে ফোনে ইনস্টল করুন।
5. App খুলে "🤖 BanglaKeys Assistant" settings screen-এ গিয়ে নতুন (regenerate করা)
   Gemini API key পেস্ট করে "💾 Gemini key সেভ করুন" চাপুন।
6. Key না দিলেও আগের মতোই সব কমান্ড কাজ করবে — শুধু "বুঝিনি"-গুলো আর বুঝবে না।

## মনে রাখবেন

- যে key-টা আগে চ্যাটে পাঠিয়েছিলেন, সেটা **এখনই Google AI Studio থেকে revoke করে
  নতুন একটা বানান** — পুরনোটা compromised ধরে নিন।
- `GEMINI-LIMITATIONS.md` পড়ুন — কী বুঝবে, আর বুঝলেও কী এখনো *করতে* পারবে না তার
  তালিকা আছে, যাতে কোন কাজের জন্য আরও feature যোগ করা লাগবে তা বুঝতে পারেন।
- আরেকটা AI দিয়ে merge করাতে চাইলে `INTEGRATION-PROMPT.md` ব্যবহার করুন।
