import { useEffect, useState } from "react";
import { useGameState } from "../../hooks/useGameState";

/**
 * Live phase countdown. The backend broadcasts `phaseEndsAt` (epoch millis) and
 * auto-advances when it hits zero — this is the Shadows' 45-second kill window made
 * visible, and the clock for Day and the Vote.
 */
const PHASE_LABEL: Record<string, string> = {
  NIGHT: "SHADOWS STRIKE",
  DAY: "THE CITY TALKS",
  VOTING: "TRIBUNAL",
};

export function Countdown() {
  const { view, phase } = useGameState();
  const endsAt = view?.phaseEndsAt ?? 0;
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(t);
  }, []);

  if (!endsAt) return null;
  const remaining = Math.max(0, Math.ceil((endsAt - now) / 1000));
  const mm = Math.floor(remaining / 60);
  const ss = String(remaining % 60).padStart(2, "0");
  const urgent = remaining <= 10;
  const accent = phase === "NIGHT" ? "text-neon-pink" : phase === "VOTING" ? "text-neon-amber" : "text-neon-cyan";

  return (
    <div className="glass flex items-center gap-3 rounded-xl px-3 py-1.5">
      <span className={`text-[9px] uppercase tracking-[0.3em] ${accent}`}>
        {PHASE_LABEL[phase] ?? phase}
      </span>
      <span className={`font-mono text-lg font-bold ${urgent ? "animate-flicker text-neon-pink" : accent}`}>
        {mm}:{ss}
      </span>
    </div>
  );
}
