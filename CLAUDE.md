# RTS Camera — Minecraft mod

Client-side Forge mod that adds an RTS-style top-down camera, as an addon for **MineColonies** (and later **Recruits**).

The user does not write Minecraft mods; Claude does all the implementation.
**At the end of every task, build the jar (`./build.sh`) and tell the user the path to the jar**:
`build/libs/rtscamera-<mc>-<version>.jar`. They drop it into their Minecraft `mods` folder.

## Target environment
- Minecraft **1.20.1**, **Forge 47.x**, Java 17, official Mojang mappings, ForgeGradle 6 (Gradle 8.8 wrapper).
- User's mod versions (from their crash report): Forge 47.3.0, MineColonies `1.20.1-1.1.835-snapshot`, Structurize `1.20.1-1.0.765-snapshot`, BlockUI `1.20.1-1.0.156-RELEASE`, Recruits `1.20.1-1.14.2.3`. Large modpack (Sinytra Connector, Embeddium + Oculus shaders, Valkyrien Skies, Epic Fight, Distant Horizons, ...); plays on a dedicated multiplayer server.
- BlockUI gotcha: attach tooltips (`PaneBuilders.*Tooltip`) only after `addChild`, or it throws "Hover pane does not have parent window specified".
- MineColonies 1.1.835 was built against Structurize `1.20.1-1.0.765-snapshot`, so we compile against that.

## Building
- `./build.sh`: runs `./gradlew build` with `JAVA_HOME=~/.local/jdk17`. There is no system Java. A portable Temurin 17 was unpacked to `~/.local/jdk17`; if it's missing, re-download it from api.adoptium.net.
- Output: `build/libs/rtscamera-1.20.1-<mod_version>.jar` (reobfuscated, ready for a normal Forge install).
- Bump `mod_version` in `gradle.properties` for releases.
- Nobody has run the mod in-game from this machine (no runClient test). Verification is compile-only unless the user reports back.

## Layout
- `libs/`: **compile-only** reference jars, never bundled. Resolved through a `flatDir` repo + `fg.deobf` in `build.gradle`.
  - `minecolonies-1.20.1-1.1.835-snapshot.jar`, `recruits-1.20.1-1.14.2.3.jar` (provided by user)
  - `structurize-1.20.1-1.0.765-snapshot.jar`, `blockui-1.20.1-1.0.156-RELEASE.jar` (from `https://ldtteam.jfrog.io/ldtteam/modding/com/ldtteam/<artifact>/`)
  - The flatDir naming is `blank:<name>-1.20.1:<version>` → `libs/<name>-1.20.1-<version>.jar`.
- `src/main/java/com/rtscamera/`
  - `RTSCamera.java`: `@Mod` entry; registers DisplayTest so servers don't need the mod.
  - `client/ClientEvents.java`: Forge event hooks. Injects the **"RTS View"** BlockUI button into the build tool window (`ScreenEvent.Init.Post` on `BOScreen`), ticks the controller, hides HUD and hand, forces FOV.
  - `client/RtsController.java`: session state. Enter/exit, per-tick WASD/zoom/rotate movement, collision plus colony-border clamping, cursor ray-picking, raid/damage auto-exit.
  - `client/RtsCameraEntity.java`: client-only `Entity` (uses `EntityType.MARKER`, never added to the level, so no registration is needed) used as `Minecraft#setCameraEntity`. Moves via `Entity.collideBoundingBox` (block collision without side effects).
  - `client/RtsScreen.java`: transparent non-pausing `Screen` for input (frees mouse) plus help panel and buttons.
  - `client/BuildToolBridge.java`: wraps Structurize's `AbstractBlueprintManipulationWindow` and its `BlueprintPreviewData` (via `RenderingCache`). Reads private `bluePrintId` / `groundOffset` and calls `updateRotationState` reflectively.
  - `client/ColonyRules.java`: MineColonies checks (in colony, `Action.PLACE_HUTS` permission, `IColonyView#isRaiding`).
- `src/main/resources/assets/rtscamera/lang/en_us.json`: all user-facing strings.

## How the MineColonies feature works
1. Player opens the Structurize build tool (used by MineColonies for huts) and selects a building.
2. The "RTS View" button (bottom-right of the 420x240 window) calls `RtsController.tryEnter(window)`.
   - Denied unless the player stands in a colony where they have `PLACE_HUTS`, no raid is active, and a blueprint is selected.
3. The camera entity spawns above and behind the preview at 55° pitch, with yaw snapped to 45°. The build tool window is swapped for `RtsScreen` and the window instance is kept.
4. Every move is applied per axis: block collision first, then the position is reverted if it leaves the colony's claimed chunks (`IColonyView#isCoordInColony`; unloaded chunks count as outside) or goes beyond the build height plus 16.
5. Left click/drag sets the preview position from a camera ray (same convention as `ItemBuildTool.useOn`: the clicked face's neighbour, plus the preview's ground offset). Targets outside the colony are ignored.
6. Enter/Esc/Done → restore the camera and call `window.open()` on the original build tool window, where the player confirms placement as usual.
7. Auto-exit without reopening when a raid starts, the player takes damage or dies, the dimension changes, or another screen replaces ours.

## Reference sources
- MineColonies: https://github.com/ldtteam/minecolonies (branch `version/main` = 1.20.1; commit `779286e99` matches 1.1.835)
- Structurize: https://github.com/ldtteam/Structurize (branch `version/main` = 1.20.1; commit `239d8b87` matches 1.0.765)
  - Key files: `client/gui/AbstractBlueprintManipulationWindow.java`, `WindowExtendedBuildTool.java`, `storage/rendering/RenderingCache.java`, `storage/rendering/types/BlueprintPreviewData.java`, `items/ItemBuildTool.java`
- Recruits: https://github.com/talhanation/recruits
- Use `~/.local/jdk17/bin/javap -cp libs/<jar>` to inspect APIs. The mod jars use SRG names (`m_12345_`) for vanilla members.

## Roadmap
- [x] MineColonies: RTS placement from the build tool
- [ ] Recruits integration (next feature: likely commanding units from the RTS camera)

## Conventions
- Brace-on-new-line Java style (matches LDTTeam code), `final` params/locals, Javadoc on classes and non-obvious methods.
- Keep the mod client-only unless a feature truly needs server logic. If it does, add a network channel and drop `IGNORESERVERONLY`.
- Put any new user-facing text in `en_us.json`.
- RTS controls: never use Shift/Ctrl/Space combos for actions. Those keys move the camera (user reported Shift+R making the camera drift). Building rotation is R (clockwise) / F (counter-clockwise).
