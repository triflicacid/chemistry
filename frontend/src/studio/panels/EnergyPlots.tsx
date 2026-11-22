import {useEffect, useMemo, useState} from "react";
import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import {type Segment, Segmented} from "../controls/Segmented";
import {BreakdownChart} from "../controls/BreakdownChart";
import {type ChartSeries, LineChart} from "../controls/LineChart";
import {stack} from "../layout/breakdown";
import {apart} from "../layout/chart";
import {bondDescribes, energyAt, positionsOf, toKcal} from "../layout/scan";
import type {EnergyStep, ScanBond, ScanPoint, ScanResult, ScfStep, SessionState} from "../session/messages";
import {useSessionContext} from "../session/SessionContext";
import {describeProgress, seconds} from "./PropertiesOptions";

type Plot = "scf" | "optimiser" | "scan" | "breakdown";

const PLOTS: Segment<Plot>[] = [
  { value: "scf", label: "SCF convergence" },
  { value: "optimiser", label: "Optimiser" },
  { value: "scan", label: "Hydrogen-bond scan" },
  { value: "breakdown", label: "Breakdown" },
];

export interface EnergyPlotsProps {
  state: SessionState | null;
  models: Models;
}

/** The energies the server works out as series: how a quantum calculation converges, how an optimisation goes, and how two molecules attract. */
export function EnergyPlots({ state, models }: EnergyPlotsProps) {
  const [plot, setPlot] = useState<Plot>("scf");
  const debug = useObservable(models.debug);

  return (
    <div className="energy-plots">
      <Segmented label="Energy plot" value={plot} options={PLOTS} onChange={setPlot} />
      {plot === "scf" && <ScfPlot state={state} models={models} debug={debug} />}
      {plot === "optimiser" && <OptimiserPlot debug={debug} />}
      {plot === "scan" && <ScanPlot state={state} models={models} debug={debug} />}
      {plot === "breakdown" && <BreakdownPlot state={state} models={models} debug={debug} />}
    </div>
  );
}

interface PlotProps {
  state: SessionState | null;
  models: Models;
  debug: boolean;
}

/** The total energy of the Hartree-Fock calculation after each Fock build, and with debugging how far it was from converged. */
function ScfPlot({ state, models, debug }: PlotProps) {
  const { charges, calculatingCharges, chargesProgress, requestCharges } = useSessionContext();
  const { chargeModel } = useObservable(models.properties);
  const steps: ScfStep[] = charges?.convergence ?? [];
  const last = steps.at(-1);
  const stale = charges !== null && state !== null && charges.version !== state.version;

  return (
    <>
      <div className="energy-actions">
        <button type="button" className="tool" disabled={calculatingCharges !== null || state === null} onClick={() => requestCharges(chargeModel)}>
          {steps.length === 0 ? "Calculate" : "Calculate again"}
        </button>
        {calculatingCharges && <span role="status">{describeProgress(chargesProgress)}</span>}
        {!calculatingCharges && charges && <span className="energy-note">{charges.requested} model, {seconds(charges.elapsed_ms)}</span>}
      </div>

      {steps.length === 0 ? (
        <p className="energy-note">
          {charges
            ? "That model did no quantum calculation, so there is nothing converging. Choose a Hartree-Fock model under Properties."
            : "Calculate the partial charges with a Hartree-Fock model to see how its energy settles."}
        </p>
      ) : (
        <>
          <p className="energy-note">
            {steps.length} Fock builds{last && `, ending at ${last.energy.toFixed(6)} Eh with an error of ${last.error.toExponential(1)}`}
            {stale && " (the structure has changed since)"}
          </p>
          <LineChart
            label="Distance of the energy from its final value against Fock build"
            series={[{ name: "|E − E final|", slot: 1, points: apart(steps.map(({ iteration, energy }) => ({ x: iteration, y: energy }))) }]}
            xTitle="Fock build (one pass over the orbitals)"
            yTitle="|E − E final| (Eh)"
            logY
            formatX={(x) => String(Math.round(x))}
            formatY={(y) => y.toExponential(0)}
          />
          {debug && <ScfDebug steps={steps} />}
        </>
      )}
    </>
  );
}

/** How far each Fock build was from self-consistent, which is what says whether the energy has settled, and every number. */
function ScfDebug({ steps }: { steps: ScfStep[] }) {
  return (
    <>
      <LineChart
        label="Error against Fock build"
        series={[{ name: "Error", slot: 2, points: steps.map(({ iteration, error }) => ({ x: iteration, y: error })) }]}
        xTitle="Fock build"
        yTitle="Error"
        logY
        formatX={(x) => String(Math.round(x))}
        formatY={(y) => y.toExponential(0)}
        height={140}
      />
      <table className="debug-table">
        <thead>
          <tr>
            <th>Build</th>
            <th>Energy (Eh)</th>
            <th>Change (Eh)</th>
            <th>Error</th>
          </tr>
        </thead>
        <tbody>
          {steps.map((step, i) => (
            <tr key={step.iteration}>
              <td>{step.iteration}</td>
              <td>{step.energy.toFixed(8)}</td>
              <td>{i === 0 ? "" : (step.energy - (steps[i - 1]?.energy ?? step.energy)).toExponential(2)}</td>
              <td>{step.error.toExponential(2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

/** The value the optimiser minimises after each step, filling in as it runs. */
function OptimiserPlot({ debug }: { debug: boolean }) {
  const { optimising, outcome } = useSessionContext();
  const history: EnergyStep[] = optimising ? optimising.history : (outcome?.history ?? []);

  const first = history[0];
  const last = history.at(-1);
  if (!first || !last) {
    return <p className="energy-note">{optimising ? "Waiting for the first step…" : "Optimise the structure to see the value the optimiser minimises fall."}</p>;
  }

  return (
    <>
      <p className="energy-note">
        {optimising ? `Step ${last.iteration} so far: ` : `${outcome?.iterations ?? last.iteration} steps: `}
        {first.energy.toPrecision(5)} → {last.energy.toPrecision(5)}. It has no unit, and is not the strain the toolbar shows.
      </p>
      <LineChart
        label="Distance of the optimiser value from its final value against step"
        series={[{ name: "|value − final|", slot: 1, points: apart(history.map(({ iteration, energy }) => ({ x: iteration, y: energy }))) }]}
        xTitle="Step (one move of the atoms)"
        yTitle="|value − final|"
        logY
        formatX={(x) => String(Math.round(x))}
        formatY={(y) => y.toExponential(0)}
      />
      {debug && (
        <>
          {outcome && !optimising && (
            <p className="energy-note debug-note">
              {outcome.status} · {outcome.atoms} atoms moved · strain {outcome.strainBefore?.toFixed(2) ?? "?"} → {outcome.strainAfter?.toFixed(2) ?? "?"}
              {outcome.message && ` · ${outcome.message}`}
            </p>
          )}
          <table className="debug-table">
            <thead>
              <tr>
                <th>Step</th>
                <th>Value</th>
                <th>Change</th>
              </tr>
            </thead>
            <tbody>
              {history.map((step, i) => (
                <tr key={step.iteration}>
                  <td>{step.iteration}</td>
                  <td>{step.energy.toPrecision(8)}</td>
                  <td>{i === 0 ? "" : (step.energy - (history[i - 1]?.energy ?? step.energy)).toExponential(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </>
  );
}

/** The interaction energy of two molecules as the acceptor's is drawn along a hydrogen bond, with a marker that moves it in the scene. */
function ScanPlot({ state, models, debug }: PlotProps) {
  const { scan, scanningSince, requestScan, dragAtoms, dropAtoms } = useSessionContext();
  const [selected, setSelected] = useState(0);
  const [overlay, setOverlay] = useState(false);
  const [others, setOthers] = useState(true);
  const [marker, setMarker] = useState<number | null>(null);
  const label = (id: string | null) => (id === null ? "?" : (models.index?.label(id) ?? id));

  // where the atoms were when the scan arrived is what tells later which of its bonds still describe the session
  const before = useMemo(() => (scan && state && scan.status === "complete" ? positionsOf(state) : null), [scan]); // eslint-disable-line react-hooks/exhaustive-deps
  const describes = (bond: ScanBond) => state !== null && before !== null && bondDescribes(bond, state, before);

  const bonds = scan?.status === "complete" ? scan.bonds : [];
  const bond = bonds[Math.min(selected, bonds.length - 1)];
  const counted = others && (scan?.molecules ?? 0) > 2;
  const current = bond !== undefined && describes(bond);

  useEffect(() => setSelected(0), [scan]);

  useEffect(() => {
    // the marker starts at the point nearest to where the molecules are now
    const distances = bond?.points.map((point) => point.distance) ?? [];
    setMarker(!bond || distances.length === 0 ? null : distances.reduce((best, each) => (Math.abs(each - bond.current) < Math.abs(best - bond.current) ? each : best)));
  }, [bond]);

  // while a scan is being looked at, debug mode draws its bond in the scene with the energy at the marker
  const shown = bond?.points.find((point) => point.distance === marker);
  const { hydrogenBond } = models;
  useEffect(() => {
    if (bond && shown) {
      hydrogenBond.to({ hydrogen: bond.hydrogen, acceptor: bond.acceptor, text: `${toKcal(energyAt(shown, counted)).toFixed(2)} kcal/mol · ${shown.distance.toFixed(1)} Å` });
    } else {
      hydrogenBond.to(null);
    }
  }, [hydrogenBond, bond, shown, counted]);
  useEffect(() => () => hydrogenBond.to(null), [hydrogenBond]);

  const moves = (distance: number) => bond?.points.find((point) => point.distance === distance)?.atoms ?? [];
  const asked = scanningSince !== null;
  const name = (each: ScanBond) => `${label(each.donor)}–${label(each.hydrogen)}···${label(each.acceptor)}`;

  // the selected bond is first, so that the marker follows it, and keeps its own colour wherever it is in the list
  const series: ChartSeries[] = bond
    ? [bond, ...(overlay ? bonds.filter((each) => each !== bond) : [])].map((each) => ({
        name: name(each),
        slot: ((bonds.indexOf(each) % 8) + 1) as ChartSeries["slot"],
        muted: each !== bond,
        points: each.points.map((point) => ({ x: point.distance, y: toKcal(energyAt(point, counted)) })),
      }))
    : [];
  const lowest = series[0]?.points.reduce((best, point) => (point.y < best.y ? point : best), series[0].points[0] as { x: number; y: number });

  return (
    <>
      <div className="energy-actions">
        <button type="button" className="tool" disabled={asked || state === null} onClick={requestScan}>
          {asked ? "Scanning…" : scan?.status === "complete" ? "Scan again" : "Scan"}
        </button>
        {scan?.status === "complete" && !asked && <span className="energy-note">{seconds(scan.elapsed_ms)}</span>}
      </div>

      {(!scan || scan.status !== "complete") && (
        <p className="energy-note">Draws one molecule away from another along a hydrogen bond. It needs at least two molecules, such as water twice (O.O).</p>
      )}

      {scan?.status === "complete" && bond && (
        <>
          <div className="energy-actions">
            <select
              value={Math.min(selected, bonds.length - 1)}
              aria-label="Which hydrogen bond to scan along"
              title="The bonds nearest their acceptors are scanned. The one chosen is the one the marker moves along"
              onChange={(event) => setSelected(Number(event.target.value))}
            >
              {bonds.map((each, i) => (
                <option key={`${each.hydrogen}:${each.acceptor}`} value={i}>
                  {`${i + 1}. ${name(each)} · ${each.current.toFixed(2)} Å now${describes(each) ? "" : " (changed since)"}`}
                </option>
              ))}
            </select>
            {bonds.length > 1 && (
              <label className="legend-check" title="Draw the curve of every scanned bond, the chosen one in full and the rest faintly">
                <input type="checkbox" checked={overlay} onChange={(event) => setOverlay(event.target.checked)} />
                Show all bonds
              </label>
            )}
            {scan.molecules > 2 && (
              <label className="legend-check" title="Count how the moved molecule interacts with every other molecule too, not only with the one it is bonded to">
                <input type="checkbox" checked={others} onChange={(event) => setOthers(event.target.checked)} />
                Include the other molecules
              </label>
            )}
          </div>
          <p className="energy-note">{describeScan(bond, shown, counted, current, label)}</p>
          <LineChart
            label="Interaction energy against hydrogen-bond length"
            series={series}
            xTitle={`${label(bond.hydrogen)} to ${label(bond.acceptor)} distance (Å)`}
            yTitle={counted ? "Energy with all others (kcal/mol)" : "Energy (kcal/mol)"}
            formatX={(x) => x.toFixed(1)}
            formatY={(y) => y.toFixed(1)}
            marker={marker}
            reference={lowest ? { x: lowest.x, label: "lowest" } : null}
            onMarker={current ? (x) => { setMarker(x); dragAtoms(moves(x)); } : undefined}
            onMarkerEnd={current ? (x) => void dropAtoms(moves(x)) : undefined}
          />
          {debug && <ScanDebug scan={scan} bond={bond} label={label} name={name} />}
        </>
      )}
    </>
  );
}

/**
 * @param bond the bond being scanned
 * @param shown the point the marker is at, if it is at one
 * @param counted whether the energies count the other molecules
 * @param current whether the scan still describes the session
 * @param label names an atom for the user
 * @returns the lowest energy, and the energy where the marker is
 */
function describeScan(bond: ScanBond, shown: ScanPoint | undefined, counted: boolean, current: boolean, label: (id: string | null) => string): string {
  const parts = [`${label(bond.donor)}–${label(bond.hydrogen)}···${label(bond.acceptor)}`];
  if (!counted) {
    parts.push(`lowest ${toKcal(bond.minimum_energy).toFixed(2)} kcal/mol at ${bond.minimum_distance.toFixed(2)} Å`);
  }
  if (shown) {
    parts.push(`marker ${toKcal(energyAt(shown, counted)).toFixed(2)} kcal/mol at ${shown.distance.toFixed(1)} Å`);
  }
  return current
    ? `${parts.join(" · ")}. Drag along the curve to move the molecule; letting go keeps it there, as one edit that can be undone.`
    : `${parts.join(" · ")}. The structure has changed since, so scan again to move along it.`;
}

interface ScanDebugProps {
  scan: ScanResult;
  bond: ScanBond;
  label: (id: string | null) => string;
  name: (bond: ScanBond) => string;
}

/** Every bond that was scanned, and what the chosen bond's energy is made of at every distance. */
function ScanDebug({ scan, bond, label, name }: ScanDebugProps) {
  const terms: ChartSeries[] = [
    { name: "Nuclear–nuclear", slot: 1, points: bond.points.map((point) => ({ x: point.distance, y: toKcal(point.terms.nuclear_nuclear) })) },
    { name: "Nuclear–electron", slot: 2, points: bond.points.map((point) => ({ x: point.distance, y: toKcal(point.terms.nuclear_electron) })) },
    { name: "Electron–electron", slot: 3, points: bond.points.map((point) => ({ x: point.distance, y: toKcal(point.terms.electron_electron) })) },
    { name: "Exchange–correlation", slot: 4, points: bond.points.map((point) => ({ x: point.distance, y: toKcal(point.terms.exchange_correlation) })) },
  ];

  return (
    <>
      <p className="energy-note debug-note">
        {scan.model} model · {scan.molecules} molecules · {scan.bonds.length} bonds · {bond.points.length} points each · donor {label(bond.donor)}, hydrogen {label(bond.hydrogen)}, acceptor {label(bond.acceptor)}
      </p>
      <table className="debug-table">
        <thead>
          <tr>
            <th>Bond</th>
            <th>Now (Å)</th>
            <th>Lowest (Å)</th>
            <th>Lowest (kcal/mol)</th>
          </tr>
        </thead>
        <tbody>
          {scan.bonds.map((each) => (
            <tr key={`${each.hydrogen}:${each.acceptor}`}>
              <td>{name(each)}</td>
              <td>{each.current.toFixed(2)}</td>
              <td>{each.minimum_distance.toFixed(2)}</td>
              <td>{toKcal(each.minimum_energy).toFixed(2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="energy-note debug-note">The terms below are of the chosen bond's energy between its two molecules, whether or not the others are counted above.</p>
      <LineChart
        label="The terms of the interaction energy against hydrogen-bond length"
        series={terms}
        xTitle="Distance (Å)"
        yTitle="Term (kcal/mol)"
        formatX={(x) => x.toFixed(1)}
        formatY={(y) => y.toFixed(0)}
        height={200}
      />
      <table className="debug-table">
        <thead>
          <tr>
            <th>Å</th>
            <th>Pair</th>
            {scan.molecules > 2 && <th>With others</th>}
            <th>N–N</th>
            <th>N–e</th>
            <th>e–e</th>
            <th>xc</th>
          </tr>
        </thead>
        <tbody>
          {bond.points.map((point) => (
            <tr key={point.distance}>
              <td>{point.distance.toFixed(2)}</td>
              <td>{toKcal(point.energy).toFixed(3)}</td>
              {scan.molecules > 2 && <td>{point.energy_with_others === null ? "" : toKcal(point.energy_with_others).toFixed(3)}</td>}
              <td>{toKcal(point.terms.nuclear_nuclear).toFixed(2)}</td>
              <td>{toKcal(point.terms.nuclear_electron).toFixed(2)}</td>
              <td>{toKcal(point.terms.electron_electron).toFixed(2)}</td>
              <td>{toKcal(point.terms.exchange_correlation).toFixed(2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

/** What the energy between each pair of molecules is made of: the repulsions, the attractions and the exchange-correlation, stacked. */
function BreakdownPlot({ state, models, debug }: PlotProps) {
  const { forces, calculatingForcesSince, requestForces } = useSessionContext();
  const asked = calculatingForcesSince !== null;
  const stale = forces !== null && state !== null && forces.version !== state.version;
  // molecules are numbered in the order the Outliner lists them
  const molecule = (id: string) => {
    const at = state?.fragments.findIndex((fragment) => fragment.atoms.includes(id)) ?? -1;
    return at < 0 ? (models.index?.label(id) ?? id) : `${at + 1} ${state?.fragments[at]?.name ?? ""}`.trim();
  };

  // with debugging on, the numbers are written in the scene so that the bars can be matched to the molecules
  const { numbers } = models;
  useEffect(() => {
    numbers.to(debug && state ? state.fragments.map((fragment, i) => ({ atoms: fragment.atoms, text: String(i + 1) })) : []);
  }, [numbers, debug, state]);
  useEffect(() => () => numbers.to([]), [numbers]);
  const interactions = forces?.status === "complete" ? forces.interactions : [];

  return (
    <>
      <div className="energy-actions">
        <button type="button" className="tool" disabled={asked || state === null} onClick={requestForces}>
          {asked ? "Calculating…" : interactions.length === 0 ? "Calculate" : "Calculate again"}
        </button>
        {forces?.status === "complete" && !asked && <span className="energy-note">{seconds(forces.elapsed_ms)}</span>}
      </div>

      {interactions.length === 0 ? (
        <p className="energy-note">Splits the energy between each pair of molecules into its parts. It needs at least two molecules, such as water twice (O.O).</p>
      ) : (
        <>
          <p className="energy-note">
            Parts to the right of zero raise the energy and parts to the left lower it. The big parts nearly cancel, and the tick is what is left.
            {stale && " The structure has changed since, so calculate again."}
          </p>
          <BreakdownChart
            label="The parts of the energy between each pair of molecules"
            unit="kcal/mol"
            rows={interactions.map((pair) => ({ label: `${molecule(pair.first)} · ${molecule(pair.second)}`, stack: stack(pair.terms) }))}
          />
          {debug && forces && (
            <>
              <p className="energy-note debug-note">
                {forces.model} model · {interactions.length} pairs · system energy {forces.energy === null ? "?" : toKcal(forces.energy).toFixed(3)} kcal/mol
              </p>
              <table className="debug-table">
                <thead>
                  <tr>
                    <th>Pair</th>
                    <th>N–N</th>
                    <th>N–e</th>
                    <th>e–e</th>
                    <th>xc</th>
                    <th>Net</th>
                  </tr>
                </thead>
                <tbody>
                  {interactions.map((pair) => (
                    <tr key={`${pair.first}:${pair.second}`}>
                      <td>{molecule(pair.first)} · {molecule(pair.second)}</td>
                      <td>{toKcal(pair.terms.nuclear_nuclear).toFixed(2)}</td>
                      <td>{toKcal(pair.terms.nuclear_electron).toFixed(2)}</td>
                      <td>{toKcal(pair.terms.electron_electron).toFixed(2)}</td>
                      <td>{toKcal(pair.terms.exchange_correlation).toFixed(2)}</td>
                      <td>{toKcal(pair.energy).toFixed(3)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </>
      )}
    </>
  );
}
