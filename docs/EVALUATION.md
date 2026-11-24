# Evaluation

No field thresholds have been agreed, and no outdoor dataset is in the repository. The numbers below are from the JVM suite on synthetic patterns. They answer whether the pipeline distinguishes those patterns. They do not answer whether a road, a corridor, or a junction will localize.

## Split

`MapEvaluator.leaveOneOut` drops one stored descriptor at a time and queries with it. A zone with a single view is skipped, because that view would have no sibling to retrieve. Adjacent video frames are not a split this harness can police. You still have to keep mapping sessions and test sessions apart when you collect real images.

## Synthetic result

Command: `./gradlew :app:testDebugUnitTest`

The demo map has 3 zones and 6 views. On that set the unit test requires:

- retrieval top-1 on all 6 leave-one-out queries
- zero accepted wrong zones
- a repeated view of zone A-01 to reach `LOCALIZED` on the second frame, with distance 45 m, which is the distance label of that reference
- a diagonal pattern, a flat frame, and an empty map not to be `LOCALIZED`
- a change in the magnetic-heading field not to change the zone, the distance, or the score

Those assertions passed in the suite that accompanies this tree. Re-run the Gradle task after any estimator change and treat a failure as a failure. Do not edit the expected zone to make it pass.

The diagnostics screen runs the same leave-one-out function on whatever map is stored and prints the same warning: stored descriptors, not a field trial.

## Metrics still missing

Top-1 and top-3 on a new session, false-localization rate on similar-looking zones, heading error against a defined reference, distance error in metres, fraction withheld, latency and memory on a phone, and battery over a real walk. Record the session split beside any future number. Do not invent the thresholds after seeing the score.
