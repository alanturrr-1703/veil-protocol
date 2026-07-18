import { useGameStore } from "../stores/gameStore";

/**
 * Surfaces the confidential-layer status to the UI. Roles live in Midnight (or the local
 * mock); the client only ever sees COMMITMENTS (hashes) here — never the roles themselves.
 */
export function useMidnight() {
  const commitments = useGameStore((s) => s.commitments);

  return {
    commitments,
    committedPlayers: Object.keys(commitments),
    shortHash: (playerId: string) =>
      (commitments[playerId] ?? "").slice(0, 10) || "—",
  };
}
