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

export const MIN_ESP_ISOVALUE = 0.001;
export const MAX_ESP_ISOVALUE = 0.02;
export const DEFAULT_ESP_ISOVALUE = 0.004;
export const MIN_ESP_OPACITY = 0.3;
export const DEFAULT_ESP_OPACITY = 0.9;

/**
 * @param isovalue an electron density in electrons per cubic bohr
 * @returns the nearest density a surface can be drawn at, to the thousandth
 */
export function clampEspIsovalue(isovalue: number): number {
  return Number.isFinite(isovalue) ? Math.round(Math.min(MAX_ESP_ISOVALUE, Math.max(MIN_ESP_ISOVALUE, isovalue)) * 1000) / 1000 : DEFAULT_ESP_ISOVALUE;
}

/**
 * @param opacity how opaque a surface is
 * @returns the nearest opacity it can be drawn at, to the hundredth
 */
export function clampEspOpacity(opacity: number): number {
  return Number.isFinite(opacity) ? Math.round(Math.min(1, Math.max(MIN_ESP_OPACITY, opacity)) * 100) / 100 : DEFAULT_ESP_OPACITY;
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
  /** Whether the electron density is drawn as a surface coloured by the electrostatic potential. */
  espSurface: boolean;
  /** The density, in electrons per cubic bohr, that the surface is at. */
  espIsovalue: number;
  /** How opaque the surface is. */
  espOpacity: number;
  /** Whether the Properties section is open. */
  open: boolean;
}

/** Which calculated properties are shown on the molecule, remembered between visits. */
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
   * @param espSurface whether to draw the electron density coloured by the electrostatic potential
   */
  espSurfaceTo(espSurface: boolean): void {
    if (this.get().espSurface !== espSurface) {
      this.change({ ...this.get(), espSurface });
    }
  }

  /**
   * @param isovalue the density the surface is at, in electrons per cubic bohr
   */
  espIsovalueTo(isovalue: number): void {
    const espIsovalue = clampEspIsovalue(isovalue);
    if (this.get().espIsovalue !== espIsovalue) {
      this.change({ ...this.get(), espIsovalue });
    }
  }

  /**
   * @param opacity how opaque the surface is
   */
  espOpacityTo(opacity: number): void {
    const espOpacity = clampEspOpacity(opacity);
    if (this.get().espOpacity !== espOpacity) {
      this.change({ ...this.get(), espOpacity });
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
    const defaults: PropertiesState = { colourBy: "element", chargeModel: "mulliken", polarLabels: false, polarThreshold: DEFAULT_POLAR_THRESHOLD, forceArrows: false, forceScale: 1, netForces: true, espSurface: false, espIsovalue: DEFAULT_ESP_ISOVALUE, espOpacity: DEFAULT_ESP_OPACITY, open: false };
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
      colourBy: asColourBy(held.colourBy),
      chargeModel: asChargeModel(held.chargeModel),
      polarLabels: typeof held.polarLabels === "boolean" ? held.polarLabels : defaults.polarLabels,
      forceArrows: typeof held.forceArrows === "boolean" ? held.forceArrows : defaults.forceArrows,
      forceScale: typeof held.forceScale === "number" ? clampForceScale(held.forceScale) : defaults.forceScale,
      netForces: typeof held.netForces === "boolean" ? held.netForces : defaults.netForces,
      polarThreshold: typeof held.polarThreshold === "number" ? clampPolarThreshold(held.polarThreshold) : defaults.polarThreshold,
      espSurface: typeof held.espSurface === "boolean" ? held.espSurface : defaults.espSurface,
      espIsovalue: typeof held.espIsovalue === "number" ? clampEspIsovalue(held.espIsovalue) : defaults.espIsovalue,
      espOpacity: typeof held.espOpacity === "number" ? clampEspOpacity(held.espOpacity) : defaults.espOpacity,
      open: typeof held.open === "boolean" ? held.open : defaults.open,
    };
  }
}
