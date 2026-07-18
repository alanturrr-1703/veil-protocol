import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { useGameState } from "../../hooks/useGameState";
import { CITY_MAP } from "../../types/Location";
import type { Intent } from "../../types/Event";

/**
 * Street-level view of the exact room you're standing in. Unlike the top-down map, this
 * renders the actual occupants who share your room as living characters: players stand
 * alert, NPC witnesses sleep at night (with drifting Zzz). Everything is clickable —
 * tap an NPC to question them, or tap a player to use your night power on them.
 */

type Kind = "self" | "human" | "ai" | "npc";
interface Person {
  id: string;
  name: string;
  kind: Kind;
  alive: boolean;
}

const COLORS: Record<Kind, string> = {
  self: "#22d3ee",
  human: "#34d399",
  ai: "#ff2e97",
  npc: "#f59e0b",
};

const districtName = (id?: string | null) => CITY_MAP.find((l) => l.id === id)?.name ?? id ?? "—";

function Character({
  person,
  index,
  count,
  asleep,
  onClick,
  ping,
}: {
  person: Person;
  index: number;
  count: number;
  asleep: boolean;
  onClick: () => void;
  ping: string | null;
}) {
  const color = person.alive ? COLORS[person.kind] : "#475569";
  // spread occupants across the floor, biased away from the edges
  const left = count === 1 ? 50 : 12 + (index * (76 / Math.max(1, count - 1)));
  const tag =
    person.kind === "self" ? "YOU" : person.kind === "npc" ? "NPC" : person.kind === "ai" ? "AI" : "";

  return (
    <button
      onClick={onClick}
      className="group absolute bottom-6 flex flex-col items-center outline-none"
      style={{ left: `${left}%`, transform: "translateX(-50%)" }}
      title={person.name}
    >
      <AnimatePresence>
        {ping && (
          <motion.span
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: -6 }}
            exit={{ opacity: 0, y: -16 }}
            className="absolute -top-6 whitespace-nowrap rounded bg-black/80 px-2 py-0.5 text-[10px] font-semibold"
            style={{ color }}
          >
            {ping}
          </motion.span>
        )}
      </AnimatePresence>

      {/* sleeping Zzz for NPCs at night */}
      {asleep && (
        <div className="absolute -top-2 left-8">
          {[0, 1, 2].map((i) => (
            <motion.span
              key={i}
              className="absolute text-[10px] font-bold text-slate-300"
              initial={{ opacity: 0, y: 4, x: 0 }}
              animate={{ opacity: [0, 0.9, 0], y: -14, x: 8 }}
              transition={{ duration: 2.2, repeat: Infinity, delay: i * 0.6 }}
              style={{ left: i * 6 }}
            >
              z
            </motion.span>
          ))}
        </div>
      )}

      <motion.div
        className="relative"
        animate={
          !person.alive
            ? { rotate: 90, y: 10, opacity: 0.6 }
            : asleep
              ? { rotate: -72, y: 8 }
              : { y: [0, -3, 0] }
        }
        transition={
          person.alive && !asleep
            ? { duration: 2.4, repeat: Infinity, ease: "easeInOut", delay: index * 0.25 }
            : { duration: 0.6 }
        }
        style={{ transformOrigin: "bottom center", filter: `drop-shadow(0 0 6px ${color})` }}
      >
        {/* head */}
        <div
          className="mx-auto h-3.5 w-3.5 rounded-full border"
          style={{ background: "#0b1120", borderColor: color }}
        />
        {/* body */}
        <div
          className="mx-auto -mt-0.5 h-6 w-4 rounded-t-md rounded-b-sm"
          style={{ background: color, opacity: person.alive ? 0.95 : 0.5 }}
        />
      </motion.div>

      {/* shadow on floor */}
      <div className="h-1.5 w-6 rounded-full bg-black/50 blur-[2px]" />

      <span
        className="mt-1 max-w-[72px] truncate text-[10px] font-medium transition group-hover:text-white"
        style={{ color }}
      >
        {person.name}
        {tag && <span className="ml-1 text-[8px] opacity-70">{tag}</span>}
      </span>
    </button>
  );
}

export function RoomScene({ send }: { send: (intent: Intent) => void }) {
  const { view, playerId, role, phase } = useGameState();
  const [pings, setPings] = useState<Record<string, string>>({});
  const timers = useRef<Record<string, number>>({});

  useEffect(() => () => Object.values(timers.current).forEach((t) => clearTimeout(t)), []);

  if (!view || !view.viewerDistrict) return null;

  const night = phase === "NIGHT";
  const roomLabel = view.rooms?.[view.viewerRoom ?? "commons"] ?? view.viewerRoom ?? "Commons";

  const people: Person[] = [];
  for (const id of Object.keys(view.positions ?? {})) {
    people.push({
      id,
      name: view.names[id] ?? id,
      kind: id === playerId ? "self" : view.humans.includes(id) ? "human" : "ai",
      alive: view.roster[id] ?? true,
    });
  }
  for (const [id, name] of Object.entries(view.npcsHere ?? {})) {
    people.push({ id, name, kind: "npc", alive: true });
  }

  const roleAction: Record<string, { label: string; make: (id: string) => Intent } | undefined> = {
    SHADOW: { label: "Eliminate", make: (id) => ({ type: "ATTACK", targetId: id }) },
    ORACLE: { label: "Investigate", make: (id) => ({ type: "INVESTIGATE", targetId: id }) },
    AEGIS: { label: "Shield", make: (id) => ({ type: "SHIELD", targetId: id }) },
  };
  const action = roleAction[role];

  const ping = (id: string, text: string) => {
    setPings((p) => ({ ...p, [id]: text }));
    clearTimeout(timers.current[id]);
    timers.current[id] = window.setTimeout(
      () => setPings((p) => ({ ...p, [id]: "" })),
      1400,
    );
  };

  const handle = (p: Person) => {
    if (p.kind === "npc") {
      send({ type: "QUERY_NPC", npcId: p.id, topic: "last night" });
      ping(p.id, night ? "shh… asking" : "asked ▸ Witness panel");
      return;
    }
    if (p.kind === "self") {
      ping(p.id, "that's you");
      return;
    }
    if (!p.alive) {
      ping(p.id, "eliminated");
      return;
    }
    if (night && action) {
      send(action.make(p.id));
      ping(p.id, `${action.label}!`);
    } else {
      ping(p.id, "you watch them");
    }
  };

  const hint = action
    ? night
      ? `Tap a person to ${action.label.toLowerCase()} · tap ${"an NPC to question them"}`
      : "Powers arm at night · tap an NPC to question them"
    : "Tap an NPC to question them";

  return (
    <div
      className="relative h-full w-full overflow-hidden rounded-xl border border-white/10"
      style={{
        background: night
          ? "linear-gradient(180deg,#05060f 0%,#0a1024 60%,#0d1530 100%)"
          : "linear-gradient(180deg,#0a1226 0%,#101a38 60%,#14203f 100%)",
      }}
    >
      {/* ambient window / moon */}
      <div
        className="absolute right-6 top-4 h-8 w-8 rounded-full"
        style={{
          background: night ? "#cbd5e1" : "#fde68a",
          boxShadow: night ? "0 0 24px #94a3b8" : "0 0 28px #fbbf24",
          opacity: 0.8,
        }}
      />
      {/* floor grid */}
      <div
        className="absolute inset-x-0 bottom-0 h-1/2"
        style={{
          backgroundImage:
            "repeating-linear-gradient(90deg,transparent 0 38px,rgba(45,212,191,0.08) 38px 39px)," +
            "repeating-linear-gradient(0deg,transparent 0 22px,rgba(45,212,191,0.06) 22px 23px)",
        }}
      />
      <div
        className="absolute left-0 right-0 top-1/2"
        style={{ borderTop: "1px solid rgba(148,163,184,0.25)" }}
      />

      {/* header */}
      <div className="absolute left-3 top-2 z-10">
        <p className="text-[10px] uppercase tracking-widest text-slate-400">You're standing in</p>
        <p className="text-sm font-semibold">
          <span className="text-neon-cyan">{districtName(view.viewerDistrict)}</span>
          <span className="text-slate-500"> · </span>
          <span className="text-neon-violet">{roomLabel}</span>
        </p>
      </div>

      {/* occupants */}
      {people.length <= 1 ? (
        <div className="flex h-full items-center justify-center">
          <p className="text-xs italic text-slate-500">
            The room is empty. Slip through a door to find someone hiding.
          </p>
        </div>
      ) : null}

      {people.map((p, i) => (
        <Character
          key={`${p.kind}:${p.id}`}
          person={p}
          index={i}
          count={people.length}
          asleep={night && (p.kind === "npc" || (!action && p.kind !== "self"))}
          onClick={() => handle(p)}
          ping={pings[p.id] || null}
        />
      ))}

      <p className="absolute bottom-1 left-0 right-0 text-center text-[9px] text-slate-500">{hint}</p>
    </div>
  );
}
