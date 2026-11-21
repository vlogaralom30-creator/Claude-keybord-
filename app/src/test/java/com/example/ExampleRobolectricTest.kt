package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.ClipboardMemory
import com.banglakeys.app.Command
import com.banglakeys.app.CommandParser
import com.banglakeys.app.Dir
import com.banglakeys.app.KeyType
import com.banglakeys.app.Layouts
import com.banglakeys.app.Notes
import com.banglakeys.app.Phonetic
import com.banglakeys.app.Suggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Bangla Keys", appName)
  }

  @Test
  fun `phonetic conversion produces expected bangla`() {
    assertEquals("আমি", Phonetic.convert("ami"))
    assertEquals("বাংলা", Phonetic.convert("bangla"))
    assertEquals("তোমাকে", Phonetic.convert("tOmake"))
    assertEquals("ক্ষ", Phonetic.convert("kkh"))
  }

  @Test
  fun `suggestions learn and suggest words`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val suggestions = Suggestions(context)
    assertTrue(suggestions.suggestionsFor("am", true).contains("আমি"))
    suggestions.learn("dhk", "ঢাকা")
    assertTrue(suggestions.suggestionsFor("dhk", true).contains("ঢাকা"))
  }

  @Test
  fun `notes and clipboard memory persist items`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val notes = Notes(context)
    notes.add("আমার ঠিকানা")
    assertTrue(notes.all().contains("আমার ঠিকানা"))

    val clipboard = ClipboardMemory(context)
    clipboard.add("হ্যালো বাংলাদেশ")
    assertTrue(clipboard.clips().contains("হ্যালো বাংলাদেশ"))
    clipboard.remove("হ্যালো বাংলাদেশ")
    assertTrue(clipboard.clips().none { it == "হ্যালো বাংলাদেশ" })
  }

  @Test
  fun `layouts panel, voice, and assistant keys build properly`() {
    val rows = Layouts.panel(listOf("One", "Two", "Three"))
    assertEquals(3, rows.size)

    val phoneticBottom = Layouts.phonetic(numberRow = false, bnDigits = true).last()
    assertTrue(phoneticBottom.any { it.type == KeyType.MIC })
    assertTrue(phoneticBottom.any { it.type == KeyType.NOTEPAD })
    assertTrue(phoneticBottom.any { it.type == KeyType.TRANSLATE })
    assertTrue(phoneticBottom.any { it.type == KeyType.ASSISTANT })
  }

  @Test
  fun `command parser handles wake word and commands`() {
    val (woke, rest) = CommandParser.splitWake("Hey Bot open TikTok")
    assertTrue(woke)
    assertEquals(Command.OpenApp("tiktok"), CommandParser.parse(rest))
    assertEquals(Command.Swipe(Dir.UP), CommandParser.parse("উপরে"))
    assertEquals(Command.Like, CommandParser.parse("sundor"))
    assertEquals(Command.Call("ammi"), CommandParser.parse("ammi ke call koro"))
    assertEquals(
      Command.YoutubeSearch("tum hi ho", mostViewed = true),
      CommandParser.parse("youtube search tum hi ho most viewed")
    )
  }
}
