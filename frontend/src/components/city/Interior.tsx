import { HologramCard } from "../ui/HologramCard";
import { useGameState } from "../../hooks/useGameState";
import { CITY_MAP } from "../../types/Location";

const districtName = (id?: string | null) => CITY_MAP.find((l) => l.id === id)?.name ?? id ?? "—";

/**
 * Your immediate surroundings. Everything here is room-scoped: you only ever see the
 * operatives and NPCs who share your exact room. Duck into a side room to slip out of
 * sight of the commons — and to sniff out anyone (or any NPC) hiding there.
 */
export function Interior() {
  const { view, playerId, role } = useGameState();
  if (!view || !view.viewerDistrict) return null;

  const here = view.viewerDistrict;
  const room = view.viewerRoom ?? "commons";
  const rooms = view.rooms ?? {};
  const others = Object.keys(view.positions ?? {}).filter((id) => id !== playerId);
  const npcs = Object.entries(view.npcsHere ?? {});
  const mustRelocate = role === "CITIZEN" || role === "ORACLE";

  return (
    <HologramCard title="Surroundings" accent="violet">
      <p className="text-xs text-slate-300">
        You are in <span className="text-neon-cyan">{districtName(here)}</span>
        {" · "}
        <span className="text-neon-violet">{rooms[room] ?? room}</span>
      </p>

      <div className="mt-3">
        <p className="mb-1 text-[10px] uppercase tracking-widest text-slate-500">In this room</p>
        {others.length === 0 && npcs.length === 0 ? (
          <p className="text-xs italic text-slate-500">No one else is here. You're alone.</p>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {others.map((id) => (
              <span key={id} className="chip text-neon-cyan">
                {view.names[id]}
              </span>
            ))}
            {npcs.map(([id, name]) => (
              <span key={id} className="chip text-neon-amber">
                {name} (NPC)
              </span>
            ))}
          </div>
        )}
      </div>

      <p className="mt-3 text-[10px] italic text-slate-500">
        Use the glowing doorways on the room stage to move between rooms.
      </p>

      {mustRelocate && (
        <p className="mt-3 text-[11px] text-neon-pink">
          ⚠ As a {role.toLowerCase()}, you can't spend two nights in the same district — relocate
          before nightfall or you'll be moved automatically.
        </p>
      )}
    </HologramCard>
  );
}
