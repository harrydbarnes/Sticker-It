package com.stickerit.app

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stickerit.app.data.model.BatchImportItem
import com.stickerit.app.data.model.BatchImportUiState
import com.stickerit.app.ui.batch.BatchImportContent
import com.stickerit.app.ui.theme.StickerItTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchImportContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun batchScreenShowsAccessibleCreateAction() {
        composeRule.setContent {
            StickerItTheme {
                BatchImportContent(
                    state = BatchImportUiState(
                        items = listOf(BatchImportItem("content://photo/1", "Sticker 1")),
                    ),
                    onBack = {},
                    onStart = {},
                    onCancel = {},
                    onRetry = {},
                    onFinished = {},
                )
            }
        }

        composeRule.onNodeWithText("Create 1 sticker").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("Sticker 1, Waiting").assertIsDisplayed()
    }
}
