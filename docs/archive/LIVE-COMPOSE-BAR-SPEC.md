# Live Secret Compose Bar — Implementation Spec (for Gemini/AI to wire in)

**কেন এটা "exact patch" হিসেবে না দিয়ে spec হিসেবে দেওয়া হলো:** `BanglaIME.kt`-তে
ইতিমধ্যে অনেকগুলো আলাদা patch (translate, notepad, voice, assistant) বসানো হয়েছে
ধাপে ধাপে আলাদা conversation-এ — তাই এখন তার exact বর্তমান অবস্থা আমি নিশ্চিত না।
ভুল assumption করে "Find this exact text" দিলে mismatch হয়ে ভুল জায়গায় বসে যেতে
পারে। তাই এখানে **কী করতে হবে আর কেন**, concrete example কোড সহ, স্পষ্ট করে
লেখা হলো — আসল project file খুলে এই অনুযায়ী বসান (বা Gemini-কে এই পুরো project +
এই spec দিন, ও বর্তমান ফাইল দেখে সঠিক জায়গায় বসাতে পারবে)।

## লক্ষ্য

কিবোর্ডে 🔐 চাপলে "Secret Mode" চালু হবে। এরপর ব্যবহারকারী স্বাভাবিকভাবেই টাইপ করবে
(বাংলা phonetic বা English, আগের মতোই) — কিন্তু **একটাও অক্ষর target app-এর আসল
text box-এ যাবে না যতক্ষণ না Enter/Send চাপা হয়**। শুধু keyboard-এর নিজের strip-এ
(যেখানে composing preview দেখা যায়) plain লেখা preview হিসেবে দেখা যাবে। Enter/Send
চাপলে: পুরো লেখাটা encrypt + glyph-encode হয়ে **একবারে** target app-এর box-এ বসে
যাবে, আর Secret Mode বন্ধ হয়ে স্বাভাবিক কিবোর্ডে ফিরে আসবে।

## ধাপ ১ — নতুন state

`BanglaIME` class-এ (অন্য field declaration-গুলোর পাশে) যোগ করুন:
```kotlin
private var secretMode = false
private val secretText = StringBuilder()
```

## ধাপ ২ — Toggle (🔐 key)

`Layouts.kt`-এ `KeyType` enum-এ `SECRET` যোগ করুন (এই zip-এর `Layouts-enum-patch.md`
দেখুন — এটা আলাদা, নিরাপদ, ছোট patch হিসেবে দেওয়া আছে কারণ enum-টা স্থিতিশীল)।
Symbols layout-এর row-এ (যেখানে 📝/Tr/🤖 আছে) একটা `Key("🔐", KeyType.SECRET, 1f)`
যোগ করুন।

`KeyboardView.kt`-এর `Listener` interface-এ:
```kotlin
fun onSecretToggle()
```
আর `fire()`-এ:
```kotlin
KeyType.SECRET -> l.onSecretToggle()
```

`BanglaIME.kt`-এ:
```kotlin
override fun onSecretToggle() {
    // চলমান স্বাভাবিক composing (phonetic buffer) থাকলে আগে শেষ করুন,
    // যেভাবে আপনার আসল commitComposing()/অনুরূপ ফাংশন কাজ করে তা অনুযায়ী কল করুন।
    commitComposing() // <- আপনার আসল ফাংশনের নাম যা-ই হোক, সেটা কল করুন

    secretMode = !secretMode
    secretText.setLength(0)
    kv?.setComposing(if (secretMode) "🔐 (খালি — লিখুন, শেষে Enter)" else "")
    android.widget.Toast.makeText(
        this,
        if (secretMode) "🔐 Secret mode চালু" else "Secret mode বন্ধ",
        android.widget.Toast.LENGTH_SHORT
    ).show()
}
```

## ধাপ ৩ — টাইপিং কে redirect করা (সবচেয়ে গুরুত্বপূর্ণ অংশ)

আপনার `BanglaIME.kt`-এ যে ফাংশনগুলো সরাসরি `currentInputConnection` (ic)-এ টেক্সট
পাঠায় (phonetic buffer থেকে word commit হওয়ার সময়, বা letter/backspace handle করার
সময়), সেগুলোর **শুরুতে** একটা চেক যোগ করুন: `if (secretMode) { ...নিচে লেখা বিকল্প
পথ...; return }`।

মূলনীতি: **secret mode চলাকালীন `ic.commitText()`/`ic.setComposingText()` কোনোটাই
call হবে না।** সব preview শুধু `kv?.setComposing(...)` দিয়ে keyboard-এর নিজের strip-এ
দেখানো হবে।

Conceptually (আপনার phonetic buffer/commit ফাংশনের ভেতরে এই logic বসান):
```kotlin
// যেখানে এখন "committed Bangla/English word" ic-তে commit হয়, সেই জায়গায়:
if (secretMode) {
    secretText.append(committedWord).append(" ")
    kv?.setComposing("🔐 " + secretText.toString())
    return // ic.commitText() call করবেন না
}
// ...না হলে আগের স্বাভাবিক commit logic (অপরিবর্তিত)...
```

Backspace-ও secret mode-এ হলে `secretText`-এর শেষ অক্ষর মুছবে (ic-তে কিছু মুছবে না):
```kotlin
if (secretMode) {
    if (secretText.isNotEmpty()) secretText.deleteCharAt(secretText.length - 1)
    kv?.setComposing("🔐 " + secretText.toString())
    return
}
```

## ধাপ ৪ — Send (Enter key reuse)

আপনার `onEnter()`-এর **শুরুতে** এই চেক বসান:
```kotlin
override fun onEnter() {
    if (secretMode) {
        sendSecretMessage()
        return
    }
    // ...আগের স্বাভাবিক onEnter() logic অপরিবর্তিত...
}

private fun sendSecretMessage() {
    val text = secretText.toString().trim()
    secretMode = false
    secretText.setLength(0)
    kv?.setComposing("")
    if (text.isBlank()) return

    val contacts = SecretConfig.getContacts(this)
    if (contacts.isEmpty()) {
        android.widget.Toast.makeText(this, "আগে Secret Message screen-এ contact/passphrase সেভ করুন", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    // সরলভাবে প্রথম/শেষ ব্যবহৃত contact; চাইলে এখানে কোনটা বাছবে তা জিজ্ঞেস করার UI পরে যোগ করা যাবে
    val passphrase = contacts.values.first()
    val style = SecretConfig.getStyle(this)
    val bytes = SecretCipher.encrypt(text, passphrase)
    val glyph = GlyphAlphabet.encode(bytes, style)
    currentInputConnection?.commitText(glyph, 1)
}
```

> **সীমাবদ্ধতা (PLAN.md-এও আছে):** এই সংস্করণে কোন contact-এর জন্য পাঠানো হচ্ছে তা
> কিবোর্ডের ভেতর থেকে বাছা যায় না — প্রথম সেভ করা contact-এর passphrase ব্যবহার হয়।
> একাধিক contact থাকলে ঠিক কোনটা বাছা হবে তা UI দিয়ে ঠিক করা এখনো বাকি (✗)।

## ধাপ ৫ — Class declaration

`BanglaIME`-কে নতুন কোনো interface implement করতে হবে না (`onSecretToggle()` এমনিতেই
`KeyboardView.Listener`-এর অংশ, যা আগে থেকেই implement করা আছে)।

## টেস্ট চেকলিস্ট

- [ ] 🔐 চাপলে স্বাভাবিক কিবোর্ড দেখা যাচ্ছে (layout বদলায়নি), কিন্তু strip-এ "🔐 (খালি)" দেখাচ্ছে
- [ ] টাইপ করার সময় target app-এর box-এ **কিছুই** বসছে না (শুধু keyboard-এর strip-এ preview)
- [ ] Enter চাপলে target app-এর box-এ শুধু glyph text বসছে, plain text কখনো দেখা যায়নি
- [ ] আবার 🔐 চাপলে normal mode-এ ফিরে আসছে
