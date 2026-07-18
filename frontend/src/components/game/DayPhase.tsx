import { HologramCard } from "../ui/HologramCard";
import { useGameStore } from "../../stores/gameStore";
import { DEMO_PLAYERS } from "../../config";

const nameOf = (id: string) => DEMO_PLAYERS.find((p) => p.id === id)?.name ?? id;

/**
 * Evidence board — the player's confidential investigation results (Oracle) surfaced from
 * the redacted view. Other players never see these entries.
 */
export function EvidenceBoard() {
  const investigations = useGameStore((s) => s.view?.ownInvestigations ?? {});
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
