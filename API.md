# RTS Camera API

How another Forge 1.20.1 mod can kick the player out of the RTS view and show its own message.

RTS Camera is a **client-side** mod. Its API must be called from client code. If the trigger happens on the server (combat, a quest event, ...), send your own packet to that player and call the API in the client-side packet handler.

## API

Class: `com.rtscamera.api.RtsCameraApi` (all methods static; safe to call from any thread).

| Method | What it does |
|---|---|
| `boolean isInRtsView()` | `true` while the player is looking through the RTS camera. |
| `void exitRtsView(@Nullable Component message)` | Leaves RTS view and returns the camera to the player. The build tool window stays open. `message` is shown in chat (`null` = no message). Does nothing if RTS isn't active. |
| `void exitRtsView(@Nullable Component message, boolean closeBuildTool)` | Same, and closes the build tool window if `closeBuildTool` is `true`. |

## Option A: compile against the jar (recommended)

1. Copy `rtscamera-1.20.1-<version>.jar` into your mod's `libs/` folder.
2. `build.gradle`:
   ```groovy
   repositories {
       flatDir { dir 'libs' }
   }
   dependencies {
       // compile-only: your mod still runs without RTS Camera installed
       compileOnly fg.deobf("blank:rtscamera-1.20.1:<version>")   // -> libs/rtscamera-1.20.1-<version>.jar
   }
   ```
3. `mods.toml`: declare it as optional, so your mod loads without it:
   ```toml
   [[dependencies.yourmodid]]
       modId="rtscamera"
       mandatory=false
       versionRange="[0.3.0,)"
       ordering="NONE"
       side="CLIENT"
   ```
4. Call it, only on the client and only if the mod is loaded. Keep the call in its own class so the JVM never loads RTS Camera classes when the mod is absent:
   ```java
   // RtsCameraCompat.java (only reference RtsCameraApi from this class)
   public final class RtsCameraCompat {
       public static void kickOut(Component reason) {
           com.rtscamera.api.RtsCameraApi.exitRtsView(reason, false);
       }
   }

   // anywhere in your client code
   if (ModList.get().isLoaded("rtscamera")) {
       RtsCameraCompat.kickOut(Component.literal("You are under attack!"));
   }
   ```

## Option B: no compile dependency (reflection)

```java
if (ModList.get().isLoaded("rtscamera")) {
    try {
        Class.forName("com.rtscamera.api.RtsCameraApi")
             .getMethod("exitRtsView", Component.class, boolean.class)
             .invoke(null, Component.literal("You are under attack!"), false);
    } catch (ReflectiveOperationException e) {
        LOGGER.warn("Could not exit RTS view", e);
    }
}
```

## Server-triggered example

```java
// Server: tell the player's client to leave RTS view
YourNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new LeaveRtsPacket("Your village is burning!"));

// Client-side handler of LeaveRtsPacket
ctx.enqueueWork(() -> {
    if (ModList.get().isLoaded("rtscamera")) {
        RtsCameraCompat.kickOut(Component.literal(packet.message()));
    }
});
```
