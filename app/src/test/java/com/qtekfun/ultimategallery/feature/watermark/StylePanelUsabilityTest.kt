package com.qtekfun.ultimategallery.feature.watermark

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimategallery.domain.watermark.FontIds
import com.qtekfun.ultimategallery.domain.watermark.WatermarkProfile
import com.qtekfun.ultimategallery.domain.watermark.WatermarkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class StylePanelUsabilityTest {
    @get:Rule
    val rule = createComposeRule()

    private var profile by mutableStateOf(WatermarkProfile())

    private val actions = EditorActions(
        onSetType = {},
        onSetBase = {},
        onText = {},
        onStyle = { style -> profile = profile.copy(source = (profile.source as WatermarkSource.Text).copy(style = style)) },
        onSpacing = {},
        onStagger = {},
        onImportLogo = {},
        hasLogo = { false },
        lastText = { profile.source as WatermarkSource.Text },
        lastImage = { null },
        onOpacity = { profile = profile.copy(opacity = it) },
        onMargin = {},
        onSnap = {},
        onResetPlacement = {}
    )

    private fun style() = (profile.source as WatermarkSource.Text).style

    @Test
    fun everyControlIsReachableInsideTheConstrainedPanelAndReachesTheState() {
        rule.setContent { StylePanel(profile, actions, Modifier.heightIn(max = 230.dp), fonts = FakeFontLibrary().initial) }

        rule.onNodeWithContentDescription("Preview").assertIsDisplayed()
        rule.onNodeWithTag("font_button").performClick()
        rule.onNodeWithText("Lora").performClick()
        assertEquals(FontIds.bundled("Lora"), style().fontId)
        rule.onNodeWithText("Italic").performClick()
        assertTrue(style().italic)

        rule.onNodeWithText("Outline").performScrollTo().performClick()
        assertTrue(style().outlineEnabled)
        rule.onNodeWithText("Outline thickness").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Background").performScrollTo().performClick()
        assertTrue(style().backgroundEnabled)
        rule.onNodeWithText("Background padding").performScrollTo().assertIsDisplayed()
        // Earlier choices are kept by later ones: no stale copy of the style is written back.
        assertEquals(FontIds.bundled("Lora"), style().fontId)
        assertTrue(style().italic && style().outlineEnabled)
    }
}
