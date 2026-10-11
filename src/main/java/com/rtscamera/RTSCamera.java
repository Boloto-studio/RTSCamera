package com.rtscamera;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;

/**
 * Entry point. All functionality is client-side (see {@link com.rtscamera.client}),
 * so servers do not need this mod installed.
 */
@Mod(RTSCamera.MOD_ID)
public class RTSCamera
{
    public static final String MOD_ID = "rtscamera";

    public RTSCamera()
    {
        // Client-only mod: don't require it on the server and don't mark servers without it as incompatible.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
          () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
    }
}
