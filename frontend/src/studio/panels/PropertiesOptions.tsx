import {useEffect, useState} from "react";
import type {Models} from "../models/models";
import {
    CHARGE_MODELS,
    type ChargeModel,
    type ColourBy,
    DEFAULT_POLAR_THRESHOLD,
    MAX_POLAR_THRESHOLD,
    MIN_POLAR_THRESHOLD,
} from "../models/PropertiesModel";
import {useObservable} from "../models/observable";
import {divergingGradient} from "../../render/scale";
import {chargeScale} from "../layout/chargeScale";
import type {ChargesProgress} from "../session/messages";
import {useSessionContext} from "../session/SessionContext";

/** What the atoms can be coloured by, with what each means. */
const COLOUR_OPTIONS: { value: ColourBy; label: string; title: string }[] = [
  { value: "element", label: "Element", title: "Each atom has the colour of its element" },
  { value: "charge", label: "Partial charge", title: "Red where an atom has gained electrons and blue where it has lost them" },
];

/** How often the running timer is redrawn. */
const TICK_MS = 100;

/** What is shown on the molecule beyond its shape: which calculated property the atoms are coloured by. */
export function PropertiesOptions({ models }: { models: Models }) {
  const { properties } = models;
  const { colourBy, chargeModel, polarLabels, polarThreshold, open } = useObservable(properties);

  return (
    <section className="render-options" aria-label="Properties">
      <button type="button" className="render-header" aria-expanded={open} onClick={() => properties.openTo(!open)}>
        <span className="twisty">{open ? "▾" : "▸"}</span>
        Properties
      </button>
      {open && (
        <dl className="properties render-body">
          <dt>Colour by</dt>
          <dd>
            <select value={colourBy} aria-label="What the atoms are coloured by" onChange={(event) => properties.colourWith(event.target.value as ColourBy)}>
              {COLOUR_OPTIONS.map(({ value, label, title }) => (
                <option key={value} value={value} title={title}>
                  {label}
                </option>
              ))}
            </select>
          </dd>
          {colourBy === "charge" && (
            <>
              <dt>Model</dt>
              <dd>
                <select value={chargeModel} aria-label="How partial charges are calculated" onChange={(event) => properties.chargeWith(event.target.value as ChargeModel)}>
                  {CHARGE_MODELS.map(({ value, label, title }) => (
                    <option key={value} value={value} title={title}>
                      {label}
                    </option>
                  ))}
                </select>
              </dd>
              <dt>Labels</dt>
              <dd>
                <label className="legend-check" title="Write δ+ above atoms with a clearly positive charge and δ− above those with a clearly negative one. Atoms with a formal charge already show it, so they are left out">
                  <input type="checkbox" checked={polarLabels} onChange={(event) => properties.labelPolarity(event.target.checked)} />
                  Label polar atoms (δ+ / δ−)
                </label>
              </dd>
              {polarLabels && (
                <>
                  <dt>Beyond</dt>
                  <dd className="position">
                    <input
                      type="range"
                      className="render-slider"
                      min={Math.round(MIN_POLAR_THRESHOLD * 100)}
                      max={Math.round(MAX_POLAR_THRESHOLD * 100)}
                      step={1}
                      value={Math.round(polarThreshold * 100)}
                      aria-label="How far from zero a partial charge must be for its atom to be labelled, in elementary charges"
                      title="How far from zero a partial charge must be for its atom to be labelled. Double-click to put it back"
                      onChange={(event) => properties.thresholdTo(Number(event.target.value) / 100)}
                      onDoubleClick={() => properties.thresholdTo(DEFAULT_POLAR_THRESHOLD)}
                    />
                    <span className="unit render-value">±{polarThreshold.toFixed(2)}</span>
                  </dd>
                </>
              )}
              <ChargeLegend />
            </>
          )}
        </dl>
      )}
    </section>
  );
}

/** What the charge colours mean, how the calculation is getting on, and whether the colours can be trusted yet. */
function ChargeLegend() {
  const { charges, calculatingCharges, chargesProgress, state } = useSessionContext();
  const scale = chargeScale(charges);
  const current = charges !== null && state !== null && charges.session_id === state.session_id && charges.version === state.version;

  return (
    <>
      {calculatingCharges && (
        <>
          <dt>Status</dt>
          <dd className="legend-note" role="status">
            <Running startedAt={calculatingCharges.startedAt} progress={chargesProgress} />
          </dd>
        </>
      )}
      {!calculatingCharges && charges?.status === "failed" && (
        <>
          <dt>Charges</dt>
          <dd className="legend-note">
            {charges.message ?? "They could not be calculated."} <Took milliseconds={charges.elapsed_ms} />
          </dd>
        </>
      )}
      {scale && charges && (
        <>
          <dt>Scale</dt>
          <dd className="legend">
            <div
              className="legend-bar"
              style={{ background: divergingGradient() }}
              role="img"
              aria-label={`From minus ${scale.limit.toFixed(2)} to plus ${scale.limit.toFixed(2)} elementary charges`}
            />
            <div className="legend-labels">
              <span>−{scale.limit.toFixed(2)}</span>
              <span>0</span>
              <span>+{scale.limit.toFixed(2)}</span>
            </div>
          </dd>
          <dt>Charges</dt>
          <dd className="legend-note">
            {describeConvention(charges.convention)}
            {current && !calculatingCharges ? <Took milliseconds={charges.elapsed_ms} /> : " (updating…)"}
          </dd>
        </>
      )}
    </>
  );
}

/** How long a calculation has been running, counting up, with what the server last said it is doing. */
function Running({ startedAt, progress }: { startedAt: number; progress: ChargesProgress | null }) {
  const [now, setNow] = useState(() => Date.now());

  // the timer counts on its own, so that it keeps moving through the stretches where the server has nothing to say
  useEffect(() => {
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(timer);
  }, [startedAt]);

  return (
    <>
      <span className="mono">{seconds(Math.max(0, now - startedAt))}</span> {describeProgress(progress)}
    </>
  );
}

/** How long a finished calculation took, in brackets. */
function Took({ milliseconds }: { milliseconds: number }) {
  return <span className="legend-took"> ({seconds(milliseconds)})</span>;
}

/**
 * @param milliseconds a length of time
 * @returns it in seconds to a tenth
 */
export function seconds(milliseconds: number): string {
  return `${(milliseconds / 1000).toFixed(1)} s`;
}

/**
 * @param progress what the server last said of the calculation, or null if it has not
 * @returns words that say what it is doing
 */
export function describeProgress(progress: ChargesProgress | null): string {
  if (!progress || progress.stage === "starting") {
    return "starting…";
  }
  if (progress.stage === "integrals") {
    return "working out the integrals…";
  }
  if (progress.iteration === 0 || progress.error === null || progress.energy === null) {
    return "iterating…";
  }
  return `iteration ${progress.iteration} · error ${progress.error.toExponential(1)} · ${progress.energy.toFixed(4)} Eh`;
}

/**
 * @param convention what the server says the charges mean
 * @returns words the user can be shown
 */
function describeConvention(convention: string | null): string {
  switch (convention) {
    case "MULLIKEN":
      return "Mulliken, from Hartree-Fock";
    case "FORMAL":
      return "Formal charges only";
    case null:
      return "unknown";
    default:
      return convention.toLowerCase();
  }
}
