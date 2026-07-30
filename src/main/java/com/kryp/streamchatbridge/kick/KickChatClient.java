package com.kryp.streamchatbridge.kick;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionStateChange;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class KickChatClient {

    public enum ConnectionState {
        CONNECTING, CONNECTED, DISCONNECTED
    }

    private static final String PUSHER_KEY = "32cbd69e4b950bf97679";

    private static final String PUSHER_CLUSTER = "us2";

    private static final String CHAT_EVENT = "App\\Events\\ChatMessageEvent";

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final BiConsumer<String, String> messageHandler;

    private Pusher pusher;
    private Channel channel;

    private volatile String chatroomId;

    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    private volatile Consumer<ConnectionState> stateListener;

    public KickChatClient(BiConsumer<String, String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public synchronized boolean connect(String username) {
        if (username == null || username.isBlank()) {
            System.err.println("[Stream Chat Bridge] Cannot connect to Kick chat: username is missing.");

            return false;
        }

        disconnect();

        setConnectionState(ConnectionState.CONNECTING);

        String resolvedChatroomId = findChatroomId(username);

        if (resolvedChatroomId == null) {
            setConnectionState(ConnectionState.DISCONNECTED);

            return false;
        }

        chatroomId = resolvedChatroomId;

        try {
            PusherOptions options = new PusherOptions().setCluster(PUSHER_CLUSTER);

            pusher = new Pusher(PUSHER_KEY, options);

            pusher.connect(new ConnectionEventListener() {

                @Override
                public void onConnectionStateChange(ConnectionStateChange change) {
                    System.out.println("[Stream Chat Bridge] Kick chat connection: " + change.getPreviousState() + " -> " + change.getCurrentState());

                    switch (change.getCurrentState()) {
                        case CONNECTED -> setConnectionState(ConnectionState.CONNECTED);

                        case CONNECTING -> setConnectionState(ConnectionState.CONNECTING);

                        case DISCONNECTED -> setConnectionState(ConnectionState.DISCONNECTED);

                        default -> {
                        }
                    }
                }

                @Override
                public void onError(String message, String code, Exception exception) {
                    System.err.println("[Stream Chat Bridge] Kick Pusher error" + (code != null ? " [" + code + "]" : "") + ": " + message);

                    if (exception != null) {
                        exception.printStackTrace();
                    }
                }
            }, com.pusher.client.connection.ConnectionState.ALL);

            String channelName = "chatrooms." + chatroomId + ".v2";

            channel = pusher.subscribe(channelName);

            channel.bind(CHAT_EVENT, new SubscriptionEventListener() {

                @Override
                public void onEvent(com.pusher.client.channel.PusherEvent event) {
                    handleChatEvent(event.getData());
                }
            });

            System.out.println("[Stream Chat Bridge] Listening to Kick chat: " + channelName);

            return true;

        } catch (Exception e) {
            System.err.println("[Stream Chat Bridge] Could not connect to Kick chat: " + e.getMessage());

            disconnect();

            return false;
        }
    }

    public synchronized void disconnect() {
        if (channel != null) {
            try {
                channel.unbind(CHAT_EVENT, null);
            } catch (Exception ignored) {
            }

            channel = null;
        }

        if (pusher != null) {
            try {
                pusher.disconnect();
            } catch (Exception ignored) {
            }

            pusher = null;
        }

        chatroomId = null;

        setConnectionState(ConnectionState.DISCONNECTED);
    }

    private String findChatroomId(String username) {
        try {
            String normalized = username.trim();

            if (normalized.startsWith("@")) {
                normalized = normalized.substring(1);
            }

            URI uri = URI.create("https://kick.com/api/v2/channels/" + normalized + "/chatroom");

            HttpRequest request = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(10)).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36").header("Referer", "https://kick.com/").GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("[Stream Chat Bridge] Kick chatroom lookup failed. HTTP " + response.statusCode() + ": " + response.body());

                return null;
            }

            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);

            if (json == null || !json.has("id") || json.get("id").isJsonNull()) {

                System.err.println("[Stream Chat Bridge] Kick chatroom lookup returned no ID.");

                return null;
            }

            String id = json.get("id").getAsString();

            System.out.println("[Stream Chat Bridge] Kick chatroom ID: " + id);

            return id;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return null;

        } catch (IOException | RuntimeException e) {
            System.err.println("[Stream Chat Bridge] Kick chatroom lookup failed: " + e.getMessage());

            return null;
        }
    }

    private void handleChatEvent(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }

        try {
            JsonObject data = GSON.fromJson(raw, JsonObject.class);

            if (data == null) {
                return;
            }

            String message = readString(data, "content");

            if (message == null || message.isBlank()) {
                return;
            }

            if (!data.has("sender") || !data.get("sender").isJsonObject()) {

                return;
            }

            JsonObject sender = data.getAsJsonObject("sender");

            String username = readString(sender, "username");

            if (username == null || username.isBlank()) {
                return;
            }

            messageHandler.accept(username, message);

        } catch (Exception e) {
            System.err.println("[Stream Chat Bridge] Could not process Kick chat message: " + e.getMessage());
        }
    }

    private static String readString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {

            return null;
        }

        return object.get(key).getAsString();
    }

    private void setConnectionState(ConnectionState state) {
        if (connectionState == state) {
            return;
        }

        connectionState = state;

        Consumer<ConnectionState> listener = stateListener;

        if (listener != null) {
            listener.accept(state);
        }
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }

    public void setStateListener(Consumer<ConnectionState> listener) {
        stateListener = listener;
    }

    public String getChatroomId() {
        return chatroomId;
    }
}