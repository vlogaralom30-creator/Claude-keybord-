# BanglaKeys — Voice Typing Setup (addendum)

এটা আগের `SETUP.md`-এর উপরে বসবে। যদি আগের ৫টা feature (suggestions/notepad/
translate) ইতিমধ্যে বসানো থাকে, শুধু এই অংশটুকু বাড়তি যোগ করলেই voice typing কাজ করবে।

**নতুন ২টা ফাইল** (আগের মতোই, একই path-এ কপি করে বসান):
```
app/src/main/java/com/banglakeys/app/VoiceInput.kt
app/src/main/java/com/banglakeys/app/VoicePermissionActivity.kt
```

---

## 1. `AndroidManifest.xml`

**Find:**
```xml
    <uses-permission android:name="android.permission.INTERNET" />
```
(যদি আগের setup করা না থাকে, শুধু `<uses-permission android:name="android.permission.VIBRATE" />` খুঁজুন)

**Add right after it:**
```xml
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
```

**Find:**
```xml
        <activity
            android:name=".SettingsActivity"
```

**Add right before it:**
```xml
        <activity
            android:name=".VoicePermissionActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />

```

---

## 2. `Layouts.kt`

### 2a. নতুন KeyType

**Find:**
```kotlin
enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, SYMBOLS2, ABC, LANG, SPACER, SNIPPET, NOTEPAD, TRANSLATE }
```
(আগের feature না বসালে শুধু আসল enum line খুঁজুন)

**Replace with:**
```kotlin
enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, SYMBOLS2, ABC, LANG, SPACER, SNIPPET, NOTEPAD, TRANSLATE, MIC }
```

### 2b. Row 4-এ 🎤 বাটন

**Find** (আগের feature বসানো থাকলে):
```kotlin
                Key("📝", KeyType.NOTEPAD, 1f),
                Key("Tr", KeyType.TRANSLATE, 1f),
                Key("⏎", KeyType.ENTER, 1.5f),
```

**Replace with:**
```kotlin
                Key("📝", KeyType.NOTEPAD, 1f),
                Key("Tr", KeyType.TRANSLATE, 1f),
                Key("🎤", KeyType.MIC, 1f),
                Key("⏎", KeyType.ENTER, 1.5f),
```

> Row-টা একটু ভরে যাবে ৯টা key নিয়ে। ছোট ফোনে চাপাচাপি লাগলে `SPACE`-এর weight
> (এখন ২.৫f) আরেকটু কমান, বা 📝/🎤-কে একটা আলাদা row-এ সরান।

(আগের suggestions/notepad feature এখনো না বসালে, শুধু আপনার original row 4-এ
`Key("⏎", KeyType.ENTER, 1.5f)`-এর ঠিক আগে `Key("🎤", KeyType.MIC, 1f)` বসিয়ে দিন।)

---

## 3. `KeyboardView.kt`

### 3a. Listener-এ নতুন callback

**Find:**
```kotlin
        fun onNotepad()
        fun onTranslate()
    }
```
(আগের feature না থাকলে `fun onFeedback()` এর পরের `}` খুঁজুন)

**Replace with:**
```kotlin
        fun onNotepad()
        fun onTranslate()
        fun onVoice()
    }
```

### 3b. MIC key-র tap handling

**Find:**
```kotlin
            KeyType.TRANSLATE -> l.onTranslate()
```

**Add right after it:**
```kotlin
            KeyType.MIC -> l.onVoice()
```

(এই key আলাদাভাবে আঁকার দরকার নেই — ডিফল্ট `else -> drawCentered(...)` branch
এমনিতেই "🎤" label ঠিকমতো দেখাবে।)

---

## 4. `BanglaIME.kt`

### 4a. Class declaration-এ দ্বিতীয় interface implement করা

**Find:**
```kotlin
class BanglaIME : InputMethodService(), KeyboardView.Listener {
```

**Replace with:**
```kotlin
class BanglaIME : InputMethodService(), KeyboardView.Listener, VoiceInput.Listener {
```

### 4b. VoiceInput তৈরি ও destroy

**Find:**
```kotlin
        clipboardMemory = ClipboardMemory(this)
        clipboardMemory.start()
    }

    override fun onDestroy() {
        clipboardMemory.stop()
        super.onDestroy()
    }
```
(আগের feature না বসানো থাকলে, নিজের `onCreate()`/`onDestroy()`-এ একইভাবে বসান।)

**Replace with:**
```kotlin
        clipboardMemory = ClipboardMemory(this)
        clipboardMemory.start()
        voice = VoiceInput(this)
        voice.listener = this
    }

    override fun onDestroy() {
        clipboardMemory.stop()
        voice.destroy()
        super.onDestroy()
    }
```

আর ফিল্ড ডিক্লেয়ারেশনের জায়গায়:

**Find:**
```kotlin
    private lateinit var clipboardMemory: ClipboardMemory
```

**Add right after it:**
```kotlin
    private lateinit var voice: VoiceInput
```

### 4c. `onVoice()` + `VoiceInput.Listener` callback-গুলো

**Find:**
```kotlin
    override fun onTranslate() {
```

**Add right before it:**
```kotlin
    override fun onVoice() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val i = Intent(this, VoicePermissionActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            return
        }
        if (voice.listening) {
            voice.stop()
        } else {
            commitComposing()
            kv?.setComposing("🎤 শুনছি…")
            voice.start(bangla)
        }
    }

    override fun onVoicePartial(text: String) {
        kv?.setComposing("🎤 $text")
    }

    override fun onVoiceResult(text: String) {
        currentInputConnection?.commitText("$text ", 1)
    }

    override fun onVoiceError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onVoiceEnd() {
        kv?.setComposing("")
    }

```

এই কোডে `Intent` ব্যবহার হয়েছে, তাই `BanglaIME.kt`-এর উপরের import list-এ এটা যোগ করুন:

**Find:**
```kotlin
import android.content.SharedPreferences
```

**Add right after it:**
```kotlin
import android.content.Intent
```

(`android.Manifest.permission...` ও `android.content.pm.PackageManager...` উপরের কোডে
পুরো path দিয়ে লেখা আছে বলে আলাদা import লাগবে না।)

---

## এরপর কী হবে

- কিবোর্ডের নিচে 🎤 বাটন চাপলে প্রথমবার মাইক্রোফোন permission চাইবে (একবারই)।
- Permission দেওয়ার পর 🎤 আবার চাপলে শোনা শুরু হবে, strip-এ "🎤 শুনছি…" এবং তারপর
  বলতে থাকা কথা real-time-এ (partial result) দেখাবে।
- কথা বলা শেষ হলে recognizer নিজে থেকেই থেমে যাবে এবং চূড়ান্ত লেখা বসে যাবে; আবার
  🎤 চাপলে মাঝপথে থামানোও যাবে।
- বর্তমান ভাষা (বাংলা/English) অনুযায়ী recognizer bn-BD বা en-US locale ব্যবহার করবে —
  তাই `🌐` দিয়ে ভাষা বদলালে voice typing-ও সেই ভাষায় শুনবে।
- Internet না থাকলে বা কিছু না বুঝলে ছোট একটা Toast message দেখাবে, app crash করবে না।

## মনে রাখার মতো বিষয়

- এটা ফোনের নিজস্ব (সাধারণত Google-এর) speech recognizer ব্যবহার করে — আলাদা কোনো
  voice API key/library লাগেনি, কিন্তু recognizer না থাকা কিছু কাস্টম ROM/ফোনে কাজ
  নাও করতে পারে (`VoiceInput.isAvailable()` সেটা check করে)।
- Google Play-তে publish করলে RECORD_AUDIO permission-এর জন্য Play Console-এ
  "Data safety" ফর্মে microphone ব্যবহারের কারণ জানাতে হতে পারে।
