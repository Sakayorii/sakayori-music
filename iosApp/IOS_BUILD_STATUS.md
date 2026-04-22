# iOS Build Status For SakayoriMusic

## What This Session Delivered

### ✅ Bug Fixes (landed on all platforms)

1. **Bug 1: Download broken** (`core/media/media-jvm/.../DownloadUtils.kt`)
   - `downloadTrack()` silently aborted if song not in DB
   - Fix: insert minimal `SongEntity` placeholder before download if missing
   - Added logging for visibility

2. **Bug 2: VLC_PLAYBACK_FAILED on cached tracks** (`core/media/media-jvm/.../VlcPlayerAdapter.kt`)
   - Retry logic trusted cached `NewFormatEntity.expiredTime` — was stale
   - Fix: on VLC error, force `invalidateFormat` + `refreshIfExpiring(Long.MAX_VALUE/2)` to bypass expiry check
   - Added 500ms delay before retry to let refresh complete

3. **Bug 3: End of track no advance/loop** (`core/media/media-jvm/.../VlcPlayerAdapter.kt`)
   - VLC sometimes doesn't emit `finished()` event at end of stream
   - Fix: position watchdog — if pos >= duration - 1.2s AND unchanged for 2.5s AND state PLAYING → force `handleTrackEndInternal()`
   - This triggers repeat/loop/advance logic correctly

### ✅ iOS Build Scaffolding

- `composeApp/build.gradle.kts`: iOS targets enabled (`iosArm64`, `iosSimulatorArm64`)
- `composeApp/src/iosMain/`: stub `actual` implementations for basic platform APIs
- `iosApp/`: Xcode project scaffolding
  - `iosApp.xcodeproj/project.pbxproj` — minimal Xcode project
  - `iosApp/iOSApp.swift` — SwiftUI entry wrapping Compose UIViewController
  - `iosApp/Info.plist` — iOS 15+ target, audio background mode, URL schemes
  - `iosApp/LaunchScreen.storyboard` — dark splash

### ✅ GitHub Actions Workflow

`.github/workflows/build.yml` builds 3 platforms, uploads artifacts:
- **Linux**: DEB + RPM (ubuntu-latest)
- **macOS**: DMG + PKG (macos-14)
- **iOS**: Unsigned IPA (macos-14)

Runs on `push to main`, `tag v*`, or `workflow_dispatch`.

## What Still Needs Work (iOS)

### 🔴 Compile-Blocking

The `commonMain` source set uses ~30 `expect fun` declarations. iOS needs `actual` for each. This session provided 15. Missing:

```
expect fun createEqualizerController(audioSessionId: Int): EqualizerController
expect fun getPackageName(): String
expect fun getFileDir(): String
expect fun changeLanguageNative(code: String)
expect fun createWebViewCookieManager(): WebViewCookieManager
expect fun clearWebViewCacheAndCookies()
expect fun PlatformWebView(...)
expect fun DiscordWebView(...)
expect fun ImageBitmap.toByteArray(): ByteArray?
expect fun Image.toImageBitmap(): ImageBitmap
expect fun HorizontalScrollBar(...)
expect fun photoPickerResult(...)
expect class PlatformBackdrop
expect fun Modifier.layerBackdrop(...)
expect fun Modifier.drawBackdropCustomShape(...)
expect fun rememberBackdrop(): PlatformBackdrop
expect fun openEqResult(audioSessionId: Int): OpenEqLauncher
expect fun MediaPlayerView(...)
expect fun MediaPlayerViewWithSubtitle(...)
expect fun getScreenSizeInfo(): ScreenSizeInfo
expect fun KeepScreenOn()
expect fun rememberIsInPipMode(): Boolean
expect fun filePickerResult(...)
expect fun fileSaverResult(...)
expect object CacheCleaner
expect fun FullScreenRotationImmersive(...)
expect fun LiquidGlassAppBottomNavigationBar(...)
```

Each needs an `actual` stub in iosMain. Most can return no-op defaults. Complex ones (PlatformWebView, LiquidGlass, MediaPlayerView) need real UIKit bindings.

### 🔴 iOS Media Playback (Critical)

Currently no `AVPlayer` wrapper exists. `MediaPlayerInterface` doesn't have an iOS actual. Need:

```
core/media/media-ios/     (new module)
├── build.gradle.kts
└── src/iosMain/kotlin/.../IosAVPlayerAdapter.kt  (~800 lines)
```

Mirrors `VlcPlayerAdapter.kt` (~2000 lines) and `CrossfadeExoPlayerAdapter.kt` using AVPlayer:
- `AVPlayer` for single-track playback
- `AVQueuePlayer` for queue
- `AVPlayerItem` for media items
- `MPNowPlayingInfoCenter` for lock screen metadata
- `MPRemoteCommandCenter` for play/pause/skip remote control
- `AVAudioSession` background audio category
- `CMTime` / `CMTimeRange` for seek/duration

### 🟡 YouTube Extraction On iOS

NewPipe extractor (Java library) doesn't run on iOS. Need one of:

- **Option A**: Port cipher deobfuscation to pure Kotlin (use JavaScriptCore for JS signature execution) — 3-4 weeks
- **Option B**: Server-side proxy — run extraction on GCP VM, iOS fetches pre-resolved URLs — 1 week
- **Option C**: Wrap yt-dlp-swift Swift binding — 1 week

Current iOS stub in `core/service/kotlinYtmusicScraper/src/iosMain/extractor/Extractor.ios.kt` returns empty list — playback won't actually work without one of the above.

### 🟡 Koin DI Wiring For iOS

`composeApp/src/iosMain` needs a Koin module equivalent to `MediaHandlerModule` that provides `MediaPlayerHandler`, `DownloadHandler`, etc. using iOS AVPlayer backend. Not yet created.

### 🟡 Icon Set

`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` needs 18 sizes generated from `composeApp/icon/circle_app_icon.png`. Can be auto-generated online (no art skill needed):
- https://appicon.co/
- https://icon.kitchen/
Drop resulting folder into `iosApp/iosApp/Assets.xcassets/`.

## Realistic Next Steps To Actually Build IPA

1. **Push current changes** to a branch → GitHub Actions runs → iOS job will FAIL with compile errors from missing actuals. Copy error list.
2. **Implement missing actuals** iteratively as CI reports them. Each is ~10-20 lines of Kotlin/Native + UIKit.
3. **Create `core/media/media-ios/` module** with AVPlayer wrapper. Can start minimal (play/pause/seek only).
4. **Create Koin iOS module** wiring iOS-specific MediaPlayerHandler.
5. **Add app icon assets** — online generator.
6. **Test on simulator** via Xcode on real Mac (CI only builds; doesn't test).
7. **TestFlight sign** — need Apple Developer Program ($99/year). Add fastlane or manual xcrun altool upload in CI.

## Estimated Remaining Effort

- **Minimum compile** (stubs for everything): 1-2 days Mac work
- **Minimum viable iOS** (plays music): 1-2 weeks
- **Production-ready iOS**: 2-3 months

## Files Changed In This Session

| File | Change |
|---|---|
| `core/media/media-jvm/.../DownloadUtils.kt` | Bug 1 fix — placeholder insert + logging |
| `core/media/media-jvm/.../VlcPlayerAdapter.kt` | Bug 2 + 3 fixes — force refresh + end-of-track watchdog |
| `composeApp/build.gradle.kts` | iOS targets enabled |
| `composeApp/src/iosMain/kotlin/com/sakayori/music/expect/IosActuals.kt` | New — ~15 stubs |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | New — minimal Xcode project |
| `iosApp/iosApp/iOSApp.swift` | New — SwiftUI entry |
| `iosApp/iosApp/Info.plist` | New — iOS 15+, audio background |
| `iosApp/iosApp/LaunchScreen.storyboard` | New — dark splash |
| `.github/workflows/build.yml` | Rewritten — Linux + macOS + iOS matrix, artifact upload only |

## Truth About This Delivery

The CI workflow structure is correct. Bug fixes are solid. But iOS build will **NOT produce a working IPA** on first CI run because ~25 actual stubs are still missing. Each push will reveal more compile errors that need filling in. The scaffolding is in place to make that iteration fast, but it's still 1-2 days of Mac-side work to get a first successful CI green light, then weeks to have actual audio playing on iPhone.

This is consistent with the plan document at `.claude/memory/ios_version_plan.md`: iOS is a 3-4 month effort.
