import { HologramCard } from "../ui/HologramCard";
import { useGameState } from "../../hooks/useGameState";
import { CITY_MAP } from "../../types/Location";
import type { Intent } from "../../types/Event";

interface Props {
  send: (intent: Intent) => void;
}

/** Night action console — shows only the actions the player's role can perform. */
export function ActionBar({ send }: Props) {
  const { role, phase, playerId, alivePlayers, nameOf, view } = useGameState();
  const isNight = phase === "NIGHT";
  const targets = alivePlayers.filter((id) => id !== playerId);
  const here = playerId ? view?.positions?.[playerId] : undefined;

  const roleAction: Record<string, { label: string; make: (id: string) => Intent } | undefined> = {
    SHADOW: { label: "Attack", make: (id) => ({ type: "ATTACK", targetId: id }) },
    ORACLE: { label: "Investigate", make: (id) => ({ type: "INVESTIGATE", targetId: id }) },
    AEGIS: { label: "Shield", make: (id) => ({ type: "SHIELD", targetId: id }) },
    CITIZEN: undefined,
  };

  const action = roleAction[role];
  // At night you may leave your district only ONCE (walk or teleport); rooms are unlimited.
  const relocateLocked = isNight && view?.relocateAvailable === false;

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
        <p className="mb-2 flex items-center justify-between text-xs uppercase tracking-widest text-neon-cyan">
          <span>Move to adjacent district</span>
          {isNight && (
            <span className={relocateLocked ? "text-neon-pink" : "text-slate-500"}>
              {relocateLocked ? "relocation spent" : "1 / night"}
            </span>
          )}
        </p>
        <div className="grid grid-cols-3 gap-2">
          {CITY_MAP.map((loc) => (
            <button
              key={loc.id}
              disabled={loc.id === here || relocateLocked}
              className={`btn justify-center text-[11px] ${loc.id === here ? "neon-border text-neon-cyan" : ""}`}
              onClick={() => send({ type: "MOVE", toLocationId: loc.id })}
            >
              {loc.id === here ? "◉ " : "→ "}
              {loc.name}
            </button>
          ))}
        </div>
        {isNight && (
          <p className="mt-2 text-[10px] italic text-slate-500">
            Rooms are unlimited — duck between them to dodge a Shadow. Leaving the district (walk or ⚡ teleport) costs your one night move.
          </p>
        )}
      </div>
    </HologramCard>
  );
}
