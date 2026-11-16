/** The side of the square each HUD dial in the canvas's corners is drawn in, in CSS pixels. */
export const HUD_SIZE = 110;

/** How far the dials are from the canvas's edges, in CSS pixels. */
export const HUD_MARGIN = 24;

/** The gap between neighbouring dials in a row, in CSS pixels. */
export const HUD_GAP = 8;

/** How far the middle of the axes triad, which the roll ring goes round, is from the canvas's left and bottom edges. */
export const HUD_CENTRE = HUD_MARGIN + HUD_SIZE / 2;

/** The radius of the orbit sphere, in CSS pixels. */
export const SPHERE_RADIUS = 34;

/** Half the side of the fly stick's square track, in CSS pixels. It is small enough for the lift ring to stand clear of it. */
export const FLY_TRACK = 30;

/** The radius of the fly stick's ball, in CSS pixels. */
export const BALL_RADIUS = 12;

/** How far the fly stick's ball can be from the centre along either axis, which keeps it inside its track. */
export const BALL_TRAVEL = FLY_TRACK - BALL_RADIUS;

/** How far from the fly stick's centre its arrowheads are, in CSS pixels. */
export const FLY_ARROW_AT = FLY_TRACK + 9;

/** The radius of the ring round the fly stick that lifts the camera, in CSS pixels. It is as wide as the dial's square allows. */
export const LIFT_RING = 48;

/** How far, in radians, the ring is turned from the top for full speed up or down. */
export const LIFT_RANGE = Math.PI / 3;

/** How far, in radians, each side of the top of the ring the up and down marks are. */
export const LIFT_MARK_AT = Math.PI / 6;

/** How far from a sphere's centre its arrowheads are, in CSS pixels. */
export const ARROW_AT = SPHERE_RADIUS + 13;

/** How far from an arrowhead's middle a press may be and still take hold of it, in CSS pixels. */
export const ARROW_REACH = 11;
