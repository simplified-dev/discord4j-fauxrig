# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

Java 21, Gradle 9.4+. Standalone repo with its own `gradlew`; also usable as an included build named
`discord4j-fauxrig` within the `Simplified-Dev` monorepo.

```bash
# Build (from this directory)
./gradlew build

# Run tests
./gradlew test

# Run tests with the per-request REST firehose elevated to INFO
./gradlew test -Dharness.debug=true

# Or, from the monorepo root:
./gradlew :discord4j-fauxrig:test
```

**No environment variables and no Discord token are required** - that is the entire point of this project.
A test that needs network access to pass is a bug in the test.

## What this is

A fake Discord for Discord4J bots: a localhost REST server plus an in-JVM gateway. A consumer points its
bot's `RestClient` at `baseUrl()` and swaps in `gateway()` as its `GatewayClient`, then pushes simulated
dispatches and asserts on the REST calls the bot makes in response. Everything downstream of the socket is
Discord4J's **real** pipeline - `DispatchHandlers` -> events -> entities -> listeners. Nothing is stubbed
there.

```
FauxDiscord (AutoCloseable aggregate, one fresh deployment per instance)
    ├── FauxConfig             - the deterministic identity shared by every piece
    ├── DiscordEntities        - typed discord-json immutables, shared by both halves
    ├── LocalDiscordServer     - reactor-netty REST server; records every request
    │       ├── Route          - package-private enum: the declarative route table
    │       └── RouteBodies    - package-private: the canned JSON each route returns
    └── FauxGatewayClient      - in-JVM GatewayClient, no socket
            └── DispatchFactory        - builds simulated dispatches for the identity
                    └── gateway/dispatch/*  - the per-kind dispatch builders
```

## The hard architectural rule

**fauxrig must never depend on a bot framework, on a consumer, or on any particular bot.** Its only compile
dependencies are Discord4J, JetBrains annotations, Log4j2, and the Simplified annotation processor. That
independence is the whole design - it is what lets any Discord4J bot use it, and it is what let this code be
extracted from `discord4j-framework` in the first place.

Practical consequences when editing here:
- No `implementation`/`api` dependency on a consumer, and no test-scoped one either.
- Tests in `src/test/` exercise the harness **as a server**: they assert on payload shape, route matching,
  and entity JSON. A test that needs a real bot on the other end belongs in the consuming repository.
- `FauxDiscord` deliberately exposes seams (`baseUrl()`, `gateway()`, `dispatches()`) rather than booting
  anything itself. It has no idea what a command or a response is.

## Key types

**`FauxDiscord`** - the aggregate and the public API surface. Owns the config, entities, server, gateway,
and dispatch factory; each instance is one fresh deployment. Beyond the seams it exposes the assertion
helpers: `requests()`, `awaitRequest(predicate[, timeout])`, `awaitInteractionReply()` (last
`PATCH .../messages/@original`), `awaitInteractionCallback()` (last `POST .../callback`),
`callbackCount(tokenContains)`, and the generic `await(condition, timeout, message)` poll primitive.

`await` polls every 25ms rather than awaiting a signal, because the awaited conditions are external state
mutated on the bot's reactive and netty threads with nothing to latch onto. A bounded poll is the pragmatic
harness primitive here - do not "fix" it into a latch without a signal to latch onto. On timeout it throws
`IllegalStateException` carrying the recorded requests, which is usually the whole diagnosis.

**`FauxConfig`** - the deterministic identity: ids, a structurally-valid fake token (its first segment
base64-decodes to the bot id, matching `TokenUtil.getSelfId`, or Discord4J's login rejects it), and the
command-id scheme. `config.commandId(name)` derives ids from the command name so the mock's bulk-overwrite
echo and later simulated interactions agree on the same id. `debug` defaults to
`Boolean.getBoolean("harness.debug")`. Built via `FauxConfig.builder().build()` and threaded into the
server, entities, and dispatch factory at construction.

**`LocalDiscordServer`** (`rest/`) - a reactor-netty HTTP server serving the endpoints a bot touches, and
recording every request into `RecordedRequest` (`method()`, `path()`, `body()`, `bodyContains()`). Unmodelled
endpoints fall through to a catch-all that logs a WARN naming the method and path - that WARN is the intended
signal when a consumer reaches something new.

**`Route`** (`rest/`) - a package-private enum, one constant per faked endpoint, pairing an HTTP method and a
path regex with a `Kind` (`JSON` serializes an entity body with 200, `ECHO` echoes a command bulk-overwrite
with synthetic ids, `NO_CONTENT` acknowledges with 204). Registered in declaration order ahead of the
catch-all, so **order matters**: a broad pattern placed above a narrow one shadows it. Snowflake path params
are `[^/]+`, or `\\d+` where the real endpoint is numeric.

**`DiscordEntities`** (`entity/`) - the typed discord-json immutables (users, messages) both halves are built
from: `gateway/` embeds them in dispatches, `rest/` serializes them. Everything comes from **discord-json
builders**, never hand-written JSON strings. This is deliberate: the builders fail to compile when the pinned
Discord4J version changes a payload shape, so the fake cannot drift silently from the real API.

**`RouteBodies`** (`rest/`) - package-private, one method per JSON-returning `Route`, each serializing a
`DiscordEntities` immutable with Discord4J's mapper. It is package-private for the same reason `Route` is:
nothing outside `rest/` has any business reaching a raw response body. The `gateway/` and `rest/` halves use
completely disjoint parts of the entity layer - gateway takes typed entities, rest takes serialized bodies -
and this split is what keeps that boundary visible.

**`FauxGatewayClient`** (`gateway/`) - implements `discord4j.gateway.GatewayClient` with no socket. `execute`
replays the handshake (a `READY` plus `GatewayStateChange.connected()`) into a `Sinks.many().replay().all()`
dispatch sink, then parks on `Mono.never()`. The replay sink matters: a consumer that subscribes late still
receives the handshake. `emit(dispatch)` pushes further dispatches into the live pipeline. `receiver()`,
`stateEvents()`, and friends are empty by design - outbound gateway traffic is not modelled.

**`DispatchFactory`** (`gateway/`) - builds the simulated dispatches for an identity, delegating to the
`gateway/dispatch/*` classes. `SlashOption` is a resolved leaf option; the factory places options at the
correct depth for flat commands, bare subcommands, or grouped subcommands. Interactions built without a
`guild_id` run as private-channel interactions, which skips bot-permission and channel resolution - the
low-friction default.

**`RenderedMessage`** (`rest/`) - parses a recorded outbound payload into a structured view of content,
embeds, and components, so consumers assert against structure instead of JSON substrings.

## Logging contract

Log through Log4j2 via `@Log` (from the Simplified annotation processor); never `System.out`. The levels are
a contract consumers rely on, documented in the README:

- **INFO** - harness construction and teardown
- **DEBUG** - REST mock bind/stop, per-request `[mock] METHOD /path body`, gateway handshake and close
- **TRACE** - every dispatch built and every dispatch emitted
- **WARN** - anything Discord itself would reject or report: double-acknowledged interaction (error 40060),
  an unmodelled endpoint, a swallowed mock error
- **ERROR** - a wait that timed out

`-Dharness.debug=true` elevates the per-request firehose to INFO without editing `log4j2-test.xml`.

## Conventions

- **Javadoc** - single hyphens ` - ` as separators, never em dashes. Class docs are noun phrases, method docs
  third-person verbs. No `@author`/`@since`. Import link targets rather than inlining FQNs (except in
  `package-info.java`, which uses fully-qualified refs and carries no imports).
- **Annotations** - `@NotNull`/`@Nullable` from `org.jetbrains.annotations` on public params and returns.
- **Sequenced collections** - `.getFirst()`/`.getLast()`, never `.get(0)`/`.get(size() - 1)`.
- **Control flow** - omit braces on single-line bodies; add them when the body wraps.
- **Adding a route**: add the constant to `Route`, the body method to `RouteBodies` (reaching into
  `DiscordEntities` for any user/message shape), and cover it in `RouteBodiesTest` plus `LocalDiscordServerTest`.
- **Adding a dispatch**: add the builder to the right `gateway/dispatch/*` class, expose it from
  `DispatchFactory`, cover the payload shape in `DispatchFactoryTest`, add a README table row.
