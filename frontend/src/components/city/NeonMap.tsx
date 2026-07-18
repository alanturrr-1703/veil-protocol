import { useEffect, useRef } from "react";
import { Application, Container, Graphics, Text } from "pixi.js";
import { CITY_MAP, CITY_EDGES } from "../../types/Location";
import { useGameStore } from "../../stores/gameStore";

/**
 * A top-down 2D neon city rendered with PixiJS — Stardew Valley by way of Blade Runner.
 * Tiled asphalt, glowing districts with lit windows, rain, and phase-driven ambient light.
 *
 * Crucially it respects location privacy: each district shows only an ANONYMOUS living
 * head-count (public), while actual operative/NPC tokens are drawn ONLY for those who share
 * your exact room (from the redacted `positions` / `npcsHere`). Slip into a side room and
 * you disappear from everyone in the commons.
 */

const DISTRICT_COLOR: Record<string, number> = {
  plaza: 0x22d3ee,
  market: 0xffb020,
  docks: 0x38bdf8,
  tower: 0xa855f7,
  alley: 0xff2e97,
  garden: 0x34d399,
};

interface Token {
  container: Container;
  gfx: Graphics;
  label: Text;
  x: number;
  y: number;
  tx: number;
  ty: number;
  seed: number;
}

export function NeonMap() {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const view = useGameStore((s) => s.view);
  const playerId = useGameStore((s) => s.playerId);

  const dataRef = useRef({
    counts: {} as Record<string, number>,
    positions: {} as Record<string, string>,
    npcsHere: {} as Record<string, string>,
    names: {} as Record<string, string>,
    humans: [] as string[],
    roster: {} as Record<string, boolean>,
    viewerDistrict: null as string | null,
    viewerRoom: null as string | null,
    phase: "LOBBY" as string,
    me: null as string | null,
  });
  dataRef.current = {
    counts: view?.districtCounts ?? {},
    positions: view?.positions ?? {},
    npcsHere: view?.npcsHere ?? {},
    names: view?.names ?? {},
    humans: view?.humans ?? [],
    roster: view?.roster ?? {},
    viewerDistrict: view?.viewerDistrict ?? null,
    viewerRoom: view?.viewerRoom ?? null,
    phase: view?.phase ?? "LOBBY",
    me: playerId,
  };

  useEffect(() => {
    let disposed = false;
    const app = new Application();

    const districtPos: Record<string, { x: number; y: number }> = {};
    const badges: Record<string, Text> = {};
    const tokens = new Map<string, Token>();
    let W = 0;
    let H = 0;

    const groundLayer = new Container();
    const roadLayer = new Container();
    const buildingLayer = new Container();
    const tokenLayer = new Container();
    const rainLayer = new Container();
    const overlay = new Graphics();
    const rain: { g: Graphics; speed: number }[] = [];

    const rand = (s: number) => {
      const x = Math.sin(s) * 10000;
      return x - Math.floor(x);
    };

    const buildStatic = () => {
      W = app.screen.width;
      H = app.screen.height;

      groundLayer.removeChildren();
      groundLayer.addChild(new Graphics().rect(0, 0, W, H).fill({ color: 0x070912 }));
      const grid = new Graphics();
      const step = 44;
      for (let x = 0; x <= W; x += step) grid.moveTo(x, 0).lineTo(x, H);
      for (let y = 0; y <= H; y += step) grid.moveTo(0, y).lineTo(W, y);
      grid.stroke({ width: 1, color: 0x1b2540, alpha: 0.6 });
      groundLayer.addChild(grid);

      for (const loc of CITY_MAP) districtPos[loc.id] = { x: loc.x * W, y: loc.y * H };

      roadLayer.removeChildren();
      const glowRoads = new Graphics();
      const roads = new Graphics();
      const dashes = new Graphics();
      for (const [a, b] of CITY_EDGES) {
        const pa = districtPos[a];
        const pb = districtPos[b];
        if (!pa || !pb) continue;
        glowRoads.moveTo(pa.x, pa.y).lineTo(pb.x, pb.y);
        roads.moveTo(pa.x, pa.y).lineTo(pb.x, pb.y);
        const dx = pb.x - pa.x;
        const dy = pb.y - pa.y;
        const n = Math.floor(Math.hypot(dx, dy) / 22);
        for (let i = 0; i < n; i += 2) {
          dashes
            .moveTo(pa.x + (dx * i) / n, pa.y + (dy * i) / n)
            .lineTo(pa.x + (dx * (i + 1)) / n, pa.y + (dy * (i + 1)) / n);
        }
      }
      glowRoads.stroke({ width: 16, color: 0x14314a, alpha: 0.5 });
      roads.stroke({ width: 4, color: 0x0e1a2e });
      dashes.stroke({ width: 1.5, color: 0x2dd4bf, alpha: 0.35 });
      roadLayer.addChild(glowRoads, roads, dashes);

      buildingLayer.removeChildren();
      for (const key of Object.keys(badges)) delete badges[key];
      for (const loc of CITY_MAP) {
        const c = DISTRICT_COLOR[loc.id] ?? 0x22d3ee;
        const p = districtPos[loc.id];
        const node = new Container();
        node.x = p.x;
        node.y = p.y;
        (node as unknown as { _accent: number })._accent = c;
        const bw = 92;
        const bh = 62;
        const glow = new Graphics()
          .roundRect(-bw / 2 - 8, -bh / 2 - 8, bw + 16, bh + 16, 12)
          .fill({ color: c, alpha: 0.08 });
        const base = new Graphics()
          .roundRect(-bw / 2, -bh / 2, bw, bh, 8)
          .fill({ color: 0x0c1424 })
          .stroke({ width: 2, color: c, alpha: 0.9 });
        const windows = new Graphics();
        for (let r = 0; r < 3; r++) {
          for (let col = 0; col < 5; col++) {
            const on = rand(loc.x * 100 + r * 7 + col * 13) > 0.45;
            const wx = -bw / 2 + 12 + col * ((bw - 24) / 4);
            const wy = -bh / 2 + 14 + r * ((bh - 28) / 2);
            windows.rect(wx - 4, wy - 4, 8, 8).fill({ color: on ? c : 0x1a2338, alpha: on ? 0.9 : 0.5 });
          }
        }
        const label = new Text({
          text: loc.name.toUpperCase(),
          style: { fill: c, fontSize: 11, fontFamily: "monospace", letterSpacing: 2, fontWeight: "700" },
        });
        label.anchor.set(0.5, 1);
        label.y = -bh / 2 - 12;
        // anonymous public head-count badge
        const badge = new Text({
          text: "",
          style: { fill: 0xe2e8f0, fontSize: 11, fontFamily: "monospace", letterSpacing: 1 },
        });
        badge.anchor.set(0.5, 0);
        badge.y = bh / 2 + 8;
        badges[loc.id] = badge;
        node.addChild(glow, base, windows, label, badge);
        buildingLayer.addChild(node);
      }

      rainLayer.removeChildren();
      rain.length = 0;
      for (let i = 0; i < 90; i++) {
        const g = new Graphics().rect(0, 0, 1.4, 12).fill({ color: 0x5eead4, alpha: 0.12 });
        g.x = Math.random() * W;
        g.y = Math.random() * H;
        rainLayer.addChild(g);
        rain.push({ g, speed: 4 + Math.random() * 6 });
      }
    };

    const playerColor = (id: string) => {
      const { roster, humans, me } = dataRef.current;
      const alive = roster[id] ?? true;
      if (!alive) return 0x475569;
      if (id === me) return 0x22d3ee;
      return humans.includes(id) ? 0x34d399 : 0xff2e97;
    };

    const drawPlayer = (t: Token, color: number) => {
      t.gfx.clear();
      t.gfx.ellipse(0, 12, 11, 4).fill({ color: 0x000000, alpha: 0.35 });
      t.gfx.roundRect(-7, -6, 14, 18, 5).fill({ color });
      t.gfx.circle(0, -12, 6).fill({ color: 0xf1f5f9 });
      t.gfx.rect(-4, -13, 8, 2.5).fill({ color });
    };

    const drawNpc = (t: Token) => {
      t.gfx.clear();
      t.gfx.ellipse(0, 12, 11, 4).fill({ color: 0x000000, alpha: 0.35 });
      t.gfx.roundRect(-7, -6, 14, 18, 4).fill({ color: 0xf59e0b, alpha: 0.9 });
      t.gfx.circle(0, -12, 6).fill({ color: 0xfde68a });
      t.gfx.rect(-4, -13, 8, 2.5).fill({ color: 0x92400e });
    };

    const ensureToken = (key: string, colorSeed: number) => {
      let t = tokens.get(key);
      if (!t) {
        const container = new Container();
        const gfx = new Graphics();
        const label = new Text({
          text: "",
          style: { fill: 0xffffff, fontSize: 10, fontFamily: "monospace", letterSpacing: 1 },
        });
        label.anchor.set(0.5, 0);
        label.y = 16;
        container.addChild(gfx, label);
        tokenLayer.addChild(container);
        t = { container, gfx, label, x: 0, y: 0, tx: 0, ty: 0, seed: rand(colorSeed) * 6.28 };
        tokens.set(key, t);
      }
      return t;
    };

    const sync = () => {
      const d = dataRef.current;
      const base = d.viewerDistrict ? districtPos[d.viewerDistrict] : null;

      // district head-count badges (public, anonymous)
      for (const loc of CITY_MAP) {
        const badge = badges[loc.id];
        if (!badge) continue;
        const n = d.counts[loc.id] ?? 0;
        badge.text = n > 0 ? `◍ ${n}` : "·";
        badge.style.fill = n > 0 ? (DISTRICT_COLOR[loc.id] ?? 0xe2e8f0) : 0x334155;
      }

      const wanted = new Set<string>();
      let slot = 0;
      const place = () => {
        const col = slot % 4;
        const row = Math.floor(slot / 4);
        slot += 1;
        const bx = base ? base.x : W / 2;
        const by = base ? base.y : H / 2;
        return { x: bx - 24 + col * 16, y: by + 46 + row * 22 };
      };

      if (base) {
        // co-located players (share your room), including you
        for (const id of Object.keys(d.positions)) {
          const key = `p:${id}`;
          wanted.add(key);
          const t = ensureToken(key, id.length + slot + 1);
          const target = place();
          t.tx = target.x;
          t.ty = target.y;
          const color = playerColor(id);
          drawPlayer(t, color);
          const tag = id === d.me ? " ◂you" : d.humans.includes(id) ? "" : " ·ai";
          t.label.text = (d.names[id] ?? id) + tag;
          t.label.style.fill = color;
        }
        // co-located NPCs
        for (const [id, name] of Object.entries(d.npcsHere)) {
          const key = `n:${id}`;
          wanted.add(key);
          const t = ensureToken(key, id.length + slot + 7);
          const target = place();
          t.tx = target.x;
          t.ty = target.y;
          drawNpc(t);
          t.label.text = name;
          t.label.style.fill = 0xfbbf24;
        }
      }

      for (const [key, t] of tokens) {
        if (!wanted.has(key)) {
          t.container.destroy();
          tokens.delete(key);
        }
      }
    };

    const tick = () => {
      const t = performance.now() / 1000;
      for (const tok of tokens.values()) {
        if (tok.x === 0 && tok.y === 0) {
          tok.x = tok.tx;
          tok.y = tok.ty;
        }
        tok.x += (tok.tx - tok.x) * 0.14;
        tok.y += (tok.ty - tok.y) * 0.14;
        tok.container.x = tok.x;
        tok.container.y = tok.y + Math.sin(t * 2 + tok.seed) * 1.6;
      }
      for (const node of buildingLayer.children) {
        const accent = (node as unknown as { _accent?: number })._accent;
        if (accent !== undefined) {
          const glow = (node as Container).children[0] as Graphics;
          glow.alpha = 0.06 + (Math.sin(t * 1.5 + accent) * 0.5 + 0.5) * 0.08;
        }
      }
      for (const r of rain) {
        r.g.y += r.speed;
        r.g.x -= r.speed * 0.3;
        if (r.g.y > H) {
          r.g.y = -12;
          r.g.x = Math.random() * (W + 40);
        }
      }
      const phase = dataRef.current.phase;
      const night = phase === "NIGHT";
      const voting = phase === "VOTING";
      overlay
        .clear()
        .rect(0, 0, W, H)
        .fill({ color: night ? 0x03040f : voting ? 0x1a0716 : 0x0a1020, alpha: night ? 0.55 : voting ? 0.3 : 0.14 });
      rainLayer.alpha = night ? 0.9 : 0.4;
    };

    app.init({ resizeTo: hostRef.current!, backgroundAlpha: 0, antialias: true }).then(() => {
      if (disposed) {
        app.destroy(true);
        return;
      }
      hostRef.current!.appendChild(app.canvas);
      app.stage.addChild(groundLayer, roadLayer, buildingLayer, tokenLayer, rainLayer, overlay);
      buildStatic();
      sync();
      (hostRef.current as unknown as { _sync?: () => void })._sync = sync;
      app.renderer.on("resize", () => {
        buildStatic();
        sync();
      });
      app.ticker.add(tick);
    });

    return () => {
      disposed = true;
      try {
        app.destroy(true);
      } catch {
        /* already gone */
      }
    };
  }, []);

  useEffect(() => {
    (hostRef.current as unknown as { _sync?: () => void })?._sync?.();
  }, [view?.positions, view?.npcsHere, view?.districtCounts, view?.roster, view?.viewerDistrict, playerId]);

  return <div ref={hostRef} className="h-full w-full" />;
}
