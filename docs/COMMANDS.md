# কমান্ড তালিকা

প্রথমে **"Hey Bot"** বলুন (বা একসাথে: "Hey Bot open TikTok")। এরপর ২০ সেকেন্ড (বদলানো যায়:
`AssistantConfig.AWAKE_WINDOW_MS`) আর "Hey Bot" বলতে হবে না — প্রতিটা কমান্ডের পর টাইমার নতুন করে শুরু।
বাংলা, English আর Banglish তিনটাই চলে (বাংলা ব্যবহার করলে Assistant অ্যাপে ভাষা "বাংলা" করুন)।

| কী বলবেন | কী হবে |
|---|---|
| `open tiktok` / `tiktok khulo` / `টিকটক খোলো` | app খোলে (যেকোনো ইনস্টল করা app-এর নামেই) |
| `up` / `next` / `উপরে` | আঙুল নিচ থেকে উপরে swipe (পরের ভিডিও) |
| `down` / `previous` / `নিচে` | আঙুল উপর থেকে নিচে swipe (আগের ভিডিও) |
| `left` / `right` | বাঁ / ডানে swipe |
| `like` / `wow` / `nice` / `sundor` / `সুন্দর` | Like (TikTok/Reels/Shorts এ screen-এ double tap, অন্য app এ Like বাটন খোঁজে) |
| `pause` / `play` / `puse` | মাঝখানে tap / Pause-Play বাটন |
| `double tap` | মাঝখানে double tap |
| `tap` | মাঝখানে একবার tap |
| `click comment` / `tap share` | screen-এ ওই লেখা/নামের বাটনে click |
| `type hello bhai` | যে বক্সে cursor আছে সেখানে লেখে |
| `enter` | Enter/Search চাপে (Android 11+) |
| `youtube search tum hi ho song` | YouTube খুলে search করে প্রথম ভিডিও চালায় |
| `youtube search arijit singh most viewed` | search করে screen-এ দেখা ভিডিওগুলোর মধ্যে সবচেয়ে বেশি view-ওয়ালা চালায় |
| `call mim` / `ammi ke call koro` / `call 017...` | contact খুঁজে call করে |
| `back` / `home` / `recents` | Back / Home / Recent apps |
| `notifications` / `quick settings` | নোটিফিকেশন / Quick settings নামায় |
| `screenshot` / `lock` | screenshot / screen lock (Android 9+) |
| `volume up` / `volume down` | আওয়াজ বাড়ায়/কমায় |
| `file e dhuko` / `ফাইল এ ঢুকো` / `gallery khulo` / `camera khulo` | Files / Gallery / Camera / Settings / Calculator / Clock / Contacts খোলে |
| `ber hao` / `bahire jao` / `বের হও` | app থেকে বের হয়ে Home (`LEAVE_APP_GOES_HOME=false` করলে Back) |
| `minimize koro` / `মিনিমাইজ করো` | Home-এ যায় |
| `pechone jao` / `back hao` | Back |
| `adele-hello video play koro` / `tum hi ho gaan chalao` | "youtube" না বললেও YouTube-এ খুঁজে প্রথম ভিডিও চালায় |
| `upore uthao` / `niche namao` / `উপরে উঠাও` / `নিচে নামাও` | উপরে / নিচে swipe |
| `bame` / `dane` | বাঁ / ডানে swipe |
| `volume barao` / `awaj komao` / `ভলিউম বাড়াও` | আওয়াজ ১ ধাপ বাড়ে/কমে |
| `volume full koro` / `awaj onek barao` | ৫ ধাপ বাড়ে |
| `mute` / `chup` / `unmute` | আওয়াজ বন্ধ / চালু |
| `sob app close koro` / `সব অ্যাপ বন্ধ করো` / `close all apps` | Recents খুলে "Clear all/Close all" চাপে |
| `ei app bondho koro` / `app close koro` | চলতি app-এর কার্ড Recents থেকে ঠেলে বন্ধ |
| `sleep` | Hey Bot না বলা পর্যন্ত কমান্ড নেয় না |
| `assistant off` / `quit` | Assistant বন্ধ |

## নতুন শব্দ শেখানো — `BanglaVocab.kt`

সব শব্দ (Banglish + বাংলা + English) এই এক ফাইলে। Toast-এ "বুঝিনি: ..." এলে ওই লেখাটা ঠিক তালিকায় যোগ করুন
(যেমন `OPEN`, `SWIPE_UP`, `VOL_UP`, `PLAY`)। অন্য কোথাও হাত দিতে হবে না। hyphen (`adele-hello`) parser নিজেই space করে।
বিস্তারিত তালিকা-ম্যাপ `SETUP-VOCAB-UPDATE.md` তে।

## সহজ সেটিং বদল (`AssistantConfig.kt`)

- `LEAVE_APP_GOES_HOME = true` — "ber hao" Home-এ নেয়; `false` করলে Back চাপে।
- `CLEAR_ALL_LABELS` — "sob app close" এর সময় Recents-এ যে লেখার বাটন খোঁজা হয়; আপনার ফোনে আলাদা লেখা থাকলে যোগ করুন।

- `UP_MEANS_FINGER_UP = false` — "up" বললে উল্টো দিকে scroll হলে এটা বদলান।
- `WAKE_WORDS` — নিজের পছন্দের নাম দিন (যেমন "hey buddy"; বাংলা উচ্চারণও যোগ করুন)।
- `CONTACT_GROUPS` — "ammi" বললে কোন কোন নামে contact খুঁজবে।
- `SHORT_VIDEO_APPS` — যেসব app এ Like = double tap।

## নতুন কমান্ড বানানোর নিয়ম (৩ জায়গা)

উদাহরণ: **"share"** বললে Share বাটনে click।

1. `CommandParser.kt` এ `sealed class Command` এর ভেতরে: `object Share : Command()`
2. একই ফাইলে `parse()` এ (Like এর আগে) একটা লাইন: `if (has("share", "শেয়ার")) return Command.Share`
   (শব্দ বেশি হলে `BanglaVocab.kt` এ `val SHARE = setOf(...)` বানিয়ে `if (hasAny(V.SHARE)) return Command.Share` লিখুন)
3. `CommandExecutor.kt` এর `execute()` এ একটা branch: 
   `Command.Share -> withSvc { if (it.clickByText("share")) "📤 share" else "Share বাটন পাইনি" }`

(সরাসরি কাজ হলে বা কোন Command এ অনেক কিছু লাগলে ২ নম্বর ধাপে `has(...)` এর ভেতরে আরও শব্দ বসান।)

## কমান্ড কাজ না করলে

- Toast-এ "বুঝিনি: ..." এলে — Recognizer যা শুনেছে সেটা দেখা যায়; ওই শব্দটা `has(...)` এ যোগ করুন।
- "Accessibility চালু করুন" এলে — Settings > Accessibility তে BanglaKeys Assistant ON আছে কিনা দেখুন।
- App খোলে না — "Display over other apps" permission দিন।
