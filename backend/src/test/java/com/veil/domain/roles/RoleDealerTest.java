package com.veil.domain.roles;

import com.veil.domain.player.Faction;
import com.veil.domain.player.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the dynamic role roster: composition, balance, and determinism. */
class RoleDealerTest {

    private static long shadows(List<RoleStrategy> roles) {
        return roles.stream().filter(r -> r.faction() == Faction.SHADOW).count();
    }

    @Test
    void dealsExactlyNRoles() {
        for (int n = RoleDealer.MIN_PLAYERS; n <= 12; n++) {
            assertThat(RoleDealer.deal(n, new Random(1))).hasSize(n);
        }
    }

    @Test
    void smallRoomHasExactlyOneShadow() {
        assertThat(shadows(RoleDealer.deal(3, new Random(7)))).isEqualTo(1);
        assertThat(shadows(RoleDealer.deal(6, new Random(7)))).isEqualTo(1);
    }

    @Test
    void largeRoomAddsASecondShadow() {
        assertThat(shadows(RoleDealer.deal(7, new Random(7)))).isEqualTo(2);
        assertThat(shadows(RoleDealer.deal(12, new Random(7)))).isEqualTo(2);
    }

    @Test
    void includesOracleAndAegisWhenSeatsAllow() {
        List<Role> roles = RoleDealer.deal(8, new Random(3)).stream().map(RoleStrategy::role).toList();
        assertThat(roles).contains(Role.ORACLE, Role.AEGIS);
        assertThat(roles).filteredOn(r -> r == Role.CITIZEN).hasSize(4); // 8 - (2 shadow + oracle + aegis)
    }

    @Test
    void belowMinimumIsRejected() {
        assertThatThrownBy(() -> RoleDealer.deal(2, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameSeedGivesSameDeal() {
        List<Role> a = RoleDealer.deal(9, new Random(42)).stream().map(RoleStrategy::role).toList();
        List<Role> b = RoleDealer.deal(9, new Random(42)).stream().map(RoleStrategy::role).toList();
        assertThat(a).isEqualTo(b);
    }
}
