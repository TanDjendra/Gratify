# Gratify loading cover — 10 October 2026

User preference: missing or not-yet-loaded music covers must show the Gratify loading logo rather than initials.

Search result cards, browse category/public playlist cards, and immediate playlist song rows use the existing pulsing Gratify logo while their image is missing, loading, or failed. The logo scales to the available artwork box. Image success removes the loading component and its infinite animation; changing the image URL resets loading for the new image. Original YouTube Music artwork remains the image source, and the AI atlas stays absent from application resources.

The shared screen loading component gained an optional logo size; its default size and appearance remain the same for existing full-screen loaders.

Verification and the newest debug APK are recorded in `artifacts/brand-loading-cover-2026-10-10/`.

Final validation: **12 JVM tests**, zero failures/errors, and **5 native Android checks** passed. Native checks confirm that the animated logo replaces cover initials on enlarged text and is removed after original artwork loads. Screenshots were visually reviewed; native checks used the API 37 emulator, not a physical phone.

APK: `artifacts/brand-loading-cover-2026-10-10/Gratify-debug-loading-cover-20261010.apk`, 104,587,599 bytes, APK v2 signature verified. The AI atlas is absent. SHA-256: `1C15FB16A87FD3E3D2BDA1B7392683B4A6401E2A6910C17BAB115A428D3EAEF1`.
