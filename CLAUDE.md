# RTS Camera — Minecraft mod

Client-side Forge mod that adds an RTS-style top-down camera, as an addon for **MineColonies** (and later **Recruits**).

The user does not write Minecraft mods; Claude does all the implementation.
**At the end of every task, build the jar (`./build.sh`) and tell the user the path to the jar**:
`build/libs/rtscamera-<mc>-<version>.jar`. They drop it into their Minecraft `mods` folder.

## Target environment
- Minecraft **1.20.1**, **Forge 47.x**, Java 17, official Mojang mappings, ForgeGradle 6 (Gradle 8.8 wrapper).
- User's mod versions (from their crash report): Forge 47.3.0, MineColonies `1.20.1-1.1.835-snapshot`, Structurize `1.20.1-1.0.765-snapshot`, BlockUI `1.20.1-1.0.156-RELEASE`, Recruits `1.20.1-1.14.2.3`. Large modpack (Sinytra Connector, Embeddium + Oculus shaders, Valkyrien Skies, Epic Fight, Distant Horizons, ...); plays on a dedicated multiplayer server.
- BlockUI gotcha: `BOScreen` overwrites `Screen#width/height` with its own scale, so they do NOT match the GUI-scaled mouse coordinates in ScreenEvents. Use `mc.getWindow().getGuiScaledWidth/Height()` for ray-picking and HUD layout (this caused the "building way off to the side of the cursor" bug in 0.2.0).
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
  - `api/RtsCameraApi.java`: **public API for other mods** (`isInRtsView`, `exitRtsView(Component[, closeBuildTool])`). Client-only, thread-safe. Keep its signatures stable; usage docs for other mods are in `API.md`.
  - `RTSCamera.java`: `@Mod` entry; registers DisplayTest so servers don't need the mod.
  - `client/ClientEvents.java`: Forge event hooks. Injects the **"RTS View" / "Exit RTS"** toggle button into the build tool window (`ScreenEvent.Init.Post` on `BOScreen`). While RTS is active, intercepts mouse and keys on that window (`ScreenEvent.*.Pre`) when the cursor isn't over UI, draws the help overlay (H toggles it), ticks the controller, hides HUD and hand, forces FOV.
  - `client/RtsController.java`: session state. Enter/exit, per-tick WASD/zoom/rotate movement (raw GLFW key polling, because a screen is open), collision plus colony-border clamping, cursor ray-picking, raid/damage auto-exit.
  - `client/RtsCameraEntity.java`: client-only `Entity` (uses `EntityType.MARKER`, never added to the level, so no registration is needed) used as `Minecraft#setCameraEntity`. Moves via `Entity.collideBoundingBox` (block collision without side effects).
  - `client/BuildToolBridge.java`: wraps Structurize's `AbstractBlueprintManipulationWindow` and its `BlueprintPreviewData` (via `RenderingCache`). Reads private `bluePrintId` / `groundOffset` and calls `updateRotationState` reflectively. `isOverUi` repeats BlockUI's click hit-test: GUI coords → window coords via `BOScreen` `x/y/mcScale/renderScale`, then children relative to parent minus `padding`. `Text` labels don't count as UI; `ScrollingView` does.
  - `client/RtsHud.java`: overlay on the build tool window. Slim status bar centred in the strip above the window; a two-column controls card in the screen centre, toggled with H (hidden by default). Structurize's "tip" label is hidden while in RTS.
  - `client/ColonyRules.java`: MineColonies checks (in colony, `Action.PLACE_HUTS` permission, `IColonyView#isRaiding`).
- `src/main/resources/assets/rtscamera/lang/en_us.json`: all user-facing strings.

## How the MineColonies feature works
RTS mode is a camera mode that runs **underneath the open build tool window**. The player selects buildings, rotates and confirms in the normal Structurize UI without leaving the top-down view.
1. Player opens the Structurize build tool (used by MineColonies for huts).
2. The "RTS View" button (bottom-right of the 420x240 window) calls `RtsController.tryEnter(window)`.
   - Denied unless the player stands in a colony where they have `PLACE_HUTS` and no raid is active. A blueprint does not need to be selected yet.
3. The camera entity spawns above and behind the preview (or the player) at 55° pitch, with yaw snapped to 45°. The build tool window stays open.
4. Every move is applied per axis: block collision first, then the position is reverted if it leaves the colony's claimed chunks (`IColonyView#isCoordInColony`; unloaded chunks count as outside) or goes beyond the build height plus 16. Camera keys are ignored while a BlockUI text field has focus.
5. Clicks and scrolls not over UI go to the world. Left click/drag sets the preview position from a camera ray, **centred on the building's footprint** (anchor = target + primaryBlockOffset - size/2; ground offset applied as in `ItemBuildTool.useOn`). Rotate and mirror keep the footprint centre fixed (Structurize pivots around the anchor); targets outside the colony are ignored. Right click rotates. Middle-drag rotates the camera. Scroll zooms.
6. Keys: R/F rotate, Page Up/Down raise/lower. Structurize's arrow-key nudge bindings are taken over so they move the building relative to the camera. M (mirror) and Enter (place) stay with Structurize.
7. RTS stays on while any `BOScreen` is open; it rebinds to a new build tool window, e.g. after switching style packs. It exits (camera back to the player) when the screen closes (Esc, cancel, survival placement confirm), on the "Exit RTS" button, or on raid, damage, death or dimension change.

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
- RTS controls: never use Shift/Ctrl/Space combos for actions. Those keys move the camera (user reported Shift+R making the camera drift). Building rotation is R (clockwise) / F (counter-clockwise). Structurize's own keys in the build tool window: arrows (nudge), Shift+Left/Right (rotate), keypad +/- (up/down), M (mirror), Enter (place). Don't reuse them.
