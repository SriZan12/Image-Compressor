# Shrink 1.0.0 release candidate

The first production application ID is **com.srizan12.shrink**, selected by the
owner on 31 October 2026. The Kotlin namespace remains
`com.example.imagecompressor`; it is not the installed application ID. Earlier
development installs with that example ID are separate apps and are not migrated.

## Build types

| Type | Application ID | Optimization | Signing | Purpose |
| --- | --- | --- | --- | --- |
| debug | com.srizan12.shrink | No shrinking | Development key | Development and host fixtures |
| qa | com.srizan12.shrink.qa | R8 + resource shrinking; non-debuggable | Development key | Isolated device acceptance of optimized code |
| release | com.srizan12.shrink | R8 + resource shrinking; non-debuggable | Owner's upload key when configured | Play Store candidate |

The QA installation has separate storage and a different identity. It must never
be uploaded as the production app. A release built without signing variables is
an **unsigned candidate**, not an installable or upload-ready release.

## Reproduce verification

Use the repository's JDK/SDK requirements from [validation](../validation/README.md).

```bash
python3 -m unittest discover -s tools/benchmark -p 'test_*.py' -v
python3 -m unittest discover -s tools/validation -p 'test_*.py' -v
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
bash gradlew testDebugUnitTest testReleaseUnitTest lintDebug lintRelease \
  assembleDebug assembleDebugAndroidTest assembleRelease bundleRelease
bash gradlew -PandroidTestBuildType=qa assembleQa assembleQaAndroidTest
```

On this managed workspace, use `/workspace/.image-compressor/gradle.sh`; build
outputs are under `/workspace/.image-compressor/builds/app`.

Inspect the actual packaged APK, not only the source manifest:

The bundle check uses [Google's bundletool 1.18.3](https://github.com/google/bundletool/releases/tag/1.18.3).
Its all-in-one JAR SHA-256 is
`a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`;
CI verifies that checksum before execution.

```bash
mkdir -p app/build/release-verification
"$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" manifest print \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  > app/build/release-verification/manifest.xml
python3 tools/release/check_artifacts.py \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk \
  --aab app/build/outputs/bundle/release/app-release.aab \
  --manifest app/build/release-verification/manifest.xml \
  --mapping app/build/outputs/mapping/release/mapping.txt \
  --output app/build/release-verification/report.json
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 -v 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk
java -jar bundletool-all-1.18.3.jar validate \
  --bundle=app/build/outputs/bundle/release/app-release.aab
```

The guard checks release/privacy metadata, actual class mappings, archive
integrity, 64-bit ELF load alignment and uncompressed native ZIP alignment.
It explicitly reports device and signature acceptance as unverified. Keep its
JSON report, hashes, R8 mapping and signed outputs together for each release.

## Signing and versioning

Keep the upload keystore outside the repository and back it up securely. Use
Play App Signing and an owner-controlled upload key. Configure these environment
variables through a local secure environment or CI secret storage:

- `SHRINK_KEYSTORE_FILE`: path to the upload keystore.
- `SHRINK_KEYSTORE_PASSWORD`: keystore password.
- `SHRINK_KEY_ALIAS`: upload key alias.
- `SHRINK_KEY_PASSWORD`: upload key password.

All four must be supplied together. No passwords or production keys are stored
in this repository. Missing variables produce unsigned release outputs; partial
configuration fails early. QA always uses its separate development signing path.

```bash
bash gradlew assembleRelease bundleRelease \
  -PreleaseVersionCode=1 -PreleaseVersionName=1.0.0
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
jarsigner -verify -strict -verbose -certs \
  -keystore "$SHRINK_KEYSTORE_FILE" -storepass:env SHRINK_KEYSTORE_PASSWORD \
  app/build/outputs/bundle/release/app-release.aab "$SHRINK_KEY_ALIAS"
```

Confirm the certificate fingerprint belongs to the intended upload key.
Plain `jarsigner -verify` is insufficient: it can return zero for an unsigned
bundle. The strict command above verifies against the owner's trusted key/alias
and must exit successfully with a verified signature before upload.

Increase the version code for each later Play upload. Do not change the permanent app ID
after publication. `releaseApplicationId` is an explicit override for a deliberate
separate product; it is not a routine release option.

## Device and publication gates

On a dedicated, connected test device, exercise the optimized build:

```bash
bash gradlew -PandroidTestBuildType=qa connectedQaAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.example.imagecompressor.validation
python3 tools/validation/check_device_results.py \
  app/build/outputs/androidTest-results --api 35
```

API 31 runs the native workflow; API 35 also runs both accessibility smoke tests.
CI now targets this optimized QA build. CI configuration being present is not
evidence of a remote run. Preserve result XML and check the required-test guard.

Before public rollout, record the physical-device matrix in
[the final execution record](../validation/editor-step-6-execution.md): real-photo
quality, latency and peak memory, TalkBack, picker/share destinations, cancellation,
process death, low storage, OEM behavior, tablets and API 31/35/37. Host tests and
synthetic debug benchmarks do not establish these acceptance gates. Repeat smoke
checks on the signed candidate delivered through Play's internal test track.

The [privacy policy](privacy-policy.md) is ready to host on a public URL controlled
by the publisher; it has not been published by this task. The
[store preparation checklist](store-checklist.md) records the remaining Console
and beta decisions. Publishing is a separate owner action.
