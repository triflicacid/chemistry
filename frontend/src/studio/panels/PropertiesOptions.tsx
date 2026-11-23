import {type ReactNode, useEffect, useState} from "react";
import type {Models} from "../models/models";
import {ORBITAL_KINDS, type OrbitalKind} from "../models/OrbitalModel";
import {
    CHARGE_MODELS,
    type ChargeModel,
    type ColourBy,
    DEFAULT_ELF_LEVEL,
    DEFAULT_NCI_LEVEL,
    DEFAULT_POLAR_THRESHOLD,
    DEFAULT_SURFACE_ISOVALUE,
    type DeformationDisplay,
    type DensityColour,
    MAX_ELF_LEVEL,
    MAX_FORCE_SCALE,
    MAX_NCI_LEVEL,
    MAX_POLAR_THRESHOLD,
    maxSurfaceIsovalue,
    MIN_ELF_LEVEL,
    MIN_FORCE_SCALE,
    MIN_NCI_LEVEL,
    MIN_POLAR_THRESHOLD,
    MIN_SURFACE_ISOVALUE,
    MIN_SURFACE_OPACITY,
    MULTIPLICITIES,
    type PropertyGroup,
    type SliceField,
    type SurfaceKind,
} from "../models/PropertiesModel";
import {KCAL_PER_HARTREE, largest, toKcalPerAngstrom} from "../layout/forceArrows";
import {ATTRACTIVE, REPULSIVE, WEAK} from "../layout/nci";
import {fieldOnSurface} from "../layout/surfaceMap";
import {dualGradient, isFrontierMap, reactivityGradient} from "../layout/reactivity";
import {ATOM_COLOUR, NET_COLOUR, TORQUE_COLOUR} from "../viewport/scene/ForceArrows";
import {useObservable} from "../models/observable";
import {divergingGradient} from "../../render/scale";
import {chargeScale} from "../layout/chargeScale";
import type {ChargesProgress, OrbitalTarget} from "../session/messages";
import {useSessionContext} from "../session/SessionContext";
import {ForceDebug} from "./ForceDebug";
import {NEGATIVE_LOBE, POSITIVE_LOBE} from "../layout/orbitalColours";
import {isOrbitalSlice, nameOf, sliceTargets} from "../layout/orbitals";
import {autoLimit, decodeEsp, espSurface as makeEspSurface, toKcalPerCharge} from "../layout/esp";
import {decodeScalarField} from "../layout/gridField";
import {decodePlane, sequentialGradient, type SliceAxis, sliceLimit} from "../layout/slice";

/** What the atoms can be coloured by, with what each means. */
const COLOUR_OPTIONS: { value: ColourBy; label: string; title: string }[] = [
  { value: "element", label: "Element", title: "Each atom has the colour of its element" },
  { value: "charge", label: "Partial charge", title: "Red where an atom has gained electrons and blue where it has lost them" },
];

/** What can be drawn as a surface of the electron density, with what each means. */
const SURFACE_OPTIONS: { value: SurfaceKind; label: string; title: string }[] = [
  { value: "none", label: "None", title: "Draw no surface" },
  { value: "density", label: "Electron density (ρ)", title: "A surface of constant electron density" },
  { value: "spin", label: "Spin density (ρα − ρβ)", title: "Where unpaired electrons are: blue for spin up, red for spin down" },
  { value: "intermolecular", label: "Density change on interaction (Δρ)", title: "Electrons gained (blue) and lost (red) when the molecules interact" },
  { value: "deformation", label: "Density change on bonding (Δρ)", title: "Electrons gained (blue) and lost (red) when the atoms bond into the molecule" },
  { value: "nci", label: "Non-covalent contacts (RDG)", title: "Where unbonded atoms touch: blue for hydrogen-bond-like, green for van der Waals, red for clashes" },
  { value: "homoMap", label: "HOMO on density (electrophilic sites)", title: "The density surface coloured by how much of the HOMO is there" },
  { value: "lumoMap", label: "LUMO on density (nucleophilic sites)", title: "The density surface coloured by how much of the LUMO is there" },
  { value: "frontierMap", label: "HOMO & LUMO on density", title: "The density surface coloured orange where the HOMO is stronger and blue where the LUMO is" },
  { value: "concentrationMap", label: "Charge concentration on density (−∇²ρ)", title: "The density surface coloured by charge concentration: blue where concentrated (lone pairs, bonds), red where depleted" },
  { value: "elf", label: "Electron localisation (ELF)", title: "A surface of constant electron localisation: bonds, lone pairs and cores show as separate lobes" },
  { value: "esp", label: "Electrostatic potential on density (ESP)", title: "The density surface coloured by electrostatic potential: red where negative, blue where positive" },
];

/** What a slice plane can show, with what each means. */
const SLICE_OPTIONS: { value: SliceField; label: string; title: string }[] = [
  { value: "none", label: "None", title: "Draw no plane" },
  { value: "density", label: "Electron density (ρ)", title: "The electron density across a plane" },
  { value: "spin", label: "Spin density (ρα − ρβ)", title: "The spin density across a plane: blue for spin up, red for spin down" },
  { value: "intermolecular", label: "Density change on interaction (Δρ)", title: "Electrons gained (blue) and lost (red) when the molecules interact, across a plane" },
  { value: "deformation", label: "Density change on bonding (Δρ)", title: "Electrons gained (blue) and lost (red) when the atoms bond into the molecule, across a plane" },
  { value: "laplacian", label: "Charge concentration (−∇²ρ)", title: "Charge concentration across a plane: blue where concentrated, red where depleted" },
  { value: "homoMap", label: "HOMO probability (|ψ|²)", title: "The probability of the HOMO across a plane, with any orbitals of equal energy" },
  { value: "lumoMap", label: "LUMO probability (|ψ|²)", title: "The probability of the LUMO across a plane, with any orbitals of equal energy" },
  { value: "homo", label: "HOMO orbitals", title: "The HOMO and the orbitals below it, across a plane" },
  { value: "lumo", label: "LUMO orbitals", title: "The LUMO and the orbitals above it, across a plane" },
  { value: "frontier", label: "Frontier orbitals", title: "The HOMO and LUMO sides together, across a plane" },
  { value: "potential", label: "Electrostatic potential (ESP)", title: "The electrostatic potential across a plane: red where negative, blue where positive" },
];

/** What is said while each surface is being worked out. */
const WORKING: Record<SurfaceKind, string> = {
  none: "",
  density: "working out the density…",
  esp: "working out the potential…",
  spin: "working out the spin density…",
  intermolecular: "working out the interaction density, which takes a calculation for each molecule…",
  deformation: "working out the density and the density of each atom on its own…",
  nci: "working out the non-covalent contacts…",
  homoMap: "working out the density…",
  lumoMap: "working out the density…",
  frontierMap: "working out the density…",
  concentrationMap: "working out the density and its Laplacian…",
  elf: "working out the electron localisation…",
};

/** How often the running timer is redrawn. */
const TICK_MS = 100;

/**
 * What is shown on the molecule beyond its shape. Each property is a group of its own: one row with its switch, a line saying how its
 * calculation is getting on and what its colours mean, and its settings, which are folded away until asked for.
 */
export function PropertiesOptions({ models }: { models: Models }) {
  const { properties } = models;
  const { colourBy, chargeModel, polarLabels, polarThreshold, forceArrows, forceScale, netForces, surface, densityColour, deformationDisplay, surfaceModel, multiplicity, sliceField, sliceAxis, slicePosition, sliceContours, surfaceIsovalue, nciLevel, elfLevel, surfaceOpacity, open, expanded } =
    useObservable(properties);
  const debug = useObservable(models.debug);
  // these surfaces are drawn where the field is a distance above and below zero, not at one value of a density
  const signedLevel = surface === "spin" || surface === "intermolecular" || (surface === "deformation" && deformationDisplay === "levels");
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
            summary={surface !== "none" ? <SurfaceSummary kind={surface} isovalue={surfaceIsovalue} deformationDisplay={deformationDisplay} /> : null}
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
                {(surface === "density" || surface === "elf") && (
                  <>
                    <dt>Colour</dt>
                    <dd>
                      <select value={densityColour} aria-label="What colours the surface" onChange={(event) => properties.densityColourTo(event.target.value as DensityColour)}>
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
                {surface === "deformation" && (
                  <>
                    <dt>Show</dt>
                    <dd>
                      <select value={deformationDisplay} aria-label="How the density change on bonding is shown" onChange={(event) => properties.deformationDisplayTo(event.target.value as DeformationDisplay)}>
                        <option value="levels" title="Surfaces where electrons are gained (blue) and lost (red)">
                          Gained and lost
                        </option>
                        <option value="range" title="The density surface coloured by how much is gained or lost at each point">
                          Range on density
                        </option>
                      </select>
                    </dd>
                  </>
                )}
                {surface === "nci" ? (
                  <>
                    <dt>Gradient</dt>
                    <dd className="position">
                      <input
                        type="range"
                        className="render-slider"
                        min={Math.round(MIN_NCI_LEVEL * 100)}
                        max={Math.round(MAX_NCI_LEVEL * 100)}
                        step={5}
                        value={Math.round(nciLevel * 100)}
                        aria-label="The reduced density gradient the contact surfaces are drawn at"
                        title="The reduced density gradient the surface is drawn at. Double-click to put it back"
                        onChange={(event) => properties.nciLevelTo(Number(event.target.value) / 100)}
                        onDoubleClick={() => properties.nciLevelTo(DEFAULT_NCI_LEVEL)}
                      />
                      <span className="unit render-value">{nciLevel.toFixed(2)}</span>
                    </dd>
                  </>
                ) : surface === "elf" ? (
                  <>
                    <dt>ELF value</dt>
                    <dd className="position">
                      <input
                        type="range"
                        className="render-slider"
                        min={Math.round(MIN_ELF_LEVEL * 100)}
                        max={Math.round(MAX_ELF_LEVEL * 100)}
                        step={1}
                        value={Math.round(elfLevel * 100)}
                        aria-label="The value of the electron localisation function the surface is drawn at"
                        title="The surface encloses where the localisation is above this value, which runs from 0 to 1 and has no unit. Double-click to put it back"
                        onChange={(event) => properties.elfLevelTo(Number(event.target.value) / 100)}
                        onDoubleClick={() => properties.elfLevelTo(DEFAULT_ELF_LEVEL)}
                      />
                      <span className="unit render-value">{elfLevel.toFixed(2)} of 1</span>
                    </dd>
                  </>
                ) : (
                  <>
                    <dt>{signedLevel ? "Density ±" : "Density"}</dt>
                    <dd className="position">
                      <input
                        type="range"
                        className="render-slider"
                        min={Math.round(MIN_SURFACE_ISOVALUE * 1000)}
                        max={Math.round(maxSurfaceIsovalue(surface, deformationDisplay) * 1000)}
                        step={1}
                        value={Math.round(surfaceIsovalue * 1000)}
                        aria-label={signedLevel ? "How far from zero the gained and lost surfaces are, in electrons per cubic bohr" : "The electron density at the surface, in electrons per cubic bohr"}
                        title={signedLevel ? "How far from zero the gained and lost surfaces are drawn, in electrons per cubic bohr. Double-click to put it back" : "The electron density the surface is drawn at, in electrons per cubic bohr. Double-click to put it back"}
                        onChange={(event) => properties.surfaceIsovalueTo(Number(event.target.value) / 1000)}
                        onDoubleClick={() => properties.surfaceIsovalueTo(DEFAULT_SURFACE_ISOVALUE)}
                      />
                      <span className="unit render-value">{surfaceIsovalue.toFixed(3)} e/bohr³</span>
                    </dd>
                  </>
                )}
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
          <Group
            title="Slice"
            {...fold("slice")}
            control={
              <select value={sliceField} aria-label="What the slice plane shows" onChange={(event) => properties.sliceFieldTo(event.target.value as SliceField)}>
                {SLICE_OPTIONS.map(({ value, label, title }) => (
                  <option key={value} value={value} title={title}>
                    {label}
                  </option>
                ))}
              </select>
            }
            summary={sliceField !== "none" ? <SliceSummary field={sliceField} models={models} /> : null}
            details={
              <>
                {isOrbitalSlice(sliceField) && <SliceOrbital models={models} field={sliceField} />}
                <dt>Axis</dt>
                <dd>
                  <select value={sliceAxis} aria-label="The axis the plane is at right angles to" onChange={(event) => properties.sliceAxisTo(event.target.value as SliceAxis)}>
                    <option value="x">x</option>
                    <option value="y">y</option>
                    <option value="z">z</option>
                  </select>
                </dd>
                <dt>Position</dt>
                <dd className="position">
                  <input
                    type="range"
                    className="render-slider"
                    min={0}
                    max={100}
                    step={1}
                    value={Math.round(slicePosition * 100)}
                    aria-label="How far along the field the plane is"
                    title="How far along the field the plane is. Double-click to put it back in the middle"
                    onChange={(event) => properties.slicePositionTo(Number(event.target.value) / 100)}
                    onDoubleClick={() => properties.slicePositionTo(0.5)}
                  />
                  <span className="unit render-value">{Math.round(slicePosition * 100)}%</span>
                </dd>
                <dt />
                <dd>
                  <label className="legend-check" title="Draw lines of constant value across the plane">
                    <input type="checkbox" checked={sliceContours} onChange={(event) => properties.sliceContoursTo(event.target.checked)} />
                    Contour lines
                  </label>
                </dd>
              </>
            }
          />
        </div>
      )}
    </section>
  );
}

/** How the calculation behind a slice is getting on, and what its colours mean. */
function SliceSummary({ field, models }: { field: SliceField; models: Models }) {
  const { density, densitySince, spin, spinSince, intermolecular, intermolecularSince, deformation, deformationSince, laplacian, laplacianSince, plane, planeSince, state, orbitalsSince, fieldsAsked } = useSessionContext();
  const { colouring, display, combine } = useObservable(models.orbital);
  const orbitalSlice = isOrbitalSlice(field);
  const frontierSlice = isFrontierMap(field);
  const result = orbitalSlice || frontierSlice ? null : { density, spin, intermolecular, deformation, laplacian, potential: plane, none: null }[field];
  const since = orbitalSlice || frontierSlice ? (orbitalsSince !== null || fieldsAsked ? 0 : null) : { density: densitySince, spin: spinSince, intermolecular: intermolecularSince, deformation: deformationSince, laplacian: laplacianSince, potential: planeSince, none: null }[field];
  const current = result !== null && state !== null && result.session_id === state.session_id && result.version === state.version;
  const decoded = current && result && result.type !== "plane.result" ? decodeScalarField(result) : null;
  const flat = current && plane && field === "potential" ? decodePlane(plane) : null;
  const signed = field === "spin" || field === "intermolecular" || field === "deformation" || field === "laplacian";
  const unit = field === "laplacian" ? "e/bohr⁵" : "e/bohr³";
  const limit = decoded ? sliceLimit(decoded, signed) : null;
  const shown = limit === null ? "" : limit.toFixed(3);
  const potentialLimit = flat ? toKcalPerCharge(autoLimit(flat.values)).toFixed(0) : null;
  // orbitals are coloured as the Orbitals tab colours them, so its key says what the colours are; only the sign has a scale of its own here
  const bySign = orbitalSlice && colouring === "phase" && display === "amplitude" && combine !== "sum";

  return (
    <>
      {since !== null && (orbitalSlice || frontierSlice) && <>working out the orbitals…</>}
      {since !== null && !orbitalSlice && !frontierSlice && <Running startedAt={since} detail={field === "potential" ? "working out the potential on the plane…" : field === "laplacian" ? "working out the Laplacian…" : WORKING[field as SurfaceKind]} />}
      {frontierSlice && (
        <div className="legend">
          <div className="legend-bar" style={{ background: reactivityGradient(field === "homoMap" ? "homo" : "lumo") }} role="img" aria-label={`From where the ${field === "homoMap" ? "HOMO" : "LUMO"} is not to where it is most`} />
          <div className="legend-labels">
            <span>none</span>
            <span>{field === "homoMap" ? "HOMO" : "LUMO"}</span>
            <span>most</span>
          </div>
        </div>
      )}
      {bySign && <Scale limit="2" unit="× surface level" label="From minus twice to plus twice the level the surface is drawn at" />}
      {potentialLimit !== null && <Scale limit={potentialLimit} unit="kcal/mol/e" label={`From minus ${potentialLimit} to plus ${potentialLimit} kilocalories per mole per charge`} />}
      {limit !== null && signed && <Scale limit={shown} unit={unit} label={`From minus ${shown} to plus ${shown} ${field === "laplacian" ? "electrons per fifth power of a bohr" : "electrons per cubic bohr"}`} />}
      {limit !== null && !signed && (
        <div className="legend">
          <div className="legend-bar" style={{ background: sequentialGradient() }} role="img" aria-label={`From zero to ${shown} electrons per cubic bohr`} />
          <div className="legend-labels">
            <span>0</span>
            <span>{shown} e/bohr³</span>
          </div>
        </div>
      )}
    </>
  );
}

/**
 * Which of the orbitals a slice plane takes where it takes one at a time, or, for a sum, which side of the gap, since the filled and the empty
 * are not summed together.
 */
function SliceOrbital({ models, field }: { models: Models; field: SliceField & ("homo" | "lumo" | "frontier") }) {
  const { orbitals, state } = useSessionContext();
  const { count, combine, sliceOrbital } = useObservable(models.orbital);
  const targets = orbitals?.status === "complete" ? sliceTargets(orbitals, field, count) : [];
  const spins = new Set(orbitals?.channels.map((channel) => channel.spin));
  const homoOf = (target: OrbitalTarget) => orbitals?.channels.find((channel) => channel.fragment === target.fragment && channel.spin === target.spin)?.homo ?? -1;
  const bothSides = targets.some((target) => target.orbital <= homoOf(target)) && targets.some((target) => target.orbital > homoOf(target));
  const label = (target: OrbitalTarget) => {
    const molecule = (state?.fragments.length ?? 0) > 1 ? `${(state?.fragments.findIndex((fragment) => target.fragment !== null && fragment.atoms.includes(target.fragment)) ?? 0) + 1}. ` : "";
    return `${molecule}${spins.size > 1 ? (target.spin === "alpha" ? "↑ " : "↓ ") : ""}${nameOf(target.orbital, homoOf(target))}`;
  };
  // one at a time there is one to pick; merged there is nothing to pick unless both sides of the gap are in it, and a union takes everything
  if (targets.length < 2 || combine === "union" || (combine !== "separate" && !bothSides)) {
    return null;
  }

  return (
    <>
      <dt>{combine === "separate" ? "Orbital" : "Side"}</dt>
      <dd>
        <select
          value={Math.min(sliceOrbital, targets.length - 1)}
          aria-label={combine === "separate" ? "Which orbital the plane takes" : "Which side of the gap the plane sums"}
          title={combine === "separate" ? "Which of the orbitals the plane takes, one at a time. Merge them with Combine in the Orbitals tab" : "Which side of the gap is summed: the filled and the empty orbitals are not summed together"}
          onChange={(event) => models.orbital.sliceOrbitalTo(Number(event.target.value))}
        >
          {targets.map((target, at) => (
            <option key={`${target.fragment}${target.spin}${target.orbital}`} value={at}>
              {label(target)}
            </option>
          ))}
        </select>
      </dd>
    </>
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
function SurfaceSummary({ kind, isovalue, deformationDisplay }: { kind: SurfaceKind; isovalue: number; deformationDisplay: DeformationDisplay }) {
  const { esp, espSince, nci, nciSince, density, densitySince, spin, spinSince, intermolecular, intermolecularSince, deformation, deformationSince, laplacian, laplacianSince, elf, elfSince, state } = useSessionContext();
  const result = { esp, nci, density, homoMap: density, lumoMap: density, frontierMap: density, concentrationMap: density, elf, spin, intermolecular, deformation, none: null }[kind];
  const since = { esp: espSince, nci: nciSince, density: densitySince, homoMap: densitySince, lumoMap: densitySince, frontierMap: densitySince, concentrationMap: densitySince ?? laplacianSince, elf: elfSince, spin: spinSince, intermolecular: intermolecularSince, deformation: deformationDisplay === "range" ? (deformationSince ?? densitySince) : deformationSince, none: null }[kind];
  const current = result !== null && state !== null && result.session_id === state.session_id && result.version === state.version;
  const decoded = kind === "esp" && current && esp ? decodeEsp(esp) : null;
  const limit = decoded ? makeEspSurface(decoded, isovalue).limit : null;
  const laplacianCurrent = laplacian !== null && state !== null && laplacian.session_id === state.session_id && laplacian.version === state.version;
  const deformationCurrent = deformation !== null && state !== null && deformation.session_id === state.session_id && deformation.version === state.version;
  const densityCurrent = density !== null && state !== null && density.session_id === state.session_id && density.version === state.version;
  const mapped = kind === "concentrationMap" || (kind === "deformation" && deformationDisplay === "range");
  const flatDensity = mapped && densityCurrent && density ? decodeScalarField(density) : null;
  const flatField = kind === "concentrationMap" && laplacianCurrent && laplacian ? decodeScalarField(laplacian) : kind === "deformation" && mapped && deformationCurrent && deformation ? decodeScalarField(deformation) : null;
  const concentrated = flatDensity && flatField ? fieldOnSurface(flatDensity, isovalue, flatField, kind === "deformation" ? "zero" : "median") : null;

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
      {since === null && (kind === "spin" || kind === "intermolecular" || (kind === "deformation" && deformationDisplay === "levels")) && result?.status === "complete" && (
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
      {since === null && (kind === "homoMap" || kind === "lumoMap") && density?.status === "complete" && (
        <>
          <div className="legend">
            <div className="legend-bar" style={{ background: reactivityGradient(kind === "homoMap" ? "homo" : "lumo") }} role="img" aria-label={`From where the ${kind === "homoMap" ? "HOMO" : "LUMO"} is not to where it is most`} />
            <div className="legend-labels">
              <span>none</span>
              <span>{kind === "homoMap" ? "HOMO" : "LUMO"}</span>
              <span>most</span>
            </div>
          </div>
          <Took milliseconds={density.elapsed_ms} />
          {current ? "" : " (updating…)"}
        </>
      )}
      {since === null && kind === "elf" && elf?.status === "complete" && (
        <>
          electron localisation
          <Took milliseconds={elf.elapsed_ms} />
          {current ? "" : " (updating…)"}
        </>
      )}
      {since === null && concentrated !== null && (
        <Scale
          limit={concentrated.limit.toFixed(3)}
          unit={kind === "deformation" ? "Δρ e/bohr³" : `from ${concentrated.centre.toFixed(3)} e/bohr⁵`}
          label={
            kind === "deformation"
              ? `From minus ${concentrated.limit.toFixed(3)} to plus ${concentrated.limit.toFixed(3)} electrons per cubic bohr`
              : `Minus the Laplacian from ${concentrated.limit.toFixed(3)} below to ${concentrated.limit.toFixed(3)} above its middle value of ${concentrated.centre.toFixed(3)} electrons per fifth power of a bohr`
          }
        />
      )}
      {since === null && kind === "frontierMap" && density?.status === "complete" && (
        <>
          <div className="legend">
            <div className="legend-bar" style={{ background: dualGradient() }} role="img" aria-label="From where the HOMO is more, through where both or neither is, to where the LUMO is more" />
            <div className="legend-labels">
              <span>HOMO</span>
              <span>both or neither</span>
              <span>LUMO</span>
            </div>
          </div>
          <Took milliseconds={density.elapsed_ms} />
          {current ? "" : " (updating…)"}
        </>
      )}
      {since === null && kind === "nci" && nci?.status === "complete" && (
        <>
          <span className="legend-key">
            <span style={{ color: ATTRACTIVE }}>●</span> attractive
          </span>
          <span className="legend-key">
            <span style={{ color: WEAK }}>●</span> van der Waals
          </span>
          <span className="legend-key">
            <span style={{ color: REPULSIVE }}>●</span> repulsive
          </span>
          <Took milliseconds={nci.elapsed_ms} />
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
