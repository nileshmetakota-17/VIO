# Architecture

VIO is one Android application module, `com.example.vio`. Packages keep the camera, the map database, and the estimator apart so mapping-time GPS cannot be passed in as a live position.

## Packages

| Package | Role |
| --- | --- |
| `core.model` | Map, zone, observation, and localization result types |
| `core.vision` | Grayscale buffer, quality checks, descriptors, homography |
| `core.localization` | Retrieval, temporal votes, confidence, heading, distance |
| `core.map` | Validation, zip packages, zone links, synthetic demo |
| `core.storage` | Room database, image files, import and export |
| `core.camera` | CameraX preview and rate-limited frame analysis |
| `core.sensors` | IMU snapshot, and GPS only for the mapping screen |
| `feature.home` | Map list, create, import, demo |
| `feature.capture` | Zone capture |
| `feature.localize` | Live estimate |
| `feature.diagnostics` | Validation, leave-one-out, export, delete |

## Runtime path

1. CameraX `ImageAnalysis` keeps only the latest frame and closes every `ImageProxy`.
2. Frames are sampled to at most about 480 px on the long edge, rotated upright, and then considered about once every 700 ms.
3. `ImageQuality` rejects dark, blown-out, and nearly featureless frames.
4. A usable frame becomes a `grid-hog-brief-v1` descriptor: a coarse gradient histogram for retrieval, plus oriented BRIEF keypoints.
5. `ZoneRetriever` scores zones from their reference images. Several images in one zone become one zone score.
6. `GeometricVerifier` matches keypoints and runs RANSAC homography. Homography assumes a dominant plane or a distant scene. Strong parallax can fail this check, which lowers confidence instead of inventing a pose.
7. `TemporalFilter` keeps a short vote window and down-weights a jump to a zone that is not a route neighbor.
8. The state is `INITIALIZING`, `LOCALIZED`, `UNCERTAIN`, or `LOST`. Heading and distance are withheld until the state is `LOCALIZED` or `UNCERTAIN`.

The localization request type has image features, quality, and optional IMU fields. It has no latitude, longitude, or GPS fix. `LocalizationReference` is the same: route distance and descriptors only. Mapping coordinates stay on `ReferenceObservation` and are written by the capture screen.

## Storage

Room database `vio-maps.db` holds map, zone, observation, descriptor, route-link, and estimate-log rows. JPEG files live under the app files directory `maps/<mapId>/images/`. Descriptor blobs are not stored as image blobs. Deleting a map removes the rows and that directory. Backup is disabled so the image set is not included in automatic device backup.

Estimate logs store state, zone label, and the uncalibrated score. They do not store coordinates.

## What is deliberately absent

No navigation graph, no SLAM, no cloud inference, no vector database, no six-degree-of-freedom pose, and no learned embedding model. Those wait on a measured need. The retrieval type is isolated in `FeatureExtractor`, so a later OpenCV ORB or LiteRT embedder can replace it without changing the screens.
