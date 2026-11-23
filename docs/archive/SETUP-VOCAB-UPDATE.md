# BanglaKeys Assistant — বাংলা/Banglish শব্দভান্ডার আপডেট (SETUP)

> এই ফাইলটা **Gemini (বা যেকোনো AI)-কে দিন** — সে নিজে আপনার প্রজেক্টে মিলিয়ে বসিয়ে নেবে।
> সাথে দিন: এই zip-এর সব ফাইল + আপনার অ্যাপের বর্তমান source।
> Package: `com.banglakeys.app` · `minSdk >= 24`

## এই আপডেটে নতুন কী

Assistant এখন এই ধরনের কথা বোঝে (Banglish, বাংলা, English — তিনটাই):

| বলবেন | কী হবে | কোন Command |
|---|---|---|
| `tiktok khulo` / `টিকটক খোলো` | app খোলে | `OpenApp` |
| `file e dhuko` / `ফাইল এ ঢুকো` | Files app খোলে (Google/Samsung/Xiaomi/Oppo/Vivo সব চেনে) | `OpenApp` |
| `ber hao` / `বের হও` / `bahire jao` | app থেকে বের হয়ে Home-এ যায় | `Home` (বা `Back`, Config দেখুন) |
| `minimize koro` / `মিনিমাইজ করো` | app ছোট করে Home-এ যায় | `Home` |
| `back hao` / `pechone jao` | Back চাপে | `Back` |
| `youtube search koro tum hi ho` | YouTube খুলে search করে প্রথম ভিডিও চালায় | `YoutubeSearch` |
| `adele-hello video play koro` | "youtube" না বললেও YouTube-এ খুঁজে চালায় | `YoutubeSearch` |
| `tum hi ho gaan chalao` | একই | `YoutubeSearch` |
| `upore uthao` / `উপরে উঠাও` | উপরে swipe | `Swipe(UP)` |
| `niche namao` / `নিচে নামাও` | নিচে swipe | `Swipe(DOWN)` |
| `volume barao` / `awaj komao` / `volume full koro` | আওয়াজ বাড়ায়/কমায় (full = ৫ ধাপ) | `VolumeUp` / `VolumeDown` |
| `mute` / `chup` | আওয়াজ বন্ধ | `Mute` |
| `sob app close koro` / `সব অ্যাপ বন্ধ করো` | Recents খুলে "Clear all / Close all" চাপে | `CloseAllApps` (নতুন) |
| `ei app bondho koro` | চলতি app-এর কার্ড Recents থেকে ঠেলে বন্ধ | `CloseApp` (নতুন) |

পুরোনো সব কমান্ড (like, pause, call, type, enter, screenshot…) আগের মতোই চলে।

## ফাইল তালিকা

**নতুন (১টা):**
- `app/src/main/java/com/banglakeys/app/BanglaVocab.kt` — সব শব্দ এখানে। নতুন শব্দ শেখাতে শুধু এটাই এডিট করতে হয়।

**বদলানো (৫টা — পুরো ফাইল replace করুন):**
- `CommandParser.kt` — এখন শব্দ `BanglaVocab` থেকে নেয়; নতুন কমান্ড `VolumeUp(steps)`, `VolumeDown(steps)`, `Mute`, `Unmute`, `CloseAllApps`, `CloseApp`
- `CommandExecutor.kt` — নতুন কমান্ডের কাজ (Recents খুলে Clear all, volume ধাপ, mute)
- `AppResolver.kt` — Files, Settings, Camera, Gallery, Calculator, Clock, Contacts-এর package তালিকা + Files-এর fallback
- `AssistantConfig.kt` — যোগ হয়েছে `LEAVE_APP_GOES_HOME`, `CLEAR_ALL_LABELS`
- `AssistantAccessibilityService.kt` — যোগ হয়েছে `clickByAnyText()`, `dismissCurrentCard()`

**বদলায়নি:** `AssistantListenerService.kt`, `AssistantActivity.kt`, `AssistantSetup.kt`, `ContactLookup.kt`, `res/` এর ২টা ফাইল।

**AndroidManifest.xml / build.gradle — কিছুই বদলাতে হবে না** (নতুন permission বা dependency লাগে না)।

## merge করার ধাপ

### Case A — আগের Assistant ইতিমধ্যে আপনার অ্যাপে merge করা আছে
1. `BanglaVocab.kt` প্রজেক্টে যোগ করুন।
2. উপরের ৫টা ফাইল পুরোপুরি replace করুন (package লাইন `com.banglakeys.app` ঠিক রাখুন)।
3. পুরো প্রজেক্টে খুঁজুন `Command.VolumeUp` / `Command.VolumeDown`: আগে এগুলো `object` ছিল, এখন `data class` (`steps` সহ)।
   যেখানে `Command.VolumeUp ->` লেখা, সেখানে `is Command.VolumeUp ->` হবে। (zip-এর `CommandExecutor.kt` এ ইতিমধ্যে ঠিক করা।)
4. যেখানে `when (cmd)` দিয়ে `Command` ধরা হয় আর `else` নেই, সেখানে নতুন ৪টা Command (`Mute`, `Unmute`, `CloseAllApps`, `CloseApp`) যোগ করুন। (zip-এর `AssistantListenerService.kt` এ `else` আছে, ঠিক আছে।)
5. Build করুন।

### Case B — প্রথমবার
1. আগে `README-FIRST.md` আর `SETUP-ASSISTANT.md` অনুযায়ী পুরো Assistant merge করুন (Manifest সহ)।
2. তারপর এই zip-এর ফাইলগুলো (এই আপডেটসহ) বসান — zip-এ সব ফাইলই নতুন সংস্করণে আছে।

## Gemini-কে যা মানতে বলুন
- কোনো ফাংশন বা কমান্ড বাদ দেবে না; কোড ছোট করার নামে logic বদলাবে না।
- `BanglaVocab.kt` এর শব্দ-তালিকা কেটে ছোট করবে না (Recognizer নানা বানানে লেখে, তাই অনেক বানান ইচ্ছা করেই আছে)।
- আমার প্রজেক্টের package আলাদা হলে সব ফাইলের `package` ও import বদলাবে।
- যেসব ফাইল বদলাবে তার পুরো কোড দেবে + শেষে চেকলিস্ট: কোন ফাইল নতুন, কোনটা বদলেছে, build-এ কী সমস্যা হতে পারে।
- অনিশ্চিত হলে আন্দাজে না করে আমাকে জিজ্ঞেস করবে।

## টেস্ট চেকলিস্ট (Assistant চালু করে বলুন)

1. `Hey Bot, tiktok khulo` → TikTok খোলে
2. `upore uthao`, `niche namao` → scroll হয় (উল্টো হলে `AssistantConfig.UP_MEANS_FINGER_UP` বদলান)
3. `volume barao`, `volume komao` → আওয়াজ বদলায়
4. `ber hao` / `minimize koro` → Home-এ যায়
5. `youtube search koro tum hi ho` → YouTube-এ ভিডিও চলে
6. `adele-hello video play koro` → YouTube-এ চলে
7. `file e dhuko` → Files app খোলে
8. `sob app close koro` → Recents খুলে সব বন্ধ হয় (ফোনভেদে বাটন আলাদা — নিচে দেখুন)

## নিজে নতুন শব্দ শেখানো (কোড না বুঝলেও পারবেন)

Toast-এ **"বুঝিনি: ..."** এলে, ওই লেখাটাই Recognizer যা শুনেছে। সেই শব্দ `BanglaVocab.kt` এর ঠিক তালিকায় বসান।
উদাহরণ: "volume barao" কে Recognizer "volume bara o" শুনলে → `VOL_UP` এ `"bara o"` যোগ করুন।

| কোন কাজ | কোন তালিকা |
|---|---|
| app খোলা | `OPEN` |
| বের হও / Home | `LEAVE_APP`, `MINIMIZE`, `HOME` |
| Back | `BACK` |
| উপরে / নিচে / বামে / ডানে | `SWIPE_UP` / `SWIPE_DOWN` / `SWIPE_LEFT` / `SWIPE_RIGHT` |
| আওয়াজ | `VOLUME_NOUN`, `VOL_UP`, `VOL_DOWN`, `VOL_BIG`, `MUTE` |
| সব app বন্ধ | `ALL` + `CLOSE` (দুটোই থাকতে হবে) |
| গান/ভিডিও চালানো | `PLAY`, `MEDIA`, `SEARCH`, `YOUTUBE` |
| Like / Pause | `LIKE` / `PAUSE` |

**একদম নতুন ধরনের কাজ** (যেমন "torch jalao") লাগলে ৩ জায়গা: `CommandParser.kt` এ নতুন `object` Command → `parse()` এ `if (hasAny(...)) return ...` → `CommandExecutor.execute()` এ নতুন branch। বিস্তারিত `COMMANDS.md` তে।

## সৎ কথা — যা জেনে রাখা ভালো

- **কোডটা আমি এখানে Android-এ build/চালিয়ে দেখিনি** (Kotlin compiler ছিল না)। তবে parser-এর যুক্তি Python-এ হুবহু নকল করে ৭৭টা বাক্য দিয়ে টেস্ট করেছি — সবগুলো ঠিক কমান্ড ধরেছে। Build-এ error এলে error লেখাটা হুবহু পাঠান।
- **"sob app close"** নির্ভর করে ফোনের Recents screen-এর উপর। Samsung-এ "Close all" বাটন সরাসরি দেখা যায়; Pixel-এ Recents-এর শেষ প্রান্তে "Clear all" থাকে (তাই কোড কয়েকবার swipe করে খোঁজে)। বাটনের লেখা আলাদা হলে `AssistantConfig.CLEAR_ALL_LABELS` এ যোগ করুন। ফোনভেদে না-ও কাজ করতে পারে।
- **Recognizer ভাষা:** Banglish ("khulo") English মোডে প্রায়ই ভুল শোনে। সবচেয়ে ভালো ফল পেতে Assistant অ্যাপে ভাষা **"বাংলা"** রাখুন — তখন recognizer বাংলা অক্ষরে লেখে ("খুলো", "ভলিউম বাড়াও"), আর শব্দভান্ডারে সেগুলোর বাংলা বানান আছে।
- **"ber hao" মানে Home ধরা হয়েছে।** Back চাইলে `AssistantConfig.LEAVE_APP_GOES_HOME = false`।
- "upore uthao" = আঙুল নিচ থেকে উপরে swipe (TikTok-এ পরের ভিডিও)। কোনো app-এ উল্টো লাগলে `UP_MEANS_FINGER_UP` বদলান।
- "sob app close" ছাড়া নির্দিষ্ট app-এর নাম ধরে ("tiktok bondho koro") বন্ধ করা এই সংস্করণে নেই — root ছাড়া এটা সরাসরি সম্ভব নয়; `ei app bondho koro` চলতি app বন্ধ করে।
- ব্যাংকিং/পাসওয়ার্ড screen-এ Accessibility কাজ আটকায়, আর Google Play-তে publish করলে Accessibility policy কড়া — আগের `SETUP-ASSISTANT.md` তে লেখা আছে।
