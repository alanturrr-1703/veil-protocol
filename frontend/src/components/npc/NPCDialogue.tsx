import { useState } from "react";
import { HologramCard } from "../ui/HologramCard";
import { MemoryViewer } from "./MemoryViewer";
import { useGameStore } from "../../stores/gameStore";
import type { Intent } from "../../types/Event";

interface Props {
  send: (intent: Intent) => void;
}

const NPC_ID = "n1";
const NPC_NAME = "Old Kesh";

/** Question the NPC witness. Answers come from the redacted view (server-authoritative). */
export function NPCDialogue({ send }: Props) {
  const [topic, setTopic] = useState("last night");
  const answers = useGameStore((s) => s.view?.ownNpcAnswers?.[NPC_ID] ?? []);

  return (
    <HologramCard title={`Witness // ${NPC_NAME}`} accent="violet">
      <div className="mb-3 flex gap-2">
        <input
          value={topic}
          onChange={(e) => setTopic(e.target.value)}
          placeholder="ask about…"
          className="flex-1 rounded bg-black/40 px-3 py-2 text-xs text-slate-200 outline-none ring-1 ring-white/10 focus:ring-neon-violet/60"
        />
        <button
          className="btn"
          onClick={() => topic.trim() && send({ type: "QUERY_NPC", npcId: NPC_ID, topic: topic.trim() })}
        >
          Ask
        </button>
      </div>
      <MemoryViewer observations={answers} />
      <p className="mt-2 text-[10px] text-slate-500">
        NPCs never lie and only recall what they witnessed — try "shadow" to see them refuse.
      </p>
    </HologramCard>
  );
}
