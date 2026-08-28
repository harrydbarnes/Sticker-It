package com.stickerit.app

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stickerit.app.data.model.BrushMode
import com.stickerit.app.ui.components.BrushOverlay
import com.stickerit.app.ui.theme.StickerItTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun brushCanvasAnnouncesItsCurrentMode() {
        composeRule.setContent {
            StickerItTheme {
                BrushOverlay(
                    modifier = Modifier.size(240.dp),
                    brushMode = BrushMode.INCLUDE,
                    brushRadius = 12f,
                    onDragStart = { _, _ -> },
                    onDrag = { _, _ -> },
                    onDragEnd = {},
                    onCursorPositionChange = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Image selection canvas. Include mode. Drag to select an area.")
            .assertIsDisplayed()
    }
}
