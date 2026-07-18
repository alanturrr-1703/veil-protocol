import { AnimatePresence, motion } from "framer-motion";
import { useGameStore } from "../../stores/gameStore";

const ROLE_COPY: Record<string, { tag: string; color: string; blurb: string }> = {
  SHADOW: { tag: "THE SHADOW", color: "#ff2e97", blurb: "Eliminate the city. Silence the witnesses." },
  ORACLE: { tag: "THE ORACLE", color: "#22d3ee", blurb: "Read the truth hidden in the neon." },
  AEGIS: { tag: "THE AEGIS", color: "#a3e635", blurb: "Shield the innocent through the night." },
  CITIZEN: { tag: "CITIZEN", color: "#e2e8f0", blurb: "Gather evidence. Trust no one." },
};

/** Full-screen dramatic role reveal, shown once when the player's role first arrives. */
export function RoleReveal() {
  const view = useGameStore((s) => s.view);
  const roleSeen = useGameStore((s) => s.roleSeen);
  const markRoleSeen = useGameStore((s) => s.markRoleSeen);

  const role = view?.ownRole;
  const show = !!role && role !== "UNKNOWN" && !roleSeen;
  const copy = role ? ROLE_COPY[role] : undefined;

  return (
    <AnimatePresence>
      {show && copy && (
        <motion.div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-xl"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={markRoleSeen}
        >
          <motion.div
            className="text-center"
            initial={{ scale: 0.7, opacity: 0, filter: "blur(12px)" }}
            animate={{ scale: 1, opacity: 1, filter: "blur(0px)" }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          >
            <p className="mb-2 text-xs uppercase tracking-[0.5em] text-slate-400">
              Identity assigned
            </p>
            <h1
              className="text-6xl font-bold tracking-widest"
              style={{ color: copy.color, textShadow: `0 0 24px ${copy.color}` }}
            >
              {copy.tag}
            </h1>
            <p className="mt-4 text-slate-300">{copy.blurb}</p>
            <p className="mt-10 animate-flicker text-xs uppercase tracking-[0.4em] text-neon-cyan">
              click to enter neon city
            </p>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
