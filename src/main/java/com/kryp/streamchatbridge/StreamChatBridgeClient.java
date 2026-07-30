package com.kryp.streamchatbridge;

import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.minecraft.MinecraftChatBridge;
import com.kryp.streamchatbridge.minecraft.StreamChatCommands;
import com.kryp.streamchatbridge.minecraft.StreamChatKeybinds;
import com.kryp.streamchatbridge.twitch.TwitchAuth;
import com.kryp.streamchatbridge.twitch.TwitchClient;
import com.kryp.streamchatbridge.twitch.TwitchEventSubClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class StreamChatBridgeClient implements ClientModInitializer {

    public static final String MOD_ID = "streamchatbridge";

    private static final TwitchAuth TWITCH_AUTH = new TwitchAuth();

    private static final TwitchClient TWITCH_CLIENT = new TwitchClient(TWITCH_AUTH);

    private static final TwitchEventSubClient TWITCH_EVENT_SUB = new TwitchEventSubClient(TWITCH_AUTH, MinecraftChatBridge::showTwitchMessage);

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        MinecraftChatBridge.registerOutgoing(TWITCH_CLIENT);

        StreamChatCommands.register();
        StreamChatKeybinds.register();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            System.out.println("[Stream Chat Bridge] Shutting down...");

            TWITCH_EVENT_SUB.shutdown();
        });

        System.out.println("[Stream Chat Bridge] Loaded");

        Thread.startVirtualThread(() -> {
            if (TWITCH_AUTH.restoreSession()) {
                onTwitchAuthenticated();
                return;
            }

            System.out.println("[Stream Chat Bridge] No valid Twitch session found.");

            System.out.println("[Stream Chat Bridge] Starting Twitch authentication...");

            if (TWITCH_AUTH.authenticate()) {
                onTwitchAuthenticated();
            } else {
                System.err.println("[Stream Chat Bridge] Twitch authentication was not completed.");
            }
        });
    }

    private void onTwitchAuthenticated() {
        System.out.println("[Stream Chat Bridge] Twitch authenticated as: " + TWITCH_AUTH.getUsername());

        String configuredChannel = ConfigManager.get().twitchChannel;

        if (!TWITCH_CLIENT.setChannel(configuredChannel)) {
            System.err.println("[Stream Chat Bridge] Could not use configured Twitch channel: " + configuredChannel);

            return;
        }

        TWITCH_EVENT_SUB.setChannelId(TWITCH_CLIENT.getChannelId());

        TWITCH_EVENT_SUB.connect();
    }

    public static TwitchAuth getTwitchAuth() {
        return TWITCH_AUTH;
    }

    public static TwitchClient getTwitchClient() {
        return TWITCH_CLIENT;
    }

    public static TwitchEventSubClient getTwitchEventSub() {
        return TWITCH_EVENT_SUB;
    }
}