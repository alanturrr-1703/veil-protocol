/**
 * PUBLIC state — the on-chain ledger, replicated to everyone (players, spectators,
 * anyone reading the chain). It mirrors the `export ledger ...` declarations in the
 * Compact contracts. By construction it contains ONLY commitments and minimal disclosed
 * facts — never a role, never a private target.
 */

import type { Faction } from "./PrivateState.ts";

export interface LedgerState {
  /** playerId -> role commitment (a hash). The role is unrecoverable from this. */
  roleCommitments: Map<string, string>;
  /** actionId -> commitment binding (target, nonce). The target stays hidden. */
  nightActions: Map<string, string>;
  /** targetId -> the ONLY thing an investigation reveals: a faction bit. */
  investigationFacts: Map<string, Faction>;
}

export function emptyLedger(): LedgerState {
  return {
    roleCommitments: new Map(),
    nightActions: new Map(),
    investigationFacts: new Map(),
  };
}

/**
 * Anything a client is allowed to see. Notice there is no field that could ever carry a
 * secret role — the type itself makes leaking impossible, the same guarantee the Java
 * engine enforces with its PublicState / PrivateState split.
 */
export function publicView(ledger: LedgerState) {
  return {
    roleCommitments: Object.fromEntries(ledger.roleCommitments),
    nightActions: Object.fromEntries(ledger.nightActions),
    investigationFacts: Object.fromEntries(ledger.investigationFacts),
  };
}
