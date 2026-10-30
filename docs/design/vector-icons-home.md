# Scalable icons and predictable Home startup

## Icon quality and consistency

The previous three brand/navigation icons were generated PNGs, displayed through
Coil. Their textured edges softened at navigation sizes, and the launcher also
used a bitmap foreground. They are replaced with original native Android vector
illustrations: curved paths, layered highlights, and gradients in the same teal,
ivory, amber, and lavender palette. These resources render at the requested size
and device density without enlarging a fixed bitmap.

- Launcher, splash, Home, Recent, and Settings use the new vector family.
- System is a divided disc; Light is an amber sun; Dark is a lavender crescent.
- Remove uses a teal bin with an ivory lid. Add photo, Save, Share, Compare, and
  Privacy use the existing matching shaded vectors.
- Arrows, close controls, and status symbols retain simple theme-aware vectors
  for clarity at small sizes. Text labels and accessible touch targets remain.
- The adaptive launcher has a 24% foreground inset and retains a separate
  monochrome vector for Android themed icons.
- All icon rendering is synchronous through Compose's vector painter. There are
  no icon image requests, asynchronous placeholders, or fixed bitmap decode sizes.
  Coil remains responsible for user photo previews.

The removed PNGs totaled 3,421,347 bytes. Their historical checksums remain in
[the original provenance record](icons/provenance.json); the files are available
in the preceding Git revision. The new vectors are original project artwork.

## Navigation behavior

Previously, session restoration treated a persisted batch as a reason to show
Results, and could also restore a saved destination or show onboarding. A new
ViewModel now always starts and finishes initialization on Home. Session recovery
restores work rather than choosing a destination.

Drafts, previous batch results, and retained export receipts are preserved. Home's
**Continue** opens the draft; **Review previous photos** opens batch results.
Back from those reviewed results returns to Home, including after comparison or
retry. Results from a newly started compression still return to the editor.
First launch also goes directly to Home after splash. Existing in-memory sessions
keep their current screen across rotation or ordinary background/foreground
transitions, so returning from a picker or share sheet does not interrupt editing.
Repository updates refresh data without triggering navigation.

## Review captures

- [Vector family at illustration and button sizes](screenshots/vector-icons-family.png)
- [Adaptive, round, and themed launcher](screenshots/vector-icons-launcher.png)
- [Home in light mode](screenshots/vector-icons-home-light.png)
- [Settings in dark mode](screenshots/vector-icons-settings-dark.png)
- [Home with previous results](screenshots/home-previous-results.png)

These are real Android/Compose renders under Robolectric's native graphics
renderer, not generated UI mockups or physical-device screenshots.

## Verification

Validated on 2026-10-30:

```sh
/workspace/.image-compressor/gradle.sh testDebugUnitTest lintDebug \
  assembleDebug assembleDebugAndroidTest
```

- **214 tests passed, one optional benchmark skipped** (215 discovered).
- Native vector resources render at 24, 48, and 192 pixels on API 31 and 35.
  Adaptive foreground and monochrome artwork fit Android's launcher safe circle.
- Compose checks cover 35 screen scenarios, including explicit review from Home,
  theme selection, large text, tablet layouts, and removal confirmation.
- Navigation regressions cover every saved destination and onboarding preference,
  retained batch states, delayed initialization, repeated repository/resume events,
  comparison, retry, and correct Back destinations.
- Lint completed with **0 errors and 33 existing warnings**.
- Debug and instrumentation APKs built successfully. Instrumentation was compiled,
  not run on a connected device.
- Debug APK: `/workspace/.image-compressor/builds/app/outputs/apk/debug/app-debug.apk`
  (34,152,689 bytes, about 3.4 MB smaller than the previous build).

Physical OEM launcher rendering and actual device launch timing remain device
checks; host-rendered captures do not measure those behaviors.
