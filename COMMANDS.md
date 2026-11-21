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
| `sleep` | Hey Bot না বলা পর্যন্ত কমান্ড নেয় না |
| `assistant off` / `quit` | Assistant বন্ধ |

## সহজ সেটিং বদল (`AssistantConfig.kt`)

- `UP_MEANS_FINGER_UP = false` — "up" বললে উল্টো দিকে scroll হলে এটা বদলান।
- `WAKE_WORDS` — নিজের পছন্দের নাম দিন (যেমন "hey buddy"; বাংলা উচ্চারণও যোগ করুন)।
- `CONTACT_GROUPS` — "ammi" বললে কোন কোন নামে contact খুঁজবে।
- `SHORT_VIDEO_APPS` — যেসব app এ Like = double tap।

## নতুন কমান্ড বানানোর নিয়ম (৩ জায়গা)

উদাহরণ: **"share"** বললে Share বাটনে click।

1. `CommandParser.kt` এ `sealed class Command` এর ভেতরে: `object Share : Command()`
2. একই ফাইলে `parse()` এ (Like এর আগে) একটা লাইন: `if (has("share", "শেয়ার")) return Command.Share`
3. `CommandExecutor.kt` এর `execute()` এ একটা branch: 
   `Command.Share -> withSvc { if (it.clickByText("share")) "📤 share" else "Share বাটন পাইনি" }`

(সরাসরি কাজ হলে বা কোন Command এ অনেক কিছু লাগলে ২ নম্বর ধাপে `has(...)` এর ভেতরে আরও শব্দ বসান।)

## কমান্ড কাজ না করলে

- Toast-এ "বুঝিনি: ..." এলে — Recognizer যা শুনেছে সেটা দেখা যায়; ওই শব্দটা `has(...)` এ যোগ করুন।
- "Accessibility চালু করুন" এলে — Settings > Accessibility তে BanglaKeys Assistant ON আছে কিনা দেখুন।
- App খোলে না — "Display over other apps" permission দিন।
