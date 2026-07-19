# Midnight Relayer

The relayer is the bridge between the Java backend (which speaks HTTP) and Midnight (which
speaks `midnight.js`). It holds the wallet and submits every confidential transaction **on
behalf of players**, so nobody has to connect a wallet to play — the hackathon-friendly
relayer model.

```
Java backend  --HTTP-->  relayer (this)  --midnight.js-->  proof server / testnet
```

## Run

```bash
# from the repo root
npm --workspace @veil/midnight run relayer
# or, from midnight/
node relayer/server.ts        # Node >= 23
```

Then start the backend with the `midnight` profile so it routes through the relayer instead of
the in-process mock:

```bash
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=midnight
```

## HTTP contract

The Java `MidnightConfidentialGateway` calls these. Every response is **public-safe** — a
commitment hash, a faction bit, an authorized flag, or an opaque reference. Raw roles/targets
never cross this boundary.

| Method | Path                       | Body                              | Returns                                  |
| ------ | -------------------------- | --------------------------------- | ---------------------------------------- |
| GET    | `/health`                  | —                                 | `{ ok, committed }`                      |
| POST   | `/commit`                  | `{ playerId, role }`              | `{ commitment }`                         |
| GET    | `/commitment?playerId=…`   | —                                 | `{ commitment }`                         |
| POST   | `/investigate`             | `{ oracleId, targetId }`          | `{ faction }`                            |
| POST   | `/attack`                  | `{ attackerId, targetId }`        | `{ authorized, opaqueRef, message }`     |
| POST   | `/win`                     | `{ aliveIds: [] }`                | `{ faction }`                            |
| POST   | `/verifyWin`               | `{ faction }`                     | `{ ok }`                                 |

## Wiring the real testnet

Today the relayer evaluates each operation with the **same commitment primitives the Compact
circuits use** (`../src/crypto/commitment.ts`), so the whole stack runs locally with no
toolchain. Each spot that becomes a real zk-transaction is marked `// [MIDNIGHT]` in
`server.ts`. See [`docs/testnet-deployment.md`](../../docs/testnet-deployment.md) for the full
compile-and-deploy walkthrough.
