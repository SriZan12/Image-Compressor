# Editor redesign plan

This is the newly approved six-step editor redesign. Its numbering is separate
from the earlier [production-readiness plan](../production-plan.md). Earlier
design captures and validation records remain evidence of those earlier changes.

The task is simple: someone has a 1.2 MB photo and needs it below a form's 500 KB
limit. They should choose a photo, see what will change, and save or share a copy
without learning about encoding.

## Experience to deliver

1. Choose a photo using the existing Android Photo Picker.
2. See a generous preview, the original file size, and quiet picture dimensions.
3. Move **Smaller file → More detail**, or choose **Size limit**: 100 KB, 200 KB,
   500 KB, 1 MB, or Custom. Choosing a limit starts preparing a result automatically.
4. Read the before-and-after size. During work, a size prediction is explicitly
   an estimate. Once ready, the saved file's measured bytes determine whether it
   fits. Rounded display values never establish that a limit was met.
5. Optionally compare with the original, then **Save photo** or **Share** from
   the same screen. Keep the original untouched.

The chosen size limit remains visible while changing the slider. The final live
session must give these controls one consistent meaning: the limit is the maximum
file size, and the slider expresses the preferred balance of size and detail.
Changing either invalidates the previous result for export and prepares a new
one. Any compromise in dimensions, transparency, or appearance must be explained.
If a suitable result cannot be made, say so and offer a useful next action.

File-size compliance does not establish exam, visa, KYC, or ID-photo compliance.
Dimensions, accepted file types, crop, background, pose, and document authenticity
vary between services. The app must not promise approval by an upload portal.

## Implementation sequence

| Step | Work | Acceptance criteria | Status |
| --- | --- | --- | --- |
| 1. Main-screen design | Establish Poppins, the uncluttered editor layout, adaptive presentation, and reusable loading/result/error states. Produce native Compose review renders. | Original information and the result have clear hierarchy; Advanced starts closed; quick limits and plain slider labels are present; phone/tablet, dark mode, and large-text renders are reviewable; estimated and stale component states cannot export. The shipping adapter remains honest about its manual preparation step. | Complete for review. See [Step 1 and validation](editor-step-1.md). |
| 2. Photo-opening performance | Measure the current source-opening and Coil path before changing it. Inspect metadata reads, requested decode size, cache behavior, placeholders, crossfade, and recomposition. | Repeatable cold/warm measurements identify where time is spent. Requests use an appropriate display size, preview geometry stays stable, and changes show measured benefit without degrading orientation or preview quality. | Implemented and host-validated; device latency pending. See [Step 2](editor-step-2.md). |
| 3. Live temporary editing session | Add responsive size feedback, cancellation of superseded work, bounded target-size search, and temporary preview ownership. | Slider interaction stays responsive; estimates are clearly labelled; only the latest source/settings can complete; work and memory are bounded; a drag does not create history entries or permanent outputs. Verify target search and cleanup with meaningful tests. | Implemented and host-validated. Device latency remains pending. See [Step 3](editor-step-3.md). |
| 4. Automatic result and export | Connect quick/custom limits and slider changes to preparation, then export the verified result directly from the editor. | Selecting 500 KB starts work without another confirmation; export uses the exact verified artifact currently shown; stale results cannot save/share; impossible limits have a useful explanation; saved location and original preservation are clear. | Implemented and host-validated for single photos. Device checks remain pending. See [Step 4](editor-step-4.md). |
| 5. Supporting flows and accessibility | Align first launch, home, selection, comparison, recent photos, settings, batch handling, and failures with the editor. | Clear next actions across flows; state survives rotation/resizing appropriately; keyboard, split-screen, landscape, large text, reduced motion, contrast, and TalkBack checks pass on the supported device range. | Implemented and host-validated. Physical-device accessibility checks remain pending. See [Step 5](editor-step-5.md). |
| 6. Production validation | Run regression, performance, privacy/storage, permission, recovery, and release-readiness checks. | Record executed checks and their environments; close critical failures; verify real-device latency, memory, cancellation, background/interruption behavior, accessibility, and release configuration. Document any remaining release gates. | Not started. |

Each step ends with actual app/component captures, a concise explanation of the
change, relevant test results, and a review checkpoint. Step 1 approval does not
make the later live-processing or release work complete.

## Existing work to reuse

- The native compression engine, EXIF handling, memory/geometry policy, and
  bounded target search provide the starting point for live preparation. The
  existing batch job is not itself a safe per-slider-update editing session.
- Android Photo Picker, retained-output storage, scoped gallery saving, checked
  sharing, history, and interruption handling remain useful. Temporary previews
  need separate ownership and cleanup before final export is connected.
- Existing theme preferences, adaptive layouts, comparison gestures, vector
  icons, and spacing tokens provide reusable foundations. Poppins replaces the
  previous typography direction.
- Existing host tests and device-validation tooling remain available. New
  component renders supplement them; they do not establish real-device
  performance or replace accessibility checks.

## Current boundary

The production editor automatically prepares temporary previews after settings
change. It shows **Updating size…** during work and the measured file size when
ready. Editing and comparison keep the preview temporary. For one photo,
**Save photo** or **Share** retains the exact verified file and creates one Recent
photos entry; retries reuse that entry. The original remains untouched. Multiple
photos offer a selectable live sample and **Prepare N photos** to run the full
batch once. Limits apply to each photo; totals and individual results are shown
after preparation. Step 5 aligns supporting flows and host-tested accessibility.
Device latency and release-readiness checks remain later validation work.
