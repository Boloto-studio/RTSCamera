package com.rtscamera.client;

import com.ldtteam.blockui.BOScreen;
import com.minecolonies.api.colony.IColonyView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * RTS overlay drawn on top of the build tool window: a slim status bar in the strip above the window,
 * and an optional controls card (toggled with H) in the middle of the screen.
 */
public final class RtsHud
{
    private static final int BG = 0xC0101010;
    private static final int BORDER = 0x40FFFFFF;
    private static final int TITLE = 0xFFD27F;
    private static final int KEY = 0xFFE08A;
    private static final int TEXT = 0xE0E0E0;
    private static final int MUTED = 0x9A9A9A;
    private static final int PAD = 6;
    private static final int COLUMN_GAP = 12;

    /** Control rows: translation key prefix; each has ".key" and ".action". */
    private static final List<String> CONTROLS = List.of(
      "move", "vertical", "fast", "rotate_camera", "zoom",
      "place", "rotate_building", "mirror", "nudge", "height",
      "exit", "toggle");

    private static boolean showControls = false;

    private RtsHud() {}

    public static void toggleControls()
    {
        showControls = !showControls;
    }

    public static void render(final BOScreen screen, final GuiGraphics graphics)
    {
        final Minecraft mc = Minecraft.getInstance();
        // BlockUI screens overwrite Screen#width/height with their own scale; use the GUI-scaled window size
        final int width = mc.getWindow().getGuiScaledWidth();
        final int height = mc.getWindow().getGuiScaledHeight();
        renderStatusBar(screen, graphics, mc.font, width);
        if (showControls)
        {
            renderControls(graphics, mc.font, width, height);
        }
    }

    private static void renderStatusBar(final BOScreen screen, final GuiGraphics graphics, final Font font, final int screenWidth)
    {
        final IColonyView colony = RtsController.colony();
        final Component title = colony == null
                                  ? Component.translatable("rtscamera.hud.title")
                                  : Component.translatable("rtscamera.hud.title_colony", colony.getName());
        final Component hint = Component.translatable(showControls ? "rtscamera.hud.hide" : "rtscamera.hud.show");
        final String separator = "  ·  ";

        final int width = font.width(title) + font.width(separator) + font.width(hint) + PAD * 2;
        final int height = font.lineHeight + 6;
        final int x = (screenWidth - width) / 2;
        // Centre the bar in the empty strip above the window when there is room
        final int y = Math.max(2, ((int) BuildToolBridge.windowTop(screen) - height) / 2);

        panel(graphics, x, y, width, height);
        int textX = x + PAD;
        final int textY = y + 4;
        graphics.drawString(font, title, textX, textY, TITLE);
        textX += font.width(title);
        graphics.drawString(font, separator, textX, textY, MUTED);
        textX += font.width(separator);
        graphics.drawString(font, hint, textX, textY, MUTED);
    }

    private static void renderControls(final GuiGraphics graphics, final Font font, final int screenWidth, final int screenHeight)
    {
        final Component heading = Component.translatable("rtscamera.hud.controls");
        int keyWidth = 0;
        int actionWidth = font.width(heading);
        for (final String id : CONTROLS)
        {
            keyWidth = Math.max(keyWidth, font.width(Component.translatable("rtscamera.control." + id + ".key")));
            actionWidth = Math.max(actionWidth, font.width(Component.translatable("rtscamera.control." + id + ".action")));
        }

        final int rowHeight = font.lineHeight + 3;
        final int width = PAD * 2 + keyWidth + COLUMN_GAP + actionWidth;
        final int height = PAD * 2 + rowHeight * (CONTROLS.size() + 1) + 4;
        final int x = (screenWidth - width) / 2;
        final int y = (screenHeight - height) / 2;

        panel(graphics, x, y, width, height);
        graphics.drawString(font, heading.copy().withStyle(ChatFormatting.BOLD), x + PAD, y + PAD, TITLE);
        int rowY = y + PAD + rowHeight + 4;
        graphics.fill(x + PAD, rowY - 4, x + width - PAD, rowY - 3, BORDER);
        for (final String id : CONTROLS)
        {
            final Component key = Component.translatable("rtscamera.control." + id + ".key");
            graphics.drawString(font, key, x + PAD + keyWidth - font.width(key), rowY, KEY);
            graphics.drawString(font, Component.translatable("rtscamera.control." + id + ".action"), x + PAD + keyWidth + COLUMN_GAP, rowY, TEXT);
            rowY += rowHeight;
        }
    }

    private static void panel(final GuiGraphics graphics, final int x, final int y, final int width, final int height)
    {
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x, y, x + width, y + 1, BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
        graphics.fill(x, y + 1, x + 1, y + height - 1, BORDER);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, BORDER);
    }
}
