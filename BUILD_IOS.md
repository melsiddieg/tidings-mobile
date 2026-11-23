# Building Tidings for iOS (iPhone)

This repository includes a full Tauri 2 iOS port alongside the Android and desktop targets.

## Prerequisites (macOS)

1. **Xcode** (from the Mac App Store) with the iOS SDK & iOS Platform component installed (**Xcode → Settings → Components → iOS**):
   ```bash
   xcode-select -p
   xcodebuild -showsdks | grep iphoneos
   ```
2. **Rust, iOS targets, and `llvm-tools`** (`llvm-tools` provides `llvm-objcopy`, required by `swift-rs` on Xcode 27+ so Swift `@_cdecl` plugin entry points remain globally visible):
   ```bash
   rustup target add aarch64-apple-ios aarch64-apple-ios-sim
   rustup component add llvm-tools
   ```
3. **Node.js (v20+)** and project dependencies:
   ```bash
   npm install
   ```
4. **XcodeGen & CocoaPods** (for regenerating `tidings.xcodeproj` if `project.yml` changes):
   ```bash
   brew install xcodegen cocoapods libimobiledevice
   ```

## Signing & Running on Your iPhone (Free Apple ID Supported)

Apple requires an iOS code-signing identity to install apps onto a physical iPhone. You can use a **free Apple ID (Personal Team)** (7-day re-signing) or a paid Apple Developer account (1-year signing / TestFlight):

1. Open the Xcode project via Tauri (or open once just to configure Signing & Capabilities):
   ```bash
   npx tauri ios dev --open
   ```
   > **Note:** Always use `npx tauri ios dev` / `npx tauri ios build` (with or without `--open`) when building or clicking **Run (▶)** in Xcode, because Xcode's `Build Rust Code` pre-build script connects to the Tauri CLI process to compile `libapp.a`.
2. In Xcode, go to **Xcode → Settings → Accounts** and sign in with your Apple ID.
3. Select the **`tidings`** project → **`tidings_iOS`** target → **Signing & Capabilities**:
   - Check **Automatically manage signing**.
   - Select your **Team** (Personal Team or Apple Developer Team).
   - If using a free Personal Team and `org.tidings.reader` is already taken, change **Bundle Identifier** to a unique value (for example `org.tidings.reader.<yourname>`).
4. Connect your iPhone via USB, unlock it, and tap **Trust This Computer**.
5. On your iPhone, enable **Developer Mode** if prompted (**Settings → Privacy & Security → Developer Mode**).
6. Select your iPhone as the run destination in Xcode and click **Run (▶)** — or build directly from the terminal:
   ```bash
   # Live development build directly to a connected iPhone
   npx tauri ios dev

   # Optimized release IPA build (requires signing team configured)
   npx tauri ios build --export-method debugging
   ```
7. On first launch with a Personal Team certificate, trust your developer profile on the iPhone under **Settings → General → VPN & Device Management**.

## Unsigned Compile & Link Verification (No Signing Identity Needed)

To verify the frontend, Rust ARM64 iOS static library, Objective-C++ bridge, and Mach-O link without a signing identity:

```bash
npm run check && npm run build
cargo build --manifest-path src-tauri/Cargo.toml --target aarch64-apple-ios --lib --features tauri/custom-protocol
# Or archive via Tauri CLI (requires Xcode's iOS Platform runtime installed):
npx tauri ios build --debug --ci --no-sign --archive-only
```

## Regenerating `tidings.xcodeproj`

If you edit [`src-tauri/gen/apple/project.yml`](src-tauri/gen/apple/project.yml), regenerate the Xcode project and `Info.plist` with:

```bash
cd src-tauri/gen/apple
xcodegen generate
```

## iOS Security, Privacy & Audio Architecture

The iOS port preserves Tidings' privacy-first architecture with native iOS hardening:

- **TLS Root Verification (`rustls` + WebPKI roots)**: iOS sandboxes do not expose `/etc/ssl/certs`, so the iOS target bundles Mozilla's curated `webpki-roots` CA store for `reqwest`/`rustls`.
- **No iCloud / Finder Backup of `tidings.db`**: `AppState::init` sets `NSURLIsExcludedFromBackupKey = true` on `data_dir` and `tidings.db`, preventing feed credentials, proxy settings, and reading history from being copied to iCloud.
- **Hardware File Data Protection**: `data_dir` and `tidings.db` are marked `NSFileProtectionCompleteUntilFirstUserAuthentication` at runtime (no paid entitlements required), encrypting the database at rest while keeping background podcast playback working when the screen locks.
- **Strict App Transport Security (ATS)**: `NSAllowsArbitraryLoads` is `false`; cleartext HTTP is only permitted for local networking (`NSAllowsLocalNetworking: true`) so the token-authenticated `127.0.0.1` loopback media server and optional LAN AI model servers (e.g. Ollama) work while external traffic stays HTTPS-only.
- **Background & Lock-Screen Podcast Audio**: `UIBackgroundModes: [audio]` + `AVAudioSession` (`.playback` / `.spokenAudio`), `WKWebView` background throttling disabled on iOS 17+, and `media_server` loopback range streaming keep podcasts playing seamlessly with the screen locked, silent switch enabled, and Lock Screen / AirPlay / Bluetooth controls active.
- **App Switcher & Screen Recording Privacy Shield**: `TidingsIos.mm` overlays a native `UIVisualEffectView` blur screen whenever the app resigns active (preventing unblurred App Switcher snapshots) or when `UIScreen.isCaptured` is true (screen recording / AirPlay mirroring). Note: iOS does not expose a public OS API equivalent to Android's `FLAG_SECURE` to block hardware screenshots of a `WKWebView`.
- **Hardened Mobile SVG & Security-Scoped Files**: Untrusted article SVGs are blocked in-process (only small feed icons <= 256 KB are rasterized after XML depth/`<use>` pre-checks), and OPML/Backup/Export dialogs use `tauri-plugin-fs` security-scoped access (`startAccessingSecurityScopedResource` / `stopAccessingSecurityScopedResource`).
- **iPhone Safe Areas & Touch Ergonomics**: Respects Dynamic Island, notch, and Home Indicator insets (`viewport-fit=cover` + `env(safe-area-inset-*)`) and prevents WebKit input focus auto-zoom (`16px` input sizing on iOS).

## Reclaiming Disk Space After Building

Rust cross-compilation artifacts and Xcode DerivedData can consume several gigabytes. Clean them up after installing onto your iPhone:

```bash
rm -rf src-tauri/target src-tauri/gen/apple/build src-tauri/gen/apple/Externals dist
rm -rf ~/Library/Developer/Xcode/DerivedData/tidings-*
```
