# 🔐 Secret Message (v3)

**নিরাপত্তা কোথা থেকে আসে:** শুধু passphrase থেকে (AES-256-GCM + PBKDF2, `SecretCipher.kt`)।
Glyph style (Runic/Dots/Shapes) শুধু ছদ্মবেশ। Passphrase ছাড়া কেউ পড়তে পারে না; ভুল key দিলে `null` (আন্দাজ করা লেখা কখনো না)।

## ১. Secret code বানানো ও বন্ধুকে পাঠানো (নতুন)
1. অ্যাপ → **🔐 Secret Message** screen।
2. নাম লিখুন → **🎲 নতুন random secret code বানান** → **💾 Contact সেভ করুন**।
3. তালিকায় ওই contact-এর পাশে **📤** চাপুন → Share sheet থেকে WhatsApp/Messenger/SMS যেকোনোটায় পাঠান।
   লেখায় থাকে `BKEY1:…` code + **যাচাই (fingerprint)**।
4. বন্ধু অ্যাপ খুলে কপি করা লেখা পেলে নিজেই import ঘরে বসে যায় → বন্ধুর নাম দিয়ে **📥 Code import**।
5. দুজনের **fingerprint** (যেমন `A1B-2C3`) মিলিয়ে দেখুন — ফোনে বলে।

> ⚠️ **Code আর secret message একই chat-এ পাঠাবেন না।** কেউ chat দেখলে দুটোই পাবে। Code একবার
> সামনাসামনি/ফোনে/আলাদা app-এ দিন; তারপর secret message যেকোনো chat-এ যাবে।

## ২. কিবোর্ড থেকে পাঠানো
- 🔐 চাপুন → উপরে "🔐 → নাম" দেখায় → লিখুন → **Enter** → encrypt হয়ে বসে যায়।
- একাধিক contact থাকলে: Secret mode চালু + লেখা খালি অবস্থায় 🔐 আবার চাপলে পরের contact-এ যায়।
- বাংলা মোডে phonetic রূপান্তর হয়ে তারপর encrypt হয়। অন্য field-এ গেলে mode নিজে বন্ধ হয়।

## ৩. পড়া
- **কপি করে 🔐 চাপুন** → Toast-এ decode করা লেখা (সব সেভ করা code নিজে try করে)।
- অথবা অ্যাপের Secret screen-এ পেস্ট → Decrypt।
- **Auto-detect bubble:** Secret screen-এর সুইচ চালু + Accessibility চালু থাকলে screen-এর glyph লেখা নিজে decode হয়ে ৮ সেকেন্ডের bubble দেখায়।

## না চললে
| লক্ষণ | কারণ / সমাধান |
|---|---|
| 🔐 চাপলে "আগে contact বানান" | কোনো contact নেই → উপরের ধাপ ১ |
| ❌ পড়া গেল না | দুজনের code হুবহু এক নয় — fingerprint মেলান / আবার import |
| Auto-detect bubble আসে না | Secret screen-এ সুইচ চালু? Accessibility চালু? contact আছে? |
| Passphrase backup-এ যায় কিনা | না — `secret_prefs` cloud backup থেকে বাদ (`backup_rules.xml`) |
