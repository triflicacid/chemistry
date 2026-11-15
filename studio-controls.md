# Molecular Studio: Controls

## Camera (viewport)

| Input | Action |
|---|---|
| Left-drag | Orbit |
| Right-drag | Pan |
| Right-click an atom | Orbit that atom from now on (the nearest atom to the click, if it lands on a bond). Right-click empty space to go back to orbiting the middle of the molecule |
| Scroll wheel, or middle-drag | Zoom |
| Touch: one finger / two fingers | Orbit / zoom and pan |

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

Free movement is in the plane facing the camera. Axes are the world's: with no molecule transform yet,
there is no local space to choose. The atom you drag is selected, so the Inspector shows its position
as it moves. While you drag, the viewport follows the pointer at once and the session is told about ten
times a second.

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
| Axes | Show or hide the scene axes, which sit on the point the view orbits (so they move when you re-anchor or pan), and a small labelled X, Y, Z triad in the lower left that turns with the camera. Off by default |
| Debug | Draw guides for moving, on the selected atom or the one being dragged: arrows both ways along an axis, a faint coloured plane for a plane constraint, or a grey plane facing the camera for free movement. They show only with the Move tool, and are drawn through the molecule |

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
