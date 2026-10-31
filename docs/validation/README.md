# Step 6 — validation and release gates

This guide originally accompanied the first production plan. The redesigned
editor's current [Step 6 execution record](editor-step-6-execution.md) and
[release guide](../release/README.md) supersede its older counts and debug-only
CI examples below. CI now also builds optimized release artifacts and runs device
smoke checks against the isolated optimized QA application.

Step 6 adds continuous checks and repeatable device validation. It does **not**
establish production readiness by compiling tests. Device execution, physical
performance and human accessibility review remain open gates.

## Automated coverage

[Android validation](../../.github/workflows/android.yml) runs on pull requests,
main-branch pushes and manual dispatch. It is prepared locally; no GitHub Actions
run has been triggered or observed from this cloud workspace.

| Job | Checks | Evidence retained by CI |
| --- | --- | --- |
| Host | JVM/Robolectric tests, native Compose renders, Android lint, benchmark/report-tool tests, debug app and instrumentation APK builds | Test/lint reports and actual native renders for 14 days; APKs for 7 days |
| API 31 emulator | Real compression → Room result → readable share URI → MediaStore save → duplicate save → activity recreation | Connected-test XML/HTML for 14 days |
| API 35 emulator | The same workflow plus Android Accessibility Test Framework checks in light and dark themes | Connected-test XML/HTML for 14 days |

The device job checks that the expected named tests executed successfully. An empty
report directory, skipped required test, failed test or duplicate stale report
cannot pass. API 31 deliberately skips the two accessibility tests: Compose's
accessibility checker requires API 34+ and does not support Robolectric.

The native workflow test uses real storage and codecs. Only the system picker's
URI handoff is injected, using a licensed fixture via FileProvider. It verifies
an exact 50,000-byte limit, unchanged original bytes, readable decoded output,
byte-identical gallery export, the gallery folder and publication flag, save
idempotence, and state after activity recreation. It does not automate the system
picker or share destination and does not simulate process death.

Accessibility tests render the production shell/screens with fixture data. Actions
exercise selection, Advanced controls, the comparison slider/zoom, Share, navigation
and settings. Root-view error checks are enabled without suppressions. These are
smoke checks, not complete screen-reader or dynamic-color acceptance.

Existing host tests cover engine policy/codecs, EXIF/alpha, size limits, migrations,
quota/storage failures, cancellation/retry, session recovery, share grants and
responsive layouts. New merged-package privacy tests guard against network/broad
media permissions, exported file providers and enabled app backups.

CI uses pinned action revisions, read-only repository permissions, no checkout
credentials retained, and a verified Gradle distribution checksum. Weekly Dependabot
configuration proposes Gradle/action updates; it does not auto-merge them. A pinned
version or clean lint report is not proof of absence of dependency vulnerabilities.

## Run locally

Use JDK 21 for Gradle and JDK 17 for compilation, Android SDK platform 37 and build
tools 36.0.0. CI installs these through the shared Android setup action. Existing
tracked local/generated files are not silently removed; CI rewrites `local.properties`
in its disposable checkout, uses a fresh project cache, and runs `clean`.

```bash
python3 -m unittest discover -s tools/benchmark -p 'test_*.py' -v
python3 -m unittest discover -s tools/validation -p 'test_*.py' -v
bash gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

For connected tests, use a **dedicated test installation** and one connected device.
The workflow test refuses an installation with existing history or a current batch.
It completes onboarding and remembers its size preset; it cleans only its own
fixture, history rows and gallery export. Do not run it against personal app data.
Use an English locale for the accessibility screen tests. API 31 and 35 are the CI
matrix; API 37 remains an explicit release-device check.

```bash
bash gradlew clean connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.example.imagecompressor.validation
python3 tools/validation/check_device_results.py \
  app/build/outputs/androidTest-results --api 35
```

Substitute `--api 31` when running on API 31. On this cloud host use
`/workspace/.image-compressor/gradle.sh` instead of `bash gradlew`; build outputs
are under `/workspace/.image-compressor/builds/app`. This host has no attached
Android device and no KVM. Its earlier software emulator attempt failed before
JUnit, so it is not used to produce device acceptance evidence.

## Physical-device gates still to execute

Record device model, Android build/API, RAM, app revision/APK hash, date, test
conditions, outcome and evidence for each row. Mark untested cases explicitly.
Do not infer a physical pass from an emulator or Robolectric result.

| Gate | Required exercise | Current status |
| --- | --- | --- |
| OS/OEM | API 31, 35 and 37; at least one non-Google OEM | Not run |
| Phones/tablet | Baseline 4 GiB phone, 8 GiB phone/tablet; portrait, landscape, split-screen, live resizing | Host layouts covered; physical checks not run |
| Accessibility | TalkBack focus order, labels, adjustable comparison, error announcements, navigation and Save/Share; 100/150/200% fonts; gesture/three-button navigation | Host semantics/layouts covered; ATF authored; device/manual checks not run |
| Themes | Light/dark/system, wallpaper colors, contrast and system bars on actual devices | Fallback palette host checks; OEM/dynamic checks not run |
| Picker/share | Select/cancel/revoke a Photo Picker URI; cloud-only provider; share to at least two receiving apps; missing receiver | Host error/grant checks; system integration not run |
| Reliability | Mixed 30-image batch, cancel/retry, rotate, background/foreground, force-stop/reopen during compression/save, low storage | Host failure/recovery checks; physical stress not run |
| Performance | 20+ Balanced and target-size 12MP measurements, real peak-memory profiling and long-batch monitoring | No valid device baseline |
| Quality/privacy | Full-resolution photos, documents/text, skin/sky/low light, transparency and all EXIF orientations; confirm GPS removal; examine HDR/HEIC before claiming support | Synthetic/native host checks; representative real-photo approval pending |

### Performance procedure

Build both APKs from the same checkout before measuring. On an idle physical test
phone, record battery/temperature/power conditions and keep them comparable across
runs. Keep fixture hashes, run JSON and raw instrumentation log together.

```bash
python3 tools/benchmark/run.py --serial DEVICE_SERIAL \
  --profile full --iterations 1 --output-dir /tmp/shrink-validation
python3 tools/benchmark/run.py --serial DEVICE_SERIAL \
  --profile performance --iterations 20 --output-dir /tmp/shrink-validation
python3 tools/benchmark/summarize.py /tmp/shrink-validation/RUN_ID.json \
  --require-timing-targets
```

The performance profile contains Balanced 12MP JPEG and 12MP JPEG under 1,000,000
bytes. Reports include median, nearest-rank p95, maximum time and sampled PSS.
The strict summary exits nonzero for insufficient samples, emulators/unknown
hardware, missing/failed measurements, unmet/unverified targets or timing overruns.
Targets are Balanced median ≤2s/p95 ≤5s and target-size p95 ≤8s.

A timing result of `met` is only a preliminary result for this debug build and
synthetic 12MP input. The report always leaves `releaseApproved` false. Sampling
PSS every 100ms cannot establish true peak allocations/RSS or the 256 MiB memory
gate. Review full-resolution quality, profile real memory, validate representative
phone inputs, and repeat relevant measurements with the Step 7 release candidate.
See [benchmark limitations](../benchmarks/README.md) and the
[production plan](../production-plan.md) for the complete acceptance criteria.

## Local execution record

See [the Step 6 execution record](step-6-execution.md). Keep prepared checks,
compiled checks and executed checks separate when reviewing this stage.
