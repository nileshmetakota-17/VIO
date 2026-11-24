<<<<<<< HEAD
# VIO

VIO is an offline Android app that estimates where the phone is inside a place you have already mapped. It compares the live camera view with reference images stored on the device.

A result shows the zone, an uncalibrated score, other candidate zones, a heading relative to the matched reference view, and an approximate distance along the mapped route. Weak evidence stays `UNCERTAIN` or `LOST`.

Mapping can store an optional GPS tag on a reference photo. The localization screen uses the camera, motion sensors, and the on-device map.

> **Project status:** Experimental Android prototype. Real-world localization accuracy, performance, and battery use have not been measured on a phone. Despite the project name, this version implements visual place recognition, not visual-inertial odometry or full SLAM.

## Where VIO can be used

VIO is intended for experiments in recognizing a previously mapped place, especially where a GPS position alone does not identify a room, corridor, or route segment. Example evaluation settings include:

- **Offices and campuses:** identify a mapped corridor, entrance, or zone from its appearance.
- **Warehouses and industrial buildings:** test recognition of visually distinct sections along a fixed route.
- **Museums and exhibition spaces:** experiment with identifying a mapped room or display area.
- **Repeated walking routes:** compare the live view with references and estimate progress from the route's starting point.
- **Education and research:** explore reference-image mapping, visual matching, geometric verification, and localization uncertainty on Android.

These are potential evaluation settings, not validated deployments. The app reports a zone and an approximate route distance; it does not provide turn-by-turn navigation, obstacle detection, a surveyed position, or continuous 3D tracking. Indoor mapping can use manually entered labels and distances when GPS is unavailable.

## Install the APK

An APK is the Android installation file. The local testing APK is named **VIO.apk** and is stored at `app/VIO.apk`. After changing the source, rebuild and refresh this file using the commands below before sharing it.

- **Device:** Android 7.0 (API 24) or newer, with a working camera. A physical phone is recommended for mapping and sensor testing.
- **For GitHub downloads:** open this repository's **Releases** section and download `VIO.apk` if a release with that attachment has been published. If there is no APK attachment, build from source below.
- **For a local copy:** transfer `app/VIO.apk` to your phone using USB or another file-transfer method.

### Install on your phone

1. Open the APK using the phone's file manager or download notification.
2. If Android asks, allow **Install unknown apps** for the browser or file manager opening it. The exact wording depends on the phone.
3. Tap **Install**, then open **VIO**.
4. Allow camera access to capture references and localize. Mapping also requests location access for GPS tagging and automatic route labels; localization itself does not request location permission.
5. Create a map and capture reference views before trying to localize. Follow **Use the app** below.

You can turn off the install permission afterward. Install only an APK from a source you trust. If Android reports a signing conflict with an existing installation, export your maps before uninstalling the old app: uninstalling deletes its local maps and photos.

### Install with ADB

With Android SDK Platform Tools installed and USB debugging enabled on the phone:

```bash
adb devices
adb install -r app/VIO.apk
```

### Publish an APK on GitHub

To share an installable build, create a GitHub Release with a version tag and attach the APK as a release asset named `VIO.apk`. APK files can stay excluded from Git commits; release attachments are uploaded separately. Describe whether the attachment is a debug/testing build or a signed release build, and identify the source commit used to build it.

For testing, build the debug APK below. For public distribution, configure release signing as described in [Release and signing](docs/RELEASE_AND_SIGNING.md). The current project has no release signing configuration.

## Prerequisites

- Android Studio with JDK 17 or newer. The system Java 11 is too old for this Android Gradle Plugin. On this machine, set `JAVA_HOME` to Android Studio's JBR.
- Android SDK platform 37 and build-tools. Set your SDK location in Android Studio or your local `local.properties`; do not commit that file.
- A phone or emulator with a camera. Unit tests do not need one.

More setup detail is in [Environment setup](docs/ENVIRONMENT_SETUP.md).

## Run the app

From the project root:

The paths below are Linux examples. Adjust them to your Android Studio and SDK installation.

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk app/VIO.apk
```

Gradle produces `app/build/outputs/apk/debug/app-debug.apk`. The copy command refreshes `app/VIO.apk`, the file to attach to a GitHub Release as **VIO.apk**. This is a debug build for testing.

Connect a phone with USB debugging on, then install:

```bash
adb devices
adb install -r app/VIO.apk
```

Open **VIO** from the launcher. You can also run the `app` configuration from Android Studio.

Unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## Use the app

On the **Map** screen, a usable new view is saved when the timer, the metres you have walked, or a large turn is reached. Any one enabled rule is enough. The same view is skipped. **Save reference now** stores one immediately. The localization screen reads the camera for the estimate and does not add those frames to the map.

### 1. Create a map

1. Tap **New map**.
2. Enter a name and a source label, such as `Gate`.
3. Latitude and longitude are optional. Enter both, or leave both blank. They are stored on the map record.
4. Tap **Create**.

**Demo map** installs synthetic patterns so the screens can be opened without a real walk. That map does not recognize a room.

### 2. Save reference views

1. On the map card, tap **Map**. Allow the camera and location. Location is requested as soon as this screen opens.
2. Leave **Type the label and distance** off. The zone name and the metres from the start are calculated from GPS. **Zone size** sets how wide each automatic zone is. **By time**, **By distance moved**, and **By a large turn** each have a number. A photo is saved when any enabled rule is reached.
3. Stand where the route starts and open **Map**. The first GPS fix on an empty map is 0 m. Later photos are measured from that point. **Set source here** moves 0 m to where you are standing now.
4. Point the phone at the place and walk. When the text says **Frame is usable**, photos are stored on their own. Each saved row shows the photo, the zone, the distance, and the GPS coordinates. Tap the photo to open it. These images stay inside the app. They are not copied to the phone gallery.
5. Turn on **Type the label and distance** when you want to name a zone and type the metres yourself. Then add a zone, select it, and set **Distance from source**.
6. **Save reference now** stores the current frame without waiting. **Auto-save** can be switched off.
7. **Remove** deletes one reference. **Delete all references** clears every photo in this map and leaves the zones.

Indoors the first GPS fix can take a while. Automatic labeling waits for that fix. Save views from more than one angle in each zone. The app links zones by their source distance.

**Delete all** on the home screen removes every map on the phone, including photos and GPS tags. The counts on each card refresh when you return to the home screen.

### 3. Localize

1. On the map card, tap **Localize**.
2. Point the camera at the mapped place. The status updates on its own:
   - **INITIALIZING** — a leading guess, waiting for another agreeing frame.
   - **LOCALIZED** — the accepted estimate.
   - **UNCERTAIN** — a leaning guess, with a wider distance range.
   - **LOST** — no zone is accepted.
3. Tap **Reset estimate** if an old zone is sticking after you move to a new area.

This screen does not ask for location permission.

### 4. Check, export, import

On the map card, tap **Check**.

- Validation lists missing images, empty zones, and broken links.
- **Run leave-one-out check** compares stored references with each other. On a real map this is a sanity check. It is not an outdoor accuracy trial.
- **Export map package** writes a zip (`manifest.json`, `images/<id>.jpg`, `descriptors/<id>.bin`). Schema version is `1`. Descriptor type is `grid-hog-brief-v1`.
- **Import** on the home screen loads that zip. An incompatible package, or a map id that is already stored, is rejected.
- **Delete map** removes that map, its images, and its descriptors from the device.
- **Delete all** on the home screen removes every map.

## What the numbers mean

- **Heading** is degrees clockwise of the matched reference view when the geometry check succeeds. The magnetic reading, when shown, is a raw sensor value and is separate from that heading.
- **Distance** is the source distance you stored on the matched reference, shown as a range from the zone length.

Read `docs/KNOWN_LIMITATIONS.md` before treating a result as a surveyed position. Field error has not been measured on a phone.

## Offline use and stored data

Reference matching runs on the device with a locally stored map. A GitHub account or internet connection is not needed to use localization after installation and mapping. Automatic labels during mapping depend on a GPS fix; use manual labels and distances when a fix is unavailable.

Reference photos stay in app storage rather than the phone gallery. Exported map packages include reference images and any saved GPS tags, so review their contents before sharing. Export maps you want to keep before uninstalling; automatic backup is disabled.

## Limitations and testing

- Map the place first; the app cannot recognize an unmapped location.
- Similar-looking zones, blank walls, poor lighting, occlusions, and viewpoint changes can cause weak or incorrect matches.
- The displayed score is not a calibrated probability. Even a `LOCALIZED` result can be wrong.
- Heading is relative to the matched reference view, and distance comes from stored route labels. Neither is a surveyed measurement.
- The demo map contains synthetic patterns and cannot recognize your surroundings.

See [Known limitations](docs/KNOWN_LIMITATIONS.md), [Device testing](docs/DEVICE_TESTING.md), and [Evaluation](docs/EVALUATION.md) for current constraints and validation guidance.

## Map package

Export and import use the zip described above. Paths outside `manifest.json`, `images/<id>.jpg`, and `descriptors/<id>.bin` are refused.
=======
# VIO
>>>>>>> 0e3acfd13a120f57b4281098c1873515a647e754
