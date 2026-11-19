package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.ValenceTable;
import com.ruben.chemistry.core.Valences;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * Works out where atoms that are added to a molecule go, and how many hydrogens its atoms lack.
 */
public final class AtomPlacement
{
    private static final double NEGLIGIBLE = 1e-6;

    private final Molecule molecule;
    private final Function<Atom, Vec3> positionOf;

    /**
     * @param molecule the molecule the atoms are added to, whose hydrogens are atoms
     * @param positionOf where each atom of the molecule is, by reference
     */
    public AtomPlacement(Molecule molecule, Function<Atom, Vec3> positionOf)
    {
        this.molecule = molecule;
        this.positionOf = positionOf;
    }

    /**
     * @param first the element of one atom
     * @param second the element of the one it is bonded to
     * @return how long a single bond between them is, in angstroms
     */
    public static double bondLength(String first, String second)
    {
        return ElementData.covalentRadius(ElementData.numberOf(first)) + ElementData.covalentRadius(ElementData.numberOf(second));
    }

    /**
     * @param atom an atom of the molecule whose valence is known
     * @return how many hydrogens it takes to fill the smallest valence its charge allows that its bonds do not exceed
     */
    public int hydrogensMissing(Atom atom)
    {
        int spent = 0;
        int aromatic = 0;
        for (Bond bond : molecule.bondsOf(atom))
        {
            switch (bond.order())
            {
                case SINGLE -> spent += 1;
                case DOUBLE -> spent += 2;
                case TRIPLE -> spent += 3;
                case QUADRUPLE -> spent += 4;
                case AROMATIC -> aromatic++;
            }
        }
        // aromatic bonds are worth one and a half each, as the molecule counts them
        spent += Math.round(1.5f * aromatic);

        int required = spent;
        OptionalInt valence = ValenceTable.allowedFor(atom.element(), atom.charge()).stream().mapToInt(Integer::intValue).filter(each -> each >= required).findFirst();
        if (valence.isEmpty())
        {
            valence = Valences.toFill(atom.element(), required);
        }
        return valence.isPresent() ? Math.max(0, valence.getAsInt() - spent) : 0;
    }

    /**
     * @param atom an atom of the molecule
     * @return unit vectors from it to each of its neighbours
     */
    public List<Vec3> armsOf(Atom atom)
    {
        Vec3 centre = positionOf.apply(atom);
        List<Vec3> arms = new ArrayList<>();
        for (Atom neighbour : molecule.neighboursOf(atom))
        {
            Vec3 arm = positionOf.apply(neighbour).minus(centre);
            if (arm.length() > NEGLIGIBLE)
            {
                arms.add(arm.normalised());
            }
        }
        return arms;
    }

    /**
     * Chooses where the next hydrogen of an atom goes.
     *
     * @param atom the atom
     * @param arms unit vectors from the atom to each of its neighbours and the hydrogens already placed
     * @return a unit vector for the new bond
     */
    public Vec3 hydrogenDirection(Atom atom, List<Vec3> arms)
    {
        List<Bond> bonds = molecule.bondsOf(atom);
        // the one arm is a bond of the atom's own, and not a hydrogen placed a moment ago on an atom with none
        if (arms.size() == 1 && bonds.size() == 1)
        {
            Vec3 along = arms.getFirst();
            BondOrder order = bonds.getFirst().order();
            if (order == BondOrder.TRIPLE)
            {
                return along.times(-1);
            }
            if (order == BondOrder.DOUBLE)
            {
                Vec3 across = acrossDoubleBond(atom, along);
                return along.times(-0.5).plus(across.times(Math.sqrt(3) / 2));
            }
        }
        return freeDirection(arms);
    }

    /**
     * Chooses where to put a new atom bonded to an existing one.
     *
     * @param target the atom to attach to
     * @param element the new atom's element symbol
     * @return where to put it, in the molecule's own frame
     */
    public Vec3 positionFor(Atom target, String element)
    {
        Vec3 direction = freeDirection(armsOf(target));
        return positionOf.apply(target).plus(direction.times(bondLength(target.element(), element)));
    }

    /**
     * @param atom an atom with one double bond and no other bond
     * @param along the unit vector from it along that bond
     * @return a unit vector at right angles to the bond, in the plane of what is bonded beyond it and on the side away from it
     */
    private Vec3 acrossDoubleBond(Atom atom, Vec3 along)
    {
        Atom other = molecule.neighboursOf(atom).getFirst();
        for (Atom beyond : molecule.neighboursOf(other))
        {
            if (beyond != atom)
            {
                Vec3 toBeyond = positionOf.apply(beyond).minus(positionOf.apply(other));
                Vec3 flat = toBeyond.minus(along.times(toBeyond.dot(along)));
                if (flat.length() > NEGLIGIBLE)
                {
                    return flat.normalised().times(-1);
                }
            }
        }
        return perpendicularTo(along);
    }

    /**
     * @param arms unit vectors from an atom to each of its neighbours
     * @return a unit vector for a new bond from it
     */
    public static Vec3 freeDirection(List<Vec3> arms)
    {
        if (arms.isEmpty())
        {
            return new Vec3(1, 0, 0);
        }

        Vec3 first = arms.getFirst();
        if (arms.size() == 1)
        {
            // a tetrahedral angle from the one bond, which is cos 109.47 = -1/3
            return first.times(-1.0 / 3).plus(perpendicularTo(first).times(Math.sqrt(8.0) / 3));
        }

        Vec3 second = arms.get(1);
        Vec3 normal = first.cross(second);
        if (arms.size() == 2)
        {
            Vec3 back = first.plus(second).times(-1);
            if (normal.length() < NEGLIGIBLE || back.length() < NEGLIGIBLE)
            {
                return perpendicularTo(first);
            }
            // arms wide apart are trigonal and leave the third place in their plane; closer ones are tetrahedral and leave two, one on each side
            if (first.dot(second) < -0.4)
            {
                return back.normalised();
            }
            return back.normalised().times(Math.sqrt(1.0 / 3)).plus(normal.normalised().times(Math.sqrt(2.0 / 3)));
        }

        if (arms.size() == 3)
        {
            Vec3 sum = first.plus(second).plus(arms.get(2));
            if (sum.length() > 0.1)
            {
                return sum.times(-1).normalised();
            }
            // three arms in a plane leave only the two directions at right angles to it
            return normal.length() < NEGLIGIBLE ? perpendicularTo(first) : normal.normalised();
        }

        // crowded: of many evenly spread directions, the one whose nearest arm is furthest away
        int samples = 256;
        Vec3 best = perpendicularTo(first);
        double bestRoom = -2;
        for (int sample = 0; sample < samples; sample++)
        {
            double y = 1 - 2 * (sample + 0.5) / samples;
            double radius = Math.sqrt(1 - y * y);
            double turn = sample * Math.PI * (3 - Math.sqrt(5));
            Vec3 candidate = new Vec3(Math.cos(turn) * radius, y, Math.sin(turn) * radius);
            double nearest = -2;
            for (Vec3 arm : arms)
            {
                nearest = Math.max(nearest, candidate.dot(arm));
            }
            if (-nearest > bestRoom)
            {
                bestRoom = -nearest;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * @param vector a unit vector
     * @return a unit vector at right angles to it
     */
    private static Vec3 perpendicularTo(Vec3 vector)
    {
        Vec3 axis = Math.abs(vector.x()) <= Math.abs(vector.y()) && Math.abs(vector.x()) <= Math.abs(vector.z())
                ? new Vec3(1, 0, 0)
                : Math.abs(vector.y()) <= Math.abs(vector.z()) ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        return vector.cross(axis).normalised();
    }
}
