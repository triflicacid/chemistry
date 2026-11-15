import { CanvasTexture, Object3D, Sprite, SpriteMaterial, Vector3 } from "three";

/** Makes text sprites */
export class LabelFactory {
  private readonly textures = new Map<string, CanvasTexture>();

  /**
   * Makes a sprite showing `text`, outlined in white so that it reads against any background.
   *
   * @param text what the sprite says
   * @param colour a CSS colour for the lettering
   * @param height the sprite's height in scene units; its width follows from the text
   * @param occluded whether atoms in front of the sprite hide it
   * @returns a sprite that the caller positions and adds to the scene
   */
  label(text: string, colour: string, height: number, occluded = false): Sprite {
    const texture = this.textureOf(text, colour);
    const image = texture.image as HTMLCanvasElement;
    const sprite = new Sprite(new SpriteMaterial({ map: texture, depthTest: occluded, transparent: true }));
    sprite.scale.set((height * image.width) / image.height, height, 1);
    sprite.renderOrder = 10;
    return sprite;
  }

  /**
   * Makes a charge badge that sits at the upper right of an atom on screen, however the view is turned.
   *
   * @param parent the object the badge will be added to, whose transform maps `at` into the scene
   * @param charge the formal charge, which is never zero
   * @param at where the atom is, in `parent`'s space
   * @param radius the radius the atom is drawn with
   * @returns a sprite that the caller adds to `parent`
   */
  chargeBadge(parent: Object3D, charge: number, at: Vector3, radius: number): Sprite {
    const size = Math.abs(charge);
    const text = (size === 1 ? "" : String(size)) + (charge > 0 ? "+" : "−");
    const sprite = this.label(text, charge > 0 ? "#1f4fd8" : "#c0392b", 0.5, true);
    // anchoring the sprite's lower left at the atom's centre, then pulling the anchor back, puts it at the upper right on screen
    const pull = Math.max(radius, 0.15) * 0.75;
    sprite.center.set(-pull / sprite.scale.x, -pull / sprite.scale.y);
    sprite.position.copy(at);
    const lift = radius * 1.05;
    const toCamera = new Vector3();
    const world = new Vector3();
    sprite.onBeforeRender = (_renderer, _scene, camera) => {
      sprite.position.copy(at);
      parent.localToWorld(world.copy(at));
      toCamera.copy(camera.position).sub(world).normalize();
      parent.worldToLocal(world.copy(world).addScaledVector(toCamera, lift));
      sprite.position.copy(world);
      sprite.updateMatrixWorld();
    };
    return sprite;
  }

  /** Frees every texture made so far. */
  dispose(): void {
    this.textures.forEach((texture) => texture.dispose());
    this.textures.clear();
  }

  private textureOf(text: string, colour: string): CanvasTexture {
    const key = `${text}|${colour}`;
    const cached = this.textures.get(key);
    if (cached) {
      return cached;
    }
    const canvas = document.createElement("canvas");
    const font = "bold 64px sans-serif";
    const context = canvas.getContext("2d") as CanvasRenderingContext2D;
    context.font = font;
    canvas.width = Math.ceil(context.measureText(text).width) + 16;
    canvas.height = 96;
    // resizing a canvas resets its context, so the font is set again
    context.font = font;
    context.textAlign = "center";
    context.textBaseline = "middle";
    context.lineWidth = 12;
    context.strokeStyle = "#ffffff";
    context.lineJoin = "round";
    context.strokeText(text, canvas.width / 2, canvas.height / 2);
    context.fillStyle = colour;
    context.fillText(text, canvas.width / 2, canvas.height / 2);
    const texture = new CanvasTexture(canvas);
    this.textures.set(key, texture);
    return texture;
  }
}
