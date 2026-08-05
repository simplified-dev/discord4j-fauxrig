package dev.simplified.discordfauxrig.test;

import dev.simplified.discordfauxrig.gateway.FauxGatewayClient;
import discord4j.discordjson.json.gateway.Dispatch;
import discord4j.gateway.retry.GatewayStateChange;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the in-JVM {@link FauxGatewayClient}: {@code execute} replays its handshake, {@code emit} pushes
 * dispatches into the pipeline, {@code close} completes it, and the metadata accessors report the stand-in
 * values login relies on. Framework-free.
 */
class FauxGatewayClientTest {

    @Test
    void execute_replays_the_handshake() {
        List<Dispatch> handshake = List.of(GatewayStateChange.connected(), GatewayStateChange.connected());
        FauxGatewayClient client = new FauxGatewayClient(handshake, 1);

        Disposable execution = client.execute("ws://ignored").subscribe();

        List<Dispatch> replayed = client.dispatch().take(2).collectList().block(Duration.ofSeconds(5));
        assertNotNull(replayed);
        assertEquals(2, replayed.size());
        execution.dispose();
    }

    @Test
    void emit_pushes_into_the_dispatch_flux() {
        FauxGatewayClient client = new FauxGatewayClient(List.of(), 1);

        client.emit(GatewayStateChange.connected());

        assertNotNull(client.dispatch().take(1).blockFirst(Duration.ofSeconds(5)));
    }

    @Test
    void close_completes_the_dispatch_flux() {
        FauxGatewayClient client = new FauxGatewayClient(List.of(), 1);
        client.emit(GatewayStateChange.connected());

        client.close(false).block(Duration.ofSeconds(5));

        List<Dispatch> drained = client.dispatch().collectList().block(Duration.ofSeconds(5));
        assertNotNull(drained, "the dispatch flux should complete after close");
        assertEquals(1, drained.size());
    }

    @Test
    void reports_the_stand_in_metadata() {
        FauxGatewayClient client = new FauxGatewayClient(List.of(), 3);

        assertEquals(3, client.getShardCount());
        assertEquals("fake-session", client.getSessionId());
        assertEquals(0, client.getSequence());
        assertEquals(Duration.ZERO, client.getResponseTime());
        assertEquals(Boolean.TRUE, client.isConnected().block(Duration.ofSeconds(1)));
    }

}
