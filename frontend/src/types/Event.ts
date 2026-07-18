/** A memory fragment an NPC shares — mirrors the backend Observation record. */
export interface Observation {
  subject: string;
  action: string;
  locationId: string;
  tick: number;
  confidence: number;
  suspects: string[];
}

import type { ChatChannel } from "./Player";

/** Intent messages the client sends over the WebSocket. */
export type Intent =
  | { type: "MOVE"; toLocationId: string }
  | { type: "ENTER_ROOM"; roomId: string }
  | { type: "POS"; x: number; y: number }
  | { type: "WHISPER"; targetId: string; text: string }
  | { type: "ATTACK"; targetId: string }
  | { type: "SHIELD"; targetId: string }
  | { type: "INVESTIGATE"; targetId: string }
  | { type: "QUERY_NPC"; npcId: string; topic: string }
  | { type: "VOTE"; targetId: string }
  | { type: "CHAT"; channel: ChatChannel; text: string };
