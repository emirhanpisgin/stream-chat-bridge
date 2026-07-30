package com.kryp.streamchatbridge.minecraft.ui;

import com.kryp.streamchatbridge.StreamChatBridgeClient;
import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.config.ModConfig;
import com.kryp.streamchatbridge.kick.KickAuth;
import com.kryp.streamchatbridge.minecraft.MinecraftChatBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class KickSettingsScreen extends Screen {

    private static final ColorOption[] COLORS = {new ColorOption("Reset", "<reset>", null), new ColorOption("White", "<white>", ChatFormatting.WHITE), new ColorOption("Gray", "<gray>", ChatFormatting.GRAY), new ColorOption("Dark Gray", "<dark_gray>", ChatFormatting.DARK_GRAY), new ColorOption("Black", "<black>", ChatFormatting.BLACK), new ColorOption("Red", "<red>", ChatFormatting.RED), new ColorOption("Dark Red", "<dark_red>", ChatFormatting.DARK_RED), new ColorOption("Gold", "<gold>", ChatFormatting.GOLD), new ColorOption("Yellow", "<yellow>", ChatFormatting.YELLOW), new ColorOption("Green", "<green>", ChatFormatting.GREEN), new ColorOption("Dark Green", "<dark_green>", ChatFormatting.DARK_GREEN), new ColorOption("Aqua", "<aqua>", ChatFormatting.AQUA), new ColorOption("Dark Aqua", "<dark_aqua>", ChatFormatting.DARK_AQUA), new ColorOption("Blue", "<blue>", ChatFormatting.BLUE), new ColorOption("Dark Blue", "<dark_blue>", ChatFormatting.DARK_BLUE), new ColorOption("Light Purple", "<light_purple>", ChatFormatting.LIGHT_PURPLE), new ColorOption("Dark Purple", "<dark_purple>", ChatFormatting.DARK_PURPLE)};

    private final Screen parent;

    private EditBox clientIdField;
    private EditBox clientSecretField;

    private EditBox prefixField;
    private EditBox platformField;
    private EditBox formatField;

    private int selectedColorIndex = 9;

    private String statusMessage = "";

    public KickSettingsScreen(Screen parent) {
        super(Component.literal("Kick Settings"));

        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = ConfigManager.get();

        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        int contentWidth = 400;
        int left = width / 2 - contentWidth / 2;

        int halfWidth = 195;

        clientIdField = new EditBox(font, left, 60, halfWidth, 20, Component.literal("Client ID"));

        clientIdField.setValue(auth.getClientId() == null ? "" : auth.getClientId());

        clientIdField.setMaxLength(256);

        addRenderableWidget(clientIdField);

        clientSecretField = new EditBox(font, left + 205, 60, halfWidth, 20, Component.literal("Client Secret"));

        clientSecretField.setValue("");
        clientSecretField.setMaxLength(512);

        addRenderableWidget(clientSecretField);

        prefixField = new EditBox(font, left, 120, contentWidth, 20, Component.literal("Outgoing Prefix"));

        prefixField.setValue(config.kickOutgoingPrefix == null ? "!k " : config.kickOutgoingPrefix);

        prefixField.setMaxLength(30);

        addRenderableWidget(prefixField);

        platformField = new EditBox(font, left, 165, contentWidth, 20, Component.literal("Platform Label"));

        platformField.setValue(config.kickIncomingPlatformLabel == null ? "Kick" : config.kickIncomingPlatformLabel);

        platformField.setMaxLength(30);

        addRenderableWidget(platformField);

        formatField = new EditBox(font, left, 210, contentWidth, 20, Component.literal("Incoming Format"));

        String format = config.kickIncomingMessageFormat;

        if (format == null || format.isBlank()) {
            format = MinecraftChatBridge.KICK_DEFAULT_FORMAT;
        }

        formatField.setMaxLength(512);
        formatField.setValue(format);

        addRenderableWidget(formatField);

        addRenderableWidget(Button.builder(selectedColorText(), button -> {
            selectedColorIndex++;

            if (selectedColorIndex >= COLORS.length) {
                selectedColorIndex = 0;
            }

            button.setMessage(selectedColorText());

            release(button);
        }).bounds(left, 245, 260, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Insert"), button -> {
            insertSelectedColor();

            release(button);
        }).bounds(left + 270, 245, 130, 20).build());

        addRenderableWidget(Button.builder(sendToggleText(), button -> {
            config.kickSendEnabled = !config.kickSendEnabled;

            button.setMessage(sendToggleText());

            release(button);
        }).bounds(left, 300, contentWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            save();

            release(button);
        }).bounds(left, 335, 195, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            save();

            release(button);

            minecraft.gui.setScreen(parent);
        }).bounds(left + 205, 335, 195, 20).build());
    }

    private Component sendToggleText() {
        return Component.literal("Minecraft → Kick: " + (ConfigManager.get().kickSendEnabled ? "ON" : "OFF"));
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

        int cursor = Math.max(0, Math.min(formatField.getCursorPosition(), value.length()));

        String updated = value.substring(0, cursor) + option.tag() + value.substring(cursor);

        formatField.setValue(updated);

        formatField.setCursorPosition(cursor + option.tag().length());

        formatField.setFocused(true);
        setFocused(formatField);
    }

    private void save() {
        ModConfig config = ConfigManager.get();

        KickAuth auth = StreamChatBridgeClient.getKickAuth();

        String oldClientId = auth.getClientId() == null ? "" : auth.getClientId().trim();

        String newClientId = clientIdField.getValue().trim();

        String newClientSecret = clientSecretField.getValue().trim();

        config.kickOutgoingPrefix = prefixField.getValue();

        config.kickIncomingPlatformLabel = platformField.getValue().isBlank() ? "Kick" : platformField.getValue();

        config.kickIncomingMessageFormat = formatField.getValue().isBlank() ? MinecraftChatBridge.KICK_DEFAULT_FORMAT : formatField.getValue();

        ConfigManager.save();

        statusMessage = "Saved";

        /*
         * Credentials are managed by KickAuth rather than ModConfig.
         *
         * We never display the stored secret.
         * Leaving Client Secret blank keeps existing credentials.
         */

        if (!newClientSecret.isBlank()) {
            if (newClientId.isBlank()) {
                statusMessage = "Client ID is required";

                return;
            }

            StreamChatBridgeClient.getKickChat().disconnect();

            StreamChatBridgeClient.getKickClient().clearChannel();

            auth.setClientCredentials(newClientId, newClientSecret);

            clientSecretField.setValue("");

            statusMessage = "Saved — credentials updated";

            return;
        }

        if (!newClientId.equals(oldClientId)) {
            clientIdField.setValue(oldClientId);

            statusMessage = "Enter Client Secret to change credentials";
        }
    }

    private Component previewComponent() {
        String format = formatField == null ? MinecraftChatBridge.KICK_DEFAULT_FORMAT : formatField.getValue();

        String platform = platformField == null ? "Kick" : platformField.getValue();

        return MinecraftChatBridge.buildIncomingComponent(format, platform, "ExampleUser", "Hello!");
    }

    private void release(Button button) {
        button.setFocused(false);

        if (getFocused() == button) {
            setFocused(null);
        }
    }

    @Override
    public void onClose() {
        save();

        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int contentWidth = 400;
        int left = width / 2 - contentWidth / 2;

        graphics.text(font, title, width / 2 - font.width(title) / 2, 20, 0xFF53FC18, true);

        graphics.text(font, "Kick App", left, 40, 0xFFAAAAAA, false);

        graphics.text(font, "Client ID", left, 49, 0xFFAAAAAA, false);

        graphics.text(font, "Client Secret", left + 205, 49, 0xFFAAAAAA, false);

        graphics.text(font, StreamChatBridgeClient.getKickAuth().hasClientCredentials() ? "Leave Client Secret blank to keep the current credentials" : "Create a Kick app and enter its credentials", left, 88, 0xFF777777, false);

        graphics.text(font, "Outgoing Prefix", left, 109, 0xFFAAAAAA, false);

        graphics.text(font, "Platform Label", left, 154, 0xFFAAAAAA, false);

        graphics.text(font, "Incoming Format", left, 199, 0xFFAAAAAA, false);

        graphics.text(font, "Preview", left, 275, 0xFFAAAAAA, false);

        graphics.text(font, previewComponent(), left, 288, 0xFFFFFFFF, false);

        if (!statusMessage.isBlank()) {
            graphics.text(font, statusMessage, width / 2 - font.width(statusMessage) / 2, 370, 0xFFAAAAAA, false);
        }
    }

    private record ColorOption(String name, String tag, ChatFormatting formatting) {
    }
}