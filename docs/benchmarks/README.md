# Compression baseline

The baseline runs the app's real `ImageCompressor` using Android's bitmap codecs
and content provider. It opens no activity and selects no user photos. Production
compression was unchanged in Step 1. Step 2 replaces the engine and adds host
correctness tests; see [the engine notes](../compression-engine.md).

## Corpus and profiles

Fixtures live in `app/src/androidTest/assets/compression`. See `NOTICE.md` for the
public-domain NASA photograph and provenance. Generated fixtures and their hashes
are listed in `manifest.json`; regenerate with Python 3, without extra packages:

```bash
python3 tools/benchmark/generate_fixtures.py --large
```

The photograph is a 512px real image. Generated patterns test geometry, alpha,
compression difficulty and large-file handling; they are not realistic substitutes
for full-resolution phone photos. Add consented/licensed examples of outdoor,
indoor/low-light, portrait, screenshot/document and HEIC/HDR inputs before final
quality calibration. Never commit private user images to this corpus.

| Profile | Measurements per iteration |
| --- | --- |
| `smoke` | 18: photo→JPEG/WebP; chart→PNG; alpha→PNG/JPEG; two size targets; impossible target; corrupt/truncated input; all eight EXIF orientations |
| `full` | The same 18 plus 12MP and 48MP synthetic inputs |
| `performance` | 2: Balanced 12MP JPEG and 12MP JPEG under 1,000,000 bytes; use 20+ iterations on physical hardware |

EXIF cases generate JPEG inputs from the chart at quality 95, set the requested
orientation, and encode to PNG. Their reference transformation is independent of
the compressor. The prepared input's hash is recorded: platform JPEG encoders can
produce different fixture bytes across Android versions.

## Build and run

Use a dedicated test device with USB debugging or a booted accelerated emulator.
The script requires an explicit serial and checks full boot. Installation updates
the debug app/test APKs; it does not clear application data. Choose a test device
where installing this package is appropriate.

```bash
bash gradlew assembleDebug assembleDebugAndroidTest
python3 tools/benchmark/run.py --serial DEVICE_SERIAL \
  --profile smoke --iterations 1 --output-dir /tmp/image-compressor-baseline
```

On this cloud checkout, use the configured external build/cache helper:

```bash
source /workspace/.image-compressor/environment.sh
/workspace/.image-compressor/gradle.sh assembleDebug assembleDebugAndroidTest
python3 tools/benchmark/run.py --serial emulator-5554 \
  --apk-dir /workspace/.image-compressor/builds/app/outputs/apk \
  --profile smoke --iterations 1 \
  --output-dir /workspace/.image-compressor/baselines
```

For correctness characterization, use `--profile full --iterations 1`. For preliminary
physical-device timings, use `--profile performance --iterations 20`. The harness
accepts 1–100 iterations. The script defaults to a 30-minute timeout, configurable
with `--timeout-seconds`. Then summarize the resulting run:

```bash
python3 tools/benchmark/summarize.py /tmp/image-compressor-baseline/RUN_ID.json \
  --require-timing-targets
```

The summary computes median, nearest-rank p95, maximum time and sampled PSS. Its
optional strict mode rejects emulators/unknown hardware, fewer than 20 samples,
unmet or unverified targets and timings beyond the proposed thresholds. It also
rejects incomplete/duplicate/missing cases and non-finite or nonpositive metrics.
Timing acceptance for a debug build and generated input is preliminary: it cannot
approve a release, full-resolution quality or true peak memory. See the
[physical validation procedure](../validation/README.md).

Reports have unique run IDs. The runner saves raw instrumentation output, checks
JUnit's completion summary (ADB alone can return zero after a test failure),
retrieves that run's JSON, and verifies its completion flag, profile, exact case
identities, iteration range and measurement count.
An interrupted/failed run is not a baseline pass. Do not use an old report in place
of the expected run ID. Tests remove only their own fixtures and successful output
files; reports remain in the app's private `files/compression-benchmark` directory.

## What the measurements mean

- `elapsedMs` covers inspect/decode/resize/encode/EXIF/file output; it excludes
  fixture preparation, quality analysis, gallery save, UI and Room history.
- One small warmup precedes the suite. The test runs a debug build. Keep device,
  OS, build mode, temperature, fixture hashes and iteration count consistent when
  comparing revisions. Emulator CPU emulation is not a hardware performance test.
- `sampledPeakPssKiB` is whole-process PSS sampled every 100ms. It can miss short
  peaks; baseline PSS is recorded separately. Use Android profiling tools on real
  hardware to establish maximum allocation/RSS and long-batch memory behavior.
  Previous cases and allocator caches can affect PSS; the harness does not force GC.
- `rgbPsnrDb` compares an upright reference and output at a preview resolution of
  at most 256px width, composited on white. `null` plus `compositedPixelsExact=true`
  means zero error, not missing data. This metric can conceal fine detail and
  dimension loss; it is diagnostic, not a visual-quality guarantee. Alpha error
  and original logical dimension preservation are recorded separately.
- Orientation correctness is checked separately against the expected geometry
  and pixel arrangement. An impossible size target is intentionally included.
- Original bytes, output decodeability, reported geometry/file size/MIME, and
  expected rejection of broken input are checked. Unexpected failures fail the
  harness. Known target/orientation deficiencies are recorded as findings rather
  than failing characterization. Step 2's separate host regression suite asserts
  those correctness contracts; characterization remains useful for comparisons.
- Step 2 reports include `reportedTargetStatus`, `encodingAttempts`,
  `encodingQuality` and `adjustments`, alongside independently measured output bytes.

The script records source revision, current compressor hash, the complete main-source
tree hash (including policies and resources), dirty-tree state, APK hashes, device
build and run ID. Build the APKs from that same checkout; a source hash taken by
the runner does not prove an older APK contains those sources. Commit selected reports with a short findings
summary; keep transient logs and repeat runs outside the checkout.

## Current evidence

The [initial cloud attempt](cloud-attempt-2026-10-30.md) failed before JUnit started
because Android killed the instrumentation process during system-wide timeouts.
There is **no valid device report yet**. The corpus and harness compile, and host
result-validation tests pass. Step 2 exercises native codecs in Robolectric host
tests; Android device performance measurements remain unexecuted.

Run the host validation tests with:

```bash
python3 -m unittest discover -s tools/benchmark -p 'test_*.py' -v
```

The pre-existing two JVM tests, debug APK build and lint passed during environment
onboarding. They do not establish compression correctness or device performance.
The software emulator previously booted but encountered System UI ANRs and an
emulator shutdown crash. This benchmark avoids the UI; successful execution still
cannot replace physical-device measurements, accessibility testing or beta usage.
