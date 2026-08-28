package com.stickerit.app.ui.gallery

/**
 * Removes selections that are no longer present in the gallery source list.
 * Keeping this pure makes the selection contract easy to exercise without a
 * Compose runtime and prevents deleted stickers being sent to pack actions.
 */
internal fun pruneSelectedStickerIds(
    selectedIds: Set<Long>,
    visibleStickerIds: Collection<Long>,
): Set<Long> {
    if (selectedIds.isEmpty()) return emptySet()
    val visibleIds = visibleStickerIds.toHashSet()
    return selectedIds.filterTo(linkedSetOf()) { it in visibleIds }
}
