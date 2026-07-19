# tests/ — cross-cutting integration & end-to-end tests

Unit tests live next to the code they cover:

- Backend (JUnit): `backend/src/test/java/...`
- Midnight (contract/demo checks): `midnight/src/tests/...`

This top-level `tests/` folder is for tests that span more than one module and therefore
don't belong to any single one:

- **Integration** — backend REST/WebSocket contract tests driving a real `VeilEngine`
  through the `ConfidentialGateway` (mock profile), asserting that confidential state never
  leaks into a `PlayerView`.
- **End-to-end** — a headless client that joins a room by code, plays a full
  Lobby → Night → Day → Vote → GameOver loop, and verifies public events + per-viewer redaction.

Populated in Phase 7 of the build plan (see [../docs/architecture-v3.md](../docs/architecture-v3.md)).
