# AutoScroll

Auto-scrolling assistant for short-video platforms (YouTube Shorts, Instagram
Reels, Facebook Reels, TikTok). Designed primarily as an accessibility tool
for users with motor impairments who find repeated swiping difficult.

## Features

- **Auto-scroll** in two modes: timer-based (swipe every X seconds) or
  end-of-video detection (swipe when current video finishes).
- **Skip Ads** automatically when a "Skip" button is detected on screen.
- **Floating overlay** with quick controls + countdown, shown only on supported
  apps.
- **Per-platform settings** — enable/disable on each platform individually.

## How it works

The app uses three Android subsystems:

1. **AccessibilityService** — reads on-screen UI tree from supported apps,
   performs swipe gestures, and locates "Skip Ad" controls.
2. **System Alert Window** (`SYSTEM_ALERT_WINDOW`) — draws the floating
   overlay panel.
3. **Foreground Service** keeps the app alive in the background reliably.

## Tech stack

- Kotlin + Jetpack Compose
- AccessibilityService (core mechanism)
- DataStore Preferences (settings)
- minSdk 26 (Android 8.0)
- Gradle Kotlin DSL + Version Catalog

## Privacy

The app collects **no user data**. UI tree access is processed on-device only
and never leaves the phone.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

You'll need to add a `local.properties` with `sdk.dir=...` pointing at your
Android SDK.
