import { motion } from "framer-motion";
import type { ReactNode } from "react";

interface Props {
  title?: string;
  accent?: "cyan" | "pink" | "violet" | "amber";
  className?: string;
  children: ReactNode;
}

const accentText: Record<NonNullable<Props["accent"]>, string> = {
  cyan: "text-neon-cyan neon-text",
  pink: "text-neon-pink neon-text-pink",
  violet: "text-neon-violet",
  amber: "text-neon-amber",
};

/** A glassmorphic holographic panel with a neon header and scanline sheen. */
export function HologramCard({ title, accent = "cyan", className = "", children }: Props) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
      className={`glass-panel scanlines relative overflow-hidden ${className}`}
    >
      {title && (
        <div className="mb-3 flex items-center gap-2">
          <span className={`h-1.5 w-1.5 rounded-full bg-current ${accentText[accent]}`} />
          <h3 className={`text-xs uppercase tracking-[0.25em] ${accentText[accent]}`}>
            {title}
          </h3>
        </div>
      )}
      {children}
    </motion.div>
  );
}
