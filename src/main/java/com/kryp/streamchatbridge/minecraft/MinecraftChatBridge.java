package com.kryp.streamchatbridge.minecraft;

import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.twitch.TwitchClient;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class MinecraftChatBridge {

    public static final String DEFAULT_FORMAT = "<light_purple>[{platform}]<reset> <green>{username}<reset>: <white>{message}";

    private MinecraftChatBridge() {
    }

    public static void registerOutgoing(TwitchClient twitchClient) {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (!ConfigManager.get().twitchSendEnabled) {
                return true;
            }

            String prefix = ConfigManager.get().outgoingPrefix;

            if (prefix == null || prefix.isEmpty()) {
                return true;
            }

            if (!message.startsWith(prefix)) {
                return true;
            }

            String twitchMessage = message.substring(prefix.length()).trim();

            if (twitchMessage.isEmpty()) {
                return false;
            }

            Thread.startVirtualThread(() -> {
                boolean sent = twitchClient.sendMessage(twitchMessage);

                if (!sent) {
                    showLocalMessage("[Stream Chat Bridge] Failed to send message to Twitch.");
                }
            });

            return false;
        });
    }

    public static void showTwitchMessage(String username, String message) {
        String format = ConfigManager.get().incomingMessageFormat;

        if (format == null || format.isBlank()) {
            format = DEFAULT_FORMAT;
        }

        String platform = ConfigManager.get().incomingPlatformLabel;

        if (platform == null || platform.isBlank()) {
            platform = "Twitch";
        }

        showLocalMessage(buildIncomingComponent(format, platform, username, message));
    }

    public static MutableComponent buildIncomingComponent(String format, String platform, String username, String message) {
        if (format == null || format.isBlank()) {
            format = DEFAULT_FORMAT;
        }

        if (platform == null || platform.isBlank()) {
            platform = "Twitch";
        }

        if (username == null) {
            username = "";
        }

        if (message == null) {
            message = "";
        }

        MutableComponent result = Component.empty();

        ChatFormatting currentColor = null;

        int position = 0;
        int textStart = 0;

        while (position < format.length()) {

            if (format.charAt(position) == '<') {
                int closing = format.indexOf('>', position + 1);

                if (closing != -1) {
                    String tag = format.substring(position + 1, closing);

                    ChatFormatting parsedColor = parseColorTag(tag);

                    boolean reset = tag.equalsIgnoreCase("reset");

                    if (parsedColor != null || reset) {
                        appendFormattedText(result, format.substring(textStart, position), currentColor, platform, username, message);

                        currentColor = reset ? null : parsedColor;

                        position = closing + 1;
                        textStart = position;

                        continue;
                    }
                }
            }

            position++;
        }

        appendFormattedText(result, format.substring(textStart), currentColor, platform, username, message);

        return result;
    }

    private static void appendFormattedText(MutableComponent result, String text, ChatFormatting color, String platform, String username, String message) {
        int position = 0;

        while (position < text.length()) {
            int platformIndex = text.indexOf("{platform}", position);

            int usernameIndex = text.indexOf("{username}", position);

            int messageIndex = text.indexOf("{message}", position);

            int nextIndex = firstIndex(platformIndex, usernameIndex, messageIndex);

            if (nextIndex == -1) {
                appendPart(result, text.substring(position), color);

                return;
            }

            if (nextIndex > position) {
                appendPart(result, text.substring(position, nextIndex), color);
            }

            if (nextIndex == platformIndex) {
                appendPart(result, platform, color);

                position = nextIndex + "{platform}".length();

            } else if (nextIndex == usernameIndex) {
                appendPart(result, username, color);

                position = nextIndex + "{username}".length();

            } else {
                appendPart(result, message, color);

                position = nextIndex + "{message}".length();
            }
        }
    }

    private static void appendPart(MutableComponent result, String text, ChatFormatting color) {
        if (text.isEmpty()) {
            return;
        }

        MutableComponent component = Component.literal(text);

        if (color != null) {
            component.withStyle(color);
        }

        result.append(component);
    }

    private static int firstIndex(int... indexes) {
        int result = -1;

        for (int index : indexes) {
            if (index >= 0 && (result == -1 || index < result)) {
                result = index;
            }
        }

        return result;
    }

    private static ChatFormatting parseColorTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }

        return switch (tag.toLowerCase()) {
            case "black" -> ChatFormatting.BLACK;

            case "dark_blue" -> ChatFormatting.DARK_BLUE;

            case "dark_green" -> ChatFormatting.DARK_GREEN;

            case "dark_aqua" -> ChatFormatting.DARK_AQUA;

            case "dark_red" -> ChatFormatting.DARK_RED;

            case "dark_purple" -> ChatFormatting.DARK_PURPLE;

            case "gold" -> ChatFormatting.GOLD;

            case "gray" -> ChatFormatting.GRAY;

            case "dark_gray" -> ChatFormatting.DARK_GRAY;

            case "blue" -> ChatFormatting.BLUE;

            case "green" -> ChatFormatting.GREEN;

            case "aqua" -> ChatFormatting.AQUA;

            case "red" -> ChatFormatting.RED;

            case "light_purple" -> ChatFormatting.LIGHT_PURPLE;

            case "yellow" -> ChatFormatting.YELLOW;

            case "white" -> ChatFormatting.WHITE;

            default -> null;
        };
    }

    public static void showLocalMessage(String message) {
        showLocalMessage(Component.literal(message));
    }

    public static void showLocalMessage(Component message) {
        Minecraft client = Minecraft.getInstance();

        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(message);
            }
        });
    }
}