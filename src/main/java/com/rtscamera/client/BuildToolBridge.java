package com.rtscamera.client;

import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import com.ldtteam.structurize.client.gui.WindowShapeTool;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Access to the Structurize build tool window and its blueprint preview.
 * Structurize keeps a few needed members private, so they are read reflectively with safe fallbacks.
 */
public final class BuildToolBridge
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Preview id used by {@code WindowExtendedBuildTool}. */
    private static final String DEFAULT_PREVIEW_ID = "blueprint";

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
     * Places the blueprint as if the build tool had been used on {@code groundPos} (the air block above the clicked face).
     */
    public void placeAt(final BlockPos groundPos)
    {
        final BlueprintPreviewData data = preview();
        data.setPos(groundPos.above(readGroundOffset(data)));
    }

    public void move(final BlockPos offset)
    {
        preview().move(offset);
    }

    public void rotate(final Rotation rotation)
    {
        preview().rotate(rotation);
    }

    public void mirror()
    {
        preview().mirror();
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
