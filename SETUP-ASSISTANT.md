# BanglaKeys — Voice Command Assistant Setup (addendum)

আগের `SETUP.md` / `SETUP-VOICE.md` এর উপরে বসবে। voice typing (`VoiceInput.kt`) যেমন আছে তেমনই
থাকবে। এটার জন্য বাড়তি ফাইলগুলো আগেই কপি করুন (`README-FIRST.md` দেখুন)।

**ধরে নেওয়া হয়েছে:** `minSdk` ২৪ বা তার বেশি। কম হলে `AssistantAccessibilityService` এর
gesture API আর Foreground service কাজ করবে না — `minSdk` বাড়ান।

---

## 1. `AndroidManifest.xml`

### 1a. নতুন permission

**Find:**
```xml
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
```

**Add right after it:**
```xml
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <!-- Android 11+: ইনস্টল করা app-এর নাম দিয়ে খুঁজতে ("open <যেকোনো app>") -->
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>
```

### 1b. Service ও Activity

**Find:**
```xml
        <activity
            android:name=".VoicePermissionActivity"
```

**Add right before it:**
```xml
        <activity
            android:name=".AssistantActivity"
            android:exported="true"
            android:label="BanglaKeys Assistant"
            android:theme="@android:style/Theme.DeviceDefault.Light">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".AssistantAccessibilityService"
            android:exported="false"
            android:label="BanglaKeys Assistant"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/assistant_accessibility_config" />
        </service>

        <service
            android:name=".AssistantListenerService"
            android:exported="false"
            android:foregroundServiceType="microphone" />

```

> `<queries>` ট্যাগটা `<application>` এর **বাইরে**, `<manifest>` এর ভেতরে থাকতে হবে —
> permission গুলোর পাশে বসালে ঠিকই থাকবে।
> আপনার মূল launcher/Settings icon আলাদা থাকবে; এতে দ্বিতীয় আরেকটা icon যোগ হবে।

---

## 2. কিবোর্ডে 🤖 বাটন (ঐচ্ছিক)

এই ধাপ না করলেও চলবে — তখন Assistant চালু/বন্ধ করবেন "BanglaKeys Assistant" অ্যাপ থেকে।

### 2a. `Layouts.kt` — নতুন KeyType

**Find:**
```kotlin
enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, SYMBOLS2, ABC, LANG, SPACER, SNIPPET, NOTEPAD, TRANSLATE, MIC }
```
(আপনার enum-এ যা যা আছে সেগুলো রেখে শেষে `ASSISTANT` যোগ করলেই হবে)

**Replace with:**
```kotlin
enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, SYMBOLS2, ABC, LANG, SPACER, SNIPPET, NOTEPAD, TRANSLATE, MIC, ASSISTANT }
```

### 2b. `Layouts.kt` — Row 4-এ বাটন

**Find:**
```kotlin
                Key("🎤", KeyType.MIC, 1f),
                Key("⏎", KeyType.ENTER, 1.5f),
```

**Replace with:**
```kotlin
                Key("🎤", KeyType.MIC, 1f),
                Key("🤖", KeyType.ASSISTANT, 1f),
                Key("⏎", KeyType.ENTER, 1.5f),
```

> Row ১০টা key নিয়ে ভরে যাবে। চাপাচাপি লাগলে `SPACE` এর weight কমান, বা 📝 / Tr বাটন
> সরিয়ে দিন, বা 🤖 কে symbols layout-এর row-তে বসান।

### 2c. `KeyboardView.kt`

**Find:**
```kotlin
        fun onVoice()
```

**Add right after it:**
```kotlin
        fun onAssistant()
```

**Find:**
```kotlin
            KeyType.MIC -> l.onVoice()
```

**Add right after it:**
```kotlin
            KeyType.ASSISTANT -> l.onAssistant()
```

### 2d. `BanglaIME.kt` — `onAssistant()`

**Find:**
```kotlin
    override fun onVoice() {
```

**Add right before it:**
```kotlin
    override fun onAssistant() {
        if (AssistantListenerService.running) {
            AssistantListenerService.stop(this)
            android.widget.Toast.makeText(this, "🤖 Assistant বন্ধ", android.widget.Toast.LENGTH_SHORT).show()
        } else if (!AssistantSetup.isReady(this)) {
            val i = Intent(this, AssistantActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } else {
            AssistantListenerService.start(this)
            android.widget.Toast.makeText(
                this, "🤖 Assistant চালু — \"Hey Bot\" বলুন", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

```
(`Intent` import আগের voice setup এ যোগ করা থাকার কথা: `import android.content.Intent`)

### 2e. Mic conflict ঠিক করা (খুব দরকারি)

কিবোর্ডের নিজের 🎤 voice typing আর Assistant একসাথে mic ব্যবহার করতে পারে না। তাই
voice typing চলার সময় Assistant-কে থামিয়ে রাখতে হবে।

`BanglaIME.kt` এ, `onVoice()` ফাংশনের ভেতরে:

**Find:**
```kotlin
            commitComposing()
            kv?.setComposing("🎤 শুনছি…")
```

**Replace with:**
```kotlin
            commitComposing()
            AssistantListenerService.setPaused(true)
            kv?.setComposing("🎤 শুনছি…")
```

**Find:**
```kotlin
    override fun onVoiceEnd() {
        kv?.setComposing("")
```

**Replace with:**
```kotlin
    override fun onVoiceEnd() {
        AssistantListenerService.setPaused(false)
        kv?.setComposing("")
```

---

## 3. প্রথমবার চালানো

1. Build করে install করুন। ফোনে **BanglaKeys Assistant** icon আসবে, খুলুন।
2. **১) Accessibility চালু করুন** -> তালিকা থেকে "BanglaKeys Assistant" -> ON।
   - **Android 13+ এ APK সরাসরি install করলে** ON করার বাটন ধূসর থাকে ("Restricted setting")।
     তখন: Assistant অ্যাপের "App info খুলুন" বাটন -> উপরে ডানে ⋮ -> **Allow restricted
     settings** -> আবার Accessibility-তে গিয়ে ON।
3. **২) permission** — Mic, Contacts, Call, Notification সব Allow।
4. **৩) Display over other apps** — ON (background থেকে app খোলা/call করার জন্য লাগে)।
5. 🧪 টেস্ট বাটন চেপে ৩ সেকেন্ডের ভেতর TikTok বা Chrome খুলুন — scroll হলে gesture কাজ করছে।
6. **▶️ Assistant চালু করুন** -> বলুন: **"Hey Bot, open TikTok"** -> তারপর "up", "up", "like", "pause"।
7. ফোনের Battery settings এ এই অ্যাপের জন্য **Unrestricted / No restrictions** দিন, নইলে
   (বিশেষ করে Xiaomi/Oppo/Vivo/Samsung এ) কিছুক্ষণ পর mic বন্ধ হয়ে যেতে পারে।

---

## মনে রাখার মতো বিষয় (সৎ কথা)

- **Speech recognizer** ফোনের Google recognizer — সাধারণত **internet লাগে**, আর বারবার চালু
  হওয়ায় কিছু ফোনে হালকা "টুং" শব্দ হয় বা ভিডিওর আওয়াজ একটু কমে যায়। পুরোপুরি offline ও
  নিঃশব্দ চাইলে পরে **Vosk** দিয়ে `AssistantListenerService.listenOnce()` বদলাতে হবে।
- **Battery**: সারাক্ষণ শোনা মানে ব্যাটারি বেশি খরচ। ব্যবহার না করলে নোটিফিকেশন থেকে বন্ধ করুন।
- **Bangla recognition** মাঝেমধ্যে ভুল করে; কমান্ড ছোট রাখুন। Contact-এর নাম না মিললে
  `AssistantConfig.CONTACT_GROUPS` এ ডাকনাম যোগ করুন।
- **YouTube "most viewed"**: শুধু ফোনের screen-এ যে result গুলো দেখা যাচ্ছে তার ভেতর থেকে
  বাছে। YouTube UI বদলালে কাজ ভাঙতে পারে।
- কিছু app (ব্যাংকিং, পাসওয়ার্ড screen, DRM ভিডিও) নিরাপত্তার জন্য Accessibility gesture/screen
  পড়া আটকায় — সেখানে কাজ করবে না।
- Android 14+ এ background থেকে mic service চালু করার বিধিনিষেধ আছে। তাই Assistant চালু করুন
  অ্যাপ বা কিবোর্ড দেখা যাওয়ার সময়, তারপর অন্য app-এ যান।
- **Google Play-তে publish** করলে Accessibility API ব্যবহারের কড়া policy/declaration আছে
  (যা সাধারণ voice-control অ্যাপের জন্যও কঠিন)। নিজের বা সরাসরি APK বিলির জন্য সমস্যা নেই।
- Accessibility স্ক্রিনের লেখা পড়তে পারে — কোডটা কিছু বাইরে পাঠায় না, কিন্তু এই permission
  শুধু নিজের/বিশ্বস্ত অ্যাপেই দিন।
