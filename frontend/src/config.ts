/** Backend endpoints. Override via Vite env vars if the backend runs elsewhere. */
export const API_BASE =
  import.meta.env.VITE_API_BASE ?? "http://localhost:8080/api";

export const WS_BASE =
  import.meta.env.VITE_WS_BASE ?? "ws://localhost:8080/ws/game";

/** Fixed roster for the demo match the backend creates. */
export const DEMO_PLAYERS = [
  { id: "p1", name: "Vex" },
  { id: "p2", name: "Mara" },
  { id: "p3", name: "Ilya" },
  { id: "p4", name: "Dax" },
] as const;
