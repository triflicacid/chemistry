/** The side of the square each HUD dial in the canvas's corners is drawn in, in CSS pixels. */
export const HUD_SIZE = 110;

/** How far the dials are from the canvas's edges, in CSS pixels. */
export const HUD_MARGIN = 24;

/** The gap between neighbouring dials in a row, in CSS pixels. */
export const HUD_GAP = 8;

/** How far the middle of the axes triad, which the roll ring goes round, is from the canvas's left and bottom edges. */
export const HUD_CENTRE = HUD_MARGIN + HUD_SIZE / 2;

/** The radius of the orbit sphere, and half the side of the fly stick's track, in CSS pixels. */
export const SPHERE_RADIUS = 34;

/** The radius of the fly stick's ball, in CSS pixels. */
export const BALL_RADIUS = 12;

/** How far the fly stick's ball can be from the centre along either axis, which keeps it inside its track. */
export const BALL_TRAVEL = SPHERE_RADIUS - BALL_RADIUS;

/** How far from a sphere's centre its arrowheads are, which is also where the ends of the pan arms are, in CSS pixels. */
export const ARROW_AT = SPHERE_RADIUS + 13;

/** How far from an arrowhead's middle a press may be and still take hold of it, in CSS pixels. */
export const ARROW_REACH = 11;
