/** A node on the neon-city map. Positions are normalized 0..1 for responsive layout. */
export interface MapLocation {
  id: string;
  name: string;
  x: number;
  y: number;
}

/** The demo city the backend builds (plaza + docks). */
export const CITY_MAP: MapLocation[] = [
  { id: "plaza", name: "Neon Plaza", x: 0.32, y: 0.5 },
  { id: "docks", name: "Rust Docks", x: 0.72, y: 0.42 },
];

export const CITY_EDGES: [string, string][] = [["plaza", "docks"]];
