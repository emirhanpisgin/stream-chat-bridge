package com.kryp.streamchatbridge.twitch;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class TwitchClient {

    private static final String CHAT_MESSAGES_URL = "https://api.twitch.tv/helix/chat/messages";

    private static final Gson GSON = new Gson();

    private final TwitchAuth auth;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public TwitchClient(TwitchAuth auth) {
        this.auth = auth;
    }

    public boolean sendMessage(String message) {
        if (!auth.isAuthenticated()) {
            System.err.println("[Stream Chat Bridge] Cannot send Twitch message: not authenticated.");
            return false;
        }

        if (message == null || message.isBlank()) {
            return false;
        }

        try {
            JsonObject body = new JsonObject();

            body.addProperty("broadcaster_id", auth.getUserId());
            body.addProperty("sender_id", auth.getUserId());
            body.addProperty("message", message);

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(CHAT_MESSAGES_URL)).header("Authorization", "Bearer " + auth.getAccessToken()).header("Client-Id", TwitchAuth.CLIENT_ID).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[Stream Chat Bridge] Failed to send Twitch message. HTTP " + response.statusCode() + ": " + response.body());
                return false;
            }

            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);

            if (json.has("data") && !json.getAsJsonArray("data").isEmpty()) {

                JsonObject result = json.getAsJsonArray("data").get(0).getAsJsonObject();

                if (result.has("is_sent") && !result.get("is_sent").getAsBoolean()) {

                    String reason = "Unknown reason";

                    if (result.has("drop_reason") && !result.get("drop_reason").isJsonNull()) {

                        JsonObject dropReason = result.getAsJsonObject("drop_reason");

                        if (dropReason.has("message")) {
                            reason = dropReason.get("message").getAsString();
                        }
                    }

                    System.err.println("[Stream Chat Bridge] Twitch rejected message: " + reason);

                    return false;
                }
            }

            System.out.println("[Stream Chat Bridge] Sent Twitch message: " + message);

            return true;

        } catch (Exception e) {
            System.err.println("[Stream Chat Bridge] Failed to send Twitch message: " + e.getMessage());

            return false;
        }
    }
}