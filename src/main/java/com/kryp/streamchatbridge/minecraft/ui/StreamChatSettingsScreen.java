package com.kryp.streamchatbridge.minecraft.ui;

import com.kryp.streamchatbridge.StreamChatBridgeClient;
import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.config.ModConfig;
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

    private EditBox channelField;
    private EditBox prefixField;
    private EditBox platformField;
    private EditBox formatField;

    private Button colorButton;

    private int selectedColorIndex = 15;

    private String statusMessage = "";

    public StreamChatSettingsScreen(Screen parent) {
        super(Component.literal("Stream Chat Bridge Settings"));

        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = ConfigManager.get();

        int contentWidth = 340;
        int left = width / 2 - contentWidth / 2;

        channelField = new EditBox(font, left, 55, contentWidth, 20, Component.literal("Twitch Channel"));

        channelField.setValue(config.twitchChannel == null ? "" : config.twitchChannel);

        channelField.setMaxLength(50);
        addRenderableWidget(channelField);

        prefixField = new EditBox(font, left, 91, contentWidth, 20, Component.literal("Send Prefix"));

        prefixField.setValue(config.outgoingPrefix == null ? "!" : config.outgoingPrefix);

        prefixField.setMaxLength(20);
        addRenderableWidget(prefixField);

        platformField = new EditBox(font, left, 127, contentWidth, 20, Component.literal("Platform Label"));

        platformField.setValue(config.incomingPlatformLabel == null ? "Twitch" : config.incomingPlatformLabel);

        platformField.setMaxLength(30);
        addRenderableWidget(platformField);

        formatField = new EditBox(font, left, 163, contentWidth, 20, Component.literal("Incoming Format"));

        String format = config.incomingMessageFormat;

        if (format == null || format.isBlank()) {
            format = MinecraftChatBridge.DEFAULT_FORMAT;
        }

        formatField.setMaxLength(512);
        formatField.setValue(format);
        addRenderableWidget(formatField);

        colorButton = addRenderableWidget(Button.builder(selectedColorText(), button -> {
            selectedColorIndex++;

            if (selectedColorIndex >= COLORS.length) {
                selectedColorIndex = 0;
            }

            button.setMessage(selectedColorText());

            release(button);
        }).bounds(left, 204, 220, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Insert"), button -> {
            insertSelectedColor();
            release(button);
        }).bounds(left + 230, 204, 110, 20).build());

        addRenderableWidget(Button.builder(sendToggleText(), button -> {
            config.twitchSendEnabled = !config.twitchSendEnabled;

            button.setMessage(sendToggleText());

            release(button);
        }).bounds(left, 270, contentWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            save();
            release(button);
        }).bounds(left, 300, 165, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            save();
            release(button);

            minecraft.gui.setScreen(parent);
        }).bounds(left + 175, 300, 165, 20).build());
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

    private Component sendToggleText() {
        return Component.literal("Minecraft → Twitch: " + (ConfigManager.get().twitchSendEnabled ? "ON" : "OFF"));
    }

    private void save() {
        ModConfig config = ConfigManager.get();

        String oldChannel = config.twitchChannel == null ? "" : config.twitchChannel.trim();

        String newChannel = channelField.getValue().trim();

        config.twitchChannel = newChannel;

        config.outgoingPrefix = prefixField.getValue();

        config.incomingPlatformLabel = platformField.getValue().isBlank() ? "Twitch" : platformField.getValue();

        config.incomingMessageFormat = formatField.getValue().isBlank() ? MinecraftChatBridge.DEFAULT_FORMAT : formatField.getValue();

        ConfigManager.save();

        statusMessage = "Saved";

        if (!oldChannel.equalsIgnoreCase(newChannel)) {
            applyChannel(newChannel);
        }
    }

    private void applyChannel(String channel) {
        TwitchAuth auth = StreamChatBridgeClient.getTwitchAuth();

        if (!auth.isAuthenticated()) {
            return;
        }

        statusMessage = "Changing channel...";

        Thread.startVirtualThread(() -> {
            TwitchClient twitchClient = StreamChatBridgeClient.getTwitchClient();

            TwitchEventSubClient eventSub = StreamChatBridgeClient.getTwitchEventSub();

            if (!twitchClient.setChannel(channel)) {
                setStatus("Channel not found");

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

        String platform = platformField == null ? "Twitch" : platformField.getValue();

        return MinecraftChatBridge.buildIncomingComponent(format, platform, "ExampleUser", "Hello!");
    }

    @Override
    public void onClose() {
        save();

        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int contentWidth = 340;
        int left = width / 2 - contentWidth / 2;

        graphics.text(font, title, width / 2 - font.width(title) / 2, 18, 0xFFFFFFFF, true);

        graphics.text(font, "Channel", left, 44, 0xFFAAAAAA, false);

        graphics.text(font, "Send Prefix", left, 80, 0xFFAAAAAA, false);

        graphics.text(font, "Platform Label", left, 116, 0xFFAAAAAA, false);

        graphics.text(font, "Incoming Format", left, 152, 0xFFAAAAAA, false);

        graphics.text(font, "Preview", left, 235, 0xFFAAAAAA, false);

        graphics.text(font, previewComponent(), left, 248, 0xFFFFFFFF, false);

        if (!statusMessage.isBlank()) {
            graphics.text(font, statusMessage, width / 2 - font.width(statusMessage) / 2, 330, 0xFFAAAAAA, false);
        }
    }

    private record ColorOption(String name, String tag, ChatFormatting formatting) {
    }
}