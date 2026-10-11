package com.rtscamera.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Owns the RTS camera session: entering/leaving, per-tick movement and the colony/raid restrictions.
 * The session runs underneath the Structurize build tool window, which stays open and usable.
 */
public final class RtsController
{
    /** Vertical field of view forced while in RTS mode; also used for cursor ray picking. */
    public static final float FOV = 70F;

    private static final float DEFAULT_PITCH = 55F;
    private static final float MIN_PITCH = 20F;
    private static final float MAX_PITCH = 89F;
    private static final double MOVE_SPEED = 0.7;
    private static final double SPRINT_MULTIPLIER = 2.5;
    private static final double ZOOM_STEP = 2.5;
    private static final float KEY_ROTATE_SPEED = 5F;
    private static final double START_DISTANCE = 22;
    private static final double PICK_RANGE = 256;
    /** How far above the build limit the camera may go. */
    private static final int CEILING_MARGIN = 16;

    private static Session session;

    private RtsController() {}

    private static final class Session
    {
        final RtsCameraEntity camera;
        final int colonyId;
        final Entity previousCamera;
        final CameraType previousCameraType;
        BuildToolBridge buildTool;
        IColonyView colony;
        Vec3 velocity = Vec3.ZERO;
        double pendingZoom;

        Session(final RtsCameraEntity camera, final BuildToolBridge buildTool, final IColonyView colony, final Entity previousCamera,
          final CameraType previousCameraType)
        {
            this.camera = camera;
            this.buildTool = buildTool;
            this.colony = colony;
            this.colonyId = colony.getID();
            this.previousCamera = previousCamera;
            this.previousCameraType = previousCameraType;
        }
    }

    public static boolean isActive()
    {
        return session != null;
    }

    @Nullable
    public static IColonyView colony()
    {
        return session == null ? null : session.colony;
    }

    @Nullable
    public static BuildToolBridge buildTool()
    {
        return session == null ? null : session.buildTool;
    }

    /** True if the given screen is the build tool window the session is driving. */
    public static boolean isSessionScreen(final Object screen)
    {
        return session != null && session.buildTool.window().getScreen() == screen;
    }

    /**
     * Called from the build tool button. Switches the camera to RTS mode if the colony rules allow it.
     * The build tool window stays open.
     */
    public static void tryEnter(final AbstractBlueprintManipulationWindow window)
    {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null || session != null)
        {
            return;
        }

        final ColonyRules.EntryCheck check = ColonyRules.checkEntry(player);
        if (!check.allowed())
        {
            deny(player, check.error());
            return;
        }

        final BuildToolBridge buildTool = new BuildToolBridge(window);
        final IColonyView colony = check.colony();
        final RtsCameraEntity camera = new RtsCameraEntity(mc.level);
        final float yaw = Math.round(player.getYRot() / 45F) * 45F;
        camera.snapRotation(yaw, DEFAULT_PITCH);
        placeCameraInitially(camera, player, colony, buildTool.preview().getPos());

        session = new Session(camera, buildTool, colony, mc.getCameraEntity(), mc.options.getCameraType());

        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
    }

    /**
     * Leaves RTS mode and restores the normal camera. Screens are left as they are.
     *
     * @param reason optional message shown to the player.
     */
    public static void exit(@Nullable final Component reason)
    {
        final Session s = session;
        if (s == null)
        {
            return;
        }
        session = null;

        final Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(s.previousCameraType);
        mc.setCameraEntity(mc.player != null ? mc.player : s.previousCamera);

        if (reason != null && mc.player != null)
        {
            mc.player.displayClientMessage(reason, false);
        }
    }

    /** Drops the session without touching anything else; used on disconnect. */
    public static void reset()
    {
        if (session != null)
        {
            Minecraft.getInstance().options.setCameraType(session.previousCameraType);
            session = null;
        }
    }

    public static void tick()
    {
        final Session s = session;
        if (s == null)
        {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null || s.camera.level() != mc.level || !player.isAlive())
        {
            exit(null);
            return;
        }

        // Stay in RTS while a BlockUI window is open (the build tool, or dialogs it opens such as the
        // style switcher). Closing it (Esc, cancel, confirming a placement) returns to first person.
        if (!(mc.screen instanceof final BOScreen screen))
        {
            exit(null);
            return;
        }
        final BOWindow window = screen.getWindow();
        if (BuildToolBridge.supports(window) && window != s.buildTool.window())
        {
            // e.g. a fresh build tool window after switching style packs
            s.buildTool = new BuildToolBridge((AbstractBlueprintManipulationWindow) window);
        }

        final IColonyView colony = IColonyManager.getInstance().getColonyView(s.colonyId, mc.level.dimension());
        if (colony == null)
        {
            exit(null);
            return;
        }
        s.colony = colony;

        if (colony.isRaiding())
        {
            exit(Component.translatable("rtscamera.exit.raid"));
            return;
        }
        if (player.hurtTime > 0)
        {
            exit(Component.translatable("rtscamera.exit.hurt"));
            return;
        }

        tickMovement(s, mc, BOWindow.getFocus() == null);
    }

    private static void tickMovement(final Session s, final Minecraft mc, final boolean acceptKeys)
    {
        final RtsCameraEntity camera = s.camera;
        camera.startTick();

        double forward = 0;
        double strafe = 0;
        double vertical = 0;
        boolean sprint = false;
        float yaw = camera.getYRot();

        // Keys are polled directly because the build tool screen is open; skip while typing in a text field
        if (acceptKeys)
        {
            if (isKeyDown(mc, InputConstants.KEY_Q)) yaw -= KEY_ROTATE_SPEED;
            if (isKeyDown(mc, InputConstants.KEY_E)) yaw += KEY_ROTATE_SPEED;
            if (isDown(mc, mc.options.keyUp)) forward++;
            if (isDown(mc, mc.options.keyDown)) forward--;
            if (isDown(mc, mc.options.keyLeft)) strafe++;
            if (isDown(mc, mc.options.keyRight)) strafe--;
            if (isDown(mc, mc.options.keyJump)) vertical++;
            if (isDown(mc, mc.options.keyShift)) vertical--;
            sprint = isDown(mc, mc.options.keySprint);
        }
        camera.setYRot(yaw);

        final double speed = MOVE_SPEED * (sprint ? SPRINT_MULTIPLIER : 1);
        final float yawRad = yaw * Mth.DEG_TO_RAD;
        final double sin = Mth.sin(yawRad);
        final double cos = Mth.cos(yawRad);
        Vec3 wish = new Vec3(strafe * cos - forward * sin, vertical, forward * cos + strafe * sin);
        if (wish.lengthSqr() > 1)
        {
            wish = wish.normalize();
        }
        s.velocity = s.velocity.scale(0.5).add(wish.scale(speed * 0.5));

        // Zoom along the view direction, eased over a few ticks
        final double zoomNow = s.pendingZoom * 0.35;
        s.pendingZoom -= zoomNow;
        if (Math.abs(s.pendingZoom) < 0.01)
        {
            s.pendingZoom = 0;
        }

        final Vec3 delta = s.velocity.add(camera.getLookAngle().scale(zoomNow));
        // Per axis, so the camera slides along walls and the colony border instead of sticking
        tryMove(s, new Vec3(delta.x, 0, 0));
        tryMove(s, new Vec3(0, delta.y, 0));
        tryMove(s, new Vec3(0, 0, delta.z));
    }

    private static void tryMove(final Session s, final Vec3 delta)
    {
        if (delta.lengthSqr() < 1.0E-8)
        {
            return;
        }
        final RtsCameraEntity camera = s.camera;
        final Vec3 start = camera.position();
        camera.moveColliding(delta);
        if (!isAllowedPosition(s.colony, camera.level(), camera.position()))
        {
            camera.setPos(start);
        }
    }

    private static boolean isAllowedPosition(final IColonyView colony, final Level level, final Vec3 pos)
    {
        return pos.y >= level.getMinBuildHeight()
                 && pos.y <= level.getMaxBuildHeight() + CEILING_MARGIN
                 && ColonyRules.isInside(colony, level, BlockPos.containing(pos));
    }

    /**
     * Puts the camera above and behind the building preview (or the player) looking down at it.
     * Falls back to the player's eyes if that spot is blocked or outside the colony.
     */
    private static void placeCameraInitially(final RtsCameraEntity camera, final LocalPlayer player, final IColonyView colony, @Nullable final BlockPos previewPos)
    {
        final Level level = camera.level();
        final BlockPos focus = previewPos != null && ColonyRules.isInside(colony, level, previewPos) ? previewPos : player.blockPosition();
        final int ground = Math.max(focus.getY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, focus.getX(), focus.getZ()));
        final Vec3 focusPoint = new Vec3(focus.getX() + 0.5, ground, focus.getZ() + 0.5);
        final Vec3 eye = focusPoint.subtract(camera.getLookAngle().scale(START_DISTANCE));
        final double eyeOffset = camera.getEyeHeight();

        camera.setPos(eye.x, eye.y - eyeOffset, eye.z);
        if (!level.noCollision(camera, camera.getBoundingBox()) || !isAllowedPosition(colony, level, camera.position()))
        {
            final Vec3 playerEye = player.getEyePosition();
            camera.setPos(playerEye.x, playerEye.y - eyeOffset, playerEye.z);
        }
        camera.startTick();
    }

    /** Queues a zoom: positive moves the camera toward what it looks at. */
    public static void zoom(final double scrollDelta)
    {
        if (session != null)
        {
            session.pendingZoom += scrollDelta * ZOOM_STEP;
        }
    }

    /** Rotates the camera directly (mouse drag); applied immediately for responsiveness. */
    public static void rotateBy(final float yawDelta, final float pitchDelta)
    {
        if (session != null)
        {
            final RtsCameraEntity camera = session.camera;
            camera.snapRotation(camera.getYRot() + yawDelta, Mth.clamp(camera.getXRot() + pitchDelta, MIN_PITCH, MAX_PITCH));
        }
    }

    /** Horizontal direction the camera is facing, snapped to the nearest axis. */
    public static Direction cameraFacing()
    {
        return session == null ? Direction.NORTH : Direction.fromYRot(session.camera.getYRot());
    }

    /**
     * Ray-casts from the camera through a mouse position (GUI-scaled coordinates, as passed to screen mouse events).
     * Uses the window's GUI-scaled size: BlockUI screens overwrite {@code Screen#width/height} with their own scale.
     *
     * @return the hit, or null if nothing was hit.
     */
    @Nullable
    public static BlockHitResult pick(final double mouseX, final double mouseY)
    {
        if (session == null)
        {
            return null;
        }
        final Minecraft mc = Minecraft.getInstance();
        final int screenWidth = mc.getWindow().getGuiScaledWidth();
        final int screenHeight = mc.getWindow().getGuiScaledHeight();
        final Camera cam = mc.gameRenderer.getMainCamera();
        final Vec3 origin = cam.getPosition();
        final Vector3f look = cam.getLookVector();
        final Vector3f up = cam.getUpVector();
        final Vector3f left = cam.getLeftVector();

        final double ndcX = 2 * mouseX / screenWidth - 1;
        final double ndcY = 1 - 2 * mouseY / screenHeight;
        final double tanHalf = Math.tan(Math.toRadians(FOV / 2));
        final double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        final double u = ndcY * tanHalf;
        final double r = ndcX * tanHalf * aspect;

        final Vec3 dir = new Vec3(look.x() + up.x() * u - left.x() * r,
          look.y() + up.y() * u - left.y() * r,
          look.z() + up.z() * u - left.z() * r).normalize();

        final BlockHitResult hit = session.camera.level().clip(
          new ClipContext(origin, origin.add(dir.scale(PICK_RANGE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, session.camera));
        return hit.getType() == HitResult.Type.MISS ? null : hit;
    }

    /**
     * Moves the building preview to the block under the cursor, if it is inside the colony.
     */
    public static void placePreviewAtCursor(final double mouseX, final double mouseY)
    {
        final BlockHitResult hit = pick(mouseX, mouseY);
        if (hit == null || session == null)
        {
            return;
        }
        final BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        if (ColonyRules.isInside(session.colony, session.camera.level(), target))
        {
            session.buildTool.placeCenteredAt(target);
        }
    }

    private static void deny(final LocalPlayer player, @Nullable final Component reason)
    {
        if (reason != null)
        {
            player.displayClientMessage(reason, false);
        }
        player.playNotifySound(SoundEvents.VILLAGER_NO, player.getSoundSource(), 1F, 1F);
    }

    private static boolean isDown(final Minecraft mc, final KeyMapping key)
    {
        final InputConstants.Key bound = key.getKey();
        return bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() != InputConstants.UNKNOWN.getValue() && isKeyDown(mc, bound.getValue());
    }

    private static boolean isKeyDown(final Minecraft mc, final int keyCode)
    {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), keyCode);
    }
}
