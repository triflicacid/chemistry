import {
  Box3,
  Color,
  Group,
  Mesh,
  MeshBasicMaterial,
  MeshStandardMaterial,
  Object3D,
  Raycaster,
  Vector3,
} from "three";
import type { MatchHighlight } from "../../api/types/match";
import type { OptionValues } from "../../api/types/options";
import type { CameraPose, Conjugation3D, Connection3D, Diagnostics3D, Site3D, Structure3D, Vec3 } from "../../api/types/structure";
import { AXIS_LENGTH, makeAxes } from "../../render/axes";
import { aromaticCentres } from "../../render/aromatic";
import { ballRadius, buildBond, LONE_ATOM_RADIUS, type Style } from "../../render/bonds";
import { disposeMaterials } from "../../render/dispose";
import { applyTints, HOVER_TINT, SELECTED_TINT } from "../../render/emphasis";
import { BOND_HALO, fade, HALO_GROWTH, HALO_PADDING, haloMaterial } from "../../render/halo";
import { angleArc, piCloud, piLabel, piOrbitals } from "../../render/overlays";
import { type Part, PartRegistry, type Pick } from "../../render/parts";
import { Primitives } from "../../render/primitives";
import { FIELD_OF_VIEW, Stage } from "../../render/stage";
import type { FaultSeverity, FaultState, Highlights, MoleculeView, SelectionState, ViewListener } from "./MoleculeView";

/** The matches of a SMARTS pattern to show in a view. */
export interface MatchLook {
  matches: MatchHighlight[];
  /** Whether the matches are all shown together, so an atom may be in several. */
  merged: boolean;
  dim: number;
  recolour: boolean;
  labels: boolean;
}

interface Settings {
  style: Style;
  hydrogens: boolean;
  charges: boolean;
  axes: boolean;
  debugBonds: boolean;
  debugAngles: boolean;
  debugTorsions: boolean;
  debugClashes: boolean;
  debugStrain: boolean;
  debugLonePairs: boolean;
  debugPiSystems: boolean;
  debugPiOrbitals: boolean;
  debugThreshold: number;
}

const END_COLOURS = [new Color("#ffd33d"), new Color("#79c0ff"), new Color("#ffa657")];
/** The colours the findings list gives each severity, so a mark in the drawing matches the row it came from. */
const FAULT_COLOURS: Record<FaultSeverity, Color> = {
  error: new Color("#cf222e"),
  warning: new Color("#9a6700"),
  info: new Color("#0969da"),
};
const STRAIN = new Color("#e02020");
const STRAIN_SATURATES_AT = 6;

const DEFAULTS: Settings = {
  style: "ball_and_stick",
  hydrogens: true,
  charges: true,
  axes: false,
  debugBonds: false,
  debugAngles: false,
  debugTorsions: false,
  debugClashes: false,
  debugStrain: false,
  debugLonePairs: false,
  debugPiSystems: false,
  debugPiOrbitals: false,
  debugThreshold: 1,
};

function settingsOf(options: OptionValues): Settings {
  const flag = (name: string, fallback: boolean) => (typeof options[name] === "boolean" ? (options[name] as boolean) : fallback);
  const style = options.style;
  return {
    style: typeof style === "string" ? (style as Style) : DEFAULTS.style,
    hydrogens: flag("hydrogens", DEFAULTS.hydrogens),
    charges: flag("charges", DEFAULTS.charges),
    axes: flag("axes", DEFAULTS.axes),
    debugBonds: flag("debug_bonds", false),
    debugAngles: flag("debug_angles", false),
    debugTorsions: flag("debug_torsions", false),
    debugClashes: flag("debug_clashes", false),
    debugStrain: flag("debug_strain", false),
    debugLonePairs: flag("debug_lone_pairs", false),
    debugPiSystems: flag("debug_pi_systems", false),
    debugPiOrbitals: flag("debug_pi_orbitals", false),
    debugThreshold: typeof options.debug_threshold === "number" ? options.debug_threshold : DEFAULTS.debugThreshold,
  };
}

export class ThreeView implements MoleculeView {
  readonly element = document.createElement("div");

  private readonly stage: Stage;
  private readonly raycaster = new Raycaster();
  private readonly kit = new Primitives();
  private readonly registry = new PartRegistry();

  private content = new Group();
  private settings: Settings;
  private atomParts = new Map<number, Part>();
  private listeners = new Set<ViewListener>();
  private selection: SelectionState = { atoms: new Set(), bonds: new Set() };
  private highlights: Highlights = { hover: null, ends: [] };
  private faults: FaultState | null = null;
  private matchLook: MatchLook | null = null;
  private radius = 3;
  /** The middle of what is drawn, which the view starts out orbiting. */
  private centre = new Vector3();
  /** Every drawn atom, by site index, with the molecule atom it is or belongs to. */
  private sitePoints = new Map<number, { atom: number; at: Vector3 }>();
  /** The structure site each picked atom stands for, so the hover plaque can look its geometry up. */
  private siteOf = new Map<unknown, number>();
  /** The connection each picked bond stands for, likewise. */
  private linkOf = new Map<unknown, Connection3D>();
  private rightDown: { x: number; y: number } | null = null;
  private axes: Group | null = null;

  constructor(
    private readonly structure: Structure3D,
    options: OptionValues,
    private readonly conformer = 0,
  ) {
    this.settings = settingsOf(options);
    this.element.className = "view3d";

    this.stage = new Stage({ sizedBy: this.element, onMove: () => this.syncAxes() });
    const canvas = this.stage.canvas;
    this.element.append(canvas);

    canvas.addEventListener("pointermove", this.onMove);
    canvas.addEventListener("pointerleave", this.onLeave);
    canvas.addEventListener("pointerdown", this.onDown);
    canvas.addEventListener("pointerup", this.onUp);

    this.build();
    this.resetView();
  }

  setOptions(options: OptionValues): void {
    this.settings = settingsOf(options);
    this.build();
  }

  listen(listener: ViewListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /** Highlights where a SMARTS pattern matches, or clears the highlights if `look` is null. */
  setMatches(look: MatchLook | null): void {
    if (!look && !this.matchLook) {
      return;
    }
    this.matchLook = look;
    this.build();
  }

  setSelection(selection: SelectionState): void {
    this.selection = selection;
    this.refreshEmphasis();
  }

  setHighlights(highlights: Highlights): void {
    this.highlights = highlights;
    this.refreshEmphasis();
  }

  setFaults(faults: FaultState | null): void {
    this.faults = faults;
    this.refreshEmphasis();
  }

  /** What the geometry says about the atom or bond under the pointer */
  describe(target: Pick): [string, string][] {
    const diagnostics = this.structure.conformers[this.conformer]?.diagnostics;
    if (!diagnostics) {
      return [];
    }
    return target.kind === "atom" ? this.describeAtom(diagnostics, target) : this.describeBond(diagnostics, target);
  }

  /** The strain, worst angle, worst clash, lone pairs and pi electrons of the atom under the pointer. */
  private describeAtom(diagnostics: Diagnostics3D, target: Pick): [string, string][] {
    const site = this.siteOf.get(target.handle);
    if (site === undefined) {
      return [];
    }

    const rows: [string, string][] = [];
    if (this.settings.debugStrain) {
      // strain sums squared deviations, so its root is back in the tolerances the report counts in
      rows.push(["Strain", `${Math.sqrt(diagnostics.atom_strain[site] ?? 0).toFixed(1)}× tolerance`]);
    }
    if (this.settings.debugAngles) {
      const worst = ThreeView.worst(
        diagnostics.angles.filter((angle) => angle.middle === site),
        (angle) => Math.abs(angle.actual - angle.ideal),
      );
      if (worst) {
        const at = [worst.first, site, worst.second].map((each) => this.siteName(each)).join(" - ");
        rows.push([
          "Worst angle",
          `${worst.actual.toFixed(1)}° at ${at}, ideal ${worst.ideal.toFixed(1)}° ${ThreeView.gap(worst.actual, worst.ideal, 1, "°")}`,
        ]);
      }
    }
    if (this.settings.debugClashes) {
      const clashes = diagnostics.clashes.filter((clash) => clash.first === site || clash.second === site);
      const worst = ThreeView.worst(clashes, (clash) => clash.contact - clash.distance);
      if (worst) {
        const other = worst.first === site ? worst.second : worst.first;
        rows.push([
          "Worst clash",
          `${worst.distance.toFixed(2)} Å to ${this.siteName(other)}, contact ${worst.contact.toFixed(2)} Å `
            + `(${(worst.contact - worst.distance).toFixed(2)} Å inside)`,
        ]);
      }
    }
    if (this.settings.debugLonePairs) {
      rows.push(["Lone pairs", String(diagnostics.lone_pairs.filter((pair) => pair.node === site).length)]);
    }
    if (this.settings.debugPiSystems || this.settings.debugPiOrbitals) {
      for (const system of diagnostics.conjugations) {
        const orbital = system.orbitals.find((each) => each.node === site);
        if (orbital) {
          rows.push(["Pi electrons lent", String(orbital.electrons)], ["Pi system", ThreeView.systemName(system)]);
        }
      }
    }
    return rows;
  }

  /** The length, worst torsion and pi system of the bond under the pointer. */
  private describeBond(diagnostics: Diagnostics3D, target: Pick): [string, string][] {
    const link = this.linkOf.get(target.handle);
    if (!link) {
      return [];
    }
    const ends = (first: number, second: number) =>
      (first === link.first && second === link.second) || (first === link.second && second === link.first);

    const rows: [string, string][] = [];
    if (this.settings.debugBonds) {
      const length = diagnostics.lengths.find((each) => ends(each.first, each.second));
      if (length) {
        rows.push([
          "Length",
          `${length.actual.toFixed(3)} Å, ideal ${length.ideal.toFixed(3)} Å ${ThreeView.gap(length.actual, length.ideal, 3, " Å")}`,
        ]);
      }
    }
    if (this.settings.debugTorsions) {
      const worst = ThreeView.worst(
        diagnostics.torsions.filter((torsion) => ends(torsion.second, torsion.third)),
        (torsion) => ThreeView.turn(torsion.actual - torsion.ideal),
      );
      if (worst) {
        // a torsion runs round a circle, so it is only ever so many degrees off, never over or under
        rows.push([
          "Worst torsion",
          `${worst.actual.toFixed(1)}° at ${this.siteName(worst.first)} - ${this.siteName(worst.fourth)}, `
            + `ideal ${worst.ideal.toFixed(0)}° (${ThreeView.turn(worst.actual - worst.ideal).toFixed(1)}° off)`,
        ]);
      }
    }
    if (this.settings.debugPiSystems || this.settings.debugPiOrbitals) {
      for (const system of diagnostics.conjugations) {
        if (system.clouds.some((cloud) => ends(cloud.first, cloud.second))) {
          rows.push(["Pi system", ThreeView.systemName(system)]);
        }
      }
    }
    return rows;
  }

  /** What a site is called in the plaque: its element, and the molecule atom it is where it is one. */
  private siteName(site: number): string {
    const found = this.structure.sites[site];
    if (!found) {
      return "?";
    }
    return found.implied ? found.element : `${found.element} ${found.atom}`;
  }

  private static systemName(system: Conjugation3D): string {
    return `${system.aromaticity.toLowerCase()} · ${system.pi_electrons} π`;
  }

  /** How far a measurement sits from its ideal, as "(22.8° over)" or "(0.112 Å under)". */
  private static gap(actual: number, ideal: number, digits: number, unit: string): string {
    const difference = actual - ideal;
    return `(${Math.abs(difference).toFixed(digits)}${unit} ${difference < 0 ? "under" : "over"})`;
  }

  /** The item the measure is largest for, or null if there are none. */
  private static worst<T>(items: T[], measure: (item: T) => number): T | null {
    return items.reduce<T | null>((best, item) => (!best || measure(item) > measure(best) ? item : best), null);
  }

  /** Where the camera is now, so the server can draw what it sees. */
  cameraPose(): CameraPose {
    const vector = ({ x, y, z }: Vector3): Vec3 => ({ x, y, z });
    return {
      eye: vector(this.stage.camera.position),
      target: vector(this.stage.controls.target),
      up: vector(this.stage.camera.up),
      field_of_view: FIELD_OF_VIEW,
    };
  }

  /** The molecule atoms of everything drawn that is inside what the camera sees now. */
  visibleAtoms(): number[] {
    this.stage.camera.updateMatrixWorld();
    const seen = new Set<number>();
    for (const { atom, at } of this.sitePoints.values()) {
      const ndc = at.clone().project(this.stage.camera);
      if (Math.abs(ndc.x) <= 1 && Math.abs(ndc.y) <= 1 && Math.abs(ndc.z) <= 1) {
        seen.add(atom);
      }
    }
    return [...seen];
  }

  readonly imageExtension = "png";

  exportImage(): Promise<Blob> {
    // drawn just before reading, as the canvas is cleared once presented
    this.stage.render();
    return new Promise((resolve, reject) => {
      this.stage.canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Could not make an image"))), "image/png");
    });
  }

  resetView(): void {
    this.stage.frame(this.centre, this.radius);
  }

  dispose(): void {
    const canvas = this.stage.canvas;
    canvas.removeEventListener("pointermove", this.onMove);
    canvas.removeEventListener("pointerleave", this.onLeave);
    canvas.removeEventListener("pointerdown", this.onDown);
    canvas.removeEventListener("pointerup", this.onUp);
    this.clearContent();
    this.kit.dispose();
    this.stage.dispose();
    this.listeners.clear();
  }

  private clearContent(): void {
    disposeMaterials(this.content);
    this.stage.scene.remove(this.content);
  }

  private build(): void {
    this.clearContent();
    this.content = new Group();
    this.registry.clear();
    this.atomParts = new Map();
    this.siteOf = new Map();
    this.linkOf = new Map();

    const { sites, connections } = this.structure;
    const positions = this.structure.conformers[this.conformer]?.positions ?? [];
    const point = (index: number) => new Vector3(...(positions[index] ?? [0, 0, 0]));
    const diagnostics = this.structure.conformers[this.conformer]?.diagnostics;

    const anyHeavy = sites.some((site) => site.element !== "H");
    const shown = sites.map((site) => this.settings.hydrogens || !anyHeavy || site.element !== "H");
    const box = new Box3();
    sites.forEach((site) => shown[site.index] && box.expandByPoint(point(site.index)));
    this.centre = box.isEmpty() ? new Vector3() : box.getCenter(new Vector3());
    this.sitePoints = new Map(
      sites.filter((site) => shown[site.index]).map((site) => [site.index, { atom: site.atom, at: point(site.index) }]),
    );
    const links = connections.filter((link) => shown[link.first] && shown[link.second]);
    const bonded = new Set(links.flatMap((link) => [link.first, link.second]));
    const neighbours = new Map<number, number[]>();
    for (const link of links) {
      neighbours.set(link.first, [...(neighbours.get(link.first) ?? []), link.second]);
      neighbours.set(link.second, [...(neighbours.get(link.second) ?? []), link.first]);
    }

    const look = this.matchLook;
    const atomSets = look?.matches.map((match) => new Set(match.atoms)) ?? [];
    const bondSets = look?.matches.map((match) => new Set(match.bonds)) ?? [];
    const dimmed = (owners: number[]) => (look && look.dim > 0 && owners.length === 0 ? 1 - look.dim : 1);

    let radius = 1;
    const strain = this.settings.debugStrain && diagnostics ? diagnostics.atom_strain : null;
    for (const site of sites) {
      if (!shown[site.index]) {
        continue;
      }
      const size = ballRadius(site.radius, this.settings.style, bonded.has(site.index));
      const at = point(site.index);
      radius = Math.max(radius, at.distanceTo(this.centre) + Math.max(size, this.settings.style === "spacefill" ? 0 : 0.3));
      // an implied hydrogen is highlighted with the atom it hangs off
      const owners = atomSets.flatMap((set, i) => (set.has(site.atom) ? [i] : []));
      owners.forEach((owner, order) => {
        const colour = look?.matches[owner]?.colour ?? "#ffd43b";
        const halo = new Mesh(this.kit.sphereGeometry, haloMaterial(colour));
        halo.position.copy(at);
        halo.scale.setScalar(Math.max(size, LONE_ATOM_RADIUS) + HALO_PADDING + HALO_GROWTH * order);
        halo.renderOrder = -1;
        this.content.add(halo);
      });
      if (size > 0) {
        const colour = new Color(site.colour);
        if (strain) {
          colour.lerp(STRAIN, Math.min(1, (strain[site.index] ?? 0) / STRAIN_SATURATES_AT));
        }
        const first = owners[0];
        if (look?.recolour && first !== undefined) {
          colour.set(look.matches[first]?.colour ?? site.colour);
        }
        const ball = new Mesh(this.kit.sphereGeometry, new MeshStandardMaterial({ color: colour, roughness: 0.5 }));
        ball.position.copy(at);
        ball.scale.setScalar(size);
        fade(ball, dimmed(owners));
        const part = this.registry.add("atom", site.implied ? null : site.atom, { element: site.element, ...this.matchNote(owners) }, ball);
        this.siteOf.set(part.pick.handle, site.index);
        this.atomParts.set(site.index, part);
        this.content.add(ball);
      }
    }

    if (this.settings.style !== "spacefill") {
      const aromaticRings = aromaticCentres(links, point);
      for (const link of links) {
        const owners = bondSets.flatMap((set, i) => (ThreeView.bondMatched(link, sites, set, atomSets[i] ?? new Set()) ? [i] : []));
        const group = buildBond(this.kit, link, sites, this.settings.style, point, neighbours, aromaticRings.get(link));
        if (group) {
          const part = this.registry.add("bond", link.bond >= 0 ? link.bond : null, { order: link.order.toLowerCase(), ...this.matchNote(owners) }, group);
          this.linkOf.set(part.pick.handle, link);
          this.registry.claimChildren(part);
          this.content.add(group);
          fade(group, dimmed(owners));
        }
        owners.forEach((owner, order) => {
          const halo = this.kit.cylinder(
            point(link.first),
            point(link.second),
            BOND_HALO + HALO_GROWTH * order,
            haloMaterial(look?.matches[owner]?.colour ?? "#ffd43b"),
          );
          halo.renderOrder = -1;
          this.content.add(halo);
        });
      }
    }

    if (look?.labels) {
      this.addMatchLabels(look, sites, links, point);
    }

    if (this.settings.charges) {
      for (const site of sites) {
        if (shown[site.index] && site.charge !== 0) {
          const radius = ballRadius(site.radius, this.settings.style, bonded.has(site.index));
          this.content.add(this.kit.labels.chargeBadge(this.content, site.charge, point(site.index), radius));
        }
      }
    }
    if (this.settings.axes) {
      this.addAxes();
    }
    if (diagnostics) {
      this.addDebug(diagnostics, point, shown);
    }

    this.radius = radius + (this.settings.axes ? AXIS_LENGTH / 2 : 0.5);
    this.stage.scene.add(this.content);
    this.refreshEmphasis();
    this.stage.requestRender();
  }

  /** Whether a bond is in a match: the bond to an implied hydrogen is in it if the atom the hydrogen hangs off is. */
  private static bondMatched(link: Connection3D, sites: Site3D[], bonds: Set<number>, atoms: Set<number>): boolean {
    if (link.bond >= 0) {
      return bonds.has(link.bond);
    }
    const first = sites[link.first];
    const heavy = first?.implied ? sites[link.second] : first;
    return heavy !== undefined && atoms.has(heavy.atom);
  }

  /** What a hovered part says of the matches it is in: `match`, as "Matched", or with how many when all are shown together. */
  private matchNote(owners: number[]): Record<string, string> {
    if (owners.length === 0) {
      return {};
    }
    return { match: this.matchLook?.merged ? `Matched (${owners.length})` : "Matched" };
  }

  /** Numbers the atoms and bonds of the matches, as the 2D drawing does. */
  private addMatchLabels(look: MatchLook, sites: Site3D[], links: Connection3D[], point: (index: number) => Vector3): void {
    const atomSites = new Map(sites.filter((site) => !site.implied).map((site) => [site.atom, site.index]));
    const bondLinks = new Map(links.filter((link) => link.bond >= 0).map((link) => [link.bond, link]));
    const add = (text: string, at: Vector3) => {
      const sprite = this.kit.labels.label(text, "#1a1a1a", 0.32);
      sprite.position.copy(at);
      this.content.add(sprite);
    };
    for (const match of look.matches) {
      for (const { index, text } of match.atom_labels) {
        const site = atomSites.get(index);
        if (site !== undefined && this.sitePoints.has(site)) {
          add(text, point(site));
        }
      }
      for (const { index, text } of match.bond_labels) {
        const link = bondLinks.get(index);
        if (link) {
          add(text, point(link.first).add(point(link.second)).multiplyScalar(0.5));
        }
      }
    }
  }

  /** Axes sit on the point the view orbits, so they show where the pivot is. */
  private addAxes(): void {
    this.axes = makeAxes();
    this.syncAxes();
    this.content.add(this.axes);
  }

  private syncAxes(): void {
    this.axes?.position.copy(this.stage.controls.target);
  }

  private addDebug(
    diagnostics: NonNullable<Structure3D["conformers"][number]["diagnostics"]>,
    point: (index: number) => Vector3,
    shown: boolean[],
  ): void {
    const { tolerances } = this.structure;
    const over = (deviation: number, tolerance: number) => deviation / tolerance >= this.settings.debugThreshold;
    const visible = (...atoms: number[]) => atoms.every((atom) => shown[atom]);
    const line = (a: Vector3, b: Vector3, colour: string, width = 0.02) =>
      this.content.add(this.kit.cylinder(a, b, width, new MeshBasicMaterial({ color: colour, depthTest: false })));

    if (this.settings.debugBonds) {
      for (const length of diagnostics.lengths) {
        if (visible(length.first, length.second) && over(Math.abs(length.actual - length.ideal), tolerances.length)) {
          const a = point(length.first);
          const b = point(length.second);
          line(a, b, "#e02020", 0.04);
          const note = this.kit.labels.label(`${length.actual.toFixed(3)} \u00c5`, "#c00000", 0.22);
          note.position.copy(a.clone().add(b).multiplyScalar(0.5));
          this.content.add(note);
        }
      }
    }

    if (this.settings.debugAngles) {
      for (const angle of diagnostics.angles) {
        if (visible(angle.first, angle.middle, angle.second) && over(Math.abs(angle.actual - angle.ideal), tolerances.angle)) {
          const text = `${angle.actual.toFixed(1)}\u00b0`;
          this.content.add(...angleArc(this.kit, point(angle.middle), point(angle.first), point(angle.second), text));
        }
      }
    }

    if (this.settings.debugTorsions) {
      const worst = new Map<string, (typeof diagnostics.torsions)[number]>();
      for (const torsion of diagnostics.torsions) {
        const deviation = ThreeView.turn(torsion.actual - torsion.ideal);
        const key = `${Math.min(torsion.second, torsion.third)}-${Math.max(torsion.second, torsion.third)}`;
        const before = worst.get(key);
        if (visible(torsion.first, torsion.second, torsion.third, torsion.fourth) && over(deviation, tolerances.torsion)
          && (!before || deviation > ThreeView.turn(before.actual - before.ideal))) {
          worst.set(key, torsion);
        }
      }
      for (const torsion of worst.values()) {
        line(point(torsion.first), point(torsion.fourth), "#8e44ad");
        const note = this.kit.labels.label(`${torsion.actual.toFixed(1)}\u00b0`, "#6c3483", 0.22);
        note.position.copy(point(torsion.second).add(point(torsion.third)).multiplyScalar(0.5));
        this.content.add(note);
      }
    }

    if (this.settings.debugClashes) {
      for (const clash of diagnostics.clashes) {
        if (visible(clash.first, clash.second) && over(clash.contact - clash.distance, tolerances.clash)) {
          const a = point(clash.first);
          const b = point(clash.second);
          line(a, b, "#e67e22", 0.03);
          const note = this.kit.labels.label(`${(clash.contact - clash.distance).toFixed(2)} \u00c5`, "#a04000", 0.22);
          note.position.copy(a.clone().add(b).multiplyScalar(0.5));
          this.content.add(note);
        }
      }
    }

    if (this.settings.debugLonePairs) {
      for (const pair of diagnostics.lone_pairs) {
        if (shown[pair.node]) {
          const tip = new Vector3(pair.tip.x, pair.tip.y, pair.tip.z);
          line(point(pair.node), tip, "#2e86c1", 0.015);
          const lobe = new Mesh(this.kit.sphereGeometry, new MeshBasicMaterial({ color: "#2e86c1", transparent: true, opacity: 0.4 }));
          lobe.position.copy(tip);
          lobe.scale.setScalar(0.12);
          this.content.add(lobe);
        }
      }
    }

    if (this.settings.debugPiSystems || this.settings.debugPiOrbitals) {
      for (const system of diagnostics.conjugations) {
        if (this.settings.debugPiSystems) {
          this.content.add(...piCloud(this.kit, system, point, shown));
        }
        if (this.settings.debugPiOrbitals) {
          this.content.add(...piOrbitals(this.kit, system, point, shown));
        }
        const note = piLabel(this.kit, system, point, shown);
        if (note) {
          this.content.add(note);
        }
      }
    }
  }

  private static turn(degrees: number): number {
    const difference = Math.abs(degrees) % 360;
    return difference > 180 ? 360 - difference : difference;
  }

  /** Tints what is hovered, what ends a hovered bond, and what is selected. */
  private refreshEmphasis(): void {
    const tints = new Map<Object3D, Color>();
    // laid down first, so that hovering or selecting a part at fault still tints it as usual
    if (this.faults) {
      const fault = FAULT_COLOURS[this.faults.severity];
      for (const part of this.registry.parts) {
        const faulted = part.index >= 0 && (part.pick.kind === "atom" ? this.faults.atoms : this.faults.bonds).has(part.index);
        if (faulted) {
          tints.set(part.object, fault);
        }
      }
    }
    for (const part of this.registry.parts) {
      const selected = part.index >= 0 && (part.pick.kind === "atom" ? this.selection.atoms : this.selection.bonds).has(part.index);
      if (selected) {
        tints.set(part.object, SELECTED_TINT);
      }
    }
    for (const end of this.highlights.ends) {
      for (const part of this.atomParts.values()) {
        if (part.index === end.atom) {
          tints.set(part.object, END_COLOURS[end.colour] ?? HOVER_TINT);
        }
      }
    }
    const hover = this.highlights.hover?.handle;
    if (hover instanceof Object3D) {
      tints.set(hover, HOVER_TINT);
    }

    applyTints(this.registry.parts, tints);
    this.stage.requestRender();
  }

  private castAt(event: PointerEvent): { part: Part; point: Vector3 } | null {
    const pointer = this.stage.pointer(event);
    return this.registry.cast(this.raycaster, this.stage.camera, pointer);
  }

  private readonly onMove = (event: PointerEvent): void => {
    if (event.buttons !== 0) {
      return;
    }
    const part = this.castAt(event)?.part;
    this.listeners.forEach((listener) => listener.hover?.(part?.pick ?? null, { x: event.clientX, y: event.clientY }));
  };

  private readonly onLeave = (): void => {
    this.listeners.forEach((listener) => listener.hover?.(null, { x: 0, y: 0 }));
  };

  private readonly onDown = (event: PointerEvent): void => {
    this.rightDown = event.button === 2 ? { x: event.clientX, y: event.clientY } : null;
  };

  /** A right-click that is not a pan pivots on the atom nearest what was clicked, or back on the middle if nothing was. */
  private readonly onUp = (event: PointerEvent): void => {
    const down = this.rightDown;
    this.rightDown = null;
    if (event.button !== 2 || !down || Math.hypot(event.clientX - down.x, event.clientY - down.y) > 4) {
      return;
    }
    const hit = this.castAt(event)?.point;
    let nearest: Vector3 | null = null;
    let best = Infinity;
    if (hit) {
      for (const { at } of this.sitePoints.values()) {
        const distance = at.distanceToSquared(hit);
        if (distance < best) {
          best = distance;
          nearest = at;
        }
      }
    }
    this.stage.pivotOn(nearest ?? this.centre);
  };
}
