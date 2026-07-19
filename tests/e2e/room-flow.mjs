/**
 * End-to-end smoke test: drives a RUNNING stack through the full room lifecycle over HTTP,
 * exactly as the frontend would. Unlike the JUnit tests (which boot an in-process context),
 * this validates the real deployed surface — REST + the confidential layer behind it.
 *
 * Prereqs (see docs): backend on :8080. For the real referee also run the relayer on :6301
 * and start the backend with `-Dspring-boot.run.profiles=midnight`.
 *
 * Run:  node tests/e2e/room-flow.mjs            # defaults to http://localhost:8080
 *       BASE=http://host:8080 node tests/e2e/room-flow.mjs
 */

const BASE = process.env.BASE ?? "http://localhost:8080";
const API = `${BASE}/api/rooms`;

let passed = 0;
function check(label, cond) {
  if (cond) {
    passed++;
    console.log(`  ok   ${label}`);
  } else {
    console.error(`  FAIL ${label}`);
    process.exitCode = 1;
  }
}

async function post(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, json: await res.json().catch(() => ({})) };
}

async function getJson(url) {
  const res = await fetch(url);
  return { status: res.status, json: await res.json().catch(() => ({})) };
}

async function main() {
  console.log(`e2e: room lifecycle against ${BASE}`);

  // Reachability first, with a friendly message if the backend is down.
  try {
    await fetch(BASE);
  } catch {
    console.error(`\nBackend not reachable at ${BASE}. Start it with:\n  npm run dev:backend\n`);
    process.exit(2);
  }

  // 1. Create a room.
  const created = await post(API, { name: "Neo" });
  check("create -> 200", created.status === 200);
  check("create -> isHost", created.json.isHost === true);
  check("create -> LOBBY", created.json.phase === "LOBBY");
  const code = created.json.code;
  const host = created.json.playerId;

  // 2. Role is hidden in the lobby.
  const lobbyView = await getJson(`${API}/${code}/view/${host}`);
  check("lobby view -> ownRole UNKNOWN", lobbyView.json.ownRole === "UNKNOWN");

  // 3. Join two operatives.
  const j1 = await post(`${API}/${code}/join`, { name: "Trinity" });
  const j2 = await post(`${API}/${code}/join`, { name: "Morpheus" });
  check("join Trinity -> 200", j1.status === 200 && j1.json.isHost === false);
  check("join Morpheus -> 200", j2.status === 200);

  // 4. Start the match.
  const started = await post(`${API}/${code}/start`);
  check("start -> NIGHT", started.json.phase === "NIGHT");
  check("start -> 3 commitments", Object.keys(started.json.commitments ?? {}).length === 3);

  // 5. Role is revealed to its owner only, after the deal.
  const gameView = await getJson(`${API}/${code}/view/${host}`);
  check(
    "post-deal view -> real role",
    ["SHADOW", "ORACLE", "AEGIS", "CITIZEN"].includes(gameView.json.ownRole),
  );

  // 6. Unknown room + too-few-players are rejected.
  const badJoin = await post(`${API}/ZZZZZ/join`, { name: "X" });
  check("join unknown room -> 409", badJoin.status === 409);
  const solo = await post(API, { name: "Solo" });
  const soloStart = await post(`${API}/${solo.json.code}/start`);
  check("start with 1 player -> 409", soloStart.status === 409);

  console.log(`\n${passed} checks passed${process.exitCode ? " (with failures)" : ""}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
