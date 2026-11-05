# Editor Step 5 — supporting flows and accessibility

This step aligns the surrounding screens with the live editor. It does not
complete the production/device validation in Step 6.

## Changes

- First launch describes the task in one sentence: choose a photo, set the upload
  limit, save a copy. The shorter top spacing keeps the next action closer on
  small screens. Startup has a plain, scrollable status for short windows, with
  no spinner or artificial delay. Android Photo Picker remains the system
  selection experience.
- Home separates **Continue** from **Choose new photos**. A cancelled picker, or
  a replacement containing no readable photos, preserves the existing selection
  and saved result. A successful replacement keeps the remembered size settings.
- Selection offers **Choose size** and **Add photos**, with individually labelled
  removal controls and a bounded tablet grid. Opening photos has a plain status
  announcement rather than an animated indeterminate bar.
- Batches have a selectable thumbnail strip. Only the chosen sample runs through
  the temporary live processor. Its caption explains that settings apply to every
  photo, and **Size limit per photo** distinguishes the limit from a batch total.
  **Prepare N photos** starts the existing sequential batch job. This explicit
  action avoids processing and retaining up to 30 outputs on every slider change.
- The chosen sample survives saved-state restoration; removing it selects the
  first remaining photo. Switching samples releases the previous temporary
  artifact. Batch samples can also use the editor's comparison overlay.
- Comparison remembers that it was open across recreation and waits for a fresh
  verified preview before displaying it again. Its loading state now uses the
  existing geometry-matched placeholder, including reduced-motion behavior.
- Batch results keep total savings and individual detail. An over-limit warning
  sits next to Save/Share; per-photo details expose the measured integer byte count.
  Failed batches can return to size settings even if no photo succeeded.
- Recent photos uses one simple preview row and named Share/Remove actions per
  photo. Unavailable results have a **Choose new photos** recovery action. Preview
  prefers the app's retained copy when present, matching the sharing behavior.
- Settings headings support screen-reader heading navigation. Removal confirmations
  survive recreation, and still explain that originals and gallery copies remain.
- At large text sizes, footer actions join the scrollable content, allowing the
  photo, instructions, and controls to remain reachable in a small window.
  Root navigation uses complete text buttons that wrap as needed, keeping words
  intact instead of squeezing them into fixed icon columns. Normal-size text
  retains the existing outlined-icon navigation bar or tablet rail.

## Verification

On 2026-10-30, the cloud command below completed successfully:

```sh
/workspace/.image-compressor/gradle.sh testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

- **207 tests discovered: 206 passed, 0 failures/errors, 1 skipped**. The skipped
  test is the existing opt-in photo-opening measurement. This step adds 13 tests.
- Host coverage includes cancelled/unreadable replacement selection, the 30-photo
  replacement boundary, sample switching and cleanup, saved sample restoration,
  comparison restoration while a fresh preview is pending, failed-batch recovery,
  exact-byte and over-limit presentation, named Recent actions, and 200% navigation.
- Existing tests also pass for split-window resizing, short landscape, custom-size
  draft restoration, simulated IME insets and Done, missing originals, partial
  batches, editor export ownership, real native encoding, and fallback 4.5:1 text
  contrast. The startup status is checked at 480 × 240dp and 200% text.
- **Lint: 0 errors, 33 warnings** (31 unused resources, 2 dependency-update notices).
  The unused-resource count includes the old selection **Done** label, replaced by
  **Choose size**. No lint checks were suppressed for this work.
- Debug and instrumented-test APKs built. The expanded Accessibility Test Framework
  suite covers first launch, Home, selection, editor/comparison, Recent, and Settings
  in light/dark mode; it was **compiled, not run on a device**.

Build log: `/workspace/.image-compressor/logs/editor-step5-validation.log`.
Debug APK: `/workspace/.image-compressor/builds/app/outputs/apk/debug/app-debug.apk`.

Captures are actual Compose screens drawn through Android's native Canvas
under Robolectric API 35, not physical-device screenshots. Fixture photographs
come from the existing licensed NASA corpus. No generated UI mockups are used.
Each PNG has adjacent JSON with its source test, pixel dimensions, theme, text
scale, and checksum. Files are copied unchanged; earlier-step captures are kept.
The batch review intentionally supplies an impossible 1-byte limit to exercise
the failure presentation; the tablet sample uses a supplied measured JPEG fixture.

| Review screen | Native capture |
| --- | --- |
| First launch | [Phone](screenshots/editor-step5-first-launch-phone.png) |
| Continue or start fresh | [Home](screenshots/editor-step5-home-phone.png) |
| Selection | [Choose size](screenshots/editor-step5-selection-phone.png) |
| Selected batch sample | [Dark tablet](screenshots/editor-step5-batch-tablet-dark.png) |
| Recent photos | [Phone](screenshots/editor-step5-recent-phone.png) |
| Unavailable result recovery | [Phone](screenshots/editor-step5-recent-recovery-phone.png) |
| Exact bytes and over-limit batch | [Scrolled to photo detail](screenshots/editor-step5-batch-review-phone.png) |
| Navigation at 200% text | [320dp phone](screenshots/editor-step5-navigation-large-text.png) |
| Startup in a short window | [480 × 240dp, 200% text](screenshots/editor-step5-startup-short-window.png) |

## Device acceptance still pending

The cloud workspace has no connected Android device or hardware virtualization.
Host state, semantics, layout, insets, and contrast checks cannot establish actual
TalkBack traversal, switch/keyboard navigation, Android nonlinear font scaling,
OEM keyboards, wallpaper palette contrast, or physical-device rotation behavior.
The instrumented accessibility suite must run on API 34+ hardware, with manual
checks on the supported API 31+ range. Device latency and memory measurements also
remain Step 6 release gates; they are not inferred from host rendering time.
