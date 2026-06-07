# Image Compressor

Image Compressor is a privacy-first Android app built with Kotlin and Jetpack Compose. It selects
images through Android Photo Picker, compresses them locally, compares before and after sizes, and
lets the user save or share the result.

## Features

- Single and batch image selection with Android Photo Picker
- Image preview cards with file size, resolution, and format
- JPEG, PNG, and WEBP conversion
- Quality slider, target-size presets, percentage resize, and custom dimensions
- Aspect-ratio preservation
- Off-main-thread compression with sampled bitmap decoding and EXIF-aware rotation
- Batch progress, original/compressed comparison, gallery save, and Android share sheet
- Room-backed compression history with saved path and deletion controls
- Privacy onboarding plus system, light, and dark appearance settings
- Android 8.0+ support

## Run the app

1. Open this folder in Android Studio and allow Gradle sync to finish.
2. Start an emulator or connect an Android device with USB debugging enabled.
3. Run the `app` configuration.

From a terminal:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Verification

```bash
./gradlew testDebugUnitTest assembleDebug
./gradlew lintDebug
```

## Architecture

- `ui/`: Compose screens and the app-level `ImageCompressorViewModel`
- `data/`: repository contracts, Room history, preferences, and models
- `util/ImageCompressor.kt`: sampled decode, scaling, EXIF rotation, and target-size iteration
- `util/GallerySaver.kt`: scoped `MediaStore` saving and Android 8/9 fallback
- `util/ShareUtils.kt`: share-sheet intents backed by `FileProvider`

Photo Picker grants access only to images selected by the user, so the app does not request broad
gallery-read access. Android 8 and 9 request legacy write permission only when saving a compressed
image to the public Pictures directory. Android 10+ uses scoped storage and needs no runtime storage
permission.
