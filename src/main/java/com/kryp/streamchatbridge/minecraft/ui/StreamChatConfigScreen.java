package com.kryp.streamchatbridge.minecraft.ui;

import com.kryp.streamchatbridge.StreamChatBridgeClient;
import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.twitch.TwitchAuth;
import com.kryp.streamchatbridge.twitch.TwitchClient;
import com.kryp.streamchatbridge.twitch.TwitchEventSubClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StreamChatConfigScreen extends Screen {

    private final Screen parent;

    private String statusMessage = "";

    public StreamChatConfigScreen(Screen parent) {
        super(Component.literal("Stream Chat Bridge"));

        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentWidth = 300;
        int left = width / 2 - contentWidth / 2;

        TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

        Button connectionButton = Button.builder(connectionButtonText(), button -> {
            button.setFocused(false);
            setFocused(null);

            TwitchEventSubClient.ConnectionState state = eventSub.getConnectionState();

            if (state == TwitchEventSubClient.ConnectionState.CONNECTED) {
                reconnect();
            } else if (state == TwitchEventSubClient.ConnectionState.DISCONNECTED) {
                connect();
            }
        }).bounds(left, 145, contentWidth, 20).build();

        connectionButton.active = eventSub.getConnectionState() != TwitchEventSubClient.ConnectionState.CONNECTING;

        addRenderableWidget(connectionButton);

        Button disconnectButton = Button.builder(Component.literal("Disconnect"), button -> {
            button.setFocused(false);
            setFocused(null);

            eventSub.disconnect();

            statusMessage = "Disconnected";
        }).bounds(left, 175, contentWidth, 20).build();

        disconnectButton.active = eventSub.getConnectionState() != TwitchEventSubClient.ConnectionState.DISCONNECTED;

        addRenderableWidget(disconnectButton);

        addRenderableWidget(Button.builder(Component.literal("Settings"), button -> {
            button.setFocused(false);
            setFocused(null);

            minecraft.gui.setScreen(new StreamChatSettingsScreen(this));
        }).bounds(left, 215, contentWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            button.setFocused(false);
            setFocused(null);

            minecraft.gui.setScreen(parent);
        }).bounds(left, 245, contentWidth, 20).build());

        eventSub.setStateListener(this::connectionStateChanged);
    }

    private Component connectionButtonText() {
        TwitchEventSubClient.ConnectionState state = StreamChatBridgeClient.getTwitchEventSub().getConnectionState();

        return Component.literal(switch (state) {
            case CONNECTED -> "Reconnect";

            case CONNECTING -> "Connecting...";

            case DISCONNECTED -> "Connect";
        });
    }

    private void connect() {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        TwitchClient twitchClient = StreamChatBridgeClient.getTwitchClient();

        TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

        if (!auth.isAuthenticated()) {
            statusMessage = "Twitch account is not authenticated";

            return;
        }

        if (twitchClient.getChannelId() == null) {
            statusMessage = "No channel selected";

            return;
        }

        statusMessage = "Connecting...";

        eventSub.setChannelId(twitchClient.getChannelId());

        eventSub.connect();
    }

    private void reconnect() {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        TwitchClient twitchClient = StreamChatBridgeClient.getTwitchClient();

        TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

        if (!auth.isAuthenticated()) {
            statusMessage = "Twitch account is not authenticated";

            return;
        }

        if (twitchClient.getChannelId() == null) {
            statusMessage = "No channel selected";

            return;
        }

        statusMessage = "Reconnecting...";

        eventSub.setChannelId(twitchClient.getChannelId());

        eventSub.reconnect();
    }

    private void connectionStateChanged(TwitchEventSubClient.ConnectionState state) {
        if (minecraft == null) {
            return;
        }

        minecraft.execute(() -> {
            statusMessage = switch (state) {
                case CONNECTING -> "Connecting...";

                case CONNECTED -> "Connected";

                case DISCONNECTED -> "Disconnected";
            };

            rebuildWidgets();
        });
    }

    private String accountText() {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        return auth.isAuthenticated() ? auth.getUsername() : "Not connected";
    }

    private String channelText() {
        String channel = ConfigManager.get().twitchChannel;

        if (channel != null && !channel.isBlank()) {
            return channel;
        }

        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        if (auth.isAuthenticated()) {
            return auth.getUsername();
        }

        return "Not selected";
    }

    private String connectionText() {
        return switch (StreamChatBridgeClient.getTwitchEventSub().getConnectionState()) {
            case CONNECTED -> "Connected";

            case CONNECTING -> "Connecting...";

            case DISCONNECTED -> "Disconnected";
        };
    }

    @Override
    public void onClose() {
        StreamChatBridgeClient.getTwitchEventSub().setStateListener(null);

        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        StreamChatBridgeClient.getTwitchEventSub().setStateListener(null);

        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int contentWidth = 300;
        int left = width / 2 - contentWidth / 2;

        graphics.text(font, title, width / 2 - font.width(title) / 2, 25, 0xFFFFFFFF, true);

        graphics.text(font, "TWITCH", left, 65, 0xFFAAAAAA, false);

        graphics.text(font, "Account", left, 82, 0xFF888888, false);

        graphics.text(font, accountText(), left + 90, 82, 0xFFFFFFFF, false);

        graphics.text(font, "Channel", left, 99, 0xFF888888, false);

        graphics.text(font, channelText(), left + 90, 99, 0xFFFFFFFF, false);

        graphics.text(font, "Status", left, 116, 0xFF888888, false);

        graphics.text(font, connectionText(), left + 90, 116, 0xFFFFFFFF, false);

        if (!statusMessage.isBlank()) {
            graphics.text(font, statusMessage, width / 2 - font.width(statusMessage) / 2, 275, 0xFFAAAAAA, false);
        }
    }
}