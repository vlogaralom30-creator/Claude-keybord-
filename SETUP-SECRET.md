# BanglaKeys — Secret Message Setup (v2, full package)

এই zip **সম্পূর্ণ প্যাকেজ** — v1-এর ৪টা ফাইলও এখানে আছে (updated ভার্সন), আলাদা করে
আগের zip থেকে কিছু কপি করা লাগবে না।

## ফাইল তালিকা

**নতুন/updated কোড (এই zip-এর path-এই বসান, replace করুন যদি আগে থেকে থাকে):**
```
GlyphAlphabet.kt                      -> ৩টা style (Runic/Dots/Shapes) + auto-detect
SecretCipher.kt                       -> + decryptWithAny() (একাধিক passphrase try)
SecretConfig.kt                       -> per-contact passphrase + style + auto-detect toggle
SecretComposeActivity.kt              -> contact manager + style picker + try-all decode UI
AssistantAccessibilityService.kt      -> + screen scan করে auto-decode bubble
app/src/main/res/xml/assistant_accessibility_config.xml  -> content-change event যোগ
```

**শুধু ডকুমেন্টেশন (কোড না, পড়ে/অনুসরণ করে আপনার আসল project-এ বসাতে হবে):**
```
LIVE-COMPOSE-BAR-SPEC.md  -> কিবোর্ডে লাইভ লেখা-encrypt করার bar (BanglaIME.kt-তে)
Layouts-enum-patch.md     -> 🔐 key যোগ করার ছোট patch (Layouts.kt + KeyboardView.kt)
PLAN.md                   -> সব কাজের ✅/✗ checklist (সবসময় হালনাগাদ রাখা হবে)
```

## করণীয় (ক্রম অনুযায়ী)

1. কোড ফাইলগুলো বসান (replace)।
2. `assistant_accessibility_config.xml`-এও replace করুন (নতুন event type লাগবে, না
   হলে auto-detect কাজ করবে না)।
3. `Layouts-enum-patch.md` অনুসরণ করে 🔐 key যোগ করুন (ছোট, নিরাপদ)।
4. `LIVE-COMPOSE-BAR-SPEC.md` অনুসরণ করে `BanglaIME.kt`-এ secret mode-এর টাইপিং
   redirect + send logic বসান (এটা আপনার/Gemini-এর বর্তমান ফাইল দেখে বসাতে হবে,
   কারণ exact patch দেওয়া সম্ভব হয়নি — কেন তা spec-এর শুরুতে লেখা আছে)।
5. Build করে টেস্ট করুন।

## এতে কী নতুন কাজ করবে

- **৩টা glyph style** — Settings-এর Secret Message screen-এ style বেছে নিতে পারবেন।
- **একাধিক contact, আলাদা আলাদা passphrase।**
- **Decrypt করার সময় কোন contact পাঠিয়েছে তা বলা লাগবে না** — সেভ করা সব
  passphrase + সব style নিজে থেকে try করে সঠিকটা বের করে।
- **Auto-detect overlay** — চালু করলে (Secret Message screen-এর সুইচ), screen-এ
  (WhatsApp/Messenger ইত্যাদিতে) glyph-লেখা text দেখলে automatic ছোট bubble-এ
  decode করা লেখা দেখাবে। Accessibility চালু থাকা লাগবে (আগে থেকেই আছে, assistant
  feature-এর জন্য)।
- **🔐 কিবোর্ডের ভেতর থেকেই** (Layouts-enum-patch.md অনুযায়ী বসালে) — আর শুধু
  Settings থেকে যেতে হবে না।

## এখনো যা বাকি

`PLAN.md` দেখুন — সেখানে ✅/✗ দিয়ে স্পষ্ট করে লেখা আছে।
