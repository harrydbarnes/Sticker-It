package com.stickerit.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stickerit.app.data.model.FinishBackgroundType
import com.stickerit.app.data.model.FinishRecipe
import com.stickerit.app.ui.editor.FinishStudioPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinishStudioTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun finishingStudioShowsControlsAndCanChooseBackground() {
        var recipe by mutableStateOf(FinishRecipe())
        var saved = false

        composeRule.setContent {
            MaterialTheme {
                FinishStudioPanel(
                    recipe = recipe,
                    onRecipeChange = { recipe = it },
                    onPickBackground = {},
                    onBackToBrush = {},
                    onSave = { saved = true },
                )
            }
        }

        composeRule.onNodeWithText("Finish sticker").assertIsDisplayed()
        composeRule.onNodeWithText("Outline").assertIsDisplayed()
        composeRule.onNodeWithText("Background").assertIsDisplayed()
        composeRule.onNodeWithText("Solid colour").performClick()
        composeRule
            .onNodeWithText("Save sticker")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(FinishBackgroundType.SOLID, recipe.backgroundType)
        assertTrue(saved)
    }
}
