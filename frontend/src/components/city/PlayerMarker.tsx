interface Props {
  name: string;
  alive: boolean;
  isSelf?: boolean;
}

/** SVG/DOM presentational fallback for a player marker (live map uses PixiJS). */
export function PlayerMarker({ name, alive, isSelf }: Props) {
  const color = !alive ? "text-slate-500" : isSelf ? "text-neon-cyan" : "text-neon-pink";
  return (
    <div className={`flex flex-col items-center ${color}`}>
      <span className={`h-3 w-3 rounded-full bg-current ${alive ? "" : "opacity-40"}`} />
      <span className="mt-1 text-[10px]">{name}{alive ? "" : " ✝"}</span>
    </div>
  );
}
