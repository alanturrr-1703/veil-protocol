import type { ReactNode } from "react";

/**
 * App-wide providers. Kept minimal for now (global state lives in the Zustand store, which
 * needs no provider). Add theme / query / auth providers here as the app grows.
 */
export function Providers({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
