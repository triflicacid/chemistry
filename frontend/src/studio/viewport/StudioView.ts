import type {Vec3} from "../../api/types/structure";
import {Primitives} from "../../render/primitives";
import {Stage} from "../../render/stage";
import type {GridKind, GridPlaneChoice} from "./grid";
import {CameraHud} from "./hud/CameraHud";
import {HUD_CENTRE, HUD_MARGIN, HUD_SIZE} from "./hud/constants";
import {RollRing} from "./hud/rollRing";
import {Triad} from "./hud/triad";
import {BondHandleController} from "./handles/BondHandleController";
import {MoleculeHandleController} from "./handles/MoleculeHandleController";
import {CameraRig} from "./camera/CameraRig";
import {CameraLock} from "./camera/CameraLock";
import {CameraHudInteraction} from "./input/CameraHudInteraction";
import {TransformHandleInteraction} from "./input/TransformHandleInteraction";
import {ActiveMode} from "./input/modes/ActiveMode";
import {MoveMode} from "./input/modes/MoveMode";
import {PanMode} from "./input/modes/PanMode";
import {RotateMode} from "./input/modes/RotateMode";
import {SelectMode} from "./input/modes/SelectMode";
import type {ModeHost, ModeOptions, ToolMode} from "./input/modes/ToolMode";
import {PointerRouter} from "./input/PointerRouter";
import {RollRingInteraction} from "./input/RollRingInteraction";
import {TriadInteraction} from "./input/TriadInteraction";
import {Annotations} from "./scene/Annotations";
import {MoleculeScene, structureKeyOf} from "./scene/MoleculeScene";
import type {Emphasis} from "../models/items";
import type {SystemView} from "../geometry/conjugation";
import {ReferenceLayer} from "./scene/ReferenceLayer";
import type {StudioViewListener} from "./StudioViewListener";
import type {GeometryResult, SessionState} from "../session/messages";
import type {AngleView, Pivot, Space, Tool, TorsionView} from "../models/ToolModel";
import type {CameraEdit, CameraState} from "../models/CameraModel";
import {type Binding, dispatchKey} from "../keys/binding";
import {KEYS} from "../keys/keymap";

export type { ScreenPoint, StudioViewListener } from "./StudioViewListener";
export type { MeasurementTarget } from "./handles/BondHandleController";

/** Draws a Studio session on a canvas. */
export class StudioView {
  private readonly stage: Stage;
  private readonly kit = new Primitives();
  private readonly triad = new Triad(this.kit.labels, HUD_SIZE, HUD_MARGIN);
  /** The dial round the triad that rolls the view. */
  private readonly ring = new RollRing(HUD_CENTRE);
  private readonly cameraHud = new CameraHud();
  /** Keeps the camera's own controls off while a handle, sphere, ring or atom is held, whoever holds it. */
  private readonly lock: CameraLock;
  /** The molecule as drawn: its atoms and bonds, tints, locks and picking. */
  private readonly scene: MoleculeScene;
  /** What is marked on the molecule: strain, lengths, torsions and angles. */
  private readonly annotations: Annotations;
  /** The camera, and the anchor the view is held on. */
  private readonly rig: CameraRig;
  /** The scene's axes, the grid and the guide for a move. */
  private readonly reference: ReferenceLayer;
  /** The handles on the molecule as a whole. */
  private readonly molecule: MoleculeHandleController;
  /** The handle on a bond or an angle. */
  private readonly bonds: BondHandleController;
  /** What a press on the viewport does, one for each tool. */
  private readonly modes: { select: SelectMode; move: MoveMode; rotate: RotateMode; pan: PanMode };
  /** Holds the mode in use, which is the one for the tool chosen, and gives the router it, whichever it is. */
  private readonly active: ActiveMode;
  private readonly listeners = new Set<StudioViewListener>();

  /** The session the camera was last framed on, so a new session is framed and an edit to the same one is not. */
  private framedSession: string | null = null;
  /** Hears the canvas's pointer, and offers it to what is in the viewport in order of precedence. */
  private readonly router: PointerRouter;
  /** Whether constraints, and the molecule's handles, follow the scene's axes or the molecule's. */
  private space: Space = "world";

  /**
   * @param canvas the canvas to draw on; it is sized to its CSS box
   */
  constructor(canvas: HTMLCanvasElement) {
    this.stage = new Stage({
      canvas,
      sizedBy: canvas,
      onMove: () => {
        this.reference.placeGuide();
        const camera = this.rig.cameraState();
        const byUser = this.rig.byUser;
        this.listeners.forEach((listener) => listener.camera?.(camera, byUser));
      },
    });
    this.scene = new MoleculeScene(this.stage, this.kit);
    this.lock = new CameraLock(this.stage);
    this.annotations = new Annotations(this.stage, this.kit, this.scene);
    this.rig = new CameraRig(this.stage, this.scene, () => {
      this.reference.placeReferences();
      this.molecule.sync();
    });
    this.reference = new ReferenceLayer(this.stage, this.scene, {
      space: () => this.space,
      constraint: () => this.modes.move.constraint,
      anchor: () => this.rig.anchor,
      guideTarget: () => this.modes.move.guideTarget(),
      guideWanted: () => this.annotations.debug && this.modes.move.guideWanted(),
    });
    // scrolling goes towards what the pointer is over, which also moves the view, so a molecule can be brought closer
    // without a pan. It moves the point the view orbits, but not the anchor the molecule's handles sit on
    this.stage.controls.zoomToCursor = true;
    // off until the viewport says otherwise, so they do not flash on before it does
    this.reference.axes.visible = false;
    this.triad.visible = true;
    this.stage.scene.add(this.scene.content, this.reference.axes, this.reference.guideFrame);

    const emit = (tell: (listener: StudioViewListener) => void): void => this.listeners.forEach(tell);
    this.bonds = new BondHandleController(this.stage, this.scene, this.lock, emit);
    this.molecule = new MoleculeHandleController({
      stage: this.stage,
      scene: this.scene,
      rig: this.rig,
      lock: this.lock,
      reference: this.reference,
      space: () => this.space,
      carry: (ids) => this.modes.move.withCarried(ids),
      blocked: () => this.modes.move.selectionLocked(),
      moveAtoms: (moves) => this.moveAtoms(moves),
      emit,
    });

    const host: ModeHost = {
      stage: this.stage,
      scene: this.scene,
      rig: this.rig,
      lock: this.lock,
      reference: this.reference,
      annotations: this.annotations,
      cameraHud: this.cameraHud,
      molecule: this.molecule,
      bonds: this.bonds,
      space: () => this.space,
      moveAtoms: (moves) => this.moveAtoms(moves),
      setPlain: (plain) => this.setPlain(plain),
      emit,
    };
    this.modes = { select: new SelectMode(host), move: new MoveMode(host), rotate: new RotateMode(host), pan: new PanMode(host) };
    this.active = new ActiveMode(this.modes.select);

    this.stage.addOverlay((renderer, camera, capturing) => {
      // the triad is always there to be read, but is only part of a picture of the molecule if the axes are
      if (!capturing || this.reference.axes.visible) {
        this.triad.draw(renderer, camera);
      }
      // the ring is for turning the view, and does not belong in a picture of the molecule
      if (!capturing) {
        this.ring.draw(renderer);
        this.cameraHud.draw(renderer, camera);
      }
    });

    const resting = () => this.mode.resting();
    // in order of precedence: the first to take a press keeps the rest from having it
    this.router = new PointerRouter(canvas, [
      new CameraHudInteraction(this.stage, this.cameraHud, this.rig, this.lock, resting),
      new TriadInteraction(this.stage, this.triad, this.rig),
      new RollRingInteraction(this.stage, this.ring, this.lock, resting),
      new TransformHandleInteraction(this.lock, () => this.molecule.hovered || this.bonds.hovered),
      this.active,
    ]);
    window.addEventListener("keydown", this.onCaptureKey, { capture: true });
    window.addEventListener("keydown", this.onKey);

    this.rig.frameScene();
  }

  /**
   * Chooses what the pointer and the keyboard do in the viewport.
   *
   * @param tool the tool in use
   * @param options the tool's settings and the selection
   */
  setMode(tool: Tool, options: ModeOptions): void {
    this.active.switchTo(this.modes[tool]);
    this.active.mode.configure(options);
  }

  /** The mode in use. */
  private get mode(): ToolMode {
    return this.active.mode;
  }

  /**
   * Shows a session's state.
   *
   * @param state the state to show; the camera is framed on it the first time a session is shown
   */
  setState(state: SessionState): void {
    const structureKey = structureKeyOf(state);
    this.scene.locked = new Set(state.locked);

    // a molecule in the user's hand is where the pointer has put it, and the session is only catching up
    if (!this.molecule.dragging) {
      this.molecule.applyPose(state.transform);
    }
    if (structureKey === this.scene.structureKey) {
      // the atoms in the user's hand are where the pointer has put them, and the session is only catching up
      const held = new Set([...this.mode.held(), ...this.molecule.shifted]);
      this.moveAtoms(state.atoms.filter((atom) => !held.has(atom.id)).map((atom) => [atom.id, atom.position]));
      this.scene.refreshLocks();
      this.reference.refreshGuide();
      this.molecule.sync();
      this.bonds.place();
      return;
    }

    this.mode.reset();
    this.scene.structureKey = structureKey;
    this.scene.clear();
    this.annotations.forget();
    this.scene.build(state);
    this.annotations.drawSystem();
    this.scene.refreshLocks();
    this.reference.refreshGuide();
    if (this.framedSession !== state.session_id) {
      this.framedSession = state.session_id;
      // a session that remembers where the camera was is shown from there, and one that does not is framed on its molecule
      this.rig.placeWhile(() => {
        this.rig.frameScene();
        if (state.camera) {
          this.rig.setCamera(state.camera);
        }
      });
    }
    this.molecule.sync();
    this.bonds.place();
  }

  /**
   * Chooses whether constraints, and the molecule's handles, follow the scene's axes or the molecule's own.
   *
   * @param space which to follow
   */
  setSpace(space: Space): void {
    this.space = space;
    this.reference.placeGrid();
    this.molecule.sync();
    this.reference.refreshGuide();
  }

  /**
   * Chooses the point the molecule's handles sit on and the molecule turns about.
   *
   * @param pivot the point the view orbits, the middle of its atoms, its own origin, or a point
   * @param point where the point is, in the scene, when the pivot is a point
   */
  setPivot(pivot: Pivot, point: Vec3): void {
    this.molecule.setPivot(pivot, point);
  }

  /**
   * Moves one atom, redrawing it and only the bonds its position affects.
   *
   * @param id the atom's identifier
   * @param position where it now is, in angstroms
   */
  moveAtom(id: string, position: Vec3): void {
    this.moveAtoms([[id, position]]);
  }

  /**
   * Moves atoms, redrawing them and only the bonds their positions affect.
   *
   * @param moves each atom's identifier and where it now is; unknown identifiers and atoms already there are skipped
   */
  moveAtoms(moves: Iterable<readonly [string, Vec3]>): void {
    if (!this.scene.moveAtoms(moves)) {
      return;
    }

    this.annotations.drawLengths();
    this.annotations.drawSystem();
    this.bonds.place();
    this.reference.placeGuide();
    this.molecule.followAtoms();
  }

  /**
   * Tints what is selected, active and hovered. Hover shows over active, and active over selected.
   *
   * @param emphasis what to tint; the tints are kept across redraws as atoms move
   */
  setEmphasis(emphasis: Emphasis): void {
    this.scene.emphasis = emphasis;
    this.scene.refreshEmphasis();
    this.annotations.drawLengths();
    this.reference.refreshGuide();
    this.molecule.sync();
  }

  /**
   * Chooses whether what is selected is drawn.
   *
   * @param plain true to draw no tint for the selection, and no lengths or names for it or the hover
   */
  private setPlain(plain: boolean): void {
    if (plain !== this.scene.plain) {
      this.scene.plain = plain;
      this.scene.refreshEmphasis();
      this.annotations.drawLengths();
    }
  }

  /**
   * Marks where the geometry is strained.
   *
   * @param result what the session said of its geometry, or null to show nothing
   */
  setStrain(result: GeometryResult | null): void {
    this.annotations.setStrain(result);
  }

  /**
   * Marks a torsion: the two bonds to the atoms it is measured from, their names, and its angle at the middle of the
   * bond it is about.
   *
   * @param torsion the torsion to mark, or null for none
   */
  setTorsion(torsion: TorsionView | null): void {
    this.annotations.setTorsion(torsion);
  }

  /**
   * Marks an angle: the two bonds, the names of the atoms at their ends, and its size inside it. With debugging on it also
   * draws the plane the angle opens and closes in.
   *
   * @param angle the angle to mark, or null for none
   */
  setAngle(angle: AngleView | null): void {
    this.annotations.setAngle(angle);
  }

  /**
   * Shows pi systems as the viewer does.
   *
   * @param systems the systems to show
   */
  setSystems(systems: readonly SystemView[]): void {
    this.annotations.setSystems(systems);
  }

  /**
   * Takes a picture of what the camera sees, with the markings and handles that are showing.
   *
   * @returns a PNG data URL
   */
  capture(): string {
    return this.stage.capture();
  }

  /**
   * Says where the camera is.
   *
   * @returns its position, what it looks at, how it is turned about that, and its field of view
   */
  cameraState(): CameraState {
    return this.rig.cameraState();
  }

  /**
   * Moves the camera.
   *
   * @param edit what to change; one that would put the camera on what it looks at is ignored
   */
  setCamera(edit: CameraEdit): void {
    this.rig.setCamera(edit);
  }

  /**
   * Brings the middle of the molecule to the middle of the view, which then orbits it and which the molecule's handles
   * sit on, keeping the view's angle and distance.
   */
  centreCamera(): void {
    this.rig.centreCamera();
  }

  /**
   * Orbits the view as a drag on it would.
   *
   * @param dx how far the pointer went across, in pixels
   * @param dy how far it went down, in pixels
   */
  orbitBy(dx: number, dy: number): void {
    this.rig.orbitBy(dx, dy);
  }

  /**
   * Pans the view as a drag on it would.
   *
   * @param dx how far the pointer went across, in pixels
   * @param dy how far it went down, in pixels
   */
  panBy(dx: number, dy: number): void {
    this.rig.panBy(dx, dy);
  }

  /** Puts the camera at the defaults the code has: the default field of view, level, framing the whole molecule. */
  resetCamera(): void {
    this.rig.resetCamera();
  }

  /** Points the camera at the whole scene. */
  frameScene(): void {
    this.rig.frameScene();
  }

  /**
   * Points the camera at some atoms, orbiting their centre.
   *
   * @param ids the atoms to show; if none of them are drawn, the whole scene is framed
   */
  frameAtoms(ids: readonly string[]): void {
    this.rig.frameAtoms(ids);
  }

  /**
   * Anchors the view on an atom, as a right-click on it does, which is where the molecule's handles sit when its pivot
   * is the anchor.
   *
   * @param id the atom's identifier; one that is not drawn is ignored
   */
  anchorOnAtom(id: string): void {
    this.rig.anchorOnAtom(id);
  }

  /**
   * Shows a grid in one of the scene's planes, or in all three, or takes it away.
   *
   * @param kind the grid to show, or {@code null} for none
   * @param plane the plane a square grid lies in, or all three; a polar grid ignores it
   */
  setGrid(kind: GridKind | null, plane: GridPlaneChoice): void {
    this.reference.setGrid(kind, plane);
  }

  /**
   * Shows or hides the scene axes, the arrows at the orbit anchor. The small triad in the corner is always shown.
   *
   * @param visible whether to show them
   */
  setAxes(visible: boolean): void {
    this.reference.setAxes(visible);
  }

  /**
   * Turns the debugging overlays on or off. Today that is the guide for a move: arrows along the axis, or a faint
   * plane, that the atom being moved is held to.
   *
   * @param enabled whether to draw them
   */
  setDebug(enabled: boolean): void {
    this.annotations.debug = enabled;
    this.reference.refreshGuide();
    this.annotations.drawLengths();
  }

  /**
   * Hears about the pointer.
   *
   * @param listener who to tell
   * @returns how to stop listening
   */
  listen(listener: StudioViewListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /** Releases the canvas, the renderer and everything drawn. */
  dispose(): void {
    this.router.dispose();
    window.removeEventListener("keydown", this.onCaptureKey, { capture: true });
    window.removeEventListener("keydown", this.onKey);
    this.scene.clear();
    this.molecule.dispose();
    this.bonds.dispose();
    this.triad.dispose();
    this.ring.dispose();
    this.cameraHud.dispose();
    this.reference.dispose();
    this.kit.dispose();
    this.stage.dispose();
    this.listeners.clear();
  }

  /** Shortcuts heard in the capture phase; see {@link KEYS} for why Escape is among them. */
  private readonly captured: Binding[] = [
    {
      ...KEYS.cancelDrag,
      run: (event) => {
        if (this.molecule.dragging) {
          event.stopPropagation();
          this.molecule.cancel();
        } else if (this.bonds.dragging) {
          event.stopPropagation();
          this.bonds.cancel();
        } else if (this.mode.dragging) {
          // the key abandons the drag and goes no further, so it does not also clear the selection
          event.stopPropagation();
          this.mode.cancelDrag();
        }
      },
    },
  ];

  private readonly onCaptureKey = (event: KeyboardEvent): void => dispatchKey(this.captured, event, true);

  /** The shortcuts of the mode in use are heard once the page has had them, as they were when React bound them. */
  private readonly onKey = (event: KeyboardEvent): void => dispatchKey(this.mode.keys(), event, false);
}
