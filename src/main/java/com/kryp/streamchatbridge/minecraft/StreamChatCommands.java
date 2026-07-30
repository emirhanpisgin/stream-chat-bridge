package com.kryp.streamchatbridge.minecraft;

import com.kryp.streamchatbridge.StreamChatBridgeClient;
import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.kick.KickAuth;
import com.kryp.streamchatbridge.kick.KickChatClient;
import com.kryp.streamchatbridge.kick.KickClient;
import com.kryp.streamchatbridge.minecraft.ui.StreamChatConfigScreen;
import com.kryp.streamchatbridge.twitch.TwitchAuth;
import com.kryp.streamchatbridge.twitch.TwitchClient;
import com.kryp.streamchatbridge.twitch.TwitchEventSubClient;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                    reconnectAll();
                    return 1;
                }))

                .then(ClientCommands.literal("kick")

                        .then(ClientCommands.literal("login").executes(context -> {
                            kickLogin();
                            return 1;
                        }))

                        .then(ClientCommands.literal("status").executes(context -> {
                            kickStatus();
                            return 1;
                        }))

                        .then(ClientCommands.literal("reconnect").executes(context -> {
                            reconnectKick();
                            return 1;
                        }))

                        .then(ClientCommands.literal("logout").executes(context -> {
                            kickLogout();
                            return 1;
                        }))

                        .then(ClientCommands.literal("send").then(ClientCommands.argument("message", StringArgumentType.greedyString()).executes(context -> {
                            String message = StringArgumentType.getString(context, "message");

                            kickSend(message);

                            return 1;
                        }))))));
    }

    private static void openDashboard() {
        Minecraft minecraft = Minecraft.getInstance();

        minecraft.execute(() -> minecraft.gui.setScreen(new StreamChatConfigScreen(null)));
    }

    /*
     * Combined status
     */

    private static void showStatus() {
        TwitchAuth twitchAuth = StreamChatBridgeClient.getTwitchAuth();

        TwitchEventSubClient twitchEventSub = StreamChatBridgeClient.getTwitchEventSub();

        KickAuth kickAuth = StreamChatBridgeClient.getKickAuth();

        KickClient kickClient = StreamChatBridgeClient.getKickClient();

        KickChatClient kickChat = StreamChatBridgeClient.getKickChat();

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage());

        /*
         * Twitch
         */

        String twitchAccount = twitchAuth.isAuthenticated() ? twitchAuth.getUsername() : "Not authenticated";

        String configuredTwitchChannel = ConfigManager.get().twitchChannel;

        String twitchChannel;

        if (configuredTwitchChannel == null || configuredTwitchChannel.isBlank()) {

            twitchChannel = twitchAuth.isAuthenticated() ? twitchAuth.getUsername() : "Own channel";

        } else {
            twitchChannel = configuredTwitchChannel;
        }

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.twitch().append(MinecraftChatBridge.separator(" — ")).append(MinecraftChatBridge.value(twitchAccount)));

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.label("Channel: ").append(MinecraftChatBridge.value(twitchChannel)));

        MutableComponent twitchStatus = MinecraftChatBridge.label("Status: ");

        if (!twitchAuth.isAuthenticated()) {
            twitchStatus.append(MinecraftChatBridge.error("Not authenticated"));

        } else {
            switch (twitchEventSub.getConnectionState()) {
                case CONNECTED -> twitchStatus.append(MinecraftChatBridge.success("Connected"));

                case CONNECTING -> twitchStatus.append(MinecraftChatBridge.warning("Connecting..."));

                case DISCONNECTED -> twitchStatus.append(MinecraftChatBridge.error("Disconnected"));
            }
        }

        MinecraftChatBridge.showLocalMessage(twitchStatus);

        MutableComponent twitchOutgoing = MinecraftChatBridge.label("Minecraft → ").append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": "));

        if (ConfigManager.get().twitchSendEnabled) {
            twitchOutgoing.append(MinecraftChatBridge.success("ON"));

        } else {
            twitchOutgoing.append(MinecraftChatBridge.error("OFF"));
        }

        MinecraftChatBridge.showLocalMessage(twitchOutgoing);

        /*
         * Kick
         */

        String kickAccount;

        if (!kickAuth.hasClientCredentials()) {
            kickAccount = "Not configured";

        } else if (!kickAuth.isAuthenticated()) {
            kickAccount = "Not authenticated";

        } else {
            kickAccount = kickAuth.getUsername();
        }

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.kick().append(MinecraftChatBridge.separator(" — ")).append(MinecraftChatBridge.value(kickAccount)));

        String kickChannel = kickClient.hasChannel() ? kickClient.getChannelSlug() : kickAuth.isAuthenticated() ? kickAuth.getUsername() : "Own channel";

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.label("Channel: ").append(MinecraftChatBridge.value(kickChannel)));

        MutableComponent kickStatus = MinecraftChatBridge.label("Status: ");

        if (!kickAuth.hasClientCredentials()) {
            kickStatus.append(MinecraftChatBridge.error("Not configured"));

        } else if (!kickAuth.isAuthenticated()) {
            kickStatus.append(MinecraftChatBridge.error("Not authenticated"));

        } else {
            switch (kickChat.getConnectionState()) {
                case CONNECTED -> kickStatus.append(MinecraftChatBridge.success("Connected"));

                case CONNECTING -> kickStatus.append(MinecraftChatBridge.warning("Connecting..."));

                case DISCONNECTED -> kickStatus.append(MinecraftChatBridge.error("Disconnected"));
            }
        }

        MinecraftChatBridge.showLocalMessage(kickStatus);

        MutableComponent kickOutgoing = MinecraftChatBridge.label("Minecraft → ").append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": "));

        if (ConfigManager.get().kickSendEnabled) {
            kickOutgoing.append(MinecraftChatBridge.success("ON"));

        } else {
            kickOutgoing.append(MinecraftChatBridge.error("OFF"));
        }

        MinecraftChatBridge.showLocalMessage(kickOutgoing);
    }

    /*
     * Reconnect all
     */

    private static void reconnectAll() {
        reconnectTwitch();
        reconnectKick();
    }

    /*
     * Twitch
     */

    private static void reconnectTwitch() {
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

            waitForTwitchReconnectResult(eventSub);
        });
    }

    private static void waitForTwitchReconnectResult(TwitchEventSubClient eventSub) {
        long deadline = System.currentTimeMillis() + 15_000L;

        while (System.currentTimeMillis() < deadline) {

            TwitchEventSubClient.ConnectionState state = eventSub.getConnectionState();

            if (state == TwitchEventSubClient.ConnectionState.CONNECTED) {

                MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.success("Connected")));

                return;
            }

            try {
                Thread.sleep(100L);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return;
            }
        }

        if (eventSub.getConnectionState() == TwitchEventSubClient.ConnectionState.CONNECTED) {

            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.success("Connected")));

        } else {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.twitch()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.warning("Reconnect is taking longer than expected")));
        }
    }

    /*
     * Kick
     */

    private static void kickLogin() {
        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        if (!auth.hasClientCredentials()) {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Client credentials are not configured")));

            return;
        }

        if (auth.isAuthenticated()) {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.success("Already authenticated as ")).append(MinecraftChatBridge.value(auth.getUsername())));

            return;
        }

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.warning("Opening authentication...")));

        Thread.startVirtualThread(() -> {
            boolean authenticated = auth.authenticate();

            if (!authenticated) {
                MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Authentication failed")));

                return;
            }

            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.success("Authenticated as ")).append(MinecraftChatBridge.value(auth.getUsername())));

            connectKickAfterLogin();
        });
    }

    private static void connectKickAfterLogin() {
        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        KickClient kickClient = StreamChatBridgeClient.getKickClient();

        KickChatClient kickChat = StreamChatBridgeClient.getKickChat();

        if (!kickClient.loadOwnChannel()) {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Could not load channel")));

            return;
        }

        if (!kickChat.connect(auth.getUsername())) {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Could not start chat connection")));
        }
    }

    private static void reconnectKick() {
        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        KickClient kickClient = StreamChatBridgeClient.getKickClient();

        KickChatClient kickChat = StreamChatBridgeClient.getKickChat();

        if (!auth.hasClientCredentials()) {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Client credentials are not configured")));

            return;
        }

        if (!auth.isAuthenticated()) {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Account is not authenticated")));

            return;
        }

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.warning("Reconnecting...")));

        Thread.startVirtualThread(() -> {
            kickChat.disconnect();

            if (!kickClient.loadOwnChannel()) {
                MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Could not load channel")));

                return;
            }

            if (!kickChat.connect(auth.getUsername())) {
                MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Could not start chat connection")));

                return;
            }

            waitForKickReconnectResult(kickChat);
        });
    }

    private static void waitForKickReconnectResult(KickChatClient kickChat) {
        long deadline = System.currentTimeMillis() + 15_000L;

        while (System.currentTimeMillis() < deadline) {

            KickChatClient.ConnectionState state = kickChat.getConnectionState();

            if (state == KickChatClient.ConnectionState.CONNECTED) {

                MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.success("Connected")));

                return;
            }

            try {
                Thread.sleep(100L);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return;
            }
        }

        if (kickChat.getConnectionState() == KickChatClient.ConnectionState.CONNECTED) {

            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.success("Connected")));

        } else {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.warning("Reconnect is taking longer than expected")));
        }
    }

    private static void kickStatus() {
        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        KickClient kickClient = StreamChatBridgeClient.getKickClient();

        KickChatClient kickChat = StreamChatBridgeClient.getKickChat();

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage());

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.kick());

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.label("Credentials: ").append(auth.hasClientCredentials() ? MinecraftChatBridge.success("Configured") : MinecraftChatBridge.error("Missing")));

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.label("Account: ").append(auth.isAuthenticated() ? MinecraftChatBridge.value(auth.getUsername()) : MinecraftChatBridge.error("Not authenticated")));

        if (kickClient.hasChannel()) {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.label("Channel: ").append(MinecraftChatBridge.value(kickClient.getChannelSlug())));
        }

        MutableComponent status = MinecraftChatBridge.label("Status: ");

        if (!auth.isAuthenticated()) {
            status.append(MinecraftChatBridge.error("Not authenticated"));

        } else {
            switch (kickChat.getConnectionState()) {
                case CONNECTED -> status.append(MinecraftChatBridge.success("Connected"));

                case CONNECTING -> status.append(MinecraftChatBridge.warning("Connecting..."));

                case DISCONNECTED -> status.append(MinecraftChatBridge.error("Disconnected"));
            }
        }

        MinecraftChatBridge.showLocalMessage(status);
    }

    private static void kickLogout() {
        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        KickClient kickClient = StreamChatBridgeClient.getKickClient();

        KickChatClient kickChat = StreamChatBridgeClient.getKickChat();

        kickChat.disconnect();

        kickClient.clearChannel();

        auth.logout();

        MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.warning("Logged out")));
    }

    private static void kickSend(String message) {
        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        KickClient kickClient = StreamChatBridgeClient.getKickClient();

        if (!auth.isAuthenticated()) {
            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Account is not authenticated")));

            return;
        }

        if (message == null || message.isBlank()) {

            MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.error("Message cannot be empty")));

            return;
        }

        Thread.startVirtualThread(() -> {
            boolean sent = kickClient.sendMessage(message);

            if (sent) {
                MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.success("Message sent")));

            } else {
                MinecraftChatBridge.showLocalMessage(MinecraftChatBridge.systemMessage().append(MinecraftChatBridge.kick()).append(MinecraftChatBridge.separator(": ")).append(MinecraftChatBridge.error("Failed to send message")));
            }
        });
    }
}