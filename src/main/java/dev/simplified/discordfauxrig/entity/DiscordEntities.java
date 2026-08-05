package dev.simplified.discordfauxrig.entity;

import dev.simplified.discordfauxrig.FauxConfig;
import discord4j.common.JacksonResources;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.PartialUserData;
import discord4j.discordjson.json.UserData;
import discord4j.discordjson.possible.Possible;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplified.annotations.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * The typed discord-json immutables fauxrig fabricates - the users and messages the dispatch factory embeds
 * in gateway events, and that the REST bodies are serialized from. Every one is built through Discord4J's own
 * immutable builders, so a payload-shape change in a Discord4J upgrade fails the compile here rather than
 * drifting silently from the real API.
 * <p>
 * Shared by both halves of the fake: {@code gateway/} embeds these directly, {@code rest/} serializes them.
 * Keeping the user and message shapes in one place is what stops the two from disagreeing.
 */
@RequiredArgsConstructor
public final class DiscordEntities {

    // Discord4J's own mapper, so the discord-json immutables below serialize exactly as the bot expects.
    private final ObjectMapper mapper = JacksonResources.create().getObjectMapper();
    private final FauxConfig config;

    /** Discord4J's mapper, shared so every serialization path uses the same configuration. */
    public @NotNull ObjectMapper mapper() {
        return this.mapper;
    }

    /** The bot's self user, reused as {@code GET /users/@me}, as the author of served messages, and in READY. */
    public @NotNull UserData botUser() {
        return user(this.config.getBotId(), "TestBot", "0000", true);
    }

    /** The non-bot user that acts as the invoker of simulated interactions. */
    public @NotNull UserData actorUser() {
        return user(this.config.getUserId(), "tester", "0001", false);
    }

    /** The partial user served as the application owner, so {@code DiscordReference#isDeveloper} resolves true for it. */
    public @NotNull PartialUserData developerUser() {
        return PartialUserData.builder()
            .id(this.config.getDeveloperUserId())
            .username("developer")
            .discriminator("0003")
            .build();
    }

    /**
     * A non-bot user targeted by a right-click user command.
     *
     * @param id the targeted user id
     * @return the target user
     */
    public @NotNull UserData targetUser(long id) {
        return user(id, "target", "0002", false);
    }

    /**
     * The fabricated message a component or modal interaction references (the cached message the component
     * lives on).
     *
     * @param messageId the message id (must match a cached response)
     * @return the interaction message
     */
    public @NotNull MessageData interactionMessage(long messageId) {
        return message(messageId, "press it");
    }

    /**
     * A bot-authored message with the given id and content, carrying a real timestamp.
     *
     * @param messageId the message id
     * @param content the message content
     * @return the message
     */
    public @NotNull MessageData message(long messageId, @NotNull String content) {
        return MessageData.builder()
            .id(messageId)
            .channelId(this.config.getChannelId())
            .author(this.botUser())
            .content(content)
            .timestamp(messageTimestamp())
            .editedTimestamp(Optional.empty())
            .tts(false)
            .mentionEveryone(false)
            .pinned(false)
            .type(0)
            .build();
    }

    @SuppressWarnings("deprecation") // discriminator is a required field on UserData even though deprecated
    private static @NotNull UserData user(long id, @NotNull String username, @NotNull String discriminator, boolean bot) {
        return UserData.builder()
            .id(id)
            .username(username)
            .discriminator(discriminator)
            .globalName(bot ? Optional.of(username) : Optional.empty())
            .avatar(Optional.empty())
            .bot(Possible.of(bot))
            .build();
    }

    /**
     * The current time as an ISO-8601 extended offset date-time (the format Discord sends for message
     * timestamps), so each fabricated message carries a real datetime rather than a fixed placeholder.
     */
    private static @NotNull String messageTimestamp() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

}
