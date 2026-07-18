import { useEffect, useRef } from "react";
import { WS_BASE } from "../config";
import { useGameStore } from "../stores/gameStore";
import type { PlayerView } from "../types/Player";
import type { Intent } from "../types/Event";

/**
 * Opens the WebSocket to the authoritative backend for a given (gameId, playerId), streams
 * redacted PlayerView snapshots into the store, and returns a `send` for client intents.
 */
export function useWebSocket(gameId: string | null, playerId: string | null) {
  const socketRef = useRef<WebSocket | null>(null);
  const setView = useGameStore((s) => s.setView);
  const setConnected = useGameStore((s) => s.setConnected);

  useEffect(() => {
    if (!gameId || !playerId) return;

    const ws = new WebSocket(`${WS_BASE}?gameId=${gameId}&playerId=${playerId}`);
    socketRef.current = ws;

    ws.onopen = () => setConnected(true);
    ws.onclose = () => setConnected(false);
    ws.onmessage = (event) => {
      try {
        const view = JSON.parse(event.data) as PlayerView;
        setView(view);
      } catch {
        /* ignore malformed frames */
      }
    };

    return () => {
      ws.close();
      socketRef.current = null;
    };
  }, [gameId, playerId, setView, setConnected]);

  const send = (intent: Intent) => {
    const ws = socketRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(intent));
    }
  };

  return { send };
}
