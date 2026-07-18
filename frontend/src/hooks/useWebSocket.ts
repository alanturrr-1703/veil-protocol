import { useEffect, useRef } from "react";
import { WS_BASE } from "../config";
import { useGameStore } from "../stores/gameStore";
import type { PlayerView } from "../types/Player";
import type { Intent } from "../types/Event";

/**
 * Opens the WebSocket to the authoritative backend for a given (gameId, playerId), streams
 * redacted PlayerView snapshots into the store, and returns a `send` for client intents.
 *
 * Auto-reconnects: if the backend isn't up yet or restarts (a very common local-dev case),
 * the socket keeps retrying with a short backoff, so movement/chat recover on their own once
 * the server is back — no browser refresh needed.
 */
export function useWebSocket(gameId: string | null, playerId: string | null) {
  const socketRef = useRef<WebSocket | null>(null);
  const setView = useGameStore((s) => s.setView);
  const setConnected = useGameStore((s) => s.setConnected);

  useEffect(() => {
    if (!gameId || !playerId) return;

    let closedByCleanup = false;
    let retry: ReturnType<typeof setTimeout> | null = null;
    let attempts = 0;

    const connect = () => {
      const ws = new WebSocket(`${WS_BASE}?gameId=${gameId}&playerId=${playerId}`);
      socketRef.current = ws;

      ws.onopen = () => {
        attempts = 0;
        setConnected(true);
      };
      ws.onmessage = (event) => {
        try {
          setView(JSON.parse(event.data) as PlayerView);
        } catch {
          /* ignore malformed frames */
        }
      };
      ws.onclose = () => {
        setConnected(false);
        if (closedByCleanup) return;
        // backoff: 0.5s, 1s, 2s … capped at 5s
        const delay = Math.min(5000, 500 * 2 ** attempts);
        attempts += 1;
        retry = setTimeout(connect, delay);
      };
      ws.onerror = () => ws.close();
    };

    connect();

    return () => {
      closedByCleanup = true;
      if (retry) clearTimeout(retry);
      socketRef.current?.close();
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
