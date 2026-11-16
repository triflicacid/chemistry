# Molecular Studio: Controls

## Camera (viewport)

| Input | Action |
|---|---|
| Left-drag | Orbit |
| Right-drag | Pan |
| **Camera** tool (toolbar), left-drag | Pan the view. Right-drag orbits instead while it is on, so both are within reach with one button or a trackpad. A click still selects. The cursor is a hand. The tool also shows the camera dials in the lower right: field of view, fly stick and orbit, left to right |
| **Camera** tool, W / S, A / D, E / Q | Fly the camera forward / back, left / right, and up / down, for as long as the key is held, moving the view's target with it so the angle is kept. Shift is three times faster. The speed follows how far the camera is from its target. Ignored while typing in a field |
| **Camera** tool, drag the **fly stick** (the square dial between the field-of-view cone and the orbit sphere) | Hold the ball out from where it was pressed: up and down fly forward and back, left and right fly sideways, faster the further it is pulled. Let go and it stops and the ball returns |
| **Camera** tool, drag the **ring round the fly stick** | Fly up or down: turn it clockwise to rise and counterclockwise to sink, as far from where it was pressed as you like, faster the further it is turned (a third of a turn is full speed). The marker on the ring shows how far it is turned. Let go and it stops and the marker returns to the top |
| Double-click an atom, or right-click it | Bring it to the middle of the view, keeping the angle and distance, and orbit it from now on (the nearest atom to the click, if it lands on a bond). On empty space, go back to the middle of the molecule. The molecule's handles move onto it too when their pivot is the orbit anchor. The second click of a double-click does not also select or deselect |
| Scroll wheel | Zoom towards the point under the pointer, which also moves the view, so a molecule can be brought closer without a pan. The view then orbits around where it has got to, not around the atom last double-clicked; the molecule's handles stay on that atom |
| Drag the **roll ring** (the faint ring round the axes triad, lower left) | Roll the view: it turns about the way you are looking, so the picture turns in the screen's plane about the middle of the view, and follows the pointer round the ring one for one (counterclockwise is counterclockwise), however far from the ring the pointer goes. Nothing in the scene moves and nothing is sent to the server. Double-click the ring to take the roll out, so that up is up again. Orbiting afterwards turns about the tilted up. The ring brightens when the pointer is over it and while it is turned, and is left out of screenshots. **Frame selection**, and a click on a letter of the triad, also take the roll out. A press on the ring does not orbit, select or pick up an atom behind it. There are no keys for it |
| Middle-drag | Zoom |
| Touch: one finger / two fingers | Orbit (pan with the **Camera** tool) / zoom and pan |

## Selecting

| Input | Action |
|---|---|
| Click an unselected atom or bond | Select it, replacing the selection |
| Shift-, Ctrl- or Cmd-click an unselected one | Add it to the selection |
| Click (with or without a modifier) a selected one | Deselect that one only, keeping the rest |
| Click empty space, or press Esc | Clear the selection |
| Hover | Highlight, without selecting |

A click that moves more than 4 px is a camera drag and selects nothing.
The last item selected is the active one, which the Inspector shows.

## Moving atoms

Choose **Move** in the toolbar (**Select** goes back). Then:

| Input | Action |
|---|---|
| Drag an atom | Move it. Dragging empty space still orbits, and a locked atom cannot be picked up |
| Drag a selected atom, with several selected | Move the whole selection rigidly, each atom keeping its place relative to the others. If any of them is locked, nothing moves. An atom outside the selection is dragged alone |
| Release | Drop it. The whole drag is one undo step |
| Esc during a drag | Abandon it; the atom goes back where it was picked up |
| X, Y or Z | Hold the move to that axis |
| Shift+X, Shift+Y or Shift+Z | Hold it to the plane across that axis (yz, xz, xy) |
| The same key again | Let go of the constraint |
| H, or **Carry H** in the viewport | Turn on carrying hydrogens: moving an atom, by dragging it or by the handles, takes the hydrogens bonded to it by the same offset, so their bond lengths and angles stay as they are. Off by default, and a convenience only: nothing is locked, and with it off moving a carbon stretches its C–H bonds. It carries a hydrogen bonded to that atom and to nothing else, however the hydrogen came to be there (written in the SMILES or implied by it, and deuterium and tritium too), but not one shared between two atoms. With several atoms selected each brings its own, none twice. Moving a hydrogen itself carries nothing. A locked hydrogen that would be carried stops the move, as a locked atom in a selection does. What is taken is decided when the atom is picked up |
| Constraint buttons in the viewport | The same, by mouse: Free, X, Y, Z, YZ, XZ, XY |
| Taking hold of a handle (below) | Sets the constraint to match it: an arrow is X, Y or Z, a square is a plane, the middle is Free. The buttons follow, and the keys and a direct drag after it use it |
| Drag an arrow or square on the handles | With atoms selected, handles sit on the active atom, or on the centre of the selected atoms, and carry all of them by the same offset along an axis or within a plane. They follow **World** / **Local** (below). Hidden while a locked atom is selected. Release, and Esc, are as for dragging an atom |

Free movement is in the plane facing the camera. Axes are the scene's in **World** space and the molecule's own
in **Local** space (see below); they differ once the molecule has been turned. The atom you drag is selected, so the Inspector shows its position
as it moves. While you drag, the viewport follows the pointer at once and the session is told about ten
times a second.

## Moving and turning the whole molecule

| Input | Action |
|---|---|
| **Move** tool, drag an arrow or a square on the handles | Carry the molecule along that axis, or within that plane |
| **Rotate** tool, **Molecule** (the choice that Rotate starts on), click an atom | Select its molecule, anchor the view on that atom and make the anchor the pivot, so the rings turn the molecule about that atom. Clicking another atom moves the pivot there |
| **Rotate** tool, **Bonds & angles** | Turns something inside the molecule instead, and picks single atoms and bonds. Click a bond to turn about it (its torsion), or an atom to change an angle at it; see *Inspector* below. The choice is a lozenge in the viewport's right-hand buttons, shown with Rotate. It is the only thing that decides what an atom click means, so it does not matter in what order you pick a tool and an atom |
| **Rotate** tool, drag a ring | Turn the molecule about the pivot, around that axis. The outer ring turns it about the view direction |
| Esc during a drag | Abandon it; the molecule goes back where it was picked up |
| Release | Drop it. The whole drag is one undo step |
| **World** / **Local** (viewport buttons, shown with Move or Rotate) | Measure the handles, and atom drags, along the scene's axes or the molecule's own, which turn with it |
| Molecule's **Position** and **Rotation** in the Inspector | Type where its origin goes, and its turn as angles about x, y and z in degrees. These always act about the molecule's own origin |
| **Pivot** in the Inspector | What the handles turn the molecule about and sit on: the orbit anchor (the default: right-click an atom to put the handles on it, or right-click empty space to go back to the middle of the molecule), the centre of its atoms, the molecule's own origin, or a point typed in the scene's coordinates |

## SMILES bar

| Input | Action |
|---|---|
| Edit the field | Draft a new structure; nothing changes until you rebuild |
| Enter, or **Rebuild structure** | Replace the whole structure with the SMILES. Atom and bond identifiers change and the selection is cleared |
| Esc, or **Reset** | Discard the draft and show the current SMILES again |

## Viewport buttons

The buttons over the viewport are in two groups. The **left** group sets what is shown, while the **right** group acts on the molecule or the view which may change based on mode.

**Left**

| Button | Action |
|---|---|
| Axes | Show or hide the scene axes, which sit on the orbit anchor, a fixed point on the molecule (so they stay with the molecule when you pan or zoom, and move when you re-anchor or move the molecule). Off by default. The small labelled X, Y, Z triad in the lower left is not part of this: it is always shown |
| Grid, and Plane | **Grid** shows **Square** lines, **Polar** nested spheres, or **Off** (the default). **Square** lies in a plane, picked with **Plane** (shown only for Square): XZ, XY, YZ, or **All**, which draws all three at once and reaches only 10 Å so that three crossing sheets do not swamp the molecule. It has a line every angstrom counted from the anchor, a stronger one every 5, and its two central lines coloured as the axes they run along. **Polar** is spherical, so it fills space and has no plane: a sphere every angstrom out to 15 Å, stronger every 5, each drawn as lines of latitude and longitude every 30°, with the poles on the scene's y axis (up) and the six half-axes from the centre in the axes' colours. Both are centred on the orbit anchor, where the axes are drawn (so right-click or double-click an atom to move the grid onto it), with lines along the scene's axes in **World** space and along the molecule's own in **Local** space, where they turn with the molecule. They fade out with distance, are drawn faintly with atoms in front of them, cannot be picked, and appear in screenshots. The spacing, the extents, the major interval and the latitude and longitude angle are constants in `viewport/grid.ts` |
| Strain | Mark what is strained: a red halo on each strained atom (stronger the more strained), each strained bond in red with its length, each strained angle as its two bonds in orange with its size, and a red line between each pair of atoms that are too close. Shown only while the strain figure is current, so it disappears while you drag and comes back when you pause. While it is on, selecting an atom or a bond details its strain in the Inspector |
| Debug | Draw guides for moving, on the selected atom or the one being dragged: arrows both ways along an axis, a faint coloured plane for a plane constraint, or a grey plane facing the camera for free movement. They show only with the Move tool, and are drawn through the molecule. It also draws the plane of an angle (a faint violet sheet through the atom and its two bonds) while the Inspector is showing that angle |
| Screenshot | Download `molecule.png`: what the camera sees at the moment of the click, including any strain marks and handles that are showing, and the axes and the small triad when **Axes** is on (but never the roll ring). Where nothing is drawn the background is transparent. The picture is the size of the canvas |

**Right**

| Button | Action |
|---|---|
| Frame selection | Centre the camera on the selection, or on everything if nothing is selected |
| Pick whole molecule (Select and Move tools only) | When on, clicking or hovering an atom or bond picks the whole molecule it belongs to |

## Outliner

| Input | Action |
|---|---|
| Click a molecule, atom or bond row | Select it (modifiers as above) |
| Ctrl-/Shift-/Cmd-click a molecule that has anything selected in it | Deselect everything in that molecule |
| Click empty space in the panel | Clear the selection |
| Hover a row | Highlight it in the viewport |
| Arrow beside a molecule, or the Atoms / Bonds rows | Expand or collapse |

Selecting something in the viewport opens the Outliner to it and scrolls it into view.

## Inspector

| Input | Action |
|---|---|
| Type in an atom's X, Y or Z field, then Enter or click away | Move the atom there. One edit, which can be undone |
| Type in a bond's **Length** field, then Enter or click away | Set the bond's length. The **Hold** atom (named by its label, such as `C1`) stays put and the other moves along the bond, taking everything beyond it along; **Neither** moves both half each. In a ring only the two atoms move. One edit, which can be undone. Refused if an atom that would move is locked |
| Type in a bond's **Torsion** field, then Enter or click away | Turn one side of the bond about it until the angle between the two **From** atoms is the value typed. **Hold** says which side stays; **Neither** turns both half each. Ring bonds cannot be turned. One edit, which can be undone |
| Type in an atom's **Angle** field, then Enter or click away | Swing one of two bonds at the atom, with everything beyond it, in the plane of the two, until the angle between the **Between** atoms is the value typed. It is a full turn: 0 puts the two on top of each other, 180 in a line, and past 180 the bond swings through the straight line to the other side, so 361 is 1 and -1 is 359. **Hold** says which side stays, and **Neither** swings both half each. The atom's other bonds stay put. One edit, which can be undone. Refused for a bond in a ring or if an atom that would move is locked |
| Select one atom, with Rotate on **Bonds & angles**, and drag the ring at it | Change the angle between the **Between** atoms. One edit, which Esc abandons. Not offered where a bond that would swing is in a ring |
| Esc in a field | Abandon what you typed |
| Select one bond, with the Move tool, and drag the arrow along it | Change its length. The atom shown by **Hold** stays; the arrow is on the atom that moves. Slid past nothing, the length goes negative and the moving side passes through the held atom to the other side. One edit, which Esc abandons |
| Select one bond, with Rotate on **Bonds & angles**, and drag the ring about it | Change its torsion between the **From** atoms. One edit, which Esc abandons. Not offered for a ring bond |
| Click a name under *Bonded to*, *Bonds* or *Joins* | Select that item (modifiers as above) |
| Hover one | Highlight it in the viewport |

## Locking

| Input | Action |
|---|---|
| **Lock** button, or L | Lock the selected atoms, or unlock them if every one of them is locked already |
| Locked checkbox in the Inspector | Lock or unlock that atom |

Tint colours: yellow is hovered, light blue is active, blue is selected.

## Undo and redo

| Input | Action |
|---|---|
| **Undo** button, or Ctrl/Cmd+Z | Take back the latest edit |
| **Redo** button, or Ctrl/Cmd+Shift+Z, or Ctrl/Cmd+Y | Make the latest undone edit again |
| **History** button | Open the list of every edit, newest first, each with the time it was made, ending in **Start**. The current state is marked "current"; states after it (undone edits) are dimmed. The button is off until there is something to list. Esc, or a click outside the list, closes it |
| **Go to** on an entry | Move to that state, keeping the edits after it so they can still be redone |
| **Rewind** on an entry (earlier states only) | Move to that state and permanently discard the later edits, so they cannot be redone. Its tooltip says how many go |
| **Clear history** in the list | Permanently forget every edit, keeping the structure as it is |

