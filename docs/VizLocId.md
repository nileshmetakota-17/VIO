# VID — Vision-Based Current Location and Pose Estimation

Your clarification changes the problem in an important way: VIO is not primarily a navigation system. It is an offline visual localization system that estimates where the device currently is by recognizing its surroundings.

We should reframe the problem, architecture, algorithms, and development plan around that objective.

## 1. Refined problem statement

## The core question: “Where am I?”

Build an offline Android application that estimates the device's current location and orientation by comparing its live camera view with a locally stored visual map of a previously captured environment.

The application should identify the most likely location zone, estimate the device's heading and approximate distance from a known source, and report a confidence level for that estimate — even when GPS/GNSS is completely unavailable.

### How the system should work

1\. Observe

Capture the current scene using the phone's RGB camera. Read available IMU sensors to help estimate orientation and motion.

[Visual Place Recognition Based on Dynamic Difference and Dual-Path Feature Enhancement | MDPI](https://images.openai.com/static-rsc-4/LDchQ9qOTFHyLPhGsXIMjzpBcbkZWdtU9yrQQlNrzEb7zk_kl6kgCgxYgg0bI6yHmDFg1v_RC97RqT2q6MQZ5Zc8irTfZDa8FvuO842pgENDv6ehSVl3NPDq1jleFy5pji1BwVJvWoxXwAdK5EILGlmcbH_BjFdhgw6sBWVXbAo?purpose=inline)

2\. Compare

Match the live scene against a local database containing reference images of known zones, including images captured at different times and under different conditions.

[神水交差点の右折信号設置と住民説明会　　2024.09.16 : 旧熊本市民病院解体工事の記録](https://images.openai.com/static-rsc-4/lbI1CwS7ORBaZlcc5lsruPM9xTCKgrWzvYxNDEG3-_lzRViPYSoCTg8c8qNPvXYlWEhbMy8tDJqIkWZpvLKrekkFj2a1L13krCldyk98tIFh-2fbsUgEtr7NkJtOP26y87OXqre1Fu5wfmocP-HfGE8LFqIZNp9ECCYOmOKGb70?purpose=inline)

3\. Estimate

Combine visual similarity, geometric consistency, temporal continuity, and sensor-derived orientation to identify the most likely zone and heading.

[Arrow GPS Offline - Navigation App | MWM](https://images.openai.com/static-rsc-4/xoYcAzWA47F3t3a56fa2wDfmxlXxQc2lq7SEvGwl8BuP7-lyrIDZfwA5sx00WBB9Fkw1toXS4jMGAGuYC1V9DrSR8ek_j5R2HtMNHIhdK1apL93_WBMjdsY07S45ifkzrILifNQmbHodLFGW40hSnmMKTjNZA6turXCR-U1Ht_I?purpose=inline)

4\. Report

Return the estimated zone, approximate distance from the source, heading, confidence, and whether the result is reliable or ambiguous.

### What this system is — and is not

| In scope                                 | Not the primary objective                           |
| ---------------------------------------- | --------------------------------------------------- |
| Visual place recognition                 | Turn-by-turn navigation                             |
| Current location/zone identification     | Route planning                                      |
| Approximate pose and heading estimation  | Autonomous vehicle control                          |
| Approximate distance from a known source | Guaranteed, continuous GPS-equivalent coordinates   |
| Confidence and ambiguity assessment      | Assuming every camera frame must produce a location |

A useful distinction: place recognition answers “Which known place does this look like?”; pose estimation answers “Where am I and how am I oriented relative to that place or map?” VIO needs both, but should begin with reliable zone identification.

## 2. Define the output before choosing algorithms

The initial product should estimate a location within a known, previously captured environment. It should not claim to locate itself anywhere in the world without a corresponding visual map.

A result could look like this:

## Current location estimate

Illustrative output

All values below are examples, not measured system performance.

Estimated zone

# Zone B-07

Northern bend

Location confidence

# 0.91

Illustrative score only

Distance from source

## \~420 m

Heading

## \~65°

Relative to map north, if calibrated

A production system should also expose the top alternative zones, estimated uncertainty, and a “location uncertain” status when evidence is insufficient.

The first design decision is to distinguish three outputs:

1. Zone identity: Which known area or route segment best matches the live scene?
2. Relative pose: What is the estimated position and orientation within that zone?
3. Source-relative distance: Approximately how far has the device travelled from the capture starting point?

These outputs have different difficulty levels. Zone identification is the best starting point; accurate metric pose and distance require additional evidence and testing.

## 3. The right solution architecture

I recommend a map-based visual localization system with multi-image zone recognition and temporal pose estimation.

The critical design choice is that a zone should not be represented by a single photograph. Each zone should have a collection of reference observations that captures its visual diversity.

### 3.1 Build a visual map of zones

During an initial mapping or capture phase, travel through the target environment while GPS/GNSS is available for reference annotation. Divide the environment into overlapping zones or route segments.

For example:

### Example of a visual map

[Rekordy polskich dróg. Najdłuższa trasa, najwyższy most i najbardziej prosta krajówka](https://images.openai.com/static-rsc-4/tlcMkiKd4O6sorUMVDEBJNlyIlWth4qrtuQW7Y75WvdY1t9S3ixaTqYZP7BTeEyEZDZAJqBzJX0MpX0FBzopjxOxgPkUzqpaZoYEbzN3MZ_yXwWhca8AXajh8JXw6tci-raQwMF0PDzZ-G8FIsQH5WqDlJmwPDt0KZcH7x8BODs?purpose=inline)

Zone A — Straight

Reference frames from multiple passes

[street in nature](https://images.openai.com/static-rsc-4/OHDs5N_qbcLorSatopTMLj7dNQJdZO86GdYeTv9VkxSS2IUFOg4GuzZVubDenCB1yRqqXkTr7YHAn7fIBZhDUf6LqSDnWwWbzoUj98Kh_Qz-Zs96Ua-3vv3nrkcKlsGJ7j0PWJJvST_Nc2a0sxt3_bzqEtHto1_BCWLHpzEP7cIcuDkavnkVHsvszmVfdO98?purpose=inline)

Zone B — Bend

Approach, apex, and exit views

[Road at Hole House © T  Eyre cc-by-sa/2.0 :: Geograph Britain and Ireland](https://images.openai.com/static-rsc-4/g4kHD4NnSNfef2HmwCNCTJ3VzRIGTwTFIv9jB3Me68BWRAB4fURx2anHY8T8GLlZKaugAihbrHODpVPLn6LxjfmS7H-KQhDs87xh9uylb5J_ez-kXgOUf6b08UlGjCPZJWXOwRmydoMEXRVNJF5710dREdehHpZzOCqA2kLdVfA?purpose=inline)

Zone C — Junction

Views from different approach directions

[Weather Alert: Heat Wave Likely in West Rajasthan, Jammu on May 27](https://images.openai.com/static-rsc-4/2UIdNABnPf-42MQjGcYcQphjIONYE-IuK-7b0Qopq4qDqT5n8SWmMAec4tOwJuziRTNOwqzgGBz9rDC9hQuevLq_iILKELlkbdpGOqP6_c5-YAHlCNOn_CZDJK9zBGEM66pXCZZ6CfH213HIHAT2xAnUTZMq88mt5I4aKSn4UH4?purpose=inline)

Zone D — Feature-poor

Repeated observations to measure ambiguity

Illustrative zone categories. The real zones should be defined from your target environment, not from these example images.

Each zone should store:

- Multiple reference images captured at different times, viewpoints, and conditions.
- Capture-session and timestamp identifiers.
- GPS coordinates when available during mapping, for evaluation and approximate geographic anchoring.
- Approximate distance along the captured route from the source.
- Camera orientation and available IMU measurements.
- Visual descriptors and local feature descriptors used for matching.
- Links to neighboring zones and their expected order, where known.

Important: More images do not automatically mean better recognition. You need useful diversity, including difficult conditions, and you must avoid treating near-identical adjacent frames as independent evidence.

### 3.2 Runtime localization pipeline

Live RGB frame + available IMU

No runtime GPS required

Image quality and preprocessing

Blur, exposure, orientation, usable visual content

Visual place recognition

Retrieve the most likely reference zones

Geometric and temporal verification

Verify candidates; use recent estimates and motion context

Location and pose estimator

Zone, relative position, heading, uncertainty

Result

Best zone · alternatives · distance estimate · heading · confidence

The pipeline should have two complementary visual stages:

1. Candidate retrieval: Quickly find a small number of likely zones from the entire local visual map.
2. Candidate verification: Check whether the best candidates are geometrically and temporally consistent with the current observation.

This is preferable to comparing every live frame exhaustively against every stored image using a single similarity score. Visual place recognition research commonly uses global descriptors to retrieve candidate places before more detailed verification.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://openaccess.thecvf.com\&sz=32)

CVPR Open Access

+1



### 3.3 What does “pose” mean for your first version?

Avoid attempting full six-degree-of-freedom pose estimation in the first prototype.

Start with:

- Zone ID: The recognized section of the environment.
- Progress within the zone: Early, middle, or late; later refined to a continuous estimate if feasible.
- Heading: Relative to the mapped route or reference camera orientation.
- Source-relative distance: Approximate distance along the mapped route.
- Confidence and uncertainty: Whether the estimate is sufficiently supported.

Full 6-DoF pose — three-dimensional position and three-dimensional orientation — is a possible later extension. A single monocular camera does not directly provide absolute metric scale, so this requires additional constraints or a mapped geometric reference.

## 4. How to estimate the current location

### Stage A — Visual zone identification

The live frame is transformed into a compact visual representation and compared against the stored representations of reference images.

The initial system should return the top candidates, not just a single match:

| Candidate | Meaning                                                  |
| --------- | -------------------------------------------------------- |
| Zone B-07 | Best visual match                                        |
| Zone B-06 | Nearby alternative                                       |
| Zone C-02 | Visually similar but geographically separate alternative |

The system then checks whether the candidates are plausible given previous estimates and sensor evidence.

### Stage B — Geometric verification

Two images can look similar without depicting the same place. Verify candidate matches using local image features and geometric consistency.

For example, the system can detect keypoints, match descriptors, reject inconsistent correspondences using RANSAC, and calculate a geometric consistency score.

This helps reject false matches, although it cannot guarantee that visually repetitive scenes will be distinguishable.

### Stage C — Temporal consistency

A single frame can be blurred, overexposed, or ambiguous. Use several successive observations to stabilize the estimate.

A probabilistic filter can combine:

- Current visual match scores.
- Previous zone and pose estimate.
- Plausible movement between successive observations.
- IMU-derived rotation or motion cues.
- Expected neighboring zones from the mapped environment.

A Hidden Markov Model (HMM) is a reasonable first implementation for discrete zone identity. An Extended Kalman Filter (EKF) becomes relevant if the state includes continuous position, heading, and motion.

### Stage D — Heading and distance

These outputs must be defined carefully.

Heading: The camera image alone does not inherently reveal compass north. The system can estimate orientation relative to a matched reference view or mapped route. To report a heading in geographic degrees, the mapping must establish a geographic reference and the phone-camera orientation must be calibrated. Magnetometer readings may help when available, but can be unreliable near metal and electrical equipment.

Distance from source: GPS captured during mapping can provide approximate distance labels for the visual map. At runtime, the system can infer an approximate position along the mapped route from the recognized zone and estimated progress. However, if the device is moving and GPS is absent, the distance estimate can drift, especially through long ambiguous or feature-poor sections. A zone label alone cannot establish precise distance.

### Stage E — Confidence and abstention

A confidence value should not simply be the similarity score of the top image. It should consider:

- The gap between the best and second-best candidates.
- Geometric verification quality.
- Agreement across successive frames.
- Consistency with recent motion and heading.
- Image quality and the distinctiveness of the scene.

Calibrate confidence against held-out test journeys. The system must be able to return “uncertain” rather than confidently report an incorrect zone.

## 5. Step-by-step development plan

Build this in two formal stages: PoC (Proof of Concept) to establish technical feasibility, followed by MVP (Minimum Viable Product) to deliver a usable offline Android application.

Do not begin by building the complete app or training a large custom model. First determine whether the target environment is visually distinguishable enough for reliable zone identification.

### Phase 1 — PoC

Suggested duration: 4–6 weeks, assuming a small team and access to a test environment.

## 01

Define the test environment and ground truth

- Select a representative test area with straight sections, bends, junctions, and feature-poor terrain.
- Divide it into identifiable zones.
- Record GPS coordinates, approximate distance from the source, timestamps, and phone orientation during mapping.
- Define how zone boundaries and ground-truth labels will be established.

Deliverable: A documented test route and zone map.

## 02

Capture a multi-condition visual dataset

- Capture multiple views per zone, including approach and departure views.
- Capture separate sessions at different times and under varying lighting and weather conditions where possible.
- Keep the initial mapping session separate from later test sessions.
- Save images, GPS labels, timestamps, orientation metadata, and route distance.

Deliverable: A locally stored, labeled reference dataset.

## 03

Build the baseline visual matcher

- Extract ORB features from reference images.
- Compare live or test images with the reference dataset.
- Rank candidate images and aggregate their scores by zone.
- Verify top candidates using geometric consistency.
- Measure accuracy on a separate capture session.

Deliverable: A working baseline that reports the top candidate zones and matching scores.

## 04

Evaluate a learned visual descriptor

Compare the ORB baseline with a compact deep-learning-based image embedding model. Test both on different sessions and environmental conditions.

Deliverable: An evidence-based decision on whether learned retrieval improves zone recognition enough to justify its runtime and storage costs.

## 05

Add temporal filtering and evaluate distance/heading

- Combine consecutive visual estimates.
- Use IMU readings to help estimate relative rotation.
- Estimate progress along the mapped route using recognized zones and their reference distances.
- Test how the estimate behaves when GPS is switched off after mapping.

Deliverable: A feasibility report covering zone accuracy, heading error, distance error, ambiguity, latency, and failure cases.

### PoC exit criteria

Do not define success as “it matched some images.” The PoC must demonstrate that the system generalizes to new journeys, rather than simply recognizing nearly identical frames from its capture session.

Measure:

- Top-1 and top-3 zone recognition accuracy.
- False localization rate — confidently identifying the wrong zone.
- Accuracy by terrain type and environmental condition.
- Heading error, in degrees, where ground truth is available.
- Distance-from-source error, in metres.
- Percentage of observations correctly marked uncertain when they cannot be resolved.
- Inference latency and memory consumption on the target Android device.

Set numerical thresholds after the first baseline run, then freeze them for the acceptance test. Avoid selecting thresholds using the same test journeys used to report final performance.

### Phase 2 — MVP

Suggested duration: 6–10 additional weeks after PoC feasibility is demonstrated.

| Activity                          | MVP deliverable                                                                    |
| --------------------------------- | ---------------------------------------------------------------------------------- |
| 1. Android application foundation | Camera preview, session management, sensor access, and permissions                 |
| 2. Local visual map builder       | Create zones, save reference images, and associate map metadata                    |
| 3. On-device retrieval engine     | Fast candidate search over the stored visual map                                   |
| 4. Localization engine            | Candidate verification, temporal filtering, zone estimate, and uncertainty         |
| 5. Heading and distance estimator | Map-relative orientation and approximate source-relative progress                  |
| 6. Offline user interface         | Current zone, confidence, heading, distance estimate, and alternative candidates   |
| 7. Evaluation and diagnostics     | Session logs, GPS-assisted validation, ground-truth comparisons, and error reports |
| 8. Device optimization            | Measure sustained latency, memory, battery usage, and local storage growth         |

The MVP should support two distinct workflows:

- Mapping/capture workflow: GPS may be used to annotate the reference map. GPS is not required to run the localization engine afterward.
- Localization workflow: The app uses the local map and live observations, with runtime GPS disabled. If GPS is enabled for validation, its data must be isolated from the algorithm being evaluated.

That separation is essential. Otherwise, you may believe visual localization is working when the result is actually being supplied by GPS.

## 6. Recommended technology stack

The following is my recommended starting stack, not a requirement imposed by the original concept.

[Sweep - AI for JetBrains IDEs](https://images.openai.com/static-rsc-4/fEXMJbRIKf0pi2BgCohFpL1rR8ZUIfC-7gVUDMJIFTBD5GnDsBM6KHv80WlFPuG2IJrpx_W1_U7hV7JA8TZjMOEfCbCqvzyEEL-PKEEsbuSbgwXx59l2lSs1siP8zmO5IaXwZVZUYzCXhUO4aNNoa9WF7qZFmU1WLrZ-AKUMq8o?purpose=inline)

Android application layer

Recommended

- Kotlin
- Jetpack Compose for the user interface
- CameraX for camera access and frame analysis
- Android SensorManager for gyroscope, accelerometer, and optional magnetometer
- Android location APIs for mapping and validation only

CameraX provides a frame-analysis interface suitable for computer vision and ML inference. Its latest-frame backpressure strategy is useful when the application cannot process every frame in real time.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.com\&sz=32)

Android Developers



[Open Source Initiative :osi: (@osi@opensource.org) - OSI Mastodon](https://images.openai.com/static-rsc-4/fxbAqZD5n5r2OBCd4nPHCiC6wbv3lpJ97epHKcaq6WLVDdG2RwhPhm3RDDJFQZtRUAjvcKEjE2uENyHdZqm3FQKIyYIOjKI8SeTzrUyYnydrkO5AqrtCdqw32Vjmry3HQhHTTdCSbmNl8lq4Kbx6Mgj0sK1TokBFoOM9JsgCUuE?purpose=inline)

Computer vision

- OpenCV for image preprocessing, ORB features, descriptor matching, and geometric verification.
- Native C++ through the Android NDK only if profiling shows that it is necessary; start with the simplest integration that meets performance targets.

[See Online Workshop: Build an ML app for Android with TFLite at Google Developer Groups GDG Chapel Hill](https://images.openai.com/static-rsc-4/gDwh26-v5OSBQG-FOZu-MOPrzUE5HQngl5Em8Rd5l6DsckYHA2pt5f3f8Cco5BHuOdM_nqZvG57J6KKiSvDo1LDGU-vRoDiEX68X1ZOIHXM5gTYStIIhxfSgN0u_f7QIPC72HVfmxULQdssvmTZ2g08fFHJtM1h01gYBlBIxHuo?purpose=inline)

Deep-learning inference

- TensorFlow Lite (TFLite) for compact image-embedding models.
- Start with a suitable pretrained mobile vision backbone and a feature-vector output.
- Evaluate a place-recognition-specific model such as NetVLAD if the baseline is insufficient.

TFLite supports on-device inference with options for hardware acceleration. Actual performance depends on the model, device, and runtime configuration.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub

+1



[▷ Best SQLite Course Online](https://images.openai.com/static-rsc-4/JskisXVPtbHvr9eyITeToJGKT2k8O37y_IhEKqM8XMxlIw2iSIp8W_ZaDOsNwpabAdTQCQrsv0d-fqRBSrB4KhB_FU-4h-NuSg4ooJW4EaL4MJMgARdpIXF4XA3hoJQNUJcmbU_ZnF80mwXV7O0WbYNNnaA7tnNvtIKRzgeTEiY?purpose=inline)

Offline data storage

- Room/SQLite for zone metadata, reference-image records, timestamps, sensor metadata, route distances, and descriptors.
- App-private files for the actual images.
- Versioned local map packages for export, backup, and import if needed.

### Should we use a vector database?

Not in the first PoC.

A large vector database is not necessary just to demonstrate visual zone recognition. Store image embeddings in SQLite or a compact local binary index and perform cosine-similarity or Euclidean-distance ranking. A straightforward scan is often sufficient for a small dataset.

Add an approximate nearest-neighbor index only when measured search latency or map size justifies the extra complexity. A vector index accelerates candidate retrieval; it does not, by itself, make the localization estimate more accurate.

## 7. Algorithms and libraries

Use a staged algorithm strategy rather than committing to one model before testing.

| Function                 | PoC baseline                                                                 | MVP direction                                                             |
| ------------------------ | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Image preprocessing      | OpenCV resize, grayscale, normalization and quality checks                   | Same, with device-specific optimization                                   |
| Visual place recognition | ORB descriptors and Hamming-distance matching                                | Learned global image embeddings plus local geometric verification         |
| Candidate ranking        | Aggregate image matches by zone                                              | Embedding similarity plus zone-level score aggregation                    |
| Geometric verification   | Feature correspondences, RANSAC, suitable homography/essential-matrix checks | Same, with calibrated camera geometry where available                     |
| Temporal consistency     | Sliding window or simple score smoothing                                     | HMM for discrete zones; EKF if estimating continuous state                |
| Relative heading         | IMU rotation and reference-view comparison                                   | Calibrated camera/IMU fusion and map-relative orientation                 |
| Source-relative distance | Reference zone distance and estimated zone progress                          | Route-progress estimator; optional visual odometry as supporting evidence |
| Model execution          | Optional; compare against classical baseline                                 | TFLite with a compact embedding model                                     |

Visual place recognition methods such as NetVLAD were developed specifically to retrieve images representing previously visited places. They are a relevant candidate family for your cross-condition recognition problem, but they still need to be tested on your own terrain and camera data.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://openaccess.thecvf.com\&sz=32)

CVPR Open Access

+1



### Important algorithm selection rule

- ORB alone: Good for a lightweight baseline and geometric verification, but may struggle with large appearance changes, weak texture, and repetitive scenery.
- Learned global descriptors: Better candidates for recognizing a place despite changes in appearance, but can still confuse similar locations.
- Visual odometry: Estimates motion between frames; useful for short-term progress, but monocular scale and accumulated drift must be handled.
- IMU fusion: Helps with orientation and motion continuity; does not independently provide reliable long-term position.
- Temporal filtering: Reduces frame-to-frame instability, but cannot fix fundamentally indistinguishable zones without additional evidence.

The recommended hybrid is therefore:

Learned or classical candidate retrieval → local feature/geometric verification → temporal filtering → zone/pose estimate with uncertainty.

## 8. Data model for the offline visual map

Keep the data model simple and versioned. A practical starting schema is:

| Record               | Core fields                                                                                             |
| -------------------- | ------------------------------------------------------------------------------------------------------- |
| `Map`                | `map_id`, name, source coordinates, creation date, version                                              |
| `Zone`               | `zone_id`, map ID, zone boundaries/extent, approximate route distance                                   |
| `ReferenceImage`     | image ID, zone ID, file path, capture session, timestamp, quality score                                 |
| `VisualDescriptor`   | image ID, descriptor type, model version, vector or binary descriptor                                   |
| `SensorObservation`  | timestamp, gyro, accelerometer, optional magnetometer, optional GNSS                                    |
| `ZoneRelation`       | zone ID, neighboring zone IDs, expected sequence if known                                               |
| `LocalizationResult` | timestamp, best zone, alternatives, heading estimate, distance estimate, confidence, uncertainty status |

A route-based zone graph is useful if your target environment is a known network of roads or tracks. If the environment is not route-constrained, the map should instead represent spatial regions and relationships. That decision depends on whether the application must recognize only the captured road network or any previously mapped area.

## 9. How to validate the system without fooling ourselves

Your GPS-assisted capture is a strength, because it provides a way to establish approximate ground truth. It must not leak into the runtime visual estimate.

Test 1 — Same-condition baseline

Capture a reference session and evaluate on a separate pass under similar conditions. This establishes basic feasibility.

Test 2 — Appearance variation

Test different daylight, weather, shadows, vegetation, and seasonal conditions where possible.

Test 3 — Visually similar zones

Evaluate different road segments that look alike. Track false-positive matches, not just overall accuracy.

Test 4 — GPS-off runtime

Disable GPS access for the localization module and confirm that all location estimates come from the visual map and permitted onboard sensors.

Test 5 — Distance and heading error

Compare estimated route progress and heading with independent reference measurements. Report median, 95th-percentile, and worst-case errors.

## 10. Main risks and how to address them

| Risk                                  | Mitigation                                                                                                            |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Different zones look almost identical | Store multiple views, add geometric checks, and return ambiguity when candidates cannot be separated                  |
| Day/night or seasonal changes         | Capture across conditions and evaluate learned descriptors against a held-out dataset                                 |
| Few visual features                   | Use temporal evidence, sensor motion cues, and uncertainty reporting; accept that some scenes may be unobservable     |
| Incorrect heading                     | Calibrate camera-to-phone alignment and distinguish map-relative from geographic heading                              |
| Distance drift                        | Use zone-level route distance as a prior; test and quantify interpolation error; avoid claiming exact metric position |
| Dataset grows large                   | Store compact descriptors and add an index only when performance measurements justify it                              |
| Overconfident wrong answers           | Calibrate confidence and set rejection thresholds on independent test data                                            |
| Runtime accidentally uses GPS         | Separate mapping/validation data pathways from runtime localization and test with GPS disabled                        |

## 11. Recommended execution roadmap

### Weeks 1–2

Dataset and baseline definition

Define zones, ground truth, capture procedure, evaluation splits, and initial metrics.

### Weeks 3–4

Classical visual PoC

Implement ORB matching, candidate ranking, geometric checks, and evaluation reports.

### Weeks 5–6

Learned-descriptor comparison and feasibility decision

Compare retrieval performance, runtime cost, and failure cases. Decide whether to proceed to the MVP.

### Weeks 7–10

Android localization MVP

Integrate the chosen retrieval pipeline, local map storage, temporal filtering, heading/distance estimates, and a minimal results UI.

### Weeks 11–16

Field validation and hardening

Run GPS-off trials, quantify errors, optimize performance, and address failure cases. This is a planning estimate, not a guaranteed schedule.

## 12. Final recommendation

Start with a dataset-driven visual localization PoC, not a navigation app, a full SLAM system, or a vector database.

The first milestone should be an Android or desktop evaluation pipeline that answers three questions:

1. Can the system reliably recognize the correct zone from a new camera observation?
2. Can it distinguish visually similar zones across different capture sessions and environmental conditions?
3. Can it estimate useful heading and source-relative progress without runtime GPS, with measured and clearly reported uncertainty?

If the answer to the first two questions is weak, invest in better reference-data coverage and retrieval algorithms before adding complex pose estimation. If zone identification is strong, add temporal filtering and progressively refine heading and distance.

The key limitation to keep explicit is that visual localization can identify a known place without GPS, but a camera cannot guarantee a unique position in every visually repetitive or featureless environment. The product should report the best-supported estimate and its uncertainty rather than force a precise answer in every situation.