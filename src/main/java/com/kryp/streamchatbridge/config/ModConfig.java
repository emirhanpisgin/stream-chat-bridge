package com.kryp.streamchatbridge.config;

public class ModConfig {

    public String outgoingPrefix = "!";
    public String incomingMessageFormat = "[Twitch] {username}: {message}";

    public boolean twitchSendEnabled = true;
    public boolean twitchReceiveEnabled = true;

    public String twitchChannel = "";
}