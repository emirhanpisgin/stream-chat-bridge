package com.kryp.streamchatbridge.minecraft.ui;

import com.kryp.streamchatbridge.StreamChatBridgeClient;
import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.config.ModConfig;
import com.kryp.streamchatbridge.kick.KickAuth;
import com.kryp.streamchatbridge.minecraft.MinecraftChatBridge;
import com.kryp.streamchatbridge.twitch.TwitchAuth;
import com.kryp.streamchatbridge.twitch.TwitchClient;
import com.kryp.streamchatbridge.twitch.TwitchEventSubClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StreamChatSettingsScreen extends Screen {

    private static final ColorOption[] COLORS = {new ColorOption("Reset", "<reset>", null), new ColorOption("White", "<white>", ChatFormatting.WHITE), new ColorOption("Gray", "<gray>", ChatFormatting.GRAY), new ColorOption("Dark Gray", "<dark_gray>", ChatFormatting.DARK_GRAY), new ColorOption("Black", "<black>", ChatFormatting.BLACK), new ColorOption("Red", "<red>", ChatFormatting.RED), new ColorOption("Dark Red", "<dark_red>", ChatFormatting.DARK_RED), new ColorOption("Gold", "<gold>", ChatFormatting.GOLD), new ColorOption("Yellow", "<yellow>", ChatFormatting.YELLOW), new ColorOption("Green", "<green>", ChatFormatting.GREEN), new ColorOption("Dark Green", "<dark_green>", ChatFormatting.DARK_GREEN), new ColorOption("Aqua", "<aqua>", ChatFormatting.AQUA), new ColorOption("Dark Aqua", "<dark_aqua>", ChatFormatting.DARK_AQUA), new ColorOption("Blue", "<blue>", ChatFormatting.BLUE), new ColorOption("Dark Blue", "<dark_blue>", ChatFormatting.DARK_BLUE), new ColorOption("Light Purple", "<light_purple>", ChatFormatting.LIGHT_PURPLE), new ColorOption("Dark Purple", "<dark_purple>", ChatFormatting.DARK_PURPLE)};

    private final Screen parent;

    private EditBox twitchChannelField;

    private EditBox kickClientIdField;
    private EditBox kickClientSecretField;

    private EditBox prefixField;
    private EditBox formatField;

    private int selectedColorIndex = 16;

    private String statusMessage = "";

    public StreamChatSettingsScreen(Screen parent) {
        super(Component.literal("Stream Chat Bridge Settings"));

        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = ConfigManager.get();

        KickAuth kickAuth = StreamChatBridgeClient.getKickAuth();

        int contentWidth = 420;
        int left = width / 2 - contentWidth / 2;

        /*
         * Twitch
         */

        twitchChannelField = new EditBox(font, left, 58, contentWidth, 20, Component.literal("Twitch Channel"));

        twitchChannelField.setValue(config.twitchChannel == null ? "" : config.twitchChannel);

        twitchChannelField.setMaxLength(50);

        addRenderableWidget(twitchChannelField);

        /*
         * Kick
         */

        int credentialWidth = 205;

        kickClientIdField = new EditBox(font, left, 103, credentialWidth, 20, Component.literal("Kick Client ID"));

        kickClientIdField.setValue(kickAuth.getClientId() == null ? "" : kickAuth.getClientId());

        kickClientIdField.setMaxLength(256);

        addRenderableWidget(kickClientIdField);

        kickClientSecretField = new EditBox(font, left + 215, 103, credentialWidth, 20, Component.literal("Kick Client Secret"));

        /*
         * Never display the stored secret.
         *
         * Blank means:
         * keep the currently stored secret.
         *
         * If the user enters a value here, the credentials
         * are replaced using the Client ID field + new secret.
         */
        kickClientSecretField.setValue("");
        kickClientSecretField.setMaxLength(512);

        addRenderableWidget(kickClientSecretField);

        /*
         * Shared outgoing settings
         */

        prefixField = new EditBox(font, left, 148, contentWidth, 20, Component.literal("Send Prefix"));

        prefixField.setValue(config.outgoingPrefix == null ? "!" : config.outgoingPrefix);

        prefixField.setMaxLength(20);

        addRenderableWidget(prefixField);

        /*
         * Incoming format
         */

        formatField = new EditBox(font, left, 193, contentWidth, 20, Component.literal("Incoming Format"));

        String format = config.incomingMessageFormat;

        if (format == null || format.isBlank()) {
            format = MinecraftChatBridge.DEFAULT_FORMAT;
        }

        formatField.setMaxLength(512);
        formatField.setValue(format);

        addRenderableWidget(formatField);

        /*
         * Color insertion
         */

        addRenderableWidget(Button.builder(selectedColorText(), button -> {
            selectedColorIndex++;

            if (selectedColorIndex >= COLORS.length) {
                selectedColorIndex = 0;
            }

            button.setMessage(selectedColorText());

            release(button);
        }).bounds(left, 228, 275, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Insert"), button -> {
            insertSelectedColor();

            release(button);
        }).bounds(left + 285, 228, 135, 20).build());

        /*
         * Platform send toggles
         */

        addRenderableWidget(Button.builder(twitchSendToggleText(), button -> {
            config.twitchSendEnabled = !config.twitchSendEnabled;

            button.setMessage(twitchSendToggleText());

            release(button);
        }).bounds(left, 286, 205, 20).build());

        addRenderableWidget(Button.builder(kickSendToggleText(), button -> {
            config.kickSendEnabled = !config.kickSendEnabled;

            button.setMessage(kickSendToggleText());

            release(button);
        }).bounds(left + 215, 286, 205, 20).build());

        /*
         * Save / Back
         */

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            save();

            release(button);
        }).bounds(left, 321, 205, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            save();

            release(button);

            minecraft.gui.setScreen(parent);
        }).bounds(left + 215, 321, 205, 20).build());
    }

    private Component selectedColorText() {
        ColorOption option = COLORS[selectedColorIndex];

        Component value = Component.literal(option.name());

        if (option.formatting() != null) {
            value = value.copy().withStyle(option.formatting());
        }

        return Component.literal("Color: ").append(value);
    }

    private void insertSelectedColor() {
        ColorOption option = COLORS[selectedColorIndex];

        String value = formatField.getValue();

        int cursor = formatField.getCursorPosition();

        cursor = Math.max(0, Math.min(cursor, value.length()));

        String updated = value.substring(0, cursor) + option.tag() + value.substring(cursor);

        formatField.setValue(updated);

        formatField.setCursorPosition(cursor + option.tag().length());

        formatField.setFocused(true);

        setFocused(formatField);
    }

    private void release(Button button) {
        button.setFocused(false);

        if (getFocused() == button) {
            setFocused(null);
        }
    }

    private Component twitchSendToggleText() {
        return Component.literal("Minecraft → Twitch: " + (ConfigManager.get().twitchSendEnabled ? "ON" : "OFF"));
    }

    private Component kickSendToggleText() {
        return Component.literal("Minecraft → Kick: " + (ConfigManager.get().kickSendEnabled ? "ON" : "OFF"));
    }

    private void save() {
        ModConfig config = ConfigManager.get();

        KickAuth kickAuth = StreamChatBridgeClient.getKickAuth();

        /*
         * Existing values
         */

        String oldTwitchChannel = config.twitchChannel == null ? "" : config.twitchChannel.trim();

        String oldKickClientId = kickAuth.getClientId() == null ? "" : kickAuth.getClientId().trim();

        /*
         * New values
         */

        String newTwitchChannel = twitchChannelField.getValue().trim();

        String newKickClientId = kickClientIdField.getValue().trim();

        String newKickClientSecret = kickClientSecretField.getValue().trim();

        /*
         * Save normal mod config
         */

        config.twitchChannel = newTwitchChannel;

        config.outgoingPrefix = prefixField.getValue();

        config.incomingMessageFormat = formatField.getValue().isBlank() ? MinecraftChatBridge.DEFAULT_FORMAT : formatField.getValue();

        ConfigManager.save();

        statusMessage = "Saved";

        /*
         * Kick credentials are NOT stored in ModConfig.
         *
         * A blank secret means keep the existing credentials.
         *
         * Entering a secret means replace the credentials using
         * the current Client ID field and the new secret.
         */

        if (!newKickClientSecret.isBlank()) {
            if (newKickClientId.isBlank()) {
                statusMessage = "Kick Client ID is required";

                return;
            }

            boolean credentialsChanged = !newKickClientId.equals(oldKickClientId) || !newKickClientSecret.isBlank();

            if (credentialsChanged) {
                StreamChatBridgeClient.getKickChat().disconnect();

                StreamChatBridgeClient.getKickClient().clearChannel();

                kickAuth.setClientCredentials(newKickClientId, newKickClientSecret);

                kickClientSecretField.setValue("");

                statusMessage = "Saved — Kick credentials updated";
            }
        } else if (!newKickClientId.equals(oldKickClientId)) {
            /*
             * We cannot safely change only the Client ID because
             * KickAuth intentionally does not expose the stored
             * Client Secret.
             */
            kickClientIdField.setValue(oldKickClientId);

            statusMessage = "Enter Client Secret to change Kick credentials";
        }

        /*
         * Apply Twitch channel immediately if changed.
         */

        if (!oldTwitchChannel.equalsIgnoreCase(newTwitchChannel)) {
            applyTwitchChannel(newTwitchChannel);
        }
    }

    private void applyTwitchChannel(String channel) {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        if (!auth.isAuthenticated()) {
            return;
        }

        statusMessage = "Changing Twitch channel...";

        Thread.startVirtualThread(() -> {
            TwitchClient twitchClient = StreamChatBridgeClient.getTwitchClient();

            TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

            if (!twitchClient.setChannel(channel)) {
                setStatus("Twitch channel not found");

                return;
            }

            eventSub.disconnect();

            eventSub.setChannelId(twitchClient.getChannelId());

            eventSub.connect();

            setStatus("Saved");
        });
    }

    private void setStatus(String message) {
        if (minecraft == null) {
            return;
        }

        minecraft.execute(() -> statusMessage = message);
    }

    private Component previewComponent() {
        String format = formatField == null ? MinecraftChatBridge.DEFAULT_FORMAT : formatField.getValue();

        return MinecraftChatBridge.buildIncomingComponent(format, "Twitch", "ExampleUser", "Hello!");
    }

    @Override
    public void onClose() {
        save();

        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int contentWidth = 420;
        int left = width / 2 - contentWidth / 2;

        graphics.text(font, title, width / 2 - font.width(title) / 2, 18, 0xFFFFFFFF, true);

        /*
         * Twitch
         */

        graphics.text(font, "TWITCH", left, 40, 0xFFAA55AA, false);

        graphics.text(font, "Channel", left, 47, 0xFFAAAAAA, false);

        /*
         * Kick
         */

        graphics.text(font, "KICK APP", left, 85, 0xFF53FC18, false);

        graphics.text(font, "Client ID", left, 92, 0xFFAAAAAA, false);

        graphics.text(font, "Client Secret", left + 215, 92, 0xFFAAAAAA, false);

        graphics.text(font, kickAuthHint(), left, 126, 0xFF777777, false);

        /*
         * Shared
         */

        graphics.text(font, "Send Prefix", left, 137, 0xFFAAAAAA, false);

        graphics.text(font, "Incoming Format", left, 182, 0xFFAAAAAA, false);

        graphics.text(font, "Preview", left, 258, 0xFFAAAAAA, false);

        graphics.text(font, previewComponent(), left, 271, 0xFFFFFFFF, false);

        if (!statusMessage.isBlank()) {
            graphics.text(font, statusMessage, width / 2 - font.width(statusMessage) / 2, 355, 0xFFAAAAAA, false);
        }
    }

    private String kickAuthHint() {
        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        if (auth.hasClientCredentials()) {
            return "Client Secret: leave blank to keep current credentials";
        }

        return "Create a Kick app and enter its Client ID and Client Secret";
    }

    private record ColorOption(String name, String tag, ChatFormatting formatting) {
    }
}