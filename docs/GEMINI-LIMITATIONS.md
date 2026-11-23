# Gemini Voice-Command Integration — কী বুঝবে, কী বুঝবে না

## কীভাবে কাজ করে
1. Mic শোনে → speech-to-text (আগের মতোই, স্থানীয়, বিনামূল্যে)।
2. প্রথমে **স্থানীয় (local) rule-based parser** (`CommandParser.kt`) চেষ্টা করে।
   এটা instant, বিনামূল্যে, internet লাগে না — up/down/like/pause/back/home/call-এর
   মতো পরিচিত কমান্ডের জন্য এটাই যথেষ্ট।
3. local parser "বুঝিনি" বললেই শুধু **Gemini-কে (ফ্রি টায়ার)** পাঠানো হয় — তাই বেশিরভাগ
   ব্যবহারে quota প্রায় লাগবেই না।
4. Gemini একটা নির্দিষ্ট JSON schema মেনে উত্তর দিতে বাধ্য (`responseSchema`) — তাই
   এলোমেলো লেখা parse করার ঝুঁকি নেই, হয় স্কিমা-মাফিক answer আসে নয়তো ব্যর্থ হয়।

## Gemini যা বুঝতে/করতে পারবে
- একই কমান্ডের অনেক রকম ঘোরানো-প্যাঁচানো বলার ধরন (যেমন "আরেকটু উপরে স্ক্রল করে দেখাও
  তো" বা "এই ভিডিওটা পছন্দ হইসে" — এগুলো local parser-এর exact শব্দের তালিকায় নাও
  থাকতে পারে, Gemini বুঝে নেবে swipe up / like)।
- নতুন slang/বানান ভ্যারিয়েশন (local parser-এ প্রতিটা variation হার্ডকোড করা লাগে,
  Gemini নিজে বুঝে নেয়)।
- ইতিমধ্যে থাকা command-গুলোর (open_app, call, swipe, like, pause, back, home,
  recents, volume ইত্যাদি) যেকোনো ঘোরানো বলার ধরন।

## Gemini যা বুঝলেও করতে পারবে না (এখনো)
`CommandExecutor.kt`/`AssistantAccessibilityService.kt`-এ যে কাজগুলোর কোড এখনো লেখা
হয়নি, Gemini সেগুলো বুঝলেও "unknown" হিসেবেই ফেরত দেবে (আন্দাজ করে ভুল command
চালাবে না) — অর্থাৎ **বোঝা** আর **করতে পারা** এক জিনিস না। যেমন:
- "আমার হয়ে একটা রিপ্লাই লিখে পাঠাও" / মেসেজ কম্পোজ করে পাঠানো
- নির্দিষ্ট কোনো app-এর settings বদলানো
- একাধিক ধাপের জটিল কাজ ("ফেসবুকে ঢুকে প্রথম পোস্টে কমেন্ট করো")
- ছবি/স্ক্রিনের ভেতরের জিনিস দেখে বোঝা (এখনকার accessibility service শুধু
  টেক্সট/বাটন খুঁজতে পারে, ছবি বোঝে না)

**এগুলোর যেকোনোটা দরকার হলে, সেটা আলাদা কাজ — আগে বলুন, আমি সেই নির্দিষ্ট feature-টার
জন্য `CommandExecutor`/`AssistantAccessibilityService`-এ নতুন function বানিয়ে দেব,
তারপর Gemini সেটায় map করতে পারবে।**

## অন্যান্য সীমাবদ্ধতা
- **Internet লাগবে।** না থাকলে Gemini চুপচাপ ব্যর্থ হবে, "বুঝিনি" toast আসবে —
  app crash করবে না।
- **সামান্য দেরি (~১-২ সেকেন্ড)।** Network round-trip-এর জন্য, local command-এর
  মতো instant না।
- **ফ্রি টায়ার quota সীমিত** (`gemini-2.0-flash`: প্রতি মিনিটে ~৩০টা, প্রতিদিন ~১৫০০টা
  request — দিনে একবার-দুবার phone-এ ব্যবহারে যথেষ্ট, কিন্তু অনেকজন একসাথে বা খুব ঘন
  ঘন ব্যবহার করলে সীমায় পৌঁছাতে পারে)।
- **গোপনীয়তা:** যা-ই Gemini-কে পাঠানো হয় (শুধু সেই মুহূর্তের বলা কথাটুকু, পুরো
  audio/screen না) সেটা Google-এর সার্ভারে যায়। কোনো সংবেদনশীল তথ্য (password,
  OTP ইত্যাদি) voice command হিসেবে না বলাই ভালো।
- **Key নিরাপত্তা:** key কখনো code-এ hardcode/commit করবেন না — app-এর নিজের settings
  screen থেকে বসান (`GeminiConfig`), যা শুধু local SharedPreferences-এ থাকে।
