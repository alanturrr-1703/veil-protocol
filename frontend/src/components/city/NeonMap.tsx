import { useEffect, useRef } from "react";
import { Application, Container, Graphics, Text } from "pixi.js";
import { CITY_MAP, CITY_EDGES } from "../../types/Location";
import { DEMO_PLAYERS } from "../../config";
import { useGameStore } from "../../stores/gameStore";

/**
 * The futuristic city map rendered with PixiJS (WebGL). Draws glowing location nodes, the
 * links between them, and a marker per player (own player highlighted, eliminated players
 * dimmed). Redraws whenever the roster in the redacted view changes.
 */
export function NeonMap() {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const appRef = useRef<Application | null>(null);
  const drawRef = useRef<(() => void) | null>(null);
  const dataRef = useRef({
    roster: {} as Record<string, boolean>,
    playerId: null as string | null,
  });

  const view = useGameStore((s) => s.view);
  const playerId = useGameStore((s) => s.playerId);
  dataRef.current = { roster: view?.roster ?? {}, playerId };

  useEffect(() => {
    let disposed = false;
    const app = new Application();

    const draw = () => {
      if (!app.stage) return;
      app.stage.removeChildren();
      const W = app.screen.width;
      const H = app.screen.height;
      const px = (n: number) => n * W;
      const py = (n: number) => n * H;

      // Links between locations.
      const links = new Graphics();
      for (const [a, b] of CITY_EDGES) {
        const la = CITY_MAP.find((l) => l.id === a)!;
        const lb = CITY_MAP.find((l) => l.id === b)!;
        links.moveTo(px(la.x), py(la.y)).lineTo(px(lb.x), py(lb.y));
      }
      links.stroke({ width: 2, color: 0x22d3ee, alpha: 0.35 });
      app.stage.addChild(links);

      // Location nodes.
      for (const loc of CITY_MAP) {
        const node = new Container();
        node.x = px(loc.x);
        node.y = py(loc.y);
        const glow = new Graphics().circle(0, 0, 46).fill({ color: 0x22d3ee, alpha: 0.08 });
        const ring = new Graphics().circle(0, 0, 30).stroke({ width: 2, color: 0x22d3ee, alpha: 0.7 });
        const core = new Graphics().circle(0, 0, 6).fill({ color: 0x22d3ee });
        const label = new Text({
          text: loc.name.toUpperCase(),
          style: { fill: 0x99f6ff, fontSize: 12, fontFamily: "monospace", letterSpacing: 2 },
        });
        label.anchor.set(0.5, 0);
        label.y = 42;
        node.addChild(glow, ring, core, label);
        app.stage.addChild(node);
      }

      // Player markers clustered on the plaza (view carries alive/dead, not positions yet).
      const hub = CITY_MAP[0];
      const { roster, playerId: me } = dataRef.current;
      const ids = DEMO_PLAYERS.map((p) => p.id);
      ids.forEach((id, i) => {
        const alive = roster[id] ?? true;
        const angle = (i / ids.length) * Math.PI * 2;
        const mx = px(hub.x) + Math.cos(angle) * 74;
        const my = py(hub.y) + Math.sin(angle) * 74;
        const isMe = id === me;
        const color = !alive ? 0x64748b : isMe ? 0x22d3ee : 0xff2e97;

        const marker = new Container();
        marker.x = mx;
        marker.y = my;
        const dot = new Graphics().circle(0, 0, isMe ? 10 : 8).fill({ color, alpha: alive ? 1 : 0.4 });
        if (isMe) dot.circle(0, 0, 16).stroke({ width: 2, color, alpha: 0.6 });
        const name = new Text({
          text: (DEMO_PLAYERS.find((p) => p.id === id)?.name ?? id) + (alive ? "" : " ✝"),
          style: { fill: color, fontSize: 11, fontFamily: "monospace" },
        });
        name.anchor.set(0.5, 0);
        name.y = 14;
        marker.addChild(dot, name);
        app.stage.addChild(marker);
      });
    };

    app
      .init({ resizeTo: hostRef.current!, backgroundAlpha: 0, antialias: true })
      .then(() => {
        if (disposed) {
          app.destroy(true);
          return;
        }
        appRef.current = app;
        drawRef.current = draw;
        hostRef.current!.appendChild(app.canvas);
        app.renderer.on("resize", draw);
        draw();
      });

    return () => {
      disposed = true;
      appRef.current = null;
      drawRef.current = null;
      try {
        app.destroy(true);
      } catch {
        /* already gone */
      }
    };
  }, []);

  // Redraw markers when the roster / identity changes.
  useEffect(() => {
    drawRef.current?.();
  }, [view?.roster, playerId]);

  return <div ref={hostRef} className="h-full w-full" />;
}
