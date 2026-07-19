/**
 * Commitment primitives that mirror what Compact's `persistentHash` does inside a
 * circuit. A commitment is HIDING (you can't recover the input) and BINDING (you can't
 * later claim a different input). The 32-byte `salt` is the blinding factor — without it,
 * a role (only 4 possible values) would be trivially brute-forceable from its hash.
 *
 * In production these exact bytes are hashed inside the ZK circuit; here we compute them
 * in the clear only so the demo can print and check them.
 */

import { createHash, randomBytes } from "node:crypto";

export type Hex = string;

function sha256(buf: Buffer): Hex {
  return createHash("sha256").update(buf).digest("hex");
}

/** Encode a small integer (a role) as a 32-byte big-endian field element. */
function fieldFromRole(role: number): Buffer {
  const b = Buffer.alloc(32);
  b.writeUInt32BE(role >>> 0, 28);
  return b;
}

/** A fresh, unpredictable 256-bit salt. */
export function randomSalt(): Hex {
  return randomBytes(32).toString("hex");
}

/**
 * roleHash(role, salt) — the on-chain commitment. Equivalent to the Compact:
 *   persistentHash<Vector<2, Bytes<32>>>([(role as Field) as Bytes<32>, salt])
 */
export function roleHash(role: number, salt: Hex): Hex {
  return sha256(Buffer.concat([fieldFromRole(role), Buffer.from(salt, "hex")]));
}

/** Bind an action to a (target, nonce) pair without revealing the target. */
export function actionCommit(targetId: string, nonce: Hex): Hex {
  return sha256(Buffer.concat([Buffer.from(targetId, "utf8"), Buffer.from(nonce, "hex")]));
}
