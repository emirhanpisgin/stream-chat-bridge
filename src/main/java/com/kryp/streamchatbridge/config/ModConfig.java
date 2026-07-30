package com.kryp.streamchatbridge.config;

public class ModConfig {

    public String outgoingPrefix = "!";

    public String incomingPlatformLabel = "Twitch";

    public String incomingMessageFormat =
            "<light_purple>[{platform}]<reset> <green>{username}<reset>: <white>{message}";

    public boolean twitchSendEnabled = true;

    public String twitchChannel = "";
}