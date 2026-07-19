/**
 * Runnable demonstration: how Midnight-style confidential contracts HIDE the roles in
 * Veil Protocol while still letting the game verify moves.
 *
 * Run it:   node src/demo.ts     (from midnight/, Node >= 23)
 *
 * It shows, step by step:
 *   1. Roles are dealt privately.
 *   2. The PUBLIC ledger stores only commitments — no roles.
 *   3. An observer with the full ledger cannot recover a role.
 *   4. A player proves CITY membership without revealing which role.
 *   5. A Shadow proves an attack is authorized; the ledger keeps only opaque commitments.
 *   6. An Oracle investigation discloses ONLY a faction bit.
 *   7. A cheater who lies about their role is rejected by the commitment check.
 */

import { roleHash, randomSalt } from "./crypto/commitment.ts";
import { Role, RoleName, Faction, FactionName, LocalWitnesses } from "./state/PrivateState.ts";
import type { PrivateRoleState } from "./state/PrivateState.ts";
import { GameStateContract } from "./contracts/GameStateContract.ts";
import { proveCityMembership, verify } from "./contracts/RolePrivacy.ts";
import { submitShadowAttack, proveInvestigation } from "./contracts/ActionVerifier.ts";

const line = (s = "") => console.log(s);
const h = (s: string) => line(`\n=== ${s} ===`);

// --- 1. Deal roles privately (only the dealer/each device knows these) ---------------
const secrets: Record<string, PrivateRoleState> = {
  p1: { playerId: "p1", role: Role.SHADOW, salt: randomSalt() },
  p2: { playerId: "p2", role: Role.AEGIS, salt: randomSalt() },
  p3: { playerId: "p3", role: Role.ORACLE, salt: randomSalt() },
  p4: { playerId: "p4", role: Role.CITIZEN, salt: randomSalt() },
};

h("1. PRIVATE role assignment (never leaves each device)");
for (const s of Object.values(secrets)) {
  line(`  ${s.playerId}: role=${RoleName[s.role]}  salt=${s.salt.slice(0, 12)}…`);
}

// --- 2. Register on-chain: publish ONLY commitments ----------------------------------
const contract = new GameStateContract();
for (const s of Object.values(secrets)) contract.register(s);

h("2. PUBLIC ledger (this is all anyone on the chain can see)");
line("  roleCommitments:");
for (const [pid, c] of Object.entries(contract.publicSnapshot().roleCommitments)) {
  line(`    ${pid} -> ${c}`);
}
line("  (notice: not a single role appears here — only hashes)");

// --- 3. Observer tries to recover a role from the ledger -----------------------------
h("3. Attacker with the FULL ledger tries to recover p1's role");
const targetCommit = contract.ledger.roleCommitments.get("p1")!;
let cracked = false;
for (const role of [Role.SHADOW, Role.ORACLE, Role.AEGIS, Role.CITIZEN]) {
  // Best the attacker can do without the secret salt: guess common salts.
  for (const guessSalt of ["00".repeat(32), "ff".repeat(32)]) {
    if (roleHash(role, guessSalt) === targetCommit) cracked = true;
  }
}
line(`  brute force over all 4 roles x guessed salts -> match found? ${cracked}`);
line("  The real salt is a random 256-bit value, so recovering the role is infeasible.");

// --- 4. Prove CITY membership WITHOUT revealing which role ---------------------------
h("4. p3 proves it is CITY faction (for a city-only action)");
const cityProof = proveCityMembership(contract.ledger, "p3", new LocalWitnesses(secrets.p3));
line(`  proof.disclosed = ${JSON.stringify(cityProof.disclosed)}  (no role, just the faction)`);
line(`  verifier accepts? ${verify(cityProof, contract.ledger)}`);

const shadowTriesCity = proveCityMembership(contract.ledger, "p1", new LocalWitnesses(secrets.p1));
line(`  p1 (secretly SHADOW) tries the same proof -> verifier accepts? ${verify(shadowTriesCity, contract.ledger)}`);

// --- 5. Shadow proves an attack is authorized; target stays hidden -------------------
h("5. Shadow submits an attack (role & target never revealed)");
const atkProof = submitShadowAttack(
  contract.ledger, "night1:a1", "p1", "p4", randomSalt(), new LocalWitnesses(secrets.p1),
);
line(`  proof valid? ${atkProof.valid}   disclosed = ${JSON.stringify(atkProof.disclosed)}`);
line(`  ledger.nightActions -> ${JSON.stringify(contract.publicSnapshot().nightActions)}`);
line("  (only an opaque commitment of the target is stored — not 'p4')");

const citizenTriesAttack = submitShadowAttack(
  contract.ledger, "night1:a2", "p4", "p2", randomSalt(), new LocalWitnesses(secrets.p4),
);
line(`  p4 (a Citizen) tries to attack -> proof valid? ${citizenTriesAttack.valid} (rejected: not the Shadow)`);

// --- 6. Oracle investigation: selective disclosure of ONLY the faction ---------------
h("6. Oracle investigates p1 — learns only the FACTION bit");
const invProof = proveInvestigation(contract.ledger, "p1", new LocalWitnesses(secrets.p1));
line(`  disclosed = ${JSON.stringify(invProof.disclosed)}  (SHADOW faction — but not 'is it Shadow vs some other exact role?')`);
line(`  ledger.investigationFacts -> ${JSON.stringify({ p1: FactionName[contract.ledger.investigationFacts.get("p1")!] })}`);

// --- 7. A cheater lies about their role ----------------------------------------------
h("7. p1 (SHADOW) lies and claims to be a CITIZEN");
const forgedSecret: PrivateRoleState = { playerId: "p1", role: Role.CITIZEN, salt: secrets.p1.salt };
const forgedProof = proveCityMembership(contract.ledger, "p1", new LocalWitnesses(forgedSecret));
line(`  forged proof valid? ${forgedProof.valid}  (commitment no longer matches -> rejected)`);

h("SUMMARY");
line("  Public chain saw: commitments + opaque action + one faction bit.");
line("  It NEVER saw: any player's role, the Shadow's identity, or the attack target.");
