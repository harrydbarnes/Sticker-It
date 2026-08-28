package com.stickerit.app.ui.gallery

/** Events emitted by the gallery; wording stays in Android resources. */
sealed interface GalleryMessage {
    data object StickerDeleted : GalleryMessage
    data object PackCreated : GalleryMessage
    data object PackRenamed : GalleryMessage
    data object PackDeleted : GalleryMessage
    data object KeepOnePack : GalleryMessage
    data object TrayImageUpdated : GalleryMessage
    data object CouldNotUseImage : GalleryMessage
    data object StickerMetadataSaved : GalleryMessage
    data object CouldNotPrepareWhatsAppPack : GalleryMessage
    data object ConfirmWhatsAppPack : GalleryMessage
    data object WhatsAppNotInstalled : GalleryMessage
    data object InvalidWhatsAppStickerCount : GalleryMessage
    data object WhatsAppPackNotFound : GalleryMessage
}
