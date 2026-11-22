import {Observable} from "./observable";

/** localStorage key. */
const PROPERTIES_KEY = "studio.properties";

/** What the atoms are coloured by: their element, or a property the server has calculated. */
export type ColourBy = "element" | "charge";

const COLOURINGS: readonly ColourBy[] = ["element", "charge"];

/** What the server calls a way of assigning partial charges. */
export type ChargeModel = "mulliken" | "rhf" | "uhf" | "rohf" | "formal";

/** The ways of assigning partial charges, in the order they are offered, with what each means. */
export const CHARGE_MODELS: readonly { value: ChargeModel; label: string; title: string }[] = [
  { value: "mulliken", label: "Hartree-Fock (automatic)", title: "Mulliken charges from Hartree-Fock: closed-shell where the electrons pair up, otherwise unrestricted as a doublet" },
  { value: "rhf", label: "Hartree-Fock (RHF)", title: "Mulliken charges from closed-shell restricted Hartree-Fock, which needs an even number of electrons" },
  { value: "uhf", label: "Hartree-Fock (UHF)", title: "Mulliken charges from unrestricted Hartree-Fock, as a singlet if the electrons pair up and otherwise a doublet" },
  { value: "rohf", label: "Hartree-Fock (ROHF)", title: "Mulliken charges from restricted open-shell Hartree-Fock, as a singlet if the electrons pair up and otherwise a doublet" },
  { value: "formal", label: "Formal charges", title: "Each atom's formal charge, with no calculation, so instantly" },
];

/**
 * @param value anything
 * @returns it if it is a charge model, and the automatic one otherwise
 */
export function asChargeModel(value: unknown): ChargeModel {
  return CHARGE_MODELS.find((model) => model.value === value)?.value ?? "mulliken";
}

/**
 * @param value anything
 * @returns it if it is a way of colouring the atoms, and colouring by element otherwise
 */
export function asColourBy(value: unknown): ColourBy {
  return COLOURINGS.find((colouring) => colouring === value) ?? "element";
}

export const MIN_FORCE_SCALE = 0.25;
export const MAX_FORCE_SCALE = 3;

/**
 * @param scale a multiple of the usual arrow length
 * @returns the nearest scale the arrows can be drawn at, to the hundredth
 */
export function clampForceScale(scale: number): number {
  return Number.isFinite(scale) ? Math.round(Math.min(MAX_FORCE_SCALE, Math.max(MIN_FORCE_SCALE, scale)) * 100) / 100 : 1;
}

/** What is drawn as a surface of the electron density: nothing, the density alone, or the density coloured by the electrostatic potential. */
export type SurfaceKind = "none" | "density" | "esp";

/** What colours a surface of the electron density alone: nothing, or the element of the atom each part of it is nearest. */
export type DensityColour = "single" | "element";

/**
 * @param value anything
 * @returns it if it is a way of colouring the density surface, and one colour otherwise
 */
export function asDensityColour(value: unknown): DensityColour {
  return value === "element" ? "element" : "single";
}

export const MIN_SURFACE_ISOVALUE = 0.001;
export const MAX_SURFACE_ISOVALUE = 0.02;
export const DEFAULT_SURFACE_ISOVALUE = 0.004;
export const MIN_SURFACE_OPACITY = 0.3;
export const DEFAULT_SURFACE_OPACITY = 0.9;

/**
 * @param isovalue an electron density in electrons per cubic bohr
 * @returns the nearest density a surface can be drawn at, to the thousandth
 */
export function clampSurfaceIsovalue(isovalue: number): number {
  return Number.isFinite(isovalue) ? Math.round(Math.min(MAX_SURFACE_ISOVALUE, Math.max(MIN_SURFACE_ISOVALUE, isovalue)) * 1000) / 1000 : DEFAULT_SURFACE_ISOVALUE;
}

/**
 * @param opacity how opaque a surface is
 * @returns the nearest opacity it can be drawn at, to the hundredth
 */
export function clampSurfaceOpacity(opacity: number): number {
  return Number.isFinite(opacity) ? Math.round(Math.min(1, Math.max(MIN_SURFACE_OPACITY, opacity)) * 100) / 100 : DEFAULT_SURFACE_OPACITY;
}

export const MIN_POLAR_THRESHOLD = 0.05;
export const MAX_POLAR_THRESHOLD = 0.6;
export const DEFAULT_POLAR_THRESHOLD = 0.2;

/**
 * @param threshold a charge in elementary charges
 * @returns the nearest charge a threshold can be, to the hundredth
 */
export function clampPolarThreshold(threshold: number): number {
  return Number.isFinite(threshold)
    ? Math.round(Math.min(MAX_POLAR_THRESHOLD, Math.max(MIN_POLAR_THRESHOLD, threshold)) * 100) / 100
    : DEFAULT_POLAR_THRESHOLD;
}

/** Which calculated properties are shown on the molecule. */
export interface PropertiesState {
  /** What the atoms are coloured by. */
  colourBy: ColourBy;
  /** How partial charges are assigned, which matters while the atoms are coloured by them. */
  chargeModel: ChargeModel;
  /** Whether atoms with a partial charge far enough from zero are labelled δ+ or δ−. */
  polarLabels: boolean;
  /** How far from zero, in elementary charges, a partial charge must be for its atom to be labelled. */
  polarThreshold: number;
  /** Whether the force the other molecules put on each atom is drawn as an arrow. */
  forceArrows: boolean;
  /** A multiple of the usual length of a force arrow. */
  forceScale: number;
  /** Whether the total force and torque on each molecule are drawn too. */
  netForces: boolean;
  /** What is drawn as a surface of the electron density. */
  surface: SurfaceKind;
  /** The density, in electrons per cubic bohr, that the surface is at. */
  /** What colours the density surface when it is not coloured by the potential. */
  densityColour: DensityColour;
  surfaceIsovalue: number;
  /** How opaque the surface is. */
  surfaceOpacity: number;
  /** Whether the Properties section is open. */
  open: boolean;
  /** Which groups of settings in it are unfolded. */
  expanded: Expanded;
}

/** The groups of settings in the Properties section, each of which folds away. */
export type PropertyGroup = "atoms" | "forces" | "surface";

/** Which groups of settings are unfolded. */
export type Expanded = Record<PropertyGroup, boolean>;

/**
 * Which calculated properties are shown on the molecule. How they are shown is remembered between visits, but not whether they are:
 * colouring by charge, force arrows and surfaces each start a calculation, which must not begin by itself on a reload.
 */
export class PropertiesModel extends Observable<PropertiesState> {
  constructor() {
    super(PropertiesModel.read());
  }

  /**
   * @param colourBy what to colour the atoms by
   */
  colourWith(colourBy: ColourBy): void {
    if (this.get().colourBy !== colourBy) {
      this.change({ ...this.get(), colourBy });
    }
  }

  /**
   * @param chargeModel how to assign partial charges
   */
  chargeWith(chargeModel: ChargeModel): void {
    if (this.get().chargeModel !== chargeModel) {
      this.change({ ...this.get(), chargeModel });
    }
  }

  /**
   * @param polarLabels whether to label the atoms that are clearly positive or negative
   */
  labelPolarity(polarLabels: boolean): void {
    if (this.get().polarLabels !== polarLabels) {
      this.change({ ...this.get(), polarLabels });
    }
  }

  /**
   * @param threshold how far from zero, in elementary charges, a partial charge must be for its atom to be labelled
   */
  thresholdTo(threshold: number): void {
    const polarThreshold = clampPolarThreshold(threshold);
    if (this.get().polarThreshold !== polarThreshold) {
      this.change({ ...this.get(), polarThreshold });
    }
  }

  /**
   * @param forceArrows whether to draw the force on each atom
   */
  forcesTo(forceArrows: boolean): void {
    if (this.get().forceArrows !== forceArrows) {
      this.change({ ...this.get(), forceArrows });
    }
  }

  /**
   * @param scale a multiple of the usual length of a force arrow
   */
  forceScaleTo(scale: number): void {
    const forceScale = clampForceScale(scale);
    if (this.get().forceScale !== forceScale) {
      this.change({ ...this.get(), forceScale });
    }
  }

  /**
   * @param netForces whether to draw the total force and torque on each molecule
   */
  netForcesTo(netForces: boolean): void {
    if (this.get().netForces !== netForces) {
      this.change({ ...this.get(), netForces });
    }
  }

  /**
   * @param surface what to draw as a surface of the electron density
   */
  surfaceTo(surface: SurfaceKind): void {
    if (this.get().surface !== surface) {
      this.change({ ...this.get(), surface });
    }
  }

  /**
   * @param densityColour what colours the density surface
   */
  densityColourTo(densityColour: DensityColour): void {
    if (this.get().densityColour !== densityColour) {
      this.change({ ...this.get(), densityColour });
    }
  }

  /**
   * @param isovalue the density the surface is at, in electrons per cubic bohr
   */
  surfaceIsovalueTo(isovalue: number): void {
    const surfaceIsovalue = clampSurfaceIsovalue(isovalue);
    if (this.get().surfaceIsovalue !== surfaceIsovalue) {
      this.change({ ...this.get(), surfaceIsovalue });
    }
  }

  /**
   * @param opacity how opaque the surface is
   */
  surfaceOpacityTo(opacity: number): void {
    const surfaceOpacity = clampSurfaceOpacity(opacity);
    if (this.get().surfaceOpacity !== surfaceOpacity) {
      this.change({ ...this.get(), surfaceOpacity });
    }
  }

  /**
   * Unfolds or folds the settings of one group.
   *
   * @param group the group
   * @param open whether its settings are shown
   */
  expandTo(group: PropertyGroup, open: boolean): void {
    if (this.get().expanded[group] !== open) {
      this.change({ ...this.get(), expanded: { ...this.get().expanded, [group]: open } });
    }
  }

  /**
   * Opens or closes the Properties section.
   *
   * @param open whether it is open
   */
  openTo(open: boolean): void {
    if (this.get().open !== open) {
      this.change({ ...this.get(), open });
    }
  }

  private change(state: PropertiesState): void {
    this.set(state);
    try {
      localStorage.setItem(PROPERTIES_KEY, JSON.stringify(state));
    } catch {
      // a browser that refuses storage still gets the settings, just not remembered ones
    }
  }

  private static read(): PropertiesState {
    const defaults: PropertiesState = { colourBy: "element", chargeModel: "mulliken", polarLabels: false, polarThreshold: DEFAULT_POLAR_THRESHOLD, forceArrows: false, forceScale: 1, netForces: true, surface: "none", densityColour: "single", surfaceIsovalue: DEFAULT_SURFACE_ISOVALUE, surfaceOpacity: DEFAULT_SURFACE_OPACITY, open: false, expanded: { atoms: false, forces: false, surface: false } };
    let stored: unknown;
    try {
      stored = JSON.parse(localStorage.getItem(PROPERTIES_KEY) ?? "null");
    } catch {
      return defaults;
    }

    const held = stored as Partial<PropertiesState> | null;
    if (!held || typeof held !== "object") {
      return defaults;
    }
    return {
      colourBy: defaults.colourBy,
      chargeModel: asChargeModel(held.chargeModel),
      polarLabels: typeof held.polarLabels === "boolean" ? held.polarLabels : defaults.polarLabels,
      forceArrows: defaults.forceArrows,
      forceScale: typeof held.forceScale === "number" ? clampForceScale(held.forceScale) : defaults.forceScale,
      netForces: typeof held.netForces === "boolean" ? held.netForces : defaults.netForces,
      polarThreshold: typeof held.polarThreshold === "number" ? clampPolarThreshold(held.polarThreshold) : defaults.polarThreshold,
      surface: defaults.surface,
      densityColour: asDensityColour(held.densityColour),
      surfaceIsovalue: typeof held.surfaceIsovalue === "number" ? clampSurfaceIsovalue(held.surfaceIsovalue) : defaults.surfaceIsovalue,
      surfaceOpacity: typeof held.surfaceOpacity === "number" ? clampSurfaceOpacity(held.surfaceOpacity) : defaults.surfaceOpacity,
      expanded: {
        atoms: held.expanded?.atoms === true,
        forces: held.expanded?.forces === true,
        surface: held.expanded?.surface === true,
      },
      open: typeof held.open === "boolean" ? held.open : defaults.open,
    };
  }
}
