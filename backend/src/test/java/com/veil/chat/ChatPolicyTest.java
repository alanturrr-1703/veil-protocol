package com.veil.chat;

import com.veil.domain.player.Role;
import com.veil.phases.GamePhaseType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the chat redaction rules. ChatPolicy is the single source of truth for both
 * posting and reading, so these tests guard the core information-leak boundary of the game.
 */
class ChatPolicyTest {

    @Test
    void dayChannelIsForLivingPlayersDuringDayOrVoting() {
        assertThat(ChatPolicy.canPost(Role.CITIZEN, true, GamePhaseType.DAY, ChatChannel.DAY)).isTrue();
        assertThat(ChatPolicy.canPost(Role.CITIZEN, true, GamePhaseType.VOTING, ChatChannel.DAY)).isTrue();
        assertThat(ChatPolicy.canPost(Role.CITIZEN, true, GamePhaseType.NIGHT, ChatChannel.DAY)).isFalse();
        assertThat(ChatPolicy.canPost(Role.CITIZEN, false, GamePhaseType.DAY, ChatChannel.DAY)).isFalse();
    }

    @Test
    void shadowChannelIsLivingShadowsAtNightOnly() {
        assertThat(ChatPolicy.canPost(Role.SHADOW, true, GamePhaseType.NIGHT, ChatChannel.SHADOW)).isTrue();
        assertThat(ChatPolicy.canPost(Role.SHADOW, true, GamePhaseType.DAY, ChatChannel.SHADOW)).isFalse();
        assertThat(ChatPolicy.canPost(Role.CITIZEN, true, GamePhaseType.NIGHT, ChatChannel.SHADOW)).isFalse();
        assertThat(ChatPolicy.canPost(Role.SHADOW, false, GamePhaseType.NIGHT, ChatChannel.SHADOW)).isFalse();
    }

    @Test
    void onlyLivingShadowsReadTheShadowChannel() {
        assertThat(ChatPolicy.canRead(Role.SHADOW, true, ChatChannel.SHADOW)).isTrue();
        assertThat(ChatPolicy.canRead(Role.CITIZEN, true, ChatChannel.SHADOW)).isFalse();
        assertThat(ChatPolicy.canRead(Role.SHADOW, false, ChatChannel.SHADOW)).isFalse();
    }

    @Test
    void deadChannelIsInvisibleToTheLiving() {
        assertThat(ChatPolicy.canRead(Role.CITIZEN, false, ChatChannel.DEAD)).isTrue();
        assertThat(ChatPolicy.canRead(Role.CITIZEN, true, ChatChannel.DEAD)).isFalse();
    }

    @Test
    void systemAndDayArePubliclyReadable() {
        assertThat(ChatPolicy.canRead(Role.CITIZEN, true, ChatChannel.SYSTEM)).isTrue();
        assertThat(ChatPolicy.canRead(Role.CITIZEN, false, ChatChannel.DAY)).isTrue();
    }

    @Test
    void directWhispersAreNeverGrantedByTheChannelGate() {
        // DIRECT is resolved per-recipient in the engine, so the generic gate must refuse it.
        assertThat(ChatPolicy.canRead(Role.CITIZEN, true, ChatChannel.DIRECT)).isFalse();
        assertThat(ChatPolicy.canPost(Role.CITIZEN, true, GamePhaseType.DAY, ChatChannel.DIRECT)).isFalse();
    }

    @Test
    void postableChannelsReflectRoleAndPhase() {
        assertThat(ChatPolicy.postableChannels(Role.SHADOW, true, GamePhaseType.NIGHT))
                .containsExactly(ChatChannel.SHADOW);
        assertThat(ChatPolicy.postableChannels(Role.CITIZEN, true, GamePhaseType.DAY))
                .containsExactly(ChatChannel.DAY);
        assertThat(ChatPolicy.postableChannels(Role.CITIZEN, false, GamePhaseType.DAY))
                .containsExactly(ChatChannel.DEAD);
    }
}
