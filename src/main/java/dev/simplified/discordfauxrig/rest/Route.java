package dev.simplified.discordfauxrig.rest;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * The declarative REST route table for {@link LocalDiscordServer}: one constant per faked endpoint, pairing an
 * HTTP method and a path-matching regex with how the mock should respond. {@code start()} loads every route
 * from here, keeping the endpoint list in one readable place as the harness grows.
 *
 * <p>
 * Path patterns are matched against the query-stripped request path; a Discord {@code {snowflake}} path
 * parameter is written as {@code [^/]+} (one segment), or {@code \\d+} where the original endpoint was numeric.
 * The routes are registered in declaration order, ahead of {@link LocalDiscordServer}'s catch-all.
 */
enum Route {

    // Login prologue - gateway and identity reads
    SELF_USER("GET", "/users/@me", Kind.JSON, RouteBodies::userJson),
    USER_BY_ID("GET", "/users/\\d+", Kind.JSON, RouteBodies::actorUserJson),
    GATEWAY("GET", "/gateway", Kind.JSON, RouteBodies::gatewayJson),
    GATEWAY_BOT("GET", "/gateway/bot", Kind.JSON, RouteBodies::gatewayBotJson),
    APPLICATION_INFO("GET", "/oauth2/applications/@me", Kind.JSON, RouteBodies::applicationInfoJson),
    GUILD("GET", "/guilds/[^/]+", Kind.JSON, RouteBodies::guildJson),

    // Command registration - bulk-overwrite echo
    GLOBAL_COMMANDS("PUT", "/applications/[^/]+/commands", Kind.ECHO, null),
    GUILD_COMMANDS("PUT", "/applications/[^/]+/guilds/[^/]+/commands", Kind.ECHO, null),

    // Interaction responses - callback, reply, and followups (webhook + token)
    INTERACTION_CALLBACK("POST", "/interactions/[^/]+/[^/]+/callback", Kind.NO_CONTENT, null),
    CREATE_FOLLOWUP("POST", "/webhooks/[^/]+/[^/]+", Kind.JSON, RouteBodies::messageJson),
    EDIT_ORIGINAL_MESSAGE("PATCH", ".*/messages/@original", Kind.JSON, RouteBodies::messageJson),
    EDIT_FOLLOWUP("PATCH", "/webhooks/[^/]+/[^/]+/messages/\\d+", Kind.JSON, RouteBodies::messageJson),
    DELETE_FOLLOWUP("DELETE", "/webhooks/[^/]+/[^/]+/messages/[^/]+", Kind.NO_CONTENT, null),

    // Channel messages
    CREATE_CHANNEL_MESSAGE("POST", "/channels/[^/]+/messages", Kind.JSON, RouteBodies::messageJson),
    EDIT_CHANNEL_MESSAGE("PATCH", "/channels/\\d+/messages/\\d+", Kind.JSON, RouteBodies::messageJson),
    GET_CHANNEL_MESSAGE("GET", "/channels/\\d+/messages/\\d+", Kind.JSON, RouteBodies::messageJson),
    DELETE_CHANNEL_MESSAGE("DELETE", "/channels/\\d+/messages/\\d+", Kind.NO_CONTENT, null),
    GET_CHANNEL("GET", "/channels/\\d+", Kind.JSON, RouteBodies::channelJson),

    // Reactions
    GET_REACTIONS("GET", "/channels/\\d+/messages/\\d+/reactions/[^/]+", Kind.JSON, RouteBodies::reactionUsersJson),
    ADD_SELF_REACTION("PUT", "/channels/\\d+/messages/\\d+/reactions/[^/]+/@me", Kind.NO_CONTENT, null),
    DELETE_SELF_REACTION("DELETE", "/channels/\\d+/messages/\\d+/reactions/[^/]+/@me", Kind.NO_CONTENT, null),
    DELETE_USER_REACTION("DELETE", "/channels/\\d+/messages/\\d+/reactions/[^/]+/\\d+", Kind.NO_CONTENT, null),
    DELETE_EMOJI_REACTIONS("DELETE", "/channels/\\d+/messages/\\d+/reactions/[^/]+", Kind.NO_CONTENT, null),

    // Application emojis
    LIST_EMOJIS("GET", "/applications/[^/]+/emojis", Kind.JSON, RouteBodies::emptyEmojisJson),
    CREATE_EMOJI("POST", "/applications/[^/]+/emojis", Kind.JSON, RouteBodies::emojiJson);

    /** How a matched route produces its response. */
    enum Kind {

        /** Serialize the route's entity body with a {@code 200}. */
        JSON,

        /** Echo a command bulk-overwrite payload with synthetic ids ({@code 200}). */
        ECHO,

        /** Acknowledge with a {@code 204} and no body. */
        NO_CONTENT

    }

    private final String method;
    private final String pathRegex;
    private final Kind kind;
    private final @Nullable Function<RouteBodies, String> body;

    Route(@NotNull String method, @NotNull @Language("RegExp") String pathRegex, @NotNull Kind kind, @Nullable Function<RouteBodies, String> body) {
        this.method = method;
        this.pathRegex = pathRegex;
        this.kind = kind;
        this.body = body;
    }

    @NotNull Kind kind() {
        return this.kind;
    }

    /**
     * Whether the given request method and query-stripped path match this route.
     *
     * @param method the request method (e.g. {@code GET})
     * @param path the query-stripped request path
     * @return whether this route handles the request
     */
    boolean matches(@NotNull String method, @NotNull String path) {
        return this.method.equals(method) && path.matches(this.pathRegex);
    }

    /**
     * Renders this route's JSON body.
     *
     * @param bodies the response bodies for this run's identity
     * @return the serialized body, or {@code null} for a route with no entity body
     */
    @Nullable String body(@NotNull RouteBodies bodies) {
        return this.body == null ? null : this.body.apply(bodies);
    }

}
