import { create } from "zustand";
import type { PlayerView } from "../types/Player";

/**
 * Client state = a direct mirror of the server's redacted PlayerView plus local UI flags.
 * The client renders whatever it is given; it never computes or infers hidden information.
 */
interface GameState {
  gameId: string | null;
  playerId: string | null;
  view: PlayerView | null;
  commitments: Record<string, string>;
  connected: boolean;
  roleSeen: boolean;

  setSession: (gameId: string, playerId: string | null) => void;
  setView: (view: PlayerView) => void;
  setCommitments: (c: Record<string, string>) => void;
  setConnected: (v: boolean) => void;
  markRoleSeen: () => void;
  reset: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  gameId: null,
  playerId: null,
  view: null,
  commitments: {},
  connected: false,
  roleSeen: false,

  setSession: (gameId, playerId) => set({ gameId, playerId }),
  setView: (view) => set({ view }),
  setCommitments: (commitments) => set({ commitments }),
  setConnected: (connected) => set({ connected }),
  markRoleSeen: () => set({ roleSeen: true }),
  reset: () =>
    set({
      gameId: null,
      playerId: null,
      view: null,
      commitments: {},
      connected: false,
      roleSeen: false,
    }),
}));
