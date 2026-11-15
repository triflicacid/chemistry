package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Works out where atoms go when a bond is made longer or shorter, or when one side of it is turned about it.
 */
public final class BondMotion
{
    /** The shortest a bond may be made, in angstroms, whichever way round: at no length it has no direction. */
    public static final double MIN_LENGTH = 0.1;

    /** The longest a bond may be made, in angstroms, whichever way round. */
    public static final double MAX_LENGTH = 10;

    /** How close, in angstroms, two atoms may be before there is no line between them to move them along. */
    private static final double COINCIDENT = 1e-6;

    private BondMotion()
    {
    }

    /**
     * Finds the atoms on one side of a bond: those reached from an atom of it without crossing it.
     *
     * @param molecule the molecule
     * @param bond a bond of {@code molecule}, by reference
     * @param start an atom of the bond, whose side is wanted
     * @return the atoms on that side, including {@code start}, as a set by reference identity; empty if the bond is in
     *     a ring, which has no one side
     * @throws IllegalArgumentException if {@code start} is not an atom of {@code bond}
     */
    public static Optional<Set<Atom>> sideOf(Molecule molecule, Bond bond, Atom start)
    {
        bond.getOther(start);
        if (!molecule.bridges().contains(bond))
        {
            return Optional.empty();
        }

        Set<Atom> side = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Atom> pending = new ArrayDeque<>();
        side.add(start);
        pending.add(start);
        while (!pending.isEmpty())
        {
            Atom here = pending.pop();
            for (Bond next : molecule.bondsOf(here))
            {
                // the bond is a bridge, so leaving it out is what keeps the search on this side
                Atom reached = next.getOther(here);
                if (next != bond && side.add(reached))
                {
                    pending.push(reached);
                }
            }
        }
        return Optional.of(side);
    }

    /**
     * Works out where atoms go to give a bond a length.
     * <p>
     * The atoms move along the line between the bond's two atoms. The held atom stays where it is; with none held, each
     * end moves half the change, so the bond keeps its midpoint. Everything on the side of an atom that moves goes with
     * it, unless the bond is in a ring, when only the atom moves.
     *
     * @param molecule the molecule
     * @param bond a bond of {@code molecule}, by reference
     * @param positions where each atom of {@code molecule} is
     * @param length the length to give it, in angstroms, measured along the bond from its first atom towards its second
     *     as it is in {@code positions}. A negative length puts the moving side on the far side of the held atom, passing
     *     through it, which reverses the bond's direction; the distance between the two atoms is then its size
     * @param held an atom of the bond to leave where it is, or {@code null} to hold neither
     * @return where each atom that moves goes, keyed by reference because atoms with the same element and charge are equal
     * @throws IllegalArgumentException if the length is not finite or its size is outside {@link #MIN_LENGTH} to
     *     {@link #MAX_LENGTH}, {@code held} is not an atom of the bond, or the bond's atoms are at the same place
     */
    public static Map<Atom, Vec3> withLength(Molecule molecule, Bond bond, Function<Atom, Vec3> positions, double length, Atom held)
    {
        if (!Double.isFinite(length) || Math.abs(length) < MIN_LENGTH || Math.abs(length) > MAX_LENGTH)
        {
            throw new IllegalArgumentException("A bond length must be " + MIN_LENGTH + " to " + MAX_LENGTH + " angstroms, or the same negative");
        }
        requireEnd(bond, held);

        Vec3 axis = positions.apply(bond.second()).minus(positions.apply(bond.first()));
        double current = axis.length();
        if (current < COINCIDENT)
        {
            throw new IllegalArgumentException("The bond's atoms are at the same place, so there is no line to move them along");
        }

        double change = length - current;
        double shiftFirst = held == bond.first() ? 0 : held == bond.second() ? -change : -change / 2;
        double shiftSecond = held == bond.second() ? 0 : held == bond.first() ? change : change / 2;
        Vec3 direction = axis.times(1 / current);

        Map<Atom, Double> shifts = new IdentityHashMap<>();
        addShift(molecule, bond, bond.first(), shiftFirst, shifts);
        addShift(molecule, bond, bond.second(), shiftSecond, shifts);

        Map<Atom, Vec3> moved = new IdentityHashMap<>();
        for (Atom atom : molecule.atoms())
        {
            Double shift = shifts.get(atom);
            if (shift != null)
            {
                moved.put(atom, positions.apply(atom).plus(direction.times(shift)));
            }
        }
        return moved;
    }

    /**
     * @param molecule the molecule
     * @param bond the bond being changed
     * @param end the atom of the bond whose side is to move
     * @param shift how far it moves; nothing moves if this is zero
     * @param into the distance each atom is to move, which this adds the atoms of the side to
     */
    private static void addShift(Molecule molecule, Bond bond, Atom end, double shift, Map<Atom, Double> into)
    {
        if (shift == 0)
        {
            return;
        }

        Optional<Set<Atom>> side = sideOf(molecule, bond, end);
        if (side.isPresent())
        {
            side.get().forEach(atom -> into.put(atom, shift));
        }
        else
        {
            into.put(end, shift);
        }
    }

    /**
     * Works out where atoms go to give a bond a torsion, by turning one side of it about the bond.
     * <p>
     * The torsion is measured between an atom bonded to each end of the bond, as {@link Vec3#torsion} measures it. The
     * side of the held atom stays and the other side turns, taking the shorter way round; with none held, each side turns
     * half the change, in opposite directions.
     *
     * @param molecule the molecule
     * @param bond a bond of {@code molecule}, by reference
     * @param from an atom bonded to the bond's first atom, other than its second
     * @param to an atom bonded to the bond's second atom, other than its first
     * @param positions where each atom of {@code molecule} is
     * @param degrees the torsion to give it, in degrees
     * @param held an atom of the bond whose side stays, or {@code null} to hold neither
     * @return where each atom that moves goes, keyed by reference because atoms with the same element and charge are equal
     * @throws IllegalArgumentException if the angle is not finite, the bond is in a ring, {@code held} is not an atom of
     *     the bond, {@code from} or {@code to} is not bonded to the right end, or the bond's atoms are at the same place
     */
    public static Map<Atom, Vec3> withTorsion(Molecule molecule, Bond bond, Atom from, Atom to, Function<Atom, Vec3> positions, double degrees, Atom held)
    {
        if (!Double.isFinite(degrees))
        {
            throw new IllegalArgumentException("A torsion must be a number of degrees");
        }
        requireEnd(bond, held);

        Set<Atom> firstSide = sideOf(molecule, bond, bond.first())
                .orElseThrow(() -> new IllegalArgumentException("The bond is in a ring, so turning one side of it would tear the ring"));
        Set<Atom> secondSide = sideOf(molecule, bond, bond.second()).orElseThrow();
        if (!isNeighbour(molecule, bond.first(), from, bond) || !isNeighbour(molecule, bond.second(), to, bond))
        {
            throw new IllegalArgumentException("The torsion must be measured between an atom bonded to each end of the bond");
        }

        Vec3 origin = positions.apply(bond.first());
        Vec3 axis = positions.apply(bond.second()).minus(origin);
        if (axis.length() < COINCIDENT)
        {
            throw new IllegalArgumentException("The bond's atoms are at the same place, so there is no axis to turn about");
        }

        double current = Vec3.torsion(positions.apply(from), origin, positions.apply(bond.second()), positions.apply(to));
        double change = shortest(degrees - current);

        // turning the far side clockwise as seen along the bond, which is the way a torsion grows, is a right-handed turn about it
        Map<Atom, Double> turns = new IdentityHashMap<>();
        if (held != bond.second())
        {
            secondSide.forEach(atom -> turns.put(atom, held == null ? change / 2 : change));
        }
        if (held != bond.first())
        {
            firstSide.forEach(atom -> turns.put(atom, held == null ? -change / 2 : -change));
        }

        Vec3 unit = axis.normalised();
        Map<Atom, Vec3> moved = new IdentityHashMap<>();
        for (Atom atom : molecule.atoms())
        {
            Double turn = turns.get(atom);
            if (turn != null)
            {
                moved.put(atom, origin.plus(turned(positions.apply(atom).minus(origin), unit, Math.toRadians(turn))));
            }
        }
        return moved;
    }

    /**
     * Works out where atoms go to give an atom's two bonds an angle, by swinging one bond and everything beyond it, about the atom.
     * <p>
     * The angle is at {@code centre}, between its bonds to {@code from} and to {@code to}.
     * The swing is in the plane of the three atoms, about the line through {@code centre} perpendicular to that plane.
     * The side of the held atom stays and the other turns; with none held, each side turns half the change, in opposite directions.
     * If the three atoms are in a line there is no plane, and the swing is about any line perpendicular to it.
     *
     * @param molecule the molecule
     * @param centre the atom the angle is at
     * @param from one atom bonded to {@code centre}
     * @param to another atom bonded to {@code centre}
     * @param positions where each atom of {@code molecule} is
     * @param degrees the angle to give it, in degrees, measured from the arm to {@code from} to the arm to {@code to}
     *     about the normal of their plane as it is in {@code positions}, which is {@code from} crossed with {@code to}.
     *     That is a full turn rather than the angle between two lines: 0 puts the arms on top of each other and 180 in a
     *     line, and an angle past 180 or below 0 swings the arm through that straight line to the far side of the plane,
     *     so 361 is 1 and -1 is 359 when an atom is held. With neither held, each arm turns half the change, so a whole
     *     turn more or less turns each arm half a turn and gives a different result
     * @param held {@code from} or {@code to}, whose side stays where it is, or {@code null} to hold neither
     * @return where each atom that moves goes
     * @throws IllegalArgumentException if the angle is not finite, {@code from} and {@code to} are not two different
     *     atoms bonded to {@code centre}, {@code held} is neither of them, a bond that would have to turn is in a ring,
     *     or an atom is at the same place as the centre
     */
    public static Map<Atom, Vec3> withAngle(Molecule molecule, Atom centre, Atom from, Atom to, Function<Atom, Vec3> positions, double degrees, Atom held)
    {
        if (!Double.isFinite(degrees))
        {
            throw new IllegalArgumentException("An angle must be a number of degrees");
        }
        if (from == to)
        {
            throw new IllegalArgumentException("An angle is measured between two different atoms");
        }
        if (held != null && held != from && held != to)
        {
            throw new IllegalArgumentException("The atom to hold must be one of the two atoms the angle is measured between");
        }

        Bond fromBond = molecule.bondBetween(centre, from)
                .orElseThrow(() -> new IllegalArgumentException("Both atoms the angle is measured between must be bonded to the atom it is at"));
        Bond toBond = molecule.bondBetween(centre, to)
                .orElseThrow(() -> new IllegalArgumentException("Both atoms the angle is measured between must be bonded to the atom it is at"));

        Vec3 origin = positions.apply(centre);
        Vec3 armFrom = positions.apply(from).minus(origin);
        Vec3 armTo = positions.apply(to).minus(origin);
        if (armFrom.length() < COINCIDENT || armTo.length() < COINCIDENT)
        {
            throw new IllegalArgumentException("An atom is at the same place as the atom the angle is at, so it has no direction");
        }

        double change = degrees - Vec3.angle(positions.apply(from), origin, positions.apply(to));

        // swinging the far arm away from the near one, which is the way an angle opens, is a right-handed turn about their normal
        Vec3 normal = armFrom.cross(armTo);
        Vec3 axis = normal.length() < 1e-9 * armFrom.length() * armTo.length() ? anyPerpendicular(armTo) : normal.normalised();

        Map<Atom, Double> turns = new IdentityHashMap<>();
        if (held != to)
        {
            sideBeyond(molecule, toBond, to).forEach(atom -> turns.put(atom, held == null ? change / 2 : change));
        }
        if (held != from)
        {
            sideBeyond(molecule, fromBond, from).forEach(atom -> turns.put(atom, held == null ? -change / 2 : -change));
        }

        Map<Atom, Vec3> moved = new IdentityHashMap<>();
        for (Atom atom : molecule.atoms())
        {
            Double turn = turns.get(atom);
            if (turn != null)
            {
                moved.put(atom, origin.plus(turned(positions.apply(atom).minus(origin), axis, Math.toRadians(turn))));
            }
        }
        return moved;
    }

    /**
     * @param molecule the molecule
     * @param bond a bond from the atom an angle is at
     * @param outer the atom of {@code bond} that is not the angle's atom
     * @return the atoms beyond the bond, from the angle's atom, including {@code outer}
     * @throws IllegalArgumentException if the bond is in a ring, so that nothing is beyond it
     */
    private static Set<Atom> sideBeyond(Molecule molecule, Bond bond, Atom outer)
    {
        return sideOf(molecule, bond, outer)
                .orElseThrow(() -> new IllegalArgumentException("A bond the angle would have to turn is in a ring, so turning it would tear the ring"));
    }

    /**
     * @param vector a vector that is not zero
     * @return a unit vector perpendicular to it
     */
    private static Vec3 anyPerpendicular(Vec3 vector)
    {
        // crossing with the axis the vector points least along cannot give a zero vector
        Vec3 least = Math.abs(vector.x()) <= Math.abs(vector.y()) && Math.abs(vector.x()) <= Math.abs(vector.z())
                ? new Vec3(1, 0, 0)
                : Math.abs(vector.y()) <= Math.abs(vector.z())
                    ? new Vec3(0, 1, 0)
                    : new Vec3(0, 0, 1);
        return vector.cross(least).normalised();
    }

    /**
     * @param molecule the molecule
     * @param end an atom of {@code bond}
     * @param candidate the atom to test
     * @param bond the bond {@code end} is an end of
     * @return whether {@code candidate} is bonded to {@code end} by some bond other than {@code bond}
     */
    private static boolean isNeighbour(Molecule molecule, Atom end, Atom candidate, Bond bond)
    {
        return molecule.bondsOf(end).stream().anyMatch(other -> other != bond && other.getOther(end) == candidate);
    }

    /**
     * @param bond the bond being edited
     * @param held the atom to hold, or {@code null} for none
     * @throws IllegalArgumentException if {@code held} is an atom but not of the bond
     */
    private static void requireEnd(Bond bond, Atom held)
    {
        if (held != null && held != bond.first() && held != bond.second())
        {
            throw new IllegalArgumentException("The atom to hold must be one of the bond's two atoms");
        }
    }

    /**
     * @param degrees an angle of any size
     * @return the same angle brought into above -180 and up to 180, which is the way round that is shortest
     */
    private static double shortest(double degrees)
    {
        double wrapped = ((degrees + 180) % 360 + 360) % 360 - 180;
        return wrapped == -180 ? 180 : wrapped;
    }

    /**
     * @param vector the vector to turn
     * @param axis the unit vector to turn it about
     * @param radians how far to turn it, right-handed
     * @return the turned vector, by Rodrigues' formula
     */
    private static Vec3 turned(Vec3 vector, Vec3 axis, double radians)
    {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return vector.times(cos)
                .plus(axis.cross(vector).times(sin))
                .plus(axis.times(axis.dot(vector) * (1 - cos)));
    }
}
