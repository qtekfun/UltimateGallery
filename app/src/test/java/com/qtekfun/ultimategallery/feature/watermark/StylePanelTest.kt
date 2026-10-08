package com.qtekfun.ultimategallery.feature.watermark

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w400dp-h900dp-xhdpi")
class StylePanelTest {
    @get:Rule
    val rule = createComposeRule()

    private val library = FakeFontLibrary()
    private var reported: TextStyleSpec? = null
    private var removedId: String? = null

    private val actions = EditorActions(
        onSetType = {}, onSetBase = {}, onText = {}, onStyle = { reported = it }, onSpacing = {}, onStagger = {}, onImportLogo = {},
        hasLogo = { false }, lastText = { WatermarkSource.Text("x") }, lastImage = { null },
        onOpacity = {}, onMargin = {}, onSnap = {}, onResetPlacement = {},
        onRemoveFont = { removedId = it }
    )

    private fun show(style: TextStyleSpec = TextStyleSpec()) {
        rule.setContent {
            StylePanel(WatermarkProfile(source = WatermarkSource.Text("Hi", style)), actions, fonts = library.initial)
        }
    }

    @Test
    fun thereIsExactlyOneItalicControlAndItTogglesItalic() {
        show()
        rule.onAllNodesWithText("Italic").assertCountEquals(1)
        rule.onAllNodesWithText("Cursiva").assertCountEquals(0)
        rule.onNodeWithText("Italic").performClick()
        assertTrue(reported?.italic == true)
    }

    @Test
    fun theOldFontChipsAreGone() {
        show()
        listOf("Sans", "Serif", "Mono", "Script", "Narrow").forEach { rule.onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun fontButtonNamesTheCurrentFontAndOpensThePickerListingEveryFont() {
        show(TextStyleSpec(fontId = FontIds.bundled("Lora")))
        rule.onNodeWithTag("font_button").assertIsDisplayed()
        rule.onNodeWithTag("font_button").assertTextContains("Lora")
        rule.onNodeWithTag("font_button").performClick()
        rule.onNodeWithText("Choose a font").assertIsDisplayed()
        rule.onNodeWithText("Import font…").assertIsDisplayed()
        rule.onAllNodesWithText("Default").assertCountEquals(1)
        rule.onAllNodesWithText("Lora").assertCountEquals(2)
        rule.onNodeWithText("Mine").assertIsDisplayed()
    }

    @Test
    fun selectingAFontReportsItsIdAndClosesThePicker() {
        show()
        rule.onNodeWithTag("font_button").performClick()
        rule.onNodeWithText("Lora").performClick()
        assertEquals(FontIds.bundled("Lora"), reported?.fontId)
        rule.onAllNodesWithText("Choose a font").assertCountEquals(0)
    }

    @Test
    fun onlyImportedFontsCanBeRemovedAndRemovalAsksFirst() {
        show()
        rule.onNodeWithTag("font_button").performClick()
        rule.onAllNodesWithContentDescription("Remove", substring = true).assertCountEquals(1)
        rule.onNodeWithContentDescription("Remove Mine").performClick()
        assertNull(removedId)
        rule.onNodeWithText("Remove this font?").assertIsDisplayed()
        rule.onNodeWithText("Remove").performClick()
        assertEquals(FontIds.imported("Mine"), removedId)
    }
}
