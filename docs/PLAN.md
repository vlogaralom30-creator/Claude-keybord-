# BanglaKeys — Secret Message: অবস্থা (v3)

**মূল নীতি:** আসল নিরাপত্তা passphrase-এর AES-GCM; glyph শুধু ছদ্মবেশ। কোড public — passphrase কখনো commit নয়।

## ✅ সম্পূর্ণ
- [x] AES-256-GCM + PBKDF2, ভুল key-তে null (`SecretCipher.kt`)
- [x] ৩ glyph style, auto-detect (`GlyphAlphabet.kt`)
- [x] একাধিক contact + per-contact passphrase; decrypt-এ sender জানা লাগে না
- [x] Manual encode/decode screen (`SecretComposeActivity.kt`)
- [x] Auto-detect bubble (Accessibility) — background decode + cache
- [x] 🔐 key + কিবোর্ডে লাইভ secret mode (`BanglaIME.kt`), বাংলা phonetic সহ
- [x] কিবোর্ডে contact বাছা (খালি অবস্থায় 🔐 আবার চাপলে পরের contact)
- [x] **Secret code বানানো / 📤 পাঠানো / 📥 import + fingerprint** (`SecretInvite.kt`)
- [x] কপি করা secret কিবোর্ডেই decode (🔐 চাপলে)
- [x] Passphrase cloud backup থেকে বাদ
- [x] Unit test: `SecretFlowTest.kt`

## ✗ বাকি / ভবিষ্যৎ
- [ ] QR code দিয়ে code আদান-প্রদান (সামনাসামনি সবচেয়ে নিরাপদ)
- [ ] Passphrase Android Keystore-এ রাখা (এখন private SharedPreferences, plain)
- [ ] Auto-detect bubble-এ tap করে copy (এখন `FLAG_NOT_TOUCHABLE` ইচ্ছাকৃত)
- [ ] সত্যিকারের wake-word engine (beep ছাড়া always-listening) — [GEMINI-LIMITATIONS](GEMINI-LIMITATIONS.md)
- [ ] ফোনে ম্যানুয়াল টেস্ট — [TEST-AND-DEBUG](TEST-AND-DEBUG.md)
