package com.ruben.chemistry.canonical;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.NeighbourReference;
import com.ruben.chemistry.smiles.SmilesParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalRankerTest
{
    @Test
    void ranks_a_single_atom_molecule()
    {
        Atom c = Atom.organic("C");
        Molecule molecule = new Molecule.Builder().addAtom(c).build();

        Ranking ranking = rank(molecule);

        assertThat(ranking.size()).isEqualTo(1);
        assertThat(ranking.rankOf(c)).isEqualTo(0);
    }

    @Test
    void ranks_an_empty_molecule()
    {
        Molecule molecule = new Molecule.Builder().build();

        assertThat(rank(molecule).size()).isZero();
    }

    @Test
    void throws_when_asked_to_rank_an_atom_outside_the_molecule()
    {
        Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.organic("C"))
                .build();

        Ranking ranking = rank(molecule);

        assertThatThrownBy(() -> ranking.rankOf(Atom.organic("C")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gives_every_atom_a_unique_dense_rank()
    {
        // Cyclobutane: four structurally equivalent carbons.
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom c3 = Atom.organic("C");
        Atom c4 = Atom.organic("C");

        Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3, c4)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, c3, BondOrder.SINGLE)
                .addBond(c3, c4, BondOrder.SINGLE)
                .addBond(c4, c1, BondOrder.SINGLE)
                .build();

        assertDenseUniqueRanks(molecule);
    }

    @Test
    void distinguishes_atoms_by_element_and_degree()
    {
        // CCO: all three atoms have different initial invariants.
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom o = Atom.organic("O");

        Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, o)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, o, BondOrder.SINGLE)
                .build();

        Ranking ranking = rank(molecule);

        assertThat(Set.of(
                ranking.rankOf(c1),
                ranking.rankOf(c2),
                ranking.rankOf(o)
        )).hasSize(3);
    }

    @Test
    void distinguishes_disconnected_atoms_by_charge()
    {
        Atom neutral = Atom.organic("C");
        Atom charged = Atom.builder("C").charge(1).build();

        Molecule molecule = new Molecule.Builder()
                .addAtoms(neutral, charged)
                .build();

        Ranking ranking = rank(molecule);

        assertThat(ranking.rankOf(neutral))
                .isNotEqualTo(ranking.rankOf(charged));
    }

    @Test
    void distinguishes_atoms_by_total_hydrogen_count()
    {
        // Both atoms are disconnected carbon atoms with degree zero and only vary on hydrogen count (4 vs 3)
        Molecule molecule = new SmilesParser("C.[CH3]").parse();

        Atom methane = molecule.atoms().stream()
                .filter(atom -> molecule.totalHydrogenCount(atom) == 4)
                .findFirst()
                .orElseThrow();

        Atom methyl = molecule.atoms().stream()
                .filter(atom -> molecule.totalHydrogenCount(atom) == 3)
                .findFirst()
                .orElseThrow();

        Ranking ranking = rank(molecule);

        assertThat(molecule.bondsOf(methane)).isEmpty();
        assertThat(molecule.bondsOf(methyl)).isEmpty();

        assertThat(ranking.rankOf(methane))
                .isNotEqualTo(ranking.rankOf(methyl));
    }

    @Test
    void distinguishes_atoms_by_isotope()
    {
        // Same; only the isotope differs.
        Molecule molecule = new SmilesParser("[13CH4].C").parse();

        Atom isotope = molecule.atoms().stream()
                .filter(atom -> atom.isotope().orElse(-1) == 13)
                .findFirst()
                .orElseThrow();

        Atom ordinary = molecule.atoms().stream()
                .filter(atom -> atom.isotope().isEmpty())
                .findFirst()
                .orElseThrow();

        Ranking ranking = rank(molecule);

        assertThat(ranking.rankOf(isotope))
                .isNotEqualTo(ranking.rankOf(ordinary));
    }

    @Test
    void distinguishes_different_bond_orders()
    {
        Molecule single = new SmilesParser("CC").parse();
        Molecule doubleBond = new SmilesParser("C=C").parse();

        assertThat(rankFingerprint(single))
                .isNotEqualTo(rankFingerprint(doubleBond));
    }

    @Test
    void preserves_aromaticity_in_canonical_rankings()
    {
        Molecule benzene = new SmilesParser("c1ccccc1").parse();

        assertThat(benzene.atoms())
                .allMatch(Atom::aromatic);

        assertThat(rebuiltFingerprints(benzene, 12))
                .containsOnly(rankFingerprint(benzene));
    }

    @Test
    void ranks_ring_atoms_consistently()
    {
        // Cyclopropanol: the carbon bonded to oxygen is unique.
        // The remaining two carbons are symmetry-equivalent.
        Molecule molecule = new SmilesParser("OC1CC1").parse();

        assertDenseUniqueRanks(molecule);

        Ranking ranking = rank(molecule);

        Atom oxygen = molecule.atoms().stream()
                .filter(atom -> atom.element().equals("O"))
                .findFirst()
                .orElseThrow();

        assertThat(ranking.rankOf(oxygen))
                .isNotEqualTo(ranking.rankOf(
                        molecule.atoms().stream()
                                .filter(atom -> atom.element().equals("C"))
                                .findFirst()
                                .orElseThrow()
                ));

        assertThat(rankFingerprint(molecule))
                .isEqualTo(rankFingerprint(rebuiltInRandomOrder(
                        molecule,
                        new Random(42)
                )));
    }

    @Test
    void distinguishes_atoms_across_different_ring_environments()
    {
        Molecule molecule = new SmilesParser("c1ccc2ccccc2c1").parse();

        assertDenseUniqueRanks(molecule);

        String expected = rankFingerprint(molecule);

        assertThat(rebuiltFingerprints(molecule, 12))
                .containsOnly(expected);
    }

    @Test
    void ranks_are_independent_of_atom_and_bond_insertion_order()
    {
        Molecule molecule = new SmilesParser("ClCCO").parse();
        String expected = rankFingerprint(molecule);

        Random random = new Random(42);

        for (int rebuild = 0; rebuild < 20; rebuild++)
        {
            assertThat(rankFingerprint(
                    rebuiltInRandomOrder(molecule, random)
            ))
                    .as("ranking after rebuild %d", rebuild)
                    .isEqualTo(expected);
        }
    }

    @Test
    void ranks_are_independent_of_build_order_when_ties_must_be_broken()
    {
        // Cl-C-C-C-C-Cl: symmetric, requiring tie-breaking.
        Molecule molecule = new SmilesParser("ClCCCCCCl").parse();

        String expected = rankFingerprint(molecule);

        Molecule reversed = new SmilesParser("ClCCCCCCl").parse();

        List<Atom> reversedAtoms = new ArrayList<>(reversed.atoms());
        List<Bond> reversedBonds = new ArrayList<>(reversed.bonds());

        Collections.reverse(reversedAtoms);
        Collections.reverse(reversedBonds);

        Molecule.Builder builder = new Molecule.Builder();
        reversedAtoms.forEach(builder::addAtom);
        reversedBonds.forEach(bond ->
                builder.addBond(
                        bond.second(),
                        bond.first(),
                        bond.order()
                )
        );

        assertThat(rankFingerprint(builder.build()))
                .isEqualTo(expected);
    }

    @Test
    void ranks_symmetric_cages_independently_of_build_order()
    {
        for (String smiles : List.of(
                "C12C3C4C1C5C4C3C25",       // Cubane
                "C1CC2CCC1CC2",             // Bicyclo[2.2.2]octane
                "C1C2CC3CC1CC(C2)C3",       // Adamantane
                "c1ccc2ccccc2c1",           // Naphthalene
                "C1CC2(CC1)CCCC2",          // Spiro[4.4]nonane
                "c1ccc2c(c1)ccc1ccccc12"    // Phenanthrene
        ))
        {
            Molecule molecule = new SmilesParser(smiles).parse();
            String expected = rankFingerprint(molecule);

            Random random = new Random(smiles.hashCode());

            for (int rebuild = 0; rebuild < 12; rebuild++)
            {
                assertThat(rankFingerprint(
                        rebuiltInRandomOrder(molecule, random)
                ))
                        .as("ranking of %s after rebuild %d", smiles, rebuild)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void produces_the_same_ranking_when_called_repeatedly()
    {
        Molecule molecule = new SmilesParser("C1CC2CCC1CC2").parse();

        CanonicalRanker ranker = new CanonicalRanker();

        String expected = rankFingerprint(molecule);

        for (int iteration = 0; iteration < 10; iteration++)
        {
            assertThat(rankFingerprint(ranker.rank(molecule), molecule))
                    .isEqualTo(expected);
        }
    }

    @Test
    void ranks_enantiomers_alike()
    {
        // mirror images share every structural role, so no ranking separates them; what tells them
        // apart is the parity read off a ranking, not the ranking itself
        Molecule anticlockwise = new SmilesParser("N[C@](F)(Cl)Br").parse();
        Molecule clockwise = new SmilesParser("N[C@@](F)(Cl)Br").parse();

        assertThat(rankFingerprint(anticlockwise))
                .isEqualTo(rankFingerprint(clockwise));
    }

    @Test
    void distinguishes_diastereomers_a_symmetric_constitution_ties_together()
    {
        Molecule meso = new SmilesParser("C[C@H](Br)[C@H](Br)C").parse();
        Molecule chiral = new SmilesParser("C[C@H](Br)[C@@H](Br)C").parse();

        assertThat(stereoFingerprint(meso))
                .isNotEqualTo(stereoFingerprint(chiral));
    }

    @Test
    void ranks_stereochemistry_independently_of_build_order()
    {
        for (String smiles : List.of(
                "N[C@](F)(Cl)Br",                   // bromochlorofluoromethanamine
                "C[C@H](Br)[C@H](Br)C",             // meso-2,3-dibromobutane
                "C[C@H](Br)[C@@H](Br)C",            // chiral 2,3-dibromobutane
                "OC(=O)[C@H](O)[C@H](O)C(=O)O",     // meso-tartaric acid
                "OC(=O)[C@H](O)[C@@H](O)C(=O)O",    // chiral tartaric acid
                "O[C@H]1CCCC[C@@H]1O",              // cis-cyclohexane-1,2-diol
                "O[C@H]1CCCC[C@H]1O",               // trans-cyclohexane-1,2-diol
                "C/C=C/C",                          // (E)-but-2-ene
                "C/C=C/C=C\\C",                     // (2E,4Z)-hexa-2,4-diene
                "C/C=C/CC/C=C\\C"                   // (2E,6Z)-octa-2,6-diene
        ))
        {
            Molecule molecule = new SmilesParser(smiles).parse();
            String expected = stereoFingerprint(molecule);

            Random random = new Random(smiles.hashCode());

            for (int rebuild = 0; rebuild < 20; rebuild++)
            {
                assertThat(stereoFingerprint(
                        rebuiltInRandomOrder(molecule, random)
                ))
                        .as("stereochemistry of %s after rebuild %d", smiles, rebuild)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void honours_stereochemistry_unless_told_to_ignore_it()
    {
        assertThat(new CanonicalRanker().isIgnoreStereochemistry()).isFalse();

        Molecule meso = new SmilesParser("C[C@H](Br)[C@H](Br)C").parse();
        CanonicalRanker ranker = new CanonicalRanker();

        String honoured = rankFingerprint(ranker.rank(meso), meso);
        String ignored = rankFingerprint(ranker.setIgnoreStereochemistry(true).rank(meso), meso);

        assertThat(ignored).isNotEqualTo(honoured);
    }

    @Test
    void ranks_stereoisomers_alike_when_ignoring_stereochemistry()
    {
        Molecule meso = new SmilesParser("C[C@H](Br)[C@H](Br)C").parse();
        Molecule chiral = new SmilesParser("C[C@H](Br)[C@@H](Br)C").parse();
        Molecule undeclared = new SmilesParser("CC(Br)C(Br)C").parse();

        Ranker ranker = new CanonicalRanker().setIgnoreStereochemistry(true);

        assertThat(rankFingerprint(ranker.rank(meso), meso))
                .isEqualTo(rankFingerprint(ranker.rank(undeclared), undeclared));
        assertThat(rankFingerprint(ranker.rank(chiral), chiral))
                .isEqualTo(rankFingerprint(ranker.rank(undeclared), undeclared));
    }

    /**
     * @param molecule the molecule to test
     * @return the molecule's ranking
     */
    private static Ranking rank(Molecule molecule)
    {
        return new CanonicalRanker().rank(molecule);
    }

    /**
     * Verifies that every atom receives a unique, dense rank.
     *
     * @param molecule the molecule to test
     */
    private static void assertDenseUniqueRanks(Molecule molecule)
    {
        Ranking ranking = rank(molecule);

        assertThat(ranking.size()).isEqualTo(molecule.atoms().size());

        assertThat(ranking.atomsByAscendingRank())
                .hasSize(molecule.atoms().size());

        assertThat(molecule.atoms().stream()
                .map(ranking::rankOf)
                .toList())
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.IntStream.range(0, molecule.atoms().size())
                                .boxed()
                                .toList()
                );
    }

    /**
     * @param molecule the molecule to describe
     * @return the molecule described using its canonical atom ranks
     */
    private static String rankFingerprint(Molecule molecule)
    {
        return rankFingerprint(rank(molecule), molecule);
    }

    /**
     * @param ranking the ranking to describe
     * @param molecule the molecule being described
     * @return a representation of the molecule in ascending rank order
     */
    private static String rankFingerprint(Ranking ranking, Molecule molecule)
    {
        StringBuilder description = new StringBuilder();

        for (Atom atom : ranking.atomsByAscendingRank())
        {
            description.append(atom.element())
                    .append(':')
                    .append(atom.aromatic())
                    .append(':')
                    .append(atom.charge())
                    .append(':')
                    .append(atom.isotope().orElse(-1))
                    .append(':')
                    .append(molecule.totalHydrogenCount(atom))
                    .append(':')
                    .append(ranking.rankOf(atom))
                    .append(':');

            molecule.bondsOf(atom).stream()
                    .map(bond ->
                            bond.order().ordinal() + "/" +
                                    ranking.rankOf(bond.getOther(atom))
                    )
                    .sorted()
                    .forEach(neighbour ->
                            description.append(neighbour).append(',')
                    );

            description.append(';');
        }

        return description.toString();
    }

    /**
     * @param molecule the molecule to rebuild
     * @param random the source of the new ordering
     * @return the same molecular graph with atoms and bonds inserted in shuffled order, carrying every
     *     configuration {@code molecule} declares, so that nothing but the build order differs
     */
    private static Molecule rebuiltInRandomOrder(
            Molecule molecule,
            Random random
    )
    {
        List<Atom> atoms = new ArrayList<>(molecule.atoms());
        List<Bond> bonds = new ArrayList<>(molecule.bonds());

        Collections.shuffle(atoms, random);
        Collections.shuffle(bonds, random);

        Molecule.Builder builder = new Molecule.Builder();

        atoms.forEach(builder::addAtom);

        bonds.forEach(bond ->
                builder.addBond(
                        bond.first(),
                        bond.second(),
                        bond.order()
                )
        );

        return carryStereochemistryOver(molecule, builder.build());
    }

    /**
     * Re-declares one molecule's stereochemistry on a rebuild of it. Insertion order decides what a
     * declaration means, and the rebuild's differs, so each is read against an order fixed by the
     * original's atom indices and re-declared against the rebuild's own.
     *
     * @param molecule the molecule to take the declarations from
     * @param rebuilt the same molecule built in a different order, with nothing declared yet
     * @return {@code rebuilt} with every configuration {@code molecule} declares
     */
    private static Molecule carryStereochemistryOver(Molecule molecule, Molecule rebuilt)
    {
        // atom references are shared, so an atom's index in the original names it in either molecule
        Map<Atom, Integer> indexOf = new IdentityHashMap<>();
        molecule.atoms().forEach(atom -> indexOf.put(atom, indexOf.size()));
        ToIntFunction<Atom> fixedKey = indexOf::get;

        Molecule.Builder builder = rebuilt.toBuilder();

        for (Atom atom : molecule.atoms())
        {
            if (molecule.chiralityOf(atom).isEmpty())
            {
                continue;
            }

            Chirality parity = molecule.chiralityOf(atom, neighbourOrder(molecule, atom, fixedKey)).orElseThrow();
            boolean reordered = isOddPermutation(
                    declaredOrder(rebuilt, atom),
                    neighbourOrder(rebuilt, atom, fixedKey)
            );
            builder.setChirality(atom, reordered ? parity.flip() : parity);
        }

        for (Bond bond : molecule.bonds())
        {
            if (molecule.doubleBondStereoOf(bond).isEmpty())
            {
                continue;
            }

            DoubleBondStereo configuration = molecule.doubleBondStereoOf(
                    bond,
                    reference(molecule, bond, bond.first(), fixedKey),
                    reference(molecule, bond, bond.second(), fixedKey)
            ).orElseThrow();

            Bond rebuiltBond = rebuilt.bondBetween(bond.first(), bond.second()).orElseThrow();
            boolean remeasured =
                    (reference(rebuilt, rebuiltBond, bond.first(), fixedKey) != defaultReference(rebuilt, rebuiltBond, bond.first()))
                            ^ (reference(rebuilt, rebuiltBond, bond.second(), fixedKey) != defaultReference(rebuilt, rebuiltBond, bond.second()));
            builder.setDoubleBondStereo(bond.first(), bond.second(), remeasured ? configuration.flip() : configuration);
        }

        return builder.build();
    }

    /**
     * @param molecule the molecule to describe
     * @return the molecule described using its canonical atom ranks, with every declared configuration
     *     read against those ranks
     */
    private static String stereoFingerprint(Molecule molecule)
    {
        Ranking ranking = rank(molecule);
        ToIntFunction<Atom> rankKey = ranking::rankOf;

        StringBuilder description = new StringBuilder(rankFingerprint(ranking, molecule));

        for (Atom atom : ranking.atomsByAscendingRank())
        {
            molecule.chiralityOf(atom, neighbourOrder(molecule, atom, rankKey)).ifPresent(parity ->
                    description.append(ranking.rankOf(atom)).append(':').append(parity).append(';')
            );
        }

        molecule.bonds().stream()
                .filter(bond -> molecule.doubleBondStereoOf(bond).isPresent())
                .map(bond ->
                {
                    int first = ranking.rankOf(bond.first());
                    int second = ranking.rankOf(bond.second());
                    DoubleBondStereo configuration = molecule.doubleBondStereoOf(
                            bond,
                            reference(molecule, bond, bond.first(), rankKey),
                            reference(molecule, bond, bond.second(), rankKey)
                    ).orElseThrow();
                    return Math.min(first, second) + "=" + Math.max(first, second) + ":" + configuration + ";";
                })
                .sorted()
                .forEach(description::append);

        return description.toString();
    }

    /**
     * @param molecule the molecule {@code atom} belongs to
     * @param atom the atom whose neighbours to order
     * @param keyOf the key to order {@code atom}'s neighbouring atoms by
     * @return {@code atom}'s neighbours in ascending key order, its implicit hydrogen leading
     */
    private static List<NeighbourReference> neighbourOrder(Molecule molecule, Atom atom, ToIntFunction<Atom> keyOf)
    {
        List<NeighbourReference> order = new ArrayList<>();

        // the implicit hydrogen is not an atom and so has no key, which leaves it the first slot
        if (atom.hydrogenCount().orElse(0) > 0)
        {
            order.add(Molecule.IMPLICIT_HYDROGEN);
        }

        molecule.bondsOf(atom).stream()
                .sorted(Comparator.comparingInt(bond -> keyOf.applyAsInt(bond.getOther(atom))))
                .forEach(order::add);
        return order;
    }

    /**
     * @param molecule the molecule {@code atom} belongs to
     * @param atom the atom whose neighbours to order
     * @return the neighbour order {@link Molecule#chiralityOf(Atom)} is relative to: {@code atom}'s
     *     implicit hydrogen, then its bonds in insertion order
     */
    private static List<NeighbourReference> declaredOrder(Molecule molecule, Atom atom)
    {
        List<NeighbourReference> order = new ArrayList<>();
        if (atom.hydrogenCount().orElse(0) > 0)
        {
            order.add(Molecule.IMPLICIT_HYDROGEN);
        }
        order.addAll(molecule.bondsOf(atom));
        return order;
    }

    /**
     * @param molecule the molecule {@code doubleBond} belongs to
     * @param doubleBond the bond whose configuration is being read
     * @param atom the end of {@code doubleBond} to measure from
     * @param keyOf the key to order {@code atom}'s neighbouring atoms by
     * @return the substituent of {@code atom} to measure against: its first neighbour in that order
     *     other than {@code doubleBond} itself
     */
    private static NeighbourReference reference(Molecule molecule, Bond doubleBond, Atom atom, ToIntFunction<Atom> keyOf)
    {
        return neighbourOrder(molecule, atom, keyOf).stream()
                .filter(candidate -> candidate != doubleBond)
                .findFirst()
                .orElse(Molecule.IMPLICIT_HYDROGEN);
    }

    /**
     * @param molecule the molecule {@code doubleBond} belongs to
     * @param doubleBond the bond whose configuration is being read
     * @param atom the end of {@code doubleBond} to measure from
     * @return the substituent {@link Molecule#doubleBondStereoOf(Bond)} measures that end against
     */
    private static NeighbourReference defaultReference(Molecule molecule, Bond doubleBond, Atom atom)
    {
        return molecule.bondsOf(atom).stream()
                .filter(candidate -> candidate != doubleBond)
                .<NeighbourReference>map(candidate -> candidate)
                .findFirst()
                .orElse(Molecule.IMPLICIT_HYDROGEN);
    }

    /**
     * @param from a list of neighbour slots
     * @param to the same slots, in a possibly different order
     * @return {@code true} if rearranging {@code from} into {@code to} is an odd permutation
     */
    private static boolean isOddPermutation(List<NeighbourReference> from, List<NeighbourReference> to)
    {
        Map<NeighbourReference, Integer> positionInTo = new IdentityHashMap<>();
        for (int position = 0; position < to.size(); position++)
        {
            positionInTo.put(to.get(position), position);
        }

        int inversions = 0;
        for (int first = 0; first < from.size(); first++)
        {
            for (int second = first + 1; second < from.size(); second++)
            {
                if (positionInTo.get(from.get(first)) > positionInTo.get(from.get(second)))
                {
                    inversions++;
                }
            }
        }
        return inversions % 2 != 0;
    }

    /**
     * @param molecule the molecule to rebuild repeatedly
     * @param count how many shuffled rebuilds to produce
     * @return fingerprints of the shuffled molecules
     */
    private static List<String> rebuiltFingerprints(
            Molecule molecule,
            int count
    )
    {
        List<String> fingerprints = new ArrayList<>();
        Random random = new Random(42);

        for (int rebuild = 0; rebuild < count; rebuild++)
        {
            fingerprints.add(rankFingerprint(
                    rebuiltInRandomOrder(molecule, random)
            ));
        }

        return fingerprints;
    }
}
