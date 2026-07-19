import { useEffect } from "react";
import { useGameStore } from "../../stores/gameStore";

/**
 * Match status bar. The match is server-driven: phases auto-advance on the backend clock and
 * the confidential referee ends the game, so there are no manual advance/resolve controls.
 * This shows the live phase, announces the winner at GAME_OVER (refreshing the leaderboard),
 * and lets the player leave for a new room.
 */
export function DirectorControls() {
  const phase = useGameStore((s) => s.view?.phase ?? "NONE");
  const connected = useGameStore((s) => s.connected);
  const bumpLeaderboard = useGameStore((s) => s.bumpLeaderboard);
  const reset = useGameStore((s) => s.reset);

  const over = phase === "GAME_OVER";

  // When the match ends, refresh the cross-match standings once.
  useEffect(() => {
    if (over) bumpLeaderboard();
  }, [over, bumpLeaderboard]);

  return (
    <div className="glass flex flex-wrap items-center gap-3 rounded-xl px-3 py-2 text-xs">
      <span className="flex items-center gap-1.5 uppercase tracking-widest text-slate-400">
        <span
          className={`h-1.5 w-1.5 rounded-full ${connected ? "bg-neon-lime" : "bg-neon-pink"}`}
        />
        {connected ? "linked" : "offline"}
      </span>
      <span className="uppercase tracking-widest text-neon-cyan">{phase}</span>
      {over && (
        <span className="text-[10px] uppercase tracking-widest text-neon-amber">
          match over
        </span>
      )}
      <span className="mx-1 h-4 w-px bg-white/10" />
      <button className="btn-danger" onClick={reset}>Leave</button>
    </div>
  );
}
