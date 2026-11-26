# Rollback — effects-এ সমস্যা হলে (দ্রুত থেকে পূর্ণ)

1. **ব্যবহারকারী পর্যায়ে:** Settings → "Key effects" বন্ধ।
2. **কোডে এক লাইন:** `EffectsConfig.MASTER_ENABLED = false` → পুনরায় build। Effects কোথাও চলে না, বাকি সব আগের মতো।
3. **পূর্ণ ফেরত (আগের অবস্থায়):**
   ```
   cp docs/fx-backup/KeyboardView.kt.orig    app/src/main/java/com/banglakeys/app/KeyboardView.kt
   cp docs/fx-backup/KeyboardTheme.kt.orig   app/src/main/java/com/banglakeys/app/KeyboardTheme.kt
   cp docs/fx-backup/BanglaKeysScreen.kt.orig app/src/main/java/com/banglakeys/app/BanglaKeysScreen.kt
   rm app/src/main/java/com/banglakeys/app/{KeyEffects,EffectsConfig,PerfRecorder}.kt
   rm app/src/test/java/com/example/KeyEffectsTest.kt
   ```
   (`.orig` ফাইলগুলো **v3.1-এর অবস্থা**: translate ফিক্স সহ, effects ছাড়া। `BanglaKeysScreen.kt.orig`-এ Translator প্যাচ আছে।)
4. Git থাকলে: effects যোগের আগে `git tag v3.1-before-fx` দিয়ে রাখুন, `git revert` সহজ হবে।
