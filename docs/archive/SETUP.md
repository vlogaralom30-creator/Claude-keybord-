# BanglaKeys — New Features Setup

এই folder-এ ৫টা **নতুন** file আছে (আগে যা ছিল তার কোনোটাই touch করা হয়নি):

```
app/src/main/java/com/banglakeys/app/Suggestions.kt      -> word suggestion + learning
app/src/main/java/com/banglakeys/app/ClipboardMemory.kt  -> clipboard history ("memory")
app/src/main/java/com/banglakeys/app/Notes.kt            -> সেভ করা notepad snippet
app/src/main/java/com/banglakeys/app/Translator.kt       -> bn<->en translation (free API)
app/src/main/java/com/banglakeys/app/NotepadActivity.kt  -> notepad + clipboard দেখার screen
```

**Step 1:** এই ৫টা ফাইল হুবহু একই path-এ (`app/src/main/java/com/banglakeys/app/`) আপনার
আসল BanglaKeys project-এ copy করে বসান। এগুলো নিজে থেকে compile হবে, কিন্তু কিবোর্ডে
দেখা যাবে না যতক্ষণ না নিচের ৫টা পুরনো file-এ ছোট ছোট change করা হয়।

**Step 2:** নিচের প্রতিটা অংশে বলা "Find this" টেক্সট পুরনো file-এ খুঁজে, তার জায়গায়
"Replace with" / "Add after" অংশটা বসান। প্রতিটা change ছোট এবং স্বয়ংসম্পূর্ণ।

---

## 1. `AndroidManifest.xml`

Internet permission (translation-এর জন্য) আর Notepad screen-এর entry যোগ করুন।

**Find:**
```xml
    <uses-permission android:name="android.permission.VIBRATE" />
```

**Replace with:**
```xml
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.INTERNET" />
```

**Find:**
```xml
        <activity
            android:name=".SettingsActivity"
```

**Add right before it (new activity entry):**
```xml
        <activity
            android:name=".NotepadActivity"
            android:exported="false"
            android:label="Notepad" />

```

---

## 2. `Layouts.kt`

### 2a. নতুন KeyType

**Find:**
```kotlin
enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, SYMBOLS2, ABC, LANG, SPACER }
```

**Replace with:**
```kotlin
enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, SYMBOLS2, ABC, LANG, SPACER, SNIPPET, NOTEPAD, TRANSLATE }
```

### 2b. বটম row-এ দুটো নতুন button (Notepad panel খোলা / Translate)

**Find:**
```kotlin
        // Row 4
        rows.add(
            listOf(
                Key("?123", KeyType.SYMBOLS, 1.5f),
                Key("🌐", KeyType.LANG, 1f),
                Key(","),
                Key(" ", KeyType.SPACE, 4f),
                if (bangla) Key("।", alt = ".") else Key("."),
                Key("⏎", KeyType.ENTER, 1.5f),
            )
        )
```

**Replace with:**
```kotlin
        // Row 4
        rows.add(
            listOf(
                Key("?123", KeyType.SYMBOLS, 1.5f),
                Key("🌐", KeyType.LANG, 1f),
                Key(","),
                Key(" ", KeyType.SPACE, 3f),
                if (bangla) Key("।", alt = ".") else Key("."),
                Key("📝", KeyType.NOTEPAD, 1f),
                Key("Tr", KeyType.TRANSLATE, 1f),
                Key("⏎", KeyType.ENTER, 1.5f),
            )
        )
```

(চাইলে 📝/Tr-এর জায়গায় অন্য label/emoji বসাতে পারেন, বা কোনো একটা বাদ দিয়ে অন্য
row-এ বসাতে পারেন — Key-র weight ভাগ হয় width অনুযায়ী, মোট ১০ হতেই হবে এমন না।)

### 2c. Notepad panel-এর layout generator (নতুন function, `object Layouts` এর ভেতরে)

**Find:**
```kotlin
    private fun bottomSymbols(): List<Key> = listOf(
```

**Add right before it:**
```kotlin
    /** Notepad panel: সেভ করা note + clipboard history-র button-গুলো দেখায়। */
    fun panel(items: List<String>): List<List<Key>> {
        val rows = ArrayList<List<Key>>()
        rows.add(listOf(Key("‹ কিবোর্ডে ফিরুন", KeyType.ABC, 10f)))
        if (items.isEmpty()) {
            rows.add(listOf(Key("কোনো note/clipboard নেই", KeyType.SPACER, 10f)))
            return rows
        }
        var row = ArrayList<Key>()
        var used = 0f
        for (raw in items) {
            val display = if (raw.length > 18) raw.take(16) + "…" else raw
            if (used + 5f > 10f) {
                rows.add(row)
                row = ArrayList()
                used = 0f
            }
            row.add(Key(display, KeyType.SNIPPET, 5f, alt = raw))
            used += 5f
        }
        if (row.isNotEmpty()) rows.add(row)
        return rows
    }

```

---

## 3. `KeyboardView.kt`

### 3a. Listener interface-এ নতুন callback

**Find:**
```kotlin
        fun onComposingTap()
        fun onFeedback()
    }
```

**Replace with:**
```kotlin
        fun onComposingTap()
        fun onFeedback()
        fun onSuggestionTap(word: String)
        fun onSnippet(text: String)
        fun onNotepad()
        fun onTranslate()
    }
```

### 3b. Suggestion state + public setter

**Find:**
```kotlin
    private var composing = ""
    private var spaceLabel = ""
```

**Replace with:**
```kotlin
    private var composing = ""
    private var suggestions: List<String> = emptyList()
    private var suggestionRects: List<RectF> = emptyList()
    private var spaceLabel = ""
```

**Find:**
```kotlin
    fun setComposing(text: String) {
        if (composing != text) {
            composing = text
            invalidate()
        }
    }
```

**Add right after it:**
```kotlin

    fun setSuggestions(list: List<String>) {
        if (suggestions != list) {
            suggestions = list
            invalidate()
        }
    }
```

### 3c. Strip-এ suggestion chip আঁকা

**Find:**
```kotlin
    private fun drawStrip(c: Canvas) {
        if (composing.isEmpty()) return
```

**Replace with:**
```kotlin
    private fun drawStrip(c: Canvas) {
        if (suggestions.isNotEmpty()) {
            drawSuggestions(c)
            return
        }
        if (composing.isEmpty()) return
```

**Find** (just above `private fun isPressed`):
```kotlin
    private fun isPressed(b: Box): Boolean {
```

**Add right before it:**
```kotlin
    private fun drawSuggestions(c: Canvas) {
        val n = suggestions.size
        val w = width / n.toFloat()
        val rects = ArrayList<RectF>()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 18f * fontD
        val fm = textPaint.fontMetrics
        val y = stripH / 2f - (fm.ascent + fm.descent) / 2f
        for (i in 0 until n) {
            val left = i * w
            val rect = RectF(left, 0f, left + w, stripH)
            rects.add(rect)
            textPaint.color = textC
            c.drawText(suggestions[i], rect.centerX(), y, textPaint)
            if (i > 0) {
                paint.color = shadowC
                paint.strokeWidth = 1f * d
                c.drawLine(left, stripH * 0.25f, left, stripH * 0.75f, paint)
            }
        }
        suggestionRects = rects
    }

```

### 3d. Strip tap হলে suggestion-এ hit-test করা

**Find:**
```kotlin
        if (p.inStrip) {
            if (y < stripH) listener?.onComposingTap()
        } else {
```

**Replace with:**
```kotlin
        if (p.inStrip) {
            if (y < stripH) {
                val idx = suggestionRects.indexOfFirst { it.contains(x, y) }
                if (suggestions.isNotEmpty() && idx >= 0) {
                    listener?.onFeedback()
                    listener?.onSuggestionTap(suggestions[idx])
                } else if (suggestions.isEmpty()) {
                    listener?.onComposingTap()
                }
            }
        } else {
```

### 3e. নতুন KeyType-গুলোর tap handling

**Find:**
```kotlin
            KeyType.ABC -> l.onSymbols(0)
```

**Replace with:**
```kotlin
            KeyType.ABC -> l.onSymbols(0)
            KeyType.SNIPPET -> l.onSnippet(k.alt ?: k.label)
            KeyType.NOTEPAD -> l.onNotepad()
            KeyType.TRANSLATE -> l.onTranslate()
```

---

## 4. `BanglaIME.kt`

### 4a. নতুন object তৈরি + clipboard listener চালু/বন্ধ

**Find:**
```kotlin
    private var heightPct = 100

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("bk", Context.MODE_PRIVATE)
        userBangla = prefs.getBoolean("bn", true)
        bangla = userBangla
    }
```

**Replace with:**
```kotlin
    private var heightPct = 100
    private var inPanel = false

    private lateinit var suggestions: Suggestions
    private lateinit var notes: Notes
    private lateinit var clipboardMemory: ClipboardMemory

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("bk", Context.MODE_PRIVATE)
        userBangla = prefs.getBoolean("bn", true)
        bangla = userBangla
        suggestions = Suggestions(this)
        notes = Notes(this)
        clipboardMemory = ClipboardMemory(this)
        clipboardMemory.start()
    }

    override fun onDestroy() {
        clipboardMemory.stop()
        super.onDestroy()
    }
```

### 4b. Composing update হলে suggestion বার refresh করা

**Find:**
```kotlin
        val bn = Phonetic.convert(buf.toString())
        ic.setComposingText(bn, 1)
        kv?.setComposing(bn)
    }
```

**Replace with:**
```kotlin
        val bn = Phonetic.convert(buf.toString())
        ic.setComposingText(bn, 1)
        kv?.setComposing(bn)
        kv?.setSuggestions(suggestions.suggestionsFor(buf.toString(), bangla))
    }
```

### 4c. Commit হওয়ার সময় শেখা + পরের suggestion দেখানো

**Find:**
```kotlin
    private fun commitComposing() {
        if (buf.isEmpty()) return
        currentInputConnection?.finishComposingText()
        buf.setLength(0)
        kv?.setComposing("")
    }
```

**Replace with:**
```kotlin
    private fun commitComposing() {
        if (buf.isEmpty()) return
        val bn = Phonetic.convert(buf.toString())
        suggestions.learn(buf.toString(), bn)
        currentInputConnection?.finishComposingText()
        buf.setLength(0)
        kv?.setComposing("")
        kv?.setSuggestions(suggestions.suggestionsFor("", bangla))
    }
```

### 4d. নতুন Listener callback-গুলোর implementation

**Find:**
```kotlin
    override fun onComposingTap() {
        commitComposing()
    }
```

**Replace with:**
```kotlin
    override fun onComposingTap() {
        commitComposing()
    }

    override fun onSuggestionTap(word: String) {
        val ic = currentInputConnection ?: return
        if (buf.isNotEmpty()) suggestions.learn(buf.toString(), word)
        buf.setLength(0)
        kv?.setComposing("")
        ic.commitText("$word ", 1)
        kv?.setSuggestions(suggestions.suggestionsFor("", bangla))
    }

    override fun onSnippet(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onNotepad() {
        commitComposing()
        inPanel = !inPanel
        if (inPanel) {
            val items = notes.all() + clipboardMemory.clips()
            kv?.setLayout(Layouts.panel(items), spaceLabelFor(), heightPct / 100f)
        } else {
            refreshLayout()
        }
    }

    override fun onTranslate() {
        val ic = currentInputConnection ?: return
        val text = if (buf.isNotEmpty()) Phonetic.convert(buf.toString())
        else ic.getSelectedText(0)?.toString()
        if (text.isNullOrBlank()) return
        val from = if (bangla) "bn" else "en"
        val to = if (bangla) "en" else "bn"
        Translator.translate(text, from, to) { result ->
            if (result != null) {
                commitComposing()
                ic.commitText(result, 1)
            }
        }
    }

    private fun spaceLabelFor(): String = if (bangla) "বাংলা" else "English"
```

### 4e. Panel থেকে বের হলে `inPanel` রিসেট করা + বাক্য শেষে suggestion context রিসেট

**Find:**
```kotlin
    override fun onSymbols(mode: Int) {
        commitComposing()
        symbols = mode
        refreshLayout()
    }
```

**Replace with:**
```kotlin
    override fun onSymbols(mode: Int) {
        commitComposing()
        inPanel = false
        symbols = mode
        refreshLayout()
    }
```

**Find:**
```kotlin
    override fun onEnter() {
        commitComposing()
        val ic = currentInputConnection ?: return
```

**Replace with:**
```kotlin
    override fun onEnter() {
        commitComposing()
        suggestions.resetSentenceContext()
        val ic = currentInputConnection ?: return
```

---

## 5. `SettingsActivity.kt`

Settings screen থেকে Notepad খোলার জন্য একটা button যোগ করুন।

**Find:**
```kotlin
        root.addView(label("Settings", 20f, true))
```

**Add right before it:**
```kotlin
        root.addView(button("Notepad ও Clipboard Memory") {
            startActivity(Intent(this, NotepadActivity::class.java))
        })

```

(এই file-এ `android.content.Intent` আগে থেকেই import করা আছে, তাই আলাদা import লাগবে না।)

---

## এরপর কী হবে

- টাইপ করার সময় স্ক্রিনের উপরের strip-এ (যেখানে এখন composing text দেখায়) এখন পর্যন্ত
  শেখা/মিল থাকা শব্দ suggestion হিসেবে দেখাবে — tap করলে সেটা বসে যাবে।
- space দেওয়ার পর, আগে যে শব্দের পরে যে শব্দ লিখেছেন তার ভিত্তিতে next-word suggestion
  আসবে (ব্যবহার যত বাড়বে, suggestion তত ভালো হবে — কোনো বড় dictionary bundle করা হয়নি)।
- কিবোর্ডের নিচের row-এ 📝 চাপলে Notepad panel খুলবে (সেভ করা note + সাম্প্রতিক
  clipboard item-এর বাটন), যেকোনোটা চাপলে সরাসরি বসে যাবে; "‹ কিবোর্ডে ফিরুন" চাপলে
  normal keyboard-এ ফিরে আসবে।
- "Tr" চাপলে এখন যা compose হচ্ছে (বা selected text) বাংলা <-> English translate হয়ে
  বসে যাবে (internet লাগবে)।
- App-এর Settings screen থেকে "Notepad ও Clipboard Memory" বাটনে note add/remove করা
  এবং clipboard history দেখা/copy/delete করা যাবে।

## মনে রাখার মতো বিষয়

- Translator ফ্রি MyMemory API ব্যবহার করে, heavy/continuous ব্যবহারে rate-limit
  খেতে পারে — production-এ নিজের API key দেওয়া translation service বসালে ভালো।
- `Suggestions` শুরুতে প্রায় খালি থাকবে; আপনি নিজে যত টাইপ করবেন তত ভালো suggestion
  দিবে। চাইলে `SEED_ROMAN_BN` ম্যাপে আরও common শব্দ যোগ করতে পারেন (`Suggestions.kt`)।
- সব data (notes, clipboard history, learned suggestions) শুধু ফোনের local
  SharedPreferences-এ থাকে, কোথাও পাঠানো হয় না। শুধু Translator অনুবাদ করার সময়
  সেই নির্দিষ্ট text MyMemory API-তে পাঠায়।
