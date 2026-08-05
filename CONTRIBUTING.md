# Contributing to discord4j-fauxrig

Thank you for your interest in contributing! This document explains how to get
started, what to expect during the review process, and the conventions this
project follows.

## Table of Contents

- [Getting Started](#getting-started)
- [The one rule](#the-one-rule)
- [Making Changes](#making-changes)
  - [Branching Strategy](#branching-strategy)
  - [Code Style](#code-style)
  - [Commit Messages](#commit-messages)
  - [Testing](#testing)
- [Adding a REST route](#adding-a-rest-route)
- [Adding a dispatch](#adding-a-dispatch)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Reporting Issues](#reporting-issues)
- [Legal](#legal)

## Getting Started

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Git](https://git-scm.com/) | 2.x+ | For cloning and contributing |
| [IntelliJ IDEA](https://www.jetbrains.com/idea/) | Latest | Recommended IDE |

**No environment variables and no Discord bot token are required.** That is the
point of this project: everything runs offline against a localhost server.

```bash
git clone https://github.com/<your-username>/discord4j-fauxrig.git
cd discord4j-fauxrig
./gradlew build
```

Open the project root as a Gradle project. Annotation processing must be enabled
(the `io.github.simplified-dev:annotations` processor generates the `@Log` logger
fields and equality pairs).

## The one rule

**fauxrig must never depend on a bot framework or on any particular bot.** It
stands in for Discord, so its only compile dependencies are Discord4J itself, the
JetBrains annotations, Log4j2, and the Simplified annotation processor. A change
that introduces a dependency on a consumer - even a test-scoped one - defeats the
design and will not be merged.

The corollary: tests in this repository exercise the harness *as a server*. Tests
that need a real bot on the other end belong in the consuming project.

## Making Changes

### Branching Strategy

- Create a feature branch from `master` for your work.
- Use a descriptive branch name: `fix/modal-submit-payload`,
  `feat/thread-routes`, `docs/dispatch-table`.

```bash
git checkout -b feat/my-feature master
```

### Code Style

- **Annotations** - `@NotNull` / `@Nullable` from `org.jetbrains.annotations` on
  all public method parameters and return types.
- **Logging** - `@Log` from the Simplified annotation processor; never
  `System.out`. Respect the level contract documented in the README (INFO for
  lifecycle, DEBUG for per-request detail, TRACE for every dispatch, WARN for
  anything Discord itself would reject).
- **Sequenced collections** - `.getFirst()` / `.getLast()`, never `.get(0)` or
  `.get(size() - 1)`.
- **Control flow** - omit braces on single-line bodies; add them when the body
  wraps.

#### Javadoc

- **Class level** - noun phrase describing what the type is.
- **Method level** - active verb, third person singular.
- **Tags** - `@param`, `@return`, `@throws` where applicable. Lowercase sentence
  fragments, no trailing period. Single space after the param name.
- **Punctuation** - only single hyphens (` - `) as separators, never em dashes.
- Never use `@author` or `@since`.

### Commit Messages

Write clear, concise commit messages that describe *what* changed and *why*.

```
Serve an application owner so isDeveloper resolves

The mock returned no owner on /oauth2/applications/@me, so any consumer
guarding on developer status saw every user as non-developer.
```

- Use the imperative mood ("Add", "Fix", "Serve").
- Keep the subject line under 72 characters.
- Add a body when the *why* isn't obvious from the subject.

### Testing

Tests use JUnit 5 (Jupiter):

```bash
./gradlew test
./gradlew test -Dharness.debug=true   # elevate the per-request firehose to INFO
```

Every test must pass with no network access. If a change makes the suite depend
on reaching discord.com, it is a bug in the change.

## Adding a REST route

When a consumer hits an endpoint the mock does not model, `LocalDiscordServer`
logs a WARN naming the method and path. To add it:

1. Add the route to `rest/Route`.
2. Add its body method to `rest/RouteBodies`, built from discord-json builders
   rather than hand-written JSON strings - the builders keep the shape honest
   against the Discord4J version in `gradle/libs.versions.toml`. Any user or
   message shape it needs belongs in `entity/DiscordEntities`, which the
   gateway half also builds on.
3. Cover the payload in `rest/RouteBodiesTest` and the endpoint in
   `test/LocalDiscordServerTest`.

## Adding a dispatch

1. Add the builder to the appropriate `gateway/dispatch/*` class
   (`InteractionDispatches`, `ComponentDispatches`, `MessageDispatches`,
   `LifecycleDispatches`).
2. Expose it from `DispatchFactory` so consumers reach it via
   `harness.dispatches()`.
3. Cover the payload shape in `test/DispatchFactoryTest`.
4. Add a row to the dispatch table in the README.

## Submitting a Pull Request

1. **Push your branch** to your fork.
2. **Open a Pull Request** against `master`.
3. **In the PR description**, include a summary of the change and its motivation,
   plus the steps to verify it.
4. **Respond to review feedback.** PRs may go through one or more rounds of
   review before being merged.

### What gets reviewed

- That the change keeps fauxrig framework-agnostic (see [The one rule](#the-one-rule)).
- Fidelity to the real Discord API: status codes, payload shape, and the errors
  Discord itself would return.
- Whether a new route or dispatch is covered by a test and documented in the
  README tables.

## Reporting Issues

Use [GitHub Issues](https://github.com/simplified-dev/discord4j-fauxrig/issues)
to report bugs or request features.

When reporting a bug, include:

- **Java version** (`java --version`)
- **Discord4J version** (check `gradle/libs.versions.toml`)
- **Operating system**
- **Full error stacktrace** (if applicable)
- **Steps to reproduce**
- **Expected vs. actual behavior**

A run with `-Dharness.debug=true` attached is worth more than a description; it
shows exactly which REST calls the bot made and where the pathway stopped.

## Legal

By submitting a pull request, you agree that your contributions are licensed
under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0),
the same license that covers this project.
