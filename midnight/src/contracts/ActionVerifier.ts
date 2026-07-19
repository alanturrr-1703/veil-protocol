/**
 * TypeScript mirror of ActionVerifier.compact.
 *
 * Proves night actions are legal without exposing the secret role or the private target.
 */

import { actionCommit, roleHash } from "../crypto/commitment.ts";
import { LocalWitnesses, Role, Faction, FactionName, factionOf } from "../state/PrivateState.ts";
import type { LedgerState } from "../state/PublicState.ts";
import type { Proof } from "./RolePrivacy.ts";

/**
 * Circuit: submitShadowAttack — the proof guarantees a committed Shadow authorized the
 * attack and binds it to a target, but the ledger only records opaque commitments. Neither
 * the role nor the target plaintext ever appears on-chain.
 */
export function submitShadowAttack(
  ledger: LedgerState,
  actionId: string,
  playerId: string,
  targetId: string,
  nonce: string,
  w: LocalWitnesses,
): Proof {
  const role = w.localRole();
  const published = ledger.roleCommitments.get(playerId);
  const commitmentOk = published === roleHash(role, w.localSalt());
  const isShadow = role === Role.SHADOW;

  const valid = commitmentOk && isShadow;
  if (valid) {
    ledger.nightActions.set(actionId, actionCommit(targetId, nonce)); // target stays hidden
  }

  return {
    circuit: "submitShadowAttack",
    playerId,
    disclosed: { actionId }, // only the action id is public; who/whom stay hidden
    valid,
  };
}

/**
 * Circuit: proveInvestigation — SELECTIVE disclosure. Prove the target's committed role,
 * then reveal ONLY its faction bit. The target's exact role never leaks.
 */
export function proveInvestigation(
  ledger: LedgerState,
  targetId: string,
  targetWitness: LocalWitnesses,
): Proof {
  const targetRole = targetWitness.localRole();
  const published = ledger.roleCommitments.get(targetId);
  const commitmentOk = published === roleHash(targetRole, targetWitness.localSalt());

  const faction = factionOf(targetRole);
  if (commitmentOk) {
    ledger.investigationFacts.set(targetId, faction);
  }

  return {
    circuit: "proveInvestigation",
    playerId: targetId,
    disclosed: { faction: FactionName[faction] }, // only SHADOW/CITY — not ORACLE vs AEGIS vs CITIZEN
    valid: commitmentOk,
  };
}
