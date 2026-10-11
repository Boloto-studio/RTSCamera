package com.rtscamera.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingView;
import com.ldtteam.blockui.views.View;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import com.ldtteam.structurize.client.gui.WindowShapeTool;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Access to the Structurize build tool window and its blueprint preview.
 * Structurize keeps a few needed members private, so they are read reflectively with safe fallbacks.
 */
public final class BuildToolBridge
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Preview id used by {@code WindowExtendedBuildTool}. */
    private static final String DEFAULT_PREVIEW_ID = "blueprint";

    private static final Field SCREEN_X = accessible(BOScreen.class, "x");
    private static final Field SCREEN_Y = accessible(BOScreen.class, "y");
    private static final Field SCREEN_MC_SCALE = accessible(BOScreen.class, "mcScale");
    private static final Field SCREEN_RENDER_SCALE = accessible(BOScreen.class, "renderScale");
    private static final Field VIEW_PADDING = accessible(View.class, "padding");

    private final AbstractBlueprintManipulationWindow window;
    private final String previewId;

    public BuildToolBridge(final AbstractBlueprintManipulationWindow window)
    {
        this.window = window;
        this.previewId = readPreviewId(window);
    }

    /** Whether the RTS button should be offered on this window (building placement, not the shape tool). */
    public static boolean supports(final Object window)
    {
        return window instanceof AbstractBlueprintManipulationWindow && !(window instanceof WindowShapeTool);
    }

    public AbstractBlueprintManipulationWindow window()
    {
        return window;
    }

    public BlueprintPreviewData preview()
    {
        return RenderingCache.getOrCreateBlueprintPreviewData(previewId);
    }

    public boolean hasBlueprint()
    {
        return preview().getBlueprint() != null;
    }

    /**
     * Places the blueprint as if the build tool had been used on {@code groundPos} (the air block above the clicked face),
     * but horizontally centred on that block instead of putting the anchor (hut block) there.
     */
    public void placeCenteredAt(final BlockPos groundPos)
    {
        final BlueprintPreviewData data = preview();
        final Blueprint blueprint = data.getBlueprint();
        final BlockPos anchor = groundPos.above(readGroundOffset(data));
        if (blueprint == null)
        {
            data.setPos(anchor);
            return;
        }
        final BlockPos offset = blueprint.getPrimaryBlockOffset();
        data.setPos(new BlockPos(anchor.getX() + offset.getX() - blueprint.getSizeX() / 2,
          anchor.getY(),
          anchor.getZ() + offset.getZ() - blueprint.getSizeZ() / 2));
    }

    public void move(final BlockPos offset)
    {
        preview().move(offset);
    }

    /** Rotates around the building's centre (Structurize rotates around the anchor, which makes it jump). */
    public void rotate(final Rotation rotation)
    {
        transformKeepingCenter(data -> data.rotate(rotation));
    }

    public void mirror()
    {
        transformKeepingCenter(BlueprintPreviewData::mirror);
    }

    private void transformKeepingCenter(final Consumer<BlueprintPreviewData> transform)
    {
        final BlueprintPreviewData data = preview();
        final BlockPos before = horizontalCenter(data);
        transform.accept(data);
        final BlockPos after = horizontalCenter(data);
        if (before != null && after != null)
        {
            data.move(new BlockPos(before.getX() - after.getX(), 0, before.getZ() - after.getZ()));
        }
        refreshWindow();
    }

    @Nullable
    private static BlockPos horizontalCenter(final BlueprintPreviewData data)
    {
        final Blueprint blueprint = data.getBlueprint();
        final BlockPos pos = data.getPos();
        if (blueprint == null || pos == null)
        {
            return null;
        }
        final BlockPos offset = blueprint.getPrimaryBlockOffset();
        return new BlockPos(pos.getX() - offset.getX() + blueprint.getSizeX() / 2, 0, pos.getZ() - offset.getZ() + blueprint.getSizeZ() / 2);
    }

    /** Top edge of the window in GUI-scaled screen coordinates, or 0 if unknown. */
    public static double windowTop(final BOScreen screen)
    {
        try
        {
            return SCREEN_Y.getDouble(screen) / SCREEN_MC_SCALE.getDouble(screen);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return 0;
        }
    }

    /** Hides a pane of the window by id, if present (e.g. Structurize's placement tip). */
    public void hidePane(final String id)
    {
        final Pane pane = window.findPaneByID(id);
        if (pane != null && pane.isVisible())
        {
            pane.hide();
        }
    }

    /**
     * Whether a GUI-scaled mouse position is over an interactive part of a BlockUI window
     * (buttons, lists, ...). Plain labels don't count, so the world behind them stays clickable.
     */
    public static boolean isOverUi(final BOScreen screen, final double mouseX, final double mouseY)
    {
        try
        {
            final double mcScale = SCREEN_MC_SCALE.getDouble(screen);
            final double renderScale = SCREEN_RENDER_SCALE.getDouble(screen);
            final double windowX = (mouseX * mcScale - SCREEN_X.getDouble(screen)) / renderScale;
            final double windowY = (mouseY * mcScale - SCREEN_Y.getDouble(screen)) / renderScale;
            return hitsChild(screen.getWindow(), windowX, windowY);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            LOGGER.debug("UI hit test failed, treating cursor as over UI", e);
            return true;
        }
    }

    /** Mirrors BlockUI's click dispatch: children are tested in coordinates relative to their parent. */
    private static boolean hitsChild(final View view, final double mx, final double my) throws IllegalAccessException
    {
        final double localX = mx - view.getX() - VIEW_PADDING.getInt(view);
        final double localY = my - view.getY() - VIEW_PADDING.getInt(view);
        for (final Pane child : view.getChildren())
        {
            if (hits(child, localX, localY))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hits(final Pane pane, final double mx, final double my) throws IllegalAccessException
    {
        if (!pane.isVisible() || !pane.isPointInPane(mx, my))
        {
            return false;
        }
        if (pane instanceof ScrollingView)
        {
            return true;
        }
        if (pane instanceof final View view)
        {
            return hitsChild(view, mx, my);
        }
        return !(pane instanceof Text);
    }


    /** Refreshes the rotation/mirror indicators of the window after changes made while it was closed. */
    public void refreshWindow()
    {
        try
        {
            final Method m = AbstractBlueprintManipulationWindow.class.getDeclaredMethod("updateRotationState");
            m.setAccessible(true);
            m.invoke(window);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            LOGGER.debug("Could not refresh build tool rotation state", e);
        }
    }

    private static String readPreviewId(final AbstractBlueprintManipulationWindow window)
    {
        try
        {
            final Field f = AbstractBlueprintManipulationWindow.class.getDeclaredField("bluePrintId");
            f.setAccessible(true);
            return (String) f.get(window);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            LOGGER.warn("Could not read build tool preview id, assuming '{}'", DEFAULT_PREVIEW_ID, e);
            return DEFAULT_PREVIEW_ID;
        }
    }

    @Nullable
    private static Field accessible(final Class<?> owner, final String name)
    {
        try
        {
            final Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            LOGGER.warn("Could not access {}.{}", owner.getSimpleName(), name, e);
            return null;
        }
    }

    private static int readGroundOffset(final BlueprintPreviewData data)
    {
        try
        {
            final Field f = BlueprintPreviewData.class.getDeclaredField("groundOffset");
            f.setAccessible(true);
            return f.getInt(data);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            LOGGER.debug("Could not read preview ground offset", e);
            return 0;
        }
    }
}
