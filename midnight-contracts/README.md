# Veil Protocol — Midnight Confidential Layer

This package shows how Veil Protocol uses **Midnight** (a ZK data-protection blockchain) to
keep player roles, targets, and investigation results **confidential** while still letting the
game **verify** that every move was legal.

## The idea

Midnight contracts have three parts. We map the game onto them:

| Midnight part | What it holds | Veil use |
|---|---|---|
| **Public ledger** | replicated on-chain state | role **commitments** (hashes), opaque action commitments, disclosed faction bits |
| **Private witness** | off-chain, on the player's device | the real **role + salt** |
| **ZK circuit** | proves a statement about the witness | "I'm a City member", "a Shadow authorized this attack", "target's faction is X" |

The role is **never** on the ledger — only a hiding+binding **commitment** `hash(role, salt)`.
Circuits prove facts about the hidden role without revealing it.

## Run the demonstration (no install needed)

Requires Node >= 23 (uses native TypeScript execution).

```bash
cd midnight-contracts
node src/demo.ts        # or: npm run demo
```

You'll see:
1. Roles dealt privately.
2. The public ledger containing **only hashes** — no roles.
3. An attacker with the full ledger **failing** to recover a role.
4. A player proving **CITY membership** without revealing which city role.
5. A **Shadow** proving an attack is authorized while the target stays an opaque commitment.
6. An **Oracle** investigation disclosing **only a faction bit**.
7. A cheater who lies about their role getting **rejected** by the commitment check.

## Files

- `src/contracts/RolePrivacy.compact` / `ActionVerifier.compact` — the **real Compact contracts**
  (the code you'd compile with `compactc` and deploy to Midnight).
- `src/contracts/*.ts` — the TypeScript DApp side: witnesses + a prover/verifier mirror of the
  circuits, used by the runnable demo.
- `src/state/PrivateState.ts` — the private witness data (role + salt).
- `src/state/PublicState.ts` — the on-chain ledger shape (commitments only).
- `src/crypto/commitment.ts` — commitment hashing that mirrors Compact's `persistentHash`.
- `src/demo.ts` — the runnable walkthrough above.

## From demo to real Midnight

The `.ts` files simulate the ZK step with a boolean so the demo runs anywhere. To go real:

1. Install the Midnight compiler (`compactc`) and run the local **proof server** (Docker).
2. Compile `RolePrivacy.compact` / `ActionVerifier.compact` → generates `contract.ts`,
   `ledger.ts`, and the ZK keys.
3. Implement the witness callbacks (`localRole`, `localSalt`, …) with the code in
   `src/state/PrivateState.ts`.
4. Submit circuits via `midnight.js`; the chain verifies the zk-proof and applies only the
   disclosed ledger updates. The boolean `valid` in the demo becomes a real zk-SNARK.

See `../docs/midnight-design.md` for how this bridges to the Java `game-engine`.
