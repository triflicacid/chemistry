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
    const defaults: PropertiesState = { colourBy: "element", chargeModel: "mulliken", polarLabels: false, polarThreshold: DEFAULT_POLAR_THRESHOLD, open: false };
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
      polarThreshold: typeof held.polarThreshold === "number" ? clampPolarThreshold(held.polarThreshold) : defaults.polarThreshold,
      open: typeof held.open === "boolean" ? held.open : defaults.open,
    };
  }
}
