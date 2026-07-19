# Deploying the Confidential Layer to Midnight Testnet

This is the path from the local commitment mode (which ships working today) to **real
zk-transactions on Midnight testnet**. Nothing in the Java backend or the frontend changes —
only the relayer sidecar and the `midnight` Spring profile's target are pointed at real
infrastructure. That is the whole point of the `ConfidentialGateway` seam.

```
Frontend ──WS/HTTP──▶ Java backend ──HTTP──▶ Relayer ──midnight.js──▶ Proof server ──▶ Testnet
   (unchanged)          (unchanged)         (this doc)                 (docker)        (public)
```

## 0. Prerequisites

| Tool | Version | Purpose |
| ---- | ------- | ------- |
| Node.js | ≥ 23 | relayer + `midnight.js` |
| Docker | latest | proof server |
| `compactc` (Compact compiler) | matches `pragma language_version >= 0.23` | compile the circuits |
| Midnight testnet wallet | — | the relayer's funded signing key |

Install the Compact toolchain and the Midnight JS SDK per the current Midnight developer docs
(`@midnight-ntwrk/compact-runtime`, `@midnight-ntwrk/midnight-js-*`, wallet + indexer packages).

## 1. Start the proof server

The proof server is already declared in `docker-compose.yml` behind the `midnight` profile:

```bash
docker compose --profile midnight up -d midnight-proof
# serves the local proving API on :6300
```

## 2. Compile the Compact contracts

The circuits live in `midnight/src/contracts/`:

- `RolePrivacy.compact` — `registerRole`, `proveCityMembership` (role commitments + faction proofs)
- `ActionVerifier.compact` — night-action authorization (Shadow attack, Oracle investigation)

```bash
cd midnight
compactc src/contracts/RolePrivacy.compact   build/RolePrivacy
compactc src/contracts/ActionVerifier.compact build/ActionVerifier
```

This emits the prover/verifier keys and the typed contract module the relayer imports.

## 3. Fund the relayer wallet

The relayer submits every transaction on players' behalf, so it needs one funded testnet key.

1. Generate/import a wallet with the Midnight wallet SDK.
2. Request tDUST from the testnet faucet.
3. Export the seed to the relayer environment (never commit it):

```bash
export MIDNIGHT_WALLET_SEED=…            # relayer signing key
export MIDNIGHT_INDEXER_URL=…            # testnet indexer
export MIDNIGHT_NODE_URL=…               # testnet node RPC
export MIDNIGHT_PROOF_SERVER_URL=http://localhost:6300
```

## 4. Deploy the contracts

On first run, deploy each compiled contract once and record the resulting contract addresses;
subsequent runs `join` the existing deployment. Persist the addresses (env or a small state
file) so the relayer reconnects to the same ledger across restarts.

## 5. Wire the relayer to the real circuits

Open `midnight/relayer/server.ts` and replace each `// [MIDNIGHT]` marker with the real call:

| Marker in `server.ts` | Real Midnight call |
| --------------------- | ------------------ |
| `commit()`            | `registerRole(playerId)` tx; witness returns `{ localRole, localSalt }` |
| `investigate()`       | `proveInvestigation(targetId)` circuit; read disclosed faction bit |
| `attack()`            | `submitShadowAttack(actionId, hash(targetId))`; proof asserts committed role == SHADOW |
| `resolveWinner()`     | win-condition circuit over the public alive-set commitments |

The relayer keeps the same HTTP contract (`/commit`, `/investigate`, `/attack`, `/win`,
`/verifyWin`), so the Java `MidnightConfidentialGateway` is untouched. Swap the in-memory
`secrets` map for: on-chain commitments (public) + a local witness store keyed by player
(private, relayer-side only).

## 6. Run the stack against testnet

```bash
# proof server (step 1) already up
npm --workspace @veil/midnight run relayer          # now talking to testnet

mvn -f backend/pom.xml spring-boot:run \
    -Dspring-boot.run.profiles=midnight \
    -Dveil.midnight.relayer-url=http://localhost:6301

npm run dev:frontend
```

## 7. Verify

- `curl localhost:6301/health` → relayer connected to node/indexer.
- Create a room, add ≥ 3 players, start → `POST /api/rooms/{code}/start` returns commitments
  that now correspond to **on-chain** `roleCommitments` entries (verify via the indexer).
- A non-Shadow attack is rejected by the circuit (not by Java) — the proof simply fails to
  generate, and the gateway's fail-safe denies it.
- End a match → the winning faction is disclosed by the win circuit, and the leaderboard
  updates.

## Fail-safe guarantees

`MidnightConfidentialGateway` treats any relayer/proof error as a **denial**: attacks are
rejected, `resolveWinner` returns `NEUTRAL`, and investigations return `NEUTRAL`. A proof
outage can never silently authorize a kill or hand out a win — the worst case is a stalled
match, never a leaked or forged result.
