/**
 * Veil Protocol — Midnight Relayer (sidecar)
 * ===========================================
 * The Java backend is authoritative for PUBLIC state but cannot speak Midnight directly
 * (Midnight's DApp SDK is TypeScript/`midnight.js`). This Node sidecar is the RELAYER: it
 * holds the wallet, talks to the proof server / testnet, and exposes a tiny HTTP API that
 * the Java `MidnightConfidentialGateway` calls. Players never connect wallets — the relayer
 * submits every confidential transaction on their behalf.
 *
 *   Java backend  --HTTP-->  this relayer  --midnight.js-->  proof server / testnet
 *
 * WHAT RUNS TODAY: to keep the whole stack runnable locally with no toolchain, the confidential
 * operations are computed here with the SAME commitment primitives the Compact circuits use
 * (see ../src/crypto/commitment.ts). Every place a real zk-transaction belongs is marked
 * `// [MIDNIGHT]` — swapping in `midnight.js` there makes the boolean checks real zk-SNARKs
 * without changing the HTTP contract the Java side depends on.
 *
 * Run:  node relayer/server.ts        (from midnight/, Node >= 23)  or  npm run relayer
 */

import http from "node:http";
import { roleHash, randomSalt, actionCommit } from "../src/crypto/commitment.ts";
import { Role, Faction } from "../src/state/PrivateState.ts";

const PORT = process.env.PORT ? Number(process.env.PORT) : 6301;

const ROLE_BY_NAME: Record<string, number> = {
  SHADOW: Role.SHADOW,
  ORACLE: Role.ORACLE,
  AEGIS: Role.AEGIS,
  CITIZEN: Role.CITIZEN,
};

/**
 * The confidential store. In a real deployment the `commitment` is the value written to the
 * Midnight ledger, and `{role, salt}` is the off-chain witness on the relayer wallet. Here it
 * doubles as both so the ZK checks can be evaluated locally.
 */
interface Secret { role: number; salt: string; commitment: string; }
const secrets = new Map<string, Secret>();

// --- Confidential operations (mirror the Compact circuits) -------------------

/** registerRole: publish only a commitment; keep {role, salt} as the private witness. */
function commit(playerId: string, roleName: string): string {
  const role = ROLE_BY_NAME[roleName] ?? Role.CITIZEN;
  const salt = randomSalt();
  const commitment = roleHash(role, salt);
  // [MIDNIGHT] submit registerRole(playerId) tx via midnight.js; the ledger stores `commitment`.
  secrets.set(playerId, { role, salt, commitment });
  return commitment;
}

/** proveInvestigation: disclose ONLY the target's faction bit, never the exact role. */
function investigate(targetId: string): string {
  const s = secrets.get(targetId);
  if (!s) return "NEUTRAL";
  // [MIDNIGHT] run proveInvestigation(targetId); read back the disclosed faction from the ledger.
  return s.role === Role.SHADOW ? "SHADOW" : "CITY";
}

/** submitShadowAttack: prove a committed Shadow authorized this (hidden) target. */
function attack(attackerId: string, targetId: string): { authorized: boolean; opaqueRef: string; message: string } {
  const s = secrets.get(attackerId);
  // [MIDNIGHT] run submitShadowAttack(actionId, attackerId, hash(targetId)); proof asserts role==SHADOW.
  if (!s || s.role !== Role.SHADOW) {
    return { authorized: false, opaqueRef: "", message: "attack rejected: submitter is not a Shadow" };
  }
  const opaqueRef = actionCommit(targetId, randomSalt()); // ledger stores only this opaque bind
  return { authorized: true, opaqueRef, message: "attack authorized by a valid Shadow" };
}

/** win-condition circuit: from the PUBLIC alive-set, disclose only the winning faction. */
function resolveWinner(aliveIds: string[]): string {
  let aliveShadows = 0;
  let aliveCity = 0;
  for (const id of aliveIds) {
    const s = secrets.get(id);
    if (!s) continue;
    if (s.role === Role.SHADOW) aliveShadows++;
    else aliveCity++;
  }
  // [MIDNIGHT] a win-check circuit with the alive commitments as public input, roles as witness.
  if (aliveShadows === 0) return "CITY";
  if (aliveShadows >= aliveCity) return "SHADOW";
  return "NEUTRAL";
}

function verifyWin(faction: string): boolean {
  const roles = [...secrets.values()].map((s) => s.role);
  if (faction === "CITY") return !roles.includes(Role.SHADOW);
  const want = faction === "SHADOW" ? Faction.SHADOW : Faction.CITY;
  return roles.some((r) => (r === Role.SHADOW ? Faction.SHADOW : Faction.CITY) === want);
}

// --- Minimal HTTP transport (zero dependencies) ------------------------------

function send(res: http.ServerResponse, code: number, body: unknown): void {
  const json = JSON.stringify(body);
  res.writeHead(code, { "Content-Type": "application/json" });
  res.end(json);
}

function readBody(req: http.IncomingMessage): Promise<any> {
  return new Promise((resolve) => {
    let data = "";
    req.on("data", (c) => (data += c));
    req.on("end", () => {
      try { resolve(data ? JSON.parse(data) : {}); } catch { resolve({}); }
    });
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://localhost:${PORT}`);
  const path = url.pathname;

  try {
    if (req.method === "GET" && path === "/health") {
      return send(res, 200, { ok: true, committed: secrets.size });
    }
    if (req.method === "GET" && path === "/commitment") {
      const id = url.searchParams.get("playerId") ?? "";
      return send(res, 200, { commitment: secrets.get(id)?.commitment ?? "" });
    }
    if (req.method === "POST") {
      const body = await readBody(req);
      switch (path) {
        case "/commit":
          return send(res, 200, { commitment: commit(body.playerId, body.role) });
        case "/investigate":
          return send(res, 200, { faction: investigate(body.targetId) });
        case "/attack":
          return send(res, 200, attack(body.attackerId, body.targetId));
        case "/win":
          return send(res, 200, { faction: resolveWinner(body.aliveIds ?? []) });
        case "/verifyWin":
          return send(res, 200, { ok: verifyWin(body.faction) });
      }
    }
    send(res, 404, { error: "not found" });
  } catch (e) {
    send(res, 500, { error: String(e) });
  }
});

server.listen(PORT, () => {
  console.log(`[veil-relayer] Midnight relayer listening on http://localhost:${PORT}`);
  console.log(`[veil-relayer] local commitment mode — see // [MIDNIGHT] markers to wire the testnet`);
});
