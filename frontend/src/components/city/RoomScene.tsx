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

// Where exits sit on the walls, in normalized arena coords. Walk into one (or click it) to
// travel to that room. The arrow hints the direction of the path.
const DOOR_SLOTS = [
  { nx: 0.02, ny: 0.5, arrow: "◄", edge: "left" },
  { nx: 0.98, ny: 0.5, arrow: "►", edge: "right" },
  { nx: 0.5, ny: 0.97, arrow: "▼", edge: "bottom" },
  { nx: 0.5, ny: 0.06, arrow: "▲", edge: "top" },
];
const DOOR_RANGE = 0.08;

const districtName = (id?: string | null) => CITY_MAP.find((l) => l.id === id)?.name ?? id ?? "—";

interface Person {
  id: string;
  name: string;
  kind: Kind;
  alive: boolean;
  x: number;
  y: number;
}

/** One of three strike animations, played where the victim stands when a Shadow attacks. */
function AttackBurst({ x, y, variant }: { x: number; y: number; variant: number }) {
  const style = { left: `${px(x)}%`, top: `${py(y)}%`, transform: "translate(-50%,-50%)" } as const;
  if (variant === 0) {
    // three neon slashes
    return (
      <div className="pointer-events-none absolute z-30" style={style}>
        {[0, 1, 2].map((i) => (
          <motion.div
            key={i}
            className="absolute h-[3px] w-12 rounded-full bg-neon-pink"
            style={{ boxShadow: "0 0 10px #ff2e97", rotate: -40 + i * 22, x: "-50%", y: "-50%" }}
            initial={{ scaleX: 0, opacity: 0 }}
            animate={{ scaleX: [0, 1, 0.2], opacity: [0, 1, 0] }}
            transition={{ duration: 0.5, delay: i * 0.09 }}
          />
        ))}
      </div>
    );
  }
  if (variant === 1) {
    // expanding shock rings + impact
    return (
      <div className="pointer-events-none absolute z-30" style={style}>
        {[0, 1].map((i) => (
          <motion.div
            key={i}
            className="absolute h-6 w-6 rounded-full border-2 border-neon-pink"
            style={{ x: "-50%", y: "-50%", boxShadow: "0 0 12px #ff2e97" }}
            initial={{ scale: 0, opacity: 0.9 }}
            animate={{ scale: 4.5, opacity: 0 }}
            transition={{ duration: 0.9, delay: i * 0.18 }}
          />
        ))}
        <motion.div
          className="text-xl"
          style={{ x: "-50%", y: "-50%" }}
          initial={{ scale: 0 }}
          animate={{ scale: [0, 1.5, 0] }}
          transition={{ duration: 0.7 }}
        >
          💥
        </motion.div>
      </div>
    );
  }
  // variant 2: glitch shrapnel
  return (
    <div className="pointer-events-none absolute z-30" style={style}>
      {Array.from({ length: 9 }).map((_, i) => (
        <motion.span
          key={i}
          className="absolute h-1.5 w-1.5 rounded-full bg-neon-pink"
          style={{ boxShadow: "0 0 6px #ff2e97" }}
          initial={{ x: 0, y: 0, opacity: 1 }}
          animate={{ x: Math.cos((i / 9) * 6.283) * 34, y: Math.sin((i / 9) * 6.283) * 34, opacity: 0 }}
          transition={{ duration: 0.8, ease: "easeOut" }}
        />
      ))}
    </div>
  );
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
  const doorsRef = useRef<{ rid: string; name: string; nx: number; ny: number; arrow: string; edge: string }[]>([]);
  const lastEnter = useRef(0);
  const iAmAlive = playerId ? (view?.roster?.[playerId] ?? true) : false;

  const currentRoom = view?.viewerRoom ?? "commons";
  const doors = useMemo(() => {
    const rooms = view?.rooms ?? {};
    return Object.entries(rooms)
      .filter(([rid]) => rid !== currentRoom)
      .map(([rid, name], i) => ({ rid, name, ...DOOR_SLOTS[i % DOOR_SLOTS.length] }));
  }, [view?.rooms, currentRoom]);
  doorsRef.current = doors;

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

          // walk into a doorway to travel to that room
          if (now - lastEnter.current > 900) {
            for (const dr of doorsRef.current) {
              if (Math.hypot(me.current.x - dr.nx, me.current.y - dr.ny) < DOOR_RANGE) {
                lastEnter.current = now;
                sendRef.current({ type: "ENTER_ROOM", roomId: dr.rid });
                break;
              }
            }
          }
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

  // --- live attack animations (only strikes in YOUR room reach you) -------------
  const [bursts, setBursts] = useState<{ key: string; x: number; y: number; variant: number }[]>([]);
  const seenFx = useRef<Set<string>>(new Set());
  useEffect(() => {
    for (const fx of view?.roomAttacks ?? []) {
      const key = `${fx.tick}:${fx.victimId}:${fx.variant}`;
      if (seenFx.current.has(key)) continue;
      seenFx.current.add(key);
      const c = view?.coords?.[fx.victimId];
      const b = { key, x: c ? c[0] : 0.5, y: c ? c[1] : 0.5, variant: fx.variant };
      setBursts((prev) => [...prev, b]);
      window.setTimeout(() => setBursts((prev) => prev.filter((z) => z.key !== key)), 1400);
    }
  }, [view?.roomAttacks, view?.coords]);

  // --- dawn reveal: who the night claimed --------------------------------------
  const [dawn, setDawn] = useState<string[] | null>(null);
  const dawnKey = useRef("");
  useEffect(() => {
    const victims = view?.lastNightVictims ?? [];
    if (phase === "DAY" && victims.length) {
      const key = victims.join(",");
      if (dawnKey.current !== key) {
        dawnKey.current = key;
        setDawn(victims);
        window.setTimeout(() => setDawn(null), 6000);
      }
    }
    if (phase === "NIGHT") dawnKey.current = "";
  }, [phase, view?.lastNightVictims]);

  // --- teleport (one jump to any district per night) ---------------------------
  const [teleOpen, setTeleOpen] = useState(false);
  const canTeleport = !!view?.teleportAvailable && iAmAlive;

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
      <div className="absolute left-1/2 top-5 h-8 w-8 -translate-x-1/2 rounded-full"
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

      {/* teleport — one jump to ANY district per night */}
      <div className="absolute left-3 top-14 z-20">
        <button
          disabled={!canTeleport}
          onClick={() => setTeleOpen((o) => !o)}
          className={`rounded-md border px-2 py-1 text-[10px] font-semibold tracking-wide backdrop-blur transition ${
            canTeleport
              ? "border-neon-cyan/60 bg-black/60 text-neon-cyan hover:bg-neon-cyan/10"
              : "border-white/10 bg-black/40 text-slate-500"
          }`}
          title={canTeleport ? "Jump to any district (once per night)" : "Recharges at nightfall"}
        >
          ⚡ Teleport {canTeleport ? "· 1/night" : "· used"}
        </button>
        {teleOpen && canTeleport && (
          <div className="mt-1 w-44 rounded-md border border-neon-cyan/40 bg-black/90 p-1 backdrop-blur">
            {CITY_MAP.filter((l) => l.id !== view.viewerDistrict).map((l) => (
              <button
                key={l.id}
                onClick={() => { send({ type: "TELEPORT", toLocationId: l.id }); setTeleOpen(false); }}
                className="flex w-full items-center justify-between rounded px-2 py-1 text-[10px] hover:bg-white/10"
              >
                <span className="text-slate-200">{l.name}</span>
                <span className="text-slate-500">{view.districtCounts?.[l.id] ?? 0} here</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {people.length <= 1 && (
        <div className="absolute inset-0 flex items-center justify-center">
          <p className="text-xs italic text-slate-500">The room is empty. Roam with WASD or slip through a door.</p>
        </div>
      )}

      {/* live strike animations */}
      {bursts.map((b) => (
        <AttackBurst key={b.key} x={b.x} y={b.y} variant={b.variant} />
      ))}

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

      {/* faint paths from the room centre to each exit */}
      {doors.length > 0 && (
        <svg className="pointer-events-none absolute inset-0 h-full w-full" viewBox="0 0 100 100" preserveAspectRatio="none">
          {doors.map((dr) => (
            <line
              key={dr.rid}
              x1={50}
              y1={60}
              x2={px(dr.nx)}
              y2={py(dr.ny)}
              stroke="#a855f7"
              strokeWidth={0.4}
              strokeDasharray="1.5 1.5"
              opacity={0.35}
            />
          ))}
        </svg>
      )}

      {/* exits / doorways to the other rooms in this district */}
      {doors.map((dr) => {
        const near = Math.hypot(meRender.x - dr.nx, meRender.y - dr.ny) < DOOR_RANGE * 2.2;
        return (
          <button
            key={dr.rid}
            onClick={() => send({ type: "ENTER_ROOM", roomId: dr.rid })}
            className={`group absolute z-10 flex -translate-x-1/2 -translate-y-1/2 flex-col items-center outline-none transition ${near ? "scale-110" : ""}`}
            style={{ left: `${px(dr.nx)}%`, top: `${py(dr.ny)}%` }}
            title={`Enter ${dr.name}`}
          >
            <div
              className="flex items-center gap-1 rounded-md border px-2 py-1 backdrop-blur"
              style={{
                borderColor: near ? "#c4b5fd" : "rgba(168,85,247,0.5)",
                background: "rgba(10,6,24,0.7)",
                boxShadow: near ? "0 0 12px rgba(168,85,247,0.6)" : "none",
              }}
            >
              <span className="text-xs text-neon-violet">{dr.arrow}</span>
              <span className="whitespace-nowrap text-[10px] font-semibold text-neon-violet group-hover:text-white">
                {dr.name}
              </span>
            </div>
            <span className="mt-0.5 text-[8px] uppercase tracking-widest text-slate-500">
              {near ? "walk in / click" : "exit"}
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

      {/* dawn reveal — who fell in the night (everyone sees this) */}
      <AnimatePresence>
        {dawn && (
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="absolute inset-0 z-40 flex flex-col items-center justify-center bg-black/75 backdrop-blur-sm"
          >
            <motion.p initial={{ y: -12, opacity: 0 }} animate={{ y: 0, opacity: 1 }}
              className="text-[10px] uppercase tracking-[0.5em] text-neon-amber">
              Dawn breaks
            </motion.p>
            <p className="mt-1 text-xs text-slate-400">The night claimed…</p>
            <div className="mt-3 flex flex-col items-center gap-2">
              {dawn.map((id, i) => (
                <motion.div key={id} initial={{ scale: 0.5, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}
                  transition={{ delay: 0.2 + i * 0.25 }} className="flex items-center gap-2">
                  <span className="text-lg">🩸</span>
                  <span className="neon-text text-xl font-bold text-neon-pink line-through decoration-neon-pink/70">
                    {view.names[id] ?? id}
                  </span>
                </motion.div>
              ))}
            </div>
            <button onClick={() => setDawn(null)}
              className="mt-5 text-[10px] uppercase tracking-widest text-slate-400 hover:text-white">
              dismiss
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      <p className="absolute bottom-1 right-2 text-[9px] text-slate-500">
        {iAmAlive ? "WASD move · E whisper · ⚡ teleport · click to act" : "you are eliminated"}
      </p>
    </div>
  );
}
