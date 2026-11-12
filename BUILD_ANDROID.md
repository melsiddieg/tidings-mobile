# Building, Reconstructing & Deploying Tidings for Android (Pixel 8)

This guide documents the complete environment setup, space-saving APK build pipeline, physical Pixel 8 installation steps, and how to recreate (or remove) the Pixel 8 Android Virtual Device (AVD) from scratch.

---

## 1. Environment & Toolchain Prerequisites

### Required Paths & Environment Variables
Add or export the following in your shell (`zsh`) before building:

```sh
export PATH="$HOME/.local/bin:$HOME/.cargo/bin:$HOME/Library/Android/sdk/platform-tools:$HOME/Library/Android/sdk/cmdline-tools/latest/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export NDK_HOME="$HOME/Library/Android/sdk/ndk/28.2.13676358"
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
```

### Toolchain Checklist
1. **Java (JDK 17 or 21)**:
   - Installed at `~/.sdkman/candidates/java/current` (JDK 21).
2. **Rust & Android Target**:
   ```sh
   rustup target add aarch64-linux-android
   ```
   *(Optional if testing on x86_64 emulators: `rustup target add x86_64-linux-android`)*
3. **Android SDK Components** (installed via `sdkmanager`):
   ```sh
   sdkmanager \
     "platform-tools" \
     "platforms;android-36" \
     "build-tools;35.0.0" \
     "build-tools;36.0.0" \
     "ndk;28.2.13676358"
   ```
4. **Node.js Dependencies**:
   ```sh
   npm install
   ```

---

## 2. Building the Compact APK (Disk-Space Friendly)

In `src-tauri/Cargo.toml`, `[profile.dev]` is configured with `debug = 0`, `strip = "debuginfo"`, and `incremental = false` so that `libtidings_lib.so` and the resulting APK stay compact (~72 MB instead of 330+ MB).

To build `tidings-pixel8.apk` for your Pixel 8 (`aarch64`) and **immediately clean up** the multi-gigabyte Rust/Gradle intermediate directories afterward:

```sh
export PATH="$HOME/.local/bin:$HOME/.cargo/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export NDK_HOME="$HOME/Library/Android/sdk/ndk/28.2.13676358"
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"

cd /Users/mohammedabdallah/.gemini/antigravity/scratch/tidings-android && \
npx tauri android build --debug --apk --target aarch64 && \
cp src-tauri/gen/android/app/build/outputs/apk/universal/debug/app-universal-debug.apk ./tidings-pixel8.apk && \
rm -rf src-tauri/target src-tauri/gen/android/app/build src-tauri/gen/android/.gradle src-tauri/gen/android/build src-tauri/gen/android/app/src/main/jniLibs ~/.gradle/daemon && \
ls -lh ./tidings-pixel8.apk
```

### Re-generating the Android Gradle Project (Only If Needed)
The customized Android native files (`MainActivity.kt`, `AudioPlaybackService.kt`, `AndroidManifest.xml`) are tracked in Git under `src-tauri/gen/android/`.
If `src-tauri/gen/android/` is ever deleted and needs to be reconstructed from scratch:
1. Run `npx tauri android init`
2. Restore the tracked files from Git:
   ```sh
   git checkout -- src-tauri/gen/android/
   ```

---

## 3. Installing & Testing on a Physical Pixel 8

### Enable Debugging on the Pixel 8
1. Go to **Settings → About phone** and tap **Build number** 7 times.
2. Go to **Settings → System → Developer options** and enable **USB debugging** (or **Wireless debugging**).
3. Plug the Pixel 8 into a data-capable USB-C port on your Mac (not the power-only pass-through port on an HDMI multiport adapter).
4. On the phone, swipe down the USB notification, select **File transfer / Android Auto**, and tap **Allow** on the *"Allow USB debugging?"* prompt.

### Install & Launch via ADB
```sh
# 1. Verify the Pixel 8 is connected
adb devices -l

# 2. Install the APK (replace <SERIAL> if multiple devices are attached)
adb install -r ./tidings-pixel8.apk

# 3. Grant notification permission for lock-screen media playback notification
adb shell pm grant org.tidings.reader android.permission.POST_NOTIFICATIONS

# 4. Launch Tidings
adb shell am start -n org.tidings.reader/.MainActivity
```

### Wireless Debugging Alternative (No USB Cable)
1. Open **Settings → System → Developer options → Wireless debugging** on your Pixel 8.
2. Tap **Pair device with pairing code**, then run:
   ```sh
   adb pair <PHONE_IP>:<PAIRING_PORT> <6_DIGIT_CODE>
   adb connect <PHONE_IP>:<MAIN_PORT>
   ```

---

## 4. Reconstructing the Pixel 8 Emulator (AVD) If Needed

To save ~3.4 GB of disk space, the virtual Pixel 8 emulator (`medium_phone`) has been removed. You can recreate it at any time with the exact Pixel 8 screen specs (`1080×2400`, `420 dpi`, Android 16 / API 36, ARM64):

### Step 1: Ensure System Image & Emulator Are Installed
```sh
export PATH="$HOME/Library/Android/sdk/cmdline-tools/latest/bin:$HOME/Library/Android/sdk/platform-tools:$PATH"
sdkmanager "emulator" "system-images;android-36;google_apis_playstore;arm64-v8a"
```

### Step 2: Create the Pixel 8 AVD (`medium_phone`)
```sh
avdmanager create avd \
  --name medium_phone \
  --package "system-images;android-36;google_apis_playstore;arm64-v8a" \
  --device "medium_phone"

# Optional: Set display name to "Pixel 8" and 1080x2400 @ 420dpi
sed -i '' 's/avd.ini.displayname=.*/avd.ini.displayname=Pixel 8/' ~/.android/avd/medium_phone.avd/config.ini
```

### Step 3: Boot the Emulator
```sh
~/Library/Android/sdk/emulator/emulator -avd medium_phone &
```
*(Or start it via the `android` CLI / Android Device Hub sidecar in Antigravity.)*

### Step 4: Stop & Delete the Emulator Again to Reclaim Disk Space
```sh
# Stop running emulator
adb -s emulator-5554 emu kill

# Delete AVD (~3.4 GB)
avdmanager delete avd --name medium_phone
```

---

## 5. Key Mobile Architecture Files

- [`src-tauri/src/lib.rs`](src-tauri/src/lib.rs):
  - `#[cfg_attr(mobile, tauri::mobile_entry_point)] pub fn run()`
  - `android_cert_workarounds()`: Configures `SSL_CERT_DIR=/system/etc/security/cacerts` for Rustls on Android.
  - `seed_default_feeds()`: Seeds **Team Coco: Conan O’Brien Needs A Friend**, **NPR: Wait Wait... Don't Tell Me!**, and **StarTalk Radio** at the top of `Subscriptions`, plus the **Jupiter Broadcasting** podcast folder.
  - `start_media_server()`: Runs the local loopback proxy (`127.0.0.1`) for article images and podcast audio streaming.
- [`src-tauri/gen/android/app/src/main/java/org/tidings/reader/MainActivity.kt`](src-tauri/gen/android/app/src/main/java/org/tidings/reader/MainActivity.kt):
  - Applies `WindowInsetsCompat` (`systemBars | displayCutout | ime`) padding to `android.R.id.content`.
  - Overrides `onPause()` to call `currentWebView?.onResume()` and `resumeTimers()` so locking the screen does not pause HTML5 `<audio>`.
  - Bridges `TidingsAndroid.openExternal` and `TidingsAndroid.setAudioPlaybackState`, and wires Android back gestures (`OnBackPressedCallback`) to `window.__tidingsBack()`.
- [`src-tauri/gen/android/app/src/main/java/org/tidings/reader/AudioPlaybackService.kt`](src-tauri/gen/android/app/src/main/java/org/tidings/reader/AudioPlaybackService.kt):
  - Foreground `mediaPlayback` service holding `PARTIAL_WAKE_LOCK` and `WIFI_MODE_FULL_LOW_LATENCY` while audio is playing so Android never freezes the app while the phone is locked.
- [`src/lib/player.svelte.ts`](src/lib/player.svelte.ts) & [`src/components/PlayerBar.svelte`](src/components/PlayerBar.svelte):
  - Synchronizes `<audio>` play/pause/ended/close events with `navigator.mediaSession` and `TidingsAndroid.setAudioPlaybackState`.
- [`src-tauri/src/commands.rs`](src-tauri/src/commands.rs):
  - Mobile-compatible `import_opml` and `export_opml` file picker configuration so `.opml` files are selectable in Android's system file picker (`DocumentsUI`).
