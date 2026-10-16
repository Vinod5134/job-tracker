import { Client } from "@stomp/stompjs";
import { currentToken } from "./auth";
import { API_URL } from "./config";
import type { LiveEvent } from "./types";

/**
 * Real-time connection to the backend (STOMP over WebSocket).
 * Pages register listeners with onLive(); the connection reconnects on its own
 * (for example when the free Render server wakes up from sleep).
 */

export type LiveState = "offline" | "connecting" | "live";

type Listener = (event: LiveEvent) => void;
type StateListener = (state: LiveState) => void;

const listeners = new Set<Listener>();
const stateListeners = new Set<StateListener>();
let client: Client | null = null;
let state: LiveState = "offline";

function setState(next: LiveState): void {
  state = next;
  stateListeners.forEach((l) => l(next));
}

export function startLive(): void {
  if (client) {
    return;
  }
  const c = new Client({
    brokerURL: `${API_URL.replace(/^http/, "ws")}/ws`,
    reconnectDelay: 5000,
    heartbeatIncoming: 20000,
    heartbeatOutgoing: 20000,
    beforeConnect: () => {
      // Read the token on every (re)connect, so a fresh sign-in is picked up
      const token = currentToken();
      c.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      setState("connecting");
    },
    onConnect: () => {
      setState("live");
      c.subscribe("/user/queue/updates", (message) => {
        try {
          const event = JSON.parse(message.body) as LiveEvent;
          listeners.forEach((l) => l(event));
        } catch {
          // ignore malformed messages
        }
      });
    },
    onWebSocketClose: () => {
      if (client) {
        setState("connecting");
      }
    },
    onStompError: () => setState("connecting"),
  });
  client = c;
  c.activate();
}

export async function stopLive(): Promise<void> {
  const c = client;
  client = null;
  if (c) {
    await c.deactivate();
  }
  setState("offline");
}

/** Subscribe to live events; returns an unsubscribe function. */
export function onLive(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function onLiveState(listener: StateListener): () => void {
  stateListeners.add(listener);
  listener(state);
  return () => stateListeners.delete(listener);
}
