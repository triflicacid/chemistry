package com.ruben.chemistry.space;

import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Re-jigs a rough structure into a low-strain one with the same stereochemistry.
 * <p>
 * The stereochemistry to keep is read from the first conformer by {@link StructureReader}, and may be overridden
 * or released with {@link Constraints}. Stereochemistry that cannot be read is left free and reported.
 * A double bond with no declared configuration keeps whichever side it is on. Every conformer is relaxed against
 * those same targets, and one that cannot be improved, or cannot meet them, is returned as it came.
 */
public final class StructureFixer
{
    /** How firmly a pin holds an atom, against the strain of the rest of the structure. */
    public static final double HARD_PIN = 200;

    /** Strain below which a structure is taken to need no fixing. */
    private static final double NEGLIGIBLE_STRAIN = 1e-3;

    /** How many ways of turning a structure over are tried. */
    private static final int TURNS = 3;

    private StructureFixer()
    {
    }

    /**
     * Stereochemistry to impose, free or hold, and atoms to keep in place. Atoms are those of the molecule read from
     * the structure, which are its non-implied sites in order; sites are the structure's own.
     *
     * @param centres the parity to give each tetrahedral centre, by atom
     * @param doubleBonds the configuration to give each double bond
     * @param free atoms whose stereochemistry is not held at all
     * @param atomPins how firmly each atom is held, by atom; its implied hydrogens go with it
     * @param sitePins how firmly each site is held, by site
     */
    public record Constraints(Map<Integer, Chirality> centres, List<BondTarget> doubleBonds, Set<Integer> free,
                              Map<Integer, Double> atomPins, Map<Integer, Double> sitePins)
    {
        public static final Constraints NONE = new Constraints(Map.of(), List.of(), Set.of(), Map.of(), Map.of());

        /**
         * @param atom a tetrahedral centre
         * @param parity the parity to give it, in the order a molecule declares it: implied hydrogen first, then bonds
         * @return these constraints with that centre set
         */
        public Constraints withCentre(int atom, Chirality parity)
        {
            Map<Integer, Chirality> next = new LinkedHashMap<>(centres);
            next.put(atom, parity);
            return new Constraints(next, doubleBonds, free, atomPins, sitePins);
        }

        /**
         * @param first one end of a double bond
         * @param second the other end
         * @param configuration whether the first substituent of each end, implied hydrogen last, lie on one side
         * @return these constraints with that bond set
         */
        public Constraints withDoubleBond(int first, int second, DoubleBondStereo configuration)
        {
            List<BondTarget> next = new ArrayList<>(doubleBonds);
            next.add(new BondTarget(first, second, configuration));
            return new Constraints(centres, next, free, atomPins, sitePins);
        }

        /**
         * @param atom an atom whose stereochemistry, as a centre or at a double bond, is not to be held
         * @return these constraints with that atom freed
         */
        public Constraints freeing(int atom)
        {
            Set<Integer> next = new LinkedHashSet<>(free);
            next.add(atom);
            return new Constraints(centres, doubleBonds, next, atomPins, sitePins);
        }

        /**
         * @param atom an atom to hold where it is, with its implied hydrogens
         * @return these constraints with that atom pinned hard
         */
        public Constraints pinning(int atom)
        {
            return pinning(atom, HARD_PIN);
        }

        /**
         * @param atom an atom to hold where it is, with its implied hydrogens
         * @param stiffness how firmly, where {@link #HARD_PIN} is a pin the strain cannot overcome
         * @return these constraints with that atom pinned
         */
        public Constraints pinning(int atom, double stiffness)
        {
            Map<Integer, Double> next = new LinkedHashMap<>(atomPins);
            next.put(atom, stiffness);
            return new Constraints(centres, doubleBonds, free, next, sitePins);
        }

        /**
         * @param site a site to hold where it is, hydrogen or not
         * @return these constraints with that site pinned hard
         */
        public Constraints pinningSite(int site)
        {
            return pinningSite(site, HARD_PIN);
        }

        /**
         * @param site a site to hold where it is, hydrogen or not
         * @param stiffness how firmly
         * @return these constraints with that site pinned
         */
        public Constraints pinningSite(int site, double stiffness)
        {
            Map<Integer, Double> next = new LinkedHashMap<>(sitePins);
            next.put(site, stiffness);
            return new Constraints(centres, doubleBonds, free, atomPins, next);
        }
    }

    /**
     * @param first one end of the double bond, as an atom
     * @param second the other end
     * @param configuration the configuration to give it
     */
    public record BondTarget(int first, int second, DoubleBondStereo configuration)
    {
    }

    /**
     * @param maximumIterations the most steps to relax each conformer for
     * @param restraint how weakly every atom is held to where it started, so that nothing wanders without reason
     * @param reading how clear the geometry must be for its stereochemistry to be read
     */
    public record Options(int maximumIterations, double restraint, StructureReader.Options reading)
    {
        public static final Options DEFAULT = new Options(2000, 0.02, StructureReader.Options.DEFAULT);
    }

    /** What became of a conformer. */
    public enum Outcome
    {
        /** It was already as good as could be had. */
        UNCHANGED,
        /** It was relaxed, and has the stereochemistry asked of it. */
        FIXED,
        /** It could not be made to meet the stereochemistry, so is as it came. */
        FAILED
    }

    /**
     * @param outcome what became of it
     * @param strainBefore its strain on the way in
     * @param strainAfter its strain on the way out
     * @param movement how far each site moved, by site
     * @param unread stereochemistry in the first conformer that could not be read, so was left free
     * @param conflicts constraints that did not apply and were ignored
     * @param disagrees whether this conformer, as it came, reads as a different stereoisomer to the first
     */
    public record Report(Outcome outcome, double strainBefore, double strainAfter, List<Double> movement,
                         List<StructureReader.Issue> unread, List<String> conflicts, boolean disagrees)
    {
    }

    /**
     * @param structure the fixed structure, its conformers in the order they came
     * @param reports what became of each conformer
     */
    public record Fixed(Structure3D structure, List<Report> reports)
    {
    }

    /**
     * @param structure the structure to fix
     * @return it with every conformer relaxed
     */
    public static Fixed fix(Structure3D structure)
    {
        return fix(structure, Constraints.NONE, Options.DEFAULT);
    }

    /**
     * @param structure the structure to fix
     * @param constraints what to impose, release and hold
     * @param options how hard to try
     * @return it with every conformer relaxed
     * @throws IllegalArgumentException if the structure has no conformers
     */
    public static Fixed fix(Structure3D structure, Constraints constraints, Options options)
    {
        if (structure.conformers().isEmpty())
        {
            throw new IllegalArgumentException("The structure has no conformers");
        }

        StructureReader.Reading reading = StructureReader.read(structure, 0, options.reading());
        Molecule molecule = reading.molecule();
        List<String> firstConformer = new ArrayList<>();
        firstConformer.add(SmilesWriter.writeCanonical(molecule));

        Mapping mapping = new Mapping(structure, new Topology(molecule));
        List<String> conflicts = new ArrayList<>();
        // the same targets for every conformer, so a conflict is found once
        Topology first = mapping.topology;
        applyConstraints(first, constraints, conflicts);
        double[] stiffness = stiffnesses(mapping, constraints, options, conflicts);

        List<Structure3D.Conformer> fixed = new ArrayList<>();
        List<Report> reports = new ArrayList<>();
        for (int at = 0; at < structure.conformers().size(); at++)
        {
            Structure3D.Conformer conformer = structure.conformers().get(at);
            boolean disagrees = at > 0 && !SmilesWriter.writeCanonical(StructureReader.read(structure, at, options.reading()).molecule()).equals(firstConformer.getFirst());

            Topology topology = new Topology(molecule);
            applyConstraints(topology, constraints, new ArrayList<>());
            double[] start = mapping.toNodes(conformer.positions());
            keepConfigurations(topology, start);

            Result result = relax(topology, start, stiffness, options);
            Outcome outcome = result.outcome;
            Structure3D.Conformer out = outcome == Outcome.FIXED
                ? new Structure3D.Conformer(result.after, mapping.toSites(result.positions))
                : new Structure3D.Conformer(result.before, conformer.positions());
            fixed.add(out);

            List<Double> movement = new ArrayList<>();
            for (int site = 0; site < out.positions().size(); site++)
            {
                movement.add(out.positions().get(site).minus(conformer.positions().get(site)).length());
            }
            reports.add(new Report(outcome, result.before, outcome == Outcome.FIXED ? result.after : result.before,
                Collections.unmodifiableList(movement), reading.issues(), List.copyOf(conflicts), disagrees));
        }
        return new Fixed(new Structure3D(structure.sites(), structure.connections(), List.copyOf(fixed)), List.copyOf(reports));
    }

    private record Result(Outcome outcome, double before, double after, double[] positions)
    {
    }

    private static Result relax(Topology topology, double[] start, double[] stiffness, Options options)
    {
        Bounds bounds = Bounds.of(topology, new Random(0));
        Strain strain = new Strain(topology, bounds, 3, 0);
        double before = strain.evaluate(start, null);
        boolean held = Embedder.respectsStereochemistry(topology, vectors(start));

        // each way of turning the structure over is tried, as some leave a geometry the relaxation cannot rescue
        double[] x = null;
        double after = 0;
        for (int variant = 0; variant < (held ? 1 : TURNS) && x == null; variant++)
        {
            double[] trial = start.clone();
            if (!held)
            {
                turnOver(topology, trial, stiffness, options.restraint(), variant);
            }
            Minimiser.minimise(new Restrained(strain, trial.clone(), stiffness), trial, options.maximumIterations());
            if (Embedder.respectsStereochemistry(topology, vectors(trial)))
            {
                x = trial;
                after = strain.evaluate(trial, null);
            }
        }

        if (x == null)
        {
            return new Result(Outcome.FAILED, before, before, start);
        }
        if (held && !(after < before - NEGLIGIBLE_STRAIN))
        {
            return new Result(Outcome.UNCHANGED, before, before, start);
        }
        if (!held || after < before)
        {
            return new Result(Outcome.FIXED, before, after, x);
        }
        return new Result(Outcome.UNCHANGED, before, before, start);
    }

    /**
     * Gets across what a relaxation cannot: a centre of the wrong hand is turned inside out by reflecting one
     * of its last three ligands' branches, the ones its handedness is measured by, and a double bond on the wrong side by turning one end's branch half way round the bond.
     * A branch is only moved if it is not in a ring, holds no centre of its own, and has no pinned atom.
     * The variant picks which ligand a centre tries first.
     */
    private static void turnOver(Topology topology, double[] x, double[] stiffness, double restraint, int variant)
    {
        Set<Integer> centres = new HashSet<>();
        for (Topology.Centre centre : topology.centres)
        {
            centres.add(centre.centre());
        }

        for (Topology.Configured bond : topology.configured)
        {
            List<Vec3> at = vectors(x);
            double torsion = Vec3.torsion(at.get(bond.firstReference()), at.get(bond.first()), at.get(bond.second()), at.get(bond.secondReference()));
            if ((Math.abs(torsion) < 90) == bond.sameSide())
            {
                continue;
            }
            for (int[] ends : new int[][] {{bond.first(), bond.second()}, {bond.second(), bond.first()}})
            {
                Set<Integer> branch = branch(topology, ends[1], ends[0]);
                if (movable(branch, centres, stiffness, restraint))
                {
                    Vec3 origin = at.get(ends[0]);
                    Vec3 axis = at.get(ends[1]).minus(origin).normalised();
                    for (int node : branch)
                    {
                        Vec3 offset = at.get(node).minus(origin);
                        set(x, node, origin.plus(axis.times(2 * axis.dot(offset))).minus(offset));
                    }
                    break;
                }
            }
        }

        for (Topology.Centre centre : topology.centres)
        {
            List<Vec3> at = vectors(x);
            int[] ligands = centre.ligands();
            double handedness = Vec3.handedness(at.get(centre.centre()), at.get(ligands[1]), at.get(ligands[2]), at.get(ligands[3]));
            if (centre.sign() * handedness >= 0)
            {
                continue;
            }
            for (int tried = 0; tried < 3; tried++)
            {
                int chosen = 1 + (tried + variant) % 3;
                Set<Integer> branch = branch(topology, ligands[chosen], centre.centre());
                if (!movable(branch, centres, stiffness, restraint))
                {
                    continue;
                }
                int[] others = java.util.stream.IntStream.range(0, 4).filter(slot -> slot != chosen).map(slot -> ligands[slot]).toArray();
                Vec3 origin = at.get(centre.centre());
                Vec3 normal = at.get(others[0]).minus(origin).cross(at.get(others[1]).minus(origin)).normalised();
                for (int node : branch)
                {
                    Vec3 offset = at.get(node).minus(origin);
                    set(x, node, at.get(node).minus(normal.times(2 * normal.dot(offset))));
                }
                break;
            }
        }
    }

    /** @return the nodes reached from {@code start} without going through {@code blocker}, or {@code null} if it leads back to it, being in a ring */
    private static Set<Integer> branch(Topology topology, int start, int blocker)
    {
        Set<Integer> seen = new LinkedHashSet<>();
        List<Integer> queue = new ArrayList<>(List.of(start));
        seen.add(start);
        while (!queue.isEmpty())
        {
            int node = queue.removeFirst();
            for (int neighbour : topology.neighbours[node])
            {
                if (neighbour == blocker)
                {
                    if (node != start)
                    {
                        return null;
                    }
                    continue;
                }
                if (seen.add(neighbour))
                {
                    queue.add(neighbour);
                }
            }
        }
        return seen;
    }

    private static boolean movable(Set<Integer> branch, Set<Integer> centres, double[] stiffness, double restraint)
    {
        if (branch == null)
        {
            return false;
        }
        for (int node : branch)
        {
            if (centres.contains(node) || stiffness[node] > restraint)
            {
                return false;
            }
        }
        return true;
    }

    private static void set(double[] x, int node, Vec3 at)
    {
        x[node * 3] = at.x();
        x[node * 3 + 1] = at.y();
        x[node * 3 + 2] = at.z();
    }

    /** The strain plus a harmonic hold on each coordinate to where it began. */
    private record Restrained(Strain strain, double[] origin, double[] stiffness) implements Minimiser.Objective
    {
        @Override
        public double evaluate(double[] x, double[] gradient)
        {
            double energy = strain.evaluate(x, gradient);
            for (int at = 0; at < x.length; at++)
            {
                double k = stiffness[at / 3];
                double difference = x[at] - origin[at];
                energy += k * difference * difference;
                if (gradient != null)
                {
                    gradient[at] += 2 * k * difference;
                }
            }
            return energy;
        }
    }

    private static List<Vec3> vectors(double[] x)
    {
        List<Vec3> list = new ArrayList<>(x.length / 3);
        for (int node = 0; node < x.length / 3; node++)
        {
            list.add(new Vec3(x[node * 3], x[node * 3 + 1], x[node * 3 + 2]));
        }
        return list;
    }

    /** Sets, frees and checks the constraints against a topology that holds what the reader found. */
    private static void applyConstraints(Topology topology, Constraints constraints, List<String> conflicts)
    {
        int atoms = topology.molecule.atoms().size();
        for (int atom : constraints.free())
        {
            if (atom < 0 || atom >= atoms)
            {
                conflicts.add("Cannot free atom " + atom + ": there is no such atom");
                continue;
            }
            topology.centres.removeIf(centre -> centre.centre() == atom);
            topology.configured.removeIf(bond -> bond.first() == atom || bond.second() == atom);
        }

        for (Map.Entry<Integer, Chirality> entry : constraints.centres().entrySet())
        {
            int atom = entry.getKey();
            if (atom < 0 || atom >= atoms)
            {
                conflicts.add("Cannot set the centre at atom " + atom + ": there is no such atom");
                continue;
            }
            List<Integer> ligands = new ArrayList<>();
            int hydrogens = 0;
            for (int neighbour : topology.neighbours[atom])
            {
                if (topology.implied[neighbour])
                {
                    hydrogens++;
                    ligands.addFirst(neighbour);
                }
            }
            for (int neighbour : topology.neighbours[atom])
            {
                if (!topology.implied[neighbour])
                {
                    ligands.add(neighbour);
                }
            }
            if (ligands.size() != 4 || hydrogens > 1)
            {
                conflicts.add("Cannot set the centre at atom " + atom + ": it does not have four ligands");
                continue;
            }
            if (!distinctLigands(topology, atom, ligands))
            {
                conflicts.add("Cannot set the centre at atom " + atom + ": two of its ligands are alike");
                continue;
            }
            topology.centres.removeIf(centre -> centre.centre() == atom);
            topology.centres.add(new Topology.Centre(atom, ligands.stream().mapToInt(Integer::intValue).toArray(),
                entry.getValue() == Chirality.CLOCKWISE ? 1 : -1));
        }

        for (BondTarget target : constraints.doubleBonds())
        {
            int a = target.first();
            int b = target.second();
            if (a < 0 || b < 0 || a >= atoms || b >= atoms || topology.link(a, b) == null
                || topology.link(a, b).order() != BondOrder.DOUBLE)
            {
                conflicts.add("Cannot set the bond between atoms " + a + " and " + b + ": it is not a double bond");
                continue;
            }
            int referenceA = substituent(topology, a, b);
            int referenceB = substituent(topology, b, a);
            if (referenceA < 0 || referenceB < 0)
            {
                conflicts.add("Cannot set the double bond between atoms " + a + " and " + b + ": an end has no substituent or too many");
                continue;
            }
            topology.configured.removeIf(bond -> Topology.pair(bond.first(), bond.second()) == Topology.pair(a, b));
            topology.configured.add(new Topology.Configured(a, b, referenceA, referenceB, target.configuration() == DoubleBondStereo.SAME_SIDE));
        }
    }

    /** Whether a centre's four ligands are different, by element, isotope and the number of atoms they hang from. */
    private static boolean distinctLigands(Topology topology, int atom, List<Integer> ligands)
    {
        Set<List<Object>> seen = new HashSet<>();
        for (int ligand : ligands)
        {
            List<Integer> around = new ArrayList<>();
            for (int neighbour : topology.neighbours[ligand])
            {
                if (neighbour != atom)
                {
                    around.add(topology.atomicNumber[neighbour] * 1000 + topology.isotope[neighbour]);
                }
            }
            Collections.sort(around);
            if (!seen.add(List.of(topology.element[ligand], topology.isotope[ligand], topology.charge[ligand], around)))
            {
                return false;
            }
        }
        return true;
    }

    /** @return the first neighbour of {@code end} but {@code partner}, hydrogens last, or -1 if it has none or more than two */
    private static int substituent(Topology topology, int end, int partner)
    {
        int found = -1;
        int count = 0;
        for (int neighbour : topology.neighbours[end])
        {
            if (neighbour != partner)
            {
                count++;
                if (found < 0)
                {
                    found = neighbour;
                }
            }
        }
        return count == 0 || count > 2 ? -1 : found;
    }

    /** Declares each double bond nothing has been said of as it lies, so that the relaxation does not choose a side for it. */
    private static void keepConfigurations(Topology topology, double[] x)
    {
        List<Vec3> positions = vectors(x);
        Set<Long> declared = new HashSet<>();
        for (Topology.Configured bond : topology.configured)
        {
            declared.add(Topology.pair(bond.first(), bond.second()));
        }
        for (Topology.Link link : topology.links)
        {
            if (link.order() != BondOrder.DOUBLE || declared.contains(Topology.pair(link.first(), link.second())))
            {
                continue;
            }
            int a = link.first();
            int b = link.second();
            int referenceA = substituent(topology, a, b);
            int referenceB = substituent(topology, b, a);
            if (referenceA < 0 || referenceB < 0)
            {
                continue;
            }
            double torsion = Vec3.torsion(positions.get(referenceA), positions.get(a), positions.get(b), positions.get(referenceB));
            topology.configured.add(new Topology.Configured(a, b, referenceA, referenceB, Math.abs(torsion) < 90));
        }
    }

    /** @return how firmly each node is held to where it started */
    private static double[] stiffnesses(Mapping mapping, Constraints constraints, Options options, List<String> conflicts)
    {
        int count = mapping.topology.count;
        double[] stiffness = new double[count];
        java.util.Arrays.fill(stiffness, options.restraint());
        int atoms = mapping.topology.molecule.atoms().size();
        constraints.atomPins().forEach((atom, value) ->
        {
            if (atom < 0 || atom >= atoms)
            {
                conflicts.add("Cannot pin atom " + atom + ": there is no such atom");
                return;
            }
            for (int node = 0; node < count; node++)
            {
                if (mapping.topology.owner[node] == atom)
                {
                    stiffness[node] = Math.max(stiffness[node], value);
                }
            }
        });
        constraints.sitePins().forEach((site, value) ->
        {
            if (site < 0 || site >= count)
            {
                conflicts.add("Cannot pin site " + site + ": there is no such site");
                return;
            }
            int node = mapping.nodeOfSite[site];
            stiffness[node] = Math.max(stiffness[node], value);
        });
        return stiffness;
    }

    /** How the nodes of the read molecule's topology line up with the sites of the structure. */
    private static final class Mapping
    {
        final Topology topology;
        final int[] nodeOfSite;
        final int[] siteOfNode;

        Mapping(Structure3D structure, Topology topology)
        {
            this.topology = topology;
            int sites = structure.sites().size();
            if (topology.count != sites)
            {
                throw new IllegalArgumentException("The structure's hydrogens cannot be reproduced as a molecule");
            }
            nodeOfSite = new int[sites];
            siteOfNode = new int[topology.count];
            java.util.Arrays.fill(siteOfNode, -1);

            int atom = 0;
            Map<Integer, Integer> atomOfSite = new HashMap<>();
            for (Structure3D.Site site : structure.sites())
            {
                if (!site.implied())
                {
                    siteOfNode[atom] = site.index();
                    nodeOfSite[site.index()] = atom;
                    atomOfSite.put(site.index(), atom++);
                }
            }

            Map<Integer, List<Integer>> hydrogensOf = new HashMap<>();
            for (Structure3D.Connection connection : structure.connections())
            {
                boolean firstImplied = structure.sites().get(connection.first()).implied();
                boolean secondImplied = structure.sites().get(connection.second()).implied();
                if (firstImplied != secondImplied)
                {
                    int heavy = firstImplied ? connection.second() : connection.first();
                    int hydrogen = firstImplied ? connection.first() : connection.second();
                    hydrogensOf.computeIfAbsent(atomOfSite.get(heavy), ignored -> new ArrayList<>()).add(hydrogen);
                }
            }
            for (int node = atom; node < topology.count; node++)
            {
                List<Integer> candidates = hydrogensOf.getOrDefault(topology.owner[node], new ArrayList<>());
                int match = -1;
                for (int candidate : candidates)
                {
                    int isotope = structure.sites().get(candidate).isotope();
                    if ((isotope == 1 ? 0 : isotope) == topology.isotope[node])
                    {
                        match = candidate;
                        break;
                    }
                }
                if (match < 0)
                {
                    throw new IllegalArgumentException("The structure's hydrogens cannot be reproduced as a molecule");
                }
                candidates.remove((Integer) match);
                siteOfNode[node] = match;
                nodeOfSite[match] = node;
            }
        }

        double[] toNodes(List<Vec3> positions)
        {
            double[] x = new double[topology.count * 3];
            for (int node = 0; node < topology.count; node++)
            {
                Vec3 at = positions.get(siteOfNode[node]);
                x[node * 3] = at.x();
                x[node * 3 + 1] = at.y();
                x[node * 3 + 2] = at.z();
            }
            return x;
        }

        List<Vec3> toSites(double[] x)
        {
            Vec3[] positions = new Vec3[topology.count];
            for (int node = 0; node < topology.count; node++)
            {
                positions[siteOfNode[node]] = new Vec3(x[node * 3], x[node * 3 + 1], x[node * 3 + 2]);
            }
            return List.of(positions);
        }
    }
}
