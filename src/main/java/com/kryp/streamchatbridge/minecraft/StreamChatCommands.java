package com.kryp.streamchatbridge.minecraft;

import com.kryp.streamchatbridge.StreamChatBridgeClient;
import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.minecraft.ui.StreamChatConfigScreen;
import com.kryp.streamchatbridge.twitch.TwitchAuth;
import com.kryp.streamchatbridge.twitch.TwitchClient;
import com.kryp.streamchatbridge.twitch.TwitchEventSubClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

public final class StreamChatCommands {

    private StreamChatCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommands.literal("scb").executes(context -> {
                    openDashboard();
                    return 1;
                })

                .then(ClientCommands.literal("config").executes(context -> {
                    openDashboard();
                    return 1;
                }))

                .then(ClientCommands.literal("status").executes(context -> {
                    showStatus();
                    return 1;
                }))

                .then(ClientCommands.literal("reconnect").executes(context -> {
                    reconnect();
                    return 1;
                }))));
    }

    private static void openDashboard() {
        Minecraft minecraft = Minecraft.getInstance();

        minecraft.execute(() -> minecraft.gui.setScreen(new StreamChatConfigScreen(null)));
    }

    private static void showStatus() {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

        String account = auth.isAuthenticated() ? auth.getUsername() : "Not connected";

        String configuredChannel = ConfigManager.get().twitchChannel;

        String channel;

        if (configuredChannel == null || configuredChannel.isBlank()) {

            channel = auth.isAuthenticated() ? auth.getUsername() : "Own channel";

        } else {
            channel = configuredChannel;
        }

        String connection = switch (eventSub.getConnectionState()) {
            case CONNECTED -> "Connected";

            case CONNECTING -> "Connecting";

            case DISCONNECTED -> "Disconnected";
        };

        MinecraftChatBridge.showLocalMessage("[Stream Chat Bridge]");

        MinecraftChatBridge.showLocalMessage("Account: " + account);

        MinecraftChatBridge.showLocalMessage("Channel: " + channel);

        MinecraftChatBridge.showLocalMessage("Twitch: " + connection);

        MinecraftChatBridge.showLocalMessage("Minecraft → Twitch: " + (ConfigManager.get().twitchSendEnabled ? "ON" : "OFF"));
    }

    private static void reconnect() {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        TwitchClient twitchClient = StreamChatBridgeClient.getTwitchClient();

        TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

        if (!auth.isAuthenticated()) {
            MinecraftChatBridge.showLocalMessage("[Stream Chat Bridge] Twitch account is not authenticated.");

            return;
        }

        MinecraftChatBridge.showLocalMessage("[Stream Chat Bridge] Reconnecting...");

        Thread.startVirtualThread(() -> {
            String configuredChannel = ConfigManager.get().twitchChannel;

            if (!twitchClient.setChannel(configuredChannel)) {
                MinecraftChatBridge.showLocalMessage("[Stream Chat Bridge] Twitch channel not found.");

                return;
            }

            eventSub.setChannelId(twitchClient.getChannelId());

            eventSub.reconnect();

            waitForReconnectResult(eventSub);
        });
    }

    private static void waitForReconnectResult(TwitchEventSubClient eventSub) {
        long deadline = System.currentTimeMillis() + 15_000L;

        /*
         * reconnect() immediately transitions the client to
         * CONNECTING. Wait for it to either become CONNECTED,
         * fail back to DISCONNECTED, or exceed our feedback
         * timeout.
         */
        while (System.currentTimeMillis() < deadline) {
            TwitchEventSubClient.ConnectionState state = eventSub.getConnectionState();

            if (state == TwitchEventSubClient.ConnectionState.CONNECTED) {

                MinecraftChatBridge.showLocalMessage("[Stream Chat Bridge] Connected.");

                return;
            }

            if (state == TwitchEventSubClient.ConnectionState.DISCONNECTED) {

                /*
                 * Automatic reconnect may still be scheduled after
                 * an unexpected failure, so give it a little time
                 * instead of reporting failure immediately.
                 */
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                continue;
            }

            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        TwitchEventSubClient.ConnectionState state = eventSub.getConnectionState();

        if (state == TwitchEventSubClient.ConnectionState.CONNECTED) {

            MinecraftChatBridge.showLocalMessage("[Stream Chat Bridge] Connected.");

        } else {
            MinecraftChatBridge.showLocalMessage("[Stream Chat Bridge] Reconnect is taking longer than expected.");
        }
    }
}