package com.kryp.streamchatbridge;

import com.kryp.streamchatbridge.config.ConfigManager;
import net.fabricmc.api.ClientModInitializer;

public class StreamChatBridgeClient implements ClientModInitializer {

    public static final String MOD_ID = "streamchatbridge";

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        System.out.println("[Stream Chat Bridge] Loaded");
        System.out.println("[Stream Chat Bridge] Outgoing prefix: " + ConfigManager.get().outgoingPrefix);
    }
}