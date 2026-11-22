# AI দিয়ে merge করানোর prompt

নিচের লেখাটা AI-কে প্রথমে দিন। তারপর **আপনার পুরো কিবোর্ড অ্যাপের source** (অন্তত
`AndroidManifest.xml`, `Layouts.kt`, `KeyboardView.kt`, `BanglaIME.kt`, `build.gradle`)
আর **এই zip-এর সব ফাইল** দিন।

---

```
আমার একটা Android Kotlin কিবোর্ড অ্যাপ আছে (package: com.banglakeys.app, InputMethodService = BanglaIME)।
আমি এতে একটা "Voice Command Assistant" মডিউল merge করে একটাই 2-in-1 অ্যাপ বানাতে চাই:
কিবোর্ড + voice দিয়ে ফোন কন্ট্রোল। সাথে দুটো জিনিস দিলাম:
(১) আমার অ্যাপের source, (২) assistant মডিউলের zip (নতুন .kt ফাইল + res ফাইল + SETUP-ASSISTANT.md
+ COMMANDS.md + README-FIRST.md)।

কাজ:
1. প্রথমে README-FIRST.md, SETUP-ASSISTANT.md আর SETUP-VOCAB-UPDATE.md পুরোটা পড়ো, তারপর merge করো।
2. নতুন ১০টা .kt ফাইল (BanglaVocab.kt সহ) আর res/xml, res/values ফাইল হুবহু আমার প্রজেক্টে যোগ করো
   (package ঠিক রাখো; আমার package আলাদা হলে সব ফাইলের package লাইন ও import বদলাও)।
3. AndroidManifest.xml এ SETUP-ASSISTANT.md এর ধাপ ১ অনুযায়ী permission, <queries>, AssistantActivity,
   AssistantAccessibilityService, AssistantListenerService যোগ করো। আগের কিছু মুছো না, ডুপ্লিকেট permission
   যোগ করো না।
4. Layouts.kt, KeyboardView.kt, BanglaIME.kt এ ধাপ ২ এর 🤖 বাটন (KeyType.ASSISTANT, onAssistant()) ও
   mic-conflict (setPaused) patch বসাও। আমার আসল row/enum/ফাংশনের নামের সাথে মিলিয়ে নাও, আমার
   আগের ফিচার (voice typing, suggestions ইত্যাদি) যেন না ভাঙে।
5. minSdk, compileSdk, targetSdk দেখো। Assistant-এর জন্য minSdk >= 24 লাগে; কম হলে আমাকে জানাও।
   targetSdk 34+ হলে FOREGROUND_SERVICE_MICROPHONE permission আছে কিনা নিশ্চিত করো।
6. আমার প্রজেক্টে AppCompat/Compose/ViewBinding যাই থাকুক, AssistantActivity প্লেইন Activity দিয়েই
   রাখো (এটা ইচ্ছা করেই XML ছাড়া)।
7. যেসব ফাইল বদলালে শুধু সেই ফাইলগুলোর পুরো নতুন কোড দাও, আর শেষে একটা চেকলিস্ট দাও:
   কোন ফাইল নতুন, কোন ফাইল বদলেছে, আর build-এ কী সমস্যা হতে পারে।
8. কোনো ফাংশন/কমান্ড বাদ দিও না, কোড সংক্ষিপ্ত করার নামে লজিক বদলিও না। অনিশ্চিত হলে আন্দাজে না
   করে আমাকে জিজ্ঞেস করো।
```

---

## শুধু শব্দভান্ডার আপডেট merge করতে (Assistant আগে থেকেই বসানো থাকলে)

```
আমার অ্যাপে BanglaKeys Voice Assistant আগে থেকেই merge করা আছে (package: com.banglakeys.app)।
এখন সাথের zip-এর আপডেট বসাতে চাই: নতুন BanglaVocab.kt, আর বদলানো CommandParser.kt, CommandExecutor.kt,
AppResolver.kt, AssistantConfig.kt, AssistantAccessibilityService.kt।
প্রথমে SETUP-VOCAB-UPDATE.md পুরোটা পড়ো, তারপর "Case A" অনুযায়ী merge করো।
- আমার প্রজেক্টে আগের ফাইলে আমি নিজে যা বদলিয়েছি (যেমন নিজের WAKE_WORDS, CONTACT_GROUPS) সেগুলো নতুন ফাইলে ধরে রাখো।
- Command.VolumeUp/VolumeDown এখন data class — পুরো প্রজেক্টে এর ব্যবহার মিলিয়ে ঠিক করো।
- BanglaVocab.kt এর শব্দ-তালিকা ছোট করবে না, কোনো logic বদলাবে না।
- শেষে চেকলিস্ট দাও: কোন ফাইল নতুন, কোনটা বদলেছে, build-এ কী সমস্যা হতে পারে।
- অনিশ্চিত হলে আন্দাজ না করে আমাকে জিজ্ঞেস করো।
```
