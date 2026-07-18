import { HologramCard } from "../ui/HologramCard";
import { useGameState } from "../../hooks/useGameState";
import { DEMO_PLAYERS } from "../../config";
import type { Intent } from "../../types/Event";

interface Props {
  send: (intent: Intent) => void;
}

const nameOf = (id: string) => DEMO_PLAYERS.find((p) => p.id === id)?.name ?? id;

/** Night action console — shows only the actions the player's role can perform. */
export function ActionBar({ send }: Props) {
  const { role, phase, playerId, alivePlayers } = useGameState();
  const isNight = phase === "NIGHT";
  const targets = alivePlayers.filter((id) => id !== playerId);

  const roleAction: Record<string, { label: string; make: (id: string) => Intent } | undefined> = {
    SHADOW: { label: "Attack", make: (id) => ({ type: "ATTACK", targetId: id }) },
    ORACLE: { label: "Investigate", make: (id) => ({ type: "INVESTIGATE", targetId: id }) },
    AEGIS: { label: "Shield", make: (id) => ({ type: "SHIELD", targetId: id }) },
    CITIZEN: undefined,
  };

  const action = roleAction[role];

  return (
    <HologramCard title="Night Ops" accent="amber">
      {!isNight && <p className="text-xs text-slate-500">// powers arm during the NIGHT phase</p>}

      {action ? (
        <>
          <p className="mb-2 text-xs uppercase tracking-widest text-neon-amber">{action.label}</p>
          <div className="grid grid-cols-3 gap-2">
            {targets.map((id) => (
              <button
                key={id}
                disabled={!isNight}
                onClick={() => send(action.make(id))}
                className="btn justify-center"
              >
                {nameOf(id)}
              </button>
            ))}
          </div>
        </>
      ) : (
        <p className="text-xs text-slate-400">Citizens have no night power — gather evidence.</p>
      )}

      <div className="mt-4">
        <p className="mb-2 text-xs uppercase tracking-widest text-neon-cyan">Move</p>
        <div className="flex gap-2">
          <button className="btn" onClick={() => send({ type: "MOVE", toLocationId: "docks" })}>
            → Rust Docks
          </button>
          <button className="btn" onClick={() => send({ type: "MOVE", toLocationId: "plaza" })}>
            → Neon Plaza
          </button>
        </div>
      </div>
    </HologramCard>
  );
}
