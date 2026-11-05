# Production readiness plan

Current status (31 October 2026): the final editor validation and release-candidate
work is recorded in [the current execution report](validation/editor-step-6-execution.md)
and [release guide](release/README.md). The owner selected `com.srizan12.shrink`
for publication. The original stage descriptions and counts below are historical;
physical acceptance and owner-controlled signing remain separate release gates.

## Product decisions

Updated 2026-10-30 after plan approval.

| Decision | Basis |
| --- | --- |
| Balance visual quality and file size for everyday photos | Confirmed by the user |
| Prioritize modern Android phones and tablets | Latest user answer; supersedes the earlier Android 8+ answer |
| No existing publication, deadline, or background-processing constraints were supplied | User reported no such constraints |
| Use Android 12 / API 31 as the production minimum | Implemented in Step 3 for modern phones and tablets |
| Keep processing on-device; do not add accounts, uploads, or analytics by default | Preserve the existing product's privacy model |
| Foreground compression with explicit progress and cancellation for v1 | No requirement to guarantee completion after leaving the app or process death |
| Material 3 visual foundation, with full light/dark/system themes and adaptive tablet layouts | Confirmed in the supplied UI brief; Shrink working name, calm teal fallback, Material You, system type |

This is a staged implementation plan. Step 1 adds requirements and measurement
infrastructure. It does not change shipping compression, navigation, or UI behavior.
Step 3 raises the minimum SDK and removes obsolete storage permission paths.

## Sequence and completion criteria

| Step | Priority | Deliverable | Completion criteria |
| --- | --- | --- | --- |
| 1. Requirements and baseline | P0 | This specification, licensed/generated fixtures, repeatable native Android benchmark, baseline report | Confirm product priorities, build/run the harness, record actual results and measurement limitations |
| 2. Compression engine | P0 | Bounded target-size search, correct geometry/alpha/metadata handling, memory limits and cancellation | Correctness fixtures pass; final output sizes are checked; unmet constraints are explicit; compare against baseline |
| 3. Reliability and storage | P0 | Partial batch results, retry/cancel, lifecycle recovery, durable output policy and database migrations | Failure, interruption, permission and storage tests pass without modifying originals or losing completed results |
| 4. Design system | P1 | Semantic colors, type/spacing/shape scales, vector icons, launcher icon and reusable components | Representative component previews work in light/dark/system modes |
| 5. Adaptive UI | P1 | Select → configure → compress → compare → save/share; phones and tablets | No inaccessible controls/overflow on the window and accessibility matrix below |
| 6. Validation | P1 | Automated regression checks, CI, physical-device benchmarks and accessibility checks | Release gates below pass; failures and untested combinations remain visible |
| 7. Release candidate | P1 | Production identity, signing/versioning, tested release AAB, disclosures and beta checklist | No unresolved release blockers; publication is a separate action |

Step 1 requirements, corpus, and benchmark tooling are prepared. Device measurement
is blocked: the software emulator killed instrumentation during startup amid
system-wide ANRs. See [the initial attempt](benchmarks/cloud-attempt-2026-10-30.md).
No valid device baseline has been recorded. Step 2's engine and result integration
are implemented; see [algorithm and validation](compression-engine.md). Device
performance comparison and full-resolution quality calibration remain pending.
Step 3's batch recovery, durable retention, gallery-save journaling and database
migration are implemented; see [reliability and storage](reliability-and-storage.md).
Physical-device lifecycle, provider and low-storage validation remains pending.
Step 4's design system and initial screen redesign are implemented; see
[the design system and review captures](design/README.md). Step 5's adaptive flow
completion is implemented and checked across live window resizing, large text,
keyboard insets, batch results and selection edge cases; see
[the scope and layout matrix](design/adaptive-ui.md). All 88 host tests pass,
including 22 native Compose rendering/interaction checks. Debug and instrumentation
APKs build; lint has 0 errors and 2 dependency update notices. Physical
accessibility and device checks remain Step 6 gates. Step 6 now adds CI, native
compression/save/recreation checks, Android accessibility smoke tests, privacy
regressions and stricter performance reporting; see the
[validation guide](validation/README.md) and [execution record](validation/step-6-execution.md).
The CI workflow is prepared locally and device tests must still be executed.
Step 6's physical/manual release gates remain open. Step 7 has not started.

## Compression contract for implementation

- Required outputs: JPEG, PNG, and lossy WebP. Required inputs: still JPEG, PNG,
  and WebP. Evaluate HEIC/HEIF with real fixtures before advertising support;
  detect/reject unsupported or animated formats rather than silently losing frames.
- The default is a balanced preset. Encoder quality is an implementation setting,
  not a percentage of retained visual quality. Do not claim lossy conversion has
  “no quality loss.” Calibrate the preset against photos and text-heavy images.
- Preserve original files byte-for-byte. Create a separate output and never
  overwrite an existing gallery image implicitly.
- Apply all eight EXIF orientation states, including mirroring, to the pixels.
  Record upright orientation and strip GPS and other identifying EXIF by default.
- Preserve alpha in PNG/WebP. JPEG uses an explicit white background, visible in
  the preview. PNG quality is not a lossy size control: resizing changes pixels
  and must be described as resizing.
- Normalize supported inputs to predictable SDR/sRGB output. HDR, wide-gamut and
  gain-map handling require explicit validation and an honest conversion policy.
- Check target bytes on the final file after metadata writes. Return `met`,
  `unmet`, or `not requested`; never report success at meeting an impossible target.
  Keep a valid best candidate when a target cannot be reached within the allowed
  quality, dimensions, or work budget.
- Show actual before/after bytes and signed size change. Conversion can increase
  size; a zero-percent reduction must not conceal that increase. Explain when an
  input is already efficient. Preserve user-requested format conversions.
- Use bounded work, cooperative cancellation between native codec operations,
  and a memory-aware queue. Do not run multiple large bitmap pipelines just because
  more CPU threads are available.
- Initial input limits: 30 images per batch, 50 million pixels per input,
  at most 16,384 pixels on either edge, and 100 MiB encoded input size. These are
  implemented per-image validation limits in Step 2; Step 3 enforces the 30-image
  batch/selection limit. Unknown provider sizes use bounded reads. Source limits
  do not authorize full-resolution allocation.
- Do not silently label a memory-limited downscale “keep original dimensions.”
  Expose required resizing or explain why the requested operation is unavailable.
- Exact bytes are authoritative for target limits. Use consistent units in the
  product. The redesigned upload-limit interface uses decimal B/KB/MB, with
  1 MB = 1,000,000 bytes. Internal memory/storage bounds remain explicitly MiB.

## Reliability, privacy, and storage gates

- A failed item must not discard successful batch items. Show which items failed
  and make retry/cancel available without duplicating completed saves.
- Snapshot settings when a job starts. Guard duplicate jobs and propagate
  coroutine cancellation rather than converting it into a generic failure.
- Handle invalid dimensions, corrupt input, revoked URI grants, missing provider
  data, full storage, and unavailable share targets with actionable messages.
- Clean up temporary buffers and incomplete files on failure. Define retention
  and quotas for unsaved results; saved gallery items belong to the user and must
  not be deleted by cache cleanup or clearing history.
- History must distinguish retained, exported, and unavailable results. Test
  database migrations; do not use destructive fallback for production user data.
- Restore small UI state/identifiers after recreation; do not put bitmaps in saved
  state. If a process dies mid-job, report interruption and offer a safe retry.
- Preserve narrowly scoped FileProvider URIs and read grants. Treat MIME types,
  filenames and dimensions from content providers as untrusted input.
- Do not log personal image contents, location metadata, or raw source URIs.
  Third-party telemetry requires an explicit product decision and disclosure.

## Device, layout, and accessibility matrix

- OS: API 31 minimum, API 35 intermediate, and API 37 current target; include a
  non-Google OEM phone. Actual physical models are still to be selected.
- Phone baseline: a modern 4 GiB RAM device, plus an 8 GiB device. Tablet baseline:
  an 8 GiB device in portrait/landscape and split-screen.
- Windows: 320, 360, 600, and 840+ dp widths; short landscape windows; keyboard
  visible; gesture and three-button navigation. Derive layouts from available
  window space, not model names or orientation alone.
- Themes: system/light/dark; dynamic colors only where contrast remains correct.
- Accessibility: 100%, 150%, and 200% font scale; TalkBack order/labels/actions;
  minimum 48dp touch targets; contrast at least 4.5:1 for normal text and 3:1 for
  large text and essential non-text controls; do not convey state by color alone.
- Screens: onboarding, selection, settings, progress, results/comparison, history,
  and app settings, including empty, loading, partial-failure, and permission states.

## Proposed performance and release gates

These are engineering targets to validate on physical hardware, not measured
claims or universal guarantees. Adjust explicitly if real measurements show a
tradeoff with quality or safety; do not silently weaken them.

| Metric | Initial target | How to validate |
| --- | --- | --- |
| Balanced 12MP JPEG on baseline phone | Median ≤2s; p95 ≤5s | Fixed corpus, one warmup, 20+ measurements on the same device/build |
| Target-size 12MP JPEG | p95 ≤8s, with bounded search | Same run conditions; report unmet targets separately |
| Peak process memory | ≤256 MiB on the baseline phone for required cases | Profile real peak allocations/RSS; sampled benchmark PSS is supporting evidence only |
| Batch reliability | 30 images complete or produce explicit per-item outcomes; no crash/ANR | Mixed corpus, cancellation, low-storage and revoked-permission tests |
| Quality | No incorrect orientation, lost required transparency, unreadable text or obvious new artifacts in the approved balanced corpus | Full-resolution visual comparisons plus calibrated objective metrics; preview PSNR alone cannot pass this gate |
| Release correctness | No unresolved data-loss, privacy, crash, or misleading-result defects | Unit/instrumentation/UI tests and beta results |

Full-size user-representative photos and final physical device models remain useful
inputs. The visual brief and references have been supplied and implemented.
This cloud host lacks KVM and previously suffered Android System UI ANRs: emulator
timing and memory values cannot establish the physical-device gates above.

## Existing risks to address

- Step 2 now reports unmet targets, includes metadata in size decisions, handles
  mirrored EXIF and releases engine resources on failure/cancellation. Its memory
  policy still needs physical-device peak-memory and quality calibration.
- Step 3 preserves individual batch successes, retains outputs in private files,
  journals exports and migrates history without destructive fallback. Real-device
  force-stop, provider and low-storage checks remain release gates.
- Steps 4–5 replace fixed colors and symbol icons with a semantic theme and vector
  icon system, adaptive layouts, accessible comparison controls and remembered
  presets. Real TalkBack, OEM dynamic-color and keyboard/inset checks remain open.
- Step 2 adds policy/search/container and native-graphics regression tests. The
  device baseline added in Step 1 remains unexecuted successfully; host tests do
  not establish physical performance or production release readiness.
- Production package identity, release shrinking/signing, native library
  compatibility and store disclosures remain Step 7 work. Step 6 CI is prepared;
  its first remote execution and physical-device gates remain unverified.
