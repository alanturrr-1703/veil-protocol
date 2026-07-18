import { HologramCard } from "../ui/HologramCard";
import { Terminal } from "../ui/Terminal";
import { useGameState } from "../../hooks/useGameState";

/** Public narration feed — the append-only stream of announcements from the engine. */
export function Timeline() {
  const { announcements } = useGameState();
  return (
    <HologramCard title="City Feed" accent="cyan" className="flex-1">
      <Terminal lines={announcements} />
    </HologramCard>
  );
}
