# Known limitations

- The estimator has not been tried on a phone or on a real place. Synthetic bars, checkers, and rings are separable in unit tests. Streets, corridors, night, season, and repeated facades are not measured.
- Two zones that look alike can still outvote the truth. The app is supposed to abstain when the gap is small. That behavior is not calibrated, so a wrong `LOCALIZED` result is still possible.
- Homography verification fails or becomes meaningless when the scene has little texture, a repeated pattern, large parallax, or people and vehicles covering the view.
- Heading is not compass north. A missing or unstable homography leaves heading blank on purpose. Magnetic heading is shown only as a raw sensor value.
- Distance can be no better than the route label you stored, plus the zone length as a range. It drifts in the sense that the wrong reference image picks the wrong label. It is not visual-odometry drift, because visual odometry is not implemented.
- Monocular scale is not estimated. Do not read the metre figure as a surveyed position.
- The gyroscope yaw on the localization screen is integrated from the moment that screen opens. It is a diagnostic and it drifts.
- GPS on a reference image cannot correct a live estimate, and it also cannot be removed from an exported package except by deleting the reference. Import keeps those tags as metadata.
- The demo map will not localize your room. An unfamiliar scene should stay `UNCERTAIN` or `LOST`. If it does not, the thresholds are wrong for that scene and the result is not trustworthy.
- There is no learned place-recognition model and no OpenCV ORB. Adding either without a separate-session comparison would only add a dependency.
- Frame rate is intentionally low. Latency, memory, battery, and thermal behavior are unmeasured.
- Automatic backup is off. Uninstalling the app deletes maps that were not exported.
- Release signing was not performed. The debug APK is the build that was produced.
