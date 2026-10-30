# Shrink — Image Compressor

Shrink is a privacy-first Android app built with Kotlin and Jetpack Compose. It selects
images through Android Photo Picker, compresses them locally, compares before and after sizes, and
lets the user save or share the result.

## Features

- Single and batch image selection with Android Photo Picker
- Image preview cards with file size, resolution, and format
- JPEG, PNG, and WEBP conversion
- Simple Smaller/Balanced/Best quality/size-limit presets; manual controls under Advanced
- Exact decimal KB/MB upload limits and remembered compression settings
- Large before/after slider with synchronized zoom and pan
- Adaptive phone/tablet layouts and consistent rounded vector icons
- Aspect-ratio preservation
- Bounded target-size search with explicit met/unmet results and optional resizing
- Memory-aware sampled decoding, all eight EXIF orientations and metadata removal
- Per-image batch outcomes, cancellation, retry and interruption recovery
- Durable retained results, duplicate-safe gallery saving and checked sharing
- Room-backed history with availability status and a non-destructive schema migration
- Optional privacy onboarding, system/light/dark themes and Material You wallpaper colors
- Android 12+ support

## Run the app

1. Open this folder in Android Studio and allow Gradle sync to finish.
2. Start an emulator or connect an Android device with USB debugging enabled.
3. Run the `app` configuration.

From a terminal:

```bash
bash gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Production readiness

The approved staged work and current product requirements are tracked in
[the production plan](docs/production-plan.md). The latest device direction is
modern Android phones and tablets; the current minimum is Android 12 / API 31.

See [the compression baseline](docs/benchmarks/README.md) for the fixture corpus,
native Android benchmark commands, measurement limits, and recorded findings.
Step 2's algorithm, limits and validation are documented in
[the compression engine](docs/compression-engine.md).
Step 3's retention policy, recovery behavior and validation are documented in
[reliability and storage](docs/reliability-and-storage.md).
Steps 4–5 implement the supplied visual brief; see [the design system and review
captures](docs/design/README.md). Step 5
[adaptive-flow checks](docs/design/adaptive-ui.md) cover resizing, keyboard insets,
large text and selection/result edge cases. Step 6 adds
[CI, native workflow/accessibility tests and performance reporting](docs/validation/README.md).
Physical-device and accessibility release gates remain open; the
[execution record](docs/validation/step-6-execution.md) separates prepared checks from executed checks.

## Verification

```bash
python3 -m unittest discover -s tools/benchmark -p 'test_*.py' -v
python3 -m unittest discover -s tools/validation -p 'test_*.py' -v
bash gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

## Architecture

- `ui/`: Compose screens and the app-level `ImageCompressorViewModel`
- `data/`: repository contracts, Room history, preferences, and models
- `util/ImageCompressor.kt`: staged input, native decode/transform/encode and atomic output
- `util/compression/`: input validation, memory/geometry policy and bounded target-size search
- `data/storage/OutputStore.kt`: owned files, retention quota and legacy cleanup
- `util/GallerySaver.kt`: journaled, scoped `MediaStore` publication
- `util/ShareUtils.kt`: share-sheet intents backed by `FileProvider`

Photo Picker grants access only to images selected by the user, so the app does not request broad
gallery-read access. Gallery saving uses scoped storage without a runtime storage
permission. Retained outputs use up to 512 MiB and stay until explicitly removed;
clearing history keeps originals and exported gallery copies.
