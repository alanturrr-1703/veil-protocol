/** Backend endpoints. Override via Vite env vars if the backend runs elsewhere. */
export const API_BASE =
  import.meta.env.VITE_API_BASE ?? "http://localhost:8080/api";

export const WS_BASE =
  import.meta.env.VITE_WS_BASE ?? "ws://localhost:8080/ws/game";

/** Fewest players a room can start with (mirrors the backend RoleDealer.MIN_PLAYERS). */
export const MIN_PLAYERS = 3;
