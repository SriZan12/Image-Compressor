# Compression engine — step 2

The app uses Android's native JPEG, PNG and WebP codecs. The new algorithm controls
validation, memory allocation, orientation/color conversion and a bounded search
for an output size. It does not implement a new image file format or codec.

## Output contract

- Balanced default: JPEG at encoder quality 80. This is an initial setting, not a
  claim of 80% visual quality or a calibrated perceptual optimum.
- Supported inputs: still JPEG, PNG and WebP. Animated PNG/WebP and unsupported
  formats are rejected. HEIC, AVIF, GIF and RAW are not advertised as supported.
- Originals are read only. Provider names, MIME types, dimensions and reported
  file sizes do not determine output paths or allocation. Compression re-inspects
  the actual bytes even when a caller supplies an earlier selection record.
- All eight EXIF orientations, including mirrors, are baked into the pixels.
  New output metadata marks orientation upright; source GPS/author/camera metadata
  is not copied. Output dimensions and byte counts describe the final file.
- PNG/WebP retain alpha. JPEG composites against white and reports that conversion.
  Processing uses a fresh SDR sRGB bitmap. Wide-gamut/gain-map inputs are converted
  to standard color and reported; HDR is not retained. Real HDR fixture validation
  remains a release gate.
- Outputs may be larger than inputs, especially PNG or format conversion. Results,
  comparison and history say “larger,” “smaller,” or “same file size” accordingly.
  The redesigned UI uses decimal B/KB/MB for upload limits and results;
  1 MB is exactly 1,000,000 bytes. Engine memory limits remain explicitly MiB.

## Target-size algorithm

1. At the chosen dimensions, encode at the requested quality. Write the upright
   metadata before measuring the candidate's complete file size.
2. If too large, try quality 40 (or the user's quality if explicitly lower).
   When that fits, make at most six binary midpoint probes between it and the
   requested quality. Preserve the highest-quality **observed** feasible candidate.
   Native codec sizes are not guaranteed monotonic, so this is not an exhaustive
   proof of the globally optimal quality.
3. If no candidate fits and the user allows smaller dimensions, estimate the next
   scale as `sqrt(target / smallestBytes) * 0.95`, clamped to 0.5–0.85. Keep the
   aspect ratio and stop at a 256px long edge. Inputs already smaller than that
   are not reduced to chase a target. Each resized candidate comes from the same
   upright base bitmap, avoiding repeated resampling of the previous candidate.
4. Search at most six resolutions, with at most eight encodes per lossy resolution
   (48 overall). PNG ignores quality and uses at most one encode per resolution.
   The search stops as soon as a resolution has a feasible result, favoring
   dimensions before quality. Without a target, use exactly one encode.
5. If no candidate fits within these limits, return the smallest valid candidate
   encountered with `UNMET`. Otherwise return `MET`; no target returns
   `NOT_REQUESTED`. Status uses the final bytes, without rounding or tolerance.

The target-resize switch prevents target search from changing dimensions. Memory
limits can still require a smaller output, which is reported separately. A failed
size target is a usable result requiring review, not an encoder error or a claim
that the target was achieved. The original remains available for comparison.

## Resource and failure handling

Inputs are streamed once per inspect/compress operation into a private staged
file, bounded at 100 MiB even when the provider reports no size. JPEG segment/end
markers, PNG chunk boundaries/checksums and WebP RIFF bounds/animation flags are
checked before native decoding. These checks catch incomplete containers; native
codecs remain responsible for validating compressed pixel data.

Bounds are checked before allocating pixels: at most 50 million source pixels and
16,384 on either edge. The working pixel budget is
`min(16_000_000, maxHeapBytes / 24)`, reserving approximately half the heap for up
to three ARGB buffers and leaving the rest for the app and codecs. Power-of-two
sampled decoding stays within that budget. Sampling may produce fewer pixels than
the requested bound; the engine never enlarges them again to disguise that loss.
A result reports `MEMORY_LIMIT` whenever this changes requested dimensions.
This allocation policy is not a guarantee about whole-process/native peak memory.

A process-wide coroutine mutex serializes bitmap pipelines, including callers
using different compressor instances. Candidate encoding goes directly to private
files; the engine retains only the best candidate and the current attempt.
Completed output is atomically moved to a reserved unique filename in the cache
folder exposed by FileProvider. Buffers, staged input, losing candidates and failed
outputs are released in `finally` paths. Originals are never written or deleted.

Cancellation is checked during staged reads, container scanning, between native
operations and before publication. Native decode/encode and provider blocking I/O
cannot be interrupted mid-call. Cancellation at the dispatcher return boundary
also removes a newly produced result. Step 3 now adds the cancel UI, partial
batch results, process-death recovery, durable output retention and storage quotas;
see [reliability and storage](reliability-and-storage.md).

## Verification and remaining evidence

Verified on 2026-10-30 in the configured cloud environment:

| Check | Result |
| --- | --- |
| JVM policy, search, container and model tests | 20 passed |
| API 35 Robolectric native-graphics integration tests | 9 passed, including all 8 EXIF orientations |
| Benchmark runner's host validation tests | 4 passed |
| Debug app and instrumentation APK builds | Passed |
| Android lint | 0 errors, 5 pre-existing warnings, 1 pre-existing hint |

Lint's outstanding findings concern an available Compose compiler update,
an existing component parameter order, existing launcher/image assets and boxed
onboarding state. No lint findings were introduced by the compression changes.

Run from a configured JDK/Android SDK environment:

```bash
bash gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
python3 -m unittest discover -s tools/benchmark -p 'test_*.py' -v
```

The unit suite includes policy/search/container tests and Robolectric 4.17 tests
with **native graphics** on API 35. Those integration tests execute bitmap codecs,
Canvas transforms, EXIF writes, FileProvider access and real temporary files. They
do not use the legacy bitmap shadows that can return placeholder pixels.

The native checks cover all eight orientations against independently mapped pixels,
alpha and JPEG white compositing, final target bytes, impossible targets, source
immutability, metadata stripping, wide-gamut conversion, the encoded-size limit,
memory-limited 48MP decoding and cleanup after publication failure. Pure tests
exercise quality selection, bounded search,
retained-candidate cleanup on cancellation/error, malformed/animated containers,
input limits, geometry and honest result formatting.

The cloud helper `/workspace/.image-compressor/gradle.sh` keeps SDKs, caches and build
outputs outside this checkout. Its external Gradle init script supplies a writable
Robolectric `user.home` and forwards the environment's TLS trust store/proxy to test
workers. Normal local machines do not need those cloud-specific settings. The
first native test run downloads Robolectric's Android SDK artifact from Maven.

Physical-device timing, peak process memory, OEM codec behavior, full-resolution
visual quality and real HDR/HEIC evaluation remain unverified. The software-emulator
failure recorded in [the baseline report](benchmarks/cloud-attempt-2026-10-30.md)
still prevents a valid before/after device benchmark. Host correctness tests do
not establish the performance budgets in [the production plan](production-plan.md).
