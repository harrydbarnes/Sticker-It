# Gboard sticker feasibility report

**Audience:** Sticker It product and engineering

**Date:** 27 August 2026

## Decision

Sticker It cannot reliably save arbitrary user-created image stickers into Gboard's sticker tray. There is no public Gboard SDK, intent, ContentProvider contract, or Android platform API for an app to register images with Gboard. Do not ship or market the obsolete provider integration.

Google's own bridge does not change that conclusion. Google's current Pixel Studio help page says Pixel Studio can no longer create images and that users cannot save stickers; it lists Pixel 9/10 models rather than Pixel 11. This confirms the feature is a Google-controlled capability subject to withdrawal, rather than a general Android integration.

## Evidence

| Claim | Evidence | Confidence |
|---|---|---|
| Current Pixel Studio cannot save stickers. | [Google Pixel Help](https://support.google.com/pixelphone/answer/15236074?hl=en), accessed 2026-08-27, says "You can't save stickers." | High |
| The historical third-party Gboard provider no longer works. | [uSticker README](https://github.com/apsun/uSticker) warns it is incompatible with Gboard 10.2 and above. [Issue #49](https://github.com/apsun/uSticker/issues/49) records successful imports disappearing after 10.2 and reappearing only after a downgrade to 10.1. | High for incompatibility; it is independent implementation evidence, not Google documentation. |
| Android supports image insertion only from an active IME to a cooperating editor. | [Android InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection) documents `commitContent`, but says the editor may ignore it when the MIME type is not in `EditorInfo.contentMimeTypes`. | High |
| A third-party keyboard is feasible but cannot coexist as a Gboard tab. | [Android's IME guide](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method) requires an `InputMethodService`; Android lets the user choose one enabled IME at a time. | High |

## Options

1. **Gboard tray via old provider — reject.** It requires pinning Gboard to a 2021-era build, is not a documented interface, and will not work on current Pixels. It is unsuitable for a product or Play release.
2. **Sticker It keyboard — conditional.** Add an optional, local-only `InputMethodService` that shows the Sticker It library. It can call `commitContent` when the target editor advertises `image/*`. The user must switch away from Gboard, and any app that does not accept image MIME types will reject it. This is the closest technically sound cross-app keyboard experience, but is not "works in every app".
3. **Library + explicit share/paste — baseline.** Keep the first-class library, Android Sharesheet, clipboard copy where the target accepts an image URI, and WhatsApp packs. This offers broad but not universal coverage without pretending to be a keyboard integration.

## Recommended product decision

Retain the WhatsApp pack path and library. Do **not** retain the Gboard provider code. If keyboard access is still a must-have, build a clearly labelled optional Sticker It keyboard prototype and test it on the user's Pixel with a defined app matrix (Messages, WhatsApp, Telegram, Instagram, Slack, Gmail, Chrome and a plain `EditText`). Only promote it if its measured coverage is acceptable; it will still be distinct from Gboard and cannot guarantee every app.

## Verification gap

No Android device was connected to this workspace, so an on-device probe of the user's exact Pixel/Gboard build could not be performed. That probe can confirm the missing UI/feature state, but it cannot create a supported third-party Gboard API where none is documented.
