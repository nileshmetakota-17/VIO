# Device testing

No phone was attached, and no Android Virtual Device existed, when this app was built. The camera, permission, GPS-tag, and sensor screens have not been launched. The JVM tests do not open a camera.

## When a device is available

1. Install the debug APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. Deny camera permission and confirm the preview explains how to grant it, and that no zone is invented.
3. Create a map with two zones. Save two usable views in each. Confirm a dark frame is refused.
4. Turn GPS tagging on and off. Confirm location permission is requested only from that switch. Confirm the localize screen never shows a location prompt.
5. Localize on the mapped walk with the phone in airplane mode, or with location services off. The estimate must still update. That is the GPS-off check.
6. Point the camera at an unrelated scene and confirm the state becomes `UNCERTAIN` or `LOST` rather than a confident wrong zone.
7. Rotate the phone and confirm the preview keeps running and frames are still closed. Leave and reopen the screen.
8. Export the zip, delete the map, import the zip, and run the leave-one-out check.
9. On a second day or a different light, walk again without adding those frames to the map. Write the session split and the errors into `docs/EVALUATION.md`. Do not mix that walk into the reference set first.

The emulator system image `android-37.0/google_apis_playstore/x86_64` is installed. Creating an AVD and booting it was not part of this build. A camera-less emulator cannot exercise localization.

Minimum SDK is 24. Compile and target SDK are 37. There is no measured latency or memory budget on hardware yet. Frame analysis is capped at about one frame each 700 ms so the preview is not waiting on every camera frame.
