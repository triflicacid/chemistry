# Molecular Studio: Controls

## Camera (viewport)

| Input | Action |
|---|---|
| Left-drag | Orbit |
| Right-drag | Pan |
| Scroll wheel, or middle-drag | Zoom |
| Touch: one finger / two fingers | Orbit / zoom and pan |

The browser's context menu is disabled over the viewport.

## Selecting

| Input | Action |
|---|---|
| Click an atom or bond | Select it, replacing the selection |
| Shift-, Ctrl- or Cmd-click | Add to the selection, or remove if already selected |
| Click empty space, or press Esc | Clear the selection |
| Hover | Highlight, without selecting |

A click that moves more than 4 px is a camera drag and selects nothing.
The last item selected is the active one, which the Inspector shows.

## Viewport buttons

| Button | Action |
|---|---|
| Frame selection | Centre the camera on the selection, or on everything if nothing is selected |
| Pick whole molecule | When on, clicking or hovering an atom or bond picks the whole molecule it belongs to |
| Axes | Show or hide the scene axes, which sit at the origin |

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
| Click a name under *Bonded to*, *Bonds* or *Joins* | Select that item (modifiers as above) |
| Hover one | Highlight it in the viewport |

Tint colours: yellow is hovered, light blue is active, blue is selected.
