import { HologramCard } from "../ui/HologramCard";
import { useGameStore } from "../../stores/gameStore";
import { CITY_MAP } from "../../types/Location";

const districtName = (id?: string) =>
  CITY_MAP.find((l) => l.id === id)?.name ?? id ?? "—";

/**
 * The eight operatives at a glance: who's alive, where they are, and which seats are AI
 * (Ollama) vs human. All of this is PUBLIC state from the redacted view — no roles.
 */
export function Roster() {
  const view = useGameStore((s) => s.view);
  const me = useGameStore((s) => s.playerId);
  if (!view) return null;

  const ids = Object.keys(view.names ?? {});

  return (
    <HologramCard title="Operatives" accent="cyan">
      <ul className="space-y-1.5">
        {ids.map((id) => {
          const alive = view.roster?.[id] ?? true;
          const isMe = id === me;
          const isHuman = (view.humans ?? []).includes(id);
          const dot = !alive
            ? "bg-slate-600"
            : isMe
              ? "bg-neon-cyan"
              : isHuman
                ? "bg-neon-lime"
                : "bg-neon-pink";
          return (
            <li
              key={id}
              className={`flex items-center justify-between text-xs ${alive ? "" : "opacity-45"}`}
            >
              <span className="flex items-center gap-2">
                <span className={`h-2 w-2 rounded-full ${dot}`} />
                <span className="text-white">{view.names[id]}</span>
                <span className="text-[9px] uppercase tracking-widest text-slate-500">
                  {isMe ? "you" : isHuman ? "human" : "ai"}
                </span>
              </span>
              <span className="text-[10px] text-slate-400">
                {alive ? districtName(view.positions?.[id]) : "eliminated"}
              </span>
            </li>
          );
        })}
      </ul>
    </HologramCard>
  );
}
