# Simply Journal

A client-side waypoint journal for Minecraft Java. Fabric and NeoForge builds cover 1.21 through 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, and 26.3. Use Java 21 for 1.21 versions and Java 25 for 26.x. Fabric builds include their required resource-loading modules.

The journal uses Minecraft's 3D enchantment book model with custom parchment and leather textures. Its controls and text are rendered on the pages.

Put the jar matching your Minecraft version and mod loader in your Minecraft instance's `mods` folder. Servers do not need the mod.

Press semicolon to open the journal. The binding can be changed in Minecraft's Controls menu.

- Click **New** on a blank page to save your current block position and dimension there. Saving keeps you on that spread.
- Click a waypoint's title or description to edit it. Changes save automatically.
- Click an X, Y, or Z value to edit it. **Copy** copies that number, and **Reset** restores its position when the waypoint was created.
- Each coordinate starts enabled. Uncheck an axis to ignore it for direction, distance, and arrival. For example, disabling Z lets you arrive at any Z position when X and Y match.
- Click **Go!** to close the book and track that waypoint. A flat, two-line white arrow floats in the world with distance text below it. The camera turns toward it briefly, then returns full control to you.
- Click **Stop** on the tracked page to end navigation.
- Within two blocks of the destination, the label changes to **Arrived** for three seconds. The arrow and label then fade out over two seconds and tracking stops.
- Use the bottom page arrows or drag right to left for the next spread, and left to right for the previous spread. Page speed is capped, and completed turns play a page sound.
- Blank pages can be browsed too. Each page holds one waypoint; deleting it leaves that page blank.
- Click **Delete**, then **Delete?** within three seconds, to remove a waypoint. The confirmation expires after that.
- Death locations are added automatically, including their dimension and the local time of death.
- Press Escape to close the book.

Waypoints are stored locally in `.minecraft/config/waypoint-journal/`, separately for each singleplayer save or server address. Tracking pauses when the destination is in another dimension. The arrow gives a direction, not a route around terrain.

The first launch shows a short guide. Both Okay and Alr open the main menu.

Release checks passed without extra mods and with Sodium 0.9.3, Lithium 0.26.4, and Xaero's Minimap 26.6.2 together. Xaero 26.6.2 shows a temporary red X for the active destination. Other Xaero versions skip this integration; the journal and its arrow still work. This does not prevent another mod from failing on its own.

## Development

With Java 25 on your path:

```powershell
./gradlew.bat build
./gradlew.bat runClient
```

The main Fabric 26.3 jar is written to `build/libs/`.

Run `python build-release.py` with Java 25 and Python 3 to build the version matrix into `release/1.0/`. Pass versions to build a subset, for example `python build-release.py 1.21.1 26.3`. The version ports live in `ports/versions/`. Loader packaging is in `ports/multiloader.gradle`.

Forge 26.x builds are compiled against Minecraft's unobfuscated API and are placed in `unverified-forge/`. They have not been launched because.. well I wasn't lazy but kinda forget and I'm lazy now

The config directory, world/server keys, JSON fields, and internal `waypointjournal` mod ID stay stable across these builds. Updating the mod jar in the same game directory preserves waypoints. When moving an instance, copy its saves and `config/waypoint-journal/` too.

 Run the in-game checks with `./gradlew.bat -Pqa runClient`. This creates a separate test world in `run-qa/`, checks editing, navigation, persistence, page controls, and death capture, then saves screenshots in `run-qa/render-checks/`.

All rights reserved.
