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

    private static final long[] RECONNECT_DELAYS_MS = {1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L};

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final BiConsumer<String, String> messageHandler;

    private volatile Pusher pusher;

    private volatile Channel channel;

    private volatile String chatroomId;

    private volatile String username;

    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    private volatile Consumer<ConnectionState> stateListener;

    private volatile boolean shouldStayConnected = false;

    private volatile boolean shuttingDown = false;

    private volatile boolean reconnectScheduled = false;

    private volatile int reconnectAttempt = 0;

    private volatile long connectionGeneration = 0;

    public KickChatClient(BiConsumer<String, String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    /*
     * Connection
     */

    public synchronized boolean connect(String username) {
        if (shuttingDown) {
            return false;
        }

        String normalized = normalizeUsername(username);

        if (normalized == null) {
            System.err.println("[Stream Chat Bridge] Cannot connect to Kick chat: username is missing.");

            return false;
        }

        shouldStayConnected = true;

        this.username = normalized;

        reconnectScheduled = false;

        resetReconnectBackoff();

        connectionGeneration++;

        cleanupConnection();

        return connectInternal(normalized, connectionGeneration);
    }

    private boolean connectInternal(String username, long generation) {
        if (shuttingDown || !shouldStayConnected || generation != connectionGeneration) {

            return false;
        }

        setConnectionState(ConnectionState.CONNECTING);

        String resolvedChatroomId = findChatroomId(username);

        if (generation != connectionGeneration || shuttingDown || !shouldStayConnected) {

            return false;
        }

        if (resolvedChatroomId == null) {
            setConnectionState(ConnectionState.DISCONNECTED);

            scheduleReconnect();

            return false;
        }

        chatroomId = resolvedChatroomId;

        try {
            PusherOptions options = new PusherOptions().setCluster(PUSHER_CLUSTER);

            Pusher newPusher = new Pusher(PUSHER_KEY, options);

            pusher = newPusher;

            newPusher.connect(new ConnectionEventListener() {

                @Override
                public void onConnectionStateChange(ConnectionStateChange change) {
                    handlePusherStateChange(newPusher, generation, change);
                }

                @Override
                public void onError(String message, String code, Exception exception) {
                    handlePusherError(newPusher, generation, message, code, exception);
                }
            }, com.pusher.client.connection.ConnectionState.ALL);

            String channelName = "chatrooms." + chatroomId + ".v2";

            Channel newChannel = newPusher.subscribe(channelName);

            channel = newChannel;

            newChannel.bind(CHAT_EVENT, new SubscriptionEventListener() {

                @Override
                public void onEvent(com.pusher.client.channel.PusherEvent event) {
                    if (generation != connectionGeneration) {

                        return;
                    }

                    handleChatEvent(event.getData());
                }
            });

            System.out.println("[Stream Chat Bridge] Listening to Kick chat: " + channelName);

            return true;

        } catch (Exception e) {
            if (generation != connectionGeneration) {

                return false;
            }

            System.err.println("[Stream Chat Bridge] Could not connect to Kick chat: " + e.getMessage());

            cleanupConnection();

            setConnectionState(ConnectionState.DISCONNECTED);

            scheduleReconnect();

            return false;
        }
    }

    /*
     * Pusher events
     */

    private synchronized void handlePusherStateChange(Pusher source, long generation, ConnectionStateChange change) {
        if (generation != connectionGeneration || source != pusher) {

            return;
        }

        System.out.println("[Stream Chat Bridge] Kick chat connection: " + change.getPreviousState() + " -> " + change.getCurrentState());

        String state = change.getCurrentState().name();

        switch (state) {
            case "CONNECTED" -> {
                reconnectScheduled = false;

                resetReconnectBackoff();

                setConnectionState(ConnectionState.CONNECTED);
            }

            case "CONNECTING", "RECONNECTING" -> setConnectionState(ConnectionState.CONNECTING);

            case "DISCONNECTED" -> {
                setConnectionState(ConnectionState.DISCONNECTED);

                scheduleReconnect();
            }

            default -> {
            }
        }
    }

    private synchronized void handlePusherError(Pusher source, long generation, String message, String code, Exception exception) {
        if (generation != connectionGeneration || source != pusher) {

            return;
        }

        System.err.println("[Stream Chat Bridge] Kick Pusher error" + (code != null ? " [" + code + "]" : "") + ": " + message);

        if (exception != null) {
            System.err.println("[Stream Chat Bridge] Kick Pusher exception: " + exception.getMessage());
        }

        /*
         * Pusher may recover from an error itself.
         * We only start our own reconnect cycle once it actually
         * transitions to DISCONNECTED.
         */
    }

    /*
     * Automatic reconnect
     */

    private synchronized void scheduleReconnect() {
        if (shuttingDown || !shouldStayConnected || reconnectScheduled) {

            return;
        }

        String targetUsername = username;

        if (targetUsername == null || targetUsername.isBlank()) {

            return;
        }

        long delay = RECONNECT_DELAYS_MS[Math.min(reconnectAttempt, RECONNECT_DELAYS_MS.length - 1)];

        reconnectAttempt++;

        reconnectScheduled = true;

        System.out.println("[Stream Chat Bridge] Kick connection lost. Reconnecting in " + formatDelay(delay) + "...");

        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return;
            }

            synchronized (this) {
                if (shuttingDown || !shouldStayConnected) {

                    reconnectScheduled = false;

                    return;
                }

                if (connectionState != ConnectionState.DISCONNECTED) {

                    reconnectScheduled = false;

                    return;
                }

                reconnectScheduled = false;

                connectionGeneration++;

                long generation = connectionGeneration;

                cleanupConnection();

                System.out.println("[Stream Chat Bridge] Reconnecting to Kick chat: " + targetUsername);

                connectInternal(targetUsername, generation);
            }
        });
    }

    private synchronized void resetReconnectBackoff() {
        reconnectAttempt = 0;
    }

    /*
     * Manual disconnect
     */

    public synchronized void disconnect() {
        shouldStayConnected = false;

        reconnectScheduled = false;

        resetReconnectBackoff();

        connectionGeneration++;

        cleanupConnection();

        chatroomId = null;

        username = null;

        setConnectionState(ConnectionState.DISCONNECTED);
    }

    /*
     * Shutdown
     */

    public synchronized void shutdown() {
        shuttingDown = true;

        shouldStayConnected = false;

        reconnectScheduled = false;

        resetReconnectBackoff();

        connectionGeneration++;

        cleanupConnection();

        chatroomId = null;

        username = null;

        setConnectionState(ConnectionState.DISCONNECTED);
    }

    /*
     * Cleanup
     */

    private void cleanupConnection() {
        Channel oldChannel = channel;

        channel = null;

        if (oldChannel != null) {
            try {
                oldChannel.unbind(CHAT_EVENT, null);
            } catch (Exception ignored) {
            }
        }

        Pusher oldPusher = pusher;

        pusher = null;

        if (oldPusher != null) {
            try {
                oldPusher.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    /*
     * Chatroom lookup
     */

    private String findChatroomId(String username) {
        try {
            String normalized = normalizeUsername(username);

            if (normalized == null) {
                return null;
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

    private static String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }

        String normalized = username.trim();

        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }

        normalized = normalized.trim();

        return normalized.isBlank() ? null : normalized;
    }

    /*
     * Incoming messages
     */

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

            if (messageHandler != null) {
                messageHandler.accept(username, message);
            }

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

    /*
     * State
     */

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

    public String getUsername() {
        return username;
    }

    private static String formatDelay(long delayMs) {
        long seconds = delayMs / 1_000L;

        return seconds + (seconds == 1 ? " second" : " seconds");
    }
}