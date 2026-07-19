/**
 * PRIVATE state — lives only on a player's own device and is fed to Compact circuits
 * through witness callbacks. It is NEVER sent on-chain. On Midnight this is exactly the
 * data a `witness` function returns; the ZK circuit consumes it to build a proof and the
 * raw values never leave the machine.
 *
 * (Roles/Factions are plain const maps rather than TS `enum`s so this file runs directly
 * under Node's type-stripping without a build step.)
 */

export const Role = {
  SHADOW: 0,
  ORACLE: 1,
  AEGIS: 2,
  CITIZEN: 3,
} as const;
export type Role = (typeof Role)[keyof typeof Role];
export const RoleName = ["SHADOW", "ORACLE", "AEGIS", "CITIZEN"] as const;

export const Faction = {
  SHADOW: 0,
  CITY: 1,
} as const;
export type Faction = (typeof Faction)[keyof typeof Faction];
export const FactionName = ["SHADOW", "CITY"] as const;

export function factionOf(role: Role): Faction {
  return role === Role.SHADOW ? Faction.SHADOW : Faction.CITY;
}

/** The confidential per-player secret. `salt` blinds the commitment. */
export interface PrivateRoleState {
  readonly playerId: string;
  readonly role: Role;
  /** 32-byte hex blinding factor — without it the role hash is not brute-forceable. */
  readonly salt: string;
}

/**
 * Witness provider: the object a Compact contract's witnesses read from at proving time.
 * Mirrors `witness localRole()` / `witness localSalt()` in RolePrivacy.compact.
 */
export class LocalWitnesses {
  private readonly secret: PrivateRoleState;

  constructor(secret: PrivateRoleState) {
    this.secret = secret;
  }

  localRole(): Role {
    return this.secret.role;
  }

  localSalt(): string {
    return this.secret.salt;
  }
}
