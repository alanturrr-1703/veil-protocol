import { useGameStore } from "../../stores/gameStore";
import { gameClient } from "../../api/gameClient";
import { DEMO_PLAYERS } from "../../config";

/**
 * Local "director" controls — drive phase transitions and hot-swap the viewing operative.
 * Switching operative reconnects the socket, which is the clearest way to SEE that each
 * viewer only ever receives their own confidential slice.
 */
export function DirectorControls() {
  const gameId = useGameStore((s) => s.gameId);
  const playerId = useGameStore((s) => s.playerId);
  const setSession = useGameStore((s) => s.setSession);
  const bumpLeaderboard = useGameStore((s) => s.bumpLeaderboard);
  const setLastWinner = useGameStore((s) => s.setLastWinner);
  const lastWinner = useGameStore((s) => s.lastWinner);
  const reset = useGameStore((s) => s.reset);

  if (!gameId) return null;

  const resolve = async () => {
    const res = await gameClient.resolve(gameId);
    setLastWinner(res.decided ? res.winner : "UNDECIDED");
    if (res.decided) bumpLeaderboard();
  };

  return (
    <div className="glass flex flex-wrap items-center gap-2 rounded-xl px-3 py-2 text-xs">
      <span className="uppercase tracking-widest text-slate-400">Director</span>
      <button className="btn" onClick={() => gameClient.start(gameId)}>Start</button>
      <button className="btn" onClick={() => gameClient.advance(gameId)}>Advance ▸</button>
      <button className="btn text-neon-amber" onClick={resolve} title="Confidentially resolve the winner via the Midnight layer">
        ◈ Resolve (Midnight)
      </button>
      {lastWinner && (
        <span className="text-[10px] uppercase tracking-widest text-neon-amber">
          {lastWinner === "UNDECIDED" ? "no winner yet" : `${lastWinner} win`}
        </span>
      )}

      <span className="mx-2 h-4 w-px bg-white/10" />
      <span className="uppercase tracking-widest text-slate-400">View as</span>
      {DEMO_PLAYERS.map((p) => (
        <button
          key={p.id}
          onClick={() => setSession(gameId, p.id)}
          className={`btn ${playerId === p.id ? "neon-border text-neon-cyan" : ""}`}
        >
          {p.name}
        </button>
      ))}

      <span className="mx-2 h-4 w-px bg-white/10" />
      <button className="btn-danger" onClick={reset}>New City</button>
    </div>
  );
}
