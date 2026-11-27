package com.banglakeys.app.robot

/**
 * EMO রোবটের ৩২টি এক্সপ্রেশন:
 * - প্রথম ১২টি: ক্লাসিক চোখ-স্টাইল (Anki Vector / EMO Reference 1)
 * - পরের ২০টি: মাল্টিপল এক্সপ্রেসিভ মুখ-চোখ-অশ্রু-ব্লাশ রিঅ্যাকশন (Reference 2)
 *
 * [tag]: p=positive, n=negative, u=neutral, f=fun, r=rare, s=sleep
 */
enum class FaceId(val bn: String, val tag: Char) {
    // Classic 12
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

    // Expressive 20
    PLEAD("অনুনয়", 'n'),
    SMUG("দুষ্টুমি", 'f'),
    SWIRL("চক্কর", 'r'),
    MISCHIEF("দুষ্টু হাসি", 'f'),
    WINK("চোখ মারা", 'p'),
    SWEAT("হতাশ", 'n'),
    FURIOUS("প্রচণ্ড রাগ", 'n'),
    CRY_RIVER("ঝরঝর কান্না", 'n'),
    HYPED("উত্তেজিত", 'p'),
    HMPH("অভিমান", 'n'),
    GRUMPY("গোমড়া", 'n'),
    UWU("কিউট", 'f'),
    GIGGLE("খিলখিল হাসি", 'p'),
    SHOCKED("অবাক", 'r'),
    NERVOUS("নার্ভাস", 'n'),
    KISS_SHY("চুমু", 'p'),
    SHOUT("চিৎকার", 'n'),
    WINK_KISS("ফ্লাইং কিস", 'p'),
    BORED("বিরক্ত", 'n'),
    BEAMING("প্রাণখোলা হাসি", 'p');

    val isExpressive: Boolean
        get() = ordinal >= 12

    companion object {
        val CLASSIC_12: List<FaceId> get() = entries.subList(0, 12)
        val EXPRESSIVE_20: List<FaceId> get() = entries.subList(12, entries.size)
    }
}
