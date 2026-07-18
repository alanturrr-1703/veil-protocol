import type { Observation } from "./Event";

/**
 * The redacted, per-viewer snapshot pushed by the backend. It mirrors the Java
 * `PlayerView` record exactly. It contains ONLY what this viewer is authorized to see:
 * their own role, their own investigations, their own NPC answers — plus public state.
 */
export interface PlayerView {
  viewerId: string;
  phase: GamePhase;
  announcements: string[];
  roster: Record<string, boolean>; // playerId -> alive
  ownRole: string;
  ownInvestigations: Record<string, string>; // targetId -> faction
  ownNpcAnswers: Record<string, Observation[]>; // npcId -> observations
}

export type GamePhase =
  | "LOBBY"
  | "NIGHT"
  | "DAY"
  | "VOTING"
  | "RESULT"
  | "NONE";

export type RoleName = "SHADOW" | "ORACLE" | "AEGIS" | "CITIZEN" | "UNKNOWN";
