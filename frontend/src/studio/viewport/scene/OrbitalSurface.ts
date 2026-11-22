import {
    BoxGeometry,
    BufferGeometry,
    DoubleSide,
    EdgesGeometry,
    Float32BufferAttribute,
    Group,
    LineBasicMaterial,
    LineSegments,
    Mesh,
    MeshStandardMaterial,
} from "three";
import {disposeMaterials} from "../../../render/dispose";
import {isosurface, type Surface} from "../../../render/isosurface";
import type {Stage} from "../../../render/stage";
import {amplitudeForShare, type DecodedField, type DrawnOrbital} from "../../layout/orbitals";
import {characterColour} from "../../layout/orbitalColours";
import type {LevelBy, OrbitalColouring, OrbitalDisplay} from "../../models/OrbitalModel";
import type {MoleculeScene} from "./MoleculeScene";

/** The colour of the part of an orbital that is positive. */
export const POSITIVE_LOBE = "#2f6fe4";

/** The colour of the part that is negative. */
export const NEGATIVE_LOBE = "#e0453a";

/** How an orbital is drawn. */
export interface OrbitalOptions {
  /** The value of the orbital at its surface. */
  isovalue: number;
  /** How opaque the surface is. */
  opacity: number;
  /** What colours the surfaces: the sign of each lobe, or what the orbital is made of whatever the sign. */
  colouring: OrbitalColouring;
  /** Whether to also draw the box the values were worked out in. */
  debug: boolean;
  /** Whether to draw the amplitude, as lobes with a sign, or the probability, as shells. */
  display: OrbitalDisplay;
  /** How the level of the lobes is chosen. */
  levelBy: LevelBy;
  /** How much of the orbital the surface holds, as a percentage, where the level is chosen by share, and always for the shells. */
  share: number;
}

/** The colour of the shell of an orbital that holds half of it, where the electron is most likely to be. */
export const CORE_SHELL = "#d9480f";

/** The colour of the shell that holds the share asked for. */
export const OUTER_SHELL = "#fab005";

/** What drawing an orbital made, which debugging reports. */
export interface OrbitalDrawn {
  /** How many triangles each lobe has. */
  positive: number;
  negative: number;
}

/**
 * Draws a molecular orbital as two surfaces, the positive lobes in one colour and the negative in another, which is how an
 * orbital's phase is shown. The surfaces sit in the molecule's group so that they move with it.
 */
export class OrbitalSurface {
  /** The surfaces, or null while there are none. */
  private marks: Group | null = null;

  constructor(
    private readonly stage: Stage,
    private readonly scene: MoleculeScene,
  ) {}

  /** Forgets the surfaces, which were taken away along with everything else drawn in the molecule's group. */
  forget(): void {
    this.marks = null;
  }

  /**
   * Draws the orbital afresh, taking away the one drawn before.
   *
   * @param orbitals each orbital to draw, with its values on a grid in the molecule's own frame, which may be of several molecules, or
   *     none to draw none
   * @param options how to draw them
   * @returns how many triangles the lobes have all together, or null if nothing was drawn
   */
  show(orbitals: readonly DrawnOrbital[], options: OrbitalOptions): OrbitalDrawn | null {
    const { content } = this.scene;
    if (this.marks) {
      content.remove(this.marks);
      disposeGeometry(this.marks);
      disposeMaterials(this.marks);
      this.marks = null;
    }

    let drawn: OrbitalDrawn | null = null;
    if (orbitals.length > 0) {
      const marks = new Group();
      drawn = { positive: 0, negative: 0 };
      for (const { field, pi } of orbitals) {
        if (options.display === "probability") {
          // the shells are of the amplitude without its sign, since the probability does not have one
          const amplitudes = field.values.map(Math.abs);
          const shells = options.share > 50 ? [{ share: 50, colour: CORE_SHELL, opacity: options.opacity }, { share: options.share, colour: OUTER_SHELL, opacity: options.opacity * 0.55 }] : [{ share: options.share, colour: CORE_SHELL, opacity: options.opacity }];
          for (const shell of shells) {
            const level = amplitudeForShare(field.values, shell.share / 100);
            if (level > 0) {
              const surface = isosurface(amplitudes, field.shape, level);
              marks.add(this.lobe(surface, shell.colour, Math.min(1, shell.opacity)));
              drawn.positive += surface.positions.length / 9;
            }
          }
          if (options.debug) {
            marks.add(this.box(field));
          }
          continue;
        }

        const level = options.levelBy === "share" ? amplitudeForShare(field.values, options.share / 100) : options.isovalue;
        if (!(level > 0)) {
          continue;
        }
        const positive = isosurface(field.values, field.shape, level);
        // the negative lobes are the positive ones of the field turned upside down
        const negative = isosurface(field.values.map((value) => -value), field.shape, level);
        // by character both lobes are the one colour, since what colours the orbital is what it is made of and not which way it points
        const character = characterColour(pi);
        marks.add(this.lobe(positive, options.colouring === "character" ? character : POSITIVE_LOBE, options.opacity));
        marks.add(this.lobe(negative, options.colouring === "character" ? character : NEGATIVE_LOBE, options.opacity));
        drawn.positive += positive.positions.length / 9;
        drawn.negative += negative.positions.length / 9;

        if (options.debug) {
          marks.add(this.box(field));
        }
      }

      content.add(marks);
      this.marks = marks;
    }
    this.stage.requestRender();
    return drawn;
  }

  /**
   * @param surface the surface of one lobe
   * @param colour what to colour it
   * @param opacity how opaque
   * @returns the mesh
   */
  private lobe(surface: Surface, colour: string, opacity: number): Mesh {
    const geometry = new BufferGeometry();
    geometry.setAttribute("position", new Float32BufferAttribute(surface.positions, 3));
    geometry.setAttribute("normal", new Float32BufferAttribute(surface.normals, 3));
    const mesh = new Mesh(
      geometry,
      // the inside of a lobe is seen through its front, so both faces are drawn, and a clear surface must not hide what is behind it
      new MeshStandardMaterial({ color: colour, roughness: 0.4, transparent: opacity < 1, opacity, side: DoubleSide, depthWrite: opacity >= 0.95 }),
    );
    mesh.renderOrder = 3;
    return mesh;
  }

  /**
   * @param field the values, whose grid is outlined
   * @returns the outline of the grid
   */
  private box(field: DecodedField): LineSegments {
    const { nx, ny, nz, origin, spacing } = field.shape;
    const [width, height, depth] = [(nx - 1) * spacing, (ny - 1) * spacing, (nz - 1) * spacing];
    const outline = new LineSegments(new EdgesGeometry(new BoxGeometry(width, height, depth)), new LineBasicMaterial({ color: "#888888" }));
    outline.position.set(origin.x + width / 2, origin.y + height / 2, origin.z + depth / 2);
    return outline;
  }
}

/**
 * @param group something drawn, whose meshes' geometry is its own and not shared
 */
function disposeGeometry(group: Group): void {
  group.traverse((object) => {
    if (object instanceof Mesh) {
      object.geometry.dispose();
    } else if (object instanceof LineSegments) {
      // the outline of the grid has a material of its own, which disposing the meshes' does not reach
      object.geometry.dispose();
      object.material.dispose();
    }
  });
}
