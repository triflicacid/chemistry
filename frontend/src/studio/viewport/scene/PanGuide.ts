import {
  ArrowHelper,
  BufferGeometry,
  CanvasTexture,
  CircleGeometry,
  DoubleSide,
  Float32BufferAttribute,
  Group,
  Line,
  LineBasicMaterial,
  LineSegments,
  Mesh,
  MeshBasicMaterial,
  Sprite,
  SpriteMaterial,
  Vector3,
} from "three";
import type {Stage} from "../../../render/stage";

const COLOUR = 0x1f9d8b;

/** How big the guide is, as a fraction of how far the camera is from what it looks at. */
const SCALE = 0.25;

/** How tall the label is, as a fraction of how far the camera is. */
const LABEL_HEIGHT = 0.04;

const LABEL_WIDTH_PX = 512;
const LABEL_HEIGHT_PX = 80;

/* What debugging shows while the view is panned. */
export class PanGuide {
  private readonly root = new Group();
  /** The cross at the orbit point, which is shown whenever the guide is. */
  private readonly pivot = new Group();
  /** The plane and its arrows, which turn to face the camera and are shown only while a pan is dragged. */
  private readonly plane = new Group();
  private readonly trail = new Line(new BufferGeometry(), new LineBasicMaterial({ color: COLOUR, depthTest: false }));
  private readonly canvas = document.createElement("canvas");
  private readonly texture = new CanvasTexture(this.canvas);
  private readonly label = new Sprite(new SpriteMaterial({ map: this.texture, depthTest: false, transparent: true }));

  private enabled = false;
  /** Where the orbit point was when the drag began, or null while there is no drag. */
  private from: Vector3 | null = null;

  /**
   * @param stage the stage the guide is drawn on
   */
  constructor(private readonly stage: Stage) {
    this.canvas.width = LABEL_WIDTH_PX;
    this.canvas.height = LABEL_HEIGHT_PX;
    this.label.scale.set((LABEL_WIDTH_PX / LABEL_HEIGHT_PX) * LABEL_HEIGHT, LABEL_HEIGHT, 1);
    this.label.renderOrder = 10;
    this.trail.renderOrder = 10;
    this.trail.frustumCulled = false;

    const reach = 0.12;
    const cross = new LineSegments(
      new BufferGeometry().setAttribute(
        "position",
        new Float32BufferAttribute([-reach, 0, 0, reach, 0, 0, 0, -reach, 0, 0, reach, 0, 0, 0, -reach, 0, 0, reach], 3),
      ),
      new LineBasicMaterial({ color: COLOUR, depthTest: false }),
    );
    cross.renderOrder = 10;
    this.pivot.add(cross);

    const disc = new Mesh(new CircleGeometry(1, 48), new MeshBasicMaterial({ color: COLOUR, transparent: true, opacity: 0.1, depthTest: false, side: DoubleSide }));
    disc.renderOrder = 9;
    this.plane.add(disc, this.arrow(new Vector3(1, 0, 0)), this.arrow(new Vector3(0, 1, 0)));

    this.root.visible = false;
    this.plane.visible = false;
    this.trail.visible = false;
    this.label.visible = false;
    this.root.add(this.pivot, this.plane);
    // the trail and its label are in the scene's own units, so they are not scaled with the rest
    stage.scene.add(this.root, this.trail, this.label);
  }

  /**
   * Shows or hides the guide.
   *
   * @param wanted whether debugging is on and the view is being panned
   */
  show(wanted: boolean): void {
    this.enabled = wanted;
    if (!wanted) {
      this.from = null;
    }
    this.update();
  }

  /** Starts a drag, which the guide marks from where the view's orbit point is now. */
  begin(): void {
    this.from = this.stage.controls.target.clone();
    this.update();
  }

  /** Ends a drag, which takes away the plane and the trail. */
  end(): void {
    this.from = null;
    this.update();
  }

  /** Puts the guide where the view's orbit point is, and the trail from where the drag began to there. */
  update(): void {
    const { camera, controls } = this.stage;
    const dragging = this.enabled && this.from !== null;
    this.root.visible = this.enabled;
    this.plane.visible = dragging;
    this.trail.visible = dragging;
    this.label.visible = dragging;

    if (this.enabled) {
      const target = controls.target;
      const scale = camera.position.distanceTo(target) * SCALE;
      this.root.position.copy(target);
      this.root.scale.setScalar(scale);
      this.plane.quaternion.copy(camera.quaternion);

      if (this.from) {
        const delta = target.clone().sub(this.from);
        this.trail.geometry.setFromPoints([this.from, target]);
        this.paint(delta);
        this.label.position.copy(this.from).add(target).multiplyScalar(0.5);
        const height = (scale / SCALE) * LABEL_HEIGHT;
        this.label.scale.set((LABEL_WIDTH_PX / LABEL_HEIGHT_PX) * height, height, 1);
      }
    }
    this.stage.requestRender();
  }

  /** Releases what the guide made. */
  dispose(): void {
    this.stage.scene.remove(this.root, this.trail, this.label);
    this.root.traverse((object) => {
      if (object instanceof Mesh || object instanceof LineSegments || object instanceof Line) {
        object.geometry.dispose();
        (object.material as LineBasicMaterial).dispose();
      }
    });
    this.trail.geometry.dispose();
    (this.trail.material as LineBasicMaterial).dispose();
    this.texture.dispose();
    this.label.material.dispose();
  }

  /** An arrow from the middle of the plane, across or up it. */
  private arrow(direction: Vector3): ArrowHelper {
    const arrow = new ArrowHelper(direction, new Vector3(), 0.9, COLOUR, 0.2, 0.12);
    for (const part of [arrow.line, arrow.cone]) {
      part.renderOrder = 10;
      (part.material as LineBasicMaterial | MeshBasicMaterial).depthTest = false;
    }
    return arrow;
  }

  /** Writes how far the view has been panned on the label. */
  private paint(delta: Vector3): void {
    const context = this.canvas.getContext("2d") as CanvasRenderingContext2D;
    const text = `Δ ${delta.length().toFixed(2)} Å  (${[delta.x, delta.y, delta.z].map((n) => n.toFixed(2)).join(", ")})`;
    context.clearRect(0, 0, LABEL_WIDTH_PX, LABEL_HEIGHT_PX);
    context.font = "bold 40px sans-serif";
    context.textAlign = "center";
    context.textBaseline = "middle";
    context.lineWidth = 8;
    context.lineJoin = "round";
    context.strokeStyle = "#ffffff";
    context.strokeText(text, LABEL_WIDTH_PX / 2, LABEL_HEIGHT_PX / 2);
    context.fillStyle = "#117a6b";
    context.fillText(text, LABEL_WIDTH_PX / 2, LABEL_HEIGHT_PX / 2);
    this.texture.needsUpdate = true;
  }
}
