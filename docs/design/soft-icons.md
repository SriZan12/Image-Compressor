# Soft 3D icon family

Historical record: the [vector icon and Home startup update](vector-icons-home.md)
replaces the three generated PNGs and Coil artwork loader with native vectors.
The captures and validation below describe the previous revision.

The user's sculpted utility-icon reference replaces the earlier outlined-only
brand direction. The app keeps its Poppins typography and calm layouts, with
warm ivory photo frames, teal surfaces, amber details, and a little lavender.

## Assets and placement

- **Shrink:** two photo frames and inward arrows. The generated transparent PNG
  is used by the adaptive launcher, Android splash, startup, first launch, Home,
  the app header, and Home navigation.
- **Recent:** photo frames with a clock, used in navigation and the empty Recent
  state.
- **Settings:** a rounded teal gear with an ivory ring and amber center, used in
  navigation.
- **Small actions:** original shaded Android vectors for Add photo, Save, Share,
  Compare, and Privacy. Simpler geometry preserves recognition at 20–24dp.
- Directional controls, close/remove, errors, and other small utility controls
  keep their theme-aware symbols. Labels, selected states, and touch targets
  remain part of every interaction; color is not the only cue.

The three PNG masters were generated with `image_gen` from the supplied style
reference. They are copied unchanged into `drawable-nodpi`; the source files in
`/workspace/generated_images` are retained. They total **3,421,347 bytes**.
[Provenance and checksums](icons/provenance.json) identify the bundled assets.
The companion vectors and monochrome logo were drawn specifically for this app.

## Android behavior

The launcher uses a deep-teal adaptive background and an 18% inset foreground.
A separate vector monochrome mark supports Android 13+ themed icons. It preserves
the photo/size-reduction meaning when the launcher applies its own color.

In-app artwork uses Coil with an explicit pixel size, exact-size decoding,
memory caching, and no crossfade or disk caching. The packaged images need no
network requests. A theme-aware symbol is available while an asset loads or if
loading fails. Decorative icons add no spoken labels; existing action labels
and descriptions are preserved. Disabled controls also fade the colored artwork.

## Validation and captures

Validated on 2026-10-30 with:

```sh
/workspace/.image-compressor/gradle.sh testDebugUnitTest \
  --tests '*AppIconResourceTest' --tests '*DesignRenderTest' \
  lintDebug assembleDebug assembleDebugAndroidTest
```

- **36 tests passed:** 34 Compose screen checks and two resource/integration
  checks. The latter check the adaptive/themed safe zone, transparency, decoding
  to the requested display size, and memory-cache reuse for all three PNGs.
- **Lint:** no errors; 33 existing warnings.
- **Build:** debug and instrumentation APKs built successfully. Instrumentation
  was compiled, not run on a connected device.
- Debug APK: `/workspace/.image-compressor/builds/app/outputs/apk/debug/app-debug.apk`.

Review captures:

- [Adaptive, round, and themed launcher icons](screenshots/soft-icons-launcher.png)
- [Home in light mode](screenshots/soft-icons-home-light.png)
- [Settings in dark mode](screenshots/soft-icons-settings-dark.png)
- [Save, Share, and Compare actions](screenshots/soft-icons-actions.png)

Screenshots use Robolectric API 35 and native Canvas; they are actual
app/resource renders, not physical-device captures or generated UI mockups.
Physical OEM launcher, splash transitions, and themed-icon behavior remain
device checks.
