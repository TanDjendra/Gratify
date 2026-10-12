# Friends notes — 9 October 2026

Friends now opens with a plain-text personal note and notes from mutual friends, followed by listening activity. The remaining destinations are Activity and Messages (including message requests). The story composer, rail, viewer, public story feed, draft continuation and music preview are removed from the production Friends screen. Saved PUBLIC navigation restores to Activity. Older story-only notifications open Friends safely without fetching or playing a story.

Notes use the existing profile fields and remain until changed or deleted. Existing notes, including those older than 24 hours and those without a timestamp, remain readable. The 100-character editor supports Save, Delete, Cancel, a saving state and failed-save retry; blank Save is disabled. A friend note opens the corresponding profile. Note editing retains the existing account guards, serialization, timeout, write success/error handling and protection from stale profile reads. Backend history is not deleted.

Inbox refresh no longer requests story feeds, so a missing story service cannot prevent messages from loading. Lyrics image sharing retains Instagram, WhatsApp, other apps and image saving; the in-app Gratify Story destination is removed.

The production UI is verified using the Friends and lyrics JVM checks. Native UI verification uses the production cards, editor, inbox and chat with sample data, without real account/database writes. APK, signature, test reports and screenshots are in `artifacts/friends-notes-2026-10-09`.

Validation completed: 40 JVM tests and 30 native emulator checks passed. Signed debug APK `Gratify-debug-friends-notes-20261009.apk`, SHA-256 `C65C766AAE829A3258C8302A350B262487F809A4FF976B49828B1F809FA61B8D`.
