package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a {@link Structure3D} back into a {@link Molecule}, declaring tetrahedral and double bond stereochemistry
 * from the geometry. It is the inverse of {@link Embedder}.
 */
public final class StructureReader
{
    /**
     * @param minimumHandedness how far from flat a centre must be to be read, as {@link Vec3#handedness} measures it from the
     *     centre and, likewise, from one ligand to the other three (about 0.7 for an ideal tetrahedron); the two must agree
     * @param torsionTolerance how many degrees a double bond's torsion may be from 0 or 180 to be read
     */
    public record Options(double minimumHandedness, double torsionTolerance)
    {
        public static final Options DEFAULT = new Options(0.2, 30);
    }

    /** What could not be read. */
    public enum Kind
    {
        /** A centre with four different ligands whose geometry is too flat to give a handedness. */
        FLAT_CENTRE,

        /** A double bond with different substituents on both ends that is too twisted to call cis or trans. */
        TWISTED_DOUBLE_BOND
    }

    /**
     * @param kind what could not be read
     * @param atom the index of the molecule atom concerned; for a double bond, the first end
     * @param message a description
     */
    public record Issue(Kind kind, int atom, String message)
    {
    }

    /**
     * @param molecule the molecule, with the stereochemistry that could be read
     * @param issues the stereochemistry that could not be read
     */
    public record Reading(Molecule molecule, List<Issue> issues)
    {
    }

    private StructureReader()
    {
    }

    /**
     * @param structure the molecule in space
     * @param conformer the index of the conformer to read
     * @return the molecule, atoms in the order of the structure's non-implied sites
     * @throws IllegalArgumentException if there is no such conformer
     */
    public static Reading read(Structure3D structure, int conformer)
    {
        return read(structure, conformer, Options.DEFAULT);
    }

    /**
     * @param structure the molecule in space
     * @param conformer the index of the conformer to read
     * @param options how clear the geometry must be
     * @return the molecule, atoms in the order of the structure's non-implied sites
     * @throws IllegalArgumentException if there is no such conformer
     */
    public static Reading read(Structure3D structure, int conformer, Options options)
    {
        if (conformer < 0 || conformer >= structure.conformers().size())
        {
            throw new IllegalArgumentException("There is no conformer " + conformer + "; there are " + structure.conformers().size());
        }
        return new Run(structure, structure.conformers().get(conformer).positions(), options).read();
    }

    private static final class Run
    {
        private final Structure3D structure;
        private final List<Vec3> positions;
        private final Options options;

        /** The atom index of each site, or -1 for an implied hydrogen. */
        private final int[] atomOfSite;
        private final List<Structure3D.Site> sites = new ArrayList<>();
        /** The site of each atom. */
        private final List<Integer> siteOfAtom = new ArrayList<>();
        /** Each atom's bonded atoms in the order the bonds are added, which is the order chirality is declared against. */
        private final List<List<Integer>> neighbours = new ArrayList<>();
        private final List<Structure3D.Connection> bonds = new ArrayList<>();
        /** Each atom's implied hydrogen sites. */
        private final List<List<Integer>> hydrogens = new ArrayList<>();
        private final Set<Integer> aromatic = new HashSet<>();
        private final List<Issue> issues = new ArrayList<>();

        Run(Structure3D structure, List<Vec3> positions, Options options)
        {
            this.structure = structure;
            this.positions = positions;
            this.options = options;
            atomOfSite = new int[structure.sites().size()];
            for (Structure3D.Site site : structure.sites())
            {
                if (site.implied())
                {
                    atomOfSite[site.index()] = -1;
                }
                else
                {
                    atomOfSite[site.index()] = siteOfAtom.size();
                    siteOfAtom.add(site.index());
                    neighbours.add(new ArrayList<>());
                    hydrogens.add(new ArrayList<>());
                }
            }
            for (Structure3D.Connection connection : structure.connections())
            {
                int first = atomOfSite[connection.first()];
                int second = atomOfSite[connection.second()];
                if (first >= 0 && second >= 0)
                {
                    bonds.add(connection);
                    neighbours.get(first).add(second);
                    neighbours.get(second).add(first);
                    if (connection.order() == BondOrder.AROMATIC)
                    {
                        aromatic.add(first);
                        aromatic.add(second);
                    }
                }
                else if (first >= 0 || second >= 0)
                {
                    int heavy = Math.max(first, second);
                    int hydrogen = first >= 0 ? connection.second() : connection.first();
                    hydrogens.get(heavy).add(hydrogen);
                }
            }
        }

        Reading read()
        {
            Molecule plain = build(Set.of(), Map.of(), Map.of());
            int[] rank = equivalenceClasses();

            Map<Integer, Chirality> centres = new HashMap<>();
            for (int atom = 0; atom < rank.length; atom++)
            {
                readCentre(atom, rank, centres);
            }
            Map<Integer, DoubleBondStereo> doubleBonds = new HashMap<>();
            for (int index = 0; index < bonds.size(); index++)
            {
                readDoubleBond(index, rank, plain, doubleBonds);
            }

            // an atom's hydrogens are only spelled out where the molecule would not work them out, or a parity needs the slot
            Set<Integer> explicit = new HashSet<>(centres.keySet());
            for (int atom = 0; atom < rank.length; atom++)
            {
                Structure3D.Site site = structure.sites().get(siteOfAtom.get(atom));
                // an isotope or charge is written in brackets, which only keep the hydrogens they are told of
                if (site.isotope() > 0 || hydrogens.get(atom).size() != plain.implicitHydrogenCount(plain.atoms().get(atom)))
                {
                    explicit.add(atom);
                }
            }
            return new Reading(build(explicit, centres, doubleBonds), List.copyOf(issues));
        }

        /** Each atom's class, which two atoms share if the graph cannot tell them apart; stereochemistry is ignored. */
        private int[] equivalenceClasses()
        {
            int count = siteOfAtom.size();
            int[] classes = new int[count];
            Map<List<Object>, Integer> seen = new HashMap<>();
            for (int atom = 0; atom < count; atom++)
            {
                Structure3D.Site site = structure.sites().get(siteOfAtom.get(atom));
                List<Object> key = List.of(site.element(), site.isotope(), site.charge(), hydrogens.get(atom).size(), aromatic.contains(atom));
                classes[atom] = seen.computeIfAbsent(key, ignored -> seen.size());
            }
            int known = seen.size();
            while (true)
            {
                List<List<Integer>> signatures = new ArrayList<>();
                for (int atom = 0; atom < count; atom++)
                {
                    signatures.add(new ArrayList<>(List.of(classes[atom])));
                }
                for (Structure3D.Connection bond : bonds)
                {
                    int first = atomOfSite[bond.first()];
                    int second = atomOfSite[bond.second()];
                    int order = bond.order().ordinal();
                    signatures.get(first).add(order * 100_000 + classes[second]);
                    signatures.get(second).add(order * 100_000 + classes[first]);
                }
                Map<List<Integer>, Integer> refined = new HashMap<>();
                int[] next = new int[count];
                for (int atom = 0; atom < count; atom++)
                {
                    List<Integer> signature = signatures.get(atom);
                    List<Integer> neighbourhood = signature.subList(1, signature.size()).stream().sorted().toList();
                    List<Integer> key = new ArrayList<>(neighbourhood);
                    key.addFirst(signature.getFirst());
                    next[atom] = refined.computeIfAbsent(key, ignored -> refined.size());
                }
                classes = next;
                if (refined.size() == known)
                {
                    return classes;
                }
                known = refined.size();
            }
        }

        private Molecule build(Set<Integer> explicit, Map<Integer, Chirality> centres, Map<Integer, DoubleBondStereo> doubleBonds)
        {
            Molecule.Builder builder = new Molecule.Builder();
            List<Atom> atoms = new ArrayList<>();
            for (int atom = 0; atom < siteOfAtom.size(); atom++)
            {
                Structure3D.Site site = structure.sites().get(siteOfAtom.get(atom));
                Atom.Builder made = Atom.builder(site.element()).charge(site.charge()).aromatic(aromatic.contains(atom));
                if (site.isotope() > 0)
                {
                    made.isotope(site.isotope());
                }
                if (explicit.contains(atom))
                {
                    made.hydrogenCount(hydrogens.get(atom).size());
                }
                Atom built = made.build();
                atoms.add(built);
                builder.addAtom(built);
            }
            for (Structure3D.Connection bond : bonds)
            {
                builder.addBond(atoms.get(atomOfSite[bond.first()]), atoms.get(atomOfSite[bond.second()]), bond.order());
            }
            centres.forEach((atom, chirality) -> builder.setChirality(atoms.get(atom), chirality));
            doubleBonds.forEach((index, stereo) ->
                builder.setDoubleBondStereo(atoms.get(atomOfSite[bonds.get(index).first()]), atoms.get(atomOfSite[bonds.get(index).second()]), stereo));
            return builder.build();
        }

        /** Declares a centre's parity if it has four different ligands and a geometry that shows which way round they go. */
        private void readCentre(int atom, int[] rank, Map<Integer, Chirality> centres)
        {
            List<Integer> ligands = new ArrayList<>();
            // the hydrogen leads, as it does when a parity is declared against a molecule's neighbour order
            if (!hydrogens.get(atom).isEmpty())
            {
                ligands.add(hydrogens.get(atom).getFirst());
            }
            for (int neighbour : neighbours.get(atom))
            {
                ligands.add(siteOfAtom.get(neighbour));
            }
            if (ligands.size() != 4 || aromatic.contains(atom) || hydrogens.get(atom).size() > 1)
            {
                return;
            }

            Set<Integer> classes = new HashSet<>();
            for (int ligand : ligands)
            {
                classes.add(atomOfSite[ligand] < 0 ? -1 : rank[atomOfSite[ligand]]);
            }
            if (classes.size() != 4)
            {
                return;
            }

            // judged both from the centre, as Topology reads a declared parity, and from the ligands alone, so that a centre
            // pushed through the plane of its ligands, which the first would still call handed, is not guessed at
            Vec3 centre = positions.get(siteOfAtom.get(atom));
            double fromCentre = Vec3.handedness(centre, positions.get(ligands.get(1)), positions.get(ligands.get(2)), positions.get(ligands.get(3)));
            Vec3 first = positions.get(ligands.get(0));
            Vec3 toSecond = positions.get(ligands.get(1)).minus(first).normalised();
            Vec3 toThird = positions.get(ligands.get(2)).minus(first).normalised();
            Vec3 toFourth = positions.get(ligands.get(3)).minus(first).normalised();
            double fromLigands = toSecond.dot(toThird.cross(toFourth));
            if (Math.abs(fromCentre) < options.minimumHandedness() || Math.abs(fromLigands) < options.minimumHandedness()
                || Math.signum(fromCentre) != Math.signum(fromLigands))
            {
                issues.add(new Issue(Kind.FLAT_CENTRE, atom, "Atom " + atom + " has four different ligands but is too flat to read"));
                return;
            }
            centres.put(atom, fromCentre > 0 ? Chirality.CLOCKWISE : Chirality.ANTICLOCKWISE);
        }

        private void readDoubleBond(int index, int[] rank, Molecule plain, Map<Integer, DoubleBondStereo> doubleBonds)
        {
            Structure3D.Connection bond = bonds.get(index);
            if (bond.order() != BondOrder.DOUBLE)
            {
                return;
            }
            int first = atomOfSite[bond.first()];
            int second = atomOfSite[bond.second()];
            // a bond in a ring is left alone, as only the largest rings can hold a trans bond
            if (plain.ringBonds().contains(plain.bondBetween(plain.atoms().get(first), plain.atoms().get(second)).orElseThrow()))
            {
                return;
            }

            int firstReference = reference(first, second, rank);
            int secondReference = reference(second, first, rank);
            if (firstReference < 0 || secondReference < 0)
            {
                return;
            }

            double torsion = Vec3.torsion(positions.get(firstReference), positions.get(bond.first()), positions.get(bond.second()), positions.get(secondReference));
            double bend = Math.abs(torsion);
            if (bend <= options.torsionTolerance())
            {
                doubleBonds.put(index, DoubleBondStereo.SAME_SIDE);
            }
            else if (bend >= 180 - options.torsionTolerance())
            {
                doubleBonds.put(index, DoubleBondStereo.OPPOSITE_SIDE);
            }
            else
            {
                issues.add(new Issue(Kind.TWISTED_DOUBLE_BOND, first, "The double bond at atom " + first + " is too twisted to read"));
            }
        }

        /**
         * @return the site of the substituent of {@code end} the double bond is measured against, or -1 if the end
         *     cannot carry stereochemistry: its substituents are alike, it has none, or it is also double bonded elsewhere
         */
        private int reference(int end, int partner, int[] rank)
        {
            List<Integer> others = new ArrayList<>();
            for (int neighbour : neighbours.get(end))
            {
                if (neighbour != partner)
                {
                    others.add(siteOfAtom.get(neighbour));
                }
            }
            for (Structure3D.Connection bond : bonds)
            {
                int a = atomOfSite[bond.first()];
                int b = atomOfSite[bond.second()];
                if (bond.order() == BondOrder.DOUBLE && (a == end || b == end) && a != partner && b != partner)
                {
                    return -1;
                }
            }

            List<Integer> classes = new ArrayList<>();
            for (int other : others)
            {
                classes.add(rank[atomOfSite[other]]);
            }
            int hydrogenCount = hydrogens.get(end).size();
            if (others.size() + hydrogenCount > 2 || hydrogenCount > 1 || others.size() + hydrogenCount == 0)
            {
                return -1;
            }
            if (others.size() == 2 && classes.get(0).equals(classes.get(1)) || others.size() == 1 && hydrogenCount == 0 && !hasLonePairAllowed(end))
            {
                return -1;
            }
            if (!others.isEmpty())
            {
                // a molecule measures from the first bond other than the double bond, else the hydrogen
                return others.getFirst();
            }
            return hydrogens.get(end).getFirst();
        }

        /** An end with one substituent and no hydrogen still has stereochemistry if it carries a lone pair, as in an imine. */
        private boolean hasLonePairAllowed(int atom)
        {
            String element = structure.sites().get(siteOfAtom.get(atom)).element();
            return element.equals("N");
        }
    }
}
