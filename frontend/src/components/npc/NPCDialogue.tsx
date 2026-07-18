import { useMemo, useState } from "react";
import { HologramCard } from "../ui/HologramCard";
import { MemoryViewer } from "./MemoryViewer";
import { useGameState } from "../../hooks/useGameState";
import type { Intent } from "../../types/Event";

interface Props {
  send: (intent: Intent) => void;
}

/**
 * Talk to a witness. You can only question an NPC standing in your own room
 * (view.npcsHere). The NPC answers in natural language, grounded in what it saw —
 * it never lies and never names a role — and the conversation streams back as a
 * private DIRECT thread on the redacted view.
 */
export function NPCDialogue({ send }: Props) {
  const { view, playerId } = useGameState();
  const [topic, setTopic] = useState("what did you see last night");

  const npcs = view?.npcsHere ?? {};
  const npcIds = Object.keys(npcs);
  const [selected, setSelected] = useState<string | null>(null);
  const npcId = selected && npcs[selected] ? selected : npcIds[0] ?? null;
  const npcName = npcId ? npcs[npcId] : null;

  // The spoken conversation with this NPC = DIRECT lines to/from it.
  const thread = useMemo(() => {
    if (!npcId) return [];
    return (view?.readableChat ?? [])
      .filter((m) => m.channel === "DIRECT" && (m.senderId === npcId || m.toId === npcId))
      .sort((a, b) => a.seq - b.seq);
  }, [view?.readableChat, npcId]);

  const answers = view?.ownNpcAnswers?.[npcId ?? ""] ?? [];

  if (npcIds.length === 0) {
    return (
      <HologramCard title="Witness // —" accent="violet">
        <p className="text-xs text-slate-500">
          No witness in this room. Move through the districts and step into rooms to find someone
          who saw something.
        </p>
      </HologramCard>
    );
  }

  const ask = () => {
    if (npcId && topic.trim()) send({ type: "QUERY_NPC", npcId, topic: topic.trim() });
  };

  return (
    <HologramCard title={`Witness // ${npcName}`} accent="violet">
      {npcIds.length > 1 && (
        <div className="mb-2 flex flex-wrap gap-1.5">
          {npcIds.map((id) => (
            <button
              key={id}
              onClick={() => setSelected(id)}
              className={`rounded px-2 py-1 text-[10px] uppercase tracking-wider ${
                id === npcId
                  ? "bg-neon-violet/30 text-neon-violet ring-1 ring-neon-violet/60"
                  : "bg-black/40 text-slate-400 ring-1 ring-white/10"
              }`}
            >
              {npcs[id]}
            </button>
          ))}
        </div>
      )}

      <div className="mb-3 max-h-52 space-y-2 overflow-y-auto pr-1">
        {thread.length === 0 ? (
          <p className="text-xs text-slate-500">// say something to {npcName}…</p>
        ) : (
          thread.map((m) => {
            const mine = m.senderId === playerId;
            return (
              <div key={m.seq} className={`flex ${mine ? "justify-end" : "justify-start"}`}>
                <div
                  className={`max-w-[85%] rounded-lg px-3 py-1.5 text-xs ${
                    mine
                      ? "bg-neon-cyan/15 text-neon-cyan"
                      : "bg-neon-violet/15 text-slate-200"
                  }`}
                >
                  {!mine && (
                    <span className="mb-0.5 block text-[9px] uppercase tracking-widest text-neon-violet/80">
                      {m.senderName}
                    </span>
                  )}
                  {m.text}
                </div>
              </div>
            );
          })
        )}
      </div>

      <div className="flex gap-2">
        <input
          value={topic}
          onChange={(e) => setTopic(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && ask()}
          placeholder={`ask ${npcName}…`}
          className="flex-1 rounded bg-black/40 px-3 py-2 text-xs text-slate-200 outline-none ring-1 ring-white/10 focus:ring-neon-violet/60"
        />
        <button className="btn" onClick={ask}>
          Ask
        </button>
      </div>

      {answers.length > 0 && (
        <details className="mt-3">
          <summary className="cursor-pointer text-[10px] uppercase tracking-widest text-slate-500">
            Witness memory fragments
          </summary>
          <div className="mt-2">
            <MemoryViewer observations={answers} />
          </div>
        </details>
      )}

      <p className="mt-2 text-[10px] text-slate-500">
        NPCs never lie and only recall what they witnessed — try "shadow" to see them refuse.
      </p>
    </HologramCard>
  );
}
