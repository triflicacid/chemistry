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
    return this.cornerBadge(this.label(text, charge > 0 ? "#1f4fd8" : "#c0392b", 0.5, true), parent, at, radius, true);
  }

  /**
   * Makes a padlock badge that sits at the lower right of an atom on screen, however the view is turned.
   *
   * @param parent the object the badge will be added to, whose transform maps `at` into the scene
   * @param at where the atom is, in `parent`'s space
   * @param radius the radius the atom is drawn with
   * @returns a sprite that the caller adds to `parent`
   */
  lockBadge(parent: Object3D, at: Vector3, radius: number): Sprite {
    const texture = this.drawn("lock", 64, 72, drawPadlock);
    const sprite = new Sprite(new SpriteMaterial({ map: texture, depthTest: true, transparent: true }));
    sprite.scale.set(0.2, 0.225, 1);
    sprite.renderOrder = 10;
    return this.cornerBadge(sprite, parent, at, radius, false);
  }

  /**
   * Holds a sprite at a corner of an atom on screen, in front of the atom whichever way the camera looks at it.
   *
   * @param sprite the badge
   * @param parent the object the badge will be added to, whose transform maps `at` into the scene
   * @param at where the atom is, in `parent`'s space
   * @param radius the radius the atom is drawn with
   * @param above whether the badge is at the upper right of the atom, rather than the lower right
   * @returns the sprite
   */
  private cornerBadge(sprite: Sprite, parent: Object3D, at: Vector3, radius: number, above: boolean): Sprite {
    // anchoring the sprite's lower left at the atom's centre, then pulling the anchor back, puts it at the upper right on screen;
    // anchoring its upper left instead puts it at the lower right
    const pull = Math.max(radius, 0.15) * 0.75;
    sprite.center.set(-pull / sprite.scale.x, above ? -pull / sprite.scale.y : 1 + pull / sprite.scale.y);
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

  /**
   * Makes a texture from a drawing, or finds the one already made for the same name.
   *
   * @param name what the drawing is, which is also what it is cached under
   * @param width the texture's width in pixels
   * @param height the texture's height in pixels
   * @param draw paints the drawing onto a context of that size
   * @returns the texture, which the factory owns
   */
  private drawn(name: string, width: number, height: number, draw: (context: CanvasRenderingContext2D, width: number, height: number) => void): CanvasTexture {
    const key = `drawn|${name}`;
    const cached = this.textures.get(key);
    if (cached) {
      return cached;
    }
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    draw(canvas.getContext("2d") as CanvasRenderingContext2D, width, height);
    const texture = new CanvasTexture(canvas);
    this.textures.set(key, texture);
    return texture;
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

/**
 * Paints a closed padlock, as the Outliner's padlock is drawn: an arc shackle over a rounded body. A thin white
 * outline keeps it readable against any background.
 *
 * @param context what to paint on
 * @param width the width of the area, which leaves room for the outline around a drawing 12 units across
 * @param height the height of the area, which leaves room for the outline around a drawing 14 units high
 */
function drawPadlock(context: CanvasRenderingContext2D, width: number, height: number): void {
  context.translate((width - 12 * PADLOCK_SCALE) / 2, (height - 14 * PADLOCK_SCALE) / 2);
  context.scale(PADLOCK_SCALE, PADLOCK_SCALE);
  context.lineCap = "round";
  context.lineJoin = "round";

  const shackle = new Path2D("M3.2 6V4.2a2.8 2.8 0 0 1 5.6 0V6");
  const body = new Path2D();
  body.roundRect(1.2, 6, 9.6, 7, 1.6);

  // the outline goes down first, wider than the drawing
  context.strokeStyle = "#ffffff";
  context.lineWidth = 1.6 + 1.2;
  context.stroke(shackle);
  context.stroke(body);

  context.strokeStyle = "#2b2b2b";
  context.lineWidth = 1.6;
  context.stroke(shackle);
  context.fillStyle = "#2b2b2b";
  context.fill(body);
}

/** How many pixels a unit of the padlock's drawing is. */
const PADLOCK_SCALE = 4;
