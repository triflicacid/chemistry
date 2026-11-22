import {useEffect, useState} from "react";
import type {Models} from "../models/models";
import {
  CHARGE_MODELS,
  type ChargeModel,
  type ColourBy,
  DEFAULT_POLAR_THRESHOLD,
  MAX_FORCE_SCALE,
  MAX_POLAR_THRESHOLD,
  MIN_FORCE_SCALE,
  MIN_POLAR_THRESHOLD,
} from "../models/PropertiesModel";
import {KCAL_PER_HARTREE, largest, toKcalPerAngstrom} from "../layout/forceArrows";
import {ATOM_COLOUR, NET_COLOUR, TORQUE_COLOUR} from "../viewport/scene/ForceArrows";
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
  const { colourBy, chargeModel, polarLabels, polarThreshold, forceArrows, forceScale, netForces, open } = useObservable(properties);

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
          <dt>Forces</dt>
          <dd>
            <label className="legend-check" title="Draw an arrow on each atom for the force the other molecules in the session put on it. A single molecule has none">
              <input type="checkbox" checked={forceArrows} onChange={(event) => properties.forcesTo(event.target.checked)} />
              Show force arrows
            </label>
          </dd>
          {forceArrows && (
            <>
              <dt>Length</dt>
              <dd className="position">
                <input
                  type="range"
                  className="render-slider"
                  min={Math.round(MIN_FORCE_SCALE * 100)}
                  max={Math.round(MAX_FORCE_SCALE * 100)}
                  step={5}
                  value={Math.round(forceScale * 100)}
                  aria-label="How long the force arrows are drawn, as a multiple of the usual length"
                  title="How long the arrows are drawn. They are drawn in proportion to the largest force, so this is relative. Double-click to put it back"
                  onChange={(event) => properties.forceScaleTo(Number(event.target.value) / 100)}
                  onDoubleClick={() => properties.forceScaleTo(1)}
                />
                <span className="unit render-value">×{forceScale.toFixed(2)}</span>
              </dd>
              <dt />
              <dd>
                <label className="legend-check" title="Also draw the total force on each molecule, and the torque that turns it">
                  <input type="checkbox" checked={netForces} onChange={(event) => properties.netForcesTo(event.target.checked)} />
                  Total force and torque
                </label>
              </dd>
              <ForceStatus net={netForces} />
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
            <Running startedAt={calculatingCharges.startedAt} detail={describeProgress(chargesProgress)} />
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

/** How the calculation of the forces is getting on, what the arrows mean, and why there are none if there are none. */
function ForceStatus({ net }: { net: boolean }) {
  const { forces, calculatingForcesSince, state } = useSessionContext();
  const current = forces !== null && state !== null && forces.session_id === state.session_id && forces.version === state.version;
  const key = (colour: string, what: string) => (
    <span className="legend-key">
      <span style={{ color: colour }}>●</span> {what}
    </span>
  );

  return (
    <>
      {calculatingForcesSince !== null && (
        <>
          <dt>Status</dt>
          <dd className="legend-note" role="status">
            <Running startedAt={calculatingForcesSince} detail="working out the forces…" />
          </dd>
        </>
      )}
      {calculatingForcesSince === null && forces && forces.status !== "complete" && (
        <>
          <dt>Forces</dt>
          <dd className="legend-note">{forces.message ?? "They could not be calculated."}</dd>
        </>
      )}
      {forces?.status === "complete" && forces.energy !== null && (
        <>
          <dt>Strongest</dt>
          <dd className="legend-note">
            {toKcalPerAngstrom(largest(forces).atom).toFixed(2)} kcal/mol/Å
            {current && calculatingForcesSince === null ? <Took milliseconds={forces.elapsed_ms} /> : " (updating…)"}
          </dd>
          <dt>Energy</dt>
          <dd className="legend-note">{(forces.energy * KCAL_PER_HARTREE).toFixed(3)} kcal/mol between the molecules</dd>
          <dt>Key</dt>
          <dd className="legend-note">
            {key(ATOM_COLOUR, "atom")} {net && key(NET_COLOUR, "total force")} {net && key(TORQUE_COLOUR, "torque axis")}
          </dd>
        </>
      )}
    </>
  );
}

/** How long a calculation has been running, counting up, with what the server last said it is doing. */
function Running({ startedAt, detail }: { startedAt: number; detail: string }) {
  const [now, setNow] = useState(() => Date.now());

  // the timer counts on its own, so that it keeps moving through the stretches where the server has nothing to say
  useEffect(() => {
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(timer);
  }, [startedAt]);

  return (
    <>
      <span className="mono">{seconds(Math.max(0, now - startedAt))}</span> {detail}
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
