import type { MapLocation } from "../../types/Location";

/**
 * SVG presentational fallback for a location node (the live map uses PixiJS in NeonMap).
 * Kept for potential lightweight/no-WebGL rendering paths.
 */
export function LocationNode({ loc }: { loc: MapLocation }) {
  return (
    <g transform={`translate(${loc.x * 100}%, ${loc.y * 100}%)`}>
      <circle r={24} className="fill-neon-cyan/10 stroke-neon-cyan/60" />
      <text className="fill-neon-cyan text-[10px] uppercase" textAnchor="middle" y={40}>
        {loc.name}
      </text>
    </g>
  );
}
