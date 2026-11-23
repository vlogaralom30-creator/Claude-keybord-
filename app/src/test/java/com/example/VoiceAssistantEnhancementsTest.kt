package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.AssistantMemory
import com.banglakeys.app.AssistantPrefs
import com.banglakeys.app.Command
import com.banglakeys.app.CommandCatalog
import com.banglakeys.app.CommandParser
import com.banglakeys.app.Dir
import com.banglakeys.app.ScrollScale
import com.banglakeys.app.SmartCommandResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceAssistantEnhancementsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("bk", Context.MODE_PRIVATE).edit().clear().commit()
        AssistantMemory.shared.clear()
    }

    @Test
    fun `test scroll scales and granular directions`() {
        val c1 = CommandParser.parse("ektu niche namo")
        assertEquals(Command.Swipe(Dir.DOWN, ScrollScale.SLIGHT), c1)

        val c2 = CommandParser.parse("aro beshi upore uthao")
        assertEquals(Command.Swipe(Dir.UP, ScrollScale.FAR), c2)

        val c3 = CommandParser.parse("niche")
        assertEquals(Command.Swipe(Dir.DOWN, ScrollScale.NORMAL), c3)
    }

    @Test
    fun `test assistant memory follow ups`() {
        val memory = AssistantMemory(ttlMs = 60_000L, clockMs = { 1000L })
        memory.recordExecution(Command.Swipe(Dir.DOWN, ScrollScale.SLIGHT), "com.zhiliaoapp.musically")

        // Follow up "aro beshi" should resolve to Swipe(DOWN, FAR)
        val resolved = memory.resolveFollowUp("aro beshi", "com.zhiliaoapp.musically")
        assertEquals(Command.Swipe(Dir.DOWN, ScrollScale.FAR), resolved)

        // Follow up "abar koro" should repeat last command
        val repeatRes = memory.resolveFollowUp("abar koro", "com.zhiliaoapp.musically")
        assertEquals(Command.Swipe(Dir.DOWN, ScrollScale.SLIGHT), repeatRes)
    }

    @Test
    fun `test chrome url and search parsing`() {
        val c1 = CommandParser.parse("chrome e prothomalo.com khulo")
        assertEquals(Command.ChromeNavigate("prothomalo.com", isUrl = true), c1)

        val c2 = CommandParser.parse("chrome e cricket search koro")
        assertEquals(Command.ChromeNavigate("cricket", isUrl = false), c2)

        val c3 = CommandParser.parse("new tab khulo")
        assertEquals(Command.AppAction("chrome", com.banglakeys.app.AppActionType.NEW_TAB), c3)
    }

    @Test
    fun `test youtube facebook tiktok comments and actions`() {
        val c1 = CommandParser.parse("youtube e comment koro darun")
        assertEquals(
            Command.AppAction("youtube", com.banglakeys.app.AppActionType.COMMENT, "darun"),
            c1
        )

        val c2 = CommandParser.parse("subscribe koro")
        assertEquals(Command.AppAction("youtube", com.banglakeys.app.AppActionType.SUBSCRIBE), c2)

        val c3 = CommandParser.parse("tiktok follow koro")
        assertEquals(Command.AppAction("tiktok", com.banglakeys.app.AppActionType.FOLLOW), c3)

        val c4 = CommandParser.parse("porer video")
        assertEquals(Command.MediaStep(next = true), c4)

        val c5 = CommandParser.parse("call kato")
        assertEquals(Command.AppAction(null, com.banglakeys.app.AppActionType.END_CALL), c5)
    }

    @Test
    fun `test command catalog and filtering`() {
        val all = CommandCatalog.filter(CommandCatalog.Category.ALL, "")
        assertTrue(all.size >= 8)

        val yt = CommandCatalog.filter(CommandCatalog.Category.YOUTUBE, "subscribe")
        assertTrue(yt.isNotEmpty())
        assertTrue(yt.any { it.testPhrase.contains("subscribe", true) })
    }

    @Test
    fun `test scroll direction preference defaults and toggling`() {
        assertTrue(AssistantPrefs.scrollByContentDirection(context))
        AssistantPrefs.setScrollByContentDirection(context, false)
        assertTrue(!AssistantPrefs.scrollByContentDirection(context))
    }
}
