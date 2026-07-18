import { HologramCard } from "../ui/HologramCard";
import { useGameState } from "../../hooks/useGameState";
import { useGameStore } from "../../stores/gameStore";
import { DEMO_PLAYERS } from "../../config";

const ROLE_ACCENT: Record<string, string> = {
  SHADOW: "text-neon-pink neon-text-pink",
  ORACLE: "text-neon-cyan neon-text",
  AEGIS: "text-neon-lime",
  CITIZEN: "text-slate-300",
  UNKNOWN: "text-slate-500",
};

/** Heads-up display: identity, secret role, phase, and connection status. */
export function PlayerHUD() {
  const { role, phase, playerId } = useGameState();
  const connected = useGameStore((s) => s.connected);
  const view = useGameStore((s) => s.view);
  const name = DEMO_PLAYERS.find((p) => p.id === playerId)?.name ?? playerId ?? "—";
  const alive = playerId ? view?.roster?.[playerId] ?? true : true;

  return (
    <HologramCard title="Operative" accent="cyan">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-2xl font-semibold tracking-wide text-white">{name}</p>
          <p className={`text-sm uppercase tracking-[0.3em] ${ROLE_ACCENT[role] ?? ""}`}>
            {role}
          </p>
        </div>
        <div className="text-right text-xs">
          <p className="text-slate-400">PHASE</p>
          <p className="text-lg font-semibold text-neon-cyan neon-text">{phase}</p>
        </div>
      </div>
      <div className="mt-3 flex items-center gap-2 text-xs">
        <span className={`chip ${connected ? "text-neon-lime" : "text-neon-pink"}`}>
          {connected ? "◉ LINK ACTIVE" : "○ OFFLINE"}
        </span>
        <span className={`chip ${alive ? "text-neon-cyan" : "text-neon-pink"}`}>
          {alive ? "ALIVE" : "ELIMINATED"}
        </span>
      </div>
    </HologramCard>
  );
}
