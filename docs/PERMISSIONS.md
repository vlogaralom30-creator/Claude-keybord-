# Permissions Guide

## ক) কে কী করতে পারে

| কাজ | Gemini (কোডে) | তুমি (ফোনে) |
|---|---|---|
| Manifest-এ permission লেখা | ✅ | — |
| Runtime permission dialog খোলা | ✅ (`requestPermissions`) | **Allow চাপা** |
| Accessibility service declare করা | ✅ | **Settings > Accessibility > ON** |
| "Restricted setting" ছাড়ানো (Android 13+, APK সরাসরি install) | ❌ | **App info > ⋮ > Allow restricted settings** |
| Display over other apps | ✅ settings পেজ খোলা | **ON করা** |
| Battery unrestricted | ✅ settings পেজ খোলা | **Unrestricted বেছে নেওয়া** |
| Notification permission (Android 13+) | ✅ dialog | **Allow** |

## খ) এখন Manifest-এ যা আছে

| Permission | ধরন | কেন | অনুরোধ কোথায় |
|---|---|---|---|
| `RECORD_AUDIO` | runtime | শোনা + voice typing | `AssistantActivity` বাটন ২, `VoicePermissionActivity` |
| `READ_CONTACTS` | runtime | "call mim" | `AssistantActivity` বাটন ২ |
| `CALL_PHONE` | runtime | সরাসরি call (না দিলে dialer খোলে) | `AssistantActivity` বাটন ২ |
| `POST_NOTIFICATIONS` | runtime (API 33+) | foreground notification | `AssistantActivity` বাটন ২ |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | normal | mic service | স্বয়ংক্রিয় |
| `SYSTEM_ALERT_WINDOW` | special | background থেকে app/call শুরু | `AssistantActivity` বাটন ৩ |
| `BIND_ACCESSIBILITY_SERVICE` (service-এ) | special | swipe/tap/click | `AssistantActivity` বাটন ১ |
| `<queries>` MAIN/LAUNCHER | normal | ইনস্টল করা app খোঁজা | স্বয়ংক্রিয় |
| `INTERNET`, `VIBRATE` | normal | অনুবাদ / ভাইব্রেশন | স্বয়ংক্রিয় |

## গ) ভবিষ্যতে লাগতে পারে (সতর্কতাসহ)

| Permission | কখন | সতর্কতা |
|---|---|---|
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | সারাক্ষণ শোনা টেকাতে | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` দিয়ে dialog; Play policy-তে সীমিত |
| `CAMERA` | torch লাগলে **লাগে না** (`CameraManager.setTorchMode`); ছবি তুলতে লাগে | দরকার না হলে নিও না |
| `ANSWER_PHONE_CALLS` | "call receive/reject" | API 26+, runtime |
| `SEND_SMS` | SMS পাঠানো | **এড়াও** — ঝুঁকিপূর্ণ + Play-তে কড়া। বরং SMS app খুলে লেখা বসাও |
| `WRITE_SETTINGS` | brightness | special permission; দরকার হলেই |
| `BLUETOOTH_CONNECT` | API 31+ bluetooth নিয়ন্ত্রণ | শুধু দরকারে |
| `QUERY_ALL_PACKAGES` | **নিও না** | `<queries>` যথেষ্ট; Play ব্লক করে |

## ঘ) নতুন permission যোগ করার ৬ ধাপ (Gemini-কে এভাবেই করতে বলো)

1. `AndroidManifest.xml` এ `<uses-permission .../>` (ডুপ্লিকেট নয়)।
2. Runtime হলে `AssistantSetup.runtimePermissions()` তালিকায়; API সীমা থাকলে `if (SDK_INT >= N)`।
   Special হলে `AssistantSetup` এ `canXxx(ctx)` ফাংশন।
3. `AssistantActivity.refresh()` এ ✅/❌ লাইন + (দরকারে) আলাদা বাটন যা সঠিক Settings পেজ খোলে।
4. যে কমান্ড permission চায় সেটার executor-এ আগে check; না থাকলে Toast:
   "⚠️ X permission দিন" — কখনো crash/সাইলেন্ট ফেইল নয়।
5. `COMMANDS.md` / docs এ লিখে রাখা।
6. ফোনে টেস্ট (`05-TEST-AND-DEBUG.md`)।

## ঙ) Settings পেজ খোলার intent (Setup screen-এ ব্যবহারযোগ্য)

| দরকার | Intent |
|---|---|
| Accessibility | `Settings.ACTION_ACCESSIBILITY_SETTINGS` |
| App info (Restricted settings) | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` + `package:<pkg>` |
| Overlay | `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` + `package:<pkg>` |
| Battery | `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `package:<pkg>` (নইলে `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) |
| Notification settings | `Settings.ACTION_APP_NOTIFICATION_SETTINGS` + `EXTRA_APP_PACKAGE` |

## চ) নিয়ম/নীতি

- Accessibility: শুধু নিজের কমান্ডের জন্য ব্যবহার; কিছু বাইরে পাঠানো নয়; ব্যাংকিং/পাসওয়ার্ড অ্যাপে
  (T4 এর blocked list) assistant নিষ্ক্রিয়।
- Play Store-এ দিতে চাইলে Accessibility API declaration ও Foreground-service-type declaration লাগে।
  নিজের/সরাসরি APK বিলিতে লাগে না।
- Microphone: foreground notification সবসময় দেখাতে হবে (এখন আছে) — লুকিয়ে শোনা নয়।
