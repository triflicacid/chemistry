import { type FormEvent, type KeyboardEvent, useState } from "react";
import type { SessionState } from "../session/messages";
import type { SubmitResult } from "../session/useSession";

export interface SmilesBarProps {
  state: SessionState | null;
  onSubmit: (smiles: string) => Promise<SubmitResult>;
}

/** Shows the molecule's SMILES and lets the user edit it and rebuild the structure from the result. */
export function SmilesBar({ state, onSubmit }: SmilesBarProps) {
  // null means the user has not edited, so the field shows whatever the session says
  const [draft, setDraft] = useState<string | null>(null);
  const [started, setStarted] = useState<string | null>(null);
  const [refusal, setRefusal] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const current = state?.smiles ?? "";
  const shown = draft ?? current;
  const dirty = draft !== null && draft !== current;
  const moved = draft !== null && started !== null && started !== current;

  const reset = () => {
    setDraft(null);
    setStarted(null);
    setRefusal(null);
  };

  const submit = async (event?: FormEvent) => {
    event?.preventDefault();
    if (!dirty || busy || shown.trim() === "") {
      return;
    }

    setBusy(true);
    setRefusal(null);
    const result = await onSubmit(shown.trim());
    setBusy(false);

    if (result.kind === "refused") {
      setRefusal(result.message);
    } else {
      reset();
    }
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === "Escape") {
      reset();
    }
  };

  return (
    <form className="smiles-bar" onSubmit={submit} aria-label="SMILES">
      <label htmlFor="smiles-field">SMILES</label>
      <input
        id="smiles-field"
        className="mono"
        type="text"
        spellCheck={false}
        autoComplete="off"
        disabled={!state}
        placeholder={state ? undefined : "Open a molecule to see its SMILES"}
        value={shown}
        aria-invalid={refusal !== null}
        onFocus={() => setStarted(current)}
        onChange={(event) => {
          setDraft(event.target.value);
          setRefusal(null);
        }}
        onKeyDown={onKeyDown}
      />
      {dirty && (
        <button type="button" className="tool" onClick={reset} disabled={busy}>
          Reset
        </button>
      )}
      <button
        type="submit"
        className="tool"
        disabled={!dirty || busy || shown.trim() === ""}
        title="Replaces the whole structure with this one. Atom and bond identifiers change and the selection is cleared."
      >
        {busy ? "Rebuilding…" : "Rebuild structure"}
      </button>
      {refusal && (
        <span className="smiles-note failed" role="alert">
          {refusal}
        </span>
      )}
      {!refusal && moved && (
        <span className="smiles-note" role="status">
          The structure has changed since you started editing.
        </span>
      )}
    </form>
  );
}
