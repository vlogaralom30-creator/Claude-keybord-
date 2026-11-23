# BanglaKeys — Secret Message Feature: Full Plan (v2)

## মূল নীতি (ভুলে যাবেন না)
কোড GitHub-এ public, তাই "glyph দিয়ে লুকানো" নিজে থেকে নিরাপত্তা না — যে কেউ code
পড়ে mapping বুঝে নিতে পারবে। আসল নিরাপত্তা আসে **passphrase**-এর AES-GCM encryption
থেকে (`SecretCipher.kt`), যেটা code-এ কখনো লেখা থাকে না। Glyph style শুধু ছদ্মবেশ।

---

## ✅ সম্পূর্ণ (v1 + v2)

- [x] AES-256-GCM encryption, PBKDF2 দিয়ে passphrase থেকে key (`SecretCipher.kt`)
- [x] ভুল passphrase/নষ্ট data হলে স্পষ্ট ব্যর্থতা, আন্দাজ করা লেখা কখনো না (GCM
      integrity check)
- [x] **৩টা glyph style**: Runic (ᚠᚢᚦ), Dots (⠿⠟⠯, Braille block), Shapes (◆▲●,
      Geometric Shapes block) — disjoint Unicode range, তাই auto-detect নিশ্চিত
      (`GlyphAlphabet.kt`)
- [x] **একাধিক contact, প্রতিটার আলাদা passphrase** (`SecretConfig.kt`)
- [x] **Decrypt করার সময় sender কে তা জানা লাগে না** — সেভ করা সব passphrase +
      সব style চেষ্টা করে (`SecretCipher.decryptWithAny`, `GlyphAlphabet.decodeAny`)
- [x] Manual encode/decode/contact-manager/style-picker screen
      (`SecretComposeActivity.kt`)
- [x] **Auto-detect overlay** — আগের থেকে থাকা Accessibility service reuse করে
      screen scan করে, glyph-text পেলে seved passphrase দিয়ে decode করে floating
      bubble দেখায় (`AssistantAccessibilityService.kt` + config XML আপডেট)
- [x] Auto-detect চালু/বন্ধ করার সুইচ (ডিফল্ট বন্ধ — ব্যাটারি/privacy বিবেচনায়)
- [x] কিবোর্ডে 🔐 key যোগ করার patch লেখা হয়েছে (`Layouts-enum-patch.md`)
- [x] Live secret-compose-bar-এর সম্পূর্ণ implementation spec লেখা হয়েছে
      (`LIVE-COMPOSE-BAR-SPEC.md`) — conceptual code + কেন exact patch না দিয়ে
      spec দেওয়া হলো তার কারণ সহ

## ✗ এখনো বাকি

- [ ] **`LIVE-COMPOSE-BAR-SPEC.md` আসল `BanglaIME.kt`-তে বসানো** — আমি (Claude)
      এই ফাইলের বর্তমান exact অবস্থা নিশ্চিত না (একাধিক আগের patch এর উপর বসেছে),
      তাই blind patch না দিয়ে spec দিয়েছি। **Gemini: এই ধাপে পুরো project file
      (`BanglaIME.kt`) দেখে spec অনুযায়ী বসাও, আর কোথায় কীভাবে বসালে তা একটা ছোট
      summary দাও।**
- [ ] Secret mode-এ পাঠানোর সময় কিবোর্ডের ভেতর থেকেই **কোন contact-এর জন্য তা বাছা**
      (এখন কোড spec-এ প্রথম সেভ করা contact ব্যবহার হয় — একাধিক contact থাকলে এটা
      যথেষ্ট না, UI/picker লাগবে)
- [ ] Auto-detect overlay-র bubble-এ tap করলে copy/dismiss করার মতো interaction
      (এখন শুধু ৮ সেকেন্ড পর নিজে থেকে মিলিয়ে যায়, tap করা যায় না কারণ
      `FLAG_NOT_TOUCHABLE` — ইচ্ছাকৃত, যাতে আসল app-এর touch block না হয়; চাইলে
      ছোট় "copy" বাটনসহ touchable বানানো যায়, কিন্তু তখন নিচের app-এর touch এলাকা
      নিয়ে আলাদা করে ভাবতে হবে)
- [ ] Overlay scanning আরেকটু efficient করা (এখন পুরো node-tree walk করে প্রতি scan-এ
      — বড় স্ক্রিনে সামান্য lag হতে পারে)
- [ ] কোনো automated test নেই — সব manual টেস্ট করে দেখতে হবে

## পরীক্ষা করার ক্রম (সাজেশন)

1. প্রথমে v2-এর ✅ অংশগুলো (contacts, style, manual encode/decode, auto-detect
   toggle+overlay) দুই ফোনে বসিয়ে টেস্ট করুন।
2. এরপর `LIVE-COMPOSE-BAR-SPEC.md` বসিয়ে কিবোর্ডের ভেতরের লাইভ secret mode টেস্ট
   করুন — এটাই সবচেয়ে জটিল অংশ, তাই আলাদা করে ধীরে-সুস্থে টেস্ট করা ভালো।
