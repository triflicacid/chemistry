package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Molecule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A laid-out {@link Molecule}.
 *
 * @param atoms the atoms to draw, each holding its own {@link AtomPlacement#placementIndex()}, in index order
 * @param bonds the bonds to draw, referring to atom placements by their placement index
 * @param rings the rings the molecule has, referring to atom placements by their placement index
 * @param fragments one frame per disconnected part of the molecule, in index order
 */
public record Depiction(List<AtomPlacement> atoms, List<BondPlacement> bonds, List<RingPlacement> rings, List<FragmentFrame> fragments)
{
    /** The distance between two bonded atoms, which is the unit depiction space is measured in. */
    public static final double BOND_LENGTH = 1.0;

    /** The distance between an atom and a hydrogen hanging off it. */
    public static final double HYDROGEN_BOND_LENGTH = 0.7;

    /** How far, in bond lengths, the distances between atoms of two drawings may differ for them to look the same. */
    private static final double SAME_SHAPE_TOLERANCE = 0.05;

    /**
     * @throws IllegalArgumentException if an atom is out of index order, or a bond refers to an atom that is not there
     */
    public Depiction
    {
        atoms = List.copyOf(atoms);
        bonds = List.copyOf(bonds);
        rings = List.copyOf(rings);
        fragments = List.copyOf(fragments);

        for (int index = 0; index < atoms.size(); index++)
        {
            if (atoms.get(index).placementIndex() != index)
            {
                throw new IllegalArgumentException("Atom at position " + index + " reports index " + atoms.get(index).placementIndex());
            }
        }
        for (int index = 0; index < fragments.size(); index++)
        {
            if (fragments.get(index).index() != index)
            {
                throw new IllegalArgumentException("Fragment at position " + index + " reports index " + fragments.get(index).index());
            }
        }
        for (AtomPlacement atom : atoms)
        {
            if (atom.fragment() >= fragments.size())
            {
                throw new IllegalArgumentException("Atom " + atom.placementIndex() + " is in fragment " + atom.fragment() + ", but there are only " + fragments.size() + " fragments");
            }
        }
        for (BondPlacement bond : bonds)
        {
            requireAtom(atoms, bond.first());
            requireAtom(atoms, bond.second());
        }
        for (RingPlacement ring : rings)
        {
            for (int member : ring.atoms())
            {
                requireAtom(atoms, member);
            }
        }
    }

    /**
     * @param atoms the atoms to draw
     * @param bonds the bonds to draw
     * @param rings the rings the molecule has
     * @see #Depiction(List, List, List, List)
     */
    public Depiction(List<AtomPlacement> atoms, List<BondPlacement> bonds, List<RingPlacement> rings)
    {
        this(atoms, bonds, rings, framesOf(atoms));
    }

    /**
     * @param atoms the atoms to draw
     * @param bonds the bonds to draw
     * @see #Depiction(List, List, List, List)
     */
    public Depiction(List<AtomPlacement> atoms, List<BondPlacement> bonds)
    {
        this(atoms, bonds, List.of());
    }

    /**
     * @return a depiction of nothing at all
     */
    public static Depiction empty()
    {
        return new Depiction(List.of(), List.of(), List.of(), List.of());
    }

    /**
     * @param placementIndex an atom placement's index
     * @return that atom placement
     * @throws IndexOutOfBoundsException if no atom placement has that index
     */
    public AtomPlacement atom(int placementIndex)
    {
        return atoms.get(placementIndex);
    }

    /**
     * @param placementIndex a bond placement's index, i.e. its position in {@link #bonds()}
     * @return that bond placement
     * @throws IndexOutOfBoundsException if no bond placement has that index
     */
    public BondPlacement bond(int placementIndex)
    {
        return bonds.get(placementIndex);
    }

    /**
     * @param first one atom placement's index
     * @param second the other atom placement's index
     * @return the bond placement drawn between them, whichever way round it is drawn
     * @throws NoSuchElementException if no bond was drawn between them
     */
    public BondPlacement bondBetween(int first, int second)
    {
        return bonds.stream()
                .filter(bond -> (bond.first() == first && bond.second() == second)
                        || (bond.first() == second && bond.second() == first))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No bond was drawn between " + first + " and " + second));
    }

    /**
     * @return the smallest rectangle containing every atom, taking each fragment as being in its own frame, which is {@link Bounds#EMPTY} if there are none.
     */
    public Bounds bounds()
    {
        return Bounds.around(atoms.stream().map(AtomPlacement::position).toList());
    }

    /**
     * @param other another depiction
     * @return whether it draws the same atoms in the same shape, give or take a turn, a reflection or a small difference in distance
     */
    public boolean looksLike(Depiction other)
    {
        if (atoms.size() != other.atoms.size() || fragments.size() != other.fragments.size())
        {
            return false;
        }
        for (int fragment = 0; fragment < fragments.size(); fragment++)
        {
            double[] mine = sortedDistances(fragment);
            double[] theirs = other.sortedDistances(fragment);
            if (mine.length != theirs.length)
            {
                return false;
            }
            for (int index = 0; index < mine.length; index++)
            {
                if (Math.abs(mine[index] - theirs[index]) > SAME_SHAPE_TOLERANCE)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @param fragment a fragment's index
     * @return the distance between every pair of its atoms, smallest first
     */
    private double[] sortedDistances(int fragment)
    {
        List<Point> points = atoms.stream().filter(atom -> atom.fragment() == fragment).map(AtomPlacement::position).toList();
        double[] distances = new double[points.size() * (points.size() - 1) / 2];
        int next = 0;
        for (int first = 0; first < points.size(); first++)
        {
            for (int second = first + 1; second < points.size(); second++)
            {
                distances[next++] = Math.hypot(points.get(first).x() - points.get(second).x(), points.get(first).y() - points.get(second).y());
            }
        }
        Arrays.sort(distances);
        return distances;
    }

    /**
     * @return whether there is nothing to draw
     */
    public boolean isEmpty()
    {
        return atoms.isEmpty();
    }

    /**
     * @param atoms atoms in the frames they are placed in
     * @return a frame for each fragment up to the highest one any atom is in, bounding the atoms in it
     */
    private static List<FragmentFrame> framesOf(List<AtomPlacement> atoms)
    {
        int count = atoms.stream().mapToInt(atom -> atom.fragment() + 1).max().orElse(0);
        List<FragmentFrame> frames = new ArrayList<>(count);
        for (int fragment = 0; fragment < count; fragment++)
        {
            int index = fragment;
            frames.add(new FragmentFrame(index, Bounds.around(atoms.stream()
                .filter(atom -> atom.fragment() == index)
                .map(AtomPlacement::position)
                .toList())));
        }
        return frames;
    }

    /**
     * @param atoms the atoms a bond may refer to
     * @param index the index the bond refers to
     * @throws IllegalArgumentException if no atom has that index
     */
    private static void requireAtom(List<AtomPlacement> atoms, int index)
    {
        if (index >= atoms.size())
        {
            throw new IllegalArgumentException("Bond refers to atom " + index + ", but there are only " + atoms.size() + " atoms");
        }
    }
}
