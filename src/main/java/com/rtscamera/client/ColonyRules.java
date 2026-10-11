package com.rtscamera.client;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.permissions.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * MineColonies restrictions for the RTS camera, evaluated on the client against synced colony views.
 */
public final class ColonyRules
{
    private ColonyRules() {}

    /**
     * Result of checking whether the player may enter RTS mode.
     *
     * @param colony the colony the player is standing in, if allowed.
     * @param error  the reason entry was denied, if not allowed.
     */
    public record EntryCheck(@Nullable IColonyView colony, @Nullable Component error)
    {
        public boolean allowed()
        {
            return colony != null;
        }
    }

    public static EntryCheck checkEntry(final Player player)
    {
        final IColony colony = IColonyManager.getInstance().getIColony(player.level(), player.blockPosition());
        if (!(colony instanceof final IColonyView view))
        {
            return new EntryCheck(null, Component.translatable("rtscamera.error.not_in_colony"));
        }
        if (!view.getPermissions().hasPermission(player, Action.PLACE_HUTS))
        {
            return new EntryCheck(null, Component.translatable("rtscamera.error.not_your_colony"));
        }
        if (view.isRaiding())
        {
            return new EntryCheck(null, Component.translatable("rtscamera.error.raid"));
        }
        return new EntryCheck(view, null);
    }

    /**
     * Unloaded chunks report no owning colony, so this also keeps the camera out of unloaded terrain.
     */
    public static boolean isInside(final IColonyView colony, final Level level, final BlockPos pos)
    {
        return colony.isCoordInColony(level, pos);
    }
}
