import { useCallback, useEffect, useState } from "react";
import { HologramCard } from "../ui/HologramCard";
import { gameClient } from "../../api/gameClient";
import type { LeaderboardRow } from "../../types/Player";

/**
 * Public, cross-match standings. This is aggregate history with NO per-viewer secret,
 * so unlike the game view it is fetched over plain REST (`GET /api/leaderboard`) and is
 * safe to show anyone. Rows arrive pre-ranked from the backend; we render them as-is.
 *
 * `refreshKey` lets a parent force a re-fetch (e.g. right after a match is resolved).
 */
export function Leaderboard({ refreshKey = 0 }: { refreshKey?: number }) {
  const [rows, setRows] = useState<LeaderboardRow[]>([]);
  const [error, setError] = useState(false);

  const load = useCallback(async () => {
    try {
      const lb = await gameClient.leaderboard();
      setRows(lb.rows);
      setError(false);
    } catch {
      setError(true);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  return (
    <HologramCard title="City Ledger // Standings" accent="amber" className="flex flex-col">
      <div className="mb-2 flex items-center justify-between">
        <p className="text-[11px] text-slate-400">Cross-match reputation across Neon City.</p>
        <button className="btn text-[10px]" onClick={load}>
          ↻ Refresh
        </button>
      </div>

      {error && <p className="text-xs text-neon-pink">Backend unreachable on :8080.</p>}

      {!error && rows.length === 0 && (
        <p className="text-xs italic text-slate-500">
          No matches recorded yet. Resolve a match to write the ledger.
        </p>
      )}

      {rows.length > 0 && (
        <table className="w-full text-xs">
          <thead>
            <tr className="text-[10px] uppercase tracking-widest text-slate-500">
              <th className="py-1 text-left">#</th>
              <th className="py-1 text-left">Operative</th>
              <th className="py-1 text-right">Pts</th>
              <th className="py-1 text-right">W</th>
              <th className="py-1 text-right">GP</th>
              <th className="py-1 text-right">Win%</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.playerId} className="border-t border-white/5">
                <td className="py-1 text-neon-amber">{r.rank}</td>
                <td className="py-1 text-white">{r.displayName}</td>
                <td className="py-1 text-right font-semibold text-neon-cyan">{r.points}</td>
                <td className="py-1 text-right text-slate-300">{r.wins}</td>
                <td className="py-1 text-right text-slate-400">{r.gamesPlayed}</td>
                <td className="py-1 text-right text-slate-400">
                  {Math.round(r.winRate * 100)}%
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </HologramCard>
  );
}
