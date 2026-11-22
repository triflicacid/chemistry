import {useState} from "react";
import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import {
    ALL,
    COUNTS,
    DEFAULT_ISOVALUE,
    DEFAULT_OPACITY,
    MAX_ISOVALUE,
    MAX_SHOWN,
    MIN_ISOVALUE,
    ORBITAL_KINDS,
    type OrbitalChoice,
    type OrbitalKind,
    type OrbitalScope,
} from "../models/OrbitalModel";
import {type Bar, BarChart} from "../controls/BarChart";
import {type Segment, Segmented} from "../controls/Segmented";
import {InteractionDiagram} from "../controls/InteractionDiagram";
import {type ChartSeries, LineChart} from "../controls/LineChart";
import {type DrawnChannel, LevelDiagram} from "../controls/LevelDiagram";
import {
    answers,
    type DecodedField,
    decodeField,
    densityOfStates,
    enclosedShare,
    fieldsFor,
    fragmentsFor,
    frontierGaps,
    frontiers,
    nameAt,
    nameOf,
    nearGap,
    resolveTargets,
    smallestGap,
    toEv,
} from "../layout/orbitals";
import {atomShares, characterOf, KINDS, localisationBars, piBars, spinSplittings} from "../layout/composition";
import type {FieldResult, OrbitalChannel, OrbitalTarget, SessionState} from "../session/messages";
import {useSessionContext} from "../session/SessionContext";
import {NEUTRAL_COLOUR, PI_COLOUR, SIGMA_COLOUR} from "../layout/orbitalColours";
import {NEGATIVE_LOBE, POSITIVE_LOBE} from "../viewport/scene/OrbitalSurface";
import {seconds} from "./PropertiesOptions";

/** How many orbitals either side of the gap are drawn, unless all are asked for. */
const NEAR_GAP = 6;

/** The values of the choice of what the orbitals are of, which a dropdown holds as text. */
const EACH = "each";
const ONE = "one:";

export interface OrbitalsPanelProps {
  state: SessionState | null;
  models: Models;
}

/**
 * @param scope what the orbitals are of
 * @returns the value that stands for it in the dropdown
 */
function valueOf(scope: OrbitalScope): string {
  return scope.kind === "all" ? "" : scope.kind === "each" ? EACH : `${ONE}${scope.atom}`;
}

/**
 * @param value what the dropdown holds
 * @returns the scope it stands for
 */
function scopeOf(value: string): OrbitalScope {
  return value === "" ? ALL : value === EACH ? { kind: "each" } : { kind: "one", atom: value.slice(ONE.length) };
}

/**
 * The molecular orbitals of the structure, as an energy-level diagram where choosing a level draws that orbital in the scene.
 * They can be of everything together, of one molecule, or of every molecule on its own, side by side, where choosing a level
 * draws the same place in each, the HOMO of every molecule at once. Any kind of Hartree-Fock calculation can be asked for, and
 * an unrestricted one has a set of orbitals for each spin.
 */
export function OrbitalsPanel({ state, models }: OrbitalsPanelProps) {
  const { orbitals, orbitalsSince, requestOrbitals, fields, fieldsAsked } = useSessionContext();
  const { selected, kind, scope, count, colouring, isovalue, opacity } = useObservable(models.orbital);
  const debug = useObservable(models.debug);
  const [everything, setEverything] = useState(false);

  const fragments = fragmentsFor(scope, state);
  const asked = orbitalsSince !== null;

  // what the server last said counts only if it was asked the same thing
  const matches = answers(orbitals, kind, fragments);
  const complete = matches && orbitals.status === "complete" && orbitals.channels.length > 0;
  const channels: OrbitalChannel[] = complete ? orbitals.channels : [];
  const stale = matches && state !== null && orbitals.version !== state.version;

  // a set is named by its molecule where there are several, and by its spin where the spins have a set each
  const nameOfMolecule = (atom: string | null) => {
    const at = atom === null ? -1 : (state?.fragments.findIndex((each) => each.atoms.includes(atom)) ?? -1);
    return at < 0 ? null : `${at + 1}. ${state?.fragments[at]?.name ?? ""}`.trim();
  };
  const drawn: DrawnChannel[] = channels.map((channel) => {
    const spin = channel.spin === "alpha" ? "↑" : channel.spin === "beta" ? "↓" : "";
    const molecule = fragments.length > 1 ? nameOfMolecule(channel.fragment) : null;
    return {
      ...channel,
      levels: everything ? channel.levels : nearGap(channel.levels, channel.homo ?? -1, NEAR_GAP),
      title: [molecule, spin].filter((part) => part !== null && part !== "").join(" ") || null,
    };
  });

  const spins = [...new Set(channels.map((channel) => channel.spin))];
  const arrow = (spin: string) => (spins.length > 1 ? (spin === "alpha" ? "↑ " : "↓ ") : "");
  // the orbitals either side of the gap, as one choice, which is none of them when another level has been picked
  const frontierOptions: Segment<string>[] = spins.flatMap((spin) =>
    [0, 1].map((offset) => ({
      value: `${spin}:${offset}`,
      label: `${arrow(spin)}${nameAt(offset)}`,
      title:
        (offset === 0 ? "Draw the highest orbital that holds electrons" : "Draw the lowest orbital that holds none") + (scope.kind === "each" ? ", of every molecule" : ""),
      disabled: !complete || resolveTargets(orbitals, { spin, offset }).length === 0,
    })),
  );
  const frontierValue = selected !== null && (selected.offset === 0 || selected.offset === 1) ? `${selected.spin}:${selected.offset}` : null;
  const targets = complete && selected !== null ? resolveTargets(orbitals, selected, count) : [];
  const ready = fieldsFor(fields, targets, state).length === targets.length;
  const inside = targets.flatMap((target) => {
    const decoded = fieldsFor(fields, [target], state).flatMap((field) => decodeField(field) ?? []);
    const homo = channels.find((channel) => channel.fragment === target.fragment && channel.spin === target.spin)?.homo ?? -1;
    const molecule = fragments.length > 1 ? `${nameOfMolecule(target.fragment) ?? "all"} ` : "";
    return decoded.map((field: DecodedField) => ({ label: `${molecule}${arrow(target.spin)}${nameOf(target.orbital, homo)}`, field }));
  });

  return (
    <div className="energy-plots">
      <div className="energy-actions">
        <select value={kind} aria-label="How the orbitals are calculated" onChange={(event) => models.orbital.kindTo(event.target.value as OrbitalKind)}>
          {ORBITAL_KINDS.map(({ value, label, title }) => (
            <option key={value} value={value} title={title}>
              {label}
            </option>
          ))}
        </select>
        <select
          value={valueOf(scope)}
          aria-label="What the orbitals are of"
          title="Everything together as one molecule, or each molecule on its own, or just one"
          onChange={(event) => models.orbital.scopeTo(scopeOf(event.target.value))}
        >
          <option value="">All molecules together</option>
          {(state?.fragments.length ?? 0) > 1 && <option value={EACH}>Each molecule</option>}
          {(state?.fragments ?? []).map((each, i) => (
            <option key={each.key} value={`${ONE}${each.atoms[0] ?? ""}`}>
              {`${i + 1}. ${each.name} (${each.formula.molecular})`}
            </option>
          ))}
        </select>
        |
        <button type="button" className="tool" disabled={asked || state === null} onClick={() => requestOrbitals(kind, fragments)}>
          {asked ? "Calculating…" : complete ? "Calculate again" : "Calculate"}
        </button>
        <span className="segmented-captioned">
          <span className="segmented-caption">Orbital</span>
          <Segmented
            label="Which orbital at the gap to draw"
            value={frontierValue}
            options={frontierOptions}
            onChange={(value) => {
              const [spin, offset] = value.split(":");
              models.orbital.select({ spin: spin as OrbitalChoice["spin"], offset: Number(offset) });
            }}
          />
        </span>
        <select
          value={count === null ? "all" : String(count)}
          aria-label="How many orbitals to draw"
          title="How many orbitals to draw at once, counted outward from the orbital chosen: the HOMO and those below it, or the LUMO and those above it. Clicking a level or a bar draws that orbital alone, and this widens it"
          onChange={(event) => models.orbital.countTo(event.target.value === "all" ? null : Number(event.target.value))}
        >
          {COUNTS.map((each) => (
            <option key={each ?? "all"} value={each === null ? "all" : String(each)}>
              {each === null ? `Show all (up to ${MAX_SHOWN})` : `Show ${each}`}
            </option>
          ))}
        </select>
        {selected !== null && (
          <button type="button" className="tool" title="Take the orbitals out of the scene" onClick={() => models.orbital.select(null)}>
            Hide
          </button>
        )}
        {complete && !asked && <span className="energy-note">{seconds(orbitals.elapsed_ms)}</span>}
      </div>

      {!complete && (
        <p className="energy-note">
          {matches && orbitals.status !== "complete"
            ? (orbitals.message ?? "The orbitals could not be calculated.")
            : "Draws the molecular orbitals from a Hartree-Fock calculation. Choose a level to see its orbital in the scene."}
        </p>
      )}

      {complete && (
        <>
          <p className="energy-note">
            {selected !== null
              ? `${arrow(selected.spin)}${nameAt(selected.offset)}${count === 1 ? "" : ` and ${count === null ? "all" : `${count - 1} more`} ${selected.offset <= 0 ? "below" : "above"}`}${describeTargets(orbitals.channels, targets.length, selected.spin, count)}${fieldsAsked ? " · working out the shape…" : ready ? "" : " · updating…"}`
              : gapNote(channels)}
            {stale && " The structure has changed since, so the levels are being worked out again."}
          </p>
          <LevelDiagram channels={drawn} selected={selected} count={count} onSelect={(choice) => models.orbital.pick(choice)} />
          <label className="legend-check" title="Show every orbital, not only those either side of the gap">
            <input type="checkbox" checked={everything} onChange={(event) => setEverything(event.target.checked)} />
            Show all {Math.max(...channels.map((channel) => channel.levels.length))} orbitals
          </label>

          <DensityOfStates channels={drawn} nameOfMolecule={nameOfMolecule} />
          <FrontierGaps channels={channels} nameOfMolecule={nameOfMolecule} />
          <Localisation channels={drawn} selected={selected} count={count} state={state} nameOfMolecule={nameOfMolecule} onSelect={(choice) => models.orbital.pick(choice)} />
          <PiCharacter channels={drawn} selected={selected} count={count} nameOfMolecule={nameOfMolecule} onSelect={(choice) => models.orbital.pick(choice)} />
          <SpinSplitting channels={drawn} nameOfMolecule={nameOfMolecule} />
          <InteractionSection state={state} kind={kind} everything={everything} nameOfMolecule={nameOfMolecule} />

          {selected !== null && (
            <dl className="properties">
              <dt>Isovalue</dt>
              <dd className="position">
                <input
                  type="range"
                  className="render-slider"
                  min={Math.round(MIN_ISOVALUE * 1000)}
                  max={Math.round(MAX_ISOVALUE * 1000)}
                  step={5}
                  value={Math.round(isovalue * 1000)}
                  aria-label="The value of the orbital at its surface"
                  title="The value of the orbital at its surface. A smaller value draws a bigger, fatter surface. Double-click to put it back"
                  onChange={(event) => models.orbital.isovalueTo(Number(event.target.value) / 1000)}
                  onDoubleClick={() => models.orbital.isovalueTo(DEFAULT_ISOVALUE)}
                />
                <span className="unit render-value">±{isovalue.toFixed(3)}</span>
              </dd>
              <dt>Opacity</dt>
              <dd className="position">
                <input
                  type="range"
                  className="render-slider"
                  min={10}
                  max={100}
                  step={5}
                  value={Math.round(opacity * 100)}
                  aria-label="How opaque the surface of the orbital is"
                  title="How opaque the surface is. Double-click to put it back"
                  onChange={(event) => models.orbital.opacityTo(Number(event.target.value) / 100)}
                  onDoubleClick={() => models.orbital.opacityTo(DEFAULT_OPACITY)}
                />
                <span className="unit render-value">{Math.round(opacity * 100)}%</span>
              </dd>
              <dt>Colour</dt>
              <dd>
                <label
                  className="legend-check"
                  title="Colour each orbital by how much of it is sigma and how much is pi, whichever way its lobes point, instead of by the sign of its lobes. Needs a flat molecule"
                >
                  <input type="checkbox" checked={colouring === "character"} onChange={(event) => models.orbital.colouringTo(event.target.checked ? "character" : "phase")} />
                  By σ / π
                </label>
              </dd>
              <dt>Key</dt>
              <dd className="legend-note">
                {colouring === "character" ? (
                  <>
                    <span className="legend-key">
                      <span style={{ color: SIGMA_COLOUR }}>●</span> σ
                    </span>
                    <span className="legend-key">
                      <span style={{ color: PI_COLOUR }}>●</span> π
                    </span>
                    <span className="legend-key">
                      <span style={{ color: NEUTRAL_COLOUR }}>●</span> not flat
                    </span>
                  </>
                ) : (
                  <>
                    <span className="legend-key">
                      <span style={{ color: POSITIVE_LOBE }}>●</span> positive
                    </span>
                    <span className="legend-key">
                      <span style={{ color: NEGATIVE_LOBE }}>●</span> negative
                    </span>
                  </>
                )}
              </dd>
            </dl>
          )}

          {selected !== null && targets.length > 0 && <ShownOrbitals targets={targets} channels={channels} fields={fields} state={state} nameOfMolecule={nameOfMolecule} />}
          {selected !== null && targets.length > 0 && <OrbitalSites targets={targets} channels={channels} state={state} models={models} nameOfMolecule={nameOfMolecule} />}
          {selected !== null && targets.length > 0 && <Character targets={targets} channels={channels} nameOfMolecule={nameOfMolecule} />}
          {inside.length > 0 && <EnclosedShare shown={inside} isovalue={isovalue} onIsovalue={(value) => models.orbital.isovalueTo(value)} />}

          {fields.some((field) => field.status === "failed" && state !== null && field.version === state.version) && selected !== null && (
            <p className="energy-note">{fields.find((field) => field.status === "failed")?.message ?? "An orbital could not be worked out."}</p>
          )}

          {debug && <OrbitalsDebug channels={drawn} nameOfMolecule={nameOfMolecule} />}
        </>
      )}
    </div>
  );
}

/**
 * @param channels the sets of orbitals
 * @param drawn how many orbitals are drawn for the choice, all together
 * @param spin the set the choice is in
 * @param count how many orbitals were asked for in each set, or null for every one
 * @returns how many orbitals that is, and of how many molecules, when it is not simply one
 */
function describeTargets(channels: readonly OrbitalChannel[], drawn: number, spin: string, count: number | null): string {
  const molecules = new Set(channels.filter((channel) => channel.spin === spin).map((channel) => channel.fragment));
  if (drawn === 0) {
    return " · there is none";
  }
  return molecules.size > 1 || count !== 1 ? ` · ${drawn} ${drawn === 1 ? "orbital" : "orbitals"}${molecules.size > 1 ? ` of ${molecules.size} molecules` : ""}` : "";
}

/**
 * @param channels the sets of orbitals
 * @returns the gap between the highest orbital that holds electrons and the lowest that holds none, for each set that has both
 */
function gapNote(channels: readonly OrbitalChannel[]): string {
  const gaps = channels.map((channel, i) => {
    const homo = channel.homo;
    const next = homo === null ? undefined : channel.levels[homo + 1];
    const here = homo === null ? undefined : channel.levels[homo];
    const name = channels.length > 1 ? `${i + 1}${channel.spin === "alpha" ? "↑" : channel.spin === "beta" ? "↓" : ""} ` : "";
    return here && next ? `${name}${toEv(next.energy - here.energy).toFixed(2)} eV` : null;
  });
  const found = gaps.filter((gap) => gap !== null);
  return `${found.length > 0 ? `HOMO–LUMO gap ${found.join(" · ")}` : "No gap to measure"}. Click a level to draw its orbital${channels.length > 1 ? ", in every set that has one there" : ""}.`;
}

/** The numbers behind the calculation and the levels, and the grids the shown orbitals were worked out on. */
function OrbitalsDebug({ channels, nameOfMolecule }: { channels: readonly DrawnChannel[]; nameOfMolecule: (atom: string | null) => string | null }) {
  const { orbitals, fields } = useSessionContext();
  return (
    <>
      <p className="energy-note debug-note">
        {orbitals?.model?.toUpperCase()} · {orbitals?.electrons} electrons · {orbitals?.iterations} Fock builds · {orbitals?.total_energy?.toFixed(6)} Eh
      </p>
      {fields
        .filter((field) => field.status === "complete" && field.grid !== null)
        .map((field) => (
          <p key={`${field.fragment}|${field.spin}|${field.orbital}`} className="energy-note debug-note">
            {nameOfMolecule(field.fragment) ?? "all"} {field.spin} orbital {field.orbital}: {field.grid?.nx}×{field.grid?.ny}×{field.grid?.nz} points, {field.grid?.spacing.toFixed(3)} Å apart · {field.min?.toPrecision(3)} to{" "}
            {field.max?.toPrecision(3)} · {field.elapsed_ms} ms
          </p>
        ))}
      {channels.map((channel) => (
        <table key={`${channel.fragment ?? ""}|${channel.spin}`} className="debug-table">
          <thead>
            <tr>
              <th>{channel.title ?? channel.spin}</th>
              <th>Name</th>
              <th>Eh</th>
              <th>eV</th>
              <th>e⁻</th>
            </tr>
          </thead>
          <tbody>
            {channel.levels.map((level) => (
              <tr key={level.index}>
                <td>{level.index}</td>
                <td>{nameOf(level.index, channel.homo ?? -1)}</td>
                <td>{level.energy.toFixed(5)}</td>
                <td>{toEv(level.energy).toFixed(3)}</td>
                <td>{level.occupancy}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ))}
    </>
  );
}

interface ShownOrbitalsProps {
  /** The orbitals that are drawn. */
  targets: readonly OrbitalTarget[];
  channels: readonly OrbitalChannel[];
  /** The fields the server has sent, which say whether each orbital has arrived. */
  fields: readonly FieldResult[];
  state: SessionState | null;
  /** Names a molecule for the user. */
  nameOfMolecule: (atom: string | null) => string | null;
}

/** A table of the orbitals that are drawn: whose each is, where it sits and what it holds, and whether its shape has arrived. */
function ShownOrbitals({ targets, channels, fields, state, nameOfMolecule }: ShownOrbitalsProps) {
  return (
    <table className="debug-table" aria-label="The orbitals drawn">
      <thead>
        <tr>
          <th>Molecule</th>
          <th>Orbital</th>
          <th>#</th>
          <th title="electronvolts">eV</th>
          <th title="hartree">Eh</th>
          <th title="electrons it holds">e⁻</th>
          <th title="how much of the orbital is pi, in a flat molecule">π</th>
          <th>Shape</th>
        </tr>
      </thead>
      <tbody>
        {targets.map((target) => {
          const channel = channels.find((each) => each.fragment === target.fragment && each.spin === target.spin);
          const level = channel?.levels[target.orbital];
          const field = fields.find(
            (each) => state !== null && each.session_id === state.session_id && each.version === state.version && each.fragment === target.fragment && each.spin === target.spin && each.orbital === target.orbital,
          );
          const spin = target.spin === "alpha" ? "↑ " : target.spin === "beta" ? "↓ " : "";
          return (
            <tr key={`${target.fragment ?? ""}|${target.spin}|${target.orbital}`}>
              <td>{nameOfMolecule(target.fragment) ?? "all"}</td>
              <td>
                {spin}
                {nameOf(target.orbital, channel?.homo ?? -1)}
              </td>
              <td>{target.orbital}</td>
              <td>{level ? toEv(level.energy).toFixed(3) : ""}</td>
              <td>{level ? level.energy.toFixed(5) : ""}</td>
              <td>{level?.occupancy ?? ""}</td>
              <td>{level?.pi == null ? "" : `${Math.round(level.pi * 100)}%`}</td>
              <td title={field?.status === "failed" ? (field.message ?? undefined) : undefined}>
                {field === undefined ? "working…" : field.status === "failed" ? "failed" : "drawn"}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

/** How wide, in electronvolts, each orbital's bell is in the density of states to begin with. */
const DEFAULT_WIDTH = 0.25;

/** The values of the isovalue the share of the orbital it encloses is found at, which are those the slider can be put at. */
const ISOVALUES = Array.from({ length: Math.round(MAX_ISOVALUE / 0.005) }, (_, i) => Math.round((i + 1) * 5) / 1000);

/** The most orbitals whose enclosed share is drawn as lines, which is as many as the chart has colours. */
const MOST_LINES = 8;

interface FrontierGapsProps {
  channels: readonly OrbitalChannel[];
  nameOfMolecule: (atom: string | null) => string | null;
}

/**
 * What it costs to move an electron from the HOMO of each molecule to the LUMO of each other: the smallest gap is the pair most
 * likely to give and take electrons, which is the donor on the row and the acceptor in the column.
 */
function FrontierGaps({ channels, nameOfMolecule }: FrontierGapsProps) {
  const all = frontiers(channels);
  if (all.length < 2) {
    return null;
  }

  const gaps = frontierGaps(all);
  const best = smallestGap(gaps);
  const name = (frontier: { fragment: string | null }) => nameOfMolecule(frontier.fragment) ?? "all";
  return (
    <>
      <h3>Frontier gaps</h3>
      <p className="energy-note">
        From the HOMO of the molecule on each row to the LUMO of the molecule in each column, in eV. The smallest is picked out: that molecule is the likelier donor and the other the likelier acceptor.
      </p>
      <table className="debug-table" aria-label="Gaps from each molecule's HOMO to each other's LUMO">
        <thead>
          <tr>
            <th>HOMO of ↓ / LUMO of →</th>
            {all.map((acceptor) => (
              <th key={acceptor.fragment ?? ""}>{name(acceptor)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {all.map((donor, i) => (
            <tr key={donor.fragment ?? ""}>
              <td>{name(donor)}</td>
              {all.map((acceptor, j) => {
                const gap = gaps[i]?.[j] ?? null;
                return (
                  <td key={acceptor.fragment ?? ""} data-best={best?.[0] === i && best[1] === j ? "true" : undefined}>
                    {gap === null ? "—" : toEv(gap).toFixed(2)}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

interface DensityOfStatesProps {
  /** The sets of orbitals whose energies are smoothed, which may be only those near the gap. */
  channels: readonly DrawnChannel[];
  nameOfMolecule: (atom: string | null) => string | null;
}

/**
 * The orbital energies smoothed into a curve for each molecule, the orbitals that hold electrons in a strong line and those that
 * hold none faintly, so that clusters of levels and the gap between them show. How wide each orbital's bell is can be changed.
 */
function DensityOfStates({ channels, nameOfMolecule }: DensityOfStatesProps) {
  const [width, setWidth] = useState(DEFAULT_WIDTH);
  const molecules = [...new Set(channels.map((channel) => channel.fragment))];
  const energies = channels.flatMap((channel) => channel.levels.map((level) => toEv(level.energy)));
  if (energies.length === 0) {
    return null;
  }

  const from = Math.min(...energies) - 4 * width;
  const to = Math.max(...energies) + 4 * width;
  const series: ChartSeries[] = molecules.flatMap((fragment, i) => {
    const own = channels.filter((channel) => channel.fragment === fragment).flatMap((channel) => channel.levels);
    const name = molecules.length > 1 ? (nameOfMolecule(fragment) ?? "all") : "";
    const slot = ((i % 8) + 1) as ChartSeries["slot"];
    const curve = (levels: typeof own) => densityOfStates(levels.map((level) => toEv(level.energy)), width, from, to, 240);
    return [
      { name: `${name} filled`.trim(), slot, points: curve(own.filter((level) => level.occupancy > 0)) },
      { name: `${name} empty`.trim(), slot, muted: true, points: curve(own.filter((level) => level.occupancy === 0)) },
    ];
  });

  return (
    <>
      <h3>Density of states</h3>
      <LineChart
        label="Density of orbital energies"
        series={series}
        xTitle="Energy (eV)"
        yTitle="States per eV"
        formatX={(x) => x.toFixed(1)}
        formatY={(y) => y.toFixed(1)}
        height={150}
      />
      <dl className="properties">
        <dt>Width</dt>
        <dd className="position">
          <input
            type="range"
            className="render-slider"
            min={5}
            max={100}
            step={5}
            value={Math.round(width * 100)}
            aria-label="How wide each orbital is smoothed, in electronvolts"
            title="How wide each orbital's bell is. A narrow one shows every level; a wide one shows clusters. Double-click to put it back"
            onChange={(event) => setWidth(Number(event.target.value) / 100)}
            onDoubleClick={() => setWidth(DEFAULT_WIDTH)}
          />
          <span className="unit render-value">{width.toFixed(2)} eV</span>
        </dd>
      </dl>
    </>
  );
}

interface EnclosedShareProps {
  /** The orbitals that are drawn, each with its values on a grid. */
  shown: readonly { label: string; field: DecodedField }[];
  isovalue: number;
  /** Told the isovalue that the marker was dragged to. */
  onIsovalue: (isovalue: number) => void;
}

/**
 * How much of each orbital lies inside its surface, for every isovalue the slider can be put at: the share of the squared amplitude
 * on the grid that is at least that value. A marker shows where the slider is, and dragging it moves the slider, so that the
 * isovalue can be chosen for how much of the orbital the surface holds.
 */
function EnclosedShare({ shown, isovalue, onIsovalue }: EnclosedShareProps) {
  const lines = shown.slice(0, MOST_LINES);
  const series: ChartSeries[] = lines.map(({ label, field }, i) => ({
    name: label,
    slot: ((i % 8) + 1) as ChartSeries["slot"],
    points: enclosedShare(field.values, ISOVALUES).map((share, k) => ({ x: ISOVALUES[k] as number, y: share * 100 })),
  }));

  return (
    <>
      <h3>Orbital inside the surface</h3>
      <p className="energy-note">
        The share of the orbital held inside the surface at each isovalue. Drag the marker to choose the isovalue for how much you want inside.
        {shown.length > MOST_LINES && ` Only the first ${MOST_LINES} of ${shown.length} orbitals are drawn here.`}
      </p>
      <LineChart
        label="Share of the orbital inside its surface against isovalue"
        series={series}
        xTitle="Isovalue"
        yTitle="Share inside (%)"
        formatX={(x) => x.toFixed(2)}
        formatY={(y) => y.toFixed(0)}
        marker={isovalue}
        onMarker={onIsovalue}
        height={150}
      />
    </>
  );
}

/** The colour a molecule is drawn in, which belongs to its place in the list and so stays with it. */
function colourOf(index: number): 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 {
  return ((Math.max(0, index) % 8) + 1) as 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8;
}

interface LocalisationProps {
  /** The sets of orbitals to draw, which may be only those near the gap. */
  channels: readonly DrawnChannel[];
  selected: OrbitalChoice | null;
  count: number | null;
  state: SessionState | null;
  nameOfMolecule: (atom: string | null) => string | null;
  /** Told which orbital a bar is of when it is clicked. */
  onSelect: (choice: OrbitalChoice) => void;
}

/**
 * How many atoms each orbital is spread over, for the orbitals near the gap: one where it is on a single atom and more the more
 * it is shared, so that localised orbitals and delocalised ones, such as the pi system of a ring, tell themselves apart. Clicking a
 * bar draws that orbital.
 */
function Localisation({ channels, selected, count, state, nameOfMolecule, onSelect }: LocalisationProps) {
  const { bars, choices, picked } = localisationBars(
    channels,
    selected,
    count,
    (state?.fragments ?? []).map((fragment) => fragment.atoms),
    nameOfMolecule,
  );
  const several = new Set(channels.map((channel) => channel.fragment)).size > 1;

  if (bars.length === 0) {
    return null;
  }
  return (
    <>
      <h3>Localisation</h3>
      <p className="energy-note">
        How many atoms each orbital is spread over: 1 is on a single atom, and more the more it is shared. Click a bar to draw that orbital{several ? ", and the same place in every other molecule" : ""}.
      </p>
      <BarChart
        label="How many atoms each orbital is spread over"
        bars={bars}
        yTitle="Atoms"
        formatY={(value) => value.toFixed(1)}
        onSelect={(key) => {
          const choice = choices.get(key);
          if (choice) {
            onSelect(choice);
          }
        }}
        picked={picked}
        legend
      />
    </>
  );
}

interface SpinSplittingProps {
  channels: readonly DrawnChannel[];
  nameOfMolecule: (atom: string | null) => string | null;
}

/**
 * How much higher each spin-up orbital is than the matching spin-down one, for the orbitals near the gap, in each molecule that has a set
 * for each spin. It is zero for a closed-shell molecule, and grows with how strongly the unpaired electrons polarise the others.
 */
function SpinSplitting({ channels, nameOfMolecule }: SpinSplittingProps) {
  const molecules = [...new Set(channels.map((channel) => channel.fragment))];
  const bars: Bar[] = molecules.flatMap((fragment, m) => {
    const up = channels.find((channel) => channel.fragment === fragment && channel.spin === "alpha");
    const down = channels.find((channel) => channel.fragment === fragment && channel.spin === "beta");
    if (!up || !down) {
      return [];
    }

    const shown = new Set(up.levels.map((level) => level.index));
    const owner = molecules.length > 1 ? `${nameOfMolecule(fragment) ?? ""} ` : "";
    return spinSplittings(up, down)
      .filter((each) => shown.has(each.index))
      .map((each) => ({
        key: `${fragment ?? ""}|${each.index}`,
        label: `${owner}${nameOf(each.index, up.homo ?? -1)}`.trim(),
        parts: [{ name: owner.trim() || "↑ − ↓", value: toEv(each.split), slot: colourOf(m) }],
      }));
  });

  if (bars.length === 0) {
    return null;
  }
  return (
    <>
      <h3>Spin splitting</h3>
      <p className="energy-note">The energy of each spin-up orbital less the spin-down one, in eV. It is zero where the spins share their orbitals.</p>
      <BarChart label="Spin splitting of each orbital" bars={bars} yTitle="↑ − ↓ (eV)" formatY={(value) => value.toFixed(2)} legend />
    </>
  );
}

interface InteractionSectionProps {
  state: SessionState | null;
  kind: OrbitalKind;
  /** Whether to draw every orbital, and not only those near the gap. */
  everything: boolean;
  nameOfMolecule: (atom: string | null) => string | null;
}

/**
 * The orbital interaction diagram: the orbitals of everything together in the middle and those of each molecule on its own at the sides, with
 * lines from each of the whole's to the molecule orbitals it is made of. It is asked for with a button, since it needs the whole and every molecule
 * calculated.
 */
function InteractionSection({ state, kind, everything, nameOfMolecule }: InteractionSectionProps) {
  const { interaction, interactionSince, requestInteraction } = useSessionContext();
  const [spin, setSpin] = useState<"restricted" | "alpha" | "beta">("restricted");
  if ((state?.fragments.length ?? 0) < 2) {
    return null;
  }

  const asked = interactionSince !== null;
  const matches = interaction !== null && interaction.requested === kind;
  const complete = matches && interaction.status === "complete" && interaction.parts.length > 0;
  const spins = complete ? [...new Set(interaction.parts[0]?.channels.map((channel) => channel.spin) ?? [])] : [];
  const drawn = spins.includes(spin) ? spin : (spins[0] ?? "restricted");
  const stale = matches && state !== null && interaction.version !== state.version;

  return (
    <>
      <h3>Interaction diagram</h3>
      <div className="energy-actions">
        <button type="button" className="tool" disabled={asked || state === null} onClick={() => requestInteraction(kind)}>
          {asked ? "Calculating…" : complete ? "Calculate again" : "Calculate"}
        </button>
        {spins.length > 1 && (
          <select value={drawn} aria-label="Which spin to draw the interaction of" onChange={(event) => setSpin(event.target.value as typeof spin)}>
            {spins.map((each) => (
              <option key={each} value={each}>
                {each === "alpha" ? "↑ spin up" : each === "beta" ? "↓ spin down" : each}
              </option>
            ))}
          </select>
        )}
        {complete && !asked && <span className="energy-note">{seconds(interaction.elapsed_ms)}</span>}
      </div>
      <p className="energy-note">
        {complete
          ? `The orbitals of everything together in the middle, and of each molecule on its own at the sides. A line joins an orbital of the whole to the molecule orbitals it is made of, and is thicker the more of it they are.${stale ? " The structure has changed since, so calculate again." : ""}`
          : matches && interaction.status !== "complete"
            ? (interaction.message ?? "The diagram could not be made.")
            : "Shows how the orbitals of the molecules together are made of the orbitals each has on its own. Needs at least two molecules."}
      </p>
      {complete && <InteractionDiagram report={interaction} spin={drawn} around={everything ? null : NEAR_GAP} nameOfMolecule={nameOfMolecule} />}
    </>
  );
}

interface OrbitalSitesProps {
  /** The orbitals that are drawn, which one of them is picked to show. */
  targets: readonly OrbitalTarget[];
  channels: readonly OrbitalChannel[];
  state: SessionState | null;
  models: Models;
  nameOfMolecule: (atom: string | null) => string | null;
}

/**
 * Where an orbital is, as a bar for each atom with how much of the orbital is on it, which does not depend on the isovalue. Where several orbitals
 * are drawn, which one is shown can be changed, and clicking a bar selects that atom in the scene.
 */
function OrbitalSites({ targets, channels, state, models, nameOfMolecule }: OrbitalSitesProps) {
  const [which, setWhich] = useState(0);
  const target = targets[Math.min(which, targets.length - 1)];
  const channel = target ? channels.find((each) => each.fragment === target.fragment && each.spin === target.spin) : undefined;
  const level = channel?.levels[target?.orbital ?? -1];
  if (!target || !channel || !level || level.weights.length === 0) {
    return null;
  }

  const homo = channel.homo ?? -1;
  const spin = target.spin === "alpha" ? "↑ " : target.spin === "beta" ? "↓ " : "";
  const name = (each: OrbitalTarget) => `${each.fragment !== null && targets.some((other) => other.fragment !== each.fragment) ? `${nameOfMolecule(each.fragment) ?? ""} ` : ""}${each.spin === "alpha" ? "↑ " : each.spin === "beta" ? "↓ " : ""}${nameOf(each.orbital, channels.find((one) => one.fragment === each.fragment && one.spin === each.spin)?.homo ?? -1)}`.trim();
  const shares = atomShares(level);
  const bars: Bar[] = channel.atoms.map((atom, i) => {
    const molecule = state?.fragments.findIndex((fragment) => fragment.atoms.includes(atom)) ?? -1;
    return { key: atom, label: models.index?.label(atom) ?? atom, parts: [{ name: nameOfMolecule(atom) ?? "share", value: shares[i] ?? 0, slot: colourOf(molecule) }] };
  });

  return (
    <>
      <h3>Where the orbital is</h3>
      <div className="energy-actions">
        {targets.length > 1 && (
          <select value={Math.min(which, targets.length - 1)} aria-label="Which orbital to show the atoms of" onChange={(event) => setWhich(Number(event.target.value))}>
            {targets.map((each, i) => (
              <option key={`${each.fragment ?? ""}|${each.spin}|${each.orbital}`} value={i}>
                {name(each)}
              </option>
            ))}
          </select>
        )}
        <span className="energy-note">
          {spin}
          {nameOf(target.orbital, homo)} · click a bar to select that atom
        </span>
      </div>
      <BarChart
        label="How much of the orbital is on each atom"
        bars={bars}
        yTitle="Share of the orbital"
        formatY={(value) => `${Math.round(value * 100)}%`}
        onSelect={(atom) => models.selection.replace([{ kind: "atom", id: atom }])}
        legend={new Set(bars.map((bar) => bar.parts[0]?.name)).size > 1}
      />
    </>
  );
}

interface CharacterProps {
  /** The orbitals that are drawn. */
  targets: readonly OrbitalTarget[];
  channels: readonly OrbitalChannel[];
  nameOfMolecule: (atom: string | null) => string | null;
}

/**
 * What each orbital that is drawn is made of: how much is in s functions, in p functions and in d and higher, all atoms together. An
 * orbital that is mostly p on a ring is a pi orbital, and one that is mostly s is a bonding orbital of the core.
 */
function Character({ targets, channels, nameOfMolecule }: CharacterProps) {
  const bars: Bar[] = targets.flatMap((target) => {
    const channel = channels.find((each) => each.fragment === target.fragment && each.spin === target.spin);
    const level = channel?.levels[target.orbital];
    if (!channel || !level || level.weights.length === 0) {
      return [];
    }

    const owner = target.fragment !== null && targets.some((other) => other.fragment !== target.fragment) ? `${nameOfMolecule(target.fragment) ?? ""} ` : "";
    const spin = target.spin === "alpha" ? "↑ " : target.spin === "beta" ? "↓ " : "";
    return [
      {
        key: `${target.fragment ?? ""}|${target.spin}|${target.orbital}`,
        label: `${owner}${spin}${nameOf(target.orbital, channel.homo ?? -1)}`.trim(),
        parts: characterOf(level).map((value, kind) => ({ name: KINDS[kind] ?? "", value, slot: colourOf(kind) })),
      },
    ];
  });

  if (bars.length === 0) {
    return null;
  }
  return (
    <>
      <h3>Orbital character</h3>
      <p className="energy-note">What each orbital is made of, over all its atoms: s, p, and d and higher functions.</p>
      <BarChart label="What each orbital is made of" bars={bars} yTitle="Share" formatY={(value) => `${Math.round(value * 100)}%`} legend />
    </>
  );
}

interface PiCharacterProps {
  /** The sets of orbitals to draw, which may be only those near the gap. */
  channels: readonly DrawnChannel[];
  selected: OrbitalChoice | null;
  count: number | null;
  nameOfMolecule: (atom: string | null) => string | null;
  /** Told which orbital a bar is of when it is clicked. */
  onSelect: (choice: OrbitalChoice) => void;
}

/** How much of each orbital is sigma and how much is pi. */
function PiCharacter({ channels, selected, count, nameOfMolecule, onSelect }: PiCharacterProps) {
  const { bars, choices, picked, flatness } = piBars(channels, selected, count, nameOfMolecule);
  if (channels.every((channel) => channel.levels.every((level) => level.weights.length === 0))) {
    return null;
  }

  return (
    <>
      <h3>σ / π character</h3>
      {bars.length === 0 ? (
        <p className="energy-note">
          {flatness.length === 0
            ? "Sigma and pi need at least three atoms that are not in a line."
            : `Sigma and pi are told apart by the plane of the atoms, and these lie ${Math.min(...flatness).toFixed(2)} Å off a plane on average, which is not flat enough. Choose a flat molecule on its own, as a ring or a carbonyl is.`}
        </p>
      ) : (
        <>
          <p className="energy-note">
            How much of each orbital is σ, which is unchanged by reflecting the molecule in its plane, and how much is π, which turns over there and has a nodal plane through the atoms. In the diagram π levels are coloured. Click a bar to draw that orbital.
          </p>
          <BarChart
            label="How much of each orbital is sigma and how much is pi"
            bars={bars}
            yTitle="Share"
            formatY={(value) => `${Math.round(value * 100)}%`}
            onSelect={(key) => {
              const choice = choices.get(key);
              if (choice) {
                onSelect(choice);
              }
            }}
            picked={picked}
            legend
          />
        </>
      )}
    </>
  );
}
