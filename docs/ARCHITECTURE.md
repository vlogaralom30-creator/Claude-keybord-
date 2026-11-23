# Architecture Map + "কোথায় কী edit করব" রেসিপি

(এই ফাইল Gemini-কেও paste করে দেওয়া যাবে: "এই map মেনে edit করো")

## ১. ফাইল-ভিত্তিক দায়িত্ব

| ফাইল | দায়িত্ব | কখন edit করবে |
|---|---|---|
| `AssistantConfig.kt` | ডিফল্ট মান: wake word, awake সময়, up/down দিক, contact alias, short-video app | ডিফল্ট বদলাতে |
| `AssistantSetup.kt` | permission/accessibility check, ভাষা pref, `runtimePermissions()` | নতুন permission যোগ হলে |
| `AssistantAccessibilityService.kt` | ফোন চালানোর নিচু স্তরের কাজ (gesture, click, type, screen পড়া) | নতুন "শারীরিক" কাজ লাগলে |
| `AssistantListenerService.kt` | সারাক্ষণ শোনা, wake word, awake window, notification | শোনা/wake/confirm লজিক বদলাতে |
| `CommandParser.kt` | কথা -> `Command` | নতুন কমান্ড/শব্দ |
| `CommandExecutor.kt` | `Command` -> কাজ | নতুন কমান্ডের আসল কাজ |
| `AppResolver.kt` | app-এর নাম -> package | নতুন app alias |
| `ContactLookup.kt` | নাম -> নম্বর | contact খোঁজার নিয়ম |
| `AssistantActivity.kt` | Setup + Controls screen (Views, XML নেই) | বাটন/সুইচ/স্ট্যাটাস |
| `BanglaIME.kt` / `KeyboardView.kt` / `Layouts.kt` | কিবোর্ডের 🤖 key | key বদলালে মাত্র |
| `BanglaKeysScreen.kt` | Compose হোম screen: badge + বাটন | হোম screen-এ কিছু দেখাতে |
| `AndroidManifest.xml` | permission, service, activity | নতুন permission/component |
| `res/values/strings.xml`, `assistant_strings.xml` | লেখা | নতুন টেক্সট |

## ২. Data flow

```
 মাইক
  │  SpeechRecognizer (AssistantListenerService.listenOnce)
  ▼
 ৫টা সম্ভাব্য transcript
  │  CommandParser.splitWake()  ── wake word বা "awake window" না থাকলে ফেলে দেয়
  ▼
 CommandParser.parse()  ──►  Command (sealed class)
  ▼
 CommandExecutor.execute()
  ├─► AssistantAccessibilityService  (swipe / tap / click / type / back / home)
  ├─► Intent (app খোলা, call, YouTube)
  └─► AudioManager (volume)
  ▼
 Toast feedback  ◄── (পরে T5-এ TTS)
```

## ৩. রেসিপি

### R1. নতুন কমান্ড যোগ (৩ জায়গা)
1. `CommandParser.kt` -> `sealed class Command`: `object Share : Command()` (প্যারামিটার লাগলে `data class`)।
2. একই ফাইলের `parse()` -> সঠিক জায়গায় `if (has("share","শেয়ার")) return Command.Share`.
   **ক্রম গুরুত্বপূর্ণ**: নির্দিষ্ট শব্দ (যেমন "volume up") সাধারণ শব্দের ("up") আগে বসবে।
3. `CommandExecutor.kt` -> `execute()` এর `when`-এ branch: `Command.Share -> withSvc { ... }`.
   `when` exhaustive, তাই branch না দিলে build ভাঙবে।
4. `COMMANDS.md` টেবিলে এক লাইন।

### R2. নতুন app নাম (alias)
`AppResolver.KNOWN` এ একটা `listOf("নাম১","নাম২", "বাংলা নাম") to listOf("package.name")`।
না মিললে label দিয়ে খোঁজে (ইনস্টল করা অ্যাপের নাম), তাই সব app-এর জন্য alias লাগে না।

### R3. নতুন wake word / সময় / দিক / ডাকনাম
`AssistantConfig.kt` (T1 করার পর `AssistantPrefs` থেকে runtime-এ বদলানো যাবে)।

### R4. নতুন permission (৬ ধাপ — বিস্তারিত `04-PERMISSIONS-GUIDE.md`)
Manifest -> `AssistantSetup.runtimePermissions()` (runtime হলে) -> `AssistantActivity` status লাইন + বাটন
-> executor-এ missing হলে Toast -> docs -> ফোনে টেস্ট।

### R5. নতুন control (settings সুইচ/স্লাইডার)
1. `AssistantPrefs` (T1) এ key + getter/setter।
2. `AssistantActivity` এ Switch/SeekBar/Button সারি।
3. যেখানে ব্যবহার হবে (parser/service/executor) সেখানে `AssistantPrefs.xxx(ctx)` পড়ো।
4. `AssistantListenerService` এর ক্ষেত্রে বদলানো মান পরের listen cycle থেকেই কাজ করবে।

### R6. নতুন mode / role (T3)
`AssistantMode` enum + `ModeProfiles.kt`; foreground package -> mode; parser/executor mode দেখে আচরণ বদলায়।

### R7. কিবোর্ডে নতুন key
`Layouts.kt` (`KeyType` + row) -> `KeyboardView.Listener` + `when` -> `BanglaIME` override।
Row-এর weight যোগ ১০ রাখো।

### R8. নতুন Activity/Service
`AndroidManifest.xml` এ **পুরো নাম** দিয়ে entry (`com.banglakeys.app.Xyz`)। ভুলে `.Xyz` লিখলে
runtime-এ ClassNotFound।

## ৪. ফাঁদ (pitfalls)

- `namespace=com.example` -> manifest-এ পুরো নাম, `R` import: `import com.example.R`।
- `Command` এর `when` exhaustive — নতুন `Command` যোগ করলে `execute()` ও listener-এর `when(cmd)` দুটোই দেখো।
- `has("up")` টোকেন-মিল; "pause" এর ভেতরের "up" ধরে না। কিন্তু একই বাক্যে একাধিক শব্দ থাকলে প্রথম মিলা rule জেতে।
- Android 14+ এ mic foreground service অ্যাপ/কিবোর্ড দৃশ্যমান থাকা অবস্থায় শুরু করতে হয়।
- SpeechRecognizer main thread-এ তৈরি/চালাতে হয়।
- YouTube/TikTok UI বদলালে node-ভিত্তিক কাজ (most viewed, click text) ভাঙতে পারে; gesture-ভিত্তিক কাজ টেকে বেশি।


## ৩. v3-তে যোগ/বদলানো ফাইল
| ফাইল | দায়িত্ব |
|---|---|
| `VoiceInput.kt` | কিবোর্ডের voice typing — continuous, retry, language fallback |
| `SecretInvite.kt` | secret code বানানো, share text, import parse, fingerprint |
| `SecretConfig.kt` | contact/passphrase/style/active contact (private prefs, backup-এ বাদ) |
| `SecretComposeActivity.kt` | contact manager, 📤 share, 📥 import, encrypt/decrypt screen |
