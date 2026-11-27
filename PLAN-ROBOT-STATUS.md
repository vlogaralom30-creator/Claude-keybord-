# Mini Robot Keyboard — Status Update (v2: Phase 1 complete)

(আগের zip-এর PLAN-ROBOT-STATUS.md-এর continuation — Open questions-এর উত্তর ও
"UI: plain Canvas, Compose না" course-correction অপরিবর্তিত আছে, সেগুলো আবার
লেখা হলো না, আগের zip-এ পড়ুন।)

## Phase 1 — এখন ✅ সম্পূর্ণ

- [x] ১২টা face (vector/HD, blink সহ)
- [x] **Brain/mood state machine** (`RobotBrain.kt`): needs (energy/bond/fun),
      idle picker (tag-weighted, repeat-এড়ানো), interrupt rule, sleep/wake
      cycle, ৫-সেকেন্ড speech rate-limit
- [x] **Event bus** (`RobotEvent.kt`): plan-এর সব ১৯টা event type, এখনো fake
      button থেকে আসছে (আসল sensor/typing Phase 2/3-এ)
- [x] **Fake event বাটন দিয়ে টেস্ট** (`RobotFaceDemoActivity.kt`) — plan-এর Phase 1
      checklist পুরোপুরি মেলে: "shob face thik dekhay kina" (✓, আগের zip) + "brain
      mood bodlay kina" (✓, এই zip)
- [x] Sleep/wake cycle **Python-এ simulate করে verify করা হয়েছে** (bug পাওয়া গিয়ে
      ঠিক করা হয়েছে — উপরে SETUP-ROBOT.md-এ বিস্তারিত) — আসল ফোনে build করার আগেই
      logic-টা সঠিক কিনা নিশ্চিত হওয়া গেছে
- [ ] `resetMemory()` আছে কিন্তু persist (DataStore/SharedPreferences) করা হয়নি —
      app বন্ধ করলে needs/bond রিসেট হয়ে যাবে। এটা Phase 2/3-এর সাথে যোগ করাই
      ভালো (তখন আসল persistent storage লাগবে)

## পরের ধাপ (plan.md-এর Phase 2)

Real sensor (accelerometer/light/proximity), battery broadcast, mic, আর
`PermissionActivity` (keyboard service থেকে dialog দেখানো যায় না বলে)। এটা
আসল ফোনের hardware/permission নিয়ে কাজ, তাই সবচেয়ে ভালো হয় যদি আপনি Phase 1-এর
এই ভার্সনটা আগে real ফোনে build করে কিছুক্ষণ চালিয়ে দেখেন (speech bubble ঠিক
জায়গায় বসছে কিনা, বাটনের size/layout ঠিক লাগছে কিনা) — তারপর Phase 2 শুরু করব,
যাতে ছোটখাটো UI সমস্যা আগেই ধরা পড়ে।
