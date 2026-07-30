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
import net.minecraft.network.chat.MutableComponent;

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

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage());

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.label("Account: ").append(MinecraftChatBridge.value(account)));

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.label("Channel: ").append(MinecraftChatBridge.value(channel)));

        MutableComponent twitchStatus = MinecraftChatBridge.twitch().append(MinecraftChatBridge.separator(": "));

        switch (eventSub.getConnectionState()) {
            case CONNECTED -> twitchStatus.append(MinecraftChatBridge.success("Connected"));

            case CONNECTING -> twitchStatus.append(MinecraftChatBridge.warning("Connecting..."));

            case DISCONNECTED -> twitchStatus.append(MinecraftChatBridge.error("Disconnected"));
        }

        MinecraftChatBridge.showLocalMessage(twitchStatus);

        MutableComponent outgoing = MinecraftChatBridge.label("Minecraft → ").append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": "));

        if (ConfigManager.get().twitchSendEnabled) {
            outgoing.append(MinecraftChatBridge.success("ON"));
        } else {
            outgoing.append(MinecraftChatBridge.error("OFF"));
        }

        MinecraftChatBridge.showLocalMessage(outgoing);
    }

    private static void reconnect() {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        TwitchClient twitchClient = StreamChatBridgeClient.getTwitchClient();

        TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

        if (!auth.isAuthenticated()) {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Account is not authenticated")));

            return;
        }

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.warning("Reconnecting...")));

        Thread.startVirtualThread(() -> {
            String configuredChannel = ConfigManager.get().twitchChannel;

            if (!twitchClient.setChannel(configuredChannel)) {
                MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Channel not found")));

                return;
            }

            eventSub.setChannelId(twitchClient.getChannelId());

            eventSub.reconnect();

            waitForReconnectResult(eventSub);
        });
    }

    private static void waitForReconnectResult(TwitchEventSubClient eventSub) {
        long deadline = System.currentTimeMillis() + 15_000L;

        while (System.currentTimeMillis() < deadline) {

            TwitchEventSubClient.ConnectionState state = eventSub.getConnectionState();

            if (state == TwitchEventSubClient.ConnectionState.CONNECTED) {

                showConnectedMessage();
                return;
            }

            if (state == TwitchEventSubClient.ConnectionState.DISCONNECTED) {

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

            showConnectedMessage();

        } else {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.warning("Reconnect is taking longer than expected")));
        }
    }

    private static void showConnectedMessage() {
        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.success("Connected")));
    }
}