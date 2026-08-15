package dev.simplified.discordfauxrig.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplified.discordfauxrig.FauxConfig;
import dev.simplified.discordfauxrig.entity.DiscordEntities;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.json.ChannelData;
import discord4j.discordjson.json.EmojiData;
import discord4j.discordjson.json.GatewayData;
import discord4j.discordjson.json.GuildUpdateData;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.UserData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms every {@link RouteBodies} response round-trips: the JSON it emits deserializes back into the
 * discord-json immutable Discord4J expects, with the identifying fields intact. Catches a malformed or
 * mistyped entity builder that the wire format would otherwise reject at runtime.
 * <p>
 * Lives in the {@code rest} package because {@link RouteBodies} is package-private, like the {@link Route}
 * table it serves.
 */
class RouteBodiesTest {

    private final FauxConfig config = FauxConfig.builder().build();
    private final DiscordEntities entities = new DiscordEntities(this.config);
    private final RouteBodies bodies = new RouteBodies(this.config, this.entities);
    private final ObjectMapper mapper = this.entities.mapper();

    @Test
    void self_user_round_trips() throws Exception {
        UserData user = this.mapper.readValue(this.bodies.userJson(), UserData.class);
        assertEquals(this.config.getBotId(), user.id().asLong());
        assertEquals("TestBot", user.username());
    }

    @Test
    void gateway_round_trips() throws Exception {
        GatewayData gateway = this.mapper.readValue(this.bodies.gatewayJson(), GatewayData.class);
        assertEquals(this.config.getGatewayUrl(), gateway.url());
    }

    @Test
    void gateway_bot_carries_session_limits() throws Exception {
        GatewayData gateway = this.mapper.readValue(this.bodies.gatewayBotJson(), GatewayData.class);
        assertEquals(1, gateway.shards().toOptional().orElseThrow());
        assertEquals(1000, gateway.sessionStartLimit().toOptional().orElseThrow().total());
    }

    @Test
    void application_info_round_trips() throws Exception {
        ApplicationInfoData application = this.mapper.readValue(this.bodies.applicationInfoJson(), ApplicationInfoData.class);
        assertEquals(this.config.getBotId(), application.id().asLong());
        assertEquals(this.config.getVerifyKey(), application.verifyKey());
    }

    @Test
    void application_info_carries_the_developer_as_owner() throws Exception {
        ApplicationInfoData application = this.mapper.readValue(this.bodies.applicationInfoJson(), ApplicationInfoData.class);
        assertEquals(this.config.getDeveloperUserId(), application.owner().toOptional().orElseThrow().id().asLong());
    }

    @Test
    void guild_round_trips() throws Exception {
        GuildUpdateData guild = this.mapper.readValue(this.bodies.guildJson(), GuildUpdateData.class);
        assertEquals(this.config.getGuildId(), guild.id().asLong());
        assertEquals("Faux Guild", guild.name());
    }

    @Test
    void reply_message_round_trips() throws Exception {
        MessageData message = this.mapper.readValue(this.bodies.messageJson(), MessageData.class);
        assertEquals(this.config.getReplyMessageId(), message.id().asLong());
    }

    @Test
    void empty_emojis_is_an_empty_item_list() throws Exception {
        JsonNode node = this.mapper.readTree(this.bodies.emptyEmojisJson());
        assertTrue(node.get("items").isArray() && node.get("items").isEmpty());
    }

    @Test
    void created_emoji_round_trips() throws Exception {
        EmojiData emoji = this.mapper.readValue(this.bodies.emojiJson(), EmojiData.class);
        assertEquals("faux", emoji.name().orElseThrow());
        assertTrue(emoji.id().isPresent());
    }

    @Test
    void channel_round_trips() throws Exception {
        ChannelData channel = this.mapper.readValue(this.bodies.channelJson(), ChannelData.class);
        assertEquals(this.config.getChannelId(), channel.id().asLong());
        assertEquals(0, channel.type());
    }

    @Test
    void reaction_users_is_an_empty_array() throws Exception {
        JsonNode node = this.mapper.readTree(this.bodies.reactionUsersJson());
        assertTrue(node.isArray() && node.isEmpty());
    }

    @Test
    void every_json_route_has_a_body() {
        Route.forEach(route -> {
            if (route.kind() != Route.Kind.JSON)
                return;

            String body = route.body(this.bodies);
            assertTrue(body != null && !body.isBlank(), route + " is a JSON route but rendered no body");
        });
    }

}
