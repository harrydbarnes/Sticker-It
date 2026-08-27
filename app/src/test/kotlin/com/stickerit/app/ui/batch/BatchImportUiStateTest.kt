package com.stickerit.app.ui.batch

import com.stickerit.app.data.model.BatchImportItem
import com.stickerit.app.data.model.BatchImportUiState
import com.stickerit.app.data.model.BatchItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchImportUiStateTest {

    @Test
    fun countsCompletedFailedAndPendingItems() {
        val state = BatchImportUiState(
            items = listOf(
                BatchImportItem("one", "One", BatchItemStatus.COMPLETE),
                BatchImportItem("two", "Two", BatchItemStatus.FAILED),
                BatchImportItem("three", "Three", BatchItemStatus.QUEUED),
                BatchImportItem("four", "Four", BatchItemStatus.CANCELLED),
            ),
        )

        assertEquals(1, state.completedCount)
        assertEquals(1, state.failedCount)
        assertEquals(2, state.pendingCount)
        assertFalse(state.isFinished)
    }

    @Test
    fun finishedStateAllowsFailedItemsToBeRetried() {
        val state = BatchImportUiState(
            items = listOf(
                BatchImportItem("one", "One", BatchItemStatus.COMPLETE),
                BatchImportItem("two", "Two", BatchItemStatus.FAILED),
            ),
        )

        assertTrue(state.isFinished)
        assertEquals(0, state.pendingCount)
    }
}
