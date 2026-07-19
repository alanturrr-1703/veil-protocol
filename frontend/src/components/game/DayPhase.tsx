import { HologramCard } from "../ui/HologramCard";
import { useGameStore } from "../../stores/gameStore";

/**
 * Evidence board — the player's confidential investigation results (Oracle) surfaced from
 * the redacted view. Other players never see these entries.
 */
export function EvidenceBoard() {
  const investigations = useGameStore((s) => s.view?.ownInvestigations ?? {});
  const names = useGameStore((s) => s.view?.names ?? {});
  const nameOf = (id: string) => names[id] ?? id;
  const entries = Object.entries(investigations);

  return (
    <HologramCard title="Evidence Board" accent="cyan">
      {entries.length === 0 ? (
        <p className="text-xs text-slate-500">// no confirmed intel</p>
      ) : (
        <ul className="space-y-2">
          {entries.map(([target, faction]) => (
            <li key={target} className="flex items-center justify-between text-xs">
              <span className="text-slate-300">{nameOf(target)}</span>
              <span
                className={
                  faction === "SHADOW"
                    ? "chip text-neon-pink"
                    : "chip text-neon-lime"
                }
              >
                {faction}
              </span>
            </li>
          ))}
        </ul>
      )}
    </HologramCard>
  );
}
