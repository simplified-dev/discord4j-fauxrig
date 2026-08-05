# discord4j-fauxrig

A **fake Discord** for [Discord4J](https://github.com/Discord4J/Discord4J) bots - a localhost REST server plus
an in-JVM gateway - that you connect a bot to and drive **fully offline**. A slash command, a component or
modal interaction, a right-click user/message command, or a message event executes the **real** code path
(`Listener -> apply/process -> callback -> reply/edit`) with no network, no token, and no human: you push a
simulated gateway event and assert on the REST calls the bot makes in response.

It depends only on Discord4J and knows nothing about any particular bot or framework. The dependency arrow
points one way: **consumer → fauxrig**.

## Table of Contents

- [Installation](#installation)
- [Connecting a bot](#connecting-a-bot)
- [FauxDiscord API](#fauxdiscord-api)
- [Building dispatches](#building-dispatches-dispatchfactory-via-dispatches)
- [Inspecting what the bot sent](#inspecting-what-the-bot-sent-renderedmessage)
- [Ids and tokens](#ids-and-tokens)
- [Debugging](#debugging)
- [Architecture](#architecture)
- [Contributing](#contributing)
- [License](#license)

## Installation

| Requirement | Version |
|-------------|---------|
| [JDK](https://adoptium.net/) | **21+** |
| [Discord4J](https://github.com/Discord4J/Discord4J) | **3.3.1** |

Add it as a test dependency:

```kotlin
repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    testImplementation("com.github.simplified-dev:discord4j-fauxrig:master-SNAPSHOT")
}
```

## Connecting a bot

Point the bot's REST client at the local server and hand it the fake gateway. Both are opt-in seams that are
no-op by default:

```java
FauxDiscord discord = new FauxDiscord();                    // fresh REST mock + fake gateway
// wire your bot's config to it:
//   .withApiBaseUrl(discord.baseUrl())                      // REST -> localhost mock
//   .withGatewayClientFactory(options -> discord.gateway()) // gateway -> in-JVM fake
//   .withRestReactorResources(plaintextRest)                // plaintext http to localhost (no TLS)
// then boot the bot and wait for it to report connected.
```

The exact wiring calls depend on your bot's configuration layer; what fauxrig needs is only that Discord4J's
`RestClient` targets `baseUrl()` over plaintext HTTP and that the `GatewayClient` is `gateway()`.

Drive events by emitting a dispatch built by the factory, then assert on the recorded REST traffic:

```java
discord.gateway().emit(discord.dispatches().slashCommand("ping"));  // push /ping
RecordedRequest reply = discord.awaitInteractionReply();            // last PATCH .../messages/@original
assertTrue(reply.bodyContains("pong"));
```

`FauxDiscord` is `AutoCloseable`; use it in try-with-resources so the server and gateway are torn down.

## FauxDiscord API

| Call | Does |
|---|---|
| `baseUrl()` | REST base url of the localhost mock (for your bot's config) |
| `gateway()` | the fake gateway; `gateway().emit(dispatch)` pushes a raw dispatch |
| `dispatches()` | the `DispatchFactory` (see below) |
| `config()` | the `FauxConfig` identity backing this run |
| `requests()` | all recorded requests, in order |
| `awaitRequest(predicate[, timeout])` | last request matching the predicate (default 10s) |
| `awaitInteractionReply()` | last `PATCH .../messages/@original` |
| `awaitInteractionCallback()` | last `POST .../callback` |
| `callbackCount(tokenContains)` | how many times an interaction was acknowledged (Discord permits one) |
| `await(condition, timeout, message)` | generic poll primitive, for your own readiness waits |
| `close()` | stop the server and fake gateway |

`RecordedRequest` exposes `method()`, `path()`, `body()`, and `bodyContains()`.

## Building dispatches (`DispatchFactory`, via `dispatches()`)

| Builder | Simulates |
|---|---|
| `slashCommand(name, options...)` | `/name` chat-input interaction (type 2) |
| `slashSubCommand(parent, group, sub, options...)` | `/parent [group] sub` subcommand interaction |
| `button(messageId, customId)` | button click (component interaction, type 3) |
| `selectMenu(messageId, customId, values...)` | string select interaction |
| `modalSubmit(messageId, modalId, inputId, value)` | modal submit (type 5) |
| `modalSubmitValues(messageId, modalId, radioId, radioValue, checkboxId, checkboxChecked)` | modal submit carrying radio + checkbox state |
| `messageCreate(messageId)` | bot-authored `MESSAGE_CREATE` |
| `userCommand(name, targetUserId)` | right-click user command (type 2, data.type 2) |
| `messageCommand(name, targetMessageId)` | right-click message command (type 2, data.type 3) |

Interactions with no `guild_id` run as private-channel interactions, skipping bot-permission and channel
resolution - the low-friction default. Slash options are supplied as `SlashOption` leaves
(`SlashOption.text(name, value)`); the factory places them at the correct depth for flat commands, bare
subcommands, or grouped subcommands, matching the `parent [group] sub` tree a consumer resolves.

## Inspecting what the bot sent (`RenderedMessage`)

`RenderedMessage` parses a recorded outbound payload into a structured view, so assertions read against
content, embeds, and components rather than raw JSON substrings. Use it when `bodyContains` is too blunt -
for example to assert a specific button's custom id, or that a select menu carries the expected options.

## Ids and tokens

`FauxConfig` (built via `FauxConfig.builder().build()`) holds the canonical ids and a
structurally-valid fake token (its first segment base64-decodes to the bot id, matching
`TokenUtil.getSelfId`). It is passed to `FauxDiscord`, `LocalDiscordServer`, and `DispatchFactory` at
construction; reach it via `discord.config()`. Command ids are derived deterministically from the command
name (`config.commandId(name)`) so the mock's bulk-overwrite echo and simulated interactions agree. The REST
mock mints `config.getReplyMessageId()` for every reply and followup message. Pass a customized
`FauxConfig` to change the identity.

## Debugging

fauxrig logs through Log4j2 (no `System.out`). By level:

- **INFO** - harness construction and teardown. On by default.
- **DEBUG** - REST mock bind/stop, per-request `[mock] METHOD /path body`, and gateway handshake/close.
- **TRACE** - every dispatch the factory builds and every event emitted into the pipeline.
- **WARN** - conditions Discord itself would reject or report: an interaction acknowledged twice
  (error 40060), a REST endpoint the mock does not model, or a swallowed mock error.
- **ERROR** - a wait that timed out, including the recorded requests at that point.

Raise the logger in your `log4j2-test.xml`:

```xml
<Logger name="dev.simplified.discordfauxrig" level="TRACE"/>
```

`-Dharness.debug=true` elevates the per-request `[mock] ...` firehose to INFO without touching the config -
the fastest way to see how far a pathway got:

```bash
./gradlew test -Dharness.debug=true
```

## Architecture

```
src/main/java/dev/simplified/discordfauxrig/
├── FauxDiscord.java             # the aggregate: owns the pieces below, exposes the assertion API
├── FauxConfig.java              # the deterministic identity (ids, fake token, command-id scheme)
├── rest/
│   ├── LocalDiscordServer.java  # reactor-netty server implementing the REST routes a bot touches
│   ├── Route.java               # the route table
│   ├── RecordedRequest.java     # one captured request
│   └── RenderedMessage.java     # parses an outbound payload into a structured view
├── gateway/
│   ├── FauxGatewayClient.java   # in-JVM GatewayClient (no socket); replays a handshake, accepts pushes
│   ├── DispatchFactory.java     # builds simulated dispatches for an identity
│   ├── SlashOption.java         # a resolved leaf option
│   └── dispatch/                # ComponentDispatches, InteractionDispatches, Interactions,
│                                # LifecycleDispatches, MessageDispatches
└── json/
    └── DiscordEntities.java     # JSON entity factory (users, channels, guilds, messages)
```

`FauxGatewayClient` replays a `READY` + `GatewayStateChange.connected()` handshake so login completes, then
lets you push further dispatches into Discord4J's *real* pipeline (`DispatchHandlers` -> events -> entities
-> listeners). Nothing is stubbed downstream of the socket.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE.md).
