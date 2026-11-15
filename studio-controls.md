# Molecular Studio: Controls

## Camera (viewport)

| Input | Action |
|---|---|
| Left-drag | Orbit |
| Right-drag | Pan |
| **Pan** tool (toolbar), left-drag | Pan the view. Right-drag orbits instead while it is on, so both are within reach with one button or a trackpad. A click still selects. The cursor is a hand |
| Double-click an atom, or right-click it | Bring it to the middle of the view, keeping the angle and distance, and orbit it from now on (the nearest atom to the click, if it lands on a bond). On empty space, go back to the middle of the molecule. The molecule's handles move onto it too when their pivot is the orbit anchor. The second click of a double-click does not also select or deselect |
| Scroll wheel | Zoom towards the point under the pointer, which also moves the view, so a molecule can be brought closer without a pan. The view then orbits around where it has got to, not around the atom last double-clicked; the molecule's handles stay on that atom |
| Middle-drag | Zoom |
| Touch: one finger / two fingers | Orbit (pan with the **Pan** tool) / zoom and pan |

The browser's context menu is disabled over the viewport.

With **Axes** on, the triad in the lower left turns with the camera. Click one of its letters to look at
the molecule from along that axis (X is the view from the right, Y from above, Z from the front). Click
the same letter again, without having turned the camera since, to look from the other side; once the
camera has been turned, the next click starts from the positive side again. The click does not orbit,
select or pick up an atom behind it.

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
| Constraint buttons in the viewport | The same, by mouse: Free, X, Y, Z, YZ, XZ, XY |
| Taking hold of a handle (below) | Sets the constraint to match it: an arrow is X, Y or Z, a square is a plane, the middle is Free. The buttons follow, and the keys and a direct drag after it use it |
| Drag an arrow or square on the handles | With atoms selected, handles sit on the active atom, or on the centre of the selected atoms, and carry all of them by the same offset along an axis or within a plane. They follow **World** / **Local** (below). Hidden while a locked atom is selected. Release, and Esc, are as for dragging an atom |

Free movement is in the plane facing the camera. Axes are the scene's in **World** space and the molecule's own
in **Local** space (see below); they differ once the molecule has been turned. The atom you drag is selected, so the Inspector shows its position
as it moves. While you drag, the viewport follows the pointer at once and the session is told about ten
times a second.

## Moving and turning the whole molecule

Select a molecule (**Pick whole molecule**, or a molecule row in the Outliner), then choose **Move** or **Rotate**.
Handles appear at the pivot. Moving or turning the molecule changes where it sits in the scene and no atom's
coordinates, so every bond length and angle is as it was.

| Input | Action |
|---|---|
| **Move** tool, drag an arrow or a square on the handles | Carry the molecule along that axis, or within that plane |
| **Rotate** tool, click an atom | Select its molecule, anchor the view on that atom and make the anchor the pivot, so the rings turn the molecule about that atom. Clicking another atom moves the pivot there |
| **Rotate** tool, drag a ring | Turn the molecule about the pivot, around that axis. The outer ring turns it about the view direction |
| Esc during a drag | Abandon it; the molecule goes back where it was picked up |
| Release | Drop it. The whole drag is one undo step |
| **World** / **Local** (viewport buttons, shown with Move or Rotate) | Measure the handles, and atom drags, along the scene's axes or the molecule's own, which turn with it |
| Molecule's **Position** and **Rotation** in the Inspector | Type where its origin goes, and its turn as angles about x, y and z in degrees. These always act about the molecule's own origin |
| **Pivot** in the Inspector | What the handles turn the molecule about and sit on: the orbit anchor (the default: right-click an atom to put the handles on it, or right-click empty space to go back to the middle of the molecule), the centre of its atoms, the molecule's own origin, or a point typed in the scene's coordinates |

The molecule's atoms are still dragged with the Move tool when no molecule is selected, or by pressing on an atom
away from a handle. A locked atom does not stop the molecule moving, because a lock holds an atom in place within
the molecule. A session holds one molecule, so selecting one fragment of it and moving it moves them all.

The Inspector's atom positions are in the molecule's own frame, not the scene's.

## SMILES bar

| Input | Action |
|---|---|
| Edit the field | Draft a new structure; nothing changes until you rebuild |
| Enter, or **Rebuild structure** | Replace the whole structure with the SMILES. Atom and bond identifiers change and the selection is cleared |
| Esc, or **Reset** | Discard the draft and show the current SMILES again |

An unreadable SMILES is refused with a message and leaves the structure as it was. If the structure
changed on the server while you were editing, the rebuild is refused and the current structure is shown.

## Viewport buttons

| Button | Action |
|---|---|
| Frame selection | Centre the camera on the selection, or on everything if nothing is selected |
| Pick whole molecule | When on, clicking or hovering an atom or bond picks the whole molecule it belongs to |
| Axes | Show or hide the scene axes, which sit on the orbit anchor, a fixed point on the molecule (so they stay with the molecule when you pan or zoom, and move when you re-anchor or move the molecule), and a small labelled X, Y, Z triad in the lower left that turns with the camera. Off by default |
| Strain | Mark what is strained: a red halo on each strained atom (stronger the more strained), each strained bond in red with its length, each strained angle as its two bonds in orange with its size, and a red line between each pair of atoms that are too close. Shown only while the strain figure is current, so it disappears while you drag and comes back when you pause. While it is on, selecting an atom or a bond details its strain in the Inspector |
| Screenshot | Download `molecule.png`: what the camera sees at the moment of the click, including any strain marks, handles and axes triad that are showing. Where nothing is drawn the background is transparent. The picture is the size of the canvas |
| Debug | Draw guides for moving, on the selected atom or the one being dragged: arrows both ways along an axis, a faint coloured plane for a plane constraint, or a grey plane facing the camera for free movement. They show only with the Move tool, and are drawn through the molecule |

## Strain

The toolbar shows `strain N`: how far the geometry is from ideal bond lengths, angles and contacts, in units of
tolerance, where 0 means nothing is wrong and about 3 is as bad as one bond, angle or contact three tolerances out.
The halos on atoms get larger, brighter and redder as an atom's share rises, and are solid red from about 6. It is worked out on the server about a quarter of a second after you stop changing anything, and
shows `…` after the number while it is catching up. It is not comparable between molecules of different sizes.

The **Geometry** tab of the Activity panel lists the worst things wrong, worst first, as bonds, angles and clashes
with how many tolerances each is out by. Click one to select the atoms it is between. The **Activity** tab is the
log of what the session has done. Torsions are not counted or listed.

With the **Strain** viewport button on, the Inspector adds a Strain section to a selected atom (its share of the
strain, and every bond, angle and clash beyond tolerance that involves it) and to a selected bond (the findings that
involve both its atoms). It says "Working it out…" while the figure is catching up after an edit.

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
| Esc in a field | Abandon what you typed |
| Click a name under *Bonded to*, *Bonds* or *Joins* | Select that item (modifiers as above) |
| Hover one | Highlight it in the viewport |

A field you are typing in is yours: updates from the session do not overwrite it. If the value
changes underneath you its border turns yellow, and leaving the field still applies what you typed.
An atom that is locked cannot be edited.

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

Inside a text field the keys are the field's own. Rebuilding from SMILES clears the history,
because every atom and bond identifier it refers to is retired.

## Saving

There is no Save button: every committed edit, and every undo and redo, is saved as it happens. The
first one stores a new molecule and the status in the toolbar reads "edits saved". The molecule you
opened is never overwritten. The page's URL is rewritten to name the saved molecule, so reloading
opens your edits. Molecules are held in the server's memory, so a restart loses them; the URL's
SMILES then reopens the structure as it was first built, without your edits.
