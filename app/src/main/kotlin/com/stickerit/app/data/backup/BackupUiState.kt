package com.stickerit.app.data.backup

enum class BackupOperation {
    IDLE,
    EXPORTING,
    IMPORTING,
}

data class BackupUiState(
    val operation: BackupOperation = BackupOperation.IDLE,
) {
    val isBusy: Boolean
        get() = operation != BackupOperation.IDLE
}

sealed interface BackupEvent {
    data class Exported(val stickerCount: Int, val packCount: Int = 0) : BackupEvent
    data class Imported(
        val importedCount: Int,
        val skippedCount: Int,
        val importedPackCount: Int = 0,
        val skippedPackCount: Int = 0,
    ) : BackupEvent
    data object Failed : BackupEvent
}
