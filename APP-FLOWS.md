# BanglaKeys Assistant — App Flows ও কমান্ড গাইড

এই গাইডে বাংলায় ধাপে ধাপে দেখানো হয়েছে কীভাবে:
1. **`AppFlows.kt`**-এ নতুন একটি অ্যাপ যোগ করবেন (কোনো নতুন লজিক না লিখেই)।
2. নতুন অ্যাপের সঠিক `inputHints`, `searchHints`, `sendHints`, `audioCallHints`, `videoCallHints` বের করবেন।
3. ফোনে নতুন অ্যাপ-ফ্লো টেস্ট ও ডিবাগ করবেন।
4. নতুন একটি everyday কমান্ড (**Recipe R1**) বা নতুন বাংলা/Banglish phrase variant যোগ করবেন।

---

## ১. `AppFlows.kt`-এ নতুন অ্যাপ যোগ (মাত্র ১টা Row)

`app/src/main/java/com/banglakeys/app/AppFlows.kt` ফাইল খুলুন এবং `AppFlows.all` লিস্টের ভেতর একটা নতুন `AppFlow(...)` row কপি করে বসান।

### উদাহরণ (ধরুন **Perplexity** বা **Viber** যোগ করবেন)

```kotlin
AppFlow(
    key = "perplexity",
    names = listOf("perplexity", "পারপ্লেক্সিটি"), // English, বাংলা ও Recognizer যা ভুল শোনে
    packages = listOf("ai.perplexity.app.android"),
    kind = Kind.AI_CHAT, // AI চ্যাট হলে Kind.AI_CHAT, মেসেঞ্জার হলে Kind.MESSENGER
    inputHints = listOf("ask anything", "ask", "message", "প্রশ্ন"),
    searchHints = emptyList(),
    sendHints = listOf("send", "submit", "পাঠাও"),
    audioCallHints = emptyList(),
    videoCallHints = emptyList()
)
```

> **ভালো খবর:** `AppResolver.KNOWN` স্বয়ংক্রিয়ভাবে `AppFlows.all` ব্যবহার করে, তাই এখানে row যোগ করলেই `"open perplexity"`-ও সাথে সাথে কাজ করবে!

---

## ২. অ্যাপের আসল Package Name ও UI Hints কীভাবে বের করবেন?

অ্যাপ ভেদে এবং Android সংস্করণ/ভাষা ভেদে বাটনের `contentDescription` বা `text` আলাদা হতে পারে। সঠিক মান জানার ৩টি উপায় আছে:

### উপায় ক: `BKAssistant` Logcat পড়া (সবচেয়ে সহজ)
1. ফোন USB debugging ON করে কম্পিউটারে চালান:
   ```bash
   adb logcat -s BKAssistant
   ```
2. ভয়েস কমান্ড দিন (যেমন `"hey bot open claude type hi and send"`)।
3. `FlowRunner` ও `AssistantAccessibilityService` প্রতিটা ধাপে কী খুঁজল (`hints=...`) এবং কী পেল লগ করে দেয় — যেমন কোন বাটন খুঁজে পায়নি।

### উপায় খ: Package Name জানা
- Google Play Store-এ অ্যাপটার পেজের লিংকে `?id=...` অংশই package name (যেমন `id=com.anthropic.claude`)।
- অথবা Android Studio **Device Explorer / App Inspection** বা `adb shell dumpsys window | grep mCurrentFocus` দিয়ে দেখতে পারেন।

### উপায় গ: UI বাটনের Hint / Description দেখা
- Android Studio-তে **Tools > Layout Inspector** বা Accessibility Scanner অ্যাপ ব্যবহার করে বাটনের **Content Description** বা **Text** দেখুন।
- `AppFlows.kt`-এ সবসময় **ছোট হাতের অক্ষরে (lowercase)** লিখবেন — `findByHints` ও `clickByHints` case-insensitive (`contains`) ম্যাচ করে।
- ইংরেজি ও বাংলা (যদি ফোনের ভাষা বাংলা হয়) দুটোই `listOf(...)`-এ দিন।

---

## ৩. নতুন অ্যাপ ফোনে টেস্ট করার ধাপ

1. ফোনে **BanglaKeys Assistant** অ্যাপ খুলে:
   - ✅ **Accessibility** চালু আছে কিনা দেখুন (Android 13+ হলে আগে App info > ⋮ > *Allow restricted settings*)।
   - ✅ **Display over other apps** এবং **Microphone / Contacts** অনুমতি দেওয়া আছে কিনা দেখুন।
   - প্রয়োজনে **App-flow timeout** স্লাইডার (2–15 সেকেন্ড) বাড়িয়ে নিন যদি আপনার ফোন বা অ্যাপ খুলতে সময় নেয়।
2. ভয়েস দিয়ে বলুন:
   - `"hey bot open <app> type hello"` (শুধু টাইপ চেক করতে)
   - `"hey bot open <app> type hello and send"` (টাইপ + সেন্ড চেক করতে)
3. কোনো ধাপে ব্যর্থ হলে স্ক্রিনে যে বাংলা Toast আসবে (যেমন `"X-এ send বাটন পাইনি"`), সেই বাটনের টেক্সট/description `AppFlows.kt`-এর নির্দিষ্ট `...Hints` লিস্টে যোগ করে আবার বিল্ড করুন।

---

## ৪. নতুন Everyday Command যোগ করার নিয়ম (Recipe R1)

ধরুন নতুন কমান্ড বানাতে চান (যেমন `"open calculator"` বা নির্দিষ্ট Intent):
1. **`CommandParser.kt`**:
   - `sealed class Command`-এর ভেতর নতুন ক্লাস/অবজেক্ট যোগ করুন: `object OpenCalculator : Command()` (ডাটা লাগলে `data class`)।
   - `parse()` ফাংশনের ভেতর সঠিক অগ্রাধিকার ক্রমে token/phrase চেক যোগ করুন (`CommandParser.has(...)` বা token লিস্ট)।
2. **`CommandExecutor.kt`**:
   - `execute()`-এর `when (cmd)` ব্লকে নতুন branch যোগ করুন (exhaustive `when`, তাই বাদ দিলে কম্পাইল হবে না)।
   - পারমিশন বা অ্যাপ না থাকলে অবশ্যই বাংলা Toast রিটার্ন করুন, কখনো crash করবেন না।
3. **`AssistantListenerService.kt`**:
   - যদি confirmation (হ্যাঁ/না) বা async `FlowRunner` দরকার হয়, `needsConfirmation()` / `handleCommand()` আপডেট করুন।
4. **`COMMANDS.md`**:
   - নতুন কমান্ডের উদাহরণ টেবিলে এক লাইন যোগ করুন।

---

## ৫. নতুন বাংলা / Banglish Phrase Variant যোগ করার নিয়ম

একই কমান্ডের জন্য নতুন উচ্চারণ বা Recognizer-এর ভুল শোনা শব্দ যোগ করতে:
- **Wake word বা Contact ডাকনাম:** `AssistantConfig.kt`-এর `WAKE_WORDS` বা `CONTACT_GROUPS` আপডেট করুন।
- **App নাম / ভুল শোনা নাম:** `AppFlows.kt`-এ ওই অ্যাপের `names` লিস্টে যোগ করুন (যেমন `"cloud"` for `"claude"`, `"ইমু"`/`"emo"` for `"imo"`).
- **ক্রিয়া/সংখ্যা/এক্সটেনশন শব্দ:** `CommandParser.kt`-এর উপরের shared token table গুলোতে (`OPEN_WORDS`, `CALL_WORDS`, `SEND_WORDS`, `NUMBER_WORDS`, `EXT_ALIASES` ইত্যাদি) সরাসরি এক শব্দ বসিয়ে দিন — আলাদা `if/else` লেখার দরকার নেই।
