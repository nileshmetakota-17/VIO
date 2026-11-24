# Dataset and mapping

A map is a route split into zones. Each zone needs more than one viewpoint if you want the leave-one-out check to include it. Images taken seconds apart from the same spot do not count as a new condition.

## Capture rules

1. Create the map and name the source anchor. Latitude and longitude are optional. If you enter one, enter both. They are stored on the map record only.
2. Add zones in the order you walk, with the distance from the source at the start of the zone and a zone length. The app links zones by that distance, both directions.
3. Select a zone, aim the camera, and save a frame only when the quality check says the frame is usable. Dark, blown-out, and blank frames are refused.
4. Set "Distance at this frame" when the shot is not at the start of the zone. That number is the distance the estimator can later report.
5. Optional route bearing is the direction of the route through the zone, in degrees you choose. It is displayed as stored metadata, not as a live compass fix.
6. Turn on "Tag references with GPS" only while mapping. The permission is requested then. A fix is copied onto that reference. If there is no fix yet, the image is still saved and the screen says so.
7. Remove a bad reference from the list. Deleting the map removes its images.

Keep a later walk as a test session. Do not treat near-duplicate frames from one recording as both the map and the test.

Transition frames that sit between two zones should be labeled as the zone you are willing to accept, or left out. This version has no separate transition class.

## On-disk format

- SQLite database `vio-maps.db` in app storage.
- JPEG files at `files/maps/<mapId>/images/<observationId>.jpg`.
- Descriptors in the `descriptors` table, type `grid-hog-brief-v1`, model version `1`.
- Schema version `1`. A package with another schema or descriptor type is rejected.

Export produces a zip:

```
manifest.json
images/<observationId>.jpg
descriptors/<observationId>.bin
```

Zip entries outside those names are refused. Import fails if the map id is already stored, if an image or descriptor is missing, or if validation reports an error.

## Demo map

"Demo map" builds three synthetic zones (bars, checker, rings), two views each, with a small shift and brightness change on the second view. Mapping coordinates are stored on those references so the diagnostics screen can show that tags exist. The estimator's input type does not include them. The demo is for the pipeline and the screens, not for claiming that a real street was recognized.
