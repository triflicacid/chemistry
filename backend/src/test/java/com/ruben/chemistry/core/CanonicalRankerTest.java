package com.ruben.chemistry.core;

import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
    void currently_does_not_distinguish_stereoisomers()
    {
        // Stereochemistry is deliberately not part of the current ranking.
        Molecule clockwise = new SmilesParser("N[C@](F)(Cl)Br").parse();
        Molecule anticlockwise = new SmilesParser("N[C@@](F)(Cl)Br").parse();

        assertThat(rankFingerprint(clockwise))
                .isEqualTo(rankFingerprint(anticlockwise));
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
     * @return the same molecular graph with atoms and bonds inserted in shuffled order
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

        return builder.build();
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
