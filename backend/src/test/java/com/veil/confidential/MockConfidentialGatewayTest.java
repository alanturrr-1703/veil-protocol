package com.veil.confidential;

import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the confidential referee behaviour. These pin the PUBLIC-SAFE contract every
 * ConfidentialGateway must honour, so the Midnight-backed implementation can be checked against
 * the same expectations.
 */
class MockConfidentialGatewayTest {

    private MockConfidentialGateway gw;

    @BeforeEach
    void setUp() {
        gw = new MockConfidentialGateway();
    }

    @Test
    void commitmentIsHidingAndSaltedPerPlayer() {
        String c1 = gw.commitRole("a", Role.SHADOW);
        String c2 = gw.commitRole("b", Role.SHADOW);
        // A commitment reveals nothing plaintext and, thanks to the salt, two identical roles
        // produce different commitments.
        assertThat(c1).isNotBlank().doesNotContain("SHADOW");
        assertThat(c1).isNotEqualTo(c2);
        assertThat(gw.commitmentOf("a")).isEqualTo(c1);
    }

    @Test
    void investigateDisclosesOnlyFaction() {
        gw.commitRole("oracle", Role.ORACLE);
        gw.commitRole("shadow", Role.SHADOW);
        gw.commitRole("cit", Role.CITIZEN);

        assertThat(gw.investigate("oracle", "shadow")).isEqualTo(Faction.SHADOW);
        assertThat(gw.investigate("oracle", "cit")).isEqualTo(Faction.CITY);
        assertThat(gw.investigate("oracle", "ghost")).isEqualTo(Faction.NEUTRAL); // unknown target
    }

    @Test
    void onlyAShadowCanAuthorizeAnAttack() {
        gw.commitRole("s", Role.SHADOW);
        gw.commitRole("c", Role.CITIZEN);

        ConfidentialResult ok = gw.submitAttack("s", "c");
        assertThat(ok.authorized()).isTrue();
        assertThat(ok.opaqueRef()).isNotBlank();

        ConfidentialResult denied = gw.submitAttack("c", "s");
        assertThat(denied.authorized()).isFalse();
        assertThat(denied.opaqueRef()).isEmpty();
    }

    @Test
    void resolveWinnerFromPublicAliveSet() {
        gw.commitRole("s", Role.SHADOW);
        gw.commitRole("c1", Role.CITIZEN);
        gw.commitRole("c2", Role.ORACLE);

        // No Shadow alive -> City wins.
        assertThat(gw.resolveWinner(Set.of("c1", "c2"))).isEqualTo(Faction.CITY);
        // Shadow reaches parity with City -> Shadows win.
        assertThat(gw.resolveWinner(Set.of("s", "c1"))).isEqualTo(Faction.SHADOW);
        // Shadows outnumbered but present -> undecided.
        assertThat(gw.resolveWinner(Set.of("s", "c1", "c2"))).isEqualTo(Faction.NEUTRAL);
    }
}
