package com.kryp.streamchatbridge.minecraft;

import com.kryp.streamchatbridge.config.ConfigManager;
import com.kryp.streamchatbridge.twitch.TwitchClient;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class MinecraftChatBridge {

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
        if (!ConfigManager.get().twitchReceiveEnabled) {
            return;
        }

        String formatted = ConfigManager.get().incomingMessageFormat.replace("{username}", username).replace("{message}", message);

        showLocalMessage(formatted);
    }

    public static void showLocalMessage(String message) {
        Minecraft client = Minecraft.getInstance();

        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal(message));
            }
        });
    }
}