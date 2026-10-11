package com.rtscamera.client;

import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.client.ModKeyMappings;
import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import com.mojang.blaze3d.platform.InputConstants;
import com.rtscamera.RTSCamera;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;

/**
 * Forge hooks. RTS mode runs underneath the open build tool window: clicks and keys that the window
 * doesn't need are intercepted here and turned into camera/placement actions.
 */
@Mod.EventBusSubscriber(modid = RTSCamera.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents
{
    private static final String BUTTON_ID = RTSCamera.MOD_ID + "_toggle";
    private static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation("structurize", "textures/gui/buildtool/button_medium.png");

    /** Placement in the build tool's 420x240 layout: bottom-right, next to the confirm button. */
    private static final int BUTTON_X = 334;
    private static final int BUTTON_Y = 218;
    private static final int BUTTON_WIDTH = 86;
    private static final int BUTTON_HEIGHT = 17;

    private static final float DRAG_ROTATE_SENSITIVITY = 0.4F;
    /** Mouse button we're currently handling a drag for, or -1. */
    private static int dragButton = -1;

    private ClientEvents() {}

    /*
     * ---------------- Build tool button ----------------
     */

    /**
     * Adds the RTS toggle button to the Structurize/MineColonies building placement window.
     */
    @SubscribeEvent
    public static void onScreenInit(final ScreenEvent.Init.Post event)
    {
        if (!(event.getScreen() instanceof final BOScreen screen))
        {
            return;
        }
        final BOWindow window = screen.getWindow();
        if (!BuildToolBridge.supports(window) || window.findPaneByID(BUTTON_ID) != null)
        {
            return;
        }

        final ButtonImage button = new ButtonImage();
        button.setID(BUTTON_ID);
        button.setPosition(BUTTON_X, BUTTON_Y);
        button.setSize(BUTTON_WIDTH, BUTTON_HEIGHT);
        button.setImage(BUTTON_TEXTURE, false);
        button.setText(buttonLabel());
        button.setTextColor(ChatFormatting.BLACK.getColor());
        button.setTextAlignment(Alignment.MIDDLE);
        button.setTextRenderBox(BUTTON_WIDTH, BUTTON_HEIGHT);
        button.setHandler(b -> {
            if (RtsController.isActive())
            {
                RtsController.exit(null);
            }
            else
            {
                RtsController.tryEnter((AbstractBlueprintManipulationWindow) window);
            }
            button.setText(buttonLabel());
        });
        // Must be added before attaching the tooltip: BlockUI requires the hover pane to already belong to a window
        window.addChild(button);
        PaneBuilders.singleLineTooltip(Component.translatable("rtscamera.gui.button.tooltip"), button);
    }

    private static Component buttonLabel()
    {
        return Component.translatable(RtsController.isActive() ? "rtscamera.gui.button.exit" : "rtscamera.gui.button");
    }

    /** Keeps the button label in sync when RTS mode ends on its own, and declutters the window while in RTS. */
    @SubscribeEvent
    public static void onScreenRender(final ScreenEvent.Render.Pre event)
    {
        if (RtsController.isSessionScreen(event.getScreen()))
        {
            // Structurize's "use the arrow buttons / right click to re-enter" tip doesn't apply in RTS
            Objects.requireNonNull(RtsController.buildTool()).hidePane("tip");
        }
        if (event.getScreen() instanceof final BOScreen screen
              && screen.getWindow().findPaneByID(BUTTON_ID) instanceof final ButtonImage button)
        {
            final Component label = buttonLabel();
            if (!Objects.equals(button.getText(), label))
            {
                button.setText(label);
            }
        }
    }

    /*
     * ---------------- RTS input (only while the build tool window of the session is open) ----------------
     */

    @SubscribeEvent
    public static void onMousePressed(final ScreenEvent.MouseButtonPressed.Pre event)
    {
        final Screen screen = event.getScreen();
        if (!RtsController.isSessionScreen(screen)
              || BuildToolBridge.isOverUi((BOScreen) screen, event.getMouseX(), event.getMouseY()))
        {
            return;
        }

        dragButton = event.getButton();
        switch (event.getButton())
        {
            case InputConstants.MOUSE_BUTTON_LEFT -> RtsController.placePreviewAtCursor(event.getMouseX(), event.getMouseY());
            case InputConstants.MOUSE_BUTTON_RIGHT -> rotateBuilding(Rotation.CLOCKWISE_90);
            default -> {}
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseDragged(final ScreenEvent.MouseDragged.Pre event)
    {
        final Screen screen = event.getScreen();
        if (!RtsController.isSessionScreen(screen) || event.getMouseButton() != dragButton)
        {
            return;
        }
        switch (dragButton)
        {
            case InputConstants.MOUSE_BUTTON_LEFT -> RtsController.placePreviewAtCursor(event.getMouseX(), event.getMouseY());
            case InputConstants.MOUSE_BUTTON_MIDDLE ->
              RtsController.rotateBy((float) event.getDragX() * DRAG_ROTATE_SENSITIVITY, (float) event.getDragY() * DRAG_ROTATE_SENSITIVITY);
            default -> {}
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseReleased(final ScreenEvent.MouseButtonReleased.Pre event)
    {
        if (event.getButton() == dragButton)
        {
            dragButton = -1;
            if (RtsController.isSessionScreen(event.getScreen()))
            {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(final ScreenEvent.MouseScrolled.Pre event)
    {
        final Screen screen = event.getScreen();
        if (RtsController.isSessionScreen(screen) && !BuildToolBridge.isOverUi((BOScreen) screen, event.getMouseX(), event.getMouseY()))
        {
            RtsController.zoom(event.getScrollDelta());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(final ScreenEvent.KeyPressed.Pre event)
    {
        if (!RtsController.isSessionScreen(event.getScreen()) || BOWindow.getFocus() != null)
        {
            return;
        }

        final InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        final Direction facing = RtsController.cameraFacing();
        // No modifier-key combos of our own: Shift/Space/Ctrl move the camera, so combos would make it drift
        final boolean handled = switch (event.getKeyCode())
        {
            case InputConstants.KEY_R -> rotateBuilding(Rotation.CLOCKWISE_90);
            case InputConstants.KEY_F -> rotateBuilding(Rotation.COUNTERCLOCKWISE_90);
            case InputConstants.KEY_PAGEUP -> moveBuilding(Direction.UP);
            case InputConstants.KEY_PAGEDOWN -> moveBuilding(Direction.DOWN);
            case InputConstants.KEY_H ->
            {
                RtsHud.toggleControls();
                yield true;
            }
            default ->
            {
                // Structurize's nudge keys move relative to the player; make them follow the RTS camera instead
                if (ModKeyMappings.MOVE_FORWARD.get().isActiveAndMatches(key)) yield moveBuilding(facing);
                if (ModKeyMappings.MOVE_BACK.get().isActiveAndMatches(key)) yield moveBuilding(facing.getOpposite());
                if (ModKeyMappings.MOVE_LEFT.get().isActiveAndMatches(key)) yield moveBuilding(facing.getCounterClockWise());
                if (ModKeyMappings.MOVE_RIGHT.get().isActiveAndMatches(key)) yield moveBuilding(facing.getClockWise());
                // Mirror around the building's centre rather than Structurize's anchor
                if (ModKeyMappings.MIRROR.get().isActiveAndMatches(key)) yield mirrorBuilding();
                yield false;
            }
        };
        if (handled)
        {
            event.setCanceled(true);
        }
    }

    private static boolean rotateBuilding(final Rotation rotation)
    {
        final BuildToolBridge buildTool = RtsController.buildTool();
        if (buildTool != null)
        {
            buildTool.rotate(rotation);
        }
        return true;
    }

    private static boolean mirrorBuilding()
    {
        final BuildToolBridge buildTool = RtsController.buildTool();
        if (buildTool != null)
        {
            buildTool.mirror();
        }
        return true;
    }

    private static boolean moveBuilding(final Direction direction)
    {
        final BuildToolBridge buildTool = RtsController.buildTool();
        if (buildTool != null)
        {
            buildTool.move(BlockPos.ZERO.relative(direction));
        }
        return true;
    }

    /*
     * ---------------- Overlay ----------------
     */

    @SubscribeEvent
    public static void onScreenRenderPost(final ScreenEvent.Render.Post event)
    {
        if (RtsController.isSessionScreen(event.getScreen()))
        {
            RtsHud.render((BOScreen) event.getScreen(), event.getGuiGraphics());
        }
    }

    /*
     * ---------------- Camera / HUD ----------------
     */

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            RtsController.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderHand(final RenderHandEvent event)
    {
        if (RtsController.isActive())
        {
            event.setCanceled(true);
        }
    }

    /** Hide the HUD (hotbar, crosshair, ...) but keep chat so restriction messages are visible. */
    @SubscribeEvent
    public static void onRenderOverlay(final RenderGuiOverlayEvent.Pre event)
    {
        if (RtsController.isActive() && !event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(final ViewportEvent.ComputeFov event)
    {
        if (RtsController.isActive() && event.usedConfiguredFov())
        {
            event.setFOV(RtsController.FOV);
        }
    }

    @SubscribeEvent
    public static void onLogout(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        RtsController.reset();
        dragButton = -1;
    }
}
