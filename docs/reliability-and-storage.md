# Reliability and storage — step 3

Step 3 records work as it completes, retains usable results across app restarts,
and gives users explicit recovery actions. The production minimum is now Android
12 / API 31; saving uses scoped MediaStore and requires no storage permission.

## Batch behavior

A batch has a persistent settings snapshot and ordered item records in Room.
Each item moves from pending to running, then completed or failed. The output
file and its history entry are committed independently of later items. A damaged
image, revoked permission or storage error does not discard earlier results.
The Results screen lists failures and offers **Retry remaining images**. Retry
preserves completed items and their history IDs, using the original settings.
To use different settings, start a new batch.

**Cancel compression** stops the worker cooperatively. It checks cancellation
between reads and native codec operations; an active native decode/encode must
return before cancellation can finish. Successfully committed outputs remain
available. Short file/database publication operations finish without cancellation
so they cannot leave an acknowledged result half-recorded. Duplicate job starts
are guarded in both the workflow and repository. Selection is capped at 30 images,
including across repeated picker sessions; valid selections survive another
selection's decoding or permission failure.

Rotation keeps the ViewModel and current worker. SavedStateHandle retains only
small values: selected URI strings, settings, screen name and comparison history
ID. It never contains image bytes. After process death, Room's unfinished items
become **interrupted**; completed results remain accessible and the UI offers an
explicit retry. Work is not automatically restarted. Providers that do not retain
read permission may require users to select their originals again. No foreground
service or guaranteed background execution is introduced. If startup cannot open
results safely, the app blocks new work behind an explicit retry screen until
initialization succeeds.

## Ownership, retention and capacity

- Encoder scratch files remain in the app's cache. Before an item is acknowledged,
  its completed file is atomically moved into private `files/compressed/` storage.
  Android cache eviction does not remove these retained results.
- Retained images stay until the user removes their history entries or clears
  history/app data. There is no automatic age-based eviction of completed images.
- New results are limited to a combined **512 MiB** of retained image files. Quota
  failures preserve existing results and explain how to free space. Scratch files,
  database files and gallery exports are additional disk usage; actual low-storage
  errors are also handled. Existing legacy results may exceed this quota during
  migration so an upgrade does not delete them to enforce a new limit.
- History distinguishes **kept in app storage**, **saved to gallery**, and
  **unavailable**. Opening/sharing validates the current result; availability is
  refreshed when the app resumes. Old history can be opened for saving/comparison.
- Removing a result or clearing history requires an in-app confirmation and
  deletes only owned private output files. Gallery copies and original inputs are
  kept. File paths are confined to the owned output directory. FileProvider exposes
  only the retained and legacy output folders, never databases or all app files.
- Startup removes unreferenced output files and abandoned encoder scratch files.
  Cleanup runs before legacy copying as well as after reconciliation, so a killed
  migration's partial copy does not permanently consume the remaining free space.

The database and filesystem cannot share one transaction. The ordering is:
create the new file, commit its history and completed-item record in one Room
transaction, then publish the state to the UI. A failed database transaction removes
only the new output. A process death before the transaction leaves an orphan that
startup removes; the item is interrupted and can be retried. Completed records
retain their referenced files during cleanup.

## Gallery and sharing

Gallery writes reserve an `IS_PENDING` MediaStore row and record its URI before
streaming bytes. The row becomes visible only after the copy completes. Publication
and its database acknowledgement run in a short non-cancellable section. Failed
or cancelled copies remove their pending row and keep the source result.

A stable, app-owned export name lets a retry find a row even if the process died
between MediaStore insertion and the database journal write. A published copy is
kept if database acknowledgement fails; startup/retry repairs the record instead
of creating another visible copy. Unclaimed pending rows from that narrow insert
window are hidden by MediaStore and subject to Android's pending-row expiration.
Save requests are serialized, and already exported results return the existing
URI. Save All updates each success immediately and reports individual failures;
retrying saves skips acknowledged successes.

Sharing checks availability off the main thread, supplies read grants through both
the intent and ClipData for every URI, and handles missing share applications or
permission failures with an actionable message. It never grants write access.

## Database upgrade

Room schema **2** adds batch/item records, retained-file paths, export journaling,
availability and compression details. `MIGRATION_1_2` preserves existing history;
there is no destructive migration fallback. Both exported schemas are retained in
`app/schemas/`.

Legacy cache results are copied into durable storage before the new path is
recorded. The old file is kept until the record has been updated, so interruption
can safely retry the migration. Missing legacy files are marked unavailable;
existing gallery links are checked and preserved when readable.

## Verification and limits

Verified on 2026-10-30 in the configured cloud environment:

| Check | Result |
| --- | --- |
| Compression-engine and model regression tests | 29 passed |
| Repository/storage and schema-migration tests | 14 passed |
| Workflow, restoration and startup-retry tests | 7 passed |
| Gallery publication protocol tests | 5 passed |
| Sharing and FileProvider tests | 3 passed |
| Debug application and instrumentation APK builds | Passed |
| Android lint | 0 errors, 6 warnings, 1 hint |

All **58 tests** passed without skips. Five lint warnings and the hint predate
this step; the additional warning advertises a newer coroutine-test release.
The pinned test/runtime versions are explained below.

Run with the configured SDK/JDK:

```bash
bash gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

The new tests use Robolectric API 35, real Room/SQLite databases and temporary
files. Coverage includes the schema 1→2 migration, mixed-success batches,
cancellation, interrupted-job recovery, retry without recompressing successes,
duplicate job/save protection, quota and database-write failures, legacy file
migration, unavailable files, original/gallery preservation and path confinement.
Workflow tests cover settings snapshots, partial saving, selection limits and
SavedStateHandle restoration. MediaStore protocol tests use a controlled content
provider with real file-descriptor streaming to inject publication and journal
failures. Sharing tests check read-only grants and unavailable share targets.

These tests simulate persistent interrupted state and provider failures. They do
not replace force-stop/rotation tests on an Android device, OEM MediaStore testing,
physical low-disk tests or accessibility/layout validation. Those remain Step 6
release gates, alongside the device performance and visual-quality work recorded
in the [production plan](production-plan.md). The cloud software emulator remains
unreliable, as documented in the [baseline attempt](benchmarks/cloud-attempt-2026-10-30.md).

The coroutine test dependency is pinned to 1.9.0 to match the runtime resolved by
the app. Lint's newer-version notice for it is deferred to a coordinated dependency
update, rather than testing with a different coroutine runtime from production.
