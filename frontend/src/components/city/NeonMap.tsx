import { useEffect, useRef } from "react";
import { Application, Container, Graphics, Text } from "pixi.js";
import { CITY_MAP, CITY_EDGES } from "../../types/Location";
import { useGameStore } from "../../stores/gameStore";

/**
 * A top-down 2D neon city rendered with PixiJS — think Stardew Valley by way of Blade
 * Runner. Tiled asphalt, glowing districts with lit windows, and little operative
 * characters that walk between districts. Everything animates on a ticker (glow pulse,
 * idle bob, rain), and the ambient light shifts with the phase (deep blue at Night).
 *
 * It is purely a VIEW of public state: it reads `positions` (public), `roster`, `names`
 * and `humans` from the redacted PlayerView — never anything confidential.
 */

const DISTRICT_COLOR: Record<string, number> = {
  plaza: 0x22d3ee,
  market: 0xffb020,
  docks: 0x38bdf8,
  tower: 0xa855f7,
  alley: 0xff2e97,
  garden: 0x34d399,
};

interface Avatar {
  container: Container;
  body: Graphics;
  ring: Graphics;
  nameText: Text;
  x: number;
  y: number;
  tx: number;
  ty: number;
  seed: number;
  alive: boolean;
}

export function NeonMap() {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const view = useGameStore((s) => s.view);
  const playerId = useGameStore((s) => s.playerId);

  const dataRef = useRef({
    roster: {} as Record<string, boolean>,
    positions: {} as Record<string, string>,
    names: {} as Record<string, string>,
    humans: [] as string[],
    phase: "LOBBY" as string,
    me: null as string | null,
  });
  dataRef.current = {
    roster: view?.roster ?? {},
    positions: view?.positions ?? {},
    names: view?.names ?? {},
    humans: view?.humans ?? [],
    phase: view?.phase ?? "LOBBY",
    me: playerId,
  };

  useEffect(() => {
    let disposed = false;
    const app = new Application();

    const districtPos: Record<string, { x: number; y: number }> = {};
    const avatars = new Map<string, Avatar>();
    let W = 0;
    let H = 0;

    // Persistent layers (built once, repositioned on resize).
    const groundLayer = new Container();
    const roadLayer = new Container();
    const buildingLayer = new Container();
    const avatarLayer = new Container();
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

      // --- Tiled asphalt ground with a faint neon grid ---
      groundLayer.removeChildren();
      const bg = new Graphics().rect(0, 0, W, H).fill({ color: 0x070912 });
      groundLayer.addChild(bg);
      const grid = new Graphics();
      const step = 44;
      for (let x = 0; x <= W; x += step) grid.moveTo(x, 0).lineTo(x, H);
      for (let y = 0; y <= H; y += step) grid.moveTo(0, y).lineTo(W, y);
      grid.stroke({ width: 1, color: 0x1b2540, alpha: 0.6 });
      groundLayer.addChild(grid);

      // District screen positions.
      for (const loc of CITY_MAP) districtPos[loc.id] = { x: loc.x * W, y: loc.y * H };

      // --- Roads between districts ---
      roadLayer.removeChildren();
      const roads = new Graphics();
      const glowRoads = new Graphics();
      for (const [a, b] of CITY_EDGES) {
        const pa = districtPos[a];
        const pb = districtPos[b];
        if (!pa || !pb) continue;
        glowRoads.moveTo(pa.x, pa.y).lineTo(pb.x, pb.y);
        roads.moveTo(pa.x, pa.y).lineTo(pb.x, pb.y);
      }
      glowRoads.stroke({ width: 16, color: 0x14314a, alpha: 0.5 });
      roads.stroke({ width: 4, color: 0x0e1a2e, alpha: 1 });
      const dashes = new Graphics();
      for (const [a, b] of CITY_EDGES) {
        const pa = districtPos[a];
        const pb = districtPos[b];
        if (!pa || !pb) continue;
        const dx = pb.x - pa.x;
        const dy = pb.y - pa.y;
        const len = Math.hypot(dx, dy);
        const n = Math.floor(len / 22);
        for (let i = 0; i < n; i += 2) {
          const t0 = i / n;
          const t1 = (i + 1) / n;
          dashes.moveTo(pa.x + dx * t0, pa.y + dy * t0).lineTo(pa.x + dx * t1, pa.y + dy * t1);
        }
      }
      dashes.stroke({ width: 1.5, color: 0x2dd4bf, alpha: 0.35 });
      roadLayer.addChild(glowRoads, roads, dashes);

      // --- Buildings (districts) ---
      buildingLayer.removeChildren();
      for (const loc of CITY_MAP) {
        const c = DISTRICT_COLOR[loc.id] ?? 0x22d3ee;
        const p = districtPos[loc.id];
        const node = new Container();
        node.x = p.x;
        node.y = p.y;
        (node as unknown as { _accent: number })._accent = c;

        const bw = 92;
        const bh = 64;
        const glow = new Graphics()
          .roundRect(-bw / 2 - 8, -bh / 2 - 8, bw + 16, bh + 16, 12)
          .fill({ color: c, alpha: 0.08 });
        const base = new Graphics()
          .roundRect(-bw / 2, -bh / 2, bw, bh, 8)
          .fill({ color: 0x0c1424 })
          .stroke({ width: 2, color: c, alpha: 0.9 });
        // Lit windows.
        const windows = new Graphics();
        const cols = 5;
        const rows = 3;
        for (let r = 0; r < rows; r++) {
          for (let col = 0; col < cols; col++) {
            const on = rand(loc.x * 100 + r * 7 + col * 13) > 0.45;
            const wx = -bw / 2 + 12 + col * ((bw - 24) / (cols - 1));
            const wy = -bh / 2 + 14 + r * ((bh - 28) / (rows - 1));
            windows
              .rect(wx - 4, wy - 4, 8, 8)
              .fill({ color: on ? c : 0x1a2338, alpha: on ? 0.9 : 0.5 });
          }
        }
        const label = new Text({
          text: loc.name.toUpperCase(),
          style: { fill: c, fontSize: 11, fontFamily: "monospace", letterSpacing: 2, fontWeight: "700" },
        });
        label.anchor.set(0.5, 1);
        label.y = -bh / 2 - 12;
        node.addChild(glow, base, windows, label);
        buildingLayer.addChild(node);
      }

      // --- Rain ---
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

    const accentFor = (id: string, alive: boolean, isMe: boolean, isHuman: boolean) => {
      if (!alive) return 0x475569;
      if (isMe) return 0x22d3ee;
      return isHuman ? 0x34d399 : 0xff2e97;
    };

    const drawAvatarBody = (av: Avatar, color: number, alive: boolean) => {
      av.body.clear();
      // shadow
      av.body.ellipse(0, 12, 11, 4).fill({ color: 0x000000, alpha: 0.35 });
      // body
      av.body.roundRect(-7, -6, 14, 18, 5).fill({ color, alpha: alive ? 1 : 0.5 });
      // head
      av.body.circle(0, -12, 6).fill({ color: alive ? 0xf1f5f9 : 0x94a3b8, alpha: alive ? 1 : 0.5 });
      // visor
      av.body.rect(-4, -13, 8, 2.5).fill({ color, alpha: alive ? 1 : 0.4 });
    };

    const syncAvatars = () => {
      const { roster, positions, names, humans, me } = dataRef.current;
      const ids = Object.keys(names);
      // slot index per district for spacing characters out
      const slotByDistrict: Record<string, number> = {};

      for (const id of ids) {
        const alive = roster[id] ?? true;
        const isMe = id === me;
        const isHuman = humans.includes(id);
        const color = accentFor(id, alive, isMe, isHuman);
        const district = positions[id] ?? CITY_MAP[0].id;
        const base = districtPos[district] ?? { x: W / 2, y: H / 2 };
        const slot = slotByDistrict[district] ?? 0;
        slotByDistrict[district] = slot + 1;
        const col = slot % 4;
        const row = Math.floor(slot / 4);
        const tx = base.x - 24 + col * 16;
        const ty = base.y + 44 + row * 22;

        let av = avatars.get(id);
        if (!av) {
          const container = new Container();
          const body = new Graphics();
          const ring = new Graphics();
          const nameText = new Text({
            text: "",
            style: { fill: color, fontSize: 10, fontFamily: "monospace", letterSpacing: 1 },
          });
          nameText.anchor.set(0.5, 0);
          nameText.y = 16;
          container.addChild(ring, body, nameText);
          container.x = tx;
          container.y = ty;
          avatarLayer.addChild(container);
          av = { container, body, ring, nameText, x: tx, y: ty, tx, ty, seed: rand(ids.indexOf(id) + 1) * 6.28, alive };
          avatars.set(id, av);
        }
        av.tx = tx;
        av.ty = ty;
        av.alive = alive;
        drawAvatarBody(av, color, alive);
        av.ring.clear();
        if (isMe && alive) av.ring.circle(0, 2, 16).stroke({ width: 2, color, alpha: 0.7 });
        const tag = isMe ? " ◂you" : isHuman ? "" : " ·ai";
        av.nameText.text = (names[id] ?? id) + (alive ? tag : " ✝");
        av.nameText.style.fill = color;
      }

      // remove avatars no longer present
      for (const [id, av] of avatars) {
        if (!ids.includes(id)) {
          av.container.destroy();
          avatars.delete(id);
        }
      }
    };

    const tickerFn = () => {
      const t = performance.now() / 1000;
      // avatars: ease toward target + idle bob
      for (const av of avatars.values()) {
        av.x += (av.tx - av.x) * 0.12;
        av.y += (av.ty - av.y) * 0.12;
        const bob = av.alive ? Math.sin(t * 2 + av.seed) * 1.6 : 0;
        av.container.x = av.x;
        av.container.y = av.y + bob;
      }
      // building glow pulse
      for (const node of buildingLayer.children) {
        const accent = (node as unknown as { _accent: number })._accent;
        if (accent !== undefined) {
          const glow = (node as Container).children[0] as Graphics;
          glow.alpha = 0.06 + (Math.sin(t * 1.5 + accent) * 0.5 + 0.5) * 0.08;
        }
      }
      // rain
      for (const r of rain) {
        r.g.y += r.speed;
        r.g.x -= r.speed * 0.3;
        if (r.g.y > H) {
          r.g.y = -12;
          r.g.x = Math.random() * (W + 40);
        }
      }
      // ambient day/night overlay
      const night = dataRef.current.phase === "NIGHT";
      const voting = dataRef.current.phase === "VOTING";
      const targetAlpha = night ? 0.55 : voting ? 0.3 : 0.14;
      const color = night ? 0x03040f : voting ? 0x1a0716 : 0x0a1020;
      overlay.clear().rect(0, 0, W, H).fill({ color, alpha: targetAlpha });
      rainLayer.alpha = night ? 0.9 : 0.4;
    };

    app
      .init({ resizeTo: hostRef.current!, backgroundAlpha: 0, antialias: true })
      .then(() => {
        if (disposed) {
          app.destroy(true);
          return;
        }
        hostRef.current!.appendChild(app.canvas);
        app.stage.addChild(groundLayer, roadLayer, buildingLayer, avatarLayer, rainLayer, overlay);
        buildStatic();
        syncAvatars();
        (hostRef.current as unknown as { _sync?: () => void })._sync = syncAvatars;
        app.renderer.on("resize", () => {
          buildStatic();
          syncAvatars();
        });
        app.ticker.add(tickerFn);
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

  // Re-sync avatars whenever the public snapshot changes.
  useEffect(() => {
    const sync = (hostRef.current as unknown as { _sync?: () => void })?._sync;
    sync?.();
  }, [view?.positions, view?.roster, view?.names, view?.humans, playerId]);

  return <div ref={hostRef} className="h-full w-full" />;
}
