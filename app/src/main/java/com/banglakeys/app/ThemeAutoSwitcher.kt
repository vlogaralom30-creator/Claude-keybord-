package com.banglakeys.app

import android.content.Context
import java.util.Calendar
import java.util.TimeZone

/**
 * Automated keyboard theme switcher।
 * সময় (সকাল/দুপুর/সন্ধ্যা/রাত) অথবা ব্যবহারের ঘনত্ব (কিবোর্ড খোলা বা key চাপার সংখ্যা) অনুযায়ী
 * যোগ করা থিমগুলোর মধ্যে স্বয়ংক্রিয়ভাবে পরিবর্তন করে।
 */
object ThemeAutoSwitcher {

    enum class Mode(val prefValue: String) {
        OFF("off"),
        TIME_OF_DAY("time_of_day"),
        USAGE_FREQUENCY("usage_frequency");

        companion object {
            fun fromPref(v: String?): Mode =
                entries.firstOrNull { it.prefValue == v } ?: OFF
        }
    }

    enum class TimeSlot(val labelBn: String, val rangeLabel: String, val prefersDark: Boolean) {
        MORNING("সকাল (Morning)", "6am–12pm", false),
        AFTERNOON("দুপুর (Afternoon)", "12pm–5pm", false),
        EVENING("সন্ধ্যা (Evening)", "5pm–9pm", true),
        NIGHT("রাত (Night)", "9pm–6am", true);

        companion object {
            fun forHour(hour24: Int): TimeSlot = when (hour24.mod(24)) {
                in 6..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..20 -> EVENING
                else -> NIGHT
            }
        }
    }

    enum class UsageMetric(val prefValue: String) {
        SESSIONS("sessions"),
        KEYSTROKES("keystrokes");

        companion object {
            fun fromPref(v: String?): UsageMetric =
                entries.firstOrNull { it.prefValue == v } ?: SESSIONS
        }
    }

    enum class UsageCadence(
        val id: String,
        val metric: UsageMetric,
        val threshold: Int,
        val label: String,
    ) {
        OPENS_5("opens_5", UsageMetric.SESSIONS, 5, "প্রতি ৫ বার খুললে"),
        OPENS_15("opens_15", UsageMetric.SESSIONS, 15, "প্রতি ১৫ বার খুললে"),
        KEYS_100("keys_100", UsageMetric.KEYSTROKES, 100, "প্রতি ১০০ কী-তে"),
        KEYS_300("keys_300", UsageMetric.KEYSTROKES, 300, "প্রতি ৩০০ কী-তে");

        companion object {
            fun fromId(id: String?): UsageCadence =
                entries.firstOrNull { it.id == id } ?: OPENS_5
        }
    }

    private const val PREFS = "bk"
    private const val K_MODE = "theme_auto_mode"
    private const val K_CADENCE = "theme_auto_cadence"
    private const val K_POOL = "theme_auto_pool"
    private const val K_ROTATION_IDX = "theme_auto_idx"
    private const val K_USAGE_PROGRESS = "theme_auto_usage_progress"
    private const val K_LAST_SLOT_KEY = "theme_auto_last_slot"
    private const val K_SLOT_PREFIX = "theme_auto_slot_idx_"
    private const val K_COUNT_PREFIX = "theme_auto_cnt_"

    private val MORNING_ORDER = listOf(
        "warm_clay", "sakura", "aurora_light", "glass_frost", "manga_ink", "violet_ink",
    )
    private val AFTERNOON_ORDER = listOf(
        "glass_frost", "manga_ink", "warm_clay", "aurora_light", "violet_ink", "teal_noir",
    )
    private val EVENING_ORDER = listOf(
        "sunset_glass", "ocean_glass", "violet_ink", "teal_noir", "forest_night", "grass_blocks", "sticker_night",
    )
    private val NIGHT_ORDER = listOf(
        Themes.EMO_ID, "amoled", "neon_cyber", "mint_dark", "graphite", "teal_noir", "pixel_arcade", "forest_night", "ocean_glass",
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getMode(ctx: Context): Mode = Mode.fromPref(prefs(ctx).getString(K_MODE, Mode.OFF.prefValue))

    fun setMode(ctx: Context, mode: Mode) {
        prefs(ctx).edit().putString(K_MODE, mode.prefValue).apply()
    }

    fun getCadence(ctx: Context): UsageCadence =
        UsageCadence.fromId(prefs(ctx).getString(K_CADENCE, UsageCadence.OPENS_5.id))

    fun setCadence(ctx: Context, cadence: UsageCadence) {
        val sp = prefs(ctx)
        val curProgress = sp.getInt(K_USAGE_PROGRESS, 0).coerceIn(0, (cadence.threshold - 1).coerceAtLeast(0))
        sp.edit()
            .putString(K_CADENCE, cadence.id)
            .putInt(K_USAGE_PROGRESS, curProgress)
            .apply()
    }

    /** Rotation-এ অংশ নিতে পারে এমন সব যোগ করা থিম। */
    fun eligibleThemes(ctx: Context): List<KeyboardTheme> {
        val hasCustom = ThemeStore.hasCustomImage(ctx)
        return Themes.all.filter { t ->
            t.id != Themes.AUTO_ID && (!t.usesImage || hasCustom)
        }
    }

    /** ব্যবহারকারীর বাছা থিম পুল; খালি হলে সব যোগ করা থিম ফেরে। */
    fun getPoolIds(ctx: Context): List<String> {
        val eligible = eligibleThemes(ctx).map { it.id }
        val eligibleSet = eligible.toSet()
        val raw = prefs(ctx).getString(K_POOL, null)
        if (raw.isNullOrBlank()) return eligible
        val parsed = raw.split(',').map { it.trim() }.filter { it in eligibleSet }.distinct()
        return parsed.ifEmpty { eligible }
    }

    fun setPoolIds(ctx: Context, ids: Collection<String>) {
        val eligible = eligibleThemes(ctx).map { it.id }.toSet()
        val clean = ids.filter { it in eligible }.distinct()
        val editor = prefs(ctx).edit()
        if (clean.isEmpty() || clean.size == eligible.size) {
            editor.remove(K_POOL)
        } else {
            editor.putString(K_POOL, clean.joinToString(","))
        }
        editor.apply()
    }

    fun toggleThemeInPool(ctx: Context, themeId: String): List<String> {
        val current = getPoolIds(ctx).toMutableList()
        if (themeId in current) {
            // অন্তত ১টি থিম পুলে রাখতে হবে
            if (current.size > 1) {
                current.remove(themeId)
            }
        } else {
            val order = eligibleThemes(ctx).map { it.id }
            val nextSet = (current + themeId).toSet()
            current.clear()
            current.addAll(order.filter { it in nextSet })
        }
        setPoolIds(ctx, current)
        return current
    }

    fun usageProgress(ctx: Context): Int = prefs(ctx).getInt(K_USAGE_PROGRESS, 0).coerceAtLeast(0)

    fun themeUseCount(ctx: Context, themeId: String): Int =
        prefs(ctx).getInt("$K_COUNT_PREFIX$themeId", 0).coerceAtLeast(0)

    fun mostUsedTheme(ctx: Context): Pair<KeyboardTheme, Int>? {
        val sp = prefs(ctx)
        var best: KeyboardTheme? = null
        var bestCount = 0
        for (t in eligibleThemes(ctx)) {
            val c = sp.getInt("$K_COUNT_PREFIX${t.id}", 0)
            if (c > bestCount) {
                bestCount = c
                best = t
            }
        }
        return if (best != null && bestCount > 0) best to bestCount else null
    }

    /**
     * সময়ের স্তর অনুযায়ী পুলে থাকা থিমগুলোর সাজানো তালিকা।
     * আগে সময়ের সাথে মানানসই থিম, তারপর পুলের বাকি থিম থাকে।
     */
    fun themesForTimeSlot(ctx: Context, slot: TimeSlot): List<KeyboardTheme> {
        val poolIds = getPoolIds(ctx).toSet()
        val byId = eligibleThemes(ctx).filter { it.id in poolIds }.associateBy { it.id }
        if (byId.isEmpty()) return listOf(Themes.lightDefault)

        val preferredOrder = when (slot) {
            TimeSlot.MORNING -> MORNING_ORDER
            TimeSlot.AFTERNOON -> AFTERNOON_ORDER
            TimeSlot.EVENING -> EVENING_ORDER
            TimeSlot.NIGHT -> NIGHT_ORDER
        }
        val preferred = ArrayList<KeyboardTheme>(byId.size)
        val seen = HashSet<String>()
        for (id in preferredOrder) {
            val t = byId[id]
            if (t != null && seen.add(id)) preferred.add(t)
        }
        // পুলের বাকি থিম: একই light/dark প্রবণতার থিম আগে রাখি
        val remaining = byId.values.filter { it.id !in seen }
        val sameTone = remaining.filter { it.dark == slot.prefersDark }
        val oppositeTone = remaining.filter { it.dark != slot.prefersDark }
        preferred.addAll(sameTone)
        if (preferred.isEmpty()) {
            preferred.addAll(oppositeTone)
        }
        return preferred
    }

    /**
     * বর্তমান মোড অনুযায়ী থিম ID নির্ধারণ করে।
     * `Mode.OFF` হলে `null` ফেরে — তখন `ThemeStore.selectedId` ব্যবহার হয়।
     */
    fun resolveThemeId(ctx: Context, nowMs: Long = System.currentTimeMillis()): String? {
        val mode = getMode(ctx)
        if (mode == Mode.OFF) return null
        val pool = getPoolIds(ctx)
        if (pool.isEmpty()) return null

        return when (mode) {
            Mode.OFF -> null
            Mode.TIME_OF_DAY -> resolveForTime(ctx, nowMs)
            Mode.USAGE_FREQUENCY -> {
                val idx = prefs(ctx).getInt(K_ROTATION_IDX, 0).mod(pool.size)
                pool[idx]
            }
        }
    }

    private fun resolveForTime(ctx: Context, nowMs: Long): String {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        val slot = TimeSlot.forHour(hour)
        val slotKey = "${dayKey}_${slot.name}"

        val candidates = themesForTimeSlot(ctx, slot)
        val sp = prefs(ctx)
        val lastSlotKey = sp.getString(K_LAST_SLOT_KEY, null)
        val slotPrefKey = "$K_SLOT_PREFIX${slot.name}"
        var slotIdx = sp.getInt(slotPrefKey, 0)

        if (lastSlotKey != null && lastSlotKey != slotKey) {
            slotIdx = (slotIdx + 1) % candidates.size
            sp.edit()
                .putString(K_LAST_SLOT_KEY, slotKey)
                .putInt(slotPrefKey, slotIdx)
                .apply()
        } else if (lastSlotKey == null) {
            sp.edit().putString(K_LAST_SLOT_KEY, slotKey).apply()
        }

        return candidates[slotIdx.mod(candidates.size)].id
    }

    /** কিবোর্ড খুললে (new input session) ডাকা হয়। থিম বদলালে true ফেরে। */
    fun onKeyboardOpen(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val sp = prefs(ctx)
        val currentResolved = resolveThemeId(ctx, nowMs) ?: ThemeStore.resolve(ctx).id
        val countKey = "$K_COUNT_PREFIX$currentResolved"
        sp.edit().putInt(countKey, sp.getInt(countKey, 0) + 1).apply()

        if (getMode(ctx) != Mode.USAGE_FREQUENCY) return false
        val cadence = getCadence(ctx)
        if (cadence.metric != UsageMetric.SESSIONS) return false
        return advanceUsageProgress(ctx, cadence)
    }

    /** Key চাপলে ডাকা হয়। Keystroke threshold পূর্ণ হলে পরের থিমে যায় ও true ফেরে। */
    fun onKeyTyped(ctx: Context): Boolean {
        if (getMode(ctx) != Mode.USAGE_FREQUENCY) return false
        val cadence = getCadence(ctx)
        if (cadence.metric != UsageMetric.KEYSTROKES) return false
        return advanceUsageProgress(ctx, cadence)
    }

    private fun advanceUsageProgress(ctx: Context, cadence: UsageCadence): Boolean {
        val pool = getPoolIds(ctx)
        if (pool.isEmpty()) return false
        val sp = prefs(ctx)
        val nextProgress = sp.getInt(K_USAGE_PROGRESS, 0) + 1
        return if (nextProgress >= cadence.threshold) {
            val nextIdx = (sp.getInt(K_ROTATION_IDX, 0) + 1) % pool.size
            sp.edit()
                .putInt(K_USAGE_PROGRESS, 0)
                .putInt(K_ROTATION_IDX, nextIdx)
                .apply()
            pool.size > 1
        } else {
            sp.edit().putInt(K_USAGE_PROGRESS, nextProgress).apply()
            false
        }
    }

    /** Settings থেকে তাৎক্ষণিক পরের থিমে ঘোরানোর জন্য ("Rotate Now")। */
    fun rotateNow(ctx: Context, nowMs: Long = System.currentTimeMillis()): KeyboardTheme {
        val mode = getMode(ctx)
        val sp = prefs(ctx)
        when (mode) {
            Mode.TIME_OF_DAY -> {
                val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = nowMs }
                val slot = TimeSlot.forHour(cal.get(Calendar.HOUR_OF_DAY))
                val candidates = themesForTimeSlot(ctx, slot)
                val slotPrefKey = "$K_SLOT_PREFIX${slot.name}"
                val nextIdx = (sp.getInt(slotPrefKey, 0) + 1) % candidates.size
                val slotKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}_${slot.name}"
                sp.edit()
                    .putString(K_LAST_SLOT_KEY, slotKey)
                    .putInt(slotPrefKey, nextIdx)
                    .apply()
            }
            Mode.USAGE_FREQUENCY, Mode.OFF -> {
                val pool = getPoolIds(ctx)
                if (pool.isNotEmpty()) {
                    val nextIdx = (sp.getInt(K_ROTATION_IDX, 0) + 1) % pool.size
                    sp.edit()
                        .putInt(K_ROTATION_IDX, nextIdx)
                        .putInt(K_USAGE_PROGRESS, 0)
                        .apply()
                }
            }
        }
        return ThemeStore.resolve(ctx)
    }

    /** Settings-এ দেখানোর জন্য বর্তমান অবস্থা (যেমন "রাত (9pm–6am) • AMOLED" বা "2 / 5 opens • Sakura")। */
    fun statusSummary(ctx: Context, nowMs: Long = System.currentTimeMillis()): String {
        val resolved = ThemeStore.resolve(ctx)
        return when (getMode(ctx)) {
            Mode.OFF -> "বন্ধ — ম্যানুয়ালি থিম: ${resolved.emoji} ${resolved.name}"
            Mode.TIME_OF_DAY -> {
                val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = nowMs }
                val slot = TimeSlot.forHour(cal.get(Calendar.HOUR_OF_DAY))
                "${slot.labelBn} (${slot.rangeLabel}) → ${resolved.emoji} ${resolved.name}"
            }
            Mode.USAGE_FREQUENCY -> {
                val cadence = getCadence(ctx)
                val prog = usageProgress(ctx)
                val unit = if (cadence.metric == UsageMetric.SESSIONS) "বার খোলা" else "কী"
                "${resolved.emoji} ${resolved.name} • পরবর্তী পরিবর্তন: $prog/${cadence.threshold} $unit"
            }
        }
    }
}
