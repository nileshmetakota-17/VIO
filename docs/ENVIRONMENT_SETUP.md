# Environment setup

Checked on this machine on 25 October 2026.

| Tool | What was found |
| --- | --- |
| OS | Linux |
| System Java | OpenJDK 11.0.32 at `/usr/lib/jvm`. Too old for Android Gradle Plugin 9. |
| Build JDK | Android Studio JBR 25 at `/opt/android-studio/jbr`. Use this as `JAVA_HOME`. |
| Android SDK | `/home/nilesh/Android/Sdk` (`local.properties` points here) |
| Platform | `platforms/android-37.0` |
| Build-tools | `36.0.0` |
| System image | `system-images/android-37.0/google_apis_playstore/x86_64` |
| AVD | None created |
| `adb` devices | None attached when the project was built |
| Gradle | 9.6.0 via the wrapper |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.2.10 |

## Build

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
cd /path/to/VIO
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

If `JAVA_HOME` stays on Java 11, the Android Gradle Plugin will not run.

AGP 9 ships Kotlin itself. KSP 2.2.10-2.0.2 still adds generated Room sources through the Kotlin source-set DSL, which AGP 9 rejects unless `android.disallowKotlinSourceSets=false` is set in `gradle.properties`. That switch is intentional for Room. Do not remove it unless the KSP plugin learns the Android source-set DSL.

Kotlin on JDK 25 prints that it does not yet have a JVM 25 target and falls back. `app/build.gradle.kts` still requests JVM 11 bytecode.

## If the SDK is missing

Install Android Studio, then SDK Platform 37, Build-Tools, and a system image if you want an emulator. Accept the SDK licenses with `sdkmanager --licenses`. Point `local.properties` at the SDK. `local.properties` is gitignored.

## Device

Enable USB debugging, then `adb devices` should list the phone before you install the APK. The live camera screens need a camera. An emulator image is installed here, but no virtual device was created during this build, and no phone was attached, so those screens were not launched.
