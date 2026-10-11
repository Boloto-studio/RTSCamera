package com.rtscamera.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Client-only entity used as the render camera while in RTS mode. It is never added to the level,
 * so no entity type registration is needed (which keeps the mod client-only).
 */
public class RtsCameraEntity extends Entity
{
    /** Size of the camera's collision box; keeps the near plane from clipping into walls. */
    private static final float SIZE = 0.6F;

    public RtsCameraEntity(final ClientLevel level)
    {
        super(EntityType.MARKER, level);
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(final Pose pose)
    {
        return EntityDimensions.fixed(SIZE, SIZE);
    }

    @Override
    protected float getEyeHeight(final Pose pose, final EntityDimensions dimensions)
    {
        return dimensions.height / 2;
    }

    /**
     * Moves the camera, stopping at block collisions. Unlike {@link Entity#move}, this has no
     * gameplay side effects (no block triggers, step sounds, or fall handling).
     *
     * @return the movement actually applied.
     */
    public Vec3 moveColliding(final Vec3 delta)
    {
        final Vec3 allowed = Entity.collideBoundingBox(this, delta, getBoundingBox(), level(), List.of());
        setPos(getX() + allowed.x, getY() + allowed.y, getZ() + allowed.z);
        return allowed;
    }

    /** Captures the current position/rotation for render interpolation. Call once per tick before moving. */
    public void startTick()
    {
        xo = xOld = getX();
        yo = yOld = getY();
        zo = zOld = getZ();
        yRotO = getYRot();
        xRotO = getXRot();
        tickCount++;
    }

    public void snapRotation(final float yaw, final float pitch)
    {
        setYRot(yaw);
        setXRot(pitch);
        yRotO = yaw;
        xRotO = pitch;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(final CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(final CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket()
    {
        throw new UnsupportedOperationException("RTS camera is client-only");
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }
}
