# tests/ — cross-cutting integration & end-to-end tests

Unit tests live next to the code they cover:

- Backend (JUnit): `backend/src/test/java/...`
- Midnight (contract/demo checks): `midnight/src/tests/...`

This top-level `tests/` folder is for tests that span more than one module and therefore
don't belong to any single one.

## What ships today

**Unit + integration (JUnit, in `backend/src/test/java`)** — run with `mvn -f backend/pom.xml test`:

- `RoleDealerTest` — dynamic roster composition, balance, and determinism.
- `MockConfidentialGatewayTest` — the confidential contract: hiding/salted commitments,
  faction-only investigation, Shadow-only attack authorization, win resolution.
- `ChatPolicyTest` — the chat redaction boundary (who may post/read each channel).
- `RoomLifecycleTest` — `@SpringBootTest` over the real REST surface: create → join → start,
  and the invariant that a role is `UNKNOWN` in the lobby and revealed only to its owner
  after the deal.

**End-to-end (`tests/e2e/room-flow.mjs`)** — drives a RUNNING stack over HTTP, exactly as the
frontend would, validating the real deployed surface (REST + the confidential layer behind it):

```bash
npm run dev:backend                 # terminal 1 (add the midnight profile to hit the relayer)
node tests/e2e/room-flow.mjs        # terminal 2  (BASE=http://host:8080 to point elsewhere)
```

See [../docs/architecture-v3.md](../docs/architecture-v3.md) for the build plan and
[../docs/testnet-deployment.md](../docs/testnet-deployment.md) to run e2e against Midnight testnet.
