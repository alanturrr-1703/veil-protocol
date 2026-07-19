import type { Observation } from "../../types/Event";
import { useGameStore } from "../../stores/gameStore";

/** Renders the memory fragments an NPC shared — including the fuzzy suspect list. */
export function MemoryViewer({ observations }: { observations: Observation[] }) {
  const names = useGameStore((s) => s.view?.names ?? {});
  const nameOf = (id: string) => names[id] ?? id;
  if (!observations || observations.length === 0) {
    return <p className="text-xs text-slate-500">// no memories shared yet</p>;
  }
  return (
    <ul className="space-y-2">
      {observations.map((o, i) => (
        <li key={i} className="rounded border border-white/10 bg-black/30 p-2 text-xs">
          <span className="text-neon-cyan">{nameOf(o.subject)}</span>{" "}
          <span className="text-slate-300">{o.action}</span>{" "}
          <span className="text-slate-500">@ {o.locationId}</span>
          {o.suspects.length > 0 && (
            <div className="mt-1 text-neon-amber">
              possible culprits: {o.suspects.map(nameOf).join(", ")}
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}
