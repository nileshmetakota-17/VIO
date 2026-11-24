# Algorithm decisions

The first estimator is a classical baseline that runs in Kotlin and is covered by JVM unit tests. OpenCV and a learned embedding are not in this build.

## Why not OpenCV ORB yet

The spec's classical baseline is ORB plus RANSAC. This tree has no NDK installed, and an OpenCV native package would be a large unverified dependency before any field images exist. The same pipeline shape is implemented without that dependency:

- Retrieval descriptor: 6 by 4 cells, 8 unsigned gradient bins, L1-normalized and quantized. Shift and brightness changes move this less than a raw pixel match. Type name: `grid-hog-brief-v1`.
- Local features: grid interest points with gradient magnitude, an intensity-centroid angle, and a 256-bit oriented BRIEF pattern.
- Verification: mutual nearest neighbors under a Hamming cutoff, then up to 80 RANSAC samples of a normalized 4-point homography. A match is consistent at 8 or more inliers and an inlier ratio of at least 0.25, with a 4 px tolerance in the 160 px-wide working image.
- Homography is the wrong model for strong parallax, repetitive texture, and scenes with too few interest points. Those cases return a weak geometric score. They do not fall through to a guessed pose.

`FeatureExtractor` is the seam for a later OpenCV ORB or a LiteRT global embedding. Neither is selected, because there is no held-out field set to compare them on.

## Temporal model

The filter keeps five votes. A zone needs two agreeing frames and 60% of the window before it is stable. A jump to a zone that is not linked along the route is down-weighted, not forbidden, so a real discontinuity can still appear after several frames. One contradicting frame does not replace a stable zone. IMU yaw is integrated only as a diagnostic from the time the localization screen opened. It is not a position and it does not vote for a zone.

An extended Kalman filter is not implemented. There is no continuous motion model or measurement model to justify one.

## Heading and distance

Heading, when shown, is the in-plane rotation of the accepted homography: degrees clockwise of the matched reference view. It is omitted when the linear part of the homography is a poor rotation. Magnetic heading from the rotation vector is displayed separately and is labeled as unused. It is unreliable near metal.

Distance is the source distance stored on the matched reference image. The range is at least half the zone length, and it widens again while the state is `UNCERTAIN`. `INITIALIZING` and `LOST` withhold the number. Nothing here treats monocular similarity as metres.

## Confidence

The score mixes the best zone similarity, the gap to the next zone, the inlier ratio, temporal agreement, and frame quality. It is not a probability. `LOCALIZED` also requires a stable window, a minimum score, and either geometric consistency or a large appearance gap. The first frame stays `INITIALIZING`. A bad frame cannot be `LOCALIZED`.

These cutoffs were chosen so the synthetic suite can separate its own patterns and refuse a different pattern. They were not fit on a field survey. Do not treat them as accepted operating thresholds.
