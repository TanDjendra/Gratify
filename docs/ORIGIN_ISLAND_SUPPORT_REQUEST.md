# Request for official Origin Island integration — Gratify

Status: sent via Gmail on 2026-10-04, 13:26 WIB. Gmail confirmed "Pesan terkirim" and the message was opened in Sent. Awaiting Vivo's response; native Origin Island eligibility has not been approved or confirmed.

Sender: Gratify Music <supportgratify@gmail.com>

The user does not yet have a Vivo Developer account. No APK, source code, credentials, or device serial number was attached.

Proof of sending: ../artifacts/now-playing/vivo-origin-island-request-sent.jpg

Recipient: globaldev@vivo.com

Official contact page: https://developer.vivo.com/contact/customer-service?id=0

Subject: Official Origin Island music integration / app eligibility request — Gratify (iQOO Z9)

---

Dear Vivo Developer Support,

I would like to request guidance on enabling official native Origin Island music controls for Gratify, an Android music application. Please route this inquiry to the OriginOS / Origin Island media integration team if necessary.

I do not yet have a Vivo Developer account. Please advise whether an individual developer can apply and whether account registration is required for this technical inquiry or the eligibility review.

Application details:

- App name: Gratify
- Production package: com.tan.gratify
- Debug/test package currently installed: com.tan.gratify.dev
- Tested version: 2.1.0-dev, version code 61
- Android implementation: Kotlin/Compose with AndroidX Media3 1.10.1, ExoPlayer, MediaLibrarySession, and MediaLibraryService
- Minimum SDK: 26; target SDK: 36

Device and observations:

- Device reported by its owner: iQOO Z9 5G; device model identifier: I2218
- Android version: 16
- Firmware: PD2361BF_EX_A_16.2.11.0.W20
- Spotify appears in the native Origin Island on this device, according to its owner.
- Read-only device inspection confirms that Gratify has an active MediaSession in PLAYING state and a running foreground service of type mediaPlayback.
- Gratify's native MediaStyle notification provides track title, artist, and bitmap artwork. Its session supports Play/Pause, Next, and Previous.
- We have not confirmed native Origin Island eligibility for Gratify. Vivo's current music-control selection points to Spotify.

We would like to integrate using documented, officially supported mechanisms. Could you please clarify:

1. Does Origin Island accept standard Android MediaSessions from arbitrary third-party music apps on this firmware, or does eligibility require an approved application list? How can Gratify apply for eligibility under its own package identity?
2. Is there an official SDK, integration guide, metadata requirement, or notification/media-session configuration we should implement for music controls in Origin Island?
3. Does eligibility depend on V-Appstore publication, a registered developer account, production signing certificates, or a partner review? Are debug package variants supported for testing?
4. Can your team review Gratify's package eligibility or provide an appropriate technical contact and test procedure for global/Indonesian iQOO devices?

Please advise which app and test evidence is required for review and the appropriate submission channel. We can prepare a focused technical report or test APK if requested.

Thank you,
Gratify development
