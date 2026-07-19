import { API_BASE } from "../config";
import type { LeaderboardView, PlayerView } from "../types/Player";

/** Response when creating or joining a room. */
export interface RoomJoinResponse {
  code: string;
  playerId: string;
  isHost: boolean;
  phase: string;
}

/** Response when the host starts the match — public role commitments (hashes) only. */
export interface StartResponse {
  phase: string;
  commitments: Record<string, string>;
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`POST ${path} -> ${res.status}`);
  return res.json() as Promise<T>;
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`);
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}`);
  return res.json() as Promise<T>;
}

/**
 * REST client for the room lifecycle (lobby / matchmaking). All real-time play — movement,
 * night actions, votes, chat — happens over the WebSocket once connected with the room
 * `code` + `playerId`. The server is authoritative; the client only expresses intents.
 */
export const gameClient = {
  /** Create a room; the caller becomes host. */
  createRoom: (name: string) => post<RoomJoinResponse>("/rooms", { name }),
  /** Join an existing room by its short code while it is still in the lobby. */
  joinRoom: (code: string, name: string) =>
    post<RoomJoinResponse>(`/rooms/${code}/join`, { name }),
  /** Host starts the match: roles are dealt + committed, and the first Night opens. */
  startRoom: (code: string) => post<StartResponse>(`/rooms/${code}/start`),
  /** One-off redacted snapshot (the live stream arrives over the WebSocket). */
  view: (code: string, playerId: string) =>
    get<PlayerView>(`/rooms/${code}/view/${playerId}`),
  /** Public role commitments (hashes) for the room. */
  commitments: (code: string) => get<Record<string, string>>(`/rooms/${code}/commitments`),
  /** Public, ranked cross-match leaderboard. */
  leaderboard: () => get<LeaderboardView>("/leaderboard"),
};
