package dev.simplified.discordfauxrig.rest;

import dev.simplified.discordfauxrig.FauxConfig;
import dev.simplified.discordfauxrig.entity.DiscordEntities;
import discord4j.core.object.entity.Guild;
import discord4j.discordjson.Id;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.json.ChannelData;
import discord4j.discordjson.json.EmojiData;
import discord4j.discordjson.json.GatewayData;
import discord4j.discordjson.json.GuildUpdateData;
import discord4j.discordjson.json.SessionStartLimitData;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.annotations.Log;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The canned response bodies the REST mock serves, one method per {@link Route} that returns JSON. Each is
 * serialized from a {@link DiscordEntities} immutable with Discord4J's own mapper, so a body cannot drift
 * from the shape the bot deserializes.
 * <p>
 * Package-private and paired with {@link Route}: the route table names these as method references, and
 * nothing outside {@code rest/} has any reason to reach a raw response body.
 */
@Log
@RequiredArgsConstructor
final class RouteBodies {

    // Empty list container returned by GET application emojis; not a Discord entity, so kept as a literal.
    private static final String EMPTY_EMOJIS_JSON = "{\"items\":[]}";

    // The id minted for a created application emoji.
    private static final long CREATED_EMOJI_ID = 900000000000000001L;

    private final FauxConfig config;
    private final DiscordEntities entities;

    /** {@code GET /users/@me} - the bot's self user. */
    @NotNull String userJson() {
        return this.write(this.entities.botUser());
    }

    /** {@code GET /gateway} - the gateway url. */
    @NotNull String gatewayJson() {
        return this.write(GatewayData.builder().url(this.config.getGatewayUrl()).build());
    }

    /** {@code GET /gateway/bot} - the gateway url with shard and session-start-limit metadata. */
    @NotNull String gatewayBotJson() {
        return this.write(GatewayData.builder()
            .url(this.config.getGatewayUrl())
            .shards(1)
            .sessionStartLimit(SessionStartLimitData.builder()
                .total(1000)
                .remaining(1000)
                .resetAfter(0)
                .maxConcurrency(1)
                .build())
            .build());
    }

    /** {@code GET /oauth2/applications/@me} - the application info. */
    @SuppressWarnings("deprecation") // summary is a required field on ApplicationInfoData even though deprecated
    @NotNull String applicationInfoJson() {
        return this.write(ApplicationInfoData.builder()
            .id(this.config.getBotId())
            .name("TestBot")
            .description("Faux Discord application")
            .botPublic(true)
            .botRequireCodeGrant(false)
            .summary("")
            .verifyKey(this.config.getVerifyKey())
            .owner(this.entities.developerUser())
            .build());
    }

    /** {@code GET /guilds/{guild}} - a minimal guild. */
    @NotNull String guildJson() {
        return this.write(GuildUpdateData.builder()
            .id(this.config.getGuildId())
            .name("Faux Guild")
            .ownerId(this.config.getBotId())
            .afkTimeout(300)
            .verificationLevel(Guild.VerificationLevel.NONE.getValue())
            .defaultMessageNotifications(0)
            .explicitContentFilter(Guild.ContentFilterLevel.DISABLED.getValue())
            .mfaLevel(Guild.MfaLevel.NONE.getValue())
            .premiumTier(Guild.PremiumTier.NONE.getValue())
            .preferredLocale("en-US")
            .nsfwLevel(Guild.NsfwLevel.DEFAULT.getValue())
            .roles(List.of())
            .emojis(List.of())
            .build());
    }

    /** The message minted for every reply/followup ({@code POST}/{@code PATCH} message endpoints). */
    @NotNull String messageJson() {
        return this.write(this.entities.message(this.config.getReplyMessageId(), ""));
    }

    /** {@code GET /applications/{app}/emojis} - an empty emoji list container. */
    @NotNull String emptyEmojisJson() {
        return EMPTY_EMOJIS_JSON;
    }

    /** {@code POST /applications/{app}/emojis} - a created application emoji. */
    @NotNull String emojiJson() {
        return this.write(EmojiData.builder()
            .id(CREATED_EMOJI_ID)
            .name("faux")
            .build());
    }

    /** {@code GET /channels/{channel}} - a minimal text channel. */
    @NotNull String channelJson() {
        return this.write(ChannelData.builder()
            .id(Id.of(this.config.getChannelId()))
            .type(0)
            .build());
    }

    /** {@code GET /channels/{channel}/messages/{message}/reactions/{emoji}} - the users who reacted (none). */
    @NotNull String reactionUsersJson() {
        return "[]";
    }

    /** Serializes a discord-json immutable with Discord4J's mapper. */
    private @NotNull String write(@NotNull Object data) {
        try {
            return this.entities.mapper().writeValueAsString(data);
        } catch (Exception exception) {
            log.error("Failed to serialize {} for a mock response", data.getClass().getSimpleName(), exception);
            throw new IllegalStateException("Failed to serialize " + data.getClass().getSimpleName(), exception);
        }
    }

}
