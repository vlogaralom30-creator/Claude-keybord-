# ফোনে টেস্ট + Debug

## কীভাবে আসল টেস্ট করবে

AI Studio preview-তে Accessibility, mic service, gesture, call কাজ করে না। তাই:

1. Gemini কাজ শেষ করলে -> GitHub-এ push (বা AI Studio থেকে GitHub sync)।
2. GitHub > Actions > **Build APK** সফল হলে > Artifacts > `BanglaKeys-apk` নামাও -> unzip -> `BanglaKeys.apk`।
3. ফোনে install (Unknown sources allow)।
4. Settings > Keyboard এ BanglaKeys চালু।
5. BanglaKeys Assistant অ্যাপ খুলে ধাপ ১-৩ + Android 13+ হলে "Allow restricted settings"।
6. Settings > Battery > BanglaKeys > Unrestricted।

Build ফেল করলে: Actions-এর লাল step খুলে error লাইনগুলো কপি করে Gemini-কে দাও (নিচে template)।

## Smoke test (৫ মিনিট)

| # | কী করবে | আশা |
|---|---|---|
| 1 | Assistant অ্যাপে 🧪 টেস্ট চেপে TikTok/Chrome-এ যাও | ৩ সেকেন্ডে পেজ scroll |
| 2 | 🤖 চাপো (কিবোর্ড বা অ্যাপ) | notification "Assistant চালু" |
| 3 | "Hey Bot open Chrome" | Chrome খোলে |
| 4 | "up" "down" | scroll |
| 5 | "Hey Bot open TikTok" -> "up" -> "like" -> "pause" | পরের ভিডিও, ❤️, pause |
| 6 | "youtube search arijit singh most viewed" | YouTube search, ~৪ সেকেন্ডে ভিডিও খোলে |
| 7 | "call ammi" | contact খুঁজে call (বা dialer) |
| 8 | কিবোর্ডের 🎤 | voice typing চলে, শেষে assistant আবার শোনে |
| 9 | "sleep" -> "up" | কিছু হয় না; "Hey Bot" বললে আবার জাগে |
| 10 | "assistant off" | service বন্ধ |

## না চললে — দ্রুত নির্ণয়

| লক্ষণ | সম্ভাব্য কারণ |
|---|---|
| "Accessibility চালু করুন" Toast | Accessibility OFF / Restricted settings ছাড়ানো হয়নি |
| কিছুই শোনে না | mic permission, internet, Battery restricted, অন্য অ্যাপ mic ধরে আছে |
| "বুঝিনি: ..." | যা শুনেছে সেটা দেখো; ওই শব্দ `has(...)`-এ যোগ করো |
| app খোলে না | Overlay permission বা `<queries>` |
| বারবার "টুং" | recognizer restart; T8 (offline) বিবেচনা |
| up/down উল্টো | `UP_MEANS_FINGER_UP` |
| Call হয় না, dialer খোলে | CALL_PHONE দেওয়া নেই |

## Gemini-কে bug পাঠানোর template

```
BUG
Task/ফিচার: <যেমন T2 custom commands>
ফোন: <মডেল, Android সংস্করণ>
আমি যা বললাম: "<ঠিক যা বলেছি>"
Toast/স্ক্রিনে যা দেখালাম: "<লেখা>"
আশা করেছিলাম: <...>
logcat (tag BKAssistant): <থাকলে paste>
Build error (থাকলে): <Actions-এর error লাইন>
নিয়ম: আগের কোনো ফিচার না ভেঙে ন্যূনতম patch দাও, কোন ফাইলের কোন ফাংশন বদলালে তা বলো।
```

## logcat কীভাবে নেবে (ঐচ্ছিক)
ফোন USB debugging ON -> কম্পিউটারে `adb logcat -s BKAssistant`। না পারলে Toast-এর লেখাই যথেষ্ট।
