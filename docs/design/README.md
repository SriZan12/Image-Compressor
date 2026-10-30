# Shrink design system and adaptive UI

The current redesign starts with the [new six-step editor plan](editor-plan.md).
See [Editor Step 1](editor-step-1.md) for Poppins, current app captures, component
states, and validation, and [Editor Step 2](editor-step-2.md) for photo opening,
viewport-aware previews, and measured cache behavior. [Editor Step 3](editor-step-3.md)
adds temporary live previews and measured size feedback. [Editor Step 4](editor-step-4.md)
connects verified export and comparison. [Editor Step 5](editor-step-5.md) aligns
the supporting flows, batch previews, recovery actions, and large-text navigation.
The records below describe the earlier production UI;
their typography, screen flow, captures, and test counts are historical.

Shrink is the working product name chosen from the supplied brief. The design aims
for calm, clear, trustworthy photo compression for everyday Android users.
The existing package identity and `Pictures / Image Compressor` export folder stay
stable; the interface names the actual folder where copies are saved.

## Visual system

- Material 3 semantic colors throughout, with warm neutral light surfaces and deep
  neutral dark surfaces. Deep teal is the fallback action color. Android 12+
  wallpaper colors are on by default and can be disabled in Settings.
- Green status roles indicate positive savings or completed saves. Larger outputs,
  unknown original sizes and unmet limits are described explicitly. Errors use red.
  Text and icons accompany status colors.
- System sans typography: 16sp body / 24sp line height, 14sp secondary body / 20sp,
  22sp screen titles, 28–40sp headings and savings. Font scaling remains enabled.
- Spacing: 4, 8, 12, 16, 20, 24 and 32dp. Content uses 20dp phone margins and 24dp
  wide-window margins. Shape radii: 8, 12, 16, 24 and 32dp.
- Primary actions have a minimum 56dp height and flexible text. Icon controls use
  Material's 48dp interaction targets. Actions stack when width or text size needs it.
- The original frame/inward-arrow brand vector is used in the app, native splash,
  adaptive launcher icon and Android themed monochrome icon. There is no timed
  splash delay. The in-app loader appears only during actual initialization.

## Flows and responsive behavior

- A short, optional onboarding page leads directly to Android Photo Picker.
- Home exposes Select photos, remembered settings, privacy and recent activity.
- Picking photos opens presets directly. Edit selection provides a responsive grid.
- Presets: Smaller (quality 65, 75% dimensions), Balanced (quality 80), Best quality
  (quality 92), and an exact per-photo upload limit. Presets create JPEG copies;
  Advanced offers JPEG/PNG/WebP, encoder quality, resizing and proportions.
- Upload limits use decimal KB/MB (1 MB = 1,000,000 bytes), parsed without rounding
  upward. Invalid limits disable compression. Manual settings are remembered.
  PNG explains that encoder quality does not reduce its lossless file size.
- Compression shows actual batch progress and cooperative cancellation. Partial
  success, interruption, retry and saving failures preserve the existing recovery flow.
- Results embed a large before/after comparison. Both images use the same fit,
  zoom and pan. A standard accessible slider controls the original-image reveal;
  pinch gestures and labeled zoom buttons provide alternatives. Originals are
  never overwritten, and missing source previews do not prevent sharing a result.
- Savings and actual file sizes are visible, with Compress and Save/Share kept in reach on
  phones. Batch results show total savings and open individual comparisons.
- Two panes start at 840dp of available content width and 480dp of height. Large
  text raises the width threshold to 1080dp. Narrow or short windows scroll in one
  column. Root navigation changes to a rail on wide windows at normal text sizes.
- History distinguishes copies retained in the app, exported copies and unavailable
  files. Destructive cleanup dialogs explain that originals/gallery copies remain.
- Settings includes System/Light/Dark, wallpaper colors, storage usage and privacy.

## Verification and review images

`DesignRenderTest` renders actual Compose screens with Android API 35 native
Canvas under Robolectric. It also exercises selection, upload-limit validation,
comparison controls, save/share reachability, tablet placement, oversized text,
short landscape windows, themes, history removal and interrupted batches. Step 5
adds live window resizing, saved-state drafts, IME insets, large-text History,
empty selection, explicit batch totals, missing originals and photo swipe handling.
See [the adaptive flow completion notes](adaptive-ui.md) for the exact matrix.
It uses the licensed NASA astronaut fixture and a real JPEG encode for previews.
Rendered captures omit operating-system chrome; they are host renders, not
physical-device screenshots or performance measurements.

Run `./gradlew testDebugUnitTest`. Fresh captures are written to
`app/build/design/` (the cloud workspace remaps this build directory externally).
Selected review captures are stored below after visual inspection. Phone captures
use a 412 × 915dp window; tablet comparison uses 1280 × 800dp. The 200% text
captures use 320dp widths and are scrolled to the relevant controls.

| Screen | Native render |
| --- | --- |
| Onboarding | [Light](screenshots/onboarding-light.png) |
| Home | [Light](screenshots/home-light.png) · [200% text](screenshots/home-large-text.png) |
| Edit selection | [Light](screenshots/selection-light.png) |
| Compression presets | [Light](screenshots/presets-light.png) |
| Progress | [Light](screenshots/progress-light.png) |
| Results | [Light](screenshots/results-light.png) · [200% text](screenshots/results-large-text.png) |
| Tablet comparison | [Dark](screenshots/compare-tablet-dark.png) |
| History | [Light](screenshots/history-light.png) · [200% text](screenshots/history-large-text.png) |
| Settings | [Dark](screenshots/settings-dark.png) |
| Batch results at 840dp | [Tablet](screenshots/batch-tablet.png) |
| Comparison after live resize | [600dp content window](screenshots/compare-split-window.png) |
| Size entry with simulated keyboard inset | [360dp phone](screenshots/size-limit-keyboard.png) |

The resize capture includes unused test-activity space to the right of the app.
The IME capture reserves the bottom 300px without drawing an OEM keyboard.

Validation on 2026-10-30: `testDebugUnitTest assembleDebug
assembleDebugAndroidTest lintDebug` passed. All 88 tests passed, including 22
native Compose rendering/interaction tests. Lint reports 0 errors and 2 dependency
update notices (Kotlin/Compose compiler and coroutines-test). Coroutines-test stays
aligned with the app's resolved runtime version. Both debug APKs build; this is
not a claim that instrumentation ran successfully on a physical device.

Fallback text/background roles have automated 4.5:1 contrast checks. Remaining
release checks include physical TalkBack traversal and gestures, OEM wallpaper
palettes, platform splash/theme transitions, actual keyboard/insets, Android
nonlinear font scaling and the complete device matrix in the production plan.
These changes do not establish release readiness on their own.

## Asset provenance

The `ic_*.xml` action vectors are converted from Google Material Symbols Rounded,
24px optical size, from
https://github.com/google/material-design-icons/tree/master/symbols/web.
They are licensed under Apache 2.0; see [the license](material-symbols-LICENSE.txt).
The Shrink brand/launcher vectors were created for this app. The photo fixture and
its attribution are described in [the corpus notice](../../app/src/androidTest/assets/compression/NOTICE.md).
