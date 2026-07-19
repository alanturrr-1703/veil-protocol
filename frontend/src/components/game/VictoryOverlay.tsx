import { AnimatePresence, motion } from "framer-motion";
import { useGameState } from "../../hooks/useGameState";

/**
 * End-of-match cinematic. When the confidential referee resolves a winner the backend freezes
 * the match on GAME_OVER and announces "The Shadows/City have won…"; we read that announcement
 * to learn the winning faction, then personalise it against the viewer's now-revealed role.
 */

const SHADOW = "#ff2e97";
const CITY = "#22d3ee";

function winningFaction(announcements: string[]): "SHADOW" | "CITY" | null {
  // Scan newest-first for the terminal announcement.
  for (let i = announcements.length - 1; i >= 0; i--) {
    const a = announcements[i];
    if (!/have won/i.test(a)) continue;
    return /shadow/i.test(a) ? "SHADOW" : "CITY";
  }
  return null;
}

export function VictoryOverlay() {
  const { phase, role, announcements } = useGameState();
  const winner = phase === "GAME_OVER" ? winningFaction(announcements) : null;
  const show = winner !== null;

  const viewerFaction = role === "SHADOW" ? "SHADOW" : "CITY";
  const viewerWon = winner !== null && winner === viewerFaction;
  const knowRole = role !== "UNKNOWN";

  const color = winner === "SHADOW" ? SHADOW : CITY;
  const factionLabel = winner === "SHADOW" ? "THE SHADOWS PREVAIL" : "THE CITY PREVAILS";
  const verdict = !knowRole ? "OPERATION COMPLETE" : viewerWon ? "VICTORY" : "DEFEAT";

  return (
    <AnimatePresence>
      {show && (
        <motion.div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-xl"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.8 }}
        >
          {/* radial faction wash */}
          <motion.div
            className="pointer-events-none absolute inset-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 0.4 }}
            transition={{ duration: 1.4 }}
            style={{ background: `radial-gradient(circle at 50% 45%, ${color}44 0%, transparent 60%)` }}
          />

          <motion.div
            className="relative text-center"
            initial={{ scale: 0.7, opacity: 0, filter: "blur(14px)" }}
            animate={{ scale: 1, opacity: 1, filter: "blur(0px)" }}
            transition={{ duration: 0.7, ease: "easeOut" }}
          >
            <motion.p
              className="mb-3 text-xs uppercase tracking-[0.6em] text-slate-400"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3 }}
            >
              {verdict}
            </motion.p>

            <h1
              className="text-5xl font-bold tracking-[0.2em] md:text-7xl"
              style={{ color, textShadow: `0 0 32px ${color}` }}
            >
              {factionLabel}
            </h1>

            <motion.p
              className="mt-6 text-sm text-slate-300"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.6 }}
            >
              {winner === "SHADOW"
                ? "The city sleeps, unaware of who walked among them."
                : "The last Shadow has been dragged into the light."}
            </motion.p>

            <motion.p
              className="mt-10 animate-flicker text-xs uppercase tracking-[0.4em] text-neon-cyan"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 1 }}
            >
              standings updated · check the leaderboard
            </motion.p>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
