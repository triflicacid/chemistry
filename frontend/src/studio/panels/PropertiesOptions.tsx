import {type ReactNode, useEffect, useState} from "react";
import type {Models} from "../models/models";
import {ORBITAL_KINDS, type OrbitalKind} from "../models/OrbitalModel";
import {
    CHARGE_MODELS,
    type ChargeModel,
    type ColourBy,
    DEFAULT_POLAR_THRESHOLD,
    DEFAULT_SURFACE_ISOVALUE,
    type DensityColour,
    MAX_FORCE_SCALE,
    MAX_POLAR_THRESHOLD,
    MAX_SURFACE_ISOVALUE,
    MIN_FORCE_SCALE,
    MIN_POLAR_THRESHOLD,
    MIN_SURFACE_ISOVALUE,
    MIN_SURFACE_OPACITY,
    MULTIPLICITIES,
    type PropertyGroup,
    type SurfaceKind,
} from "../models/PropertiesModel";
import {KCAL_PER_HARTREE, largest, toKcalPerAngstrom} from "../layout/forceArrows";
import {ATOM_COLOUR, NET_COLOUR, TORQUE_COLOUR} from "../viewport/scene/ForceArrows";
import {useObservable} from "../models/observable";
import {divergingGradient} from "../../render/scale";
import {chargeScale} from "../layout/chargeScale";
import type {ChargesProgress} from "../session/messages";
import {useSessionContext} from "../session/SessionContext";
import {ForceDebug} from "./ForceDebug";
import {NEGATIVE_LOBE, POSITIVE_LOBE} from "../viewport/scene/OrbitalSurface";
import {decodeEsp, espSurface as makeEspSurface, toKcalPerCharge} from "../layout/esp";

/** What the atoms can be coloured by, with what each means. */
const COLOUR_OPTIONS: { value: ColourBy; label: string; title: string }[] = [
  { value: "element", label: "Element", title: "Each atom has the colour of its element" },
  { value: "charge", label: "Partial charge", title: "Red where an atom has gained electrons and blue where it has lost them" },
];

/** What can be drawn as a surface of the electron density, with what each means. */
const SURFACE_OPTIONS: { value: SurfaceKind; label: string; title: string }[] = [
  { value: "none", label: "None", title: "Draw no surface" },
  { value: "density", label: "Electron density", title: "A surface where the electron density has a chosen value, from Hartree-Fock" },
  { value: "spin", label: "Spin density", title: "Where the unpaired electrons are: blue where spin up is in excess, red where spin down is. A molecule whose electrons all pair up has none" },
  { value: "difference", label: "Difference density", title: "The density of everything together less the densities of its molecules each on its own: blue where electrons gather as the molecules meet, red where they leave. Needs two or more molecules" },
  { value: "esp", label: "Potential on density", title: "The same surface coloured by the electrostatic potential: red where it is negative, which attracts positive charge, and blue where it is positive" },
];

/** What is said while each surface is being worked out. */
const WORKING: Record<SurfaceKind, string> = {
  none: "",
  density: "working out the density…",
  esp: "working out the potential…",
  spin: "working out the spin density…",
  difference: "working out the difference density, which takes a calculation for each molecule…",
};

/** How often the running timer is redrawn. */
const TICK_MS = 100;

/**
 * What is shown on the molecule beyond its shape. Each property is a group of its own: one row with its switch, a line saying how its
 * calculation is getting on and what its colours mean, and its settings, which are folded away until asked for.
 */
export function PropertiesOptions({ models }: { models: Models }) {
  const { properties } = models;
  const { colourBy, chargeModel, polarLabels, polarThreshold, forceArrows, forceScale, netForces, surface, densityColour, surfaceModel, multiplicity, surfaceIsovalue, surfaceOpacity, open, expanded } =
    useObservable(properties);
  const debug = useObservable(models.debug);
  const fold = (group: PropertyGroup) => ({ open: expanded[group], onToggle: () => properties.expandTo(group, !expanded[group]) });

  return (
    <section className="render-options" aria-label="Properties">
      <button type="button" className="render-header" aria-expanded={open} onClick={() => properties.openTo(!open)}>
        <span className="twisty">{open ? "▾" : "▸"}</span>
        Properties
      </button>
      {open && (
        <div className="properties-groups">
          <Group
            title="Atoms"
            {...fold("atoms")}
            control={
              <select value={colourBy} aria-label="What the atoms are coloured by" onChange={(event) => properties.colourWith(event.target.value as ColourBy)}>
                {COLOUR_OPTIONS.map(({ value, label, title }) => (
                  <option key={value} value={value} title={title}>
                    {label}
                  </option>
                ))}
              </select>
            }
            summary={colourBy === "charge" ? <ChargeSummary /> : null}
            details={
              colourBy === "charge" ? (
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
                  <ChargeDetails />
                </>
              ) : (
                <dd className="note-row panel-note">Colour by partial charge to see its settings.</dd>
              )
            }
          />
          <Group
            title="Forces"
            {...fold("forces")}
            control={
              <label className="legend-check" title="Draw an arrow on each atom for the force the other molecules in the session put on it. A single molecule has none">
                <input type="checkbox" checked={forceArrows} onChange={(event) => properties.forcesTo(event.target.checked)} />
                Show arrows
              </label>
            }
            summary={forceArrows ? <ForceSummary /> : null}
            details={
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
                {forceArrows && <ForceDetails net={netForces} debug={debug} scale={forceScale} models={models} />}
              </>
            }
          />
          <Group
            title="Surface"
            {...fold("surface")}
            control={
              <select value={surface} aria-label="What is drawn as a surface of the electron density" onChange={(event) => properties.surfaceTo(event.target.value as SurfaceKind)}>
                {SURFACE_OPTIONS.map(({ value, label, title }) => (
                  <option key={value} value={value} title={title}>
                    {label}
                  </option>
                ))}
              </select>
            }
            summary={surface !== "none" ? <SurfaceSummary kind={surface} isovalue={surfaceIsovalue} /> : null}
            details={
              <>
                <dt>Calculation</dt>
                <dd>
                  <select value={surfaceModel} aria-label="The kind of Hartree-Fock calculation the surfaces come from" onChange={(event) => properties.surfaceModelTo(event.target.value as OrbitalKind)}>
                    {ORBITAL_KINDS.map(({ value, label, title }) => (
                      <option key={value} value={value} title={title}>
                        {label}
                      </option>
                    ))}
                  </select>
                </dd>
                <dt>Spin</dt>
                <dd>
                  <select
                    value={multiplicity === null ? "" : String(multiplicity)}
                    aria-label="The spin multiplicity the surfaces are calculated at"
                    onChange={(event) => properties.multiplicityTo(event.target.value === "" ? null : Number(event.target.value))}
                  >
                    {MULTIPLICITIES.map(({ value, label, title }) => (
                      <option key={String(value)} value={value === null ? "" : String(value)} title={title}>
                        {label}
                      </option>
                    ))}
                  </select>
                </dd>
                {surface === "density" && (
                  <>
                    <dt>Colour</dt>
                    <dd>
                      <select value={densityColour} aria-label="What colours the density surface" onChange={(event) => properties.densityColourTo(event.target.value as DensityColour)}>
                        <option value="single" title="The whole surface in one colour">
                          One colour
                        </option>
                        <option value="element" title="Each part of the surface in the colour of the atom it is nearest">
                          Nearest atom
                        </option>
                      </select>
                    </dd>
                  </>
                )}
                <dt>{surface === "spin" || surface === "difference" ? "Level" : "Density"}</dt>
                <dd className="position">
                  <input
                    type="range"
                    className="render-slider"
                    min={Math.round(MIN_SURFACE_ISOVALUE * 1000)}
                    max={Math.round(MAX_SURFACE_ISOVALUE * 1000)}
                    step={1}
                    value={Math.round(surfaceIsovalue * 1000)}
                    aria-label="The electron density at the surface, in electrons per cubic bohr"
                    title="The electron density the surface is at: lower is a larger, looser surface. Double-click to put it back"
                    onChange={(event) => properties.surfaceIsovalueTo(Number(event.target.value) / 1000)}
                    onDoubleClick={() => properties.surfaceIsovalueTo(DEFAULT_SURFACE_ISOVALUE)}
                  />
                  <span className="unit render-value">{surfaceIsovalue.toFixed(3)}</span>
                </dd>
                <dt>Opacity</dt>
                <dd className="position">
                  <input
                    type="range"
                    className="render-slider"
                    min={Math.round(MIN_SURFACE_OPACITY * 100)}
                    max={100}
                    step={5}
                    value={Math.round(surfaceOpacity * 100)}
                    aria-label="How opaque the surface is"
                    onChange={(event) => properties.surfaceOpacityTo(Number(event.target.value) / 100)}
                  />
                  <span className="unit render-value">{Math.round(surfaceOpacity * 100)}%</span>
                </dd>
              </>
            }
          />
        </div>
      )}
    </section>
  );
}

/**
 * One property: a row with its switch, a line under it for how its calculation is getting on and what its colours mean, and its
 * settings behind a twisty.
 */
function Group({ title, open, onToggle, control, summary, details }: { title: string; open: boolean; onToggle: () => void; control: ReactNode; summary: ReactNode; details: ReactNode }) {
  return (
    <div className="prop-group">
      <div className="prop-row">
        <button type="button" className="prop-twisty" aria-expanded={open} aria-label={`${title} settings`} title={`${open ? "Hide" : "Show"} the settings`} onClick={onToggle}>
          {open ? "▾" : "▸"}
        </button>
        <span className="prop-title">{title}</span>
        <div className="prop-control">{control}</div>
      </div>
      {summary && (
        <div className="prop-summary legend-note" role="status">
          {summary}
        </div>
      )}
      {open && <dl className="properties prop-details">{details}</dl>}
    </div>
  );
}

/** The colour scale of a property, with its two ends and the middle written under it. */
function Scale({ limit, unit, label }: { limit: string; unit: string; label: string }) {
  return (
    <div className="legend">
      <div className="legend-bar" style={{ background: divergingGradient() }} role="img" aria-label={label} />
      <div className="legend-labels">
        <span>−{limit}</span>
        <span>{unit}</span>
        <span>+{limit}</span>
      </div>
    </div>
  );
}

/** How the calculation of the charges is getting on, and what their colours mean. */
function ChargeSummary() {
  const { charges, calculatingCharges, chargesProgress } = useSessionContext();
  const scale = chargeScale(charges);

  return (
    <>
      {calculatingCharges && <Running startedAt={calculatingCharges.startedAt} detail={describeProgress(chargesProgress)} />}
      {scale && <Scale limit={scale.limit.toFixed(2)} unit="e" label={`From minus ${scale.limit.toFixed(2)} to plus ${scale.limit.toFixed(2)} elementary charges`} />}
    </>
  );
}

/** What the charges are and whether the colours can be trusted yet. */
function ChargeDetails() {
  const { charges, calculatingCharges, state } = useSessionContext();
  const current = charges !== null && state !== null && charges.session_id === state.session_id && charges.version === state.version;
  if (!charges || !chargeScale(charges)) {
    return null;
  }

  return (
    <>
      <dt>Charges</dt>
      <dd className="legend-note">
        {describeConvention(charges.convention)}
        {current && !calculatingCharges ? <Took milliseconds={charges.elapsed_ms} /> : " (updating…)"}
      </dd>
    </>
  );
}

/** How the calculation behind a surface is getting on, and for the potential what the colours mean. */
function SurfaceSummary({ kind, isovalue }: { kind: SurfaceKind; isovalue: number }) {
  const { esp, espSince, density, densitySince, spin, spinSince, difference, differenceSince, state } = useSessionContext();
  const result = { esp, density, spin, difference, none: null }[kind];
  const since = { esp: espSince, density: densitySince, spin: spinSince, difference: differenceSince, none: null }[kind];
  const current = result !== null && state !== null && result.session_id === state.session_id && result.version === state.version;
  const decoded = kind === "esp" && current && esp ? decodeEsp(esp) : null;
  const limit = decoded ? makeEspSurface(decoded, isovalue).limit : null;

  return (
    <>
      {since !== null && <Running startedAt={since} detail={WORKING[kind]} />}
      {since === null && kind === "density" && density?.status === "complete" && (
        <>
          electrons per cubic bohr
          <Took milliseconds={density.elapsed_ms} />
          {current ? "" : " (updating…)"}
        </>
      )}
      {since === null && (kind === "spin" || kind === "difference") && result?.status === "complete" && (
        <>
          <span className="legend-key">
            <span style={{ color: POSITIVE_LOBE }}>●</span> {kind === "spin" ? "spin up" : "gained"}
          </span>
          <span className="legend-key">
            <span style={{ color: NEGATIVE_LOBE }}>●</span> {kind === "spin" ? "spin down" : "lost"}
          </span>
          <Took milliseconds={result.elapsed_ms} />
          {current ? "" : " (updating…)"}
        </>
      )}
      {limit !== null && (
        <Scale
          limit={toKcalPerCharge(limit).toFixed(0)}
          unit="kcal/mol/e"
          label={`From minus ${toKcalPerCharge(limit).toFixed(0)} to plus ${toKcalPerCharge(limit).toFixed(0)} kilocalories per mole per charge`}
        />
      )}
    </>
  );
}

/** How the calculation of the forces is getting on, and the strongest of them. */
function ForceSummary() {
  const { forces, calculatingForcesSince, state } = useSessionContext();
  const current = forces !== null && state !== null && forces.session_id === state.session_id && forces.version === state.version;

  return (
    <>
      {calculatingForcesSince !== null && <Running startedAt={calculatingForcesSince} detail="working out the forces…" />}
      {calculatingForcesSince === null && forces?.status === "complete" && forces.energy !== null && (
        <>
          strongest {toKcalPerAngstrom(largest(forces).atom).toFixed(2)} kcal/mol/Å
          {current ? <Took milliseconds={forces.elapsed_ms} /> : " (updating…)"}
        </>
      )}
    </>
  );
}

/** The energy between the molecules, what the arrows mean, and with Debug on every force. */
function ForceDetails({ net, debug, scale, models }: { net: boolean; debug: boolean; scale: number; models: Models }) {
  const { forces } = useSessionContext();
  const key = (colour: string, what: string) => (
    <span className="legend-key">
      <span style={{ color: colour }}>●</span> {what}
    </span>
  );
  if (forces?.status !== "complete" || forces.energy === null) {
    return null;
  }

  return (
    <>
      <dt>Energy</dt>
      <dd className="legend-note">{(forces.energy * KCAL_PER_HARTREE).toFixed(3)} kcal/mol between the molecules</dd>
      <dt>Key</dt>
      <dd className="legend-note">
        {key(ATOM_COLOUR, "atom")} {net && key(NET_COLOUR, "total force")} {net && key(TORQUE_COLOUR, "torque axis")}
      </dd>
      {debug && <ForceDebug forces={forces} scale={scale} label={(id) => models.index?.label(id) ?? id} />}
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
