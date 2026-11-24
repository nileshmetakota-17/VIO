# Phase status

The project documents describe a gated sequence that stops after each phase. The request in this session was to build the application from those documents, so the repository now contains one vertical slice through that design rather than a Phase 0 shell only. Anything not listed as run was not run.

| Phase | State | Evidence |
| --- | --- | --- |
| 0 Environment and project | Done for this machine | JDK, SDK, and Gradle recorded in `docs/ENVIRONMENT_SETUP.md`. `./gradlew :app:testDebugUnitTest` compiles the app. No emulator was booted and no device was attached, so "launches on a device" is not met. |
| 1 Camera, permissions, sensors | Implemented, not device-tested | CameraX preview, 700 ms analysis, `ImageProxy` closed on every path, permission denial UI, IMU snapshot, location permission only on the mapping switch. Needs the procedure in `docs/DEVICE_TESTING.md`. |
| 2 Map format | Implemented and unit-tested | Room schema, files, validation, zip import/export, path rejection, schema rejection. Round-trip test passes. A full database instrumentation test was not run. |
| 3 Classical retrieval | Implemented and unit-tested | `grid-hog-brief-v1`. Synthetic leave-one-out top-1 is required by the test. Not OpenCV ORB. |
| 4 Geometric verification | Implemented and unit-tested | RANSAC homography. Translation test passes. Failure modes are documented. Not compared with a Phase 3 number on field images, because there is no field set. |
| 5 Learned descriptors | Not started | No model bundled, no accuracy claim. |
| 6 Temporal, heading, distance | Implemented and unit-tested | Vote window, route-neighbor weight, view-relative heading, route distance. Synthetic second frame of zone A localizes at the stored 45 m label. No field error in degrees or metres. |
| 7 Offline UI | Implemented, not device-tested | Home, mapping, localization, diagnostics. No internet permission in the manifest. Runtime types used by the estimator have no GPS fields. |
| 8 Field performance | Not started | No device numbers. |
| 9 Release | Not signed | Debug APK only. See `docs/RELEASE_AND_SIGNING.md`. |

## Next action

Install `app/build/outputs/apk/debug/app-debug.apk` on a phone with a camera, run `docs/DEVICE_TESTING.md`, and only then collect a second-session walk before changing the estimator.
