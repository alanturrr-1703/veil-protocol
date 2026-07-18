/**
 * GameStateContract — thin façade tying the confidential contracts to the ledger, and the
 * bridge back to the Java game-engine. The engine stays the real-time authority; Midnight
 * is the settlement layer that makes the *confidential* moves verifiable and tamper-proof.
 *
 * Mapping (see docs/midnight-design.md):
 *   engine PrivateState  <->  Compact witnesses (localRole / localSalt)
 *   engine PublicState   <->  Compact ledger (roleCommitments / nightActions / facts)
 *   GameResolver.execute <->  Compact circuits (submitShadowAttack / proveInvestigation)
 */

import { emptyLedger, publicView } from "../state/PublicState.ts";
import type { LedgerState } from "../state/PublicState.ts";
import { LocalWitnesses } from "../state/PrivateState.ts";
import type { PrivateRoleState } from "../state/PrivateState.ts";
import { registerRole } from "./RolePrivacy.ts";

export class GameStateContract {
  readonly ledger: LedgerState = emptyLedger();

  /** Deal a player in: publish only their role commitment. */
  register(secret: PrivateRoleState): void {
    registerRole(this.ledger, secret.playerId, new LocalWitnesses(secret));
  }

  /** Exactly what any client/spectator is allowed to read. */
  publicSnapshot() {
    return publicView(this.ledger);
  }
}
