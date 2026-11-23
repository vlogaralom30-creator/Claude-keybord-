# `Layouts.kt` — 🔐 key যোগ করা (ছোট, নিরাপদ patch)

## enum-এ SECRET যোগ করুন

**Find** (আপনার বর্তমান enum লাইন — আগের patch-গুলো অনুযায়ী এটার মতো দেখতে হওয়ার কথা,
শেষে যা-ই থাকুক তার পরে `SECRET` জুড়ে দিন):
```kotlin
enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, SYMBOLS2, ABC, LANG, SPACER, SNIPPET, NOTEPAD, TRANSLATE, MIC, ASSISTANT }
```

**Replace with:**
```kotlin
enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, SYMBOLS2, ABC, LANG, SPACER, SNIPPET, NOTEPAD, TRANSLATE, MIC, ASSISTANT, SECRET }
```

(আপনার আসল এনাম লাইনটা একটু ভিন্ন হলেও, নিয়ম একই: শেষে `, SECRET` জুড়ে দিন।)

## Symbols layout-এর row-এ 🔐 key যোগ করুন

যেখানে 📝 (NOTEPAD) / Tr (TRANSLATE) / 🤖 (ASSISTANT) key-গুলো আছে (symbols layout-এর
প্রথম row, `?123` চাপলে যেখানে যাওয়া হয়), তার পাশে এটা যোগ করুন:
```kotlin
                Key("🔐", KeyType.SECRET, 1f),
```

## `KeyboardView.kt`

**Find:**
```kotlin
        fun onNotepad()
```
**এর আশেপাশে Listener interface-এ যোগ করুন:**
```kotlin
        fun onSecretToggle()
```

**Find** (`fire()`-এর ভেতর, অন্য KeyType handling-এর পাশে):
```kotlin
            KeyType.NOTEPAD -> l.onNotepad()
```
**এর নিচে যোগ করুন:**
```kotlin
            KeyType.SECRET -> l.onSecretToggle()
```

এরপর `BanglaIME.kt`-এ `onSecretToggle()` implement করার জন্য
`LIVE-COMPOSE-BAR-SPEC.md` দেখুন।
