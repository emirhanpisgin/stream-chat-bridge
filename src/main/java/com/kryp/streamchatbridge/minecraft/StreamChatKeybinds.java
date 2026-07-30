package com.kryp.streamchatbridge.minecraft;

import com.kryp.streamchatbridge.StreamChatBridgeClient;
import com.kryp.streamchatbridge.minecraft.ui.StreamChatConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class StreamChatKeybinds {

    private static final int DEFAULT_KEY = InputConstants.KEY_S;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(StreamChatBridgeClient.MOD_ID, "main"));

    private static KeyMapping openDashboard;

    private StreamChatKeybinds() {
    }

    public static void register() {
        openDashboard = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.streamchatbridge.open_dashboard", InputConstants.Type.KEYSYM, DEFAULT_KEY, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openDashboard.consumeClick()) {

                if (openDashboard.isDefault()) {
                    long window = client.getWindow().handle();

                    boolean rightAltDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

                    if (!rightAltDown) {
                        continue;
                    }
                }

                client.gui.setScreen(new StreamChatConfigScreen(null));
            }
        });
    }
}