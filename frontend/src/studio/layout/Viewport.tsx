import {useEffect, useMemo, useRef, useState} from "react";
import type {SystemView} from "../geometry/conjugation";
import {type Item, type PartItem, selectsEveryMolecule} from "../models/items";
import type {Models, PickScope} from "../models/models";
import {useObservable} from "../models/observable";
import type {StateIndex} from "../geometry/StateIndex";
import {heldSide} from "../geometry/bondLength";
import {defaultPair, heldArm} from "../geometry/angle";
import type {GeometryResult, SessionState} from "../session/messages";
import type {SessionPhase} from "../session/types";
import {useSessionContext} from "../session/SessionContext";
import type {GridKind, GridPlaneChoice} from "../viewport/grid";
import {StudioView} from "../viewport/StudioView";
import type {ScreenRect} from "../viewport/StudioViewListener";
import {chargeScale} from "./chargeScale";
import {
  decodeDensity,
  decodeEsp,
  densitySurface,
  espSurface,
  nearestAtomColours,
  sampleGrid,
  signedDensitySurfaces,
  toKcalPerCharge,
  vertexColours
} from "./esp";
import {decodeNci, nciColours, nciSurface} from "./nci";
import {
  dualColours,
  dualSurface,
  frontierTargets,
  isFrontierMap,
  reactivityColour,
  reactivityColours,
  reactivityField,
  reactivitySurface
} from "./reactivity";
import {
  answers,
  type DrawnOrbital,
  drawnOrbitals,
  fragmentsFor,
  isOrbitalSlice,
  resolveTargets,
  sliceTargets
} from "./orbitals";
import {isCurrent} from "./findings";
import {OpenMolecule} from "../controls/OpenMolecule";
import {useStructureActions} from "./useStructureActions";
import {ActionTools, DisplayTools} from "./ViewportOverlay";
import {decodePlane, planeDrawing, planeValueAt, sliceDrawing} from "./slice";
import {orbitalSliceDrawing} from "./orbitalSlice";
import type {SurfacePart} from "../viewport/scene/FieldSurfaceMesh";
import {NEGATIVE_LOBE, POSITIVE_LOBE} from "./orbitalColours";

export interface ViewportProps {
  phase: SessionPhase;
  state: SessionState | null;
  /** What the server last said about the geometry. */
  geometry: GeometryResult | null;
  models: Models;
}

/** What each density is called in a label. */
const FIELD_NAMES = { none: "", density: "density", esp: "density", nci: "density", homoMap: "density", lumoMap: "density", frontierMap: "density", spin: "spin density", difference: "difference density", potential: "potential", homo: "orbital", lumo: "orbital", frontier: "orbital" };

/** No orbitals. */
const NO_ORBITALS: DrawnOrbital[] = [];

/** The colour of a surface of the electron density that is not coloured by anything. */
const DENSITY_COLOUR = "#4aa3a0";

/** The canvas, which fills whatever space the workspace leaves it, and the molecule drawn on it. */
export function Viewport({ phase, state, geometry, models }: ViewportProps) {
  const {
    openSmiles,
    saveCamera,
    dragAtoms,
    dropAtoms,
    cancelDrag,
    dragMolecule,
    dropMolecule,
    cancelMoleculeDrag,
    dragBond,
    dropBond,
    cancelBondDrag,
    addAtom,
    addBond,
    charges,
    forces,
    orbitals,
    fields: orbitalFieldList,
    esp,
    nci,
    density,
    spin,
    difference,
    plane,
  } = useSessionContext();
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [view, setView] = useState<StudioView | null>(null);
  const [axes, setAxes] = useState(false);
  const [grid, setGrid] = useState<GridKind | "off">("off");
  const [gridPlane, setGridPlane] = useState<GridPlaneChoice>("xz");
  const [look, setLook] = useState(false);
  const [selectionArea, setSelectionArea] = useState<ScreenRect | null>(null);
  const [mode, setMode] = useState<PickScope>("item");
  const { selection, hover, tools, index } = models;
  const debug = useObservable(models.debug);
  const toolState = useObservable(tools);
  const { tool, constraint, space, rotateTarget, pivot, point, strain, carryHydrogens, snap, torsion, angle, system, hold, axis, editClick, ringSize, bondOrder } = toolState;
  // turning something inside a molecule is done on single atoms and bonds, whatever else is picked
  const internal = tool === "rotate" && rotateTarget === "internal";
  const picking: PickScope = internal || tool === "edit" ? "item" : mode;
  const structure = useStructureActions(models);
  const selected = useObservable(selection);
  const hovered = useObservable(hover);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }
    const made = new StudioView(canvas);
    setView(made);
    return () => {
      made.dispose();
      setView(null);
    };
  }, []);

  useEffect(() => {
    if (view && state) {
      view.setState(state);
    }
  }, [view, state]);

  useEffect(() => {
    view?.setAxes(axes);
  }, [view, axes]);

  // render options
  const { style, finish, brightness } = useObservable(models.render);
  useEffect(() => {
    view?.setStyle(style);
  }, [view, style]);

  useEffect(() => {
    view?.setFinish(finish);
  }, [view, finish]);

  useEffect(() => {
    view?.setBrightness(brightness);
  }, [view, brightness]);

  useEffect(() => {
    view?.setLookAround(look);
  }, [view, look]);

  useEffect(() => {
    view?.setDebug(debug);
  }, [view, debug]);

  useEffect(() => {
    view?.setGrid(grid === "off" ? null : grid, gridPlane);
  }, [view, grid, gridPlane]);

  // atoms are coloured by what the server last calculated for them. A result a little behind the session is kept, so that
  // the colours do not flicker while an atom is dragged, and the Properties section says that it is updating
  const { colourBy, polarLabels, polarThreshold, forceArrows, forceScale, netForces, surface: surfaceKind, densityColour, surfaceIsovalue, nciLevel, surfaceOpacity, sliceField, sliceAxis, slicePosition, sliceContours } = useObservable(models.properties);
  const charged = useMemo(() => (colourBy === "charge" && charges?.session_id === state?.session_id ? chargeScale(charges) : null), [colourBy, charges, state?.session_id]);
  useEffect(() => {
    view?.colourAtoms(charged?.colours ?? null);
  }, [view, charged]);
  useEffect(() => {
    view?.labelPolarity(polarLabels && charged ? charged.charges : null, polarThreshold);
  }, [view, charged, polarLabels, polarThreshold]);

  // forces are drawn only for the version they were made from, since an arrow that stays where an atom was is worse than none
  const forceable = forceArrows && forces !== null && state !== null && forces.session_id === state.session_id && forces.version === state.version;
  useEffect(() => {
    view?.showForces(forceable ? forces : null, { scale: forceScale, net: netForces, debug });
  }, [view, forceable, forces, forceScale, netForces, debug]);

  // a surface is drawn only for the version it was worked out at, since one that stays where the atoms were is worse than none
  const atVersion = (result: { session_id: string; version: number } | null) =>
    result !== null && state !== null && result.session_id === state.session_id && result.version === state.version;
  const decodedEsp = useMemo(() => (surfaceKind === "esp" && esp && atVersion(esp) ? decodeEsp(esp) : null), [surfaceKind, esp, state]); // eslint-disable-line react-hooks/exhaustive-deps
  const decodedNci = useMemo(() => (surfaceKind === "nci" && nci && atVersion(nci) ? decodeNci(nci) : null), [surfaceKind, nci, state]); // eslint-disable-line react-hooks/exhaustive-deps
  // the density alone, the spin and the difference each come in a message of their own, and are decoded the same way
  const mapSide = surfaceKind === "homoMap" ? "homo" : surfaceKind === "lumoMap" ? "lumo" : null;
  const dual = surfaceKind === "frontierMap";
  const plain = surfaceKind === "density" || mapSide !== null || dual ? density : surfaceKind === "spin" ? spin : surfaceKind === "difference" ? difference : null;
  const decodedDensity = useMemo(() => (plain && atVersion(plain) ? decodeDensity(plain) : null), [plain, state]); // eslint-disable-line react-hooks/exhaustive-deps
  const signed = surfaceKind === "spin" || surfaceKind === "difference";
  // a map of the frontier orbital reads the HOMO or the LUMO of each set where the density surface is, as the Orbitals tab calculates them
  const { scope: mapScope } = useObservable(models.orbital);
  // a plane through a frontier orbital reads them the same way
  const sliceMapSide = sliceField === "homoMap" ? "homo" : sliceField === "lumoMap" ? "lumo" : null;
  const frontierOrbitals = useMemo(() => {
    const read = (side: "homo" | "lumo") =>
      answers(orbitals, models.orbital.get().kind, fragmentsFor(mapScope, state)) ? drawnOrbitals(orbitals, frontierTargets(orbitals, side), orbitalFieldList, state) : [];
    return { homo: mapSide === "homo" || sliceMapSide === "homo" || dual ? read("homo") : [], lumo: mapSide === "lumo" || sliceMapSide === "lumo" || dual ? read("lumo") : [] };
  }, [mapSide, sliceMapSide, dual, mapScope, orbitals, orbitalFieldList, state, models.orbital]);
  const mapOrbitals = mapSide === null ? NO_ORBITALS : frontierOrbitals[mapSide];
  const drawn = useMemo((): SurfacePart[] => {
    if (decodedEsp) {
      const made = espSurface(decodedEsp, surfaceIsovalue);
      return [{ surface: made.surface, colours: vertexColours(made.values, made.limit), colour: DENSITY_COLOUR }];
    }
    if (decodedNci) {
      const made = nciSurface(decodedNci, nciLevel);
      return [{ surface: made.surface, colours: nciColours(made.values), colour: DENSITY_COLOUR }];
    }
    if (!decodedDensity) {
      return [];
    }
    // until the orbitals arrive the density surface is drawn plain, so that something is there to see
    if (dual && frontierOrbitals.homo.length > 0 && frontierOrbitals.lumo.length > 0) {
      const made = dualSurface(decodedDensity, surfaceIsovalue, frontierOrbitals.homo, frontierOrbitals.lumo);
      return [{ surface: made.surface, colours: dualColours(made), colour: DENSITY_COLOUR }];
    }
    if (mapSide !== null && mapOrbitals.length > 0) {
      const made = reactivitySurface(decodedDensity, surfaceIsovalue, mapOrbitals);
      return [{ surface: made.surface, colours: reactivityColours(made.values, made.limit, mapSide), colour: DENSITY_COLOUR }];
    }
    if (signed) {
      const { positive, negative } = signedDensitySurfaces(decodedDensity, surfaceIsovalue);
      return [
        { surface: positive, colours: null, colour: POSITIVE_LOBE },
        { surface: negative, colours: null, colour: NEGATIVE_LOBE },
      ];
    }
    const made = densitySurface(decodedDensity, surfaceIsovalue);
    return [{ surface: made, colours: densityColour === "element" && state ? nearestAtomColours(made, state.atoms) : null, colour: DENSITY_COLOUR }];
  }, [decodedEsp, decodedNci, decodedDensity, signed, surfaceIsovalue, nciLevel, densityColour, mapSide, mapOrbitals, dual, frontierOrbitals, state]);
  useEffect(() => {
    view?.showFieldSurface(drawn, surfaceOpacity);
  }, [view, drawn, surfaceOpacity]);

  // an orbital is drawn only for the version it was worked out at, since a surface that stays where the atoms were is worse than none
  const { selected: shownOrbital, scope: shownScope, count: shownCount, colouring, isovalue, opacity, display: orbitalDisplay, levelBy, share, combine, style: orbitalStyle, sliceOrbital } = useObservable(models.orbital);
  const orbitalFields = useMemo(() => {
    const asked = fragmentsFor(shownScope, state);
    if (shownOrbital === null || !answers(orbitals, models.orbital.get().kind, asked)) {
      return [];
    }
    return drawnOrbitals(orbitals, resolveTargets(orbitals, shownOrbital, shownCount), orbitalFieldList, state);
  }, [shownOrbital, shownScope, shownCount, orbitals, orbitalFieldList, state, models.orbital]);
  useEffect(() => {
    view?.showOrbitals(orbitalFields, { isovalue, opacity, colouring, debug, display: orbitalDisplay, levelBy, share, combine, style: orbitalStyle });
  }, [view, orbitalFields, isovalue, opacity, colouring, debug, orbitalDisplay, levelBy, share, combine, orbitalStyle]);

  // a slice plane shows one of the densities, whether or not a surface of it is drawn, and is drawn only for the version it was worked out at
  const sliced = sliceField === "density" ? density : sliceField === "spin" ? spin : sliceField === "difference" ? difference : null;
  const slicedDensity = useMemo(() => (sliced && atVersion(sliced) ? decodeDensity(sliced) : null), [sliced, state]); // eslint-disable-line react-hooks/exhaustive-deps
  // or a frontier orbital, with its degenerate partners, as a probability
  const sliceFrontier = sliceMapSide === null ? NO_ORBITALS : frontierOrbitals[sliceMapSide];
  const frontierSlice = useMemo(() => reactivityField(sliceFrontier), [sliceFrontier]);
  const decodedSlice = frontierSlice ?? slicedDensity;
  const signedSlice = sliceField === "spin" || sliceField === "difference";
  // or orbitals, which are shown as the Orbitals tab shows them, with the same number from the gap, level, colours and way of showing several,
  // whether or not their surfaces are drawn
  const sliceOrbitals = useMemo(() => {
    if (!isOrbitalSlice(sliceField) || !answers(orbitals, models.orbital.get().kind, fragmentsFor(shownScope, state))) {
      return [];
    }
    return drawnOrbitals(orbitals, sliceTargets(orbitals, sliceField, shownCount), orbitalFieldList, state);
  }, [sliceField, orbitals, shownScope, shownCount, orbitalFieldList, state, models.orbital]);
  const orbitalDrawing = useMemo(
    () =>
      isOrbitalSlice(sliceField)
        ? orbitalSliceDrawing(sliceOrbitals, {
            axis: sliceAxis,
            fraction: slicePosition,
            contours: sliceContours,
            display: orbitalDisplay,
            colouring,
            combine,
            levelBy,
            isovalue,
            share,
            pick: sliceOrbital,
          })
        : null,
    [sliceField, sliceOrbitals, sliceAxis, slicePosition, sliceContours, orbitalDisplay, colouring, combine, levelBy, isovalue, share, sliceOrbital],
  );
  // the potential is worked out for the plane itself, which stays where it was worked out until the next result arrives
  const decodedPlane = useMemo(() => (sliceField === "potential" && plane && atVersion(plane) ? decodePlane(plane) : null), [sliceField, plane, state]); // eslint-disable-line react-hooks/exhaustive-deps
  const slice = useMemo(
    () =>
      orbitalDrawing
        ? orbitalDrawing
        : decodedPlane
          ? planeDrawing(decodedPlane, sliceContours)
          : decodedSlice
          ? sliceDrawing(decodedSlice, sliceAxis, slicePosition, signedSlice, sliceContours, sliceMapSide === null ? undefined : reactivityColour(sliceMapSide))
          : null,
    [orbitalDrawing, decodedPlane, decodedSlice, signedSlice, sliceAxis, slicePosition, sliceContours, sliceMapSide],
  );
  useEffect(() => {
    view?.showSlice(slice);
  }, [view, slice]);

  // with Debug on, the value of the field is read where the pointer is on the surface, or on the slice plane if that is nearer
  const [probe, setProbe] = useState<{ left: number; top: number; lines: string[] } | null>(null);
  const probed = decodedEsp ?? decodedDensity;
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!view || !canvas || !debug || (!((probed || decodedNci) && drawn.length > 0) && !decodedSlice && !decodedPlane && !orbitalDrawing)) {
      setProbe(null);
      return;
    }
    const move = (event: PointerEvent) => {
      const hit = event.buttons === 0 ? view.probeField(event) : null;
      if (!hit) {
        setProbe(null);
        return;
      }

      const { x, y, z } = hit.at;
      let lines: string[] = [];
      if (hit.on === "slice" && orbitalDrawing) {
        const read = orbitalDrawing.read(x, y, z);
        lines = [read ? `${read.name} ${read.value.toFixed(4)} ${read.unit}` : "orbital n/a", `slice at ${sliceAxis} = ${hit.at[sliceAxis].toFixed(2)} Å`];
      } else if (hit.on === "slice" && decodedPlane) {
        const potential = planeValueAt(decodedPlane, x, y, z);
        lines = [`potential ${Number.isNaN(potential) ? "n/a" : `${toKcalPerCharge(potential).toFixed(1)} kcal/mol/e`}`, `slice at ${decodedPlane.axis} = ${hit.at[decodedPlane.axis].toFixed(2)} Å`];
      } else if (hit.on === "slice" && decodedSlice) {
        lines = [`${isFrontierMap(sliceField) ? (sliceField === "homoMap" ? "HOMO" : "LUMO") : FIELD_NAMES[sliceField]} ${sampleGrid(decodedSlice.density, decodedSlice.shape, x, y, z).toFixed(4)} e/bohr³`, `slice at ${sliceAxis} = ${hit.at[sliceAxis].toFixed(2)} Å`];
      } else if (hit.on === "surface" && decodedNci) {
        const signedDensity = sampleGrid(decodedNci.signed, decodedNci.shape, x, y, z);
        const reduced = sampleGrid(decodedNci.gradient, decodedNci.shape, x, y, z);
        lines = [`density ${Number.isNaN(signedDensity) ? "n/a" : Math.abs(signedDensity).toFixed(4)} e/bohr³`, `reduced gradient ${Number.isNaN(reduced) ? "n/a" : reduced.toFixed(3)}`, `curvature ${signedDensity < 0 ? "draws the density together (attractive)" : signedDensity > 0 ? "pushes the density apart (repulsive)" : "none"}`];
      } else if (hit.on === "surface" && probed) {
        lines = [`${FIELD_NAMES[surfaceKind]} ${sampleGrid(probed.density, probed.shape, x, y, z).toFixed(4)} e/bohr³`];
        if (dual) {
          for (const [name, found] of [["HOMO", frontierOrbitals.homo], ["LUMO", frontierOrbitals.lumo]] as const) {
            lines.push(`${name} ${found.reduce((sum, { field }) => sum + sampleGrid(field.values, field.shape, x, y, z) ** 2, 0).toFixed(4)} e/bohr³`);
          }
        }
        if (mapSide !== null && mapOrbitals.length > 0) {
          const there = mapOrbitals.reduce((sum, { field }) => sum + sampleGrid(field.values, field.shape, x, y, z) ** 2, 0);
          lines.push(`${mapSide === "homo" ? "HOMO" : "LUMO"} ${there.toFixed(4)} e/bohr³`);
        }
        if (decodedEsp) {
          const potential = sampleGrid(decodedEsp.potential, decodedEsp.shape, x, y, z);
          lines.push(`potential ${Number.isNaN(potential) ? "n/a" : `${toKcalPerCharge(potential).toFixed(1)} kcal/mol/e`}`);
        }
      }
      const box = canvas.getBoundingClientRect();
      setProbe(lines.length > 0 ? { left: event.clientX - box.left, top: event.clientY - box.top, lines } : null);
    };
    const leave = () => setProbe(null);
    canvas.addEventListener("pointermove", move);
    canvas.addEventListener("pointerleave", leave);
    return () => {
      canvas.removeEventListener("pointermove", move);
      canvas.removeEventListener("pointerleave", leave);
      setProbe(null);
    };
  }, [view, debug, probed, mapSide, mapOrbitals, dual, frontierOrbitals, decodedNci, decodedEsp, decodedSlice, decodedPlane, orbitalDrawing, drawn, surfaceKind, sliceField, sliceAxis]);

  // the hydrogen bond the scan is on is drawn from where its atoms are now, so it is redrawn as they move
  const bond = useObservable(models.hydrogenBond);
  useEffect(() => {
    view?.showHydrogenBond(debug ? bond : null);
  }, [view, debug, bond, state]);

  // molecules are numbered where they are now, so the numbers are redrawn as they move
  const numbered = useObservable(models.numbers);
  useEffect(() => {
    view?.showMoleculeNumbers(debug ? numbered : []);
  }, [view, debug, numbered, state]);

  // a result says where the strain was at the version it was made from, so it is only drawn while the session is still there
  const markable = strain && isCurrent(geometry, state);
  useEffect(() => {
    view?.setStrain(markable ? geometry : null);
  }, [view, markable, geometry]);

  useEffect(() => {
    if (tool === "pan") {
      hover.hover(null);
    }
  }, [tool, hover]);

  // the Inspector shows the camera, and its edits are made here
  const { camera } = models;
  useEffect(() => {
    if (!view) {
      return;
    }
    camera.report(view.cameraState());
    const stopListening = view.listen({
      camera: (now, byUser) => {
        camera.report(now);
        if (byUser) {
          saveCamera({ position: now.position, target: now.target, fov: now.fov, roll: now.roll });
        }
      },
    });
    const stopHandling = camera.handle((edit) => view.setCamera(edit));
    return () => {
      stopListening();
      stopHandling();
    };
  }, [view, camera, saveCamera]);

  // the move tool only tints the atoms, and the rotate tool shows no torsion; the select tool marks them all
  useEffect(() => {
    view?.setTorsion(tool === "move" || tool === "rotate" ? null : torsion);
  }, [view, torsion, tool]);

  useEffect(() => {
    view?.setAngle(tool === "move" ? null : angle);
  }, [view, angle, tool]);

  // the pi systems marked in the viewport: the label of the ring the Inspector shows, and every cloud turned on
  const { chemistry } = models.chemistry;
  useEffect(() => {
    if (!view || !index || !chemistry) {
      view?.setSystems([]);
      return;
    }
    const wanted = new Map<number, SystemView>();
    const want = (id: string, part: "cloud" | "label") => {
      const ring = chemistry.ring(id);
      if (!ring?.system) {
        return;
      }
      const known = wanted.get(ring.system.index);
      if (known) {
        known[part] = true;
        return;
      }
      const bonds = ring.systemBonds.flatMap((bondId) => {
        const bond = index.bonds.get(bondId);
        return bond ? [[bond.first, bond.second] as const] : [];
      });
      wanted.set(ring.system.index, {
        atoms: ring.systemAtoms,
        bonds,
        cloud: part === "cloud",
        label: part === "label",
        piElectrons: ring.system.pi_electrons,
        aromaticity: ring.system.aromaticity.toUpperCase() as SystemView["aromaticity"],
      });
    };
    system.clouds.forEach((id) => want(id, "cloud"));
    if (system.label !== null) {
      want(system.label, "label");
    }
    view.setSystems([...wanted.values()]);
  }, [view, index, chemistry, system]);

  useEffect(() => {
    view?.setSpace(space);
  }, [view, space]);

  useEffect(() => {
    view?.setPivot(pivot, point);
  }, [view, pivot, point]);

  // the axle the molecule is turned about is a bond clicked with the rotate tool, which lasts only while it is turning the molecule
  const turning = tool === "rotate" && rotateTarget === "molecule";
  useEffect(() => {
    if (!turning && axis !== null) {
      tools.turnAbout(null);
    }
    view?.setAxis(turning && axis !== null ? (index?.bonds.get(axis) ?? null) : null);
  }, [view, index, tools, turning, axis]);

  const anyMolecule = selected.items.some((item) => item.kind === "molecule");
  const moleculeSelected = tool === "move" ? anyMolecule && selectsEveryMolecule(selected.items, state?.fragments.length ?? 1) : anyMolecule;

  // a selected bond has a handle: an arrow along it with the move tool, which slides to change its length, and a ring about
  // it with the rotate tool, which turns to change its torsion. There is none where an atom it would move is locked
  const locked = state?.locked;

  // what the viewport does with the pointer and the keyboard, and the handles it offers, follows the tool and the selection.
  // It comes after the state is shown, so that it finds the atoms it is to put handles on
  useEffect(() => {
    view?.setMode(tool, { constraint, carryHydrogens, snap, rotateTarget, editClick, ringSize, bondOrder, selection: selected.items, index, hold, torsion, angle });
  }, [view, state, locked, tool, constraint, carryHydrogens, snap, rotateTarget, editClick, ringSize, bondOrder, selected, index, hold, torsion, angle]);

  // a selection with a locked atom in it cannot be moved, so there is nothing for a constraint to hold
  const blocked = state !== null && index !== null && [...index.members(selected.items).atoms].some((id) => state.locked.includes(id));


  useEffect(() => {
    if (!view || !index) {
      return;
    }
    // the ends of a hovered bond are tinted as the SMILES marks them, but only while nothing is selected, as the Inspector does
    const bond = hovered?.kind === "bond" && selected.items.length === 0 ? index.bonds.get(hovered.id) : undefined;
    // turning in a molecule leaves one side where it is, which is shown so that the Hold choice can be seen
    const turned = tool === "rotate" && rotateTarget === "internal" && selected.items.length === 1 ? selected.items[0] : undefined;
    const turnedBond = turned?.kind === "bond" ? index.bonds.get(turned.id) : undefined;
    const pair = turned?.kind === "atom" ? (angle?.centre === turned.id ? angle : defaultPair(index, turned.id)) : null;
    const held = turnedBond ? heldSide(index, turnedBond, hold) : turned && pair ? heldArm(index, turned.id, pair, hold) : null;
    view.setEmphasis({
      held: held ?? undefined,
      anchor: held?.anchor,
      selected: index.members(selected.items),
      active: index.members(selected.active ? [selected.active] : []),
      hover: index.members(hovered ? [hovered] : []),
      ends: bond ? [{ id: bond.first, colour: 1 }, { id: bond.second, colour: 2 }] : [],
    });
  }, [view, index, selected, hovered, tool, rotateTarget, hold]);

  useEffect(() => {
    if (!view || !index) {
      return;
    }
    return view.listen({
      hover: (pick) => hover.hover(pick ? resolve(pick, picking, index) : null),
      click: (pick, additive) => {
        // turning the whole molecule, an atom stands for its molecule, turned about that atom, and a bond for its molecule,
        // turned about the bond as an axle
        const { tool: current, rotateTarget: turning } = tools.get();
        if ((pick?.kind === "atom" || pick?.kind === "bond") && current === "rotate" && turning === "molecule") {
          const fragment = index.fragmentOf(pick);
          if (fragment !== null) {
            // clicking what is selected already would deselect it, and here it only moves the pivot
            if (!selection.get().items.some((item) => item.kind === "molecule" && item.id === fragment)) {
              selection.select({ kind: "molecule", id: fragment });
            }
            if (pick.kind === "bond") {
              tools.turnAbout(pick.id);
            } else {
              tools.turnAbout(null);
              tools.pivotOn("anchor");
              view.anchorOnAtom(pick.id);
            }
            return;
          }
        }

        if (pick) {
          selection.select(resolve(pick, picking, index), additive);
        } else if (!additive) {
          selection.clear();
        }
      },
      updateSelectionArea: setSelectionArea,
      releaseSelectionArea: (all) => {
        // the rectangle selects from one molecule, the one of the first thing in it, and leaves what is behind that in others
        const [first] = all;
        if (first === undefined) {
          return;
        }
        const fragment = index.fragmentOf({ kind: "atom", id: first });
        const atoms = fragment === null ? [first] : all.filter((id) => index.fragmentOf({ kind: "atom", id }) === fragment);

        // the bonds between the atoms inside go with them
        const inside = new Set(atoms);
        const bonds = [...index.bonds.values()].filter((bond) => inside.has(bond.first) && inside.has(bond.second));
        selection.addAll([...atoms.map((id): Item => ({ kind: "atom", id })), ...bonds.map((bond): Item => ({ kind: "bond", id: bond.id }))]);
      },
      place: async (placement) => {
        const { element, bondOrder: order } = tools.get();
        const result = await addAtom(element, placement, order);
        return result.kind === "applied" ? (result.added?.atoms[0] ?? null) : null;
      },
      bond: async (first, second) => {
        await addBond(first, second, tools.get().bondOrder);
      },
      ringSize: (size) => tools.ringSizeTo(size),
      // picking an atom up selects it, so the Inspector shows where it is while it moves
      dragStart: (id) => {
        // an atom of what is selected already, such as a molecule or a ring, is carried with it and does not take its place
        if (!index.members(selection.get().items).atoms.has(id)) {
          selection.select({ kind: "atom", id });
        }
      },
      drag: (moves, done) => (done ? void dropAtoms(moves) : dragAtoms(moves)),
      dragCancel: (moves) => void cancelDrag(moves),
      constraint: (held) => tools.constrain(held),
      carryHydrogens: (on) => tools.carry(on),
      moleculeDrag: (pose, done) => (done ? void dropMolecule(pose) : dragMolecule(pose)),
      moleculeDragCancel: (from) => void cancelMoleculeDrag(from),
      bondDrag: (edit, done) => (done ? void dropBond(edit) : dragBond(edit)),
      bondDragCancel: () => void cancelBondDrag(),
    });
  }, [view, index, picking, selection, hover, tools, addAtom, addBond, dragAtoms, dropAtoms, cancelDrag, dragMolecule, dropMolecule, cancelMoleculeDrag, dragBond, dropBond, cancelBondDrag]);

  useEffect(() => {
    if (!view) {
      return;
    }
    return models.focus.subscribe((atoms) => view.frameAtoms(atoms));
  }, [view, models.focus]);

  const frame = () => {
    if (!view || !index) {
      return;
    }
    const { atoms, bonds } = index.members(selected.items);
    const ends = [...bonds].flatMap((id) => {
      const bond = index.bonds.get(id);
      return bond ? [bond.first, bond.second] : [];
    });
    view.frameAtoms([...atoms, ...ends]);
  };

  const screenshot = () => {
    if (!view) {
      return;
    }
    const link = document.createElement("a");
    link.href = view.capture();
    link.download = "molecule.png";
    link.click();
  };

  return (
    <section className="viewport" aria-label="3D viewport">
      <canvas ref={canvasRef} className="viewport-canvas" />
      {probe && (
        <div className="field-probe" style={{ left: probe.left + 14, top: probe.top + 14 }} aria-hidden="true">
          {probe.lines.map((line, at) => (
            <div key={at}>{line}</div>
          ))}
        </div>
      )}
      {selectionArea && <div className="marquee" style={{ left: selectionArea.x, top: selectionArea.y, width: selectionArea.width, height: selectionArea.height }} aria-hidden="true" />}
      {state && (
        <>
          <DisplayTools
            axes={axes}
            onAxes={setAxes}
            grid={grid}
            onGrid={setGrid}
            gridPlane={gridPlane}
            onGridPlane={setGridPlane}
            strain={strain}
            tools={tools}
            debug={debug}
            onDebug={(on) => models.debug.to(on)}
            onScreenshot={screenshot}
          />

          <ActionTools
            tools={tools}
            state={toolState}
            gridShown={grid !== "off"}
            moleculeSelected={moleculeSelected}
            blocked={blocked}
            look={look}
            onLook={setLook}
            picking={picking}
            onPickScope={() => setMode(mode === "molecule" ? "item" : "molecule")}
            structure={structure}
            onFrame={frame}
            onCentre={() => view?.centreCamera()}
            onReset={() => view?.resetCamera()}
          />
        </>
      )}
      {phase.kind === "wanted" ? (
        <div className="viewport-overlay">
          <OpenMolecule message={phase.message} onOpen={openSmiles} />
        </div>
      ) : (
        (phase.kind === "connecting" || phase.kind === "opening") && (
          <div className="viewport-overlay">
            <p className="notice" role="status">
              {phase.kind === "connecting" ? "Connecting to the server…" : "Building the structure…"}
            </p>
          </div>
        )
      )}
    </section>
  );
}

/** What a click or hover on `pick` selects: the atom or bond, or the molecule it is in. */
function resolve(pick: PartItem, mode: PickScope, index: StateIndex): Item {
  const fragment = mode === "molecule" ? index.fragmentOf(pick) : null;
  return fragment !== null ? { kind: "molecule", id: fragment } : pick;
}
