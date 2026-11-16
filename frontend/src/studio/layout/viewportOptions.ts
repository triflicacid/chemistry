import type {RotateTarget, Space} from "../selection/ToolModel";
import type {Constraint} from "../viewport/handles/dragPlane";
import type {GridKind, GridPlaneChoice} from "../viewport/grid";
import type {Segment} from "./Segmented";

export const GRIDS: Segment<GridKind | "off">[] = [
  { value: "off", label: "Off", title: "Show no grid" },
  { value: "cartesian", label: "Square", title: "Lines every angstrom, with a stronger one every five. In Local space it turns with the molecule" },
  { value: "polar", label: "Polar", title: "Spheres every angstrom about the orbit anchor, with lines of latitude and longitude every 30 degrees. It fills space, so it has no plane" },
];

export const GRID_PLANES: Segment<GridPlaneChoice>[] = [
  { value: "xz", label: "XZ", title: "The plane spanned by the x and z axes" },
  { value: "xy", label: "XY", title: "The plane spanned by the x and y axes" },
  { value: "yz", label: "YZ", title: "The plane spanned by the y and z axes" },
  { value: "all", label: "All", title: "All three planes at once, which reach less far" },
];

export const ROTATE_TARGETS: Segment<RotateTarget>[] = [
  { value: "molecule", label: "Molecule", title: "Click an atom to turn the whole molecule about it, with rings that turn it about the pivot" },
  { value: "internal", label: "Bonds & angles", title: "Click a bond to turn one side of it about the bond (its torsion), or an atom to change an angle between two of its bonds" },
];

export const SPACES: Segment<Space>[] = [
  { value: "world", label: "World", title: "Measure moves and handles along the scene's axes" },
  { value: "local", label: "Local", title: "Measure moves and handles along the molecule's own axes, which turn with it" },
];

export const CONSTRAINTS: { value: Constraint; label: string; title: string }[] = [
  { value: "free", label: "Free", title: "Move in the plane facing the camera" },
  { value: "x", label: "X", title: "Move along the x axis (X)" },
  { value: "y", label: "Y", title: "Move along the y axis (Y)" },
  { value: "z", label: "Z", title: "Move along the z axis (Z)" },
  { value: "yz", label: "YZ", title: "Move in the yz plane (Shift+X)" },
  { value: "xz", label: "XZ", title: "Move in the xz plane (Shift+Y)" },
  { value: "xy", label: "XY", title: "Move in the xy plane (Shift+Z)" },
];
