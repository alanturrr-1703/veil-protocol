import type { Observation } from "./Event";

/** The chat channels — mirrors the Java `ChatChannel` enum. */
export type ChatChannel = "DAY" | "SHADOW" | "DEAD" | "SYSTEM" | "DIRECT";

/** One chat line — mirrors the Java `ChatMessage` record (already redacted per viewer). */
export interface ChatMessage {
  senderId: string;
  senderName: string;
  channel: ChatChannel;
  phaseWhenSent: GamePhase;
  tick: number;
  seq: number;
  text: string;
  toId?: string | null; // recipient for a DIRECT whisper
}

/**
 * The redacted, per-viewer snapshot pushed by the backend. It mirrors the Java
 * `PlayerView` record exactly. It contains ONLY what this viewer is authorized to see:
 * their own role, their own investigations, their own NPC answers, the chat lines they
 * may read, the channels they may post to — plus public state.
 */
export interface PlayerView {
  viewerId: string;
  phase: GamePhase;
  phaseEndsAt: number; // epoch millis the phase auto-ends (0 = untimed)
  announcements: string[];
  roster: Record<string, boolean>; // playerId -> alive (public)
  names: Record<string, string>; // playerId -> display name (public)
  humans: string[]; // human-controlled seats; the rest are AI
  districtCounts: Record<string, number>; // districtId -> living count (public, anonymous)
  viewerDistrict: string | null; // where the viewer is
  viewerRoom: string | null; // which room the viewer is in
  rooms: Record<string, string>; // rooms of the viewer's district: roomId -> name
  positions: Record<string, string>; // VISIBLE players (share your room): playerId -> districtId
  coords: Record<string, number[]>; // VISIBLE players' room positions: playerId -> [x,y]
  npcsHere: Record<string, string>; // NPCs sharing your room: npcId -> name
  ownRole: string;
  ownInvestigations: Record<string, string>; // targetId -> faction
  ownNpcAnswers: Record<string, Observation[]>; // npcId -> observations
  readableChat: ChatMessage[]; // already filtered by ChatPolicy
  postableChannels: ChatChannel[]; // channels this viewer may post to now
}

/** One ranked row of the public leaderboard — mirrors Java `LeaderboardView.Row`. */
export interface LeaderboardRow {
  rank: number;
  playerId: string;
  displayName: string;
  points: number;
  gamesPlayed: number;
  wins: number;
  winRate: number;
}

export interface LeaderboardView {
  rows: LeaderboardRow[];
}

export type GamePhase =
  | "LOBBY"
  | "NIGHT"
  | "DAY"
  | "VOTING"
  | "RESULT"
  | "NONE";

export type RoleName = "SHADOW" | "ORACLE" | "AEGIS" | "CITIZEN" | "UNKNOWN";
