# Editor redesign — Step 2: photo opening

Step 2 investigates the time between selecting a photo and loading its preview.
It keeps the [Step 1 screen hierarchy](editor-step-1.md) and the current manual
**Make smaller** flow. Live compression estimates and automatic export are later
steps in the [editor plan](editor-plan.md).

**Status: implemented and host-validated; device latency remains unverified.** The changes below
address unnecessary source copying and preview sizing. Host measurements cannot
establish that the reported two-second delay on a phone is fixed.

## Findings

Before the editor received original-photo metadata, inspection copied the entire
source into a temporary file, validated it, read dimensions/orientation, and then
deleted that copy. Coil subsequently opened the source again for the preview.
For a normal seekable local file, inspection therefore performed an unnecessary
temporary-file write before the screen could use its metadata.

The app uses Coil 2.7's shared loader. Memory/disk caching was already enabled,
crossfade was already off, and ordinary requests already decoded to bounded
display sizes. There is no separate remote-banner loading path in this app.
Enabling caches or adding a transition would not address the demonstrated issue.

A second problem appeared when a loaded preview grew from 120dp to 320dp. A
regression test first failed because the original request did not resolve to the
new 640-pixel viewport. The enlarged view could keep an undersized bitmap rather
than request enough pixels for its new display size.

## Changes

### Inspect a suitable descriptor without copying it

Inspection now uses a direct path only when the provider supplies a whole,
seekable file descriptor with a known size. It validates the existing image
container, reads bounds and EXIF orientation, and returns the real source size.
The 100 MiB input cap and the existing PNG CRC/JPEG/WebP container checks remain
in place. This removes the temporary copy; it does not remove source validation
or promise that inspecting a large file requires only reading its header.

Non-seekable sources, unknown sizes, and sliced descriptors keep the bounded
temporary-copy path. That fallback consumes the already-open provider stream
rather than opening another request. Cancellation and cleanup remain necessary
on both paths. A changed file length is rejected before publishing inspection
metadata.

Compression still makes and validates its own fresh staged snapshot. Metadata
inspection does not authorize later processing of a source that may have changed
since selection. In particular, a direct inspection is not an immutable snapshot
of an externally owned file.

### Match requests to the measured preview

A shared remembered request now keys off the source and measured pixel viewport.
A real source/size change produces the appropriate request; unrelated Compose
updates do not restart it. When the same photo grows or shrinks, the previous
successful memory-cache key can provide a placeholder while the new size loads.
Recording that successful key does not itself trigger another request.

Loading, success, and error callbacks keep the visible state aligned with the
request, including resizing while a request is pending. An error cannot leave a
permanent loading placeholder. The comparison view keeps its original-image
request attached after an error so a subsequent request can recover.

Requests use the viewport size. They do not force a four-times-larger bitmap for
every preview. Comparison zoom therefore still magnifies the available viewport
pixels; this step does not add a full-resolution or tiled zoom renderer.

The screen layout and matched placeholder geometry from Step 1 remain the design
baseline. There are no artificial loading delays. The refreshed [phone screen](screenshots/editor-step2-phone.png) and
[dark tablet result-state render](screenshots/editor-step2-tablet-dark.png) show
the current Compose implementation. These are native host renders, not device
captures or timing evidence. The tablet uses the same measured-result fixture
contract as Step 1; automatic live compression/export is still later work.

## Evidence and measurement method

`PhotoLoadingIntegrationTest` exercises actual Coil fetching, decoding, and
memory caching without manually seeding bitmaps. Its cases check a cold preview,
warm re-entry, unrelated recomposition, viewport growth, resize during loading,
and recovery after an unreadable source. Warm memory-cache reuse must avoid an
additional fetch/decode. These are behavior checks, not host wall-clock speed
assertions. Executed results are recorded below.

`PhotoOpeningMeasurementTest` separately measures inspection, a Coil request with
an empty bitmap cache, and immediate memory-cache reuse. Both before and after reports are complete.

- Environment: Robolectric API 35 with native graphics on the host JVM.
- Four fixtures: astronaut PNG, 12 MP PNG, 12 MP JPEG encoded at quality 92, and
  48 MP PNG. Each has seven measured iterations after one code warmup.
- Requests: **744 × 480 pixels**, FIT/INEXACT sizing, software bitmaps, one shared
  loader, and local content URIs.
- “Cold” clears Coil's memory cache after warmup. It does not clear the operating
  system's page cache or provider caches. Coil's `DISK` result identifies a local
  source here; it is not evidence of a Coil disk-cache hit.
- Timings use `System.nanoTime`. Fixture preparation, picker return, batch/Room
  setup, Compose layout, first drawn frame, GPU work, and network-provider
  downloads are excluded.
- Reports include fixture hashes, source sizes/dimensions, decoded
  sizes/allocations, cache sources, and individual measurements. Compare medians
  and spread across the same fixtures instead of selecting a favorable sample.

Raw reports are retained as
[photo-opening-before.json](../benchmarks/photo-opening-before.json) and
[photo-opening-after.json](../benchmarks/photo-opening-after.json).

| Fixture | Inspection before / after (ms) | Cold Coil before / after (ms) | Inspection + cold Coil before / after (ms) |
| --- | ---: | ---: | ---: |
| Astronaut PNG | 4.21 / 3.67 | 14.43 / 10.68 | 18.10 / 14.02 |
| 12 MP PNG | 3.88 / 3.14 | 61.60 / 53.34 | 66.75 / 56.35 |
| 12 MP JPEG | 15.12 / 12.77 | 22.61 / 20.14 | 37.06 / 32.70 |
| 48 MP PNG | 5.92 / 3.04 | 190.27 / 166.29 | 194.57 / 169.33 |

All 28 after-run warm requests returned `MEMORY_CACHE`; per-fixture warm medians
were 0.96–2.00 ms. The integration tests separately verify that cache hits perform
no additional fetch or decode. Decoded images were 480 × 480 or 640 × 480 pixels
(0.92–1.23 MB of bitmap storage) for the requested frame. This does not measure
transient decoder memory peaks.

Inspection removes one source-sized staging write for eligible files. The table
shows host timing observations; changes to cold decode time are not attributable
to a new codec or cache configuration. Separate-process JIT and host load can
affect these short measurements. [Source hashes](../benchmarks/photo-opening-provenance.json)
identify the code, and [the comparison script](../../tools/benchmark/summarize_photo_opening.py)
checks matching input hashes, request settings, iteration counts, and cache outcomes.

No phone speedup or picker-to-visible-photo latency can be inferred from this
table. The host probe isolates work done by inspection and Coil; it does not
measure an end-to-end UI frame.

## Device follow-up

A matching Android instrumentation probe and [device run
instructions](../benchmarks/photo-opening-device.md) are prepared. The probe compiled successfully in the instrumentation APK; it has not run on a device. This cloud
environment has no connected device or usable KVM emulator for that check.

Run the probe repeatedly on the same supported physical phone with comparable
power/thermal conditions. Separately trace the actual picker-to-visible-preview
flow, including provider access and the first drawn frame, to investigate the
reported two seconds. A network-backed provider can add download time that the
local-fixture probe deliberately excludes.

## Validation

On 2026-10-30:

- Full host suite: **116 passed, 0 failed**. The separate opt-in measurement test
  is skipped in this ordinary run (117 discovered); it passed independently for
  both before and after runs, each collecting 28 measurements.
- The 26 focused checks passed: 8 descriptor/fallback tests, 5 real-Coil loading
  tests, and 13 existing compression/container tests. They include all eight
  EXIF orientations, exact-length descriptors, original preservation, corrupt
  input rejection, input limits, cancellation, and descriptor cleanup.
- Robolectric models native pipes as regular files. Two explicitly scoped test
  adapters model unknown-length/unseekable provider contracts with streamed
  bytes. They do not establish real Binder/kernel-pipe behavior on a phone.
- `lintDebug`: **0 errors, 32 existing warnings** (30 unused resources from the
  earlier UI simplification and 2 dependency-update notices).
- `assembleDebug` and `assembleDebugAndroidTest` passed, including the new device
  probe. No device instrumentation was executed.
- Native phone/tablet renders were regenerated and visually reviewed. The
  existing large-text, loading-geometry, resize, and interaction checks passed.

Reproduce the isolated host measurement with:

```bash
bash gradlew testDebugUnitTest --tests '*PhotoOpeningMeasurementTest' -PpreviewMeasure=true -PpreviewLabel=local
python3 tools/benchmark/summarize_photo_opening.py docs/benchmarks/photo-opening-before.json app/build/preview-performance/local.json
```

The managed cloud wrapper redirects `app/build` to
`/workspace/.image-compressor/builds/app`. Opt-in measurement classes run in
separate test JVMs so Coil's cached main dispatcher is not shared with Compose's
paused test Looper. This isolation is part of the host harness, not an app change.

The checked behavior is:

- Known-size whole descriptors avoid the inspection copy while preserving
  validation, actual file size, and upright dimensions.
- Sliced, unknown-length, and non-seekable inputs use the bounded same-stream
  fallback; oversized/corrupt/cancelled inputs fail safely and clean up.
- A cold request actually fetches/decodes; a warm request uses memory; unrelated
  UI edits do not add requests; a larger viewport requests sufficient pixels.
- Loading, error, and retry states settle correctly without changing the editor
  hierarchy or introducing a fake delay.
- Host before/after evidence is reported with its limits, and device latency
  remains explicitly unverified until the device checks run.

Batch inspection remains sequential and waits for all selected metadata before
navigation. Cold app startup, remote-provider delays, stalled-read cancellation,
and picker-to-first-frame latency still need physical-device tracing. These
measurements do not claim that the original two-second report is resolved.
