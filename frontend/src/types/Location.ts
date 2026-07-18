/** A node on the neon-city map. Positions are normalized 0..1 for responsive layout. */
export interface MapLocation {
  id: string;
  name: string;
  x: number;
  y: number;
}

/** The six-district neon city the backend builds. Ids MUST match the backend locations. */
export const CITY_MAP: MapLocation[] = [
  { id: "plaza", name: "Neon Plaza", x: 0.2, y: 0.6 },
  { id: "market", name: "Data Market", x: 0.45, y: 0.36 },
  { id: "docks", name: "Rust Docks", x: 0.78, y: 0.26 },
  { id: "tower", name: "Spire Tower", x: 0.82, y: 0.72 },
  { id: "alley", name: "Glitch Alley", x: 0.18, y: 0.28 },
  { id: "garden", name: "Hydro Garden", x: 0.5, y: 0.82 },
];

export const CITY_EDGES: [string, string][] = [
  ["plaza", "market"],
  ["plaza", "alley"],
  ["market", "docks"],
  ["market", "garden"],
  ["docks", "tower"],
  ["alley", "garden"],
  ["garden", "tower"],
];
