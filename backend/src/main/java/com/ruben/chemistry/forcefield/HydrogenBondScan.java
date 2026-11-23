package com.ruben.chemistry.forcefield;

import com.ruben.chemistry.space.ElementData;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Scans the interaction energy of two rigid molecules along a hydrogen bond.
 */
public final class HydrogenBondScan
{
    private static final double BOHR_PER_ANGSTROM = 1.8897261246;
    private static final double BOND_TOLERANCE = 1.2;
    private static final Set<String> HEAVY_DONORS_AND_ACCEPTORS = Set.of("N", "O", "F");

    private HydrogenBondScan()
    {
    }

    /**
     * A hydrogen and the electronegative atom it is bonded to.
     *
     * @param heavyAtom the index of the N, O or F atom
     * @param hydrogen the index of the hydrogen
     */
    public record Donor(int heavyAtom, int hydrogen)
    {
    }

    /**
     * One point of a scan.
     *
     * @param distance the H...A distance, in bohr
     * @param energy the interaction energy contributions, in hartree
     */
    public record Point(double distance, Energy energy)
    {
    }

    /**
     * The outcome of a scan.
     *
     * @param points the scan points, in order of increasing distance
     * @param minimumDistance the H...A distance of the lowest energy, in bohr, refined by a parabola through the lowest point and its neighbours
     * @param minimumEnergy the total interaction energy at {@code minimumDistance}, in hartree
     */
    public record Result(List<Point> points, double minimumDistance, double minimumEnergy)
    {
        public Result
        {
            points = List.copyOf(points);
        }
    }

    /**
     * @param molecule the molecule to search
     * @return every N-H, O-H and F-H pair in the molecule, in atom order
     */
    public static List<Donor> donors(ForceFieldMolecule molecule)
    {
        Objects.requireNonNull(molecule);
        List<ForceFieldAtom> atoms = molecule.atoms();
        List<Donor> donors = new ArrayList<>();
        for (int i = 0; i < atoms.size(); i++)
        {
            if (HEAVY_DONORS_AND_ACCEPTORS.contains(atoms.get(i).element()))
            {
                for (int neighbour : neighbours(atoms, i))
                {
                    if (atoms.get(neighbour).element().equals("H"))
                    {
                        donors.add(new Donor(i, neighbour));
                    }
                }
            }
        }
        return donors;
    }

    /**
     * An N, O or F atom is taken as an acceptor unless it already has as many neighbours as it has valences.
     *
     * @param molecule the molecule to search
     * @return the indices of the atoms that can accept a hydrogen bond, in atom order
     */
    public static List<Integer> acceptors(ForceFieldMolecule molecule)
    {
        Objects.requireNonNull(molecule);
        List<ForceFieldAtom> atoms = molecule.atoms();
        List<Integer> acceptors = new ArrayList<>();
        for (int i = 0; i < atoms.size(); i++)
        {
            String element = atoms.get(i).element();
            if (HEAVY_DONORS_AND_ACCEPTORS.contains(element) && neighbours(atoms, i).size() <= maximumNeighbours(element))
            {
                acceptors.add(i);
            }
        }
        return acceptors;
    }

    /**
     * Moves the acceptor molecule rigidly into a hydrogen-bonded position.
     *
     * <p>The lone-pair direction is taken to point from the centroid of the acceptor's neighbours
     * to the acceptor, which is exact for water and ammonia and an approximation for a carbonyl
     * oxygen. An acceptor with no neighbours, such as a chloride ion, is translated without being
     * rotated, and a molecule is not twisted about the hydrogen bond.
     *
     * @param donorMolecule the molecule that holds the donor
     * @param donor the donor X-H pair
     * @param acceptorMolecule the molecule that holds the acceptor
     * @param acceptor the index of the acceptor atom in {@code acceptorMolecule}
     * @param forceField the force field used to rebuild the moved molecule
     * @param distance the H...A distance, in bohr
     * @return the acceptor molecule at its new position
     * @throws IllegalArgumentException if an index is outside its molecule or the distance is not positive
     */
    public static ForceFieldMolecule placeAcceptor(ForceFieldMolecule donorMolecule, Donor donor, ForceFieldMolecule acceptorMolecule, int acceptor, ForceField forceField, double distance)
    {
        return placeAcceptorAtAngle(donorMolecule, donor, acceptorMolecule, acceptor, forceField, distance, 180.0);
    }

    /**
     * Moves the acceptor molecule rigidly to a given H...A distance and X-H...A angle.
     *
     * <p>The acceptor atom is placed in the plane of the donor atom's X-H bond and its first other
     * neighbour, so for water the scan stays in the molecular plane. The donor atom may have no
     * other neighbour, in which case an arbitrary plane containing the bond is used. The
     * lone-pair handling is that of {@link #placeAcceptor}.
     *
     * @param donorMolecule the molecule that holds the donor
     * @param donor the donor X-H pair
     * @param acceptorMolecule the molecule that holds the acceptor
     * @param acceptor the index of the acceptor atom in {@code acceptorMolecule}
     * @param forceField the force field used to rebuild the moved molecule
     * @param distance the H...A distance, in bohr
     * @param angleDegrees the X-H...A angle, where 180 is a linear hydrogen bond
     * @return the acceptor molecule at its new position
     * @throws IllegalArgumentException if an index is outside its molecule or the distance is not positive
     */
    public static ForceFieldMolecule placeAcceptorAtAngle(ForceFieldMolecule donorMolecule, Donor donor, ForceFieldMolecule acceptorMolecule, int acceptor, ForceField forceField, double distance, double angleDegrees)
    {
        Objects.requireNonNull(donorMolecule);
        Objects.requireNonNull(donor);
        Objects.requireNonNull(acceptorMolecule);
        Objects.requireNonNull(forceField);
        if (donor.heavyAtom() < 0 || donor.heavyAtom() >= donorMolecule.atoms().size() || donor.hydrogen() < 0 || donor.hydrogen() >= donorMolecule.atoms().size())
        {
            throw new IllegalArgumentException("donor atom is outside the molecule");
        }
        if (acceptor < 0 || acceptor >= acceptorMolecule.atoms().size())
        {
            throw new IllegalArgumentException("acceptor atom is outside the molecule");
        }
        if (!(distance > 0.0))
        {
            throw new IllegalArgumentException("distance must be positive");
        }

        Vec3 heavy = position(donorMolecule.atoms().get(donor.heavyAtom()));
        Vec3 hydrogen = position(donorMolecule.atoms().get(donor.hydrogen()));
        Vec3 bondDirection = hydrogen.minus(heavy).normalised();
        double angle = Math.toRadians(angleDegrees);
        // the hydrogen-to-acceptor direction is the bond axis at 180 degrees and the in-plane perpendicular at 90
        Vec3 direction = bondDirection.times(-Math.cos(angle)).plus(inPlanePerpendicular(donorMolecule.atoms(), donor, bondDirection).times(Math.sin(angle)));
        Vec3 target = hydrogen.plus(direction.times(distance));

        List<ForceFieldAtom> atoms = acceptorMolecule.atoms();
        Vec3 origin = position(atoms.get(acceptor));
        List<Integer> neighbours = neighbours(atoms, acceptor);
        Vec3 lonePair = null;
        if (!neighbours.isEmpty())
        {
            Vec3 centroid = Vec3.ZERO;
            for (int neighbour : neighbours)
            {
                centroid = centroid.plus(position(atoms.get(neighbour)));
            }
            lonePair = origin.minus(centroid.times(1.0 / neighbours.size())).normalised();
        }

        List<ForceFieldAtom> moved = new ArrayList<>();
        for (ForceFieldAtom atom : atoms)
        {
            Vec3 offset = position(atom).minus(origin);
            if (lonePair != null)
            {
                offset = rotate(offset, lonePair, direction.times(-1.0));
            }
            Vec3 p = target.plus(offset);
            moved.add(new ForceFieldAtom(atom.element(), p.x(), p.y(), p.z(), atom.partialCharge()));
        }
        return forceField.molecule(acceptorMolecule.name(), moved);
    }

    /**
     * @param donorMolecule the molecule that holds the donor
     * @param donor the donor X-H pair
     * @param acceptorMolecule the molecule that holds the acceptor
     * @param acceptor the index of the acceptor atom in {@code acceptorMolecule}
     * @param forceField the force field
     * @param from the shortest H...A distance, in bohr
     * @param to the longest H...A distance, in bohr
     * @param steps the number of evenly spaced points, at least 3
     * @return the interaction energy at each distance and the refined minimum
     * @throws IllegalArgumentException if {@code steps} is below 3, {@code from} is not positive, or {@code to} is not above {@code from}
     */
    public static Result scan(ForceFieldMolecule donorMolecule, Donor donor, ForceFieldMolecule acceptorMolecule, int acceptor, ForceField forceField, double from, double to, int steps)
    {
        if (steps < 3)
        {
            throw new IllegalArgumentException("a scan needs at least 3 points");
        }
        if (!(from > 0.0) || !(to > from))
        {
            throw new IllegalArgumentException("scan range must be positive and increasing");
        }

        List<Point> points = new ArrayList<>();
        int lowest = 0;
        for (int i = 0; i < steps; i++)
        {
            double distance = from + (to - from) * i / (steps - 1);
            ForceFieldMolecule moved = placeAcceptor(donorMolecule, donor, acceptorMolecule, acceptor, forceField, distance);
            points.add(new Point(distance, ElectronDensityForceField.interaction(donorMolecule, moved, forceField.xc())));
            if (points.get(i).energy().total() < points.get(lowest).energy().total())
            {
                lowest = i;
            }
        }

        double minimumDistance = points.get(lowest).distance();
        double minimumEnergy = points.get(lowest).energy().total();
        if (lowest > 0 && lowest < steps - 1)
        {
            double before = points.get(lowest - 1).energy().total();
            double after = points.get(lowest + 1).energy().total();
            double curvature = before - 2.0 * minimumEnergy + after;
            if (curvature > 0.0)
            {
                // a parabola through three equally spaced points has its vertex at this fraction of a step
                double fraction = 0.5 * (before - after) / curvature;
                minimumDistance += fraction * (to - from) / (steps - 1);
                minimumEnergy -= 0.25 * (before - after) * fraction;
            }
        }
        return new Result(points, minimumDistance, minimumEnergy);
    }

    /**
     * One point of an angular scan.
     *
     * @param angleDegrees the X-H...A angle, in degrees
     * @param energy the interaction energy contributions, in hartree
     */
    public record AnglePoint(double angleDegrees, Energy energy)
    {
    }

    /**
     * The outcome of an angular scan.
     *
     * @param points the scan points, in order of increasing angle
     * @param lowest the point of lowest total energy
     */
    public record AngularResult(List<AnglePoint> points, AnglePoint lowest)
    {
        public AngularResult
        {
            points = List.copyOf(points);
            Objects.requireNonNull(lowest);
        }
    }

    /**
     * Scans the X-H...A angle at a fixed H...A distance.
     *
     * @param donorMolecule the molecule that holds the donor
     * @param donor the donor X-H pair
     * @param acceptorMolecule the molecule that holds the acceptor
     * @param acceptor the index of the acceptor atom in {@code acceptorMolecule}
     * @param forceField the force field
     * @param distance the H...A distance, in bohr
     * @param fromDegrees the smallest X-H...A angle
     * @param toDegrees the largest X-H...A angle
     * @param steps the number of evenly spaced points, at least 2
     * @return the interaction energy at each angle and the lowest point
     * @throws IllegalArgumentException if {@code steps} is below 2 or {@code toDegrees} is not above {@code fromDegrees}
     */
    public static AngularResult angularScan(ForceFieldMolecule donorMolecule, Donor donor, ForceFieldMolecule acceptorMolecule, int acceptor, ForceField forceField, double distance, double fromDegrees, double toDegrees, int steps)
    {
        if (steps < 2)
        {
            throw new IllegalArgumentException("a scan needs at least 2 points");
        }
        if (!(toDegrees > fromDegrees))
        {
            throw new IllegalArgumentException("scan range must be increasing");
        }

        List<AnglePoint> points = new ArrayList<>();
        AnglePoint lowest = null;
        for (int i = 0; i < steps; i++)
        {
            double angle = fromDegrees + (toDegrees - fromDegrees) * i / (steps - 1);
            ForceFieldMolecule moved = placeAcceptorAtAngle(donorMolecule, donor, acceptorMolecule, acceptor, forceField, distance, angle);
            AnglePoint point = new AnglePoint(angle, ElectronDensityForceField.interaction(donorMolecule, moved, forceField.xc()));
            points.add(point);
            if (lowest == null || point.energy().total() < lowest.energy().total())
            {
                lowest = point;
            }
        }
        return new AngularResult(points, lowest);
    }

    /**
     * Moves the acceptor molecule rigidly to a given X...A distance and X-H...A angle, where X is the donor's heavy atom.
     *
     * <p>Holding X...A fixed separates the angular dependence from the change in separation that
     * bending causes at a fixed H...A distance. The placement is otherwise that of
     * {@link #placeAcceptorAtAngle}.
     *
     * @param donorMolecule the molecule that holds the donor
     * @param donor the donor X-H pair
     * @param acceptorMolecule the molecule that holds the acceptor
     * @param acceptor the index of the acceptor atom in {@code acceptorMolecule}
     * @param forceField the force field used to rebuild the moved molecule
     * @param heavyDistance the X...A distance, in bohr
     * @param angleDegrees the X-H...A angle, where 180 is a linear hydrogen bond
     * @return the acceptor molecule at its new position
     * @throws IllegalArgumentException if an index is outside its molecule, or no placement has that X...A distance at that angle because it is shorter than the X-H bond's perpendicular reach
     */
    public static ForceFieldMolecule placeAcceptorAtHeavyDistance(ForceFieldMolecule donorMolecule, Donor donor, ForceFieldMolecule acceptorMolecule, int acceptor, ForceField forceField, double heavyDistance, double angleDegrees)
    {
        Objects.requireNonNull(donorMolecule);
        Objects.requireNonNull(donor);
        if (donor.heavyAtom() < 0 || donor.heavyAtom() >= donorMolecule.atoms().size() || donor.hydrogen() < 0 || donor.hydrogen() >= donorMolecule.atoms().size())
        {
            throw new IllegalArgumentException("donor atom is outside the molecule");
        }
        double bond = position(donorMolecule.atoms().get(donor.heavyAtom())).distanceTo(position(donorMolecule.atoms().get(donor.hydrogen())));
        double angle = Math.toRadians(angleDegrees);
        double reach = bond * Math.sin(angle);
        if (!(heavyDistance > Math.abs(reach)))
        {
            throw new IllegalArgumentException("no placement has X...A of " + heavyDistance + " bohr at " + angleDegrees + " degrees");
        }
        // the law of cosines in the triangle X, H, A, taking the longer root so that A lies beyond H
        double hydrogenDistance = bond * Math.cos(angle) + Math.sqrt(heavyDistance * heavyDistance - reach * reach);
        return placeAcceptorAtAngle(donorMolecule, donor, acceptorMolecule, acceptor, forceField, hydrogenDistance, angleDegrees);
    }

    /**
     * Scans the X-H...A angle at a fixed X...A distance, where X is the donor's heavy atom.
     *
     * @param donorMolecule the molecule that holds the donor
     * @param donor the donor X-H pair
     * @param acceptorMolecule the molecule that holds the acceptor
     * @param acceptor the index of the acceptor atom in {@code acceptorMolecule}
     * @param forceField the force field
     * @param heavyDistance the X...A distance, in bohr
     * @param fromDegrees the smallest X-H...A angle
     * @param toDegrees the largest X-H...A angle
     * @param steps the number of evenly spaced points, at least 2
     * @return the interaction energy at each angle and the lowest point
     * @throws IllegalArgumentException if {@code steps} is below 2, {@code toDegrees} is not above {@code fromDegrees}, or an angle has no placement at that distance
     */
    public static AngularResult angularScanAtHeavyDistance(ForceFieldMolecule donorMolecule, Donor donor, ForceFieldMolecule acceptorMolecule, int acceptor, ForceField forceField, double heavyDistance, double fromDegrees, double toDegrees, int steps)
    {
        if (steps < 2)
        {
            throw new IllegalArgumentException("a scan needs at least 2 points");
        }
        if (!(toDegrees > fromDegrees))
        {
            throw new IllegalArgumentException("scan range must be increasing");
        }

        List<AnglePoint> points = new ArrayList<>();
        AnglePoint lowest = null;
        for (int i = 0; i < steps; i++)
        {
            double angle = fromDegrees + (toDegrees - fromDegrees) * i / (steps - 1);
            ForceFieldMolecule moved = placeAcceptorAtHeavyDistance(donorMolecule, donor, acceptorMolecule, acceptor, forceField, heavyDistance, angle);
            AnglePoint point = new AnglePoint(angle, ElectronDensityForceField.interaction(donorMolecule, moved, forceField.xc()));
            points.add(point);
            if (lowest == null || point.energy().total() < lowest.energy().total())
            {
                lowest = point;
            }
        }
        return new AngularResult(points, lowest);
    }

    private static Vec3 inPlanePerpendicular(List<ForceFieldAtom> atoms, Donor donor, Vec3 bondDirection)
    {
        for (int neighbour : neighbours(atoms, donor.heavyAtom()))
        {
            if (neighbour != donor.hydrogen())
            {
                Vec3 toNeighbour = position(atoms.get(neighbour)).minus(position(atoms.get(donor.heavyAtom())));
                Vec3 perpendicular = toNeighbour.minus(bondDirection.times(toNeighbour.dot(bondDirection)));
                if (perpendicular.length() > 1.0e-6)
                {
                    return perpendicular.normalised();
                }
            }
        }
        Vec3 axis = Math.abs(bondDirection.x()) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return bondDirection.cross(axis).normalised();
    }

    private static int maximumNeighbours(String element)
    {
        return switch (element)
        {
            case "N" -> 3;
            case "O" -> 2;
            default -> 1;
        };
    }

    private static List<Integer> neighbours(List<ForceFieldAtom> atoms, int index)
    {
        List<Integer> neighbours = new ArrayList<>();
        ForceFieldAtom atom = atoms.get(index);
        for (int j = 0; j < atoms.size(); j++)
        {
            if (j != index)
            {
                double limit = BOND_TOLERANCE * BOHR_PER_ANGSTROM * (covalentRadius(atom) + covalentRadius(atoms.get(j)));
                if (position(atom).distanceTo(position(atoms.get(j))) <= limit)
                {
                    neighbours.add(j);
                }
            }
        }
        return neighbours;
    }

    private static double covalentRadius(ForceFieldAtom atom)
    {
        return ElementData.covalentRadius(ElementData.numberOf(atom.element()));
    }

    private static Vec3 position(ForceFieldAtom atom)
    {
        return new Vec3(atom.x(), atom.y(), atom.z());
    }

    /**
     * Rotates {@code vector} by the smallest rotation that takes the unit vector {@code from} onto the unit vector {@code to}.
     */
    private static Vec3 rotate(Vec3 vector, Vec3 from, Vec3 to)
    {
        double cosine = from.dot(to);
        if (cosine < -1.0 + 1.0e-9)
        {
            // antiparallel: any axis perpendicular to from will do, and the rotation is a half turn about it
            Vec3 axis = Math.abs(from.x()) < 0.9 ? new Vec3(1, 0, 0).cross(from) : new Vec3(0, 1, 0).cross(from);
            axis = axis.normalised();
            return axis.times(2.0 * axis.dot(vector)).minus(vector);
        }
        Vec3 axis = from.cross(to);
        // Rodrigues' formula with the sine and the half-angle terms folded into the cross product
        return vector.times(cosine).plus(axis.cross(vector)).plus(axis.times(axis.dot(vector) / (1.0 + cosine)));
    }
}
