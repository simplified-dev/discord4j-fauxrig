/**
 * A standalone, discord4j-specific offline test harness: a fake Discord REST server
 * ({@link dev.simplified.discordfauxrig.rest.LocalDiscordServer LocalDiscordServer}) and a fake in-JVM
 * gateway ({@link dev.simplified.discordfauxrig.gateway.FauxGatewayClient FauxGatewayClient}), wired
 * together by {@link dev.simplified.discordfauxrig.FauxDiscord FauxDiscord} over a deterministic
 * identity ({@link dev.simplified.discordfauxrig.FauxConfig FauxConfig}).
 *
 * <p>
 * The harness stands in for Discord itself and holds no concept of a bot: a consumer wires its own
 * {@code DiscordBot} to the server's REST base url and fake gateway,
 * pushes simulated dispatches built by
 * {@link dev.simplified.discordfauxrig.gateway.DispatchFactory DispatchFactory}, and asserts on the
 * captured {@link dev.simplified.discordfauxrig.rest.RecordedRequest RecordedRequest}s.
 *
 * <p>
 * Depends only on discord4j, never on a bot framework or any consumer's code; the dependency arrow points
 * only inward, from a consumer to here.
 */
package dev.simplified.discordfauxrig;
