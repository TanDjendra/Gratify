---
name: gratify-ui-overhaul
description: Panduan arsitektur dan blueprint navigasi Gratify untuk agen AI. Skill ini menginstruksikan perombakan total UI menggunakan library komponen eksternal (menggantikan UI bawaan).
---

# GRATIFY — Architecture & UI Overhaul Guidelines

You are acting as a Senior Android Engineer building **GRATIFY**, a modern music player application built with Jetpack Compose.
Your current primary objective is to **completely overhaul the User Interface (UI)** using the designated external UI Library, ensuring maximum consistency across all screens while strictly adhering to the existing navigation and architectural blueprint.

---

## 1. UI Overhaul Directive (CRITICAL)
We are migrating away from raw/custom UI implementations in individual screens. 
- **Target UI Library:** `[MASUKKAN NAMA LIBRARY DI SINI - misal: namalibrary-compose]`
- **Rule:** Do NOT use standard Material 3 components (`Button`, `Card`, etc.) directly if the target library provides an equivalent.
- **Rule:** Do NOT implement custom glassmorphism or squircles manually in the screens if the target library has a standard way of doing it.
- **Rule:** All screens must be refactored to use the theme, tokens, and components exclusively from the target UI library to ensure 100% consistency.

---

## 2. Core Tech Stack & Constraints
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (using the designated external UI library)
- **Navigation:** Type-Safe Navigation Compose (Kotlin `@Serializable` Destination objects)
- **Audio Engine:** Jetpack Media3 (ExoPlayer + MediaSessionService)
- **Architecture:** Clean Architecture + MVI/MVVM (StateFlow, SharedFlow, Koin DI)

---

## 3. Navigation Blueprint & Source Map
When overhauling screens, always adhere strictly to the following navigation codes and file naming conventions. DO NOT break the routing logic.

### A. Auth Flow (`LoginScreenGraph.kt`)
- `A` -> `SplashScreen.kt` (Entry point & session check)
- `B` -> `LoginLandingScreen.kt` (Onboarding & auth choices)
- `B.1` -> `SignUpScreen.kt` (Register)
- `B.2` -> `EmailLoginScreen.kt` (Login)
- `B.2.1` -> `ForgotPasswordScreen.kt` (Reset Password)
- `B.3` -> `LogInScreen.kt` (YouTube Music WebView Login)
- `A.3` -> `CreateProfileScreen.kt` (Post-signup profile creation)

### C. Main Flow (`MainScreen.kt` Shell)
- `C.1` -> `HomeScreen.kt` (Main recommendations feed)
  - `C.1.1` -> `ProfileDrawer` (Drawer inside MainScreen)
  - `C.1.2` -> `SettingScreen.kt`
  - `C.1.3` -> `NotificationScreen.kt`
  - `C.1.4` -> `RecentlySongsScreen.kt`
  - `C.1.5` -> `MoodScreen.kt` (Param: `params`)
  - `C.1.6` -> `CreditScreen.kt`
  - `C.1.7` -> `AnalyticsScreen.kt`
  - `C.1.8` -> `ProfileScreen.kt`
- `C.2` -> `SearchScreen.kt`
- `C.3` -> `LibraryScreen.kt`
  - `C.3.1` -> `LibraryDynamicPlaylistScreen.kt` (Param: `type`)
  - `C.3.2` -> `ArtistSelectionScreen.kt`
  - `C.3.3` -> `AddSongsToPlaylistScreen.kt` (Param: `playlistTitle`)
  - `C.3.4` -> `LocalPlaylistScreen.kt` (Param: `id`)
- `C.4` -> `FriendsActivityScreen.kt`
- `C.5` -> `FullscreenPlayer.kt` (Video/Fullscreen mode)

### D. Content Detail Screens (`ListScreenGraph.kt`)
- `D.1` -> `AlbumScreen.kt` (Param: `browseId`)
- `D.2` -> `ArtistScreen.kt` (Param: `channelId`)
- `D.3` -> `PlaylistScreen.kt` (Params: `playlistId`, `isYourYouTubePlaylist`)
- `D.4` -> `SharedPlaylistScreen.kt` (Param: `playlistId`)
- `D.5` -> `PodcastsScreen.kt` (Param: `podcastId`)
- `D.6` -> `MoreAlbumsScreen.kt` (Params: `type`, `id`)
- `D.7` -> `UserProfileScreen.kt` (Param: `userId`)
- `D.8` -> `FollowListScreen.kt` (Params: `userId`, `initialTab`)

### E. Persistent & Overlay Components
- `E.1` -> `MiniPlayer.kt` (Floating bottom bar, transitions into NowPlaying)
- `E.2` -> `NowPlayingScreen.kt` (Full player sheet + queue)
- `E.6` -> `AppBottomNavigationBar` (Mobile portrait navigation)
- `E.7` -> `NavigationRail` (Tablet / wide screen navigation)

---

## 4. Deep Link Rules
Always ensure deep link intent filters map correctly when refactoring:
- `gratify://app/profile/{userId}` -> `D.7 UserProfileScreen`
- `https://gratify.org/app/profile/{userId}` -> `D.7 UserProfileScreen`
- `?v={videoId}` / `youtu.be/{id}` -> Global Audio Playback
- `?list=OLAK5uy_...` -> `D.1 AlbumScreen`
- `?list=VL...` -> `D.3 PlaylistScreen`
- `channel/{channelId}` -> `D.2 ArtistScreen`
- `gratify://notification` -> `C.1.3 NotificationScreen`

---

## 5. Coding & Implementation Rules
1. **Type-Safe Routes:** Use Kotlin `@Serializable` objects/data classes for every route.
2. **State Management:** Screens must be stateless composables that accept `UiState` and emit `UiEvent` (State Hoisting).
3. **Separation of Concerns:** Do not place business or playback logic inside Composable functions; delegate to ViewModels and MediaController.
4. **Error Handling:** Use `SharedViewModel.snackbarEvent` for global toasts/snackbars. Do not instantiate local snackbar hosts unless isolated from the main flow.
5. **Naming Convention:** Navigation routes must be suffixed with `Destination` (e.g., `HomeDestination.kt`), and UI Composables must be suffixed with `Screen` (e.g., `HomeScreen.kt`).
