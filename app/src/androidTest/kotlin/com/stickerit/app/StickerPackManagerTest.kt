package com.stickerit.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stickerit.app.data.model.StickerPackEntity
import com.stickerit.app.ui.gallery.StickerPackManagerDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StickerPackManagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun packManagerShowsNamedPackControls() {
        composeRule.setContent {
            MaterialTheme {
                StickerPackManagerDialog(
                    packs = listOf(StickerPackEntity(id = "animals", name = "Animals")),
                    selectedPackId = "animals",
                    packItems = emptyList(),
                    stickers = emptyList(),
                    onDismiss = {},
                    onSelectPack = {},
                    onCreatePack = {},
                    onRenamePack = { _, _ -> },
                    onDeletePack = {},
                    onPickTrayImage = {},
                    onReorderItems = {},
                    onUpdateMetadata = { _, _, _, _ -> },
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithText("WhatsApp packs").assertIsDisplayed()
        composeRule.onNodeWithText("Animals").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete").assertIsNotEnabled()
        composeRule.onNodeWithText("Create pack").assertHasClickAction()
        composeRule.onNodeWithText("Add selected to pack").assertHasClickAction()
    }
}
