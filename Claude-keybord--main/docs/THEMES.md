# Keyboard Themes

## নতুন theme যোগ করার নিয়ম
`KeyboardTheme.kt`-এর `Themes.all` list-এ একটা `KeyboardTheme(...)` যোগ করুন — আর কিছু বদলাতে হবে না।
Picker-এ card আসবে, keyboard-ও সেই theme আঁকবে।

## Style গুলো
| KeyStyle | চেহারা |
|---|---|
| FLAT | সাধারণ rounded key, optional border/shadow |
| PIXEL | retro pixel — মোটা outline + bevel (monoFont = true দিলে pixel-ish লেখা) |
| BLOCK | উপরে ঘাস, নিচে মাটি (keyTop = ঘাসের রঙ) |
| NEON | glow outline (border = neon রঙ) |
| GLASS | স্বচ্ছ key (bgTop/bgBottom gradient background-এর সাথে ভালো লাগে) |
| GRADIENT | key-র ভেতরে keyTop → keyBottom gradient |

## My Photo theme
Picker-এ "🖼️ My Photo" চাপলে gallery খুলবে। ছবি app-এর ভেতরে ছোট করে রাখা হয়,
Key স্বচ্ছতা আর ছবির উপর অন্ধকার slider দিয়ে ঠিক করা যায়।

## কোথায় কী সংরক্ষিত
- নির্বাচিত theme: SharedPreferences "bk" → `theme`
- ছবি: app-এর `filesDir/kb_theme_bg.jpg`

## Built-in ছবির theme (base64)
`ThemeAssets.kt`-এ WebP ছবিগুলো base64 string হিসেবে আছে (মোট ~৩৬ KB), তাই আলাদা asset/drawable file লাগে না।
- `bgAsset = "teal_bg"` → theme-এর background ছবি
- `keyTiles = listOf("manga_t1", ...)` + `style = KeyStyle.TILE` → প্রতিটা key-তে ছোট ছবি
- `tileScrim` → tile-এর উপর স্বচ্ছ সাদা আবরণ (লেখা পড়ার সুবিধার জন্য)

নতুন ছবি যোগ করতে: ছোট WebP বানান (background ~360px, tile ~64x80px) → base64 করে `ThemeAssets.kt`-এ
`private const val` আর `map`-এ যোগ করুন। প্রতিটা string ৬০,০০০ অক্ষরের কম রাখবেন (JVM limit)।

নতুন ৩টা theme: Teal Noir, Manga Ink, Violet Eye।
