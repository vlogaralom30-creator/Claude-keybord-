# AI দিয়ে merge করানোর prompt (Gemini integration)

নিচের লেখাটা AI-কে দিন, সাথে **আপনার পুরো প্রজেক্ট** (অন্তত `AssistantListenerService.kt`
এর আগের ভার্সন, `AssistantActivity.kt`, `AssistantConfig.kt`, `CommandParser.kt`,
`CommandExecutor.kt`, `AppResolver.kt`, `AndroidManifest.xml`) আর **এই zip-এর সব ফাইল**।

---

```
আমার Android Kotlin কিবোর্ড+ভয়েস-অ্যাসিস্ট্যান্ট অ্যাপে (package: com.banglakeys.app)
Gemini API (ফ্রি টায়ার) দিয়ে voice command বোঝার ক্ষমতা যোগ করতে চাই। আমি দিলাম:
(১) আমার প্রজেক্টের source, (২) এই zip: GeminiConfig.kt, GeminiCommandInterpreter.kt
(নতুন ফাইল), আর AssistantListenerService.kt + AssistantActivity.kt (updated ভার্সন,
যা আমার পুরনো ফাইলগুলোর জায়গায় বসবে), আর GEMINI-LIMITATIONS.md।

কাজ:
1. প্রথমে GEMINI-LIMITATIONS.md পুরোটা পড়ো, বুঝে নাও এই integration কী করে আর কী
   করে না।
2. GeminiConfig.kt, GeminiCommandInterpreter.kt আমার প্রজেক্টে হুবহু (package ঠিক
   রেখে, আমার package আলাদা হলে বদলে) যোগ করো।
3. আমার পুরনো AssistantListenerService.kt আর AssistantActivity.kt-কে এই zip-এর
   ভার্সন দিয়ে replace করো — কিন্তু তার আগে আমার পুরনো ফাইলে আমি যদি অন্য কোনো নিজের
   পরিবর্তন করে থাকি (যেমন নিজের কোনো command বা UI যোগ করেছি), সেগুলো না হারিয়ে
   নতুন ভার্সনের সাথে merge করো — পুরোপুরি overwrite করার আগে আমাকে জানিয়ে দাও কী
   কী আমার পুরনো থেকে বাদ যাচ্ছে।
4. AndroidManifest.xml-এ `<uses-permission android:name="android.permission.INTERNET" />`
   আছে কিনা দেখো, না থাকলে যোগ করো (duplicate করো না)।
5. কোথাও কোনো API key hardcode কোরো না — key শুধু GeminiConfig-এর SharedPreferences
   দিয়ে runtime-এ পড়া/লেখা হবে, AssistantActivity-এর settings screen থেকে।
6. কোনো নতুন Gradle dependency লাগার কথা না (`org.json`, `java.net` সবই Android-এর
   built-in) — যদি তোমার যোগ করা কোডে কোনো নতুন dependency লাগে, সেটা বলার আগে
   আমাকে জিজ্ঞেস করো, নিজে থেকে build.gradle বদলিও না।
7. শেষে একটা ছোট checklist দাও: কোন ফাইল নতুন, কোনটা বদলেছে, build-এ কী সমস্যা হতে
   পারে, আর GEMINI-LIMITATIONS.md অনুযায়ী এখনো যা করা যাবে না তার একটা সংক্ষিপ্ত তালিকা।
8. অনিশ্চিত হলে আন্দাজে কিছু করার বদলে আমাকে জিজ্ঞেস করো।
```
