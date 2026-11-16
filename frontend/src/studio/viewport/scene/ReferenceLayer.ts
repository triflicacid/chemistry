import {type LineBasicMaterial, type LineSegments, Group, Quaternion, type Vector3} from "three";
import {makeAxes} from "../../../render/axes";
import {disposeMaterials} from "../../../render/dispose";
import type {Stage} from "../../../render/stage";
import type {Space} from "../../models/ToolModel";
import {type Constraint} from "../handles/dragPlane";
import {type Guide, makeGuide} from "../handles/moveGuides";
import {buildGrid, type GridKind, type GridPlaneChoice} from "../grid";
import type {AtomNode, MoleculeScene} from "./MoleculeScene";

export interface ReferenceHost {
  /** Whether constraints, and the molecule's handles, follow the scene's axes or the molecule's. */
  space(): Space;
  /** What a dragged atom is held to. */
  constraint(): Constraint;
  /** The point the view was last anchored on, in the molecule's own frame. */
  anchor(): Vector3;
  /** The atom the move guide is drawn on, or null for none. */
  guideTarget(): AtomNode | null;
  /** Whether a guide is wanted at all: debugging is on, atoms can be moved, and there is something to move. */
  guideWanted(): boolean;
}

export class ReferenceLayer {
  readonly axes = makeAxes();
  /** Turns a guide to the frame it is drawn in: the scene's, the molecule's, or the camera's. */
  readonly guideFrame = new Group();

  /** The grid being shown, or null while there is none. */
  private grid: LineSegments | null = null;
  private guide: Guide | null = null;

  constructor(
    private readonly stage: Stage,
    private readonly scene: MoleculeScene,
    private readonly host: ReferenceHost,
  ) {}

  /** Puts the axes and the grid where they belong, which is where the anchor and the molecule now are. */
  placeReferences(): void {
    this.placeAxes();
    this.placeGrid();
  }

  /**
   * Shows a grid in one of the scene's planes, or in all three, or takes it away.
   *
   * The grid is centred on the orbit anchor, where the axes are drawn.
   * In **World** space its lines run along the scene's axes; in **Local** space they turn with the molecule.
   *
   * @param kind the grid to show, or {@code null} for none
   * @param plane the plane a square grid lies in, or all three; a polar grid ignores it
   */
  setGrid(kind: GridKind | null, plane: GridPlaneChoice): void {
    if (this.grid) {
      this.stage.scene.remove(this.grid);
      this.grid.geometry.dispose();
      (this.grid.material as LineBasicMaterial).dispose();
      this.grid = null;
    }

    if (kind) {
      this.grid = buildGrid(kind, plane);
      this.stage.scene.add(this.grid);
      this.placeGrid();
    }
    this.stage.requestRender();
  }

  /** Moves the grid onto the anchor, and turns it with the molecule if the space is the molecule's own. */
  placeGrid(): void {
    const grid = this.grid;
    if (!grid) {
      return;
    }

    const { content } = this.scene;
    content.updateMatrixWorld(true);
    const centre = content.localToWorld(this.host.anchor().clone());
    grid.position.copy(centre);
    grid.quaternion.copy(this.host.space() === "local" ? content.quaternion : new Quaternion());
    this.stage.requestRender();
  }

  /** Puts the axes on the anchor. */
  private placeAxes(): void {
    const { content } = this.scene;
    content.updateMatrixWorld(true);
    this.axes.position.copy(content.localToWorld(this.host.anchor().clone()));
    this.stage.requestRender();
  }

  /**
   * Shows or hides the scene axes.
   *
   * @param visible whether to show them
   */
  setAxes(visible: boolean): void {
    this.axes.visible = visible;
    this.stage.requestRender();
  }

  /** Draws the guide for the current constraint afresh, or removes it when there is nothing for it to show. */
  refreshGuide(): void {
    if (this.guide) {
      this.guideFrame.remove(this.guide.group);
      this.guide.dispose();
      this.guide = null;
    }

    if (this.host.guideWanted()) {
      this.guide = makeGuide(this.host.constraint());
      this.guideFrame.add(this.guide.group);
      this.placeGuide();
    }
    this.stage.requestRender();
  }

  /** Moves the guide onto its atom, and turns a camera-facing one to face the camera. */
  placeGuide(): void {
    const target = this.host.guideTarget();
    if (!this.guide || !target) {
      return;
    }

    // the guide is drawn in the frame the constraint is measured in:
    // the camera's for free movement, otherwise the molecule's own axes or the scene's
    this.guideFrame.position.copy(this.scene.worldOf(target));
    if (this.guide.facesCamera) {
      this.guideFrame.quaternion.copy(this.stage.camera.quaternion);
    } else if (this.host.space() === "local") {
      this.guideFrame.quaternion.copy(this.scene.content.quaternion);
    } else {
      this.guideFrame.quaternion.identity();
    }
    this.stage.requestRender();
  }

  /** Releases the guide, the axes and the grid. */
  dispose(): void {
    this.guide?.dispose();
    disposeMaterials(this.axes);
    this.setGrid(null, "xz");
  }
}
