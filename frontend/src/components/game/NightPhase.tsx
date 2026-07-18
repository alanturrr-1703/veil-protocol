import { AnimatePresence, motion } from "framer-motion";
import { useGameState } from "../../hooks/useGameState";

/**
 * Night overlay — a cinematic vignette + haze that washes over the city while the phase is
 * NIGHT, reinforcing that actions are happening in secret.
 */
export function NightOverlay() {
  const { phase } = useGameState();
  const isNight = phase === "NIGHT";

  return (
    <AnimatePresence>
      {isNight && (
        <motion.div
          className="pointer-events-none fixed inset-0 z-30"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 1.2 }}
          style={{
            background:
              "radial-gradient(circle at 50% 40%, transparent 30%, rgba(2,6,23,0.75) 100%)",
          }}
        >
          <motion.p
            className="absolute left-1/2 top-8 -translate-x-1/2 text-xs uppercase tracking-[0.6em] text-neon-violet"
            animate={{ opacity: [0.3, 1, 0.3] }}
            transition={{ duration: 3, repeat: Infinity }}
          >
            night falls // the city sleeps
          </motion.p>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
