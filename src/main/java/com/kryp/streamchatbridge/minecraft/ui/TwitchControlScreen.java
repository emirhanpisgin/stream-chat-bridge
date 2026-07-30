package com.kryp.streamchatbridge.minecraft.ui;

import com.kryp.streamchatbridge.StreamChatBridgeClient;
import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.twitch.TwitchAuth;
import com.kryp.streamchatbridge.twitch.TwitchClient;
import com.kryp.streamchatbridge.twitch.TwitchEventSubClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TwitchControlScreen extends Screen {

    private final Screen parent;

    private EditBox channelField;
    private String statusMessage = "";

    public TwitchControlScreen(Screen parent) {
        super(Component.literal("Twitch"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentWidth = 340;
        int left = width / 2 - contentWidth / 2;

        channelField = new EditBox(font, left, 135, contentWidth, 20, Component.literal("Twitch Channel"));

        channelField.setValue(ConfigManager.get().twitchChannel);

        channelField.setMaxLength(50);

        addRenderableWidget(channelField);

        addRenderableWidget(Button.builder(Component.literal("Save Channel & Connect"), button -> {
            clearFocus();
            saveChannelAndConnect();
        }).bounds(left, 165, contentWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Connect"), button -> {
            clearFocus();
            connectChat();
        }).bounds(left, 216, 105, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Reconnect"), button -> {
            clearFocus();
            reconnectChat();
        }).bounds(left + 117, 216, 106, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Disconnect"), button -> {
            clearFocus();
            disconnectChat();
        }).bounds(left + 235, 216, 105, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            clearFocus();

            minecraft.gui.setScreen(parent);
        }).bounds(left, 276, contentWidth, 20).build());

        StreamChatBridgeClient.getTwitchEventSub().setStateListener(this::connectionStateChanged);
    }

    private void saveChannelAndConnect() {
        String channel = channelField.getValue().trim();

        ConfigManager.get().twitchChannel = channel;

        ConfigManager.save();

        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        if (!auth.isAuthenticated()) {
            statusMessage = "Connect a Twitch account first.";
            return;
        }

        statusMessage = "Finding channel...";

        Thread.startVirtualThread(() -> {
            TwitchClient twitchClient = StreamChatBridgeClient.getTwitchClient();

            TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

            if (!twitchClient.setChannel(channel)) {
                setStatus("Channel not found.");
                return;
            }

            eventSub.disconnect();

            eventSub.setChannelId(twitchClient.getChannelId());

            setStatus("Connecting...");

            eventSub.connect();
        });
    }

    private void connectChat() {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        TwitchClient twitchClient = StreamChatBridgeClient.getTwitchClient();

        TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

        if (!auth.isAuthenticated()) {
            statusMessage = "Connect a Twitch account first.";
            return;
        }

        if (twitchClient.getChannelId() == null) {
            statusMessage = "Choose a channel first.";
            return;
        }

        eventSub.setChannelId(twitchClient.getChannelId());

        eventSub.connect();
    }

    private void reconnectChat() {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        TwitchClient twitchClient = StreamChatBridgeClient.getTwitchClient();

        TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

        if (!auth.isAuthenticated()) {
            statusMessage = "Connect a Twitch account first.";
            return;
        }

        if (twitchClient.getChannelId() == null) {
            statusMessage = "Choose a channel first.";
            return;
        }

        statusMessage = "Reconnecting...";

        eventSub.disconnect();

        eventSub.setChannelId(twitchClient.getChannelId());

        eventSub.connect();
    }

    private void disconnectChat() {
        StreamChatBridgeClient.getTwitchEventSub().disconnect();

        statusMessage = "Disconnected.";
    }

    private void connectionStateChanged(TwitchEventSubClient.ConnectionState state) {
        switch (state) {
            case CONNECTING -> setStatus("Connecting...");

            case CONNECTED -> setStatus("Connected successfully.");

            case DISCONNECTED -> setStatus("Disconnected.");
        }
    }

    private void setStatus(String message) {
        if (minecraft == null) {
            return;
        }

        minecraft.execute(() -> statusMessage = message);
    }

    private String connectionText() {
        return switch (StreamChatBridgeClient.getTwitchEventSub().getConnectionState()) {
            case CONNECTED -> "Connected";

            case CONNECTING -> "Connecting...";

            case DISCONNECTED -> "Disconnected";
        };
    }

    private String channelText() {
        String configured = ConfigManager.get().twitchChannel;

        if (configured == null || configured.isBlank()) {

            TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

            if (auth.isAuthenticated()) {
                return auth.getUsername() + " (your channel)";
            }

            return "Your channel";
        }

        return configured;
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

        int contentWidth = 340;
        int left = width / 2 - contentWidth / 2;

        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        String account = auth.isAuthenticated() ? auth.getUsername() : "Not connected";

        graphics.text(font, title, width / 2 - font.width(title) / 2, 20, 0xFFFFFFFF, true);

        String subtitle = "Manage your Twitch connection.";

        graphics.text(font, subtitle, width / 2 - font.width(subtitle) / 2, 38, 0xFFAAAAAA, false);

        graphics.text(font, "ACCOUNT", left, 65, 0xFFAAAAAA, false);

        graphics.text(font, account, left, 79, 0xFFFFFFFF, false);

        graphics.text(font, "STATUS", left + 175, 65, 0xFFAAAAAA, false);

        graphics.text(font, connectionText(), left + 175, 79, 0xFFFFFFFF, false);

        graphics.text(font, "CHANNEL", left, 105, 0xFFAAAAAA, false);

        graphics.text(font, channelText(), left, 118, 0xFFFFFFFF, false);

        graphics.text(font, "Channel login", left, 124, 0xFF888888, false);

        graphics.text(font, "CONNECTION", left, 202, 0xFFAAAAAA, false);

        if (!statusMessage.isBlank()) {
            graphics.text(font, statusMessage, width / 2 - font.width(statusMessage) / 2, 252, 0xFFAAAAAA, false);
        }
    }
}