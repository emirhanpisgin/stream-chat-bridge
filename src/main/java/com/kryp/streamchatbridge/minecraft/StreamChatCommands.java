package com.kryp.streamchatbridge.minecraft;

import com.kryp.streamchatbridge.StreamChatBridgeClient;
import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.kick.KickAuth;
import com.kryp.streamchatbridge.kick.KickChatClient;
import com.kryp.streamchatbridge.kick.KickClient;
import com.kryp.streamchatbridge.minecraft.ui.StreamChatConfigScreen;
import com.kryp.streamchatbridge.twitch.TwitchAuth;
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
                }))));
    }

    private static void openDashboard() {
        Minecraft minecraft = Minecraft.getInstance();

        minecraft.execute(() -> minecraft.gui.setScreen(new StreamChatConfigScreen(null)));
    }

    private static void showStatus() {
        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage());

        showTwitchStatus();
        showKickStatus();
    }

    private static void showTwitchStatus() {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

        /*
         * Header / account
         */

        MutableComponent account = MinecraftChatBridge.twitch().append(MinecraftChatBridge.separator(" — "));

        if (auth.isAuthenticated()) {
            account.append(MinecraftChatBridge.value(auth.getUsername()));

        } else {
            account.append(MinecraftChatBridge.error("Not authenticated"));
        }

        MinecraftChatBridge.showLocalMessage(account);

        /*
         * Channel
         */

        String configuredChannel = ConfigManager.get().twitchChannel;

        String channel;

        if (configuredChannel != null && !configuredChannel.isBlank()) {

            channel = configuredChannel;

        } else if (auth.isAuthenticated()) {
            channel = auth.getUsername();

        } else {
            channel = "Own channel";
        }

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.label("Channel: ").append(MinecraftChatBridge.value(channel)));

        /*
         * Connection
         */

        MutableComponent status = MinecraftChatBridge.label("Status: ");

        if (!auth.isAuthenticated()) {
            status.append(MinecraftChatBridge.error("Not authenticated"));

        } else {
            switch (eventSub.getConnectionState()) {
                case CONNECTED -> status.append(MinecraftChatBridge.success("Connected"));

                case CONNECTING -> status.append(MinecraftChatBridge.warning("Connecting..."));

                case DISCONNECTED -> status.append(MinecraftChatBridge.error("Disconnected"));
            }
        }

        MinecraftChatBridge.showLocalMessage(status);

        /*
         * Outgoing
         */

        MutableComponent outgoing = MinecraftChatBridge.label("Minecraft → ").append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": "));

        if (ConfigManager.get().twitchSendEnabled) {
            outgoing.append(MinecraftChatBridge.success("ON"));

        } else {
            outgoing.append(MinecraftChatBridge.error("OFF"));
        }

        MinecraftChatBridge.showLocalMessage(outgoing);
    }

    private static void showKickStatus() {
        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        KickClient client = StreamChatBridgeClient.getKickClient();

        KickChatClient chat = StreamChatBridgeClient.getKickChat();

        /*
         * Header / account
         */

        MutableComponent account = MinecraftChatBridge.kick().append(MinecraftChatBridge.separator(" — "));

        if (!auth.hasClientCredentials()) {
            account.append(MinecraftChatBridge.error("Not configured"));

        } else if (!auth.isAuthenticated()) {
            account.append(MinecraftChatBridge.error("Not authenticated"));

        } else {
            account.append(MinecraftChatBridge.value(auth.getUsername()));
        }

        MinecraftChatBridge.showLocalMessage(account);

        /*
         * Channel
         */

        String channel;

        if (client.hasChannel()) {
            channel = client.getChannelSlug();

        } else if (auth.isAuthenticated()) {
            channel = auth.getUsername();

        } else {
            channel = "Own channel";
        }

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.label("Channel: ").append(MinecraftChatBridge.value(channel)));

        /*
         * Connection
         */

        MutableComponent status = MinecraftChatBridge.label("Status: ");

        if (!auth.hasClientCredentials()) {
            status.append(MinecraftChatBridge.error("Not configured"));

        } else if (!auth.isAuthenticated()) {
            status.append(MinecraftChatBridge.error("Not authenticated"));

        } else {
            switch (chat.getConnectionState()) {
                case CONNECTED -> status.append(MinecraftChatBridge.success("Connected"));

                case CONNECTING -> status.append(MinecraftChatBridge.warning("Connecting..."));

                case DISCONNECTED -> status.append(MinecraftChatBridge.error("Disconnected"));
            }
        }

        MinecraftChatBridge.showLocalMessage(status);

        /*
         * Outgoing
         */

        MutableComponent outgoing = MinecraftChatBridge.label("Minecraft → ").append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": "));

        if (ConfigManager.get().kickSendEnabled) {
            outgoing.append(MinecraftChatBridge.success("ON"));

        } else {
            outgoing.append(MinecraftChatBridge.error("OFF"));
        }

        MinecraftChatBridge.showLocalMessage(outgoing);
    }
}