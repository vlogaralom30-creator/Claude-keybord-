# Keyboard Themes

## নতুন theme যোগ করার নিয়ম
`KeyboardTheme.kt`-এর `Themes.all` list-এ একটা `KeyboardTheme(...)` যোগ করুন — আর কিছু বদলাতে হবে না।
Picker-এ card আসবে, keyboard-ও সেই theme আঁকবে।

## Style গুলো
| KeyStyle | চেহারা |
|---|---|
| FLAT | সাধারণ rounded key, optional border/shadow |
| PIXEL | retro pixel — মোটা outline + bevel (monoFont = true দিলে pixel-ish লেখা) |
| BLOCK | উপরে ঘাসের স্তর, নিচে মাটি (keyTop = ঘাসের রঙ) |
| NEON | glow outline (border = neon রঙ) |
| GLASS | স্বচ্ছ key (bgTop/bgBottom gradient background-এর সাথে ভালো লাগে) |
| GRADIENT | key-র ভেতরে keyTop → keyBottom gradient |

## My Photo theme
Picker-এ "🖼️ My Photo" চাপলে gallery খুলবে। ছবি app-এর ভেতরে ছোট করে রাখা হয়,
Key স্বচ্ছতা আর ছবির উপর অন্ধকার slider দিয়ে ঠিক করা যায়।

## কোথায় কী সংরক্ষিত
- নির্বাচিত theme: SharedPreferences "bk" → `theme`
- ছবি: app-এর `filesDir/kb_theme_bg.jpg`
