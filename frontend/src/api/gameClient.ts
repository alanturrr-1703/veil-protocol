import { API_BASE } from "../config";
import type { PlayerView } from "../types/Player";

export interface CreateGameResponse {
  gameId: string;
  phase: string;
  commitments: Record<string, string>;
}

async function post<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, { method: "POST" });
  if (!res.ok) throw new Error(`POST ${path} -> ${res.status}`);
  return res.json() as Promise<T>;
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`);
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}`);
  return res.json() as Promise<T>;
}

/** REST client for match lifecycle. Real-time state arrives over the WebSocket instead. */
export const gameClient = {
  createGame: () => post<CreateGameResponse>("/games"),
  start: (id: string) => post<{ phase: string }>(`/games/${id}/start`),
  advance: (id: string) => post<{ phase: string }>(`/games/${id}/advance`),
  view: (id: string, playerId: string) =>
    get<PlayerView>(`/games/${id}/view/${playerId}`),
  commitments: (id: string) => get<Record<string, string>>(`/games/${id}/commitments`),
  investigate: (id: string, oracle: string, target: string) =>
    post<{ target: string; faction: string }>(
      `/games/${id}/investigate?oracle=${oracle}&target=${target}`,
    ),
};
