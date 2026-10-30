# Step 5: adaptive flow completion

The initial design pass introduced the screens and shared theme. This pass finishes
adaptive behavior and the transitions between selection, settings and results.

## Changes

- Moving between one and two panes preserves the existing Compose content rather
  than creating new comparison controls. Zoom, reveal position and unfinished
  size-limit text survive live window resizing. Each layout keeps its scroll state.
- Adding photos from the selection editor stays in that editor. Initial selection
  still opens compression settings directly. Cancelling the picker or receiving
  only unreadable photos keeps the current selection and screen.
- An empty selection has one usable Add photos action. The editor heading shows
  the photo count, and Home says Add photos when a selection already exists.
- Size and custom-dimension fields expose a Done keyboard action that clears focus
  and hides the keyboard. Reduced window height allows the form and its action to
  scroll above the keyboard rather than squeezing them into fixed panes.
- History metadata and actions use more vertical space on narrow windows or with
  large text. Filenames no longer compete with a thumbnail and chevron for a small
  text column, and action labels retain enough width.
- Batch before/after totals are labeled separately from the photo preview. Every
  result still opens its own comparison and Save/Share actions.
- At Fit, swiping over the comparison photo scrolls the page. Pinching zooms; once
  zoomed, swiping pans the photo. Labeled zoom controls remain available.
- An unavailable original produces an explanation and disables the reveal slider;
  saving and sharing the available compressed result remain usable.

## Verification scope

The native Compose tests cover these specific configurations and behaviors:

| Configuration | Checks |
| --- | --- |
| 320dp width, 200% text | Home selection, stacked results actions, History metadata/action labels |
| 360dp width, 800dp height | Numeric entry with a dispatched 300px IME inset, focus release, primary action above that inset |
| 412 × 915dp | Main screens, empty selection, presets, exact limits, saving/sharing callbacks, missing original, swipe scrolling |
| 600 → 1280 → 840 → 360 → 600dp | Reveal position and zoom survive live changes in available width |
| 1280 → 600dp and saved-state recreation | Invalid size-limit draft remains visible and compression remains disabled |
| 840 × 900dp | Two-pane batch totals, labeled preview, per-photo comparison access |
| 1280 × 800dp, dark theme | Two-pane comparison and actions |
| 640 × 320dp | Primary compression action remains reachable by scrolling |

The width-change tests constrain the app's content at runtime within a host test
activity. The keyboard test dispatches Android window insets; it does not launch
an OEM keyboard or draw a keyboard image. Text scaling is supplied through Compose
density. These checks supplement, rather than establish, physical-device behavior.

Actual TalkBack traversal, nonlinear system font scaling, OEM keyboards, display
cutouts, navigation modes, dynamic wallpaper palettes, and device performance
remain Step 6 release gates. CI and release packaging are outside Step 5.

## Completed checks

On 2026-10-30, `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`
passed. All 88 tests passed (22 native UI tests and 66 other regression tests).
Lint reported 0 errors and 2 dependency-version notices. Both debug APKs were
built. Updated native renders are linked in the [design gallery](README.md).
