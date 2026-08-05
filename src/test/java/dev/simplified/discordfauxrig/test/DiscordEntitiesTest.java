package dev.simplified.discordfauxrig.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplified.discordfauxrig.FauxConfig;
import dev.simplified.discordfauxrig.entity.DiscordEntities;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.PartialUserData;
import discord4j.discordjson.json.UserData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms every typed {@link DiscordEntities} immutable serializes and re-reads as the discord-json type
 * Discord4J expects, with the identifying fields intact. These are the entities the dispatch factory embeds
 * in gateway events, so a malformed one surfaces as a deserialization failure deep in Discord4J's pipeline.
 */
class DiscordEntitiesTest {

    private final FauxConfig config = FauxConfig.builder().build();
    private final DiscordEntities entities = new DiscordEntities(this.config);
    private final ObjectMapper mapper = this.entities.mapper();

    @Test
    void bot_user_is_the_configured_bot() throws Exception {
        UserData bot = this.reread(this.entities.botUser(), UserData.class);
        assertEquals(this.config.getBotId(), bot.id().asLong());
        assertEquals("TestBot", bot.username());
        assertTrue(bot.bot().toOptional().orElseThrow());
    }

    @Test
    void actor_user_is_the_configured_non_bot_invoker() throws Exception {
        UserData actor = this.reread(this.entities.actorUser(), UserData.class);
        assertEquals(this.config.getUserId(), actor.id().asLong());
        assertFalse(actor.bot().toOptional().orElseThrow());
    }

    @Test
    void developer_user_is_the_configured_owner() throws Exception {
        PartialUserData developer = this.reread(this.entities.developerUser(), PartialUserData.class);
        assertEquals(this.config.getDeveloperUserId(), developer.id().asLong());
    }

    @Test
    void target_user_carries_the_requested_id() throws Exception {
        UserData target = this.reread(this.entities.targetUser(777L), UserData.class);
        assertEquals(777L, target.id().asLong());
        assertFalse(target.bot().toOptional().orElseThrow());
    }

    @Test
    void interaction_message_carries_the_requested_id() throws Exception {
        MessageData message = this.reread(this.entities.interactionMessage(4242L), MessageData.class);
        assertEquals(4242L, message.id().asLong());
        assertEquals(this.config.getChannelId(), message.channelId().asLong());
    }

    @Test
    void message_is_authored_by_the_bot_and_carries_its_content() throws Exception {
        MessageData message = this.reread(this.entities.message(99L, "hello"), MessageData.class);
        assertEquals(99L, message.id().asLong());
        assertEquals("hello", message.content());
        assertEquals(this.config.getBotId(), message.author().id().asLong());
    }

    private <T> T reread(Object entity, Class<T> type) throws Exception {
        return this.mapper.readValue(this.mapper.writeValueAsString(entity), type);
    }

}
