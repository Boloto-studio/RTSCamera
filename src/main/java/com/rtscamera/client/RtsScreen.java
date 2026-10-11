package com.rtscamera.client;

import com.minecolonies.api.colony.IColonyView;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * Overlay screen shown while in RTS mode. Frees the mouse for point-and-click placement and
 * forwards input to {@link RtsController}. The world keeps rendering behind it.
 */
public class RtsScreen extends Screen
{
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL_COLOR = 0x90000000;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int HINT_COLOR = 0xAAAAAA;
    private static final float DRAG_ROTATE_SENSITIVITY = 0.4F;

    private static final List<Component> HELP = List.of(
      Component.translatable("rtscamera.help.move"),
      Component.translatable("rtscamera.help.rotate_camera"),
      Component.translatable("rtscamera.help.zoom"),
      Component.translatable("rtscamera.help.place"),
      Component.translatable("rtscamera.help.rotate_building"),
      Component.translatable("rtscamera.help.height"),
      Component.translatable("rtscamera.help.done"));

    /** Mouse button currently dragging, or -1. */
    private int dragButton = -1;

    public RtsScreen()
    {
        super(Component.translatable("rtscamera.screen.title"));
    }

    @Override
    protected void init()
    {
        final Button[] buttons = {
          Button.builder(Component.translatable("rtscamera.gui.rotate_left"), b -> rotateBuilding(Rotation.COUNTERCLOCKWISE_90)).build(),
          Button.builder(Component.translatable("rtscamera.gui.rotate_right"), b -> rotateBuilding(Rotation.CLOCKWISE_90)).build(),
          Button.builder(Component.translatable("rtscamera.gui.mirror"), b -> mirrorBuilding()).build(),
          Button.builder(Component.translatable("rtscamera.gui.raise"), b -> moveBuilding(1)).build(),
          Button.builder(Component.translatable("rtscamera.gui.lower"), b -> moveBuilding(-1)).build(),
          Button.builder(Component.translatable("rtscamera.gui.done"), b -> finish()).build()
        };

        final int gap = 4;
        final int buttonWidth = Math.min(80, (width - 20 - gap * (buttons.length - 1)) / buttons.length);
        final int totalWidth = buttons.length * buttonWidth + (buttons.length - 1) * gap;
        int x = (width - totalWidth) / 2;
        final int y = height - BUTTON_HEIGHT - 8;
        for (final Button button : buttons)
        {
            button.setX(x);
            button.setY(y);
            button.setWidth(buttonWidth);
            addRenderableWidget(button);
            x += buttonWidth + gap;
        }
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        // Intentionally no background: the world must stay visible.
        renderHelp(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHelp(final GuiGraphics graphics)
    {
        final IColonyView colony = RtsController.colony();
        final Component title = colony == null
                                  ? getTitle()
                                  : Component.translatable("rtscamera.screen.title_colony", colony.getName());

        final int lineHeight = font.lineHeight + 2;
        int panelWidth = font.width(title);
        for (final Component line : HELP)
        {
            panelWidth = Math.max(panelWidth, font.width(line));
        }
        final int panelHeight = lineHeight * (HELP.size() + 1) + 6;
        graphics.fill(4, 4, 4 + panelWidth + 8, 4 + panelHeight, PANEL_COLOR);

        int y = 8;
        graphics.drawString(font, title, 8, y, TEXT_COLOR);
        for (final Component line : HELP)
        {
            y += lineHeight;
            graphics.drawString(font, line, 8, y, HINT_COLOR);
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        dragButton = button;
        switch (button)
        {
            case InputConstants.MOUSE_BUTTON_LEFT -> RtsController.placePreviewAtCursor(mouseX, mouseY, width, height);
            case InputConstants.MOUSE_BUTTON_RIGHT -> rotateBuilding(Rotation.CLOCKWISE_90);
            default -> {}
        }
        return true;
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        if (button != dragButton)
        {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        switch (button)
        {
            case InputConstants.MOUSE_BUTTON_LEFT -> RtsController.placePreviewAtCursor(mouseX, mouseY, width, height);
            case InputConstants.MOUSE_BUTTON_MIDDLE -> RtsController.rotateBy((float) dragX * DRAG_ROTATE_SENSITIVITY, (float) dragY * DRAG_ROTATE_SENSITIVITY);
            default -> {}
        }
        return true;
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        if (button == dragButton)
        {
            dragButton = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        RtsController.zoom(delta);
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers)
    {
        switch (keyCode)
        {
            // No modifier keys here: Shift/Space/Ctrl move the camera, so combos would make it drift
            case InputConstants.KEY_R ->
            {
                rotateBuilding(Rotation.CLOCKWISE_90);
                return true;
            }
            case InputConstants.KEY_F ->
            {
                rotateBuilding(Rotation.COUNTERCLOCKWISE_90);
                return true;
            }
            case InputConstants.KEY_M ->
            {
                mirrorBuilding();
                return true;
            }
            case InputConstants.KEY_PAGEUP ->
            {
                moveBuilding(1);
                return true;
            }
            case InputConstants.KEY_PAGEDOWN ->
            {
                moveBuilding(-1);
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER ->
            {
                finish();
                return true;
            }
            default ->
            {
                // ESC is handled by super and routed to onClose()
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void rotateBuilding(final Rotation rotation)
    {
        final BuildToolBridge buildTool = RtsController.buildTool();
        if (buildTool != null)
        {
            buildTool.rotate(rotation);
        }
    }

    private void mirrorBuilding()
    {
        final BuildToolBridge buildTool = RtsController.buildTool();
        if (buildTool != null)
        {
            buildTool.mirror();
        }
    }

    private void moveBuilding(final int dy)
    {
        final BuildToolBridge buildTool = RtsController.buildTool();
        if (buildTool != null)
        {
            buildTool.move(new BlockPos(0, dy, 0));
        }
    }

    private void finish()
    {
        RtsController.exit(true, null);
    }

    @Override
    public void onClose()
    {
        finish();
    }

    @Override
    public void removed()
    {
        RtsController.onScreenRemoved(this);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
