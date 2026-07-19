import { useState } from "react";
import { motion } from "framer-motion";
import { Providers } from "./app/providers";
import { useGameStore } from "./stores/gameStore";
import { useWebSocket } from "./hooks/useWebSocket";
import { gameClient } from "./api/gameClient";
import { MIN_PLAYERS } from "./config";

import { NeonMap } from "./components/city/NeonMap";
import { PlayerHUD } from "./components/game/PlayerHUD";
import { ActionBar } from "./components/game/ActionBar";
import { VotingPanel } from "./components/game/VotingPanel";
import { Timeline } from "./components/game/Timeline";
import { EvidenceBoard } from "./components/game/DayPhase";
import { RoleReveal } from "./components/game/RoleReveal";
import { NightOverlay } from "./components/game/NightPhase";
import { VictoryOverlay } from "./components/game/VictoryOverlay";
import { NPCDialogue } from "./components/npc/NPCDialogue";
import { DirectorControls } from "./components/game/DirectorControls";
import { ChatPanel } from "./components/chat/ChatPanel";
import { Leaderboard } from "./components/game/Leaderboard";
import { Roster } from "./components/game/Roster";
import { Interior } from "./components/city/Interior";
import { RoomScene } from "./components/city/RoomScene";
import { Countdown } from "./components/game/Countdown";
import { HologramCard } from "./components/ui/HologramCard";

function Title() {
  return (
    <div className="text-center">
      <h1 className="text-5xl font-bold tracking-[0.35em] text-neon-cyan neon-text md:text-7xl">
        VEIL
      </h1>
      <p className="mt-1 text-sm uppercase tracking-[0.6em] text-neon-pink neon-text-pink">
        Protocol
      </p>
    </div>
  );
}

/** Landing screen: create a new room or join an existing one by its short code. */
function Landing() {
  const setSession = useGameStore((s) => s.setSession);
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState<"create" | "join" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const backendError = () =>
    setError("Cannot reach the backend on :8080. Start it with `npm run dev:backend`.");

  const create = async () => {
    setBusy("create");
    setError(null);
    try {
      const res = await gameClient.createRoom(name.trim() || "Host");
      setSession(res.code, res.playerId, res.isHost);
    } catch {
      backendError();
    } finally {
      setBusy(null);
    }
  };

  const join = async () => {
    const room = code.trim().toUpperCase();
    if (!room) return;
    setBusy("join");
    setError(null);
    try {
      const res = await gameClient.joinRoom(room, name.trim() || "Operative");
      setSession(res.code, res.playerId, res.isHost);
    } catch {
      setError("Room not found, or it has already started.");
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 p-6">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <Title />
      </motion.div>

      <HologramCard title="Neon City // Uplink" accent="cyan" className="w-full max-w-md">
        <p className="mb-4 text-sm text-slate-300">
          A confidential multiplayer deduction game. Roles live in the Midnight layer — the
          client only ever receives what you are authorized to see.
        </p>

        <label className="mb-1 block text-[10px] uppercase tracking-widest text-slate-400">
          Codename
        </label>
        <input
          className="mb-4 w-full rounded-lg border border-white/10 bg-black/40 px-3 py-2 text-sm text-white outline-none focus:border-neon-cyan"
          placeholder="e.g. Vex"
          value={name}
          maxLength={16}
          onChange={(e) => setName(e.target.value)}
        />

        <button
          className="btn mb-5 w-full justify-center text-neon-cyan"
          disabled={busy !== null}
          onClick={create}
        >
          {busy === "create" ? "Creating…" : "Create Room"}
        </button>

        <div className="mb-3 flex items-center gap-3 text-[10px] uppercase tracking-widest text-slate-500">
          <span className="h-px flex-1 bg-white/10" /> or join <span className="h-px flex-1 bg-white/10" />
        </div>

        <div className="flex gap-2">
          <input
            className="w-full rounded-lg border border-white/10 bg-black/40 px-3 py-2 text-sm uppercase tracking-[0.3em] text-white outline-none focus:border-neon-pink"
            placeholder="ROOM CODE"
            value={code}
            maxLength={6}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            onKeyDown={(e) => e.key === "Enter" && join()}
          />
          <button className="btn shrink-0 text-neon-pink" disabled={busy !== null} onClick={join}>
            {busy === "join" ? "…" : "Join"}
          </button>
        </div>

        {error && <p className="mt-3 text-xs text-neon-pink">{error}</p>}
      </HologramCard>

      <div className="w-full max-w-md">
        <Leaderboard />
      </div>
    </div>
  );
}

/** Pre-match waiting room: share the code, watch operatives arrive, host starts the match. */
function LobbyRoom() {
  const code = useGameStore((s) => s.code)!;
  const isHost = useGameStore((s) => s.isHost);
  const view = useGameStore((s) => s.view);
  const setCommitments = useGameStore((s) => s.setCommitments);
  const reset = useGameStore((s) => s.reset);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);

  const names = view?.names ?? {};
  const ids = Object.keys(names);
  const canStart = ids.length >= MIN_PLAYERS;

  const start = async () => {
    setBusy(true);
    try {
      const res = await gameClient.startRoom(code);
      setCommitments(res.commitments);
    } catch {
      /* the WS view will flip to NIGHT on success regardless */
    } finally {
      setBusy(false);
    }
  };

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {
      /* clipboard may be blocked; the code is shown anyway */
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 p-6">
      <Title />
      <HologramCard title="Assembly // Lobby" accent="violet" className="w-full max-w-md">
        <p className="mb-2 text-xs text-slate-400">Share this code so others can join:</p>
        <button
          onClick={copyCode}
          className="mb-5 w-full rounded-lg border border-neon-cyan/40 bg-black/50 py-3 text-center text-3xl font-bold tracking-[0.5em] text-neon-cyan shadow-neon"
          title="Click to copy"
        >
          {code}
        </button>
        {copied && <p className="-mt-3 mb-3 text-center text-[10px] text-neon-lime">copied</p>}

        <p className="mb-2 text-[10px] uppercase tracking-widest text-slate-400">
          Operatives ({ids.length})
        </p>
        <ul className="mb-5 space-y-1.5">
          {ids.map((id) => (
            <li
              key={id}
              className="flex items-center justify-between rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm"
            >
              <span className="text-white">{names[id]}</span>
              {id === view?.viewerId && (
                <span className="text-[9px] uppercase tracking-widest text-neon-cyan">you</span>
              )}
            </li>
          ))}
          {ids.length === 0 && (
            <li className="text-xs italic text-slate-500">connecting…</li>
          )}
        </ul>

        {isHost ? (
          <button
            className="btn w-full justify-center text-neon-lime"
            disabled={busy || !canStart}
            onClick={start}
          >
            {busy ? "Dealing roles…" : canStart ? "Start Match" : `Need ${MIN_PLAYERS}+ operatives`}
          </button>
        ) : (
          <p className="text-center text-xs text-slate-400">Waiting for the host to start…</p>
        )}

        <button className="btn-danger mt-3 w-full justify-center" onClick={reset}>
          Leave
        </button>
      </HologramCard>
    </div>
  );
}

function GameScreen({ send }: { send: ReturnType<typeof useWebSocket>["send"] }) {
  const leaderboardBump = useGameStore((s) => s.leaderboardBump);

  return (
    <div className="min-h-screen p-4">
      <RoleReveal />
      <NightOverlay />
      <VictoryOverlay />

      <header className="mb-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <div className="flex items-baseline gap-3">
            <span className="text-2xl font-bold tracking-[0.3em] text-neon-cyan neon-text">VEIL</span>
            <span className="text-xs uppercase tracking-[0.4em] text-neon-pink">neon city</span>
          </div>
          <Countdown />
        </div>
        <DirectorControls />
      </header>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[320px_1fr_340px]">
        <aside className="space-y-4">
          <PlayerHUD />
          <Roster />
          <ActionBar send={send} />
          <VotingPanel send={send} />
        </aside>

        <main className="flex min-h-[70vh] flex-col gap-4">
          <HologramCard title="Room · Street Level" accent="violet" className="relative min-h-[520px] flex-1">
            <div className="absolute inset-3 top-10">
              <RoomScene send={send} />
            </div>
            <div className="absolute right-5 top-12 z-30 w-[230px] rounded-lg border border-neon-cyan/40 bg-black/70 p-1.5 shadow-neon backdrop-blur">
              <p className="mb-1 flex items-center gap-1.5 px-0.5 text-[9px] uppercase tracking-widest text-neon-cyan">
                <span className="h-1.5 w-1.5 rounded-full bg-neon-cyan" /> Sector Grid
              </p>
              <div className="relative h-[150px] w-full overflow-hidden rounded">
                <NeonMap compact />
              </div>
            </div>
          </HologramCard>
          <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
            <Timeline />
            <ChatPanel send={send} />
          </div>
        </main>

        <aside className="space-y-4">
          <Interior />
          <NPCDialogue send={send} />
          <EvidenceBoard />
          <Leaderboard refreshKey={leaderboardBump} />
        </aside>
      </div>
    </div>
  );
}

/** Once seated in a room, hold the WebSocket open and switch between lobby and match. */
function Session() {
  const code = useGameStore((s) => s.code);
  const playerId = useGameStore((s) => s.playerId);
  const view = useGameStore((s) => s.view);
  const { send } = useWebSocket(code, playerId);

  // Until the first view arrives, or while still in the lobby, show the waiting room.
  const phase = view?.phase ?? "LOBBY";
  if (phase === "LOBBY" || phase === "NONE") return <LobbyRoom />;
  return <GameScreen send={send} />;
}

export function App() {
  const playerId = useGameStore((s) => s.playerId);
  return <Providers>{!playerId ? <Landing /> : <Session />}</Providers>;
}
