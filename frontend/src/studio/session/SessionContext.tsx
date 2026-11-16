import { createContext, type ReactNode, useContext } from "react";
import type { Session } from "./useSession";

const SessionContext = createContext<Session | null>(null);

export interface SessionProviderProps {
  value: Session;
  children: ReactNode;
}

export function SessionProvider({ value, children }: SessionProviderProps) {
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

/**
 * @returns the session
 */
export function useSessionContext(): Session {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error("useSessionContext needs a SessionProvider above it");
  }
  return session;
}
