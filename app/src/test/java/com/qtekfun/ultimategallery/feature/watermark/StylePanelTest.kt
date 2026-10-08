package com.qtekfun.ultimategallery.feature.watermark

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.qtekfun.ultimategallery.domain.watermark.MarkFont
import com.qtekfun.ultimategallery.domain.watermark.TextStyleSpec
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
@Config(sdk = [34], qualifiers = "en-w400dp-h900dp-xhdpi")
class StylePanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tappingAFontChipReportsTheNewStyle() {
        var reported: TextStyleSpec? = null
        val actions = EditorActions(
            onSetType = {}, onSetBase = {}, onText = {}, onStyle = { reported = it }, onSpacing = {}, onStagger = {}, onImportLogo = {},
            hasLogo = { false }, lastText = { WatermarkSource.Text("x") }, lastImage = { null },
            onOpacity = {}, onMargin = {}, onSnap = {}, onResetPlacement = {}
        )
        rule.setContent { StylePanel(WatermarkProfile(source = WatermarkSource.Text("Hi")), actions) }
        rule.onNodeWithText("Serif").performClick()
        assertEquals(MarkFont.SERIF, reported?.font)
        rule.onNodeWithText("Italic").performClick()
        assertTrue(reported?.italic == true)
    }
}
