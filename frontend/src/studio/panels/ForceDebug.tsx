import {ATOM_ARROW, KCAL_PER_HARTREE, largest, magnitude, NEGLIGIBLE, toKcalPerAngstrom} from "../layout/forceArrows";
import type {ForcesResult} from "../session/messages";

export interface ForceDebugProps {
  /** What the server said of the forces, which must be a complete one. */
  forces: ForcesResult;
  /** How much the arrows are stretched, as the user has set it. */
  scale: number;
  /** Names an atom for the user. */
  label: (id: string) => string;
}

/** The numbers behind the force arrows: where they came from, how they are scaled, and every force and torque. */
export function ForceDebug({ forces, scale, label }: ForceDebugProps) {
  const biggest = largest(forces);
  const perAngstrom = biggest.atom > 0 ? (ATOM_ARROW * scale) / toKcalPerAngstrom(biggest.atom) : 0;

  return (
    <>
      <dt>Debug</dt>
      <dd className="legend-note debug-note">
        {forces.model} model · session version {forces.version} · {forces.elapsed_ms} ms
        <br />
        {biggest.atom > 0 ? `${ATOM_ARROW * scale} Å of arrow is ${toKcalPerAngstrom(biggest.atom).toFixed(3)} kcal/mol/Å, so 1 Å is ${(1 / perAngstrom).toFixed(2)}` : "every force is zero"}
        <br />
        arrows under {Math.round(NEGLIGIBLE * 100)}% of the largest are left out
      </dd>
      <dt>Molecules</dt>
      <dd className="debug-table-cell">
        <table className="debug-table">
          <thead>
            <tr>
              <th>Molecule</th>
              <th title="kcal/mol/Å">Force</th>
              <th title="kcal/mol">Torque</th>
            </tr>
          </thead>
          <tbody>
            {forces.fragments.map((fragment, index) => (
              <tr key={fragment.atoms.join(",")}>
                <td title={fragment.atoms.map(label).join(", ")}>{index + 1}</td>
                <td>{toKcalPerAngstrom(magnitude(fragment.force)).toFixed(3)}</td>
                <td>{(magnitude(fragment.torque) * KCAL_PER_HARTREE).toFixed(3)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </dd>
      <dt>Atoms</dt>
      <dd className="debug-table-cell">
        <table className="debug-table">
          <thead>
            <tr>
              <th>Atom</th>
              <th title="kcal/mol/Å">x</th>
              <th>y</th>
              <th>z</th>
              <th>|F|</th>
            </tr>
          </thead>
          <tbody>
            {forces.atoms.map(({ id, force }) => {
              const size = magnitude(force);
              const hidden = biggest.atom > 0 && size / biggest.atom < NEGLIGIBLE;
              return (
                <tr key={id} data-hidden={hidden ? "true" : undefined} title={hidden ? "Too small to be drawn" : undefined}>
                  <td>{label(id)}</td>
                  <td>{toKcalPerAngstrom(force.x).toFixed(2)}</td>
                  <td>{toKcalPerAngstrom(force.y).toFixed(2)}</td>
                  <td>{toKcalPerAngstrom(force.z).toFixed(2)}</td>
                  <td>{toKcalPerAngstrom(size).toFixed(2)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </dd>
    </>
  );
}
