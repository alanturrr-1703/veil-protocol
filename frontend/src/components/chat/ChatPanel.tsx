import { useEffect, useMemo, useRef, useState } from "react";
import { HologramCard } from "../ui/HologramCard";
import { useGameStore } from "../../stores/gameStore";
import type { ChatChannel } from "../../types/Player";
import type { Intent } from "../../types/Event";

/**
 * Encrypted comms. Every line here came pre-redacted from the backend
 * (`PlayerView.readableChat`) — the client renders what it is given and never sees a
 * channel it isn't authorized for. Which channels the input can target is likewise
 * dictated by the server (`PlayerView.postableChannels`), so send-rules and see-rules
 * always match the authoritative engine.
 */
const CHANNEL_STYLE: Record<ChatChannel, string> = {
  DAY: "text-neon-cyan",
  SHADOW: "text-neon-pink",
  DEAD: "text-neon-violet",
  SYSTEM: "text-neon-amber",
};

const CHANNEL_LABEL: Record<ChatChannel, string> = {
  DAY: "City",
  SHADOW: "Shadows",
  DEAD: "The Fallen",
  SYSTEM: "Narrator",
};

export function ChatPanel({ send }: { send: (intent: Intent) => void }) {
  const view = useGameStore((s) => s.view);
  const [draft, setDraft] = useState("");
  const [channel, setChannel] = useState<ChatChannel | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const postable = useMemo(() => view?.postableChannels ?? [], [view]);
  const messages = view?.readableChat ?? [];

  // Keep a valid selected channel as phase/role changes flip what's postable.
  useEffect(() => {
    if (postable.length === 0) {
      setChannel(null);
    } else if (!channel || !postable.includes(channel)) {
      setChannel(postable[0]);
    }
  }, [postable, channel]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages.length]);

  const submit = () => {
    const text = draft.trim();
    if (!text || !channel) return;
    send({ type: "CHAT", channel, text });
    setDraft("");
  };

  return (
    <HologramCard title="Encrypted Comms" accent="violet" className="flex h-full flex-col">
      <div ref={scrollRef} className="mb-3 flex-1 space-y-1.5 overflow-y-auto pr-1" style={{ maxHeight: 260 }}>
        {messages.length === 0 && (
          <p className="text-xs italic text-slate-500">No transmissions on your channels yet.</p>
        )}
        {messages.map((m) => (
          <div key={`${m.tick}-${m.seq}`} className="text-xs leading-snug">
            <span className={`font-semibold ${CHANNEL_STYLE[m.channel]}`}>
              [{CHANNEL_LABEL[m.channel]}] {m.senderName}
            </span>
            <span className="text-slate-300">: {m.text}</span>
          </div>
        ))}
      </div>

      {postable.length === 0 ? (
        <p className="text-[11px] italic text-slate-500">
          You can't transmit right now — the channel is closed for your role this phase.
        </p>
      ) : (
        <div className="space-y-2">
          <div className="flex flex-wrap gap-1">
            {postable.map((c) => (
              <button
                key={c}
                onClick={() => setChannel(c)}
                className={`rounded-md px-2 py-0.5 text-[10px] uppercase tracking-widest transition ${
                  channel === c
                    ? `neon-border ${CHANNEL_STYLE[c]}`
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                {CHANNEL_LABEL[c]}
              </button>
            ))}
          </div>
          <div className="flex gap-2">
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && submit()}
              placeholder={channel ? `Transmit to ${CHANNEL_LABEL[channel]}…` : "…"}
              className="min-w-0 flex-1 rounded-lg bg-black/40 px-3 py-1.5 text-xs text-white outline-none ring-1 ring-white/10 focus:ring-neon-violet/60"
            />
            <button className="btn text-neon-violet" onClick={submit}>
              Send
            </button>
          </div>
        </div>
      )}
    </HologramCard>
  );
}
