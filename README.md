# MicArbiter Mobile (Android)

An Android (Kotlin + Jetpack Compose + Material 3) application that monitors active AI voice sessions and arbitrates microphone access when multiple AI voice apps (ChatGPT Voice, Google Gemini Live, Claude, Copilot, Perplexity, Discord, Zoom) are active on a phone.

## Option A: Build the APK in the Cloud via GitHub Actions (No PC Required)

1. Unzip `mic_arbiter_android.zip`.
2. Create a new repository on GitHub and upload the contents of `mic_arbiter_android/` (including the hidden `.github/workflows/build-apk.yml` folder).
3. On GitHub, open the **Actions** tab, select **Build MicArbiter Android APK**, and click **Run workflow** (or it runs automatically on push).
4. Once the build finishes, download the **MicArbiter-debug-apk** artifact, unzip it on your phone, and tap `app-debug.apk` to install.

## Option B: Build Locally in Android Studio

1. Open `mic_arbiter_android/` in **Android Studio** (JDK 17, Android SDK 35).
2. Let Gradle sync, then click **Run** on a physical Android phone or emulator (API 26+).
3. Grant **Microphone** and **Notifications** permissions when prompted.

## Features

- **Exclusive Focus**: Keeps the mic live for the selected AI voice app while requesting `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` against competing apps.
- **Push-to-Talk & Toggle-to-Talk**: Hold **HOLD TO TALK** or tap **Toggle Mic** to control when the mic is live.
- **Auto-Priority**: Automatically selects the highest-priority active voice app.
- **Quick Settings Tile**: Adds a one-tap **MicArbiter** tile to the Android notification shade.
- **Built-in Demo Mode**: Toggle **Demo: ON** to test with simulated ChatGPT Voice, Google Gemini Live, and Claude sessions.

## Note on iOS

Apple iOS strictly sandboxes third-party apps and does not allow one app to mute or intercept the microphone of another app.
