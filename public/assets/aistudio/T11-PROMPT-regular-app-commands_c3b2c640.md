# T11 Prompt — Regular App Commands (files/folders, AI chat apps, imo/WhatsApp calls, everyday apps)

**কীভাবে ব্যবহার করবে:** আগে Gemini-কে `BanglaKeys-Gemini-Upgrade-Kit` এর md ফাইলগুলো (00–05) দিয়ে দাও
(roles/নিয়ম ঢোকানো)। তারপর নিচের `=====` লাইনের পর থেকে শেষ পর্যন্ত পুরোটা paste করো।
আগে T0, T1, T4 করা থাকলে সবচেয়ে ভালো (না থাকলেও চলবে, prompt-এ বলা আছে কী করবে)।

=====================================================================

TASK T11 — Regular-app voice commands for the BanglaKeys Assistant.

STEP A — Follow the Kit. The files of "BanglaKeys-Gemini-Upgrade-Kit" (00-READ-ME-FIRST.md,
01-GEMINI-MASTER-PROMPT.md, 02-ARCHITECTURE-MAP.md, 03-UPGRADE-TASKS.md, 04-PERMISSIONS-GUIDE.md,
05-TEST-AND-DEBUG.md) are in this project/conversation. Read ALL of them before anything else and obey them as
the rulebook: the project facts and non-negotiable rules in 01, the recipes R1 (new command), R2 (app alias),
R4 (new permission, 6 steps), R5 (new control) and the pitfalls in 02, the permission rules in 04, and the
report/test format in 05. If any of these files is missing, STOP and tell me which one; do not guess its content.
Work in the kit's order: Step 0 read, Step 1 plan, Step 2 implement, Step 3 self-review checklist, Step 4 report in
Bangla. Keep logging with tag "BKAssistant". This task is ASK-FIRST only for Step 1: show the plan, then wait for
my "OK".

Prerequisites check (do this in Step 0): does AssistantPrefs (T1) exist? does the confirmation flow (T4,
`pending: Command?` in AssistantListenerService) exist? If NOT, implement ONLY the minimal slice this task
needs: the few AssistantPrefs keys listed below, and the pending-confirm mechanism exactly as in T4 point 1.
Say clearly in your report which slice you had to add.

GOAL — Make everyday "use an app for me" voice commands work, in a data-driven way so that adding a new app later
means adding ONE table row, not new logic.

-----------------------------------------------------------------
PACK F — Create files and folders (no dangerous operations)
-----------------------------------------------------------------
Phrases (English / Banglish / Bangla), spoken after the wake word:
  "create folder photos" / "folder banao photos" / "ফোল্ডার বানাও ছবি"
  "create folder tour inside photos" / "photos er moddhe folder banao tour"
  "create file notes" / "create file notes dot txt" / "file banao notes" / "ফাইল বানাও নোটস"
  "create file shopping with text milk eggs bread"  -> file with that content
Behaviour:
- Use the Storage Access Framework, NOT broad storage permissions. Add AssistantPrefs.storageTreeUri. In
  AssistantActivity add a row "Assistant storage folder" with a button that launches
  Intent.ACTION_OPEN_DOCUMENT_TREE; on result call
  contentResolver.takePersistableUriPermission(uri, READ|WRITE) and save the URI. Show ✅/❌ status for it.
  This is a user tap — you cannot do it for me; make the screen tell me so.
- Create with DocumentsContract only (no new dependency; do NOT add androidx.documentfile):
  parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)) (or the
  resolved sub-folder); folder mime = DocumentsContract.Document.MIME_TYPE_DIR; files:
  DocumentsContract.createDocument(resolver, parent, mime, displayName); write text via
  contentResolver.openOutputStream(newUri). Default extension .txt, mime text/plain; understand spoken
  extensions ("dot txt", "ডট টিএক্সটি", "md", "csv", "json").
- Sanitize names: strip / \ : * ? " < > |, trim, max 80 chars, keep Bangla characters; if the name exists,
  append " (2)", " (3)". NEVER overwrite, NEVER delete, NEVER rename. (No delete/rename commands in this task.)
- If no storage folder is chosen yet: Toast "আগে Assistant storage folder বেছে নিন" and open AssistantActivity.
- Nested folders: support one level "X inside Y" (find Y under the chosen root; if missing, say so — don't create it silently).
- Feedback: "📁 photos তৈরি হয়েছে" / "📄 notes.txt তৈরি হয়েছে".

-----------------------------------------------------------------
PACK G — Open an app, type text, send (AI chat apps first)
-----------------------------------------------------------------
Phrases:
  "open claude type hi and send" / "claude khulo hi likho send koro" / "ক্লড খোলো হাই লিখে পাঠাও"
  "open chatgpt type hello and send"            "open gemini type what is the weather and send"
  "open claude" / "open chatgpt"                 (plain open must keep working)
  "type hi and send"                              (in the CURRENT app: type into the focused field, then send)
Parsing: [open|খোলো <app>] [then|and|tarpor] (type|write|likho|লেখো) <TEXT> [and|then] (send|pathao|পাঠাও|send koro).
The send word counts only at the END of the sentence, so TEXT can contain the word "and". If there is no send word,
only type (don't send).
Flow (new `Command.AppChat(app, text, send)`):
  1. launch the app (AppResolver); 2. wait until it is in the foreground (poll AssistantAccessibilityService
  foregroundPackage up to 6 s); 3. wait for an editable input; 4. put the text in; 5. wait ~400 ms; 6. click the send
  control; 7. report the result.
Text entry fallback chain (implement in AssistantAccessibilityService as `enterText(node, text)`):
  a) focus/click the node then ACTION_SET_TEXT; b) if that fails, set the clipboard and ACTION_PASTE on the focused node
  (note: ClipboardMemory records clips — mark this one so it is not saved into the notepad history).
Finding the input: first node with isEditable (or className EditText); else a node whose text/hint/description
contains one of the app's inputHints ("message", "ask", "type", "প্রশ্ন", "মেসেজ"); else the lowest clickable node
at the bottom area. Finding send: node whose contentDescription/text matches the app's sendHints ("send",
"send message", "পাঠান", "পাঠাও"); never click anything else.
Known packages (verify at runtime; if not installed the AppResolver label search is the fallback, and tell me
if a package name looked wrong): Claude com.anthropic.claude, ChatGPT com.openai.chatgpt, Gemini
com.google.android.apps.bard.

-----------------------------------------------------------------
PACK H — Calls and chats inside messaging apps (imo, WhatsApp, Messenger, Telegram)
-----------------------------------------------------------------
Phrases:
  "open imo call fatema"  /  "imo te fatema ke call koro"            -> AUDIO call
  "open imo video call fatema" / "imo te fatema ke video call"       -> VIDEO call
  "ইমুতে ফাতেমাকে ভিডিও কল"        "whatsapp call ammi audio"
  "call fatema on imo video"         (plain "call fatema" with NO app name = normal phone call, existing behaviour)
  "whatsapp e fatema ke hi pathao"   -> open chat, type "hi", send (see confirm rule)
Flow (new `Command.AppCall(app, contact, video)` and `Command.AppMessage(app, contact, text, send)`):
  open app -> wait foreground -> click the app's searchHints node (or its search icon) -> type the contact name
  (also try CONTACT_GROUPS aliases and the Bangla/English variants) -> click the FIRST matching result (a node
  containing the contact name) -> for calls click the audioCallHints or videoCallHints node; for messages type
  the text and click sendHints.
  If a step is not found within its timeout: stop, Toast the step that failed in Bangla ("imo-তে search বাটন পাইনি"),
  and leave the app open. Never tap blindly at coordinates.
Safety: calls and human-to-human messages go through the confirmation flow (T4) by default:
  "imo-তে fatema কে video call করব? হ্যাঁ/না". Controls switches (R5) with these defaults:
    confirmBeforeAppCall = ON, confirmBeforeHumanSend = ON, confirmBeforeAiChatSend = OFF.
  Never auto-send in apps on the blocked list (T4). Never send to more than one contact per command.

-----------------------------------------------------------------
Data-driven design (important — this is how I will add apps later)
-----------------------------------------------------------------
Create `AppFlows.kt`:
  data class AppFlow(
    val key: String, val names: List<String>, val packages: List<String>, val kind: Kind /* AI_CHAT, MESSENGER */,
    val inputHints: List<String>, val searchHints: List<String>, val sendHints: List<String>,
    val audioCallHints: List<String>, val videoCallHints: List<String>,
    val openTimeoutMs: Long = 6000, val stepTimeoutMs: Long = 4000)
  object AppFlows { val all: List<AppFlow>; fun find(spokenName: String): AppFlow? }
Fill rows for: claude, chatgpt, gemini (AI_CHAT) and imo (com.imo.android.imoim), whatsapp (com.whatsapp),
messenger (com.facebook.orca), telegram (org.telegram.messenger) (MESSENGER). Hints in English AND Bangla.
A new app = one new row; no other code change. AppResolver.KNOWN gets the same names so plain "open X" works.
Create `FlowRunner.kt` that runs these flows with kotlinx coroutines (already a dependency — verify in
app/build.gradle.kts before using): a CoroutineScope(SupervisorJob()+Dispatchers.Main) owned by
AssistantListenerService, cancelled in onDestroy AND by the "stop everything"/panic command; helper
`waitFor(timeoutMs){ condition }` polling every 300 ms; no blocking sleeps on the main thread.
Add to AssistantAccessibilityService (no behaviour change to existing functions): `findNode(predicate)`,
`findEditable()`, `findByHints(hints)`, `clickByHints(hints)`, `enterText(node, text)`, `waitForPackage`.
Accessibility UI differs per app version and language — design every lookup around hints lists and
text/contentDescription (lowercase, contains), and log what was searched/found with tag "BKAssistant".

-----------------------------------------------------------------
PACK I — Everyday app commands (intents first, accessibility only if needed)
-----------------------------------------------------------------
Implement each as its own Command (R1), with Bangla failure Toasts:
  "open camera" / "take photo" -> open the camera (MediaStore.ACTION_IMAGE_CAPTURE or launch by label); "take photo"
                                   additionally taps the shutter ONLY if a node described "shutter/capture/ছবি তুলুন" is found
  "set alarm 7 30 am" / "7:30 am e alarm" / "সকাল ৭টা ৩০ এ alarm"
                                -> AlarmClock.ACTION_SET_ALARM (needs normal permission
                                   com.android.alarm.permission.SET_ALARM — add once via R4; skip UI = true)
  "set timer 5 minutes" / "5 minute timer" -> AlarmClock.ACTION_SET_TIMER
  "navigate to Dhaka airport" / "route dekhao ..." -> geo:0,0?q=<place> (Google Maps if installed, else chooser)
  "search google for best phones" / "google e khojo ..." -> ACTION_WEB_SEARCH
  "play song tum hi ho" / "gaan chalao ..." -> MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
                                   (existing "youtube search ..." must keep winning when the word youtube is present)
  "save note buy milk" / "note likho ..." -> use the existing Notes.kt class (keyboard notepad). Confirm with a Toast.
  "write email to <name> subject <s> body <b>" -> ACTION_SENDTO mailto: with prefilled fields. The user presses
                                   send. Never auto-send email or SMS.
  "write sms to <name> <text>" -> open the SMS composer prefilled (ACTION_SENDTO smsto:). Never auto-send.
  "open wifi settings / bluetooth settings / display settings / sound settings" -> the matching Settings intent.
Understand digits and number words in English, Banglish and Bangla (১২৩, "সাত", "saat") for times/durations.

-----------------------------------------------------------------
Parser rules (CommandParser.parse)
-----------------------------------------------------------------
Order: custom commands (T2, if present) -> quit/sleep/panic -> PACK H (app call/message: needs an app name from
AppFlows + a call/message word) -> PACK G (app + type/send) -> PACK F -> PACK I -> existing rules (call, youtube,
open, tap, type, like, ...). The existing plain "call fatema" (no app) and "open tiktok" behaviour must be
unchanged — add regression examples to your report. "type X" with NO app named keeps its current behaviour.
Every new phrase has English, Banglish and Bangla variants in the shared token tables (not one-off string checks).
Recognizer can mishear app names ("claude" -> "cloud", "imo" -> "emo/ইমো"): add a small `misheard` alias list
inside AppFlows.names ("cloud", "clod", "emo", "ইমু", "ইমো", "চ্যাট জিপিটি", "chat gpt", "chatgpt").

-----------------------------------------------------------------
Permissions / Controls / Docs
-----------------------------------------------------------------
- New permissions only: com.android.alarm.permission.SET_ALARM (normal). Storage uses SAF (no permission).
  Camera/maps/web-search are intents (no permission). Apply the 6-step procedure in 04 and update
  AssistantSetup/AssistantActivity status rows. Remind me (in the report) exactly which taps I must do on the
  phone: pick the storage folder, plus the Accessibility/overlay/battery ones that already exist.
- Controls (R5): the three confirm switches above, "Assistant storage folder" row, "app-flow timeout (s)" SeekBar
  2..15 (default 6).
- Docs: add a section per pack to COMMANDS.md (phrase tables) and create APP-FLOWS.md explaining in Bangla, step by
  step, how I add a NEW app (copy a row in AppFlows.kt, how to find the right hints with an Accessibility
  inspector or by reading the log lines, how to test it), how to add a new everyday command (R1) and how to add a
  new phrase variant. Use the real class and function names.

-----------------------------------------------------------------
Do NOT
-----------------------------------------------------------------
No delete/rename/move of files. No automatic sending of SMS/email/payment. No coordinate-based blind tapping. No
broad storage permissions, no QUERY_ALL_PACKAGES, no new Gradle dependencies. No change to keyboard behaviour. No
change to signingConfigs/Gradle versions/workflow. Do not claim a flow works in an app you could not test —
say "best effort, needs phone test".

-----------------------------------------------------------------
Acceptance (I will test on a real phone using the GitHub-built APK)
-----------------------------------------------------------------
1 "hey bot create folder test" -> folder appears in my chosen storage folder; again -> "test (2)".
2 "create file notes with text hello" -> notes.txt containing hello.
3 "hey bot open claude type hi and send" -> Claude opens, "hi" typed, sent.
4 "open chatgpt type hello" -> typed, not sent.
5 "open imo video call fatema" -> confirm -> imo opens, contact found, video call started (or a precise Bangla
  message about the step that failed).
6 "call fatema" -> still a normal phone call; "open tiktok" and "up" still work.
7 "set alarm 7 30 am", "set timer 5 minutes", "navigate to <place>", "save note buy milk" all work.
Finish with the kit's Step 4 report: changed/new files, my manual phone steps, a 5-line voice test script, known limits.
