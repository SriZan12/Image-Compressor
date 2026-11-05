# Editor Step 4 — save and share the verified result

One-photo editing now stays on the same screen. Choosing a quick/custom size
limit or moving the slider automatically prepares the candidate. When its bytes
are verified, **Save photo** and **Share** become available. Neither action runs
the image encoder again.

## User experience

- Save makes a separate copy in **Pictures / Image Compressor**. The editor
  shows **Saved**, names the folder beside the result, and keeps Share available.
  Repeated actions reuse the retained result and existing gallery copy.
- Share opens Android's share chooser with a read-only content URI for the
  verified copy. It prefers the app's retained file, even if another app later
  edits the gallery copy. A cancelled chooser leaves the prepared copy in Recent
  photos for later use.
- **Compare with original** opens a full-window overlay with the existing
  before/after viewer and zoom controls. Close or Android Back returns to the
  editor. Opening comparison does not create a history entry or run compression.
- Invalid custom text, invalid dimensions, unfinished work, or stale source/
  settings prevent export and comparison. Saving and preparing a share have
  distinct progress wording and block duplicate actions.
- An unmet limit remains explicitly marked as over the limit. The exact byte
  count decides this; rounding never establishes a fit. The user can choose a
  larger limit or another photo, or deliberately **Save anyway**. Dimension,
  transparency, and appearance notices remain visible when applicable.
- Export failures keep the measured result available for retry. **Update
  preview** creates a fresh candidate if a cache file changed or disappeared.
  A failed gallery save reuses its retained result rather than creating another
  history entry on retry.

## Ownership and exact bytes

The editing session grants an export lease only for its exact current Ready
artifact, source, settings, and displayed output path. Changing settings,
pausing the app, or closing the session prevents new acquisitions. An existing
lease keeps the private bytes alive until the accepted operation releases them.

Each completed temporary preview receives a cancellable SHA-256 fingerprint.
Retention validates the owned preview path, expected length, digest, and file
type while copying into private retained storage. The copy is flushed and moved
atomically before its history record is committed. No original is overwritten,
and the FileProvider does not expose the private preview directory.

The retained filename derives from the unique preview path. Retrying the same
artifact therefore finds or repairs one history record, including after a
cancelled dispatcher return. A failed pre-commit copy/transaction cleans its
uncommitted destination while preserving the original preview for retry. A
completed commit survives cancellation; startup removes abandoned copy scratch.
The existing batch record is neither reset nor replaced by single-photo export.

Gallery publication reuses the existing MediaStore journal and duplicate-safe
save behavior. Sharing grants read permission only. No network, broad media,
or extra storage permission was added.

## Lifecycle and recovery

After retention, the editor uses the durable result instead of preparing a new
temporary file on each resume. Saved state records its history ID and settings.
On restoration, the repository rechecks availability and accepts the receipt
only for the same selected photo/settings and an available private file. A
gallery-only history item does not masquerade as a verified editor preview.

History list emissions can lag the export commit. The editor queries its current
record before changing Saved status and checks that the whole receipt is still
the one it queried. This prevents an old list or suspended lookup from erasing
or downgrading a newer result.

A share that finishes while the app is paused keeps the copy and asks the user
to tap Share on return. The share launcher also rejects a destroyed, finishing,
or changing Activity, so an old callback cannot open a chooser after rotation.
Committed results remain in Recent photos if the process dies; an interrupted
share is not launched automatically on restart.

## Scope and remaining checks

The direct path covers a single selected photo. Multiple-photo selection keeps
the existing explicit batch action and labels the live preview as the first
photo. The complete batch/supporting-flow redesign belongs to Step 5.

The existing 150 ms debounce still precedes actual processing; no speculative
size estimate is labelled verified. Large-file latency, peak memory, real picker/
provider behavior, MediaStore publication, share-sheet return, rotation during
export, and TalkBack still need supported-device validation. This step does not
establish release readiness or change signing.

## Validation and captures

On 2026-10-30:

- Full host suite: **193 passed, 0 failures/errors**, with **1 skipped** opt-in
  Step 2 measurement (194 discovered). Step 4 adds 48 tests.
- `lintDebug`: **0 errors, 32 existing warnings**: 30 unused resources from the
  earlier UI simplification and 2 dependency-update notices.
- `assembleDebug` and `assembleDebugAndroidTest`: passed. The new native device
  workflow and accessibility checks compile; no device instrumentation ran.
- Phone, tablet, comparison, short-window, large-text, busy, failure, invalid
  input, and stale-result host checks passed. Captures were visually reviewed.
- One Robolectric generic-Activity teardown check emits an `Invalid ID 0x00000000`
  resource diagnostic; its destroyed-Activity assertion and all tests pass.

The executed command was:

```bash
bash gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

New coverage includes:

| Tests | What they establish |
| --- | --- |
| 13 native repository tests | Real engine bytes copied through real Room/private storage; no source inspection or encoding during retention; exact digest, format, ownership and quota checks; single-row concurrent/retried retention; DB rollback; committed-result survival after cancellation; private-copy preference after gallery mutation. |
| 12 export feature tests | Exact displayed artifact acquisition, duplicate-action prevention, stale same-settings/path rejection, invalid draft/geometry guards, save/share failure retry, lifecycle pause, receipt integrity and changed-input completion guards. |
| 11 ViewModel integration tests | Save/Share receipt reuse, no re-encode after pause/resume, saved-state restore, missing/gallery-only receipt recovery, editing guards, retry, and lagging history emissions. |
| 5 added lease/fingerprint tests | Owner/session closure cannot delete leased bytes; final release cleans up; stale acquisition fails; fingerprints match completed files and reject mismatched length. |
| 6 added UI tests | Direct export busy/error/saved states, comparison without export or re-encoding, and reachable comparison controls in short landscape windows. |
| 1 share-launcher test | A callback holding a destroyed Activity cannot launch a share sheet. |

The updated device workflow selects a photo, waits for a real automatic 50 KB
result, then temporarily moves only its test-owned source before Save. It checks
that retained/gallery/share bytes match the preview, only one history row exists,
and Saved survives activity recreation. Its source is restored and only its own
records are cleaned up. This workflow is **compiled, not executed** in the cloud.

The captures below come from native Robolectric API 35 rendering of the real
Compose adapter, live session, engine output and Coil requests. The phone and
tablet use different detail preferences; their actual verified counts are
195,488 and 45,921 bytes respectively, from the same 791,555-byte fixture. These
are host captures, not physical-device screenshots or timing evidence. The
updating capture uses a test gate to hold real pending work; the app has no
artificial loading delay. Pixels were copied without edits.

- [Phone: direct Save photo / Share](screenshots/editor-step4-ready-phone.png)
- [Phone: updating with export disabled](screenshots/editor-step4-updating-phone.png)
- [Dark tablet: preview and controls in two panes](screenshots/editor-step4-tablet-dark.png)
- [Full-window comparison](screenshots/editor-step4-comparison-phone.png)

The corresponding JSON files beside each PNG record actual byte counts. Fixture
provenance is in [NOTICE.md](../../app/src/androidTest/assets/compression/NOTICE.md).
Earlier Step 3 captures remain unchanged as historical evidence.

The cloud debug APK is at
`/workspace/.image-compressor/builds/app/outputs/apk/debug/app-debug.apk`.
