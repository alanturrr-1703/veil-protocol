package com.veil.confidential;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * The real confidential referee: a {@link ConfidentialGateway} backed by the Midnight
 * relayer sidecar. Every method is an HTTP call to the relayer (see {@code midnight/relayer/}),
 * which holds the wallet and submits the actual zk-transactions to the proof server / testnet.
 * The engine depends only on {@link ConfidentialGateway}, so selecting this (via the
 * {@code midnight} Spring profile) instead of {@link MockConfidentialGateway} changes no game
 * logic — the confidential state simply moves on-chain.
 *
 * <p>Every response is PUBLIC-SAFE (a commitment hash, a faction bit, an authorized flag, an
 * opaque ref, a winning faction). Raw roles/targets never cross this boundary. On any transport
 * failure the gateway fails SAFE (deny attack, no winner, neutral faction) so a relayer outage
 * can never silently authorize a kill or hand someone a win.
 */
public class MidnightConfidentialGateway implements ConfidentialGateway {

    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public MidnightConfidentialGateway(String baseUrl, ObjectMapper mapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public String commitRole(String playerId, Role role) {
        JsonNode res = post("/commit", Map.of("playerId", playerId, "role", role.name()));
        return res == null ? "" : res.path("commitment").asText("");
    }

    @Override
    public String commitmentOf(String playerId) {
        JsonNode res = get("/commitment?playerId=" + enc(playerId));
        return res == null ? "" : res.path("commitment").asText("");
    }

    @Override
    public Faction investigate(String oracleId, String targetId) {
        JsonNode res = post("/investigate", Map.of("oracleId", oracleId, "targetId", targetId));
        return res == null ? Faction.NEUTRAL : parseFaction(res.path("faction").asText("NEUTRAL"));
    }

    @Override
    public ConfidentialResult submitAttack(String attackerId, String targetId) {
        JsonNode res = post("/attack", Map.of("attackerId", attackerId, "targetId", targetId));
        if (res == null) return ConfidentialResult.denied("relayer unreachable — attack denied (fail-safe)");
        return res.path("authorized").asBoolean(false)
                ? ConfidentialResult.ok(res.path("opaqueRef").asText(""), res.path("message").asText("authorized"))
                : ConfidentialResult.denied(res.path("message").asText("attack rejected"));
    }

    @Override
    public boolean verifyWin(Faction faction) {
        JsonNode res = post("/verifyWin", Map.of("faction", faction.name()));
        return res != null && res.path("ok").asBoolean(false);
    }

    @Override
    public Faction resolveWinner(Set<String> alivePlayerIds) {
        JsonNode res = post("/win", Map.of("aliveIds", alivePlayerIds));
        return res == null ? Faction.NEUTRAL : parseFaction(res.path("faction").asText("NEUTRAL"));
    }

    // --- transport ------------------------------------------------------------

    private JsonNode post(String path, Object body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) return null;
            return mapper.readTree(res.body());
        } catch (Exception e) {
            return null; // caller applies a fail-safe default
        }
    }

    private JsonNode get(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) return null;
            return mapper.readTree(res.body());
        } catch (Exception e) {
            return null;
        }
    }

    private static Faction parseFaction(String raw) {
        try {
            return Faction.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Faction.NEUTRAL;
        }
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
