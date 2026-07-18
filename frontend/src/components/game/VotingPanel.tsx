import { HologramCard } from "../ui/HologramCard";
import { useGameState } from "../../hooks/useGameState";
import type { Intent } from "../../types/Event";

interface Props {
  send: (intent: Intent) => void;
}

/** Voting screen: exile a suspect. Enabled only during the VOTING phase. */
export function VotingPanel({ send }: Props) {
  const { phase, playerId, alivePlayers, nameOf } = useGameState();
  const active = phase === "VOTING";
  const candidates = alivePlayers.filter((id) => id !== playerId);

  return (
    <HologramCard title="Tribunal" accent="pink">
      {!active && (
        <p className="text-xs text-slate-500">// voting opens during the VOTING phase</p>
      )}
      <div className="grid grid-cols-2 gap-2">
        {candidates.map((id) => {
          const name = nameOf(id);
          return (
            <button
              key={id}
              disabled={!active}
              onClick={() => send({ type: "VOTE", targetId: id })}
              className="btn-danger justify-center"
            >
              Exile {name}
            </button>
          );
        })}
      </div>
    </HologramCard>
  );
}
