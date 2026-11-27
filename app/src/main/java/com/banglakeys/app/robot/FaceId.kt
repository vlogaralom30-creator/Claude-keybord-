package com.banglakeys.app.robot

/**
 * mini-robot-keyboard-plan.md-এর "Set 1 (চোখ-style, ১২টা)" — শুধু দুটো চোখের
 * shape দিয়ে পুরো emotion বোঝানো (Anki Vector/robot-face ধরনের), কোনো আলাদা
 * মুখ/eyebrow নেই। আপনার দেওয়া reference ছবির ১২টা shape-এর সাথে মিলিয়ে বানানো।
 *
 * [tag]: p=positive, n=negative, u=neutral, f=fun, r=rare, s=sleep — plan-এর
 * Section 8 অনুযায়ী, Brain (Phase 1-এ এখনো বানানো হয়নি) mood বাছতে এটা ব্যবহার করবে।
 *
 * আমার শেপ-টু-নাম মিল আন্দাজে করা (ছবিতে কোনো লেখা ছিল না) — কোনোটা ভুল মনে হলে
 * বলবেন, শুধু নাম বদলানো এক লাইনের কাজ, শেপ পাল্টানো লাগবে না।
 */
enum class FaceId(val bn: String, val tag: Char) {
    SMILE("হাসি", 'p'),
    NORMAL("স্বাভাবিক", 'u'),
    SLY("চালাক", 'f'),
    ANGRY("রাগ", 'n'),
    CURIOUS("কৌতূহলী", 'u'),
    CALM("শান্ত", 'u'),
    REST("বিশ্রাম", 's'),
    JOY("আনন্দ", 'p'),
    SQUEEZE("চোখ বন্ধ", 'n'),
    DIZZY("মাথা ঘোরা", 'r'),
    STARE("তাকিয়ে আছে", 'u'),
    MEH("মেহ্‌", 'n'),
}
