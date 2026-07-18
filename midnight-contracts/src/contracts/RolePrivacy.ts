/**
 * TypeScript mirror of RolePrivacy.compact.
 *
 * Each exported function corresponds to a Compact circuit. The PROVER side runs locally
 * with access to the private witness (role + salt) and produces a Proof. The VERIFIER
 * side (the chain / other players) only ever sees the Proof and the public ledger — never
 * the role. That asymmetry is the whole point of Midnight's zero-knowledge model.
 */

import { roleHash } from "../crypto/commitment.ts";
import { LocalWitnesses, Role, Faction, FactionName } from "../state/PrivateState.ts";
import type { LedgerState } from "../state/PublicState.ts";

/** What a verifier receives. Deliberately contains NO role and NO salt. */
export interface Proof {
  readonly circuit: string;
  readonly playerId: string;
  /** Only explicitly disclosed outputs appear here. */
  readonly disclosed: Record<string, string | number>;
  /**
   * Stand-in for a zk-SNARK. In real Midnight the verifier checks this cryptographically
   * without the witness; here it is the circuit's internal assertion result.
   */
  readonly valid: boolean;
}

/** Circuit: registerRole — publish ONLY the commitment to the ledger. */
export function registerRole(ledger: LedgerState, playerId: string, w: LocalWitnesses): void {
  const commitment = roleHash(w.localRole(), w.localSalt());
  ledger.roleCommitments.set(playerId, commitment); // the role itself is never stored
}

/**
 * Circuit: proveCityMembership — prove the caller holds a CITY role (1..3) without
 * revealing which one. Discloses a single bit: faction = CITY.
 */
export function proveCityMembership(
  ledger: LedgerState,
  playerId: string,
  w: LocalWitnesses,
): Proof {
  const role = w.localRole();
  const published = ledger.roleCommitments.get(playerId);
  const recomputed = roleHash(role, w.localSalt());

  const commitmentOk = published === recomputed;      // assert commitment matches
  const isCity = role >= Role.ORACLE && role <= Role.CITIZEN; // assert role in 1..3

  return {
    circuit: "proveCityMembership",
    playerId,
    disclosed: { faction: FactionName[Faction.CITY] }, // only "CITY" leaks — not the role
    valid: commitmentOk && isCity,
  };
}

/**
 * VERIFIER: the chain / another player. It has only the ledger and the proof. It cannot
 * see the role. It accepts iff the proof is valid and bound to the published commitment.
 */
export function verify(proof: Proof, ledger: LedgerState): boolean {
  if (!ledger.roleCommitments.has(proof.playerId)) return false;
  return proof.valid;
}
