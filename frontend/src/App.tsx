import { useState } from "react";
import { motion } from "framer-motion";
import { Providers } from "./app/providers";
import { useGameStore } from "./stores/gameStore";
import { useWebSocket } from "./hooks/useWebSocket";
import { useMidnight } from "./hooks/useMidnight";
import { gameClient } from "./api/gameClient";
import { DEMO_PLAYERS } from "./config";

import { NeonMap } from "./components/city/NeonMap";
import { PlayerHUD } from "./components/game/PlayerHUD";
import { ActionBar } from "./components/game/ActionBar";
import { VotingPanel } from "./components/game/VotingPanel";
import { Timeline } from "./components/game/Timeline";
import { EvidenceBoard } from "./components/game/DayPhase";
import { RoleReveal } from "./components/game/RoleReveal";
import { NightOverlay } from "./components/game/NightPhase";
import { NPCDialogue } from "./components/npc/NPCDialogue";
import { DirectorControls } from "./components/game/DirectorControls";
import { ChatPanel } from "./components/chat/ChatPanel";
import { Leaderboard } from "./components/game/Leaderboard";
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

function Lobby() {
  const setSession = useGameStore((s) => s.setSession);
  const setCommitments = useGameStore((s) => s.setCommitments);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const createCity = async () => {
    setBusy(true);
    setError(null);
    try {
      const res = await gameClient.createGame();
      setCommitments(res.commitments);
      setSession(res.gameId, null);
    } catch {
      setError("Cannot reach the backend on :8080. Start it with `mvn spring-boot:run`.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 p-6">
      <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }}>
        <Title />
      </motion.div>
      <HologramCard title="Neon City // Uplink" accent="cyan" className="w-full max-w-md text-center">
        <p className="mb-4 text-sm text-slate-300">
          A confidential deduction sim. Roles live in the Midnight layer — the client only
          ever receives what you are authorized to see.
        </p>
        <button className="btn w-full justify-center text-neon-cyan" disabled={busy} onClick={createCity}>
          {busy ? "Booting…" : "Establish Uplink"}
        </button>
        {error && <p className="mt-3 text-xs text-neon-pink">{error}</p>}
      </HologramCard>
      <div className="w-full max-w-md">
        <Leaderboard />
      </div>
    </div>
  );
}

function OperativeSelect() {
  const gameId = useGameStore((s) => s.gameId)!;
  const setSession = useGameStore((s) => s.setSession);
  const { shortHash } = useMidnight();

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-8 p-6">
      <Title />
      <HologramCard title="Select Operative" accent="violet" className="w-full max-w-lg">
        <p className="mb-4 text-xs text-slate-400">
          Each operative sees a different confidential slice. Their role is hidden behind a
          commitment (hash) below — never the role itself.
        </p>
        <div className="grid grid-cols-2 gap-3">
          {DEMO_PLAYERS.map((p) => (
            <button
              key={p.id}
              onClick={() => setSession(gameId, p.id)}
              className="glass rounded-xl p-4 text-left transition hover:shadow-neon"
            >
              <p className="text-lg font-semibold text-white">{p.name}</p>
              <p className="mt-1 truncate text-[10px] text-neon-cyan">◈ {shortHash(p.id)}…</p>
            </button>
          ))}
        </div>
      </HologramCard>
    </div>
  );
}

function GameScreen() {
  const gameId = useGameStore((s) => s.gameId);
  const playerId = useGameStore((s) => s.playerId);
  const leaderboardBump = useGameStore((s) => s.leaderboardBump);
  const { send } = useWebSocket(gameId, playerId);

  return (
    <div className="min-h-screen p-4">
      <RoleReveal />
      <NightOverlay />

      <header className="mb-4 flex items-center justify-between gap-4">
        <div className="flex items-baseline gap-3">
          <span className="text-2xl font-bold tracking-[0.3em] text-neon-cyan neon-text">VEIL</span>
          <span className="text-xs uppercase tracking-[0.4em] text-neon-pink">neon city</span>
        </div>
        <DirectorControls />
      </header>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[320px_1fr_340px]">
        <aside className="space-y-4">
          <PlayerHUD />
          <ActionBar send={send} />
          <VotingPanel send={send} />
        </aside>

        <main className="flex min-h-[60vh] flex-col gap-4">
          <HologramCard title="Sector Grid" accent="cyan" className="relative flex-1">
            <div className="absolute inset-3 top-10">
              <NeonMap />
            </div>
          </HologramCard>
          <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
            <Timeline />
            <ChatPanel send={send} />
          </div>
        </main>

        <aside className="space-y-4">
          <NPCDialogue send={send} />
          <EvidenceBoard />
          <Leaderboard refreshKey={leaderboardBump} />
        </aside>
      </div>
    </div>
  );
}

export function App() {
  const gameId = useGameStore((s) => s.gameId);
  const playerId = useGameStore((s) => s.playerId);

  return (
    <Providers>
      {!gameId ? <Lobby /> : !playerId ? <OperativeSelect /> : <GameScreen />}
    </Providers>
  );
}
