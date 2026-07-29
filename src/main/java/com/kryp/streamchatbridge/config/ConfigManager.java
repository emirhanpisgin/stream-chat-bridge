package com.kryp.streamchatbridge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("streamchatbridge.json");

    private static ModConfig config;

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            config = new ModConfig();
            save();
            return;
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            config = GSON.fromJson(json, ModConfig.class);

            if (config == null) {
                config = new ModConfig();
                save();
            }
        } catch (Exception e) {
            System.err.println("[Stream Chat Bridge] Failed to load config: " + e.getMessage());
            config = new ModConfig();
        }
    }

    public static void save() {
        if (config == null) {
            return;
        }

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
        } catch (IOException e) {
            System.err.println("[Stream Chat Bridge] Failed to save config: " + e.getMessage());
        }
    }

    public static ModConfig get() {
        if (config == null) {
            config = new ModConfig();
        }

        return config;
    }

    private ConfigManager() {
    }
}