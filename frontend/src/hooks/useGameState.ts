import { useGameStore } from "../stores/gameStore";

/** Convenience selectors derived from the redacted view. */
export function useGameState() {
  const view = useGameStore((s) => s.view);
  const playerId = useGameStore((s) => s.playerId);

  const alivePlayers = view
    ? Object.entries(view.roster).filter(([, alive]) => alive).map(([id]) => id)
    : [];

  return {
    view,
    playerId,
    phase: view?.phase ?? "NONE",
    role: view?.ownRole ?? "UNKNOWN",
    alivePlayers,
    announcements: view?.announcements ?? [],
  };
}
