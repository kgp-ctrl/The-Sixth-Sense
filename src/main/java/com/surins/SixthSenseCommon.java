package com.surins;
import net.fabricmc.api.ModInitializer;
public final class SixthSenseCommon implements ModInitializer {
    public void onInitialize(){SensePackets.register();SenseServer.initialize();}
}
