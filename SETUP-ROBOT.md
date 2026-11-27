# Robot Face — Setup (Phase 1 complete: faces + brain)

এটা **সম্পূর্ণ Phase 1 প্যাকেজ** — আগের `BanglaKeys-robot-face-phase1.zip`-এর ফাইলও
(updated ভার্সনসহ) এখানে আছে। আলাদা করে আগের zip থেকে কিছু মেলানো লাগবে না।

## ফাইল (৮টা, সব `robot` package-এ)
```
FaceId.kt                -> ১২টা face-এর নাম + tag (অপরিবর্তিত)
FaceRenderer.kt          -> vector দিয়ে face আঁকা (অপরিবর্তিত)
BlinkAnimator.kt         -> blink timing (অপরিবর্তিত)
RobotNeeds.kt            -> নতুন: energy/bond/fun state
RobotEvent.kt            -> নতুন: event bus (sensor/typing event-এর জায়গা, এখন fake)
RobotBrain.kt            -> নতুন: mood state machine, idle picker, sleep, rate-limit
RobotFaceView.kt         -> updated: এখন RobotBrain-চালিত, speech bubble + needs bar আঁকে
RobotFaceDemoActivity.kt -> updated: face-এর নিচে ১৯টা fake-event বাটন
```

## করণীয়
আগের zip থেকে `robot` folder পুরোপুরি **replace** করে দিন এই zip দিয়ে। Manifest
entry আগেরটাই থাকবে (`RobotFaceDemoActivity` আগেই register করা, নতুন কিছু লাগবে না)।

## টেস্ট করবেন কীভাবে

Build করে "🤖 Robot Face টেস্ট করুন" খুলুন। এবার:
- উপরে face, নিচে speech bubble (কিছু ঘটলে), তার নিচে Energy/Bond/Fun বার
- স্ক্রিনের face-এ ট্যাপ করলে real Tap event পাঠাবে (brain bond বাড়াবে, SMILE face দেখাবে)
- নিচের বাটনগুলো (Shake, Clap, Battery low, ইত্যাদি) চেপে চেপে দেখুন brain কেমন react করে
- কিছু না চাপলে, idle picker প্রতি ৪-৮ সেকেন্ডে নিজে থেকে mood বদলাবে
- **প্রায় ৩ মিনিট কিছু না চেপে রেখে দিলে** energy কমে ঘুমিয়ে পড়বে (REST face, চোখ
  প্রায় বন্ধ দেখাবে), প্রায় ২ মিনিট পর নিজে থেকে জেগে উঠবে — এটা আমি নিজে Python-এ
  simulate করে নিশ্চিত হয়েছি যে ঘুম-জাগরণ চক্র আটকে যায় না
- "🗑️ সব Memory মুছে ফেলো" চাপলে সব আগের অবস্থায় ফিরে আসবে

## একটা bug যা টেস্ট করেই ধরা পড়েছে (তাই শেয়ার করছি)

প্রথম ভার্সনে energy ঘুমের মধ্যেও শুধু কমতো, কোনোভাবে বাড়তো না — মানে একবার ঘুমিয়ে
পড়লে `Charging` event ছাড়া আর কখনো জাগতো না। Python-এ ১৫ মিনিট simulate করে এটা
ধরা পড়ে, এখন ঠিক করা হয়েছে: ঘুমের মধ্যে energy আস্তে আস্তে বাড়ে (আসল বিশ্রামের মতো),
তাই Charging ছাড়াই স্বাভাবিক ঘুম-জাগরণ চক্র চলে।
