# Upgrade Tasks (একটা একটা করে Gemini-কে paste করো)

**আগে `01-GEMINI-MASTER-PROMPT.md` paste করা থাকতে হবে।** তারপর নিচের task-গুলো একটা একটা করে।
প্রতিটা task-এর নিচের বাক্সের (text block) ভেতরের লেখাটুকু কপি করবে।

## প্রস্তাবিত ক্রম

`T0` অডিট -> `T1` Controls -> `T4` Safety -> `T3` Modes -> `T2` Custom commands -> `T6` Command packs
-> `T5` Feedback -> `T9` Onboarding -> `T7` Floating bubble -> `T8` Offline engine -> `T10` Docs।

একসাথে সর্বোচ্চ ২টা task। প্রতিটার পর build + ফোনে টেস্ট।

| Task | কী পাবে | ঝুঁকি |
|---|---|---|
| T0 | Health audit + logging + compile-risk ঠিক | কম |
| T1 | Runtime Controls (settings সুইচ), wake word/ডাকনাম এডিট | কম |
| T2 | কোড ছাড়া নিজের কমান্ড/macro বানানো | মাঝারি |
| T3 | Modes/Roles (app অনুযায়ী আচরণ), "up 5 times", auto-scroll | মাঝারি |
| T4 | Confirm ("call mim? হ্যাঁ"), blocked apps, panic stop | কম |
| T5 | বলে উত্তর (TTS), ভাইব্রেশন, notification-এ শেষ কমান্ড | কম |
| T6 | নতুন কমান্ড প্যাক (share, follow, skip ad, torch, media...) | মাঝারি |
| T7 | Floating mic bubble | উচ্চ (ASK-FIRST) |
| T8 | Speech engine abstraction + offline/wake-word বিকল্প | উচ্চ (ASK-FIRST) |
| T9 | Setup wizard + self-test + battery/notification ধাপ | কম |
| T10 | Docs/CHANGELOG সিঙ্ক | কম |

---

## T0 — Health audit + logging

````text
TASK T0 — Health audit, logging, compile-risk fixes. (Not ASK-FIRST.)

Goal: make the existing Assistant observable and safe before adding features.

Do:
1. Read every Assistant*.kt, Command*.kt, AppResolver.kt, ContactLookup.kt, the manifest and the IME hooks.
2. Add `Log.d("BKAssistant", ...)` at: AssistantListenerService.handleResults (candidates list, woke flag,
   awake flag), after CommandParser.parse (Command or null), CommandExecutor.execute (result string),
   AccessibilityService connect/disconnect. Create a tiny `AssistantLog.kt` (object with d(msg)) and use it
   everywhere so logging can be switched off from one place (const DEBUG = true).
3. Audit and FIX only real problems you find (null-safety, thread/Handler misuse, leaks of the
   SpeechRecognizer, double startForeground, restart loops without back-off, AccessibilityNodeInfo
   recycling on API < 33, missing permission checks before ACTION_CALL, manifest names).
4. Make AssistantActivity.refresh() also show: "Accessibility service connected" (AssistantAccessibilityService.instance != null)
   in addition to the "enabled in settings" check, because those can differ.
5. Do NOT add features. Do NOT change behaviour that works.

Report: list of problems found (file + line + why), what you fixed, what you left alone and why.
````

---

## T1 — Controls (runtime settings)

````text
TASK T1 — Runtime Controls via SharedPreferences + settings UI. (Not ASK-FIRST.)

Goal: everything now hard-coded in AssistantConfig becomes user-editable at runtime, with AssistantConfig
kept as the DEFAULTS.

Create `AssistantPrefs.kt` (object, uses getSharedPreferences(AssistantConfig.PREFS)) with typed
getters/setters and defaults taken from AssistantConfig:
- requireWakeWord: Boolean (default true). If false, every utterance is treated as a command
  (warn in UI: more false triggers, more battery).
- awakeWindowSec: Int 5..120 (default 20)
- upMeansFingerUp: Boolean (default AssistantConfig.UP_MEANS_FINGER_UP)
- wakeWords: List<String> (stored as one string separated by "|"; default AssistantConfig.WAKE_WORDS; always lowercase/trimmed; never empty)
- contactGroups: List<List<String>> (stored as lines "a,b,c"; default AssistantConfig.CONTACT_GROUPS)
- listenLanguageBangla: Boolean (reuse the existing AssistantSetup.isBangla/setBangla — delegate to them, don't duplicate)
- showToastFeedback: Boolean (default true)
- scrollDurationMs: Int 120..600 (default 260) and swipeLengthPct: Int 30..70 (default 44)

Wire them in (read the pref at USE time, not at class-load time):
- CommandParser.splitWake uses AssistantPrefs.wakeWords(ctx) -> change signature to take a Context
  (or pass the list in) and update all callers; if requireWakeWord is false, splitWake returns (true, raw).
- AssistantListenerService uses awakeWindowSec and showToastFeedback.
- CommandExecutor.swipe uses upMeansFingerUp; AssistantAccessibilityService.swipe(dir) uses
  scrollDurationMs and swipeLengthPct.
- ContactLookup uses contactGroups.

UI: extend AssistantActivity (plain Views, no XML) with a "Controls" section: Switch rows, SeekBar rows with
a live value label, an EditText for wake words (comma separated) with a Save button, a multi-line EditText for
contact groups (one group per line: ammi,amma,mom,আম্মি), and a "ডিফল্টে ফিরুন" reset button.
Validate input; never allow an empty wake-word list.

Acceptance: change a control, restart nothing except (if needed) stop/start of the service, and the
behaviour changes. App still builds. Keyboard untouched.
````

---

## T2 — Custom commands (কোড ছাড়া নিজের কমান্ড)

````text
TASK T2 — User-defined voice commands and macros. (Not ASK-FIRST.)

Goal: the user can create commands from inside the app without editing code, e.g.
  phrase "good morning"  ->  OPEN_APP:youtube, WAIT:3000, YOUTUBE_SEARCH:morning songs

Data model (new file CustomCommands.kt):
- enum CustomActionType { OPEN_APP, CALL, CLICK_TEXT, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT,
  TAP_CENTER, DOUBLE_TAP, BACK, HOME, TYPE_TEXT, WAIT, YOUTUBE_SEARCH, VOLUME_UP, VOLUME_DOWN }
- data class CustomAction(type, arg: String = "")      // WAIT arg = milliseconds
- data class CustomCommand(id: String, phrases: List<String>, actions: List<CustomAction>, enabled: Boolean)
- Store as JSON in SharedPreferences key "custom_commands" using org.json (built into Android; no new
  dependency). Provide load()/save()/add()/update()/delete() and a safe parser that ignores corrupt entries.

Matching: in CommandParser.parse, check custom commands FIRST (before built-ins): a custom phrase matches when
the normalized transcript equals the phrase or contains it on word boundaries. Return
Command.Macro(name, actions).

Execution: Command.Macro in CommandExecutor runs actions sequentially on the main Handler, honoring WAIT,
reusing the existing executor paths (OpenApp, Call, Swipe, ClickText, ...). Limits: max 20 actions,
max total ~60 s, cancelled by the "stop everything" command (see T4) or if the service stops. A CALL or
TYPE_TEXT inside a macro must go through the confirmation step once T4 exists.

UI (new CustomCommandsActivity, plain Views, registered in the manifest with the FULLY QUALIFIED name,
exported=false): list of commands with enable switch, tap to edit, long-press to delete, "+ নতুন কমান্ড"
button. Editor = AlertDialog or simple screen with: name, phrases (one per line), actions (one per line in the
format TYPE or TYPE:arg, e.g. OPEN_APP:tiktok / WAIT:2000 / CLICK_TEXT:follow). Validate the action lines
and show Bangla error messages for bad lines. Add an "উদাহরণ যোগ করুন" button that inserts 2 sample commands.
Add an entry button "আমার কমান্ডগুলো" in AssistantActivity.

Acceptance: create "morning" macro, say "hey bot good morning", it runs the steps in order. Corrupt JSON does
not crash the app.
````

---

## T3 — Modes / Roles (app অনুযায়ী আচরণ) + Repeat + Auto-scroll

````text
TASK T3 — Modes (roles) per foreground app, "repeat N times", auto-scroll. (Not ASK-FIRST.)

Goal: the same word behaves best for the app in front.

1. New file `AssistantMode.kt`:
   enum AssistantMode { GENERAL, SHORT_VIDEO, YOUTUBE, BROWSER, MESSAGING }
   object ModeProfiles { fun modeFor(pkg: String): AssistantMode } using AssistantConfig.SHORT_VIDEO_APPS
   for SHORT_VIDEO (TikTok, Reels, FB Reels), YouTube package -> YOUTUBE, Chrome/Firefox/Brave/Samsung Internet
   packages -> BROWSER, WhatsApp/Messenger/Telegram -> MESSAGING, else GENERAL. Make the package lists editable
   later from AssistantPrefs (store as sets; defaults in AssistantConfig).
2. Mode behaviour table (implement in CommandExecutor, reading the mode from
   AssistantAccessibilityService.foregroundPackage()):
   - SHORT_VIDEO: up/down = full swipe; like = double-tap center; pause/play = tap center.
   - YOUTUBE: like = click "like" button by description; pause/play = tap center; up/down = full swipe
     (Shorts) — if not in Shorts, a half-length swipe.
   - BROWSER / MESSAGING / GENERAL: up/down = HALF-length swipe (page scroll), like = click-by-text "like" else double-tap.
3. Show the current mode in the AssistantActivity status area (refresh on resume) and in the log.
4. New command `Command.Repeat(times, inner)`: "up 5 times", "5 bar up", "পাঁচ বার নিচে", "down 3". Parse
   digits and number words in English/Banglish/Bangla (1..20). Execute with 400 ms gaps, cancellable.
   Cap at 20.
5. New commands `Command.AutoScrollStart(intervalSec)` and `Command.AutoScrollStop`: "auto scroll" (default
   8 s), "auto scroll 5 seconds", "stop auto scroll" / "থামাও scroll". Implement with the main Handler
   posting `Command.Swipe(UP)` repeatedly; stop on Stop command, on "stop everything", when the service stops,
   or when the foreground package changes to HOME/launcher. Show a Toast on start/stop.
6. Update COMMANDS.md.

Acceptance: in TikTok "auto scroll 6 seconds" moves to the next video about every 6 s until "stop auto scroll".
````

---

## T4 — Safety: confirm, blocked apps, panic stop

````text
TASK T4 — Safety layer. (Not ASK-FIRST.)

1. Confirmation flow in AssistantListenerService: a nullable `pending: Command?` with a 10 s timeout.
   Commands that need confirmation (configurable list in AssistantPrefs, default: Call, TypeText, Macro that
   contains CALL or TYPE_TEXT): instead of executing, set pending and show "call mim? হ্যাঁ/না বলুন"
   (Toast now, TTS later if T5 exists). Next utterance: yes-words (yes, ha, haa, ji, ok, confirm, হ্যাঁ, জি,
   ঠিক আছে) -> execute pending; no-words (no, na, cancel, না, বাতিল) -> drop it. Anything else keeps waiting
   until timeout. Add a Controls switch "Call এর আগে confirm চাই" (default ON).
2. Blocked apps: AssistantPrefs.blockedPackages (set). When the foreground package is blocked, the executor
   refuses gesture/click/type commands with the Toast "🔒 এই app-এ assistant বন্ধ". Seed the default list with
   package names of banking / mobile-money / wallet apps ONLY IF you are sure of the exact package name;
   otherwise ship an empty default and tell me how to add package names. Provide an editor in the Controls
   section (multi-line EditText, one package per line) plus a button "এখনকার app ব্লক করুন" that adds the
   current foreground package.
3. Password protection: AssistantAccessibilityService gets `isPasswordFieldFocused()` (focused node
   isPassword). TypeText must refuse there with a Toast.
4. Panic stop: "stop everything" / "sob bondho" / "সব বন্ধ" cancels pending confirmation, macros, repeat,
   auto-scroll (T2/T3 if present) and puts the assistant to sleep. This must work even when the assistant is
   in sleep mode only if the wake word is said first.
5. Never auto-click buttons whose text/description looks like send/pay/delete/confirm purchase in
   ClickText unless the user said that exact word; for ClickText with such words ask for confirmation.

Acceptance: "hey bot call mim" -> asks to confirm -> "yes" -> calls; "no" -> nothing. In a blocked app, "up"
does nothing and says so.
````

---

## T5 — Feedback (TTS, vibration, notification)

````text
TASK T5 — Feedback channels. (Not ASK-FIRST.)

1. `AssistantSpeaker.kt` wrapping android.speech.tts.TextToSpeech (built in; no permission). Init lazily,
   language follows the assistant language pref (bn-BD / en-US, fall back to default if unavailable),
   shutdown in AssistantListenerService.onDestroy. `speak(text)` only when the Controls switch
   "বলে উত্তর দাও" is ON (default OFF). Use short phrases; do not speak every command, only: confirmations,
   errors, wake acknowledgement ("বলুন"), and command results marked speakable.
2. Important: pause recognition while TTS is speaking (use UtteranceProgressListener) to avoid the assistant
   hearing itself; resume after.
3. Short vibration (VibratorManager/Vibrator with API guards) on "command recognized" — switch default ON.
4. Update the foreground notification text with the last command + result (re-notify the same id).
5. Controls: switches for speech, vibration; a "Test voice" button.

Acceptance: with speech ON, "hey bot" says "বলুন"; errors are spoken; the mic does not self-trigger.
````

---

## T6 — Command packs

````text
TASK T6 — New command packs. (Not ASK-FIRST, but implement per pack and stop after each pack if the build is
at risk.)

Follow recipe R1 in 02-ARCHITECTURE-MAP.md for every command (Command class -> parse -> execute -> COMMANDS.md).
Keep parse() ordering safe: specific phrases before generic words. Every node-based command must fail with a
Bangla Toast when the button is not found. Respect T4 safety rules and T3 modes if they exist.

Pack A — Social/video (node-based, best effort):
  share / শেয়ার            -> click "share"
  comment / কমেন্ট          -> click "comment"
  follow / ফলো              -> click "follow"
  subscribe / সাবস্ক্রাইব   -> click "subscribe"
  save / bookmark / সেভ     -> click "save" / "bookmark"
  skip ad / বিজ্ঞাপন বন্ধ   -> click "skip" (YouTube mode)
  fullscreen / ফুলস্ক্রিন   -> click "fullscreen" / "full screen"
Pack B — Navigation:
  top / শুরুতে             -> repeated downward swipes (max 8)
  scroll fast / slow        -> adjusts AssistantPrefs.scrollDurationMs
Pack C — System (no special permission):
  torch on/off / টর্চ        -> CameraManager.setTorchMode (find a camera with FLASH_INFO_AVAILABLE; guard exceptions)
  volume max / mute          -> AudioManager
  media play / next / previous (system-wide) -> AudioManager.dispatchMediaKeyEvent with KEYCODE_MEDIA_PLAY_PAUSE / NEXT / PREVIOUS
  wifi / bluetooth / internet -> open the Settings panel (Settings.Panel.ACTION_INTERNET_CONNECTIVITY on API 29+,
                                 else the matching Settings screen). Do NOT try to toggle programmatically.
  open <url> / website খোলো  -> ACTION_VIEW for http(s) URLs only
Pack D — Search in current app:
  search <query>             -> focus the first editable field / click a node described "search", type the
                                 query, press Enter. Best effort; Toast on failure.

Do NOT implement: sending SMS/WhatsApp messages automatically, payments, deleting anything, answering
calls, installing apps.

Acceptance: each new command has a COMMANDS.md row, a Bangla failure Toast, and no new permission except
what 04-PERMISSIONS-GUIDE.md already lists as normal.
````

---

## T7 — Floating mic bubble  *(ASK-FIRST)*

````text
TASK T7 — Floating assistant bubble. ASK-FIRST: show me the plan (files, permissions, risks) and wait for "OK".

Goal: a small draggable overlay bubble so I can wake the assistant without saying "Hey Bot" and see its state.

Spec: WindowManager overlay (TYPE_APPLICATION_OVERLAY on API 26+, TYPE_PHONE below) hosted by
AssistantListenerService (create/remove with the service lifecycle). Requires the existing
SYSTEM_ALERT_WINDOW permission; if not granted, show the Controls row as ❌ and don't crash.
- tap: open a 20 s "awake window" immediately (same as hearing the wake word)
- long-press: toggle sleep
- drag: reposition; remember position in AssistantPrefs
- colour/icon states: sleeping / awake / listening / paused (during keyboard voice typing)
- a Controls switch "Floating bubble" (default OFF); remove the view in onDestroy to avoid leaks
- the bubble must be excluded from Accessibility gestures (use FLAG_NOT_FOCUSABLE; make sure swipes are not
  intercepted) and must not appear in blocked apps (T4) if that feature exists.

Acceptance: bubble visible over TikTok, tap -> say "up" works without the wake word.
````

---

## T8 — Speech engine abstraction + offline options  *(ASK-FIRST)*

````text
TASK T8 — Pluggable speech engine. ASK-FIRST for anything that adds a dependency or downloads a model.

Phase 1 (safe, do it): introduce
    interface SpeechEngine { fun start(); fun stop(); fun pause(); fun resume(); var onResult: (List<String>) -> Unit }
and move the current SpeechRecognizer logic out of AssistantListenerService into `SystemSpeechEngine`.
Behaviour must stay identical (restart/back-off, language pref, paused handling). AssistantListenerService
only talks to SpeechEngine. Build must pass with zero behaviour change.

Phase 2 (ASK-FIRST): research and propose, with honest trade-offs, ONE offline option, e.g. Vosk (needs a
Gradle dependency + downloadable model, APK/RAM size) or a wake-word engine such as Porcupine (needs an
AccessKey and a custom wake word). Tell me: which languages/models really exist (do not assume Bangla
support — say clearly if only English is available), size, battery, licence, which Gradle lines you would
add to app/build.gradle.kts / libs.versions.toml, and where model files would live. Wait for my "OK"
before changing Gradle files.

Acceptance (Phase 1): identical behaviour on the phone; the code compiles.
````

---

## T9 — Onboarding wizard + self-test

````text
TASK T9 — Setup wizard and self-test. (Not ASK-FIRST.)

Goal: a new user reaches "working" with one primary button.

In AssistantActivity (plain Views) replace the loose button list with a step list. Each step shows ✅/❌, one line of Bangla explanation, and a button that opens the exact page:
 1 Keyboard enabled & selected (InputMethodManager.getEnabledInputMethodList; button -> ACTION_INPUT_METHOD_SETTINGS; second button -> showInputMethodPicker)
 2 Microphone, Contacts, Call, Notifications (runtime dialog; status per permission)
 3 Accessibility (service connected vs only enabled; Restricted-settings help text for Android 13+ with a button to App info)
 4 Display over other apps
 5 Battery: unrestricted (PowerManager.isIgnoringBatteryOptimizations; button -> ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS with package URI; add the permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to the manifest once)
 6 Start assistant
A primary "পরের ধাপ" button always opens the first incomplete step. Re-check every step in onResume.
Add "Self-test": (a) SpeechRecognizer.isRecognitionAvailable, (b) Accessibility instance != null,
(c) a test swipe after 3 s, (d) AudioManager mic mute state hint — report each as ✅/❌ in a result box.
Also update the Compose badge text in BanglaKeysScreen to reflect "Setup needed / Ready / On" using the same
checks (keep existing string resources; add new ones if required).

Acceptance: from a fresh install, pressing only the primary button repeatedly walks me through every
page; nothing crashes if a Settings page is missing on an OEM ROM (wrap startActivity in try/catch and fall
back to general Settings).
````

---

## T10 — Docs & release notes

````text
TASK T10 — Documentation sync. (Not ASK-FIRST.)

Regenerate/update: COMMANDS.md (every built-in command, phrases in English/Banglish/Bangla, modes
behaviour, custom-command format), SETUP-ASSISTANT.md (current manifest + permission list + first-run
steps), README-FIRST.md (file map), and create CHANGELOG.md with one entry per task done so far.
Do not change code in this task. Keep each doc in the existing Bangla/English mix, short and accurate to the
code as it is NOW (read the code, do not rely on memory).
````
