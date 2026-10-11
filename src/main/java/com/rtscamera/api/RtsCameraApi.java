package com.rtscamera.api;

import com.rtscamera.client.RtsController;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Public API for other mods. Client-side only: call it from client code (e.g. a client packet handler
 * or client event), never from server logic. Safe to call from any thread; work is moved onto the
 * client thread automatically.
 *
 * <p>Treat RTS Camera as an optional dependency and guard calls with
 * {@code ModList.get().isLoaded("rtscamera")}.
 */
public final class RtsCameraApi
{
    private RtsCameraApi() {}

    /**
     * @return true if the player is currently looking through the RTS camera.
     */
    public static boolean isInRtsView()
    {
        return RtsController.isActive();
    }

    /**
     * Leaves the RTS view and returns the camera to the player. The build tool window stays open.
     * Does nothing if the RTS view isn't active.
     *
     * @param message optional chat message shown to the player (e.g. why they were kicked out), or null for none.
     */
    public static void exitRtsView(@Nullable final Component message)
    {
        exitRtsView(message, false);
    }

    /**
     * Leaves the RTS view and returns the camera to the player.
     * Does nothing if the RTS view isn't active.
     *
     * @param message        optional chat message shown to the player, or null for none.
     * @param closeBuildTool also close the build tool window (the in-progress placement preview is kept).
     */
    public static void exitRtsView(@Nullable final Component message, final boolean closeBuildTool)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread())
        {
            mc.execute(() -> exitRtsView(message, closeBuildTool));
            return;
        }
        if (!RtsController.isActive())
        {
            return;
        }

        final boolean wasInBuildTool = RtsController.isSessionScreen(mc.screen);
        RtsController.exit(message);
        if (closeBuildTool && wasInBuildTool)
        {
            mc.setScreen(null);
        }
    }
}
