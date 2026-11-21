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
| `stop everything` / `sob bondho` / `সব বন্ধ` | চলমান app flow ও confirmation বাতিল করে sleep মোডে যায় |
| `assistant off` / `quit` | Assistant বন্ধ |

## Pack F — ফাইল ও ফোল্ডার তৈরি (SAF — নিরাপদ, কোনো delete/overwrite নেই)

> প্রথমবার Assistant অ্যাপের **Assistant storage folder** বাটনে ট্যাপ করে একটি ফোল্ডার বেছে নিতে হবে।

| কী বলবেন | কী হবে |
|---|---|
| `create folder photos` / `folder banao photos` / `ফোল্ডার বানাও ছবি` | Storage folder-এ নতুন ফোল্ডার বানায় (একই নাম থাকলে `(2)` যোগ করে) |
| `create folder tour inside photos` / `photos er moddhe folder banao tour` | ভেতরের `photos` ফোল্ডারে `tour` বানায় |
| `create file notes` / `create file notes dot txt` / `ফাইল বানাও নোটস` | `notes.txt` বানায় (`.txt`, `.md`, `.csv`, `.json` বোঝে) |
| `create file shopping with text milk eggs bread` | ভেতরে ওই লেখা বসিয়ে ফাইল তৈরি করে |

## Pack G — AI Chat অ্যাপে লেখা ও পাঠানো (`claude`, `chatgpt`, `gemini`)

| কী বলবেন | কী হবে |
|---|---|
| `open claude type hi and send` / `claude khulo hi likho send koro` / `ক্লড খোলো হাই লিখে পাঠাও` | অ্যাপ খুলে লেখা বসায় ও Send চাপে |
| `open chatgpt type hello` | ChatGPT খুলে `hello` লিখে রাখে (send করে না) |
| `open gemini type weather today and send` | Gemini খুলে লিখে ও পাঠায় |
| `type hi and send` | এখন যে অ্যাপ খোলা আছে তার input বক্সে লিখে Send চাপে |

## Pack H — imo / WhatsApp / Messenger / Telegram কল ও মেসেজ

| কী বলবেন | কী হবে |
|---|---|
| `open imo call fatema` / `imo te fatema ke call koro` | imo খুলে যোগাযোগ খুঁজে Audio call করে (confirm সহ) |
| `open imo video call fatema` / `ইমুতে ফাতেমাকে ভিডিও কল` | imo খুলে Video call করে (confirm সহ) |
| `whatsapp call ammi audio` / `call fatema on imo video` | নির্ধারিত অ্যাপে কল করে |
| `whatsapp e fatema ke hi pathao` | চ্যাট খুলে `hi` লিখে Send করে (confirm default ON) |
| `call fatema` (অ্যাপ নাম ছাড়া) | আগের মতোই সাধারণ ফোন কল |

*(Confirmation চাইলে **হ্যাঁ / yes / ঠিক আছে** অথবা **না / no / বাতিল** বলুন — ১০ সেকেন্ড সময় থাকে।)*

## Pack I — দৈনন্দিন অ্যাপ কমান্ড

| কী বলবেন | কী হবে |
|---|---|
| `open camera` / `take photo` / `ছবি তোলো` | ক্যামেরা খোলে (`take photo`-তে shutter বাটন পেলে ক্লিক করে) |
| `set alarm 7 30 am` / `7:30 am e alarm` / `সকাল ৭টা ৩০ এ alarm` | Alarm সেট করে |
| `set timer 5 minutes` / `5 minute timer` | Timer চালু করে |
| `navigate to Dhaka airport` / `route dekhao ...` | Google Maps / ম্যাপ অ্যাপে রুট খোলে |
| `search google for best phones` / `google e khojo ...` | Google সার্চ খোলে |
| `play song tum hi ho` / `gaan chalao ...` | মিউজিক অ্যাপে গান চালায় (`youtube search ...` আগের মতোই আলাদা থাকবে) |
| `save note buy milk` / `note likho ...` | Keyboard Notepad-এ নোট সেভ করে |
| `write email to <name> subject <s> body <b>` | ইমেইল ড্রাফট খোলে (নিজে দেখে Send চাপতে হবে) |
| `write sms to <name> <text>` | SMS কম্পোজার খোলে (কখনো নিজে send করে না) |
| `open wifi settings` / `bluetooth settings` / `display settings` / `sound settings` | নির্দিষ্ট সিস্টেম সেটিংস পেজ খোলে |

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
