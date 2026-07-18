import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useGameState } from "../../hooks/useGameState";
import { CITY_MAP } from "../../types/Location";
import type { Intent } from "../../types/Event";

/**
 * Street-level, real-time view of the room you're standing in. Roam with WASD / arrow keys;
 * everyone sharing your room sees you move (positions relayed through the backend). Walk up
 * to someone and a private whisper opens (press E) — proximity chat that only the two of you
 * can read. NPC witnesses sleep at night; tap anyone to act on them.
 */

type Kind = "self" | "human" | "ai" | "npc";
const COLORS: Record<Kind, string> = {
  self: "#22d3ee",
  human: "#34d399",
  ai: "#ff2e97",
  npc: "#f59e0b",
};

// Arena bounds inside the panel, in % (leaves room for the header/footer).
const AX0 = 7, AX1 = 93, AY0 = 34, AY1 = 86;
const px = (x: number) => AX0 + (AX1 - AX0) * x;
const py = (y: number) => AY0 + (AY1 - AY0) * y;
const WHISPER_RANGE = 0.18;
const SPEED = 0.55; // arena-widths per second

const districtName = (id?: string | null) => CITY_MAP.find((l) => l.id === id)?.name ?? id ?? "—";

interface Person {
  id: string;
  name: string;
  kind: Kind;
  alive: boolean;
  x: number;
  y: number;
}

function Figure({ color, alive, asleep, index }: { color: string; alive: boolean; asleep: boolean; index: number }) {
  return (
    <motion.div
      className="relative"
      animate={!alive ? { rotate: 90, y: 8, opacity: 0.55 } : asleep ? { rotate: -70, y: 6 } : { y: [0, -3, 0] }}
      transition={alive && !asleep ? { duration: 2.2, repeat: Infinity, ease: "easeInOut", delay: index * 0.2 } : { duration: 0.4 }}
      style={{ transformOrigin: "bottom center", filter: `drop-shadow(0 0 6px ${color})` }}
    >
      <div className="mx-auto h-3.5 w-3.5 rounded-full border" style={{ background: "#0b1120", borderColor: color }} />
      <div className="mx-auto -mt-0.5 h-6 w-4 rounded-t-md rounded-b-sm" style={{ background: color, opacity: alive ? 0.95 : 0.5 }} />
    </motion.div>
  );
}

export function RoomScene({ send }: { send: (intent: Intent) => void }) {
  const { view, playerId, role, phase } = useGameState();

  const night = phase === "NIGHT";
  const roomKey = `${view?.viewerDistrict}/${view?.viewerRoom}`;

  // --- my local position (authoritative for smooth movement) --------------------
  const me = useRef({ x: 0.5, y: 0.6 });
  const [meRender, setMeRender] = useState({ x: 0.5, y: 0.6 });
  const keys = useRef<Set<string>>(new Set());
  const lastSent = useRef({ x: -1, y: -1, t: 0 });
  const sendRef = useRef(send);
  sendRef.current = send;
  const iAmAlive = playerId ? (view?.roster?.[playerId] ?? true) : false;

  // Teleport my marker to the server position whenever I change room/district.
  useEffect(() => {
    const c = playerId ? view?.coords?.[playerId] : undefined;
    const start = c ? { x: c[0], y: c[1] } : { x: 0.5, y: 0.6 };
    me.current = start;
    setMeRender(start);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomKey]);

  // WASD / arrow key capture (ignored while typing in an input).
  useEffect(() => {
    const typing = () => {
      const el = document.activeElement;
      return el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA");
    };
    const down = (e: KeyboardEvent) => {
      const k = e.key.toLowerCase();
      if (["w", "a", "s", "d", "arrowup", "arrowdown", "arrowleft", "arrowright"].includes(k)) {
        if (typing()) return;
        keys.current.add(k);
        e.preventDefault();
      }
    };
    const up = (e: KeyboardEvent) => keys.current.delete(e.key.toLowerCase());
    window.addEventListener("keydown", down);
    window.addEventListener("keyup", up);
    return () => {
      window.removeEventListener("keydown", down);
      window.removeEventListener("keyup", up);
    };
  }, []);

  // Movement + throttled position broadcast loop.
  useEffect(() => {
    let raf = 0;
    let prev = performance.now();
    const loop = (now: number) => {
      const dt = Math.min(0.05, (now - prev) / 1000);
      prev = now;
      const k = keys.current;
      if (iAmAlive && k.size) {
        let dx = 0, dy = 0;
        if (k.has("a") || k.has("arrowleft")) dx -= 1;
        if (k.has("d") || k.has("arrowright")) dx += 1;
        if (k.has("w") || k.has("arrowup")) dy -= 1;
        if (k.has("s") || k.has("arrowdown")) dy += 1;
        if (dx || dy) {
          const len = Math.hypot(dx, dy) || 1;
          me.current.x = Math.max(0, Math.min(1, me.current.x + (dx / len) * SPEED * dt));
          me.current.y = Math.max(0, Math.min(1, me.current.y + (dy / len) * SPEED * dt));
          setMeRender({ x: me.current.x, y: me.current.y });
        }
      }
      // send at ~12Hz when moved
      const moved = Math.abs(me.current.x - lastSent.current.x) + Math.abs(me.current.y - lastSent.current.y) > 0.004;
      if (moved && now - lastSent.current.t > 80) {
        lastSent.current = { x: me.current.x, y: me.current.y, t: now };
        sendRef.current({ type: "POS", x: me.current.x, y: me.current.y });
      }
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, [iAmAlive]);

  // --- assemble everyone in the room -------------------------------------------
  const people: Person[] = useMemo(() => {
    if (!view) return [];
    const out: Person[] = [];
    for (const [id, c] of Object.entries(view.coords ?? {})) {
      const isMe = id === playerId;
      out.push({
        id,
        name: view.names[id] ?? id,
        kind: isMe ? "self" : view.humans.includes(id) ? "human" : "ai",
        alive: view.roster[id] ?? true,
        x: isMe ? meRender.x : c[0],
        y: isMe ? meRender.y : c[1],
      });
    }
    const npcIds = Object.entries(view.npcsHere ?? {});
    npcIds.forEach(([id, name], i) => {
      out.push({
        id,
        name,
        kind: "npc",
        alive: true,
        x: 0.2 + (i * 0.6) / Math.max(1, npcIds.length - 1 || 1),
        y: 0.22,
      });
    });
    return out;
  }, [view, playerId, meRender]);

  // nearest other PLAYER within whisper range
  const nearest = useMemo(() => {
    let best: Person | null = null;
    let bestD = WHISPER_RANGE;
    for (const p of people) {
      if (p.kind === "npc" || p.id === playerId || !p.alive) continue;
      const d = Math.hypot(p.x - meRender.x, p.y - meRender.y);
      if (d < bestD) { bestD = d; best = p; }
    }
    return best;
  }, [people, meRender, playerId]);

  // --- whisper panel ------------------------------------------------------------
  const [whisperTo, setWhisperTo] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const thread = useMemo(
    () =>
      (view?.readableChat ?? []).filter(
        (m) =>
          m.channel === "DIRECT" &&
          whisperTo &&
          ((m.senderId === playerId && m.toId === whisperTo) ||
            (m.senderId === whisperTo && m.toId === playerId)),
      ),
    [view, whisperTo, playerId],
  );
  const whisperTargetNear = whisperTo && nearest?.id === whisperTo;

  const openWhisper = useCallback((id: string) => {
    setWhisperTo(id);
    setDraft("");
  }, []);

  // E to open whisper with nearest
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const el = document.activeElement;
      if (el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA")) return;
      if (e.key.toLowerCase() === "e" && nearest) openWhisper(nearest.id);
      if (e.key === "Escape") setWhisperTo(null);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [nearest, openWhisper]);

  const sendWhisper = () => {
    const text = draft.trim();
    if (!text || !whisperTo) return;
    send({ type: "WHISPER", targetId: whisperTo, text });
    setDraft("");
  };

  // click actions (attack / investigate / shield / ask NPC)
  const roleAction: Record<string, { label: string; make: (id: string) => Intent } | undefined> = {
    SHADOW: { label: "Eliminate", make: (id) => ({ type: "ATTACK", targetId: id }) },
    ORACLE: { label: "Investigate", make: (id) => ({ type: "INVESTIGATE", targetId: id }) },
    AEGIS: { label: "Shield", make: (id) => ({ type: "SHIELD", targetId: id }) },
  };
  const action = roleAction[role];
  const [pings, setPings] = useState<Record<string, string>>({});
  const ping = (id: string, text: string) => {
    setPings((p) => ({ ...p, [id]: text }));
    window.setTimeout(() => setPings((p) => ({ ...p, [id]: "" })), 1300);
  };
  const clickPerson = (p: Person) => {
    if (p.kind === "npc") {
      send({ type: "QUERY_NPC", npcId: p.id, topic: "last night" });
      ping(p.id, "asked ▸ Witness");
    } else if (p.kind === "self") {
      ping(p.id, "that's you");
    } else if (night && action && p.alive) {
      send(action.make(p.id));
      ping(p.id, `${action.label}!`);
    } else if (p.alive) {
      openWhisper(p.id);
    }
  };

  if (!view || !view.viewerDistrict) return null;

  return (
    <div
      className="relative h-full w-full select-none overflow-hidden rounded-xl border border-white/10"
      style={{
        background: night
          ? "linear-gradient(180deg,#05060f 0%,#0a1024 60%,#0d1530 100%)"
          : "linear-gradient(180deg,#0a1226 0%,#101a38 60%,#14203f 100%)",
      }}
    >
      <div className="absolute right-6 top-4 h-8 w-8 rounded-full"
        style={{ background: night ? "#cbd5e1" : "#fde68a", boxShadow: night ? "0 0 24px #94a3b8" : "0 0 28px #fbbf24", opacity: 0.8 }} />
      <div className="absolute inset-x-0 bottom-0 h-2/3"
        style={{
          backgroundImage:
            "repeating-linear-gradient(90deg,transparent 0 40px,rgba(45,212,191,0.08) 40px 41px)," +
            "repeating-linear-gradient(0deg,transparent 0 24px,rgba(45,212,191,0.06) 24px 25px)",
        }} />

      <div className="absolute left-3 top-2 z-10">
        <p className="text-[10px] uppercase tracking-widest text-slate-400">You're standing in</p>
        <p className="text-sm font-semibold">
          <span className="text-neon-cyan">{districtName(view.viewerDistrict)}</span>
          <span className="text-slate-500"> · </span>
          <span className="text-neon-violet">{view.rooms?.[view.viewerRoom ?? "commons"] ?? "Commons"}</span>
        </p>
      </div>

      {people.length <= 1 && (
        <div className="absolute inset-0 flex items-center justify-center">
          <p className="text-xs italic text-slate-500">The room is empty. Roam with WASD or slip through a door.</p>
        </div>
      )}

      {/* characters */}
      {people.map((p, i) => {
        const color = p.alive ? COLORS[p.kind] : "#475569";
        const asleep = night && (p.kind === "npc" || (!action && p.kind !== "self"));
        const isNear = nearest?.id === p.id;
        return (
          <button
            key={`${p.kind}:${p.id}`}
            onClick={() => clickPerson(p)}
            className="group absolute flex -translate-x-1/2 -translate-y-full flex-col items-center outline-none"
            style={{ left: `${px(p.x)}%`, top: `${py(p.y)}%`, transition: p.id === playerId ? "none" : "left 0.12s linear, top 0.12s linear" }}
            title={p.name}
          >
            <AnimatePresence>
              {pings[p.id] && (
                <motion.span initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: -4 }} exit={{ opacity: 0, y: -14 }}
                  className="absolute -top-6 whitespace-nowrap rounded bg-black/80 px-2 py-0.5 text-[10px] font-semibold" style={{ color }}>
                  {pings[p.id]}
                </motion.span>
              )}
            </AnimatePresence>

            {asleep && (
              <div className="absolute -top-1 left-7">
                {[0, 1, 2].map((z) => (
                  <motion.span key={z} className="absolute text-[10px] font-bold text-slate-300"
                    initial={{ opacity: 0, y: 4 }} animate={{ opacity: [0, 0.9, 0], y: -14, x: 8 }}
                    transition={{ duration: 2.2, repeat: Infinity, delay: z * 0.6 }} style={{ left: z * 6 }}>
                    z
                  </motion.span>
                ))}
              </div>
            )}

            {isNear && (
              <span className="absolute -top-9 whitespace-nowrap rounded-full border border-neon-lime/60 bg-black/70 px-2 py-0.5 text-[9px] font-semibold text-neon-lime">
                press E to whisper
              </span>
            )}

            <div className="relative" style={isNear ? { filter: "drop-shadow(0 0 8px #a3e635)" } : undefined}>
              <Figure color={color} alive={p.alive} asleep={asleep} index={i} />
            </div>
            <div className="h-1.5 w-6 rounded-full bg-black/50 blur-[2px]" />
            <span className="mt-1 max-w-[80px] truncate text-[10px] font-medium group-hover:text-white" style={{ color }}>
              {p.name}
              {p.kind === "self" && <span className="ml-1 text-[8px] opacity-70">YOU</span>}
              {p.kind === "npc" && <span className="ml-1 text-[8px] opacity-70">NPC</span>}
              {p.kind === "ai" && <span className="ml-1 text-[8px] opacity-70">AI</span>}
            </span>
          </button>
        );
      })}

      {/* whisper panel */}
      <AnimatePresence>
        {whisperTo && (
          <motion.div
            initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 12 }}
            className="absolute bottom-2 left-2 z-20 w-64 rounded-lg border border-neon-lime/40 bg-black/85 p-2 backdrop-blur"
          >
            <div className="mb-1 flex items-center justify-between">
              <span className="text-[11px] font-semibold text-neon-lime">
                ⟿ Whisper · {view.names[whisperTo] ?? whisperTo}
              </span>
              <button className="text-[11px] text-slate-400 hover:text-white" onClick={() => setWhisperTo(null)}>✕</button>
            </div>
            {!whisperTargetNear && (
              <p className="mb-1 text-[9px] text-neon-pink">They've moved out of earshot — step closer to whisper.</p>
            )}
            <div className="mb-1 max-h-20 space-y-0.5 overflow-y-auto pr-1">
              {thread.length === 0 ? (
                <p className="text-[10px] italic text-slate-500">No whispers yet.</p>
              ) : (
                thread.map((m) => (
                  <p key={`${m.tick}-${m.seq}`} className="text-[10px] leading-snug">
                    <span className="font-semibold" style={{ color: m.senderId === playerId ? "#22d3ee" : "#a3e635" }}>
                      {m.senderId === playerId ? "you" : m.senderName}
                    </span>
                    <span className="text-slate-300">: {m.text}</span>
                  </p>
                ))
              )}
            </div>
            <div className="flex gap-1">
              <input
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") sendWhisper(); if (e.key === "Escape") setWhisperTo(null); }}
                placeholder={whisperTargetNear ? "whisper…" : "too far…"}
                disabled={!whisperTargetNear}
                className="min-w-0 flex-1 rounded bg-black/50 px-2 py-1 text-[11px] text-white outline-none ring-1 ring-white/10 focus:ring-neon-lime/60 disabled:opacity-50"
              />
              <button className="btn text-[11px] text-neon-lime" disabled={!whisperTargetNear} onClick={sendWhisper}>Send</button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <p className="absolute bottom-1 right-2 text-[9px] text-slate-500">
        {iAmAlive ? "WASD move · E whisper · click to act" : "you are eliminated"}
      </p>
    </div>
  );
}
