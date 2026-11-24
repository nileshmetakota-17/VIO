# Prompts to replicate VIO

Paste these into a new chat in this order. The first prompt builds the app from the docs. The later prompts are the changes made after the first phone test. Each one is written so it can be pasted on its own once the app already exists.

The screenshot prompt at the end describes the three phone images, so those images do not need to be attached again.

## 1. Build the app

```text
Read this workspace and the docs, especially docs/VizLocId.md and docs/VizIdDevPrompt.md, and create the VIO Android application they describe.

It is an offline visual localization app. A result is a zone, a heading, a distance from the mapped source, and a confidence, with states LOCALIZED, UNCERTAIN, LOST, and INITIALIZING. GPS may be stored as mapping metadata on a reference. Localization must not read GPS and must not use it as a position fix.

Build it as one Android app module in Kotlin with Jetpack Compose, CameraX, and Room. Keep capture, localization, storage, and the screens in the app. Update README.md with how to build, install, map, and localize.
```

## 2. Explain and document use

```text
Explain how the app works, how to use it, and whether capture is automatic.

Then update README.md so it says how to run the app and how to use it: create a map, save reference views, what GPS does during mapping, how to localize, and how to check, export, and import a map.
```

## 3. GPS tags, auto-save, and delete all

This is the refined form of the request that came with three phone screenshots.

What those screenshots showed:

- Home at 7:01. Maps named gate2 and Punjab each say 0 zones and 0 references. Synthetic demo says 3 zones and 6 references. Buttons on each card are Map, Localize, and Check. The bottom row is New map, Demo map, and Import.
- Localize for gate2 a minute earlier. The state is UNCERTAIN, the zone is zone 4, and the uncalibrated score is about 0.45 to 0.50. Heading is Unavailable because geometric verification was too weak. Distance from source is 40 m, with a range of about 0 m to 160 m. The photo is an indoor stair. Magnetic heading 332° is shown only as a sensor reading. Candidates are zone 1, zone 4, and zone 2, each with similarity 0.86, at 10 m, 40 m, and 20 m. Retrieval 0 ms, verify 1 ms, inliers 4/5.
- The home counts stayed at 0 after leaving Map and Localize, so the home list was stale. The localize screen proved the references existed.

```text
The phone screenshots show two problems. GPS tags are not stored on references, and the home screen still says 0 zones and 0 references after mapping. Localization of gate2 already shows zone 4 and candidates, so the data was saved and the home list did not refresh.

Change mapping so that:

- Opening the Map screen asks for location and starts GPS. Show the live fix as latitude, longitude, and accuracy.
- Save a usable reference automatically about every 5 seconds while a zone is selected. Skip a frame that is almost the same as the last saved view. Keep a Save reference now button.
- Store the GPS latitude and longitude on each saved reference. If there is no fix yet, still save the photo and say that there is no GPS fix yet. Show the coordinates, or "no GPS", on each reference in the list.
- Localization must still not read GPS.

Add Delete all on the home screen, with a confirmation dialog, to remove every map, photo, and GPS tag. Add Delete all references on the Map screen, with a confirmation dialog, to remove that map's photos and leave the zones. Refresh the zone and reference counts on the home screen when the user returns to it.

Update README.md so it no longer says that save is manual only and that GPS tagging is off by default.
```

## 4. Make the UI professional and add the icon

```text
Make the UI more professional. Keep the dark field look and the same behavior. Give the home, map, localize, and check screens one layout: grouped panels, quieter labels, and actions in their own rows instead of a crowded line of text buttons.

On home, each map is a card with zone and reference counts, and Map, Localize, and Check sit in one bar. New map is the main button. Demo and Import sit beside it. Delete all maps is a separate red action.

On the map screen, GPS and auto-save are switches in one panel. Zones are selectable rows. Adding a zone and saving a frame are their own sections. Each saved reference shows its distance and GPS, with Remove on that row.

On localize, the zone, state, and score sit in a result card above the camera. Heading and distance stay as the large readings. Candidates are a list. Sensor notes are grouped underneath.

On check, source, validation, and recent estimates are separate panels. Export stays full width. Delete map is the red action at the bottom.

Replace the default Android robot launcher icon with a VIO mark that matches the app: a cream viewfinder and an amber center on the dark background. Use that same mark beside the VIO title on the home screen. Provide the adaptive icon and the density fallbacks.
```

## 5. GPS labels, tunable capture, and seeing the photos

```text
Calculate the zone label and the distance from GPS. The place where mapping starts is the source and is 0 m. Every later distance is metres from that GPS point. On an empty map, the first GPS fix becomes 0 m. Set source here moves 0 m to the current fix. A map that already has photos keeps its original start.

Capture a usable image when any one enabled rule is reached, and make each rule a parameter the user can tune:

- By time, default every 5 seconds.
- By distance moved, default 5 metres of GPS movement since the last saved photo.
- By a large IMU turn, default 45 degrees of heading change.

The user can turn each rule on or off. Auto-save can be turned off. Save reference now stores the current frame immediately. Skip a view that matches the last saved one.

Zone size in metres is also tunable. Automatic zones are named from the GPS distance, such as 0–20 m. Add a manual toggle, Type the label and distance, for when the user wants to name the zone and type the metres.

Show the saved reference images on the Map screen under Saved references: a thumbnail, the zone, the distance, and the GPS coordinates. Tapping the thumbnail opens the photo. The images stay inside the app and are not added to the phone gallery.

Keep localization from reading GPS. The distance stored on a reference is still the mapping distance from the GPS source.
```

## 6. Show the matched reference while localizing

```text
When localizing, show a small image of the original reference photo that the current frame matched. Put it in the result card next to the zone name and label it Matched reference. Tapping it opens the photo larger. Update the thumbnail as the match changes. If the frame is too weak to accept a zone, still show the closest stored photo.
```

## 7. If distance capture does not fire

```text
Confirm that the distance on each photo is calculated from GPS, measured from the source where the map started, which is 0 m.

By distance moved is not saving a new photo when I walk. Fix that. Do not let a coarse network location replace a fresh GPS fix. Count metres from the last saved photo's GPS position, and do not reset that count when the timer or a turn fires. Show how many metres GPS has moved since the last photo, and save when that reaches the Moved, m setting. If the GPS accuracy is worse than the step, say so, because the phone cannot see a shorter walk.
```

## One paste for a full rebuild

Use this only when the docs are already in the workspace and the goal is the finished behavior, not the original draft.

```text
Read this workspace and docs/VizLocId.md and docs/VizIdDevPrompt.md. Build the offline VIO Android app they describe, then apply the product behavior below. Localization must never read GPS.

Mapping:
- Opening Map asks for location and keeps a live GPS fix. Prefer a fresh GPS fix over a coarse network location.
- The start of an empty map is 0 m. The first GPS fix sets that source. Set source here moves 0 m to the current spot. Later distances are metres from that point, and the zone name comes from that distance. Zone size is tunable.
- Type the label and distance is a manual toggle. Off by default.
- Auto-save a usable new view when any enabled rule is reached: every N seconds, after N metres of GPS movement since the last saved photo, or after a heading change of N degrees. Defaults are 5 seconds, 5 metres, and 45 degrees. Each rule can be switched off. Skip a near-duplicate view. Save reference now stores the current frame.
- Show saved photos on the Map screen with zone, distance, and GPS coordinates. Tap a thumbnail to open it. Photos stay in the app, not the gallery.
- Delete all references on the map, and Delete all maps on home, both with confirmation. Home counts refresh when the screen resumes.

Localize:
- Show zone, state, score, heading, and distance from the mapped source.
- Show a small image of the matched reference photo, and open it larger on tap.

UI:
- Dark, quiet, professional layout. Grouped panels and one action bar per map card.
- Launcher icon is a cream viewfinder with an amber center on the dark background, also shown beside the home title.

Update README.md with how to build, install, map, and localize.
```
