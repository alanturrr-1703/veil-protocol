/** Backend endpoints. Override via Vite env vars if the backend runs elsewhere. */
export const API_BASE =
  import.meta.env.VITE_API_BASE ?? "http://localhost:8080/api";

export const WS_BASE =
  import.meta.env.VITE_WS_BASE ?? "ws://localhost:8080/ws/game";

/** Fixed 8-operative roster for the demo match the backend creates (2 Shadows hidden among them). */
export const DEMO_PLAYERS = [
  { id: "p1", name: "Vex" },
  { id: "p2", name: "Nyx" },
  { id: "p3", name: "Mara" },
  { id: "p4", name: "Dax" },
  { id: "p5", name: "Ilya" },
  { id: "p6", name: "Juno" },
  { id: "p7", name: "Rook" },
  { id: "p8", name: "Echo" },
] as const;
