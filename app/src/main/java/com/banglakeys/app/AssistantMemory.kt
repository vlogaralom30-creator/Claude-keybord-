package com.banglakeys.app

import android.os.SystemClock

/**
 * স্বল্পমেয়াদি ভয়েস-সেশন মেমোরি (Short-Term Conversational & Screen Context Memory)।
 *
 * ইউজার আগের কমান্ডের পর "আরো বেশি", "আরেকটু নিচে", "আবার করো", "এটা সেন্ড করো", "এটা প্লে করো"
 * ধরনের follow-up বললে আগের অ্যাপ, শেষ স্ক্রল ডিরেকশন, শেষ সার্চ কোয়েরি ও দৃশ্যমান অবস্থা মনে রেখে
 * সঠিক কমান্ডে রূপান্তর করে।
 */
class AssistantMemory(
    private val ttlMs: Long = 90_000L,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() }
) {

    data class Snapshot(
        val lastCommand: Command? = null,
        val lastScrollDir: Dir? = null,
        val lastScrollScale: ScrollScale = ScrollScale.NORMAL,
        val lastForegroundPackage: String = "",
        val lastAppKey: String? = null,
        val lastSearchQuery: String? = null,
        val lastContact: String? = null,
        val lastTypedText: String? = null,
        val updatedAtMs: Long = 0L
    )

    private var state = Snapshot()

    @Synchronized
    fun snapshot(): Snapshot {
        expireIfNeeded()
        return state
    }

    @Synchronized
    fun clear() {
        state = Snapshot()
    }

    @Synchronized
    fun updateScreenContext(foregroundPackage: String) {
        val pkg = foregroundPackage.trim()
        if (pkg.isEmpty()) return
        val appKey = appKeyFromPackage(pkg) ?: state.lastAppKey
        state = state.copy(
            lastForegroundPackage = pkg,
            lastAppKey = appKey,
            updatedAtMs = clockMs()
        )
    }

    @Synchronized
    fun recordExecution(cmd: Command, foregroundPackage: String = "") {
        val now = clockMs()
        val pkg = foregroundPackage.trim().ifEmpty { state.lastForegroundPackage }
        val appKeyFromPkg = appKeyFromPackage(pkg)

        state = when (cmd) {
            is Command.Swipe -> state.copy(
                lastCommand = cmd,
                lastScrollDir = cmd.dir,
                lastScrollScale = cmd.scale,
                lastForegroundPackage = pkg,
                lastAppKey = appKeyFromPkg ?: state.lastAppKey,
                updatedAtMs = now
            )
            is Command.OpenApp -> state.copy(
                lastCommand = cmd,
                lastAppKey = AppFlows.find(cmd.name)?.key ?: cmd.name.lowercase(),
                lastForegroundPackage = pkg,
                updatedAtMs = now
            )
            is Command.YoutubeSearch -> state.copy(
                lastCommand = cmd,
                lastAppKey = "youtube",
                lastSearchQuery = cmd.query,
                lastForegroundPackage = pkg,
                updatedAtMs = now
            )
            is Command.InAppSearch -> state.copy(
                lastCommand = cmd,
                lastAppKey = cmd.appKey ?: appKeyFromPkg ?: state.lastAppKey,
                lastSearchQuery = cmd.query,
                lastForegroundPackage = pkg,
                updatedAtMs = now
            )
            is Command.ChromeNavigate -> state.copy(
                lastCommand = cmd,
                lastAppKey = "chrome",
                lastSearchQuery = cmd.urlOrQuery,
                lastForegroundPackage = pkg,
                updatedAtMs = now
            )
            is Command.Call -> state.copy(
                lastCommand = cmd,
                lastContact = cmd.target,
                lastForegroundPackage = pkg,
                updatedAtMs = now
            )
            is Command.AppCall -> state.copy(
                lastCommand = cmd,
                lastAppKey = cmd.appName,
                lastContact = cmd.contact,
                lastForegroundPackage = pkg,
                updatedAtMs = now
            )
            is Command.AppMessage -> state.copy(
                lastCommand = cmd,
                lastAppKey = cmd.appName,
                lastContact = cmd.contact,
                lastTypedText = cmd.text,
                lastForegroundPackage = pkg,
                updatedAtMs = now
            )
            is Command.AppChat -> state.copy(
                lastCommand = cmd,
                lastAppKey = cmd.appName ?: appKeyFromPkg ?: state.lastAppKey,
                lastTypedText = cmd.text,
                lastForegroundPackage = pkg,
                updatedAtMs = now
            )
            is Command.TypeText -> state.copy(
                lastCommand = cmd,
                lastTypedText = cmd.text,
                lastForegroundPackage = pkg,
                lastAppKey = appKeyFromPkg ?: state.lastAppKey,
                updatedAtMs = now
            )
            else -> state.copy(
                lastCommand = cmd,
                lastForegroundPackage = pkg,
                lastAppKey = appKeyFromPkg ?: state.lastAppKey,
                updatedAtMs = now
            )
        }
    }

    /**
     * Follow-up বাক্যগুলোকে আগের মেমোরির সাহায্যে সম্পূর্ণ [Command]-এ রূপান্তর করে।
     * যেমন:
     *  - আগে `ektu niche` -> পরে `aro beshi` => `Swipe(DOWN, FAR)`
     *  - আগে `upore` -> পরে `arekto` => `Swipe(UP, SLIGHT)`
     *  - যেকোনো কাজের পর `abar` / `abar koro` => `lastCommand`
     *  - আগে কিছু টাইপ করার পর `send koro` / `pathao` => `SubmitActiveInput`
     */
    @Synchronized
    fun resolveFollowUp(rawText: String, foregroundPackage: String = ""): Command? {
        expireIfNeeded()
        val clean = rawText.lowercase()
            .replace(Regex("[,.!?।\"'“”‘’\\-_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isEmpty()) return null

        val tokens = clean.split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val curDir = state.lastScrollDir

        // ১. "abar" / "abar koro" / "repeat" / "again"
        if (tokens.all { it in BanglaVocab.REPEAT_WORDS || it in BanglaVocab.FILLERS }) {
            val prev = state.lastCommand
            if (prev != null && prev !is Command.StopAssistant && prev !is Command.PanicStop && prev !is Command.Sleep) {
                return prev
            }
        }

        // ২. কেবল intensity follow-up: "aro beshi", "arekto", "ektu", "more", "aro" (দিক ছাড়া)
        val hasDirectionWord = tokens.any {
            it in BanglaVocab.SWIPE_UP || it in BanglaVocab.SWIPE_DOWN ||
                it in BanglaVocab.SWIPE_LEFT || it in BanglaVocab.SWIPE_RIGHT
        }
        if (!hasDirectionWord && curDir != null) {
            val onlyMoreOrSlight = tokens.all {
                it in BanglaVocab.SCROLL_FAR || it in BanglaVocab.SCROLL_SLIGHT ||
                    it in BanglaVocab.SCROLL_NOUN || it in BanglaVocab.FILLERS || it == "more"
            }
            if (onlyMoreOrSlight) {
                val isFar = tokens.any { it in BanglaVocab.SCROLL_FAR }
                val scale = if (isFar) ScrollScale.FAR else ScrollScale.SLIGHT
                return Command.Swipe(curDir, scale)
            }
        }

        // ৩. "eta send koro" / "send koro" / "pathao" (আগের app chat/type-এর পরে)
        val hasSend = tokens.any { it in BanglaVocab.SUBMIT_WORDS }
        val nonFiller = tokens.filter { it !in BanglaVocab.FILLERS && it !in BanglaVocab.SUBMIT_WORDS && it !in setOf("eta", "eita", "oita", "এটা", "এইটা") }
        if (hasSend && nonFiller.isEmpty() && (state.lastTypedText != null || foregroundPackage.isNotEmpty())) {
            return Command.SubmitActiveInput
        }

        return null
    }

    private fun expireIfNeeded() {
        if (state.updatedAtMs > 0L && (clockMs() - state.updatedAtMs) > ttlMs) {
            state = Snapshot()
        }
    }

    companion object {
        val shared = AssistantMemory()

        fun appKeyFromPackage(pkg: String): String? {
            if (pkg.isBlank()) return null
            val low = pkg.lowercase()
            return AppFlows.all.firstOrNull { flow ->
                flow.packages.any { low.contains(it.lowercase()) }
            }?.key
        }
    }
}
