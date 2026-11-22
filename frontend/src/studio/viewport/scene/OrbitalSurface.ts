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
import {
    characterColour,
    CORE_SHELL,
    energyColour,
    isRole,
    NEGATIVE_LOBE,
    OUTER_SHELL,
    POSITIVE_LOBE,
    roleColour
} from "../../layout/orbitalColours";
import {mergeSummed, mergeUnion} from "../../layout/orbitalMerge";
import {contourLines} from "../../layout/contours";
import type {LevelBy, OrbitalColouring, OrbitalCombine, OrbitalDisplay, OrbitalStyle} from "../../models/OrbitalModel";
import type {MoleculeScene} from "./MoleculeScene";

export {CORE_SHELL, NEGATIVE_LOBE, OUTER_SHELL, POSITIVE_LOBE};

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
  /** How several orbitals are shown when more than one is drawn. */
  combine: OrbitalCombine;
  /** Whether the surfaces are drawn as solid skins or as outlines. */
  style: OrbitalStyle;
}

/** The distance between the planes an outline is drawn at, in ångström, which are the same for every surface so that their lines line up. */
const OUTLINE_SPACING = 0.5;

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
  /** Whether the surfaces being drawn are drawn as outlines. */
  private outline = false;

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

    this.outline = options.style === "outline";
    let drawn: OrbitalDrawn | null = null;
    if (orbitals.length > 0) {
      const marks = new Group();
      drawn = { positive: 0, negative: 0 };
      // several orbitals can be merged into one surface, which is of no use for one
      const merged = orbitals.length > 1 && options.combine !== "separate";
      if (merged) {
        const parts = options.combine === "sum" ? mergeSummed(orbitals, options.share, options.opacity, options.colouring) : mergeUnion(orbitals, options);
        for (const part of parts) {
          marks.add(this.lobe(part.surface, part.colour, part.opacity, part.colours));
          drawn.positive += part.surface.positions.length / 9;
        }
        if (options.debug) {
          const boxed = new Set<string>();
          for (const { field } of orbitals) {
            const key = `${field.shape.nx},${field.shape.ny},${field.shape.nz},${field.shape.origin.x},${field.shape.origin.y},${field.shape.origin.z}`;
            if (!boxed.has(key)) {
              boxed.add(key);
              marks.add(this.box(field));
            }
          }
        }
      }

      for (const { field, pi, occupied, fromGapEv, offset, spin } of merged ? [] : orbitals) {
        if (options.display === "probability") {
          // the shells are of the amplitude without its sign, since the probability does not have one
          const amplitudes = field.values.map(Math.abs);
          // by energy both shells are the colour of the orbital's energy, the outer one more transparent, in place of the usual orange
          const [core, outer] =
            options.colouring === "energy"
              ? [energyColour(occupied, fromGapEv), energyColour(occupied, fromGapEv)]
              : isRole(options.colouring)
                ? [roleColour(options.colouring, offset, spin), roleColour(options.colouring, offset, spin)]
                : [CORE_SHELL, OUTER_SHELL];
          const shells = options.share > 50 ? [{ share: 50, colour: core, opacity: options.opacity }, { share: options.share, colour: outer, opacity: options.opacity * 0.55 }] : [{ share: options.share, colour: core, opacity: options.opacity }];
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
        // by energy too, which is the same for both lobes, since the sign is not what is being shown
        const whole =
          options.colouring === "character"
            ? characterColour(pi)
            : options.colouring === "energy"
              ? energyColour(occupied, fromGapEv)
              : isRole(options.colouring)
                ? roleColour(options.colouring, offset, spin)
                : null;
        marks.add(this.lobe(positive, whole ?? POSITIVE_LOBE, options.opacity));
        marks.add(this.lobe(negative, whole ?? NEGATIVE_LOBE, options.opacity));
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
   * @param colours the red, green and blue of each vertex, from 0 to 1, or none to draw it in the one colour
   * @returns the mesh, or its lines where the surfaces are drawn as outlines
   */
  private lobe(surface: Surface, colour: string, opacity: number, colours: Float32Array | null = null): Mesh | LineSegments {
    if (this.outline) {
      const lines = contourLines(surface, OUTLINE_SPACING, colours);
      const geometry = new BufferGeometry();
      geometry.setAttribute("position", new Float32BufferAttribute(lines.positions, 3));
      if (lines.colours) {
        geometry.setAttribute("color", new Float32BufferAttribute(lines.colours, 3));
      }
      // lines never hide what is behind them, and are thin, so they are kept stronger than a skin of the same opacity would be
      const strength = Math.min(1, opacity + 0.25);
      const outline = new LineSegments(geometry, new LineBasicMaterial({ ...(lines.colours ? { vertexColors: true } : { color: colour }), transparent: strength < 1, opacity: strength, depthWrite: false }));
      outline.renderOrder = 3;
      return outline;
    }

    const geometry = new BufferGeometry();
    geometry.setAttribute("position", new Float32BufferAttribute(surface.positions, 3));
    geometry.setAttribute("normal", new Float32BufferAttribute(surface.normals, 3));
    if (colours) {
      geometry.setAttribute("color", new Float32BufferAttribute(colours, 3));
    }
    const mesh = new Mesh(
      geometry,
      // the inside of a lobe is seen through its front, so both faces are drawn, and a clear surface must not hide what is behind it
      new MeshStandardMaterial({ ...(colours ? { vertexColors: true } : { color: colour }), roughness: 0.4, transparent: opacity < 1, opacity, side: DoubleSide, depthWrite: opacity >= 0.95 }),
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
