package com.kryp.streamchatbridge;

import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.twitch.TwitchAuth;
import com.kryp.streamchatbridge.twitch.TwitchEventSubClient;
import net.fabricmc.api.ClientModInitializer;

public class StreamChatBridgeClient implements ClientModInitializer {

    public static final String MOD_ID = "streamchatbridge";

    private static final TwitchAuth TWITCH_AUTH = new TwitchAuth();
    private static final TwitchEventSubClient TWITCH_EVENT_SUB = new TwitchEventSubClient(TWITCH_AUTH);

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        System.out.println("[Stream Chat Bridge] Loaded");

        Thread.startVirtualThread(() -> {
            if (TWITCH_AUTH.restoreSession()) {
                System.out.println("[Stream Chat Bridge] Twitch authenticated as: " + TWITCH_AUTH.getUsername());

                TWITCH_EVENT_SUB.connect();
                return;
            }

            System.out.println("[Stream Chat Bridge] No Twitch account connected.");
            System.out.println("[Stream Chat Bridge] Starting Twitch authentication...");

            if (TWITCH_AUTH.authenticate()) {
                System.out.println("[Stream Chat Bridge] Twitch authenticated as: " + TWITCH_AUTH.getUsername());

                TWITCH_EVENT_SUB.connect();
            }
        });
    }

    public static TwitchAuth getTwitchAuth() {
        return TWITCH_AUTH;
    }

    public static TwitchEventSubClient getTwitchEventSub() {
        return TWITCH_EVENT_SUB;
    }
}