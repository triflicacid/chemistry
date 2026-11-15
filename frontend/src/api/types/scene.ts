import type { CameraPose } from "./structure";
import type { OptionValues } from "./options";

export interface ScenePoint {
  x: number;
  y: number;
}

/** Attribute values are strings, numbers or lists of numbers; names follow SVG's. */
export type SceneAttributes = Record<string, string | number | number[]>;

export interface SceneGradient {
  id: string;
  from: ScenePoint;
  to: ScenePoint;
  stops: { offset: number; colour: string }[];
}

interface SceneBase {
  attributes: SceneAttributes;
}

export type SceneElement =
  | (SceneBase & { type: "line"; from: ScenePoint; to: ScenePoint })
  | (SceneBase & { type: "circle"; centre: ScenePoint; radius: number })
  | (SceneBase & { type: "rect"; corner: ScenePoint; width: number; height: number })
  | (SceneBase & { type: "polygon"; points: ScenePoint[] })
  | (SceneBase & { type: "text"; at: ScenePoint; runs: { text: string; attributes: SceneAttributes | null }[] })
  | (SceneBase & { type: "group"; children: SceneElement[] })
  | (SceneBase & { type: "title" | "description"; text: string });

export interface GeneratedNotation {
  smiles: string;
  canonical_smiles: string;
  /** Whether the canonical SMILES is the stored molecule's, so its metadata applies to it. */
  matches_source: boolean;
  issues: string[];
}

export interface Scene {
  width: number;
  height: number;
  background: string | null;
  attributes: SceneAttributes;
  gradients: SceneGradient[];
  elements: SceneElement[];
  /** Only a projection has this: the SMILES read back from the 3D structure, or null if it could not be read. */
  generated?: GeneratedNotation | null;
}

/** How to draw a 3D structure flat: from the clearest direction, or exactly as `camera` sees it. */
export interface ProjectionRequest {
  mode: "best" | "force";
  conformer?: number;
  camera?: CameraPose;
  /** Draw only the disconnected parts these molecule atoms are in; every part if absent. */
  atoms?: number[];
  options: OptionValues;
}
