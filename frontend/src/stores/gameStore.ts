import { create } from "zustand";
import type { PlayerView } from "../types/Player";

/**
 * Client state = a direct mirror of the server's redacted PlayerView plus local UI flags.
 * The client renders whatever it is given; it never computes or infers hidden information.
 */
interface GameState {
  code: string | null; // the room code (also the server-side session id)
  playerId: string | null;
  isHost: boolean;
  view: PlayerView | null;
  commitments: Record<string, string>;
  connected: boolean;
  roleSeen: boolean;
  leaderboardBump: number; // increment to force a leaderboard re-fetch
  lastWinner: string | null; // faction the confidential layer last resolved

  setSession: (code: string, playerId: string | null, isHost: boolean) => void;
  setView: (view: PlayerView) => void;
  setCommitments: (c: Record<string, string>) => void;
  setConnected: (v: boolean) => void;
  markRoleSeen: () => void;
  bumpLeaderboard: () => void;
  setLastWinner: (faction: string | null) => void;
  reset: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  code: null,
  playerId: null,
  isHost: false,
  view: null,
  commitments: {},
  connected: false,
  roleSeen: false,
  leaderboardBump: 0,
  lastWinner: null,

  setSession: (code, playerId, isHost) => set({ code, playerId, isHost }),
  setView: (view) => set({ view }),
  setCommitments: (commitments) => set({ commitments }),
  setConnected: (connected) => set({ connected }),
  markRoleSeen: () => set({ roleSeen: true }),
  bumpLeaderboard: () => set((s) => ({ leaderboardBump: s.leaderboardBump + 1 })),
  setLastWinner: (lastWinner) => set({ lastWinner }),
  reset: () =>
    set({
      code: null,
      playerId: null,
      isHost: false,
      view: null,
      commitments: {},
      connected: false,
      roleSeen: false,
      lastWinner: null,
    }),
}));
