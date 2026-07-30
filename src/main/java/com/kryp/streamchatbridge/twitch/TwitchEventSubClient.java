package com.kryp.streamchatbridge.twitch;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

public final class TwitchEventSubClient implements WebSocket.Listener {

    private static final String WEBSOCKET_URL = "wss://eventsub.wss.twitch.tv/ws";

    private static final String SUBSCRIPTIONS_URL = "https://api.twitch.tv/helix/eventsub/subscriptions";

    private static final Gson GSON = new Gson();

    private final TwitchAuth auth;
    private final BiConsumer<String, String> messageHandler;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private WebSocket webSocket;
    private boolean connected;

    private final StringBuilder messageBuffer = new StringBuilder();

    private String channelId;

    public TwitchEventSubClient(TwitchAuth auth, BiConsumer<String, String> messageHandler) {
        this.auth = auth;
        this.messageHandler = messageHandler;
    }

    public void connect() {
        if (!auth.isAuthenticated()) {
            System.err.println("[Stream Chat Bridge] Cannot connect to Twitch chat: not authenticated.");
            return;
        }

        if (channelId == null) {
            System.err.println("[Stream Chat Bridge] Cannot connect to Twitch chat: no channel selected.");
            return;
        }

        if (connected) {
            return;
        }

        System.out.println("[Stream Chat Bridge] Connecting to Twitch EventSub...");

        httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(WEBSOCKET_URL), this).exceptionally(error -> {
            System.err.println("[Stream Chat Bridge] EventSub connection failed: " + error.getMessage());
            return null;
        });
    }

    public void disconnect() {
        connected = false;

        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting");

            webSocket = null;
        }
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;

        System.out.println("[Stream Chat Bridge] EventSub WebSocket opened.");

        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);

        if (last) {
            String message = messageBuffer.toString();
            messageBuffer.setLength(0);

            try {
                handleMessage(message);
            } catch (Exception e) {
                System.err.println("[Stream Chat Bridge] Failed to handle EventSub message: " + e.getMessage());
            }
        }

        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected = false;

        System.out.println("[Stream Chat Bridge] EventSub disconnected: " + statusCode + " " + reason);

        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected = false;

        System.err.println("[Stream Chat Bridge] EventSub error: " + error.getMessage());
    }

    private void handleMessage(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);

        JsonObject metadata = root.getAsJsonObject("metadata");

        if (metadata == null || !metadata.has("message_type")) {
            return;
        }

        String type = metadata.get("message_type").getAsString();

        switch (type) {
            case "session_welcome" -> handleWelcome(root);
            case "notification" -> handleNotification(root);

            case "session_keepalive" -> {
            }

            case "session_reconnect" -> System.out.println("[Stream Chat Bridge] Twitch requested EventSub reconnect.");

            case "revocation" -> System.err.println("[Stream Chat Bridge] Twitch EventSub subscription revoked.");

            default -> {
            }
        }
    }

    private void handleWelcome(JsonObject root) {
        JsonObject session = root.getAsJsonObject("payload").getAsJsonObject("session");

        String sessionId = session.get("id").getAsString();

        connected = true;

        System.out.println("[Stream Chat Bridge] EventSub connected. Session: " + sessionId);

        Thread.startVirtualThread(() -> createChatSubscription(sessionId));
    }

    private void createChatSubscription(String sessionId) {
        try {
            JsonObject condition = new JsonObject();

            condition.addProperty("broadcaster_user_id", channelId);

            condition.addProperty("user_id", auth.getUserId());

            JsonObject transport = new JsonObject();
            transport.addProperty("method", "websocket");
            transport.addProperty("session_id", sessionId);

            JsonObject body = new JsonObject();
            body.addProperty("type", "channel.chat.message");
            body.addProperty("version", "1");
            body.add("condition", condition);
            body.add("transport", transport);

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(SUBSCRIPTIONS_URL)).header("Authorization", "Bearer " + auth.getAccessToken()).header("Client-Id", TwitchAuth.CLIENT_ID).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 202) {
                System.err.println("[Stream Chat Bridge] Failed to subscribe to Twitch chat. HTTP " + response.statusCode() + ": " + response.body());
                return;
            }

            System.out.println("[Stream Chat Bridge] Twitch chat subscription active.");

        } catch (Exception e) {
            System.err.println("[Stream Chat Bridge] Failed to create Twitch chat subscription: " + e.getMessage());
        }
    }

    private void handleNotification(JsonObject root) {
        JsonObject payload = root.getAsJsonObject("payload");

        if (payload == null) {
            return;
        }

        JsonObject subscription = payload.getAsJsonObject("subscription");

        if (subscription == null || !subscription.has("type") || !"channel.chat.message".equals(subscription.get("type").getAsString())) {
            return;
        }

        JsonObject event = payload.getAsJsonObject("event");

        if (event == null) {
            return;
        }

        String username = event.get("chatter_user_name").getAsString();

        String message = event.getAsJsonObject("message").get("text").getAsString();

        System.out.println("[Twitch] " + username + ": " + message);

        if (messageHandler != null) {
            messageHandler.accept(username, message);
        }
    }

    public boolean isConnected() {
        return connected;
    }
}