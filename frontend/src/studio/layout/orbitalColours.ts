/** The colour of an orbital that is all sigma. */
export const SIGMA_COLOUR = "#1baf7a";

/** The colour of an orbital that is all pi. */
export const PI_COLOUR = "#eb6834";

/** The colour of an orbital that is neither, because the atoms are not flat enough for it to be one or the other. */
export const NEUTRAL_COLOUR = "#8a8f98";

/**
 * @param hex a colour as `#rrggbb`
 * @returns its red, green and blue, each from 0 to 255
 */
function channels(hex: string): [number, number, number] {
  return [1, 3, 5].map((at) => parseInt(hex.slice(at, at + 2), 16)) as [number, number, number];
}

/**
 * The colour an orbital is drawn in when it is coloured by its character.
 *
 * @param pi how much of the orbital is pi, from zero to one, or null if the atoms are not flat enough for it to be sigma or pi
 * @returns the colour, as `#rrggbb`
 */
export function characterColour(pi: number | null): string {
  if (pi === null) {
    return NEUTRAL_COLOUR;
  }

  const share = Math.min(1, Math.max(0, pi));
  const [from, to] = [channels(SIGMA_COLOUR), channels(PI_COLOUR)];
  return `#${from.map((channel, i) => Math.round(channel + ((to[i] as number) - channel) * share).toString(16).padStart(2, "0")).join("")}`;
}

/** The colour of the shell of an orbital that holds half of it, where the electron is most likely to be. */
export const CORE_SHELL = "#d9480f";

/** The colour of the shell that holds the share asked for. */
export const OUTER_SHELL = "#fab005";

/** The colour of the part of an orbital that is positive. */
export const POSITIVE_LOBE = "#2f6fe4";

/** The colour of the part that is negative. */
export const NEGATIVE_LOBE = "#e0453a";

/** The colour of the shell that holds half of the empty orbitals, where an electron put in them would most likely go. */
export const EMPTY_CORE_SHELL = "#0b7285";

/** The colour of the empty orbitals' shell that holds the share asked for. */
export const EMPTY_OUTER_SHELL = "#66d9e8";

/** The colour of a filled orbital at the gap, the HOMO, and of one far below it. */
const FILLED_NEAR = "#d9480f";
const FILLED_FAR = "#ffd8a8";

/** The colour of an empty orbital at the gap, the LUMO, and of one far above it. */
const EMPTY_NEAR = "#0b7285";
const EMPTY_FAR = "#99e9f2";

/** How far from the gap, in electronvolts, an orbital is as faint as it gets when it is coloured by its energy. */
export const ENERGY_REACH_EV = 8;

/**
 * The colour an orbital is drawn in when it is coloured by its energy: warm for the orbitals that hold electrons and cool for those that do
 * not, as vivid as can be at the gap, which is where the HOMO and the LUMO are, and fainter the further from it.
 *
 * @param occupied whether the orbital holds electrons
 * @param fromGapEv how far it is from the gap, in electronvolts: below the HOMO if it is filled, above the LUMO if it is not
 * @returns the colour, as `#rrggbb`
 */
export function energyColour(occupied: boolean, fromGapEv: number): string {
  const [near, far] = occupied ? [channels(FILLED_NEAR), channels(FILLED_FAR)] : [channels(EMPTY_NEAR), channels(EMPTY_FAR)];
  const share = Math.min(1, Math.max(0, fromGapEv / ENERGY_REACH_EV));
  return `#${near.map((channel, i) => Math.round(channel + ((far[i] as number) - channel) * share).toString(16).padStart(2, "0")).join("")}`;
}

/**
 * @returns a CSS gradient that runs along the energy axis the colours of an orbital's energy stand for: pale orange far below the gap, vivid
 *     orange at the HOMO, vivid teal at the LUMO and pale teal far above, to draw a key with
 */
export function energyGradient(): string {
  return `linear-gradient(to right, ${FILLED_FAR}, ${FILLED_NEAR} 48%, ${EMPTY_NEAR} 52%, ${EMPTY_FAR})`;
}

/** The colours of the first types of filled orbital, from the HOMO down, which are warm so that they are not taken for the empty ones. */
const FILLED_TYPES: readonly string[] = ["#e8590c", "#f59f00", "#c2255c", "#862e9c", "#5c940d"];

/** The colours of the first types of empty orbital, from the LUMO up, which are cool. */
const EMPTY_TYPES: readonly string[] = ["#1971c2", "#0ca678", "#7048e8", "#15aabf", "#364fc7"];

/** The range of hues, in degrees, the types beyond those with a colour above are given, warm for the filled and cool for the empty. */
const FILLED_HUES: readonly [number, number] = [-35, 60];
const EMPTY_HUES: readonly [number, number] = [165, 280];

/** How far a spin-down orbital's colour is taken towards white from its type's, so that the two spins of one type can be told apart. */
const SPIN_DOWN_TINT = 0.5;

/** The colours of the classes of orbital: the HOMO, the LUMO, and the others that hold electrons and that do not. */
const HOMO_CLASS = "#e8590c";
const LUMO_CLASS = "#1971c2";
const OTHER_FILLED_CLASS = "#f4b183";
const OTHER_EMPTY_CLASS = "#9cc9e8";

/** What colours an orbital by its place relative to the gap: by each place, or by the class of the place. */
export type RoleColouring = "type" | "class";

/**
 * @param colouring a way of colouring orbitals
 * @returns whether it colours an orbital by its place relative to the gap
 */
export function isRole(colouring: string): colouring is RoleColouring {
  return colouring === "type" || colouring === "class";
}

/**
 * @param hue a hue in degrees, which may be negative or beyond a turn
 * @param lightness how light, from 0 to 1
 * @returns that hue at a strong saturation, as `#rrggbb`
 */
function fromHue(hue: number, lightness: number): string {
  const saturation = 0.68;
  const h = (((hue % 360) + 360) % 360) / 60;
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
  const x = chroma * (1 - Math.abs((h % 2) - 1));
  const [r, g, b] = h < 1 ? [chroma, x, 0] : h < 2 ? [x, chroma, 0] : h < 3 ? [0, chroma, x] : h < 4 ? [0, x, chroma] : h < 5 ? [x, 0, chroma] : [chroma, 0, x];
  const m = lightness - chroma / 2;
  return `#${[r, g, b].map((channel) => Math.round((channel + m) * 255).toString(16).padStart(2, "0")).join("")}`;
}

/**
 * @param place how far from the gap the type is, counting from zero for the HOMO or the LUMO
 * @param palette the colours of the first types on that side
 * @param hues the range of hues to take the rest from
 * @returns the colour of the type, which is one of the palette's if it has one and otherwise a hue picked from the range so that no two are alike
 */
function typeOf(place: number, palette: readonly string[], hues: readonly [number, number]): string {
  const given = palette[place];
  if (given !== undefined) {
    return given;
  }
  // the golden ratio spreads successive places across the range, and every other one is darker, so that neighbours do not look alike
  const spread = (place * 0.6180339887) % 1;
  return fromHue(hues[0] + spread * (hues[1] - hues[0]), place % 2 === 0 ? 0.42 : 0.58);
}

/**
 * @param colour a colour as `#rrggbb`
 * @param spin which set of orbitals the orbital is of
 * @returns the colour, taken towards white for a spin-down orbital
 */
function forSpin(colour: string, spin: string): string {
  if (spin !== "beta") {
    return colour;
  }
  return `#${channels(colour).map((channel) => Math.round(channel + (255 - channel) * SPIN_DOWN_TINT).toString(16).padStart(2, "0")).join("")}`;
}

/**
 * The colour an orbital is drawn in when it is coloured by its type, which is its place relative to the gap: the HOMO, the one below it, the
 * LUMO and each one beyond has a colour of its own, warm for those that hold electrons and cool for those that do not. The first of each
 * are chosen to be far apart, and the rest are picked from a range of hues, so that no two types are alike however far from the gap. The
 * spin-down orbital of an unrestricted calculation has the paler colour of its type.
 *
 * @param offset how far above the highest orbital that holds electrons it is: zero for that orbital, one for the LUMO, minus one for the one below the HOMO
 * @param spin which set of orbitals it is of
 * @returns the colour, as `#rrggbb`
 */
export function typeColour(offset: number, spin: string): string {
  const colour = offset <= 0 ? typeOf(-offset, FILLED_TYPES, FILLED_HUES) : typeOf(offset - 1, EMPTY_TYPES, EMPTY_HUES);
  return forSpin(colour, spin);
}

/**
 * The colour an orbital is drawn in when it is coloured by its class: the HOMO, the LUMO, and the others that hold electrons or that do
 * not, which are four colours, so that the HOMO and the LUMO stand out from everything else. The spin-down orbital has the paler one.
 *
 * @param offset how far above the highest orbital that holds electrons it is
 * @param spin which set of orbitals it is of
 * @returns the colour, as `#rrggbb`
 */
export function classColour(offset: number, spin: string): string {
  return forSpin(offset === 0 ? HOMO_CLASS : offset === 1 ? LUMO_CLASS : offset < 0 ? OTHER_FILLED_CLASS : OTHER_EMPTY_CLASS, spin);
}

/**
 * @param colouring whether to colour by type or by class
 * @param offset how far above the highest orbital that holds electrons the orbital is
 * @param spin which set of orbitals it is of
 * @returns its colour, as `#rrggbb`
 */
export function roleColour(colouring: RoleColouring, offset: number, spin: string): string {
  return colouring === "class" ? classColour(offset, spin) : typeColour(offset, spin);
}

/**
 * @param offset how far above the highest orbital that holds electrons the orbital is
 * @returns the name of its class: the HOMO, the LUMO, or the others that hold electrons or do not
 */
export function className(offset: number): string {
  return offset === 0 ? "HOMO" : offset === 1 ? "LUMO" : offset < 0 ? "other filled" : "other empty";
}
