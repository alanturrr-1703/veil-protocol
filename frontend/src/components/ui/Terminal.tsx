interface Props {
  lines: string[];
  className?: string;
}

/** A retro terminal readout for public announcements / narration. */
export function Terminal({ lines, className = "" }: Props) {
  return (
    <div className={`rounded-lg border border-neon-cyan/20 bg-black/50 p-3 ${className}`}>
      <div className="space-y-1 font-mono text-xs leading-relaxed">
        {lines.length === 0 && <p className="text-slate-500">// awaiting transmission…</p>}
        {lines.map((line, i) => (
          <p key={i} className="text-neon-lime/90">
            <span className="text-slate-500">{String(i + 1).padStart(2, "0")} </span>
            {line}
          </p>
        ))}
        <p className="text-neon-cyan">
          <span className="animate-flicker">▍</span>
        </p>
      </div>
    </div>
  );
}
