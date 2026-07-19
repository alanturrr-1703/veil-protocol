package com.veil.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test over the full room lifecycle through the real Spring context (default
 * profile = in-process confidential layer). It exercises create -> join -> start and asserts
 * the key redaction invariant: a role is NEVER exposed in the lobby, only after the deal.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoomLifecycleTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode postJson(String url, String body) throws Exception {
        String res = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    @Test
    void createJoinStart_dealsRolesAndNeverLeaksThemInLobby() throws Exception {
        // 1. Create a room; the creator is the host.
        JsonNode created = postJson("/api/rooms", "{\"name\":\"Neo\"}");
        String code = created.get("code").asText();
        String host = created.get("playerId").asText();
        assertThat(created.get("isHost").asBoolean()).isTrue();
        assertThat(created.get("phase").asText()).isEqualTo("LOBBY");

        // 2. Redaction invariant: in the lobby the host's own role is UNKNOWN (nothing dealt yet).
        mvc.perform(get("/api/rooms/{code}/view/{pid}", code, host))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("LOBBY"))
                .andExpect(jsonPath("$.ownRole").value("UNKNOWN"));

        // 3. Two more operatives join.
        postJson("/api/rooms/" + code + "/join", "{\"name\":\"Trinity\"}");
        postJson("/api/rooms/" + code + "/join", "{\"name\":\"Morpheus\"}");

        // 4. Host starts: roles are dealt + committed, first Night opens.
        JsonNode started = postJson("/api/rooms/" + code + "/start", "");
        assertThat(started.get("phase").asText()).isEqualTo("NIGHT");
        assertThat(started.get("commitments")).hasSize(3);

        // 5. After the deal, the host now sees a REAL role (still only their own).
        String view = mvc.perform(get("/api/rooms/{code}/view/{pid}", code, host))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ownRole = json.readTree(view).get("ownRole").asText();
        assertThat(ownRole).isIn("SHADOW", "ORACLE", "AEGIS", "CITIZEN");
    }

    @Test
    void joiningAnUnknownRoomIsRejected() throws Exception {
        mvc.perform(post("/api/rooms/ZZZZZ/join").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void startingWithTooFewPlayersIsRejected() throws Exception {
        JsonNode created = postJson("/api/rooms", "{\"name\":\"Solo\"}");
        String code = created.get("code").asText();
        mvc.perform(post("/api/rooms/{code}/start", code))
                .andExpect(status().isConflict());
    }
}
