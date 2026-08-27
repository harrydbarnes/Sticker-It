package com.stickerit.app

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import com.stickerit.app.ui.home.HomeScreen
import com.stickerit.app.ui.theme.StickerItTheme

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreenExposesPrimaryActions() {
        composeRule.setContent {
            StickerItTheme {
                HomeScreen(
                    onPickImages = {},
                    onOpenGallery = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Pick a Photo").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("My Stickers").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed().assertHasClickAction()
    }
}
