# Key Effects (animation) — v3.2

## কী যোগ হয়েছে
| ফাইল | কাজ |
|---|---|
| `KeyEffects.kt` | Press effect (RIPPLE / GLOW / FLASH) + popup "pop" animation। শুধু overlay |
| `EffectsConfig.kt` | Level (0 বন্ধ · 1 Light · 2 Normal · 3 Rich), kill switch, guard স্ট্যাটাস |
| `PerfRecorder.kt` | onDraw সময় ও touch→draw latency রেকর্ড, auto-guard, লগ ফাইল |
| `KeyboardView.kt` | ৬টা ছোট hook (নিচে) |
| `KeyboardTheme.kt` | ঐচ্ছিক `effect` ফিল্ড — নতুন theme-এ আলাদা effect দিতে |
| `BanglaKeysScreen.kt` | Settings-এ "Key effects" + "Rich" toggle |

## Theme অনুযায়ী default
NEON → GLOW · PIXEL/BLOCK → FLASH · বাকি সব → RIPPLE। বদলাতে theme-এ `effect = KeyEffects.Kind.GLOW` লিখুন।

## Typing নিরাপত্তা (কেন আগের মতোই 100% accurate/fast)
1. Hit-test, key আকার, listener, commit পথ — **কিছুই বদলায়নি**। Effect শুধু আঁকা হয়।
2. Effect শুরু হয় `onFeedback()`-এর **পরে**; key commit-এর আগে কোনো কাজ নেই।
3. draw-এ কোনো object তৈরি হয় না (fixed slot, পূর্বনির্ধারিত Paint) → GC jank নেই।
4. Default = Light (১৫০ms, কম opacity)। একসাথে সর্বোচ্চ ৬টা effect।
5. **Auto-guard:** টানা দু'বার (প্রতিটি ৯০ frame) avg draw > 7ms বা p95 > 12ms বা p95 latency > 45ms হলে effects নিজে বন্ধ, Settings-এ কারণ দেখায়।

## KeyboardView-এ হুক (সব মিলিয়ে ~২৫ লাইন)
`fx` ফিল্ড · `applyTheme()` শেষে kind/level · `onDraw` (সময় মাপা + overlay) · `drawPopups(now)` (scale) ·
`onDown` (markDown + fx.onPress) · `onWindowVisibilityChanged/onDetachedFromWindow`।
