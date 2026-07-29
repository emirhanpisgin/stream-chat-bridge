package com.kryp.streamchatbridge.minecraft;

import com.kryp.streamchatbridge.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class MinecraftChatBridge {

    private MinecraftChatBridge() {
    }

    public static void showTwitchMessage(String username, String message) {
        if (!ConfigManager.get().twitchReceiveEnabled) {
            return;
        }

        String formatted = ConfigManager.get().incomingMessageFormat.replace("{username}", username).replace("{message}", message);

        Minecraft client = Minecraft.getInstance();

        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal(formatted));
            }
        });
    }
}