# Editor Step 3 — live temporary previews

The editor now prepares a real preview when a photo or its settings change.
The size-limit chips, custom limit, slider, and Advanced controls all use the
same settings snapshot. The chosen limit remains the maximum size; the slider
sets the preferred detail level within that limit.

## Behavior

- Edits invalidate the previous result immediately. A 150 ms debounce coalesces
  rapid changes before expensive work starts. The UI remains interactive while
  the engine runs off the main thread.
- The previous photo preview remains visible while updating the same source.
  Its old byte count and any “Fits” claim disappear. Changing the source clears
  the previous preview too.
- The screen says **Updating size…** until it has measured the completed file.
  It does not predict bytes from a quality percentage or scale a thumbnail's
  file size. The existing estimate presentation remains explicitly labelled,
  but this engine does not emit speculative estimates.
- A ready preview shows the friendly before/after sizes plus the exact verified
  byte count. Only integer bytes establish whether a size limit was met.
  The byte-count line keeps its space during loading/updating so the slider
  stays in place. Hidden previous counts are excluded from TalkBack semantics.
- Changing either control cancels superseded work. A generation check rejects
  late completion even if the operation did not respond promptly to cancellation.
- Invalid custom-limit text or picture dimensions pause preview work. Retry
  repeats failed preparation. Leaving the editor or pausing the app releases
  the temporary preview; returning prepares it again from the current settings.
- For a batch, the live preview describes the first photo and says so. The
  existing final batch action still handles every selected photo.

## Ownership and resource bounds

The live session does not call the repository's retained batch pipeline. It
owns temporary artifacts separately from retained results and Room history.
Temporary previews use private file URIs for Coil and are outside the
FileProvider's sharing paths.

Each editor processor owns a private session folder. Each preparation owns a
separate work folder containing its source snapshot, search candidates, and
final preview. Replacing/closing an artifact deletes its work folder; closing
the processor removes its session after active work drains. The first preview
session in a fresh app process sweeps recognized abandoned session folders.
This cleanup assumes the app's current single-process configuration.

The preview path reuses the existing engine's source validation, EXIF orientation,
sampled decoding, memory policy, color/transparency notices, and bounded
target search. Each request reopens and validates a fresh source snapshot; it
does not retain full-resolution bitmaps between edits. It processes one native
bitmap job at a time. Cancellation is
checked between source reads, decode/render/encode stages, and search attempts;
Android's individual native decode/encode calls cannot be interrupted midway.
Likewise, a provider blocked inside a source read cannot be forcibly interrupted
by coroutine cancellation. New requests wait for the cancelled work to drain;
they do not start overlapping bitmap jobs or publish that work's late result.

At each of at most six picture sizes, the search tries the preferred detail,
then a lower value if needed, and at most six midpoint probes. PNG skips quality
search. Every candidate includes metadata before its bytes are measured. The
search retains a measured fitting candidate, or reports the smallest candidate
it found with an unmet-limit explanation. It does not promise a globally optimal
encoder result or automatic compliance with a form's other requirements.

## Step boundary

This step wires automatic **temporary preparation** into the real editor.
**Make smaller** still starts the existing retained-result flow, which encodes
again and records its own measured results. Direct Save/Share of the displayed
artifact, with export ownership and recovery, is Step 4. No release or push is
part of this step.

Step 4 needs an explicit ownership transfer into retained storage so the file
shown in the editor is the file that is saved/shared. It must not broaden the
FileProvider to expose the private preview tree or save a stale candidate.

Physical-device checks remain necessary for sustained dragging, large-image
latency and peak memory, slow/cloud providers, background interruptions, and
TalkBack. A 150 ms debounce is a scheduling policy, not a claim that a large
photo finishes encoding in 150 ms. No device performance claim is made here.

## Validation

The host suite covers 145 tests, including 29 new checks for this step:

| Checks | Coverage |
| --- | --- |
| 9 native preview tests | Actual decodable bytes, bounded unmet targets, byte equality with the retained encoder, source preservation, FileProvider exclusion, separate session ownership, orphan cleanup, corrupt input, and cancellation during dispatcher return or preparation. |
| 9 session tests | Debounce, duplicate suppression, same-source retention, changed source identity/metadata, late noncooperative completion, maximum one active job, explicit retry, and parent/session cancellation. |
| 8 ViewModel tests | Settings/selection/lifecycle integration, invalid drafts/dimensions, failure retry, no repository image writes while editing, explicit final batch behavior, and ViewModel disposal. |
| 3 native Compose tests | Real engine → live session → production screen adapter → real Coil loading; automatic 500 KB → 100 KB change, slider/target coordination, exact measured bytes, unchanged original, stable updating geometry, and dark tablet layout. |

Existing tests also cover rounding above a limit, stale settings/source guards,
large text, loading geometry, orientation, native compression, permissions,
storage, and the previously improved Coil path. The opt-in Step 2 performance
probe is excluded from the ordinary suite; it is not a live-compression speed
test.

On 2026-10-30, the full host suite passed **145 tests**, with **0 failures/errors**
and **1 skipped** opt-in measurement (146 discovered). After the final
summary-height adjustment, all **12 editor rendering/interaction tests** passed
again, including stable loading/updating geometry. Both `assembleDebug` and
`assembleDebugAndroidTest` passed. `lintDebug` reports **0 errors and 32 existing
warnings**: 30 unused resources from earlier UI simplification and 2 dependency
update notices. No device instrumentation was executed.

Reproduce the complete checks with:

```bash
bash gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The cloud wrapper redirects build outputs to
`/workspace/.image-compressor/builds/app`. The debug APK is
`/workspace/.image-compressor/builds/app/outputs/apk/debug/app-debug.apk`.

## Native screen captures

These are native Robolectric API 35 renders of the real Compose screen adapter,
live editing session, native compression engine, and Coil file requests. They
are host layout/interaction evidence, not screenshots or latency measurements
from a physical phone. Image pixels have not been edited.

- [Phone — measured result](screenshots/live-editor-ready-phone.png): the actual
  791,555-byte fixture becomes 195,488 bytes, verified against a 500,000-byte
  limit. This capture uses the slider's highest detail preference.
- [Phone — updating](screenshots/live-editor-updating-phone.png): choosing
  100 KB starts work automatically. The previous preview stays visible, the
  verified count and “Fits” claim disappear, and the slider keeps its position.
  A controlled test gate holds real pending work so the state can be captured;
  there is no artificial app delay.
- [Dark tablet — measured result](screenshots/live-editor-tablet-dark.png):
  the same fixture becomes 45,921 bytes at the default detail preference, with
  the photo and controls in two panes.

The corresponding [phone](screenshots/live-editor-ready-phone.json),
[updating](screenshots/live-editor-updating-phone.json), and
[tablet](screenshots/live-editor-tablet-dark.json) records retain actual byte
counts. The fixture's provenance is in
[NOTICE.md](../../app/src/androidTest/assets/compression/NOTICE.md).
