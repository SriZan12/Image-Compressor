# Photo-opening device probe

`PhotoOpeningBaselineTest` measures image inspection, a Coil load with an empty
bitmap cache, and an immediate memory-cache load. It uses the same four fixtures
as the host probe: astronaut PNG, 12 MP PNG, a 12 MP JPEG encoded at 92, and 48 MP
PNG. Requests are 744 × 480 pixels with software bitmaps and default Fit precision.

This probe is prepared for a real Android 12+ device. It has not been run on a
device in the cloud environment. A host result does not establish phone latency.

## Run and retrieve

Build both debug APKs, connect one device, and install them. The cloud workspace
uses its external Gradle wrapper/output directory instead of the paths below.

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
preview_run_id="opening-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
preview_source_sha="$(python3 -c 'import hashlib,pathlib; print(hashlib.sha256(pathlib.Path("app/src/main/java/com/example/imagecompressor/util/ImageCompressor.kt").read_bytes()).hexdigest())')"
adb shell am instrument -w \
  -e class com.example.imagecompressor.benchmark.PhotoOpeningBaselineTest \
  -e previewRunId "$preview_run_id" -e previewIterations 7 \
  -e previewRevision "$(git rev-parse HEAD)" \
  -e previewCompressorSha256 "$preview_source_sha" \
  com.example.imagecompressor.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as com.example.imagecompressor \
  cat "files/preview-performance/$preview_run_id.json" > "$preview_run_id.json"
```

Require the instrumentation output to report **OK (1 test)**. A zero `adb` exit
code alone does not establish success. Also check the report before using numbers:

```bash
python3 - "$preview_run_id.json" <<'PY'
import json, math, sys
r = json.load(open(sys.argv[1]))
rows = r['measurements']
assert r['complete'] is True and 'failure' not in r
assert len(rows) == r['expectedMeasurements'] == 4 * r['iterations']
assert len({(x['file'], x['iteration']) for x in rows}) == len(rows)
for x in rows:
    assert x['coldSource'] == 'DISK' and x['warmSource'] == 'MEMORY_CACHE'
    assert 0 < x['decodedWidth'] * x['decodedHeight'] <= 744 * 480 * 4
    for k in ('inspectMs', 'coldCoilMs', 'warmCoilMs', 'inspectAndColdCoilMs'):
        assert math.isfinite(x[k]) and x[k] >= 0
print('Complete probe:', r['runId'], len(rows), 'measurements')
PY
```

Defaults are one measured iteration per fixture, with one code warmup per fixture;
`previewIterations` accepts 1–30. Every run needs a new ID. Reports are reserved
before fixture preparation and remain `complete: false` after a failure. Fixture
files and the loader are cleaned up; reports remain in app-private storage.

## Interpret carefully

- “Cold” clears Coil's memory cache, not OS page caches or provider caches.
  `DISK` means a local content-URI source, not a Coil disk-cache hit.
- Timings use the monotonic device clock. Fixture copying/JPEG generation, picker
  return, Room/batch startup, Compose layout, first drawn frame, GPU work, and
  network-backed provider downloads are excluded.
- The report records SDK/device identity, debug/release status, source revision,
  compressor hash, fixture hashes, decoded dimensions, and bitmap allocation.
  Keep the working-tree diff with a run: a commit ID alone does not identify
  uncommitted code. Generated JPEG hashes can vary across Android codec versions.
- Compare multiple runs on the same physical device, fixtures, build type, and
  thermal/power conditions. Use medians and spread; do not infer a phone speedup
  from host data or treat these values as picker-to-visible-photo latency.
