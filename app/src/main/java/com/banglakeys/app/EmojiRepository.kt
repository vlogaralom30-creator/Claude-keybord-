package com.banglakeys.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.BreakIterator

enum class EmojiCategory(val id: String, val icon: String, val title: String) {
    RECENT("recent", "🕒", "Recent • সাম্প্রতিক"),
    SMILEYS("smileys", "😀", "Smileys & Emotion"),
    PEOPLE("people", "👋", "People & Body"),
    NATURE("nature", "🐻", "Animals & Nature"),
    FOOD("food", "🍔", "Food & Drink"),
    TRAVEL("travel", "✈️", "Travel & Places"),
    ACTIVITIES("activities", "⚽", "Activities"),
    OBJECTS("objects", "💡", "Objects"),
    SYMBOLS_FLAGS("symbols", "❤️", "Symbols & Flags"),
}

/**
 * Curated categorized emoji catalog + local persistence for Recent Emojis
 * and remembered skin-tone variants.
 */
class EmojiRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val recentList: MutableList<String> = ArrayList()
    private val skinToneMap: MutableMap<String, String> = LinkedHashMap()

    init {
        reload()
    }

    fun reload() {
        recentList.clear()
        skinToneMap.clear()
        val recentRaw = prefs.getString(KEY_RECENTS, null)
        if (!recentRaw.isNullOrBlank()) {
            try {
                val arr = JSONArray(recentRaw)
                for (i in 0 until arr.length()) {
                    val e = arr.optString(i)
                    if (e.isNotEmpty() && !recentList.contains(e)) recentList.add(e)
                }
            } catch (_: Exception) {
            }
        }
        val tonesRaw = prefs.getString(KEY_TONES, null)
        if (!tonesRaw.isNullOrBlank()) {
            try {
                val obj = JSONObject(tonesRaw)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optString(k)
                    if (k.isNotEmpty() && v.isNotEmpty()) skinToneMap[k] = v
                }
            } catch (_: Exception) {
            }
        }
    }

    fun recents(): List<String> = recentList.toList()

    fun hasRecents(): Boolean = recentList.isNotEmpty()

    fun recordRecent(emoji: String) {
        val clean = emoji.trim()
        if (clean.isEmpty()) return
        recentList.remove(clean)
        recentList.add(0, clean)
        while (recentList.size > MAX_RECENTS) recentList.removeAt(recentList.size - 1)
        saveRecents()
    }

    fun clearRecents() {
        recentList.clear()
        saveRecents()
    }

    fun emojisFor(category: EmojiCategory): List<String> {
        if (category == EmojiCategory.RECENT) return recents()
        val raw = CATALOG[category].orEmpty()
        if (category != EmojiCategory.PEOPLE) return raw
        return raw.map { base -> preferredEmoji(base) }
    }

    fun preferredEmoji(emoji: String): String {
        val base = stripToneModifiers(emoji)
        return skinToneMap[base] ?: emoji
    }

    fun setPreferredSkinTone(emoji: String, variant: String) {
        val base = stripToneModifiers(emoji)
        if (variant == base) {
            skinToneMap.remove(base)
        } else {
            skinToneMap[base] = variant
        }
        saveTones()
    }

    fun supportsSkinTone(emoji: String): Boolean {
        val stripped = stripToneModifiers(emoji)
        return stripped in SKIN_TONE_BASES
    }

    fun skinToneVariants(emoji: String): List<String> {
        val base = stripToneModifiers(emoji)
        if (base !in SKIN_TONE_BASES) return emptyList()
        val stem = if (base.endsWith(VARIATION_SELECTOR)) base.dropLast(1) else base
        return buildList(6) {
            add(base)
            for (tone in SKIN_TONE_MODIFIERS) {
                add(stem + tone)
            }
        }
    }

    private fun saveRecents() {
        val arr = JSONArray()
        for (e in recentList) arr.put(e)
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    private fun saveTones() {
        val obj = JSONObject()
        for ((k, v) in skinToneMap) obj.put(k, v)
        prefs.edit().putString(KEY_TONES, obj.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "bk_emoji"
        private const val KEY_RECENTS = "recent_emojis"
        private const val KEY_TONES = "skin_tones"
        private const val MAX_RECENTS = 40
        private const val VARIATION_SELECTOR = '\uFE0F'

        private val SKIN_TONE_MODIFIERS = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

        fun stripToneModifiers(emoji: String): String {
            var out = emoji
            for (tone in SKIN_TONE_MODIFIERS) {
                out = out.replace(tone, "")
            }
            if (out in VARIATION_RESTORE_MAP) {
                return VARIATION_RESTORE_MAP.getValue(out)
            }
            return out
        }

        fun deleteLastGrapheme(text: String): String {
            if (text.isEmpty()) return ""
            val it = BreakIterator.getCharacterInstance()
            it.setText(text)
            val end = it.last()
            val prev = it.previous()
            if (prev == BreakIterator.DONE || prev < 0 || end <= prev) {
                return text.dropLast(1)
            }
            return text.substring(0, prev)
        }

        private val VARIATION_RESTORE_MAP = mapOf(
            "✌" to "✌️",
            "☝" to "☝️",
            "🖐" to "🖐️",
            "✍" to "✍️",
            "🏋" to "🏋️",
            "⛹" to "⛹️",
            "🕵" to "🕵️",
        )

        private val SKIN_TONE_BASES = setOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘",
            "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛",
            "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🙏", "✍️", "💅", "🤳", "💪", "🦵",
            "🦶", "👂", "🦻", "👃", "👶", "🧒", "👦", "👧", "🧑", "👨", "👩", "🧓",
            "👴", "👵", "👮", "🕵️", "💂", "👷", "🤴", "👸", "👳", "👲", "🧕", "🤵",
            "👰", "🤰", "🤱", "👼", "🎅", "🤶", "🦸", "🦹", "🧙", "🧚", "🧛", "🧜",
            "🧝", "💆", "💇", "🚶", "🏃", "💃", "🕺", "🧖", "🧗", "🏋️", "⛹️", "🏊",
            "🚴", "🛌", "🛀",
        )

        private val CATALOG: Map<EmojiCategory, List<String>> = mapOf(
            EmojiCategory.SMILEYS to listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
                "🙂", "🙃", "🫠", "😉", "😊", "😇", "🥰", "😍",
                "🤩", "😘", "😗", "☺️", "😚", "😙", "🥲", "😋",
                "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🫢",
                "🫣", "🤫", "🤔", "🫡", "🤐", "🤨", "😐", "😑",
                "😶", "🫥", "😏", "😒", "🙄", "😬", "🤥", "😌",
                "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢",
                "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠",
                "🥳", "🥸", "😎", "🤓", "🧐", "😕", "🫤", "😟",
                "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺", "🥹",
                "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱",
                "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤",
                "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩",
                "🤡", "👹", "👺", "👻", "👽", "👾", "🤖", "😺",
                "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾",
                "💋", "💌", "💘", "💝", "💖", "💗", "💓", "💞",
                "💕", "💟", "❣️", "💔", "❤️", "🩷", "🧡", "💛",
                "💚", "💙", "🩵", "💜", "🤎", "🖤", "🩶", "🤍",
                "💯", "💢", "💥", "💫", "💦", "💨", "🕳️", "💬",
                "💭", "💤",
            ),
            EmojiCategory.PEOPLE to listOf(
                "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏",
                "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
                "👆", "🖕", "👇", "☝️", "🫵", "👍", "👎", "✊",
                "👊", "🤛", "🤜", "👏", "🙌", "🫶", "👐", "🤲",
                "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦵",
                "🦶", "👂", "🦻", "👃", "🧠", "🫀", "🫁", "🦷",
                "🦴", "👀", "👁️", "👅", "👄", "🫦", "👶", "🧒",
                "👦", "👧", "🧑", "👨", "👩", "🧓", "👴", "👵",
                "👮", "🕵️", "💂", "👷", "🤴", "👸", "👳", "👲",
                "🧕", "🤵", "👰", "🤰", "🤱", "👼", "🎅", "🤶",
                "🦸", "🦹", "🧙", "🧚", "🧛", "🧜", "🧝", "🧞",
                "🧟", "💆", "💇", "🚶", "🏃", "💃", "🕺", "👯",
                "🧖", "🧗", "🏋️", "⛹️", "🏊", "🚴", "🛌", "🛀",
                "🗣️", "👤", "👥", "🫂", "👪", "🧑‍🤝‍🧑", "👫", "👬",
                "👭",
            ),
            EmojiCategory.NATURE to listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
                "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸",
                "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦",
                "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺",
                "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌",
                "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️",
                "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐",
                "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋",
                "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🐘",
                "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🐃", "🐂",
                "🐄", "🐎", "🐖", "🐏", "🐑", "🦙", "🐐", "🦌",
                "🐕", "🐩", "🐈", "🐓", "🦚", "🦜", "🦢", "🦩",
                "🕊️", "🐇", "🦝", "🦨", "🦡", "🦦", "🦥", "🐁",
                "🐀", "🐿️", "🦔", "🐾", "🐉", "🐲", "🌵", "🎄",
                "🌲", "🌳", "🌴", "🌱", "🌿", "☘️", "🍀", "🎍",
                "🪴", "🎋", "🍃", "🍂", "🍁", "🍄", "🌾", "💐",
                "🌷", "🌹", "🥀", "🌺", "🌸", "🌼", "🌻", "🌞",
                "🌝", "🌛", "🌜", "🌚", "🌕", "🌖", "🌗", "🌘",
                "🌑", "🌒", "🌓", "🌔", "🌙", "🌎", "🌍", "🌏",
                "🪐", "💫", "⭐", "🌟", "✨", "⚡", "☄️", "💥",
                "🔥", "🌪️", "🌈", "☀️", "🌤️", "⛅", "🌥️", "☁️",
                "🌦️", "🌧️", "⛈️", "🌩️", "🌨️", "❄️", "☃️", "⛄",
                "🌬️", "💨", "💧", "💦", "☔", "☂️", "🌊", "🌫️",
            ),
            EmojiCategory.FOOD to listOf(
                "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
                "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
                "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️",
                "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠",
                "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳",
                "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🦴",
                "🌭", "🍔", "🍟", "🍕", "🫓", "🥪", "🥙", "🧆",
                "🌮", "🌯", "🫔", "🥗", "🥘", "🫕", "🥫", "🍝",
                "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤",
                "🍙", "🍚", "🍘", "🍥", "🥠", "🥮", "🍢", "🍡",
                "🍧", "🍨", "🍦", "🥧", "🧁", "🍰", "🎂", "🍮",
                "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰", "🥜",
                "🍯", "🥛", "🍼", "☕", "🫖", "🍵", "🧃", "🥤",
                "🧋", "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸",
                "🍹", "🧉", "🍾", "🧊", "🥄", "🍴", "🍽️", "🥣",
                "🥡", "🥢", "🧂",
            ),
            EmojiCategory.TRAVEL to listOf(
                "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
                "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🦯", "🦽",
                "🦼", "🛴", "🚲", "🛵", "🏍️", "🛺", "🚨", "🚔",
                "🚍", "🚘", "🚖", "🛞", "🚡", "🚠", "🚟", "🚃",
                "🚋", "🚞", "🚝", "🚄", "🚅", "🚈", "🚂", "🚆",
                "🚇", "🚊", "🚉", "✈️", "🛫", "🛬", "🛩️", "💺",
                "🛰️", "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛥️",
                "🛳️", "⛴️", "🚢", "⚓", "🛟", "⛽", "🚧", "🚦",
                "🚥", "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️",
                "🎡", "🎢", "🎠", "⛲", "⛱️", "🏖️", "🏝️", "🏜️",
                "🌋", "⛰️", "🏔️", "🗻", "🏕️", "⛺", "🛖", "🏠",
                "🏡", "🏘️", "🏚️", "🏗️", "🏭", "🏢", "🏬", "🏣",
                "🏤", "🏥", "🏦", "🏨", "🏪", "🏫", "🏩", "💒",
                "🏛️", "⛪", "🕌", "🕍", "🛕", "🕋", "⛩️", "🛤️",
                "🛣️", "🗾", "🎑", "🏞️", "🌅", "🌄", "🌠", "🎇",
                "🎆", "🌇", "🌆", "🏙️", "🌃", "🌌", "🌉", "🌁",
            ),
            EmojiCategory.ACTIVITIES to listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
                "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
                "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿",
                "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌",
                "🎿", "⛷️", "🏂", "🪂", "🏆", "🥇", "🥈", "🥉",
                "🏅", "🎖️", "🏵️", "🎗️", "🎫", "🎟️", "🎪", "🤹",
                "🎭", "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹",
                "🥁", "🪘", "🎷", "🎺", "🪗", "🎸", "🪕", "🎻",
                "🎲", "♟️", "🎯", "🎳", "🎮", "🎰", "🧩", "🪄",
                "🎉", "🎊", "🎈", "🎁", "🎀", "🪅", "🪩", "🧨",
                "✨", "🎃", "🎄", "🎆", "🎇", "🧧", "🎎", "🎏",
                "🎐", "🎑",
            ),
            EmojiCategory.OBJECTS to listOf(
                "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️",
                "🖲️", "🕹️", "🗜️", "💽", "💾", "💿", "📀", "📼",
                "📷", "📸", "📹", "🎥", "📽️", "🎞️", "📞", "☎️",
                "📟", "📠", "📺", "📻", "🎙️", "🎚️", "🎛️", "🧭",
                "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "🔋",
                "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸",
                "💵", "💴", "💶", "💷", "🪙", "💰", "💳", "💎",
                "⚖️", "🪜", "🧰", "🪛", "🔧", "🔨", "⚒️", "🛠️",
                "⛏️", "🪚", "🔩", "⚙️", "🪤", "🧱", "⛓️", "🧲",
                "🔫", "💣", "🧨", "🪓", "🔪", "🗡️", "⚔️", "🛡️",
                "🚬", "⚰️", "🪦", "⚱️", "🏺", "🔮", "📿", "🧿",
                "💈", "⚗️", "🔭", "🔬", "🕳️", "🩹", "🩺", "💊",
                "💉", "🩸", "🧬", "🦠", "🧫", "🧪", "🌡️", "🧹",
                "🪠", "🧺", "🧻", "🚽", "🚰", "🚿", "🛁", "🧼",
                "🪥", "🪒", "🧽", "🪣", "🧴", "🛎️", "🔑", "🗝️",
                "🚪", "🪑", "🛋️", "🛏️", "🛌", "🧸", "🪆", "🖼️",
                "🛍️", "🛒", "🎁", "🎈", "🎀", "📜", "📃", "📄",
                "📑", "🧾", "📊", "📈", "📉", "🗒️", "🗓️", "📆",
                "📅", "🗑️", "📇", "🗃️", "🗳️", "🗄️", "📋", "📁",
                "📂", "🗂️", "🗞️", "📰", "📓", "📔", "📒", "📕",
                "📗", "📘", "📙", "📚", "📖", "🔖", "🧷", "🔗",
                "📎", "🖇️", "📐", "📏", "🧮", "📌", "📍", "✂️",
                "🖊️", "🖋️", "✒️", "🖌️", "🖍️", "📝", "✏️", "🔍",
                "🔎", "🔏", "🔐", "🔒", "🔓",
            ),
            EmojiCategory.SYMBOLS_FLAGS to listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
                "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
                "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️",
                "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈",
                "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐",
                "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️", "☣️",
                "📴", "📳", "🆚", "💮", "🉐", "㊙️", "㊗️", "🈴",
                "🈵", "🈹", "🈲", "🅰️", "🅱️", "🆎", "🆑", "🅾️",
                "🆘", "❌", "⭕", "🛑", "⛔", "📛", "🚫", "💯",
                "💢", "♨️", "🚷", "🚯", "🚳", "🚱", "🔞", "📵",
                "🚭", "❗", "❕", "❓", "❔", "‼️", "⁉️", "🔅",
                "🔆", "〽️", "⚠️", "🚸", "🔱", "⚜️", "🔰", "♻️",
                "✅", "🈯", "💹", "❇️", "✳️", "❎", "🌐", "💠",
                "🌀", "💤", "🏧", "🚾", "♿", "🅿️", "🛂", "🛃",
                "🛄", "🛅", "🚹", "🚺", "🚼", "⚧️", "🚻", "🚮",
                "🎦", "📶", "🈁", "🔣", "ℹ️", "🔤", "🔡", "🔠",
                "🆖", "🆗", "🆙", "🆒", "🆕", "🆓", "0️⃣", "1️⃣",
                "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣",
                "🔟", "🔢", "#️⃣", "*️⃣", "⏏️", "▶️", "⏸️", "⏯️",
                "⏹️", "⏺️", "⏭️", "⏮️", "⏩", "⏪", "⏫", "⏬",
                "◀️", "🔼", "🔽", "➡️", "⬅️", "⬆️", "⬇️", "↗️",
                "↘️", "↙️", "↖️", "↕️", "↔️", "↪️", "↩️", "⤴️",
                "⤵️", "🔀", "🔁", "🔂", "🔄", "🔃", "🎵", "🎶",
                "➕", "➖", "➗", "✖️", "♾️", "💲", "💱", "™️",
                "©️", "®️", "〰️", "➰", "➿", "🔚", "🔙", "🔛",
                "🔝", "🔜", "✔️", "☑️", "🔘", "🔴", "🟠", "🟡",
                "🟢", "🔵", "🟣", "⚫", "⚪", "🟤", "🔺", "🔻",
                "🔸", "🔹", "🔶", "🔷", "🔳", "🔲", "▪️", "▫️",
                "◾", "◽", "◼️", "◻️", "🟥", "🟧", "🟨", "🟩",
                "🟦", "🟪", "⬛", "⬜", "🟫", "🔈", "🔇", "🔉",
                "🔊", "🔔", "🔕", "📣", "📢", "👁️‍🗨️", "💬", "💭",
                "🗯️", "♠️", "♣️", "♥️", "♦️", "🃏", "🎴", "🀄",
                "🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗",
                "🕘", "🕙", "🕚", "🕛", "🏳️", "🏴", "🏁", "🚩",
                "🏳️‍🌈", "🏳️‍⚧️", "🇧🇩", "🇮🇳", "🇵🇰", "🇸🇦", "🇦🇪", "🇬🇧",
                "🇺🇸", "🇨🇦", "🇦🇺", "🇩🇪", "🇫🇷", "🇯🇵", "🇰🇷", "🇨🇳",
                "🇹🇷", "🇲🇾", "🇸🇬", "🇮🇩", "🇧🇷", "🇮🇹", "🇪🇸", "🇶🇦",
            ),
        )
    }
}
