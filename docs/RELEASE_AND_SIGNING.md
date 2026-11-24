# Release and signing

The verified artifact is the debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

It is signed with the local debug keystore that the Android SDK creates outside the repository. That key is not a release key. Do not ship it.

## Release build

`app/build.gradle.kts` keeps the template release optimization for AndroidX and Kotlin packages. There is no release signing config in Gradle, and there must not be a password in the repo.

To sign a release build on a machine you control:

1. Create a keystore outside the project tree. `*.jks`, `*.keystore`, and `keystore.properties` are gitignored. Never commit them.
2. Point the release build type at that store with environment variables or a gitignored properties file.
3. Run `./gradlew :app:assembleRelease`.
4. Record the certificate fingerprint, the backup location of the keystore, and who can restore it. Losing the key means you cannot update the installed app in place.
5. Install the release APK on a device that does not have the debug build's assumptions, with networking disabled, and repeat the GPS-off check in `docs/DEVICE_TESTING.md`.

Debug logs go through `BuildConfig.DEBUG` only where that wrapper is used. The estimator does not log coordinates. Release builds should still be checked so a future log line cannot print a GPS tag or an image path with a location in the name.

Map schema `1` and descriptor `grid-hog-brief-v1` model `1` are the compatibility contract. A later descriptor must bump `DESCRIPTOR_TYPE` or `DESCRIPTOR_MODEL_VERSION` and reject older packages with the existing error path. There is no silent migration.

Rollback is uninstall plus install of the previous APK. Exported zip packages remain readable only while their schema and descriptor type match the installed app.
