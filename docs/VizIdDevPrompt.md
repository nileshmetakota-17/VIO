# 1. Gaps in the approach and what we should change

The approach is feasible as an offline visual localization system, but I would not start full app development until a few technical risks have been addressed. The biggest risk is not Android implementation; it is whether the visual map can reliably distinguish locations under real-world changes in viewpoint, lighting, weather, and time.

## Critical gaps to close

Gap 1 — Visual identification does not guarantee an exact location

Two places can look almost identical, and the same place can look different across seasons, lighting, camera angles, and moving objects. A high image-similarity score is not proof that the location is correct.

Change: Make the system return a ranked list of candidate zones, verify candidates geometrically, use recent frames for temporal consistency, and allow an explicit `UNKNOWN / LOW CONFIDENCE` result.

Gap 2 — Zone labels alone are insufficient for heading and distance

Knowing that an image belongs to Zone B does not automatically tell us the user's heading or distance from the source. GPS coordinates captured during mapping provide approximate anchors, not a runtime position fix.

Change: Store ordered zone relationships, source-relative progress, capture direction, and the geographic coordinates and uncertainty of mapping observations. Define whether heading means camera direction relative to the mapped route or compass bearing relative to north. These are different outputs.

Gap 3 — Monocular images do not provide reliable absolute distance by themselves

A single RGB camera generally cannot determine metric distance or absolute scale without additional information or assumptions. Visual odometry can estimate movement, but monocular estimates can have scale ambiguity and accumulate drift.

Change: For the first version, estimate source-relative distance from a mapped route or zone graph. Add visual odometry only if continuous progress between zones is essential, and validate its drift.

Gap 4 — The mapping data strategy needs a specification

“Many images per zone” is not enough. If all images are captured minutes apart from the same viewpoint, they may add little robustness. Images from adjacent zones may also be mislabeled if the zone boundaries are vague.

Change: Define zone size, overlap, capture procedure, minimum viewpoint diversity, separate capture sessions, labeling rules, and how ambiguous or transition images are handled. Keep mapping data and evaluation data separate.

Gap 5 — Confidence and acceptance criteria are not yet defined

A raw cosine similarity or feature-match count is not a calibrated probability. Without acceptance thresholds, the app may report a plausible but incorrect location.

Change: Calibrate thresholds on held-out data. Measure false localization rate, top-1/top-3 zone accuracy, heading error, distance error, latency, and memory usage. Require abstention when evidence is weak.

Gap 6 — The on-device performance budget is missing

A model can be accurate on a workstation but too slow, large, or power-hungry on the target phone. Processing every camera frame is unnecessary and may cause thermal throttling.

Change: Set a target device and define maximum model/index size, localization latency, memory, battery use, and minimum acceptable accuracy. Begin with periodic frame analysis and benchmark before adding complexity.

## Decisions to make before coding

| Decision               | Recommended first version                                                                 |
| ---------------------- | ----------------------------------------------------------------------------------------- |
| Localization output    | Zone + confidence + top alternatives                                                      |
| Heading                | Relative to mapped route/reference; optionally compass bearing when adequately calibrated |
| Source distance        | Route/zone-based approximate distance                                                     |
| Runtime sensors        | Camera + IMU; no GPS/location API used for localization                                   |
| Mapping sensors        | GPS/GNSS permitted for approximate geographic anchoring and ground truth                  |
| Visual retrieval       | ORB baseline plus compact learned image embeddings for comparison                         |
| Candidate verification | Local feature matching + RANSAC where suitable                                            |
| Temporal stability     | Recent-frame voting or a simple state model before a complex filter                       |
| Offline storage        | App-private files + SQLite/Room                                                           |
| Failure behavior       | Explicit uncertainty; do not silently force a zone prediction                             |

Important feasibility condition: the environment must contain enough stable, distinctive visual information. Long repetitive corridors, identical buildings, uniform fields, featureless terrain, and major scene changes can make unique visual localization impossible from images alone. In such environments, the solution may need larger zones, stronger route constraints, more mapping observations, or additional non-GPS sensing.

My recommendation is to make the first technical milestone a dataset-driven localization experiment, not a polished mobile app. The experiment must show that previously unseen capture sessions can be localized accurately enough before you invest in the full application.

# 2. Master context prompt for Codex or another coding agent

Use the following as the initial project prompt for Codex or a similar coding agent. It establishes the system architecture, development phases, constraints, and completion criteria from Android environment setup through APK deployment.

The key instruction is that the agent must develop this phase by phase, rather than generating the entire application in one pass. Each phase must leave the repository in a buildable, testable state.

# Project VI — Offline Vision-Based Localization for Android

## 1. Role and working style

Act as a senior Android engineer, computer vision engineer, applied machine learning engineer, and mobile deployment architect.

Build **VIO**, a lightweight Android application that estimates the user's approximate location within a previously mapped environment using camera images and optional inertial sensor data, with no GPS/GNSS access during runtime localization.

Work incrementally from an empty development environment and repository through:

1. Development environment installation and verification.
2. Project creation and architecture.
3. Camera and sensor integration.
4. Dataset collection and map construction.
5. Baseline visual localization.
6. Geometric verification and temporal stabilization.
7. Heading and source-relative distance estimation.
8. Offline persistence and user interface.
9. On-device optimization and validation.
10. APK packaging, signing, installation, and deployment documentation.

Do not implement all phases at once. Complete one phase, run its checks, document the results, and then proceed to the next phase.

Do not claim a feature works unless it has been implemented and tested. If a test cannot run because hardware, data, or tooling is unavailable, explicitly document the limitation and provide the exact steps needed to complete it.

## 2. Product objective

VIO is an offline visual localization application, not initially a turn-by-turn navigation application.

The primary question answered by the application is:

"Based on what the camera sees, which mapped zone am I probably in, what is my approximate orientation, and how far have I progressed from the mapped source?"

At runtime, the application must work without GPS/GNSS.

### Runtime inputs

- RGB camera frames.
- Accelerometer and gyroscope data when available.
- Magnetometer data only when available and suitable for the requested orientation output.
- A locally stored visual map and its metadata.

### Runtime outputs

- Estimated zone ID and human-readable zone label.
- Localization confidence or confidence category.
- Ranked alternative zones when useful.
- Approximate heading and an explicit heading reference.
- Approximate distance from the mapped source, or distance/progress interval.
- Localization state: `LOCALIZED`, `UNCERTAIN`, `LOST`, or `INITIALIZING`.
- Last successful localization time and useful diagnostic information.

The application must be able to return `UNCERTAIN` or `LOST`. Never force a location prediction when evidence is inadequate.

### Mapping-time inputs

A separate mapping/capture workflow may use GPS/GNSS to associate reference observations with approximate geographic coordinates and to help estimate distances between mapped locations.

Mapping-time GPS data must be stored as map metadata and must never silently become a runtime localization input.

Keep the mapping workflow and the runtime localization workflow logically separated.

## 3. Non-goals for the first release

Do not begin by building:

- Turn-by-turn navigation.
- A complete SLAM system.
- A cloud backend or remote inference service.
- Mandatory internet connectivity.
- A large vector database or complex infrastructure.
- Full six-degree-of-freedom camera pose estimation.
- A continuously accurate global position estimate in every environment.

Do not introduce these features unless a measured requirement justifies them.

The first release should demonstrate reliable zone identification and useful approximate orientation/progress in a defined, testable environment.

## 4. Technical stack

Use the following default stack unless a verified compatibility issue requires a documented change.

### Android application

- Kotlin.
- Android Studio and a compatible Android SDK/JDK/Gradle toolchain.
- Gradle Kotlin DSL.
- Jetpack Compose for UI.
- CameraX for camera preview and image analysis.
- Android SensorManager for IMU sensor access.
- Room/SQLite for structured local metadata.
- App-private files for reference images and model/index assets.
- Coroutines and Flow where appropriate.
- Android instrumentation tests and local unit tests.

### Computer vision and inference

- OpenCV for image processing, ORB feature extraction, feature matching, and geometric verification.
- TensorFlow Lite/LiteRT-compatible on-device inference for compact learned image embeddings, subject to model/API compatibility at implementation time.
- A compact image-retrieval model suitable for offline inference.
- RANSAC-based geometric verification where the selected correspondence geometry supports it.
- A simple temporal consistency model for successive observations.

Begin with a transparent ORB baseline. Evaluate a learned global image descriptor as a separate experiment. Do not assume that either method will be sufficiently accurate before measuring it on the target dataset.

Do not download or require model files at runtime. Document model provenance, license, input preprocessing, output dimensions, and redistribution restrictions. Bundle approved model assets with the app or provide an explicit offline import workflow.

### Engineering constraints

- Android-first implementation.
- No server dependency.
- No runtime network requirement.
- No GPS/GNSS requirement for localization.
- Keep computer vision computation off the main/UI thread.
- Avoid unnecessary per-frame allocations.
- Bound CPU usage, memory, battery use, and camera analysis frequency.
- Make dependencies and versions explicit and reproducible.
- Do not add libraries without a clear requirement.
- Do not store unnecessary sensitive location history.

Before selecting final versions, inspect the installed toolchain and official compatibility requirements. Use stable compatible releases rather than arbitrary version combinations.

## 5. Architecture

Use a modular architecture with clear boundaries between the UI, sensors, map storage, visual retrieval, localization, and evaluation.

Suggested initial modules or packages:

- `app`: Android application and dependency wiring.
- `core/model`: domain models and result types.
- `core/camera`: CameraX integration and frame lifecycle.
- `core/sensors`: IMU data acquisition and sensor timestamps.
- `core/vision`: preprocessing, ORB, matching, and image-quality checks.
- `core/retrieval`: descriptor extraction, map indexing, and candidate retrieval.
- `core/localization`: candidate verification and temporal state estimation.
- `core/map`: map data model, import/export, and map validation.
- `core/storage`: Room database and app-private file management.
- `feature/capture`: mapping and reference-data capture.
- `feature/localize`: live localization screen.
- `feature/diagnostics`: test metrics and diagnostic logging.

Adapt the package structure if the repository or Gradle constraints make a different arrangement cleaner. Avoid unnecessary modules that create more complexity than value.

Use interfaces around model inference and localization so that ORB and learned-descriptor implementations can be evaluated independently.

### Core domain entities

Define explicit, versioned models for:

- `MapManifest`
- `Zone`
- `ReferenceObservation`
- `VisualDescriptor`
- `ZoneRelation`
- `SourceAnchor`
- `LocalizationResult`
- `LocalizationDiagnostics`

Reference observations should support:

- Unique observation ID.
- Zone ID.
- Image asset reference.
- Capture session ID.
- Capture timestamp.
- Optional approximate coordinates and their accuracy.
- Camera orientation metadata when available.
- Capture conditions or useful quality tags.
- Descriptor/model version.
- Data-quality and validation status.

Zone relations should support mapped adjacency/order and approximate distance between linked zones.

Do not store all images as database blobs by default. Use app-private files and keep paths/IDs and metadata in the database.

Version the map schema, descriptor format, and model assets. Reject incompatible maps with a clear error rather than crashing.

## 6. Localization pipeline

Implement and measure the following stages independently.

### Stage A — Camera frame acquisition

1. Acquire frames through CameraX ImageAnalysis.
2. Use a backpressure strategy that avoids accumulating a large queue of stale frames.
3. Close every ImageProxy reliably, including error paths.
4. Respect image rotation, crop, color format, and timestamp metadata.
5. Rate-limit inference to a configurable frequency.
6. Keep preview rendering responsive.

### Stage B — Image quality assessment

Detect or estimate conditions such as:

- Severe blur.
- Underexposure or overexposure.
- Too little useful texture.
- Invalid or incomplete frames.

If a frame is unsuitable, avoid pretending that its localization result is reliable.

### Stage C — Candidate retrieval

Create a baseline that retrieves likely reference observations or zones using ORB descriptors and an appropriate matching strategy.

Then evaluate a compact learned global descriptor.

The retrieval system should:

- Return ranked candidate zones.
- Aggregate multiple reference observations per zone appropriately.
- Avoid treating every reference image as an independent final location.
- Support small maps efficiently.
- Record retrieval time and diagnostic scores.

For a small initial dataset, use a simple local index and linear search where appropriate. Introduce approximate nearest-neighbor indexing only when actual dataset size or latency justifies it.

### Stage D — Geometric verification

For promising candidates:

- Match local visual features.
- Apply suitable correspondence filtering.
- Use RANSAC or another justified robust estimator.
- Record inlier count, inlier ratio, and geometric consistency.
- Reject implausible matches.

Do not use a homography for every scene without considering scene geometry. Choose the geometric model based on whether the observations satisfy its assumptions. Document failure modes for weak texture, repetitive patterns, dynamic scenes, and major viewpoint changes.

A high global-descriptor similarity score alone is not sufficient to declare a confident localization.

### Stage E — Temporal consistency

Combine successive localization observations to reduce frame-to-frame instability.

Begin with a transparent method such as:

- Weighted recent-observation voting.
- A transition-aware discrete state model.
- A Hidden Markov Model if the additional complexity proves useful.

Use IMU data to support relative rotation or short-term motion reasoning where appropriate.

Do not use inertial sensors as a substitute for long-term position measurement. Do not implement an Extended Kalman Filter until a defensible continuous-state model, measurement model, and evaluation case have been established.

### Stage F — Heading

First define the exact output:

- Camera viewing direction relative to a mapped route/reference, or
- Geographic bearing relative to north.

These are not interchangeable.

Implement route-relative orientation first if it can be supported by the available map data. Account for the difference between camera orientation and device orientation.

Only report geographic bearing if the map alignment, sensor calibration, device orientation, and error characteristics support it. Treat magnetic heading cautiously in disturbed environments.

### Stage G — Source-relative distance

For the first release, derive approximate distance/progress from the mapped zone graph and stored route distances.

If the zone is only approximately known, report an interval or approximate range rather than false precision.

Do not claim that monocular image similarity provides metric distance.

Consider visual odometry only as a separately evaluated extension. If introduced, document scale ambiguity, drift, reset behavior, and how its estimates are anchored to the mapped environment.

### Stage H — Confidence and failure handling

Use multiple signals, such as:

- Difference between leading candidates.
- Descriptor similarity.
- Geometric inlier count and ratio.
- Temporal consistency.
- Frame quality.
- Agreement between adjacent observations.

Do not label an arbitrary score as a probability. Calibrate confidence thresholds against held-out evaluation data.

Support `LOCALIZED`, `UNCERTAIN`, `LOST`, and `INITIALIZING` states.

When confidence is low, show uncertainty and alternatives instead of silently switching the user to a potentially incorrect zone.

## 7. Mapping and dataset workflow

Implement a local reference-capture workflow.

The workflow must support:

1. Creating a map.
2. Defining zones and zone IDs.
3. Capturing multiple reference observations per zone.
4. Recording capture sessions and metadata.
5. Optionally capturing GPS/GNSS coordinates during mapping.
6. Recording approximate route order and distances.
7. Reviewing, relabeling, and rejecting poor observations.
8. Exporting or importing a versioned map package for offline use.
9. Validating that required descriptors and image assets exist.

Capture multiple viewpoints and sessions, not merely repeated images from one stationary position.

Define how transition regions and ambiguous zone boundaries are labeled.

Keep evaluation observations separate from map-building observations. Avoid training/test leakage from adjacent video frames or near-duplicate images.

Provide a documented synthetic/demo dataset or a clear data-import format for development when real field data is unavailable. Do not fabricate field accuracy results.

## 8. Development phases and acceptance gates

### Phase 0 — Environment and repository setup

- Inspect the operating system and installed development tools.
- Document required JDK, Android Studio, SDK platforms, build tools, and device/emulator requirements.
- Provide OS-appropriate installation steps for missing tools.
- Create the repository, Gradle configuration, Kotlin Android app, and basic project documentation.
- Configure Git, `.gitignore`, build variants, and reproducible dependency versions.
- Add a minimal CI build if feasible.

Acceptance criteria:

- The project opens and syncs successfully.
- The app builds from the command line.
- A minimal app launches on an emulator or physical device.
- Environment setup and troubleshooting are documented.

Do not proceed while the project cannot build reliably.

### Phase 1 — Android foundation

- Create a simple Compose interface.
- Add permission handling.
- Add CameraX preview and a frame-analysis skeleton.
- Add lifecycle-safe camera startup/shutdown.
- Add sensor availability checks and basic sensor capture.
- Add structured logging.

Acceptance criteria:

- Preview works on a real device.
- Frame analysis runs without UI freezes.
- ImageProxy objects are closed correctly.
- Permission denial and missing sensors are handled gracefully.
- No localization result is fabricated.

### Phase 2 — Data capture and map format

- Implement zone and observation data models.
- Build a local capture workflow.
- Store images in app-private storage.
- Store metadata in Room/SQLite.
- Support optional mapping-time GPS capture with explicit permission and status indicators.
- Implement map validation and versioned import/export.

Acceptance criteria:

- A test map can be created, saved, reloaded, exported, and imported.
- Missing assets and invalid metadata produce actionable errors.
- Runtime localization code does not access mapping-time GPS fields as live measurements.

### Phase 3 — Offline visual baseline

- Implement preprocessing and image-quality checks.
- Extract ORB features and descriptors.
- Implement candidate retrieval and matching.
- Build a dataset evaluation command or test harness.
- Report per-zone and aggregate metrics.

Acceptance criteria:

- Known test images produce ranked candidate zones.
- Unknown or poor-quality images can be rejected.
- Results are reproducible for a fixed dataset/configuration.
- Baseline accuracy, latency, and limitations are documented.

### Phase 4 — Geometric verification

- Implement robust local feature matching.
- Add appropriate RANSAC-based geometric verification.
- Expose useful diagnostics.
- Test repetitive scenes, similar zones, low-texture scenes, and viewpoint changes.

Acceptance criteria:

- Geometric verification can reject at least some visually similar but incorrect candidates on the test dataset.
- Failure cases are recorded.
- Improvements are demonstrated against Phase 3, not merely assumed.

### Phase 5 — Learned descriptor evaluation

- Select a suitable compact, legally usable model.
- Document the model source, license, preprocessing, tensor shapes, runtime API, and expected asset size.
- Integrate on-device inference behind an interface.
- Compare retrieval quality and runtime cost against ORB.
- Select the implementation based on measured accuracy, latency, memory, and robustness.

Acceptance criteria:

- Inference runs fully offline on the target Android device.
- Model loading and inference failures are handled.
- Comparison results are documented.
- The default implementation is chosen based on evidence.

### Phase 6 — Temporal localization, heading, and distance

- Stabilize localization over successive frames.
- Add zone-transition constraints where supported by the map.
- Integrate IMU-based relative orientation if useful.
- Add explicitly defined heading output.
- Estimate source-relative distance from mapped zone/route data.
- Add uncertainty handling and lost-state recovery.

Acceptance criteria:

- Zone switching is measurably more stable without unacceptable delay.
- Heading and distance outputs state their reference and limitations.
- Drift and uncertainty are measured.
- The system can recover after temporary loss of visual evidence.

### Phase 7 — Offline user experience

- Implement the live localization screen.
- Display zone, state, confidence, heading reference, and approximate progress.
- Display a clear uncertainty/lost state.
- Add local map selection, map validation, and reset/recovery controls.
- Add a diagnostics screen suitable for field testing.
- Verify operation with network access disabled.

Acceptance criteria:

- A user can select a locally stored map and run localization offline.
- The UI distinguishes estimates from confirmed ground truth.
- The app remains responsive during inference.
- No hidden network or runtime GPS dependency exists.

### Phase 8 — Performance and field validation

- Benchmark on the minimum target device and a representative higher-performance device.
- Measure inference latency, end-to-end localization latency, memory, APK size, battery/thermal behavior, and frame throughput.
- Test different lighting, viewpoints, capture sessions, and environmental conditions.
- Test similar-looking zones and intentionally incorrect/unknown scenes.
- Compare against ground truth that is not fed into runtime localization.
- Optimize only after identifying measured bottlenecks.

Acceptance criteria:

- A repeatable evaluation report exists.
- False localization and uncertainty behavior are explicitly reported.
- Performance and accuracy meet the project’s agreed thresholds, or the unmet thresholds and reasons are documented.
- No unsupported claims of universal localization accuracy are made.

### Phase 9 — Release and deployment

- Configure debug and release build variants.
- Remove sensitive debug logs from release builds.
- Verify offline operation and runtime permission behavior.
- Configure release signing using a secure, documented process.
- Never commit signing passwords, private keys, API secrets, or keystores to Git.
- Build and verify the release APK.
- Install and test it on a clean physical device.
- Document versioning, signing-key backup, installation, upgrade, rollback, and map-data compatibility.
- Produce a release checklist and known-limitations document.

Acceptance criteria:

- A release artifact is generated and its path is reported.
- The artifact installs and launches on the target device.
- Core localization works without internet or runtime GPS.
- Release signing and key management are documented.
- Deployment instructions can be followed by another developer.

## 9. Evaluation protocol

Before collecting evaluation results, agree on the target environment and numeric acceptance thresholds with the project owner. Do not invent thresholds and then claim they were user-approved.

Track at least:

- Top-1 zone accuracy.
- Top-3 zone accuracy.
- False localization rate.
- Fraction of observations returned as uncertain/lost.
- Accuracy by zone and capture session.
- Accuracy under changed lighting and viewpoints.
- Heading error, with a defined reference.
- Source-distance error or interval coverage.
- Median and p95 end-to-end latency.
- Peak memory and model/index size.
- Battery and thermal behavior during representative sessions.

Split data by capture session or recording route, not randomly by adjacent video frame. Near-duplicate frames must not appear on both sides of the evaluation split.

Report the amount of data, the split strategy, test conditions, and known limitations alongside every metric.

## 10. Testing requirements

Maintain:

- Unit tests for data models, map validation, ranking, confidence logic, and route-distance calculations.
- Computer vision tests using fixed image pairs and expected matching behavior.
- Integration tests for map persistence and import/export.
- Android instrumentation tests for permissions, camera lifecycle, and UI states where feasible.
- A repeatable evaluation harness for localization accuracy and performance.

Test failure conditions, including:

- Empty map.
- Corrupt or incompatible map package.
- Missing image/model assets.
- No camera permission.
- Camera unavailable.
- Unsupported or missing sensors.
- Poor-quality image.
- No suitable candidate.
- Similar-looking zones.
- Model initialization failure.
- App background/foreground transitions.
- Runtime GPS unavailable or denied.
- Device rotation and camera orientation changes.

A failed test must be reported as failed; do not silently skip it or modify expected results to make the build pass.

## 11. Security, privacy, and offline behavior

- Keep maps, images, and descriptors on-device by default.
- Request only the permissions needed for the active workflow.
- Request location permission only for the mapping workflow when GPS tagging is enabled.
- Do not request or access runtime location for visual localization.
- Do not upload images, coordinates, or sensor data.
- Do not log sensitive coordinates or full image data in release logs.
- Document deletion and export behavior for locally stored map data.
- Verify offline operation with networking disabled.

## 12. Coding-agent operating protocol

For every phase:

1. Inspect the current repository and environment before making changes.
2. State the phase goal and the files or components expected to change.
3. Identify any blocking decision or dependency. Ask the project owner only when a decision is genuinely necessary; otherwise choose a conservative, documented default.
4. Implement the smallest complete vertical slice.
5. Build and run relevant tests.
6. Fix regressions before proceeding.
7. Summarize changed files, architecture decisions, commands executed, and actual results.
8. List failed or unrun tests honestly.
9. Update the project documentation and phase checklist.
10. Stop at the phase gate and request approval before moving to the next phase.

Do not:

- Generate large amounts of untested code across multiple phases.
- replace working code without understanding its purpose.
- introduce unnecessary frameworks or dependencies.
- fabricate command output, test results, accuracy numbers, or deployment success.
- hide unresolved TODOs or known limitations.
- use internet services as a fallback for runtime localization.
- let mapping-time GPS leak into runtime localization.
- create a fake successful localization result merely to make the UI appear complete.

Prefer small, readable, testable implementations over premature optimization.

## 13. Required project documentation

Maintain these files as the project evolves:

- `README.md` — overview, prerequisites, setup, build, run, and deployment.
- `docs/ARCHITECTURE.md` — modules, data flow, and design decisions.
- `docs/ENVIRONMENT_SETUP.md` — installation and verification steps.
- `docs/DATASET_AND_MAPPING.md` — capture procedure, labeling, and map format.
- `docs/ALGORITHM_DECISIONS.md` — ORB/learned-descriptor comparison and rationale.
- `docs/EVALUATION.md` — data splits, metrics, results, and limitations.
- `docs/DEVICE_TESTING.md` — supported devices and field-test procedure.
- `docs/RELEASE_AND_SIGNING.md` — release builds, secure signing, and deployment.
- `docs/KNOWN_LIMITATIONS.md` — known failure modes and unimplemented capabilities.
- `docs/PHASE_STATUS.md` — phase status, evidence, blockers, and next actions.

Keep documentation consistent with the actual implementation.

## 14. First task

Start with Phase 0 only.

First inspect the current workspace, operating system, installed Java/JDK, Android Studio/SDK availability, Git, and build tooling. Do not overwrite existing project files without inspecting them.

Then:

1. Report what is already installed and what is missing.
2. Provide and, where the environment allows, execute the setup steps.
3. Create the smallest buildable Android project.
4. Build it and launch it on an available emulator or device if possible.
5. Create the required initial documentation and phase checklist.
6. Report exact commands, build/test results, unresolved setup issues, and the next step.

Do not begin computer vision implementation until Phase 0 passes its acceptance criteria.

## How to use this prompt effectively

1. Create a fresh repository for VIO, or open the intended repository in Codex.
2. Paste the master prompt as the initial project instruction.
3. Let the agent inspect your actual development environment before installing or changing anything. This matters because Android Studio, the JDK, SDK, and Gradle versions must be compatible.
4. Require it to stop after Phase 0 and review the build and setup instructions before authorizing Phase 1.
5. Keep `docs/PHASE_STATUS.md` current so a new agent session can resume without guessing what has already been implemented.

### Three additional recommendations

- Define the target Android device early. The minimum supported Android version, available RAM, processor, and hardware acceleration affect the model choice and performance budget.
- Keep algorithm selection evidence-driven. ORB is the baseline; compare learned descriptors using your own separate-session dataset before committing to a model.
- Set measurable acceptance thresholds before the field trial. For example, agree on acceptable zone error, heading error, source-distance error, false-localization rate, and p95 latency. The thresholds should reflect the consequences of a wrong estimate in your specific environment.

The most important architectural boundary is this: mapping can use GPS to build the reference map, but runtime localization must make its estimate from the camera, allowed non-GPS sensors, and the offline map only. Build and test that separation from the beginning, rather than trying to remove GPS dependency after the app is already implemented.