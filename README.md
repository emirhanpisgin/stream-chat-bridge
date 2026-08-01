# Stream Chat Bridge

Stream Chat Bridge is a client-side Fabric mod that connects Minecraft chat with
Twitch and Kick. Messages from watched channels appear in Minecraft, and
Minecraft messages can be sent back to the selected channel using platform
prefixes.

## Requirements

- Minecraft version configured in `gradle.properties` (currently `26.2`)
- Fabric Loader `0.19.3` or newer
- Fabric API
- Java 25 or newer

## Installation

1. Install Fabric Loader and Fabric API for the supported Minecraft version.
2. Download the mod jar from a release, or build it with `gradlew.bat build`.
3. Put the resulting jar from `build/libs` in the Minecraft `mods` folder.
4. Start Minecraft with the Fabric profile.

## Getting Started

Press `F8` in-game to open the Stream Chat Bridge dashboard. You can also use
`/scb` or `/scb config`.

### Twitch

Open Twitch settings or use the dashboard login button. The mod opens Twitch's
device authorization page. Enter the displayed code, then return to Minecraft.

### Kick

Kick requires a developer application:

1. Open the [Kick Developer Portal](https://dev.kick.com/).
2. Create an application.
3. Set its redirect URL to:
   `http://localhost:17564/kick/callback`
4. In the application's Permissions or Scopes section, enable:
   - `user:read` - read the authenticated account
   - `channel:read` - look up watched channels
   - `chat:write` - send messages to Kick chat
   - `events:subscribe` - receive live chat events
5. Enter the application's Client ID and Client Secret in Kick settings.
6. Return to the dashboard and log in with Kick.

The Client Secret is stored locally and is never sent anywhere except Kick's
OAuth token endpoint.

## Commands

- `/scb` - open the dashboard
- `/scb config` - open the dashboard
- `/scb status` - show authentication, channel, connection, and outgoing-chat status
- `/scb watch twitch` - show the current Twitch channel
- `/scb watch twitch self` - watch your authenticated Twitch channel
- `/scb watch twitch <channel>` - watch another Twitch channel
- `/scb watch kick` - show the current Kick channel
- `/scb watch kick self` - watch your authenticated Kick channel
- `/scb watch kick <channel>` - watch another Kick channel

Watched channels are persistent. A blank channel means the authenticated
account's own channel. The selected channel controls both incoming messages and
outgoing messages for that platform.

## Sending Messages

When outgoing chat is enabled, type a message with one of these prefixes in
Minecraft chat:

- `!t hello` sends `hello` to Twitch
- `!k hello` sends `hello` to Kick

Prefixes, outgoing-chat toggles, and watched channels are configurable in the
platform settings screens.

## Incoming Message Formatting

Each platform has its own incoming format and platform label. Supported
placeholders are:

- `{platform}`
- `{username}`
- `{message}`

Formatting tags use Minecraft color names, for example:

```text
<dark_purple>[{platform}]<reset> <green>{username}<reset>: <white>{message}
```

The settings screens include a preview and color-tag insertion control.

## Configuration

The mod stores normal settings in:

```text
.minecraft/config/streamchatbridge.json
```

Authentication data is stored separately in the same config directory. Use the
dashboard's logout controls to clear tokens while keeping channel and display
settings. Resetting the Kick app also removes its Client ID and Client Secret.

## License

This project is available under the [MIT License](LICENSE).
