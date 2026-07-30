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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.MutableComponent;

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

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return;
            }

            client.execute(StreamChatBridgeClient::showJoinStatus);
        }));

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

    private static void showJoinStatus() {
        TwitchEventSubClient.ConnectionState state = TWITCH_EVENT_SUB.getConnectionState();

        MutableComponent message = MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": "));

        switch (state) {
            case CONNECTED -> {
                message.append(MinecraftChatBridge.success("Connected"));

                String channel = getDisplayChannel();

                if (channel != null) {
                    message.append(MinecraftChatBridge.separator(" → "));

                    message.append(MinecraftChatBridge.value(channel));
                }
            }

            case CONNECTING -> message.append(MinecraftChatBridge.warning("Connecting..."));

            case DISCONNECTED -> message.append(MinecraftChatBridge.error("Disconnected"));
        }

        MinecraftChatBridge.showLocalMessage(message);
    }

    private static String getDisplayChannel() {
        String configuredChannel = ConfigManager.get().twitchChannel;

        if (configuredChannel != null && !configuredChannel.isBlank()) {
            return configuredChannel;
        }

        if (TWITCH_AUTH.isAuthenticated()) {
            return TWITCH_AUTH.getUsername();
        }

        return null;
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