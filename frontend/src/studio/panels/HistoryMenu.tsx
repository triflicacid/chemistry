import {useCallback, useEffect, useRef, useState} from "react";
import type {HistoryEntry} from "../session/messages";

/** Writes a time as dd/mm/yyyy hh:mm:ss in the viewer's time zone. */
export function formatEditTime(at: number): string {
  const date = new Date(at);
  const two = (n: number) => String(n).padStart(2, "0");
  return `${two(date.getDate())}/${two(date.getMonth() + 1)}/${date.getFullYear()} ${two(date.getHours())}:${two(date.getMinutes())}:${two(date.getSeconds())}`;
}

export interface HistoryMenuProps {
  /** Whether there is any history to show. */
  enabled: boolean;
  /** Lists the edits made, or resolves null if they could not be listed. */
  onLoad: () => Promise<{ entries: HistoryEntry[]; position: number } | null>;
  /** Moves to a point in the history, forgetting the edits after it if asked. */
  onGoTo: (position: number, discard: boolean) => void;
  /** Forgets every edit, leaving the structure as it is. */
  onClear: () => void;
}

/** A button that opens the list of edits, each of which can be gone to or rewound to. */
export function HistoryMenu({ enabled, onLoad, onGoTo, onClear }: HistoryMenuProps) {
  const [open, setOpen] = useState(false);
  const [history, setHistory] = useState<{ entries: HistoryEntry[]; position: number } | null>(null);
  const root = useRef<HTMLDivElement>(null);

  const close = useCallback(() => setOpen(false), []);

  useEffect(() => {
    if (!open) {
      return;
    }

    const away = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) {
        close();
      }
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        close();
      }
    };
    document.addEventListener("pointerdown", away);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", away);
      document.removeEventListener("keydown", escape);
    };
  }, [open, close]);

  const toggle = () => {
    if (open) {
      close();
      return;
    }
    setOpen(true);
    setHistory(null);
    void onLoad().then((loaded) => (loaded ? setHistory(loaded) : close()));
  };

  const go = (position: number, discard: boolean) => {
    close();
    onGoTo(position, discard);
  };

  return (
    <div className="history" ref={root}>
      <button
        type="button"
        className="tool"
        disabled={!enabled}
        aria-haspopup="true"
        aria-expanded={open}
        title="Go back to any earlier state, or rewind to it"
        onClick={toggle}
      >
        History
      </button>
      {open && (
        <div className="history-menu" role="menu" aria-label="History">
          {history === null ? (
            <div className="history-note">Loading</div>
          ) : (
            <>
              <div className="history-head">
                <button
                  type="button"
                  role="menuitem"
                  title="Permanently forget every edit, keeping the structure as it is"
                  onClick={() => {
                    close();
                    onClear();
                  }}
                >
                  Clear history
                </button>
              </div>
              <ol>
                {history.entries
                  .map((entry, i) => (
                    <Row key={i} label={`${i + 1}. ${entry.label}`} at={entry.at} position={i + 1} current={history.position} total={history.entries.length} onGoTo={go} />
                  ))
                  .reverse()}
                <Row label="Start" position={0} current={history.position} total={history.entries.length} onGoTo={go} />
              </ol>
            </>
          )}
        </div>
      )}
    </div>
  );
}

interface RowProps {
  label: string;
  /** When the edit was made; absent for the start. */
  at?: number;
  position: number;
  current: number;
  /** How many edits the history holds. */
  total: number;
  onGoTo: (position: number, discard: boolean) => void;
}

function Row({ label, at, position, current, total, onGoTo }: RowProps) {
  const here = position === current;
  const later = position > current;
  const dropped = total - position;

  return (
    <li className={`history-row${here ? " history-here" : ""}${later ? " history-later" : ""}`} role="none">
      <span className="history-label">
        {label}
        {at !== undefined && <span className="history-time">{formatEditTime(at)}</span>}
      </span>
      {here ? (
        <span className="history-note">current</span>
      ) : (
        <span className="history-actions">
          <button type="button" role="menuitem" title="Go here, keeping the later edits to redo" onClick={() => onGoTo(position, false)}>
            Go to
          </button>
          {!later && (
            <button
              type="button"
              role="menuitem"
              title={`Go here and permanently discard the ${dropped} later ${dropped === 1 ? "edit" : "edits"}`}
              onClick={() => onGoTo(position, true)}
            >
              Rewind
            </button>
          )}
        </span>
      )}
    </li>
  );
}
