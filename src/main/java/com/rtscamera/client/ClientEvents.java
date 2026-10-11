package com.rtscamera.client;

import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import com.rtscamera.RTSCamera;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

@Mod.EventBusSubscriber(modid = RTSCamera.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents
{
    private static final String BUTTON_ID = RTSCamera.MOD_ID + "_enter";
    private static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation("structurize", "textures/gui/buildtool/button_medium.png");

    /** Placement in the build tool's 420x240 layout: bottom-right, next to the confirm button. */
    private static final int BUTTON_X = 334;
    private static final int BUTTON_Y = 218;
    private static final int BUTTON_WIDTH = 86;
    private static final int BUTTON_HEIGHT = 17;

    private ClientEvents() {}

    /**
     * Adds the "RTS View" button to the Structurize/MineColonies building placement window.
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
        button.setText(Component.translatable("rtscamera.gui.button"));
        button.setTextColor(ChatFormatting.BLACK.getColor());
        button.setTextAlignment(Alignment.MIDDLE);
        button.setTextRenderBox(BUTTON_WIDTH, BUTTON_HEIGHT);
        button.setHandler(b -> RtsController.tryEnter((AbstractBlueprintManipulationWindow) window));
        // Must be added before attaching the tooltip: BlockUI requires the hover pane to already belong to a window
        window.addChild(button);
        PaneBuilders.singleLineTooltip(Component.translatable("rtscamera.gui.button.tooltip"), button);
    }

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
    }
}
