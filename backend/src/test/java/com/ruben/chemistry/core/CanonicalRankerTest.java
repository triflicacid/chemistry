package com.ruben.chemistry.core;

import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
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
        Molecule molecule = new Molecule.Builder().addAtom(Atom.organic("C")).build();
        Ranking ranking = rank(molecule);

        assertThatThrownBy(() -> ranking.rankOf(Atom.organic("C"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gives_every_atom_a_unique_dense_rank()
    {
        // cyclobutane: four structurally equivalent carbons, so refinement alone can't
        // tell them apart, but tie-breaking must still resolve them to unique ranks
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

        Ranking ranking = rank(molecule);

        assertThat(ranking.size()).isEqualTo(4);
        assertThat(Set.of(ranking.rankOf(c1), ranking.rankOf(c2), ranking.rankOf(c3), ranking.rankOf(c4))).isEqualTo(Set.of(0, 1, 2, 3));
    }

    @Test
    void distinguishes_atoms_by_element_and_degree()
    {
        // CCO: the two carbons already differ in degree, and the oxygen differs in element,
        // so all three should end up in distinct classes without needing any tie-break
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom o = Atom.organic("O");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2, o)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(c2, o, BondOrder.SINGLE)
            .build();

        Ranking ranking = rank(molecule);

        assertThat(Set.of(ranking.rankOf(c1), ranking.rankOf(c2), ranking.rankOf(o))).hasSize(3);
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

        assertThat(ranking.rankOf(neutral)).isNotEqualTo(ranking.rankOf(charged));
    }

    @Test
    void marks_ring_atoms_as_distinct_from_a_chain_atom_of_otherwise_equal_invariant()
    {
        // cyclopropanol: C1 (bonded to O) is already unique, but C2 and C3 only
        // differ from a hypothetical chain carbon by being part of a ring
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Atom c3 = Atom.organic("C");
        Atom o = Atom.organic("O");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2, c3, o)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(c2, c3, BondOrder.SINGLE)
            .addBond(c3, c1, BondOrder.SINGLE)
            .addBond(c1, o, BondOrder.SINGLE)
            .build();

        Ranking ranking = rank(molecule);

        assertThat(ranking.size()).isEqualTo(4);
        assertThat(Set.of(ranking.rankOf(c1), ranking.rankOf(c2), ranking.rankOf(c3), ranking.rankOf(o))).isEqualTo(Set.of(0, 1, 2, 3));
        // c1 is unique (bonded to O); c2 and c3 are tied until tie-break splits them,
        // but both must remain distinguishable from c1
        assertThat(ranking.rankOf(c1)).isNotEqualTo(ranking.rankOf(c2));
        assertThat(ranking.rankOf(c1)).isNotEqualTo(ranking.rankOf(c3));
    }

    @Test
    void ranks_are_independent_of_atom_and_bond_insertion_order()
    {
        // Cl-C-C-O, fully asymmetric: every atom should land in the same relative
        // rank order whichever end of the chain is built first
        Atom cl1 = Atom.organic("Cl");
        Atom c1a = Atom.organic("C");
        Atom c2a = Atom.organic("C");
        Atom o1 = Atom.organic("O");
        Molecule forwardMolecule = new Molecule.Builder()
            .addAtoms(cl1, c1a, c2a, o1)
            .addBond(cl1, c1a, BondOrder.SINGLE)
            .addBond(c1a, c2a, BondOrder.SINGLE)
            .addBond(c2a, o1, BondOrder.SINGLE)
            .build();

        Atom o = Atom.organic("O");
        Atom c2 = Atom.organic("C");
        Atom c1 = Atom.organic("C");
        Atom cl = Atom.organic("Cl");
        Molecule backwardMolecule = new Molecule.Builder()
            .addAtoms(o, c2, c1, cl)
            .addBond(c2, o, BondOrder.SINGLE)
            .addBond(c1, c2, BondOrder.SINGLE)
            .addBond(cl, c1, BondOrder.SINGLE)
            .build();

        assertThat(elementsByAscendingRank(forwardMolecule)).isEqualTo(elementsByAscendingRank(backwardMolecule));
    }

    @Test
    void ranks_are_independent_of_build_order_when_ties_must_be_broken()
    {
        // Cl-C-C-C-C-Cl: symmetric, so we have three ties
        Atom cl1 = Atom.organic("Cl");
        Atom c1a = Atom.organic("C");
        Atom c2a = Atom.organic("C");
        Atom c3a = Atom.organic("C");
        Atom c4a = Atom.organic("C");
        Atom cl2 = Atom.organic("Cl");
        Molecule forwardMolecule = new Molecule.Builder()
            .addAtoms(cl1, c1a, c2a, c3a, c4a, cl2)
            .addBond(cl1, c1a, BondOrder.SINGLE)
            .addBond(c1a, c2a, BondOrder.SINGLE)
            .addBond(c2a, c3a, BondOrder.SINGLE)
            .addBond(c3a, c4a, BondOrder.SINGLE)
            .addBond(c4a, cl2, BondOrder.SINGLE)
            .build();

        Atom cl2b = Atom.organic("Cl");
        Atom c4b = Atom.organic("C");
        Atom c3b = Atom.organic("C");
        Atom c2b = Atom.organic("C");
        Atom c1b = Atom.organic("C");
        Atom cl1b = Atom.organic("Cl");
        Molecule backwardMolecule = new Molecule.Builder()
            .addAtoms(cl2b, c4b, c3b, c2b, c1b, cl1b)
            .addBond(c4b, cl2b, BondOrder.SINGLE)
            .addBond(c3b, c4b, BondOrder.SINGLE)
            .addBond(c2b, c3b, BondOrder.SINGLE)
            .addBond(c1b, c2b, BondOrder.SINGLE)
            .addBond(cl1b, c1b, BondOrder.SINGLE)
            .build();

        assertThat(elementsByAscendingRank(forwardMolecule)).isEqualTo(elementsByAscendingRank(backwardMolecule));
    }

    @Test
    void ranks_symmetric_cages_independently_of_build_order()
    {
        // cages are the hard case: refinement cannot separate their atoms at all, so the ranking
        // rests entirely on tie-breaking, and any atom property that varies with build order shows up
        for (String smiles : List.of(
            "C12C3C4C1C5C4C3C25",       // cubane
            "C1CC2CCC1CC2",             // bicyclo[2.2.2]octane
            "C1C2CC3CC1CC(C2)C3",       // adamantane
            "c1ccc2ccccc2c1",           // naphthalene
            "C1CC2(CC1)CCCC2",          // spiro[4.4]nonane
            "c1ccc2c(c1)ccc1ccccc12"))  // phenanthrene
        {
            Molecule molecule = new SmilesParser(smiles).parse();
            String expected = rankFingerprint(molecule);

            Random random = new Random(smiles.hashCode());
            for (int rebuild = 0; rebuild < 12; rebuild++)
            {
                assertThat(rankFingerprint(rebuiltInRandomOrder(molecule, random)))
                    .describedAs("ranking of %s after rebuild %d", smiles, rebuild)
                    .isEqualTo(expected);
            }
        }
    }

    /**
     * @param molecule the molecule to describe
     * @return the molecule described only by rank, so two rankings of the same molecule agree
     *     exactly when they are the same ranking
     */
    private static String rankFingerprint(Molecule molecule)
    {
        Ranking ranking = new CanonicalRanker().rank(molecule);

        StringBuilder description = new StringBuilder();
        for (Atom atom : ranking.atomsByAscendingRank())
        {
            description.append(atom.element()).append(ranking.rankOf(atom)).append(':');
            molecule.bondsOf(atom).stream()
                .map(bond -> bond.order().ordinal() + "/" + ranking.rankOf(bond.getOther(atom)))
                .sorted()
                .forEach(neighbour -> description.append(neighbour).append(','));
            description.append(';');
        }
        return description.toString();
    }

    /**
     * @param molecule the molecule to rebuild
     * @param random the source of the new ordering
     * @return the same atoms and bonds, added to a fresh molecule in a shuffled order
     */
    private static Molecule rebuiltInRandomOrder(Molecule molecule, Random random)
    {
        List<Atom> atoms = new ArrayList<>(molecule.atoms());
        List<Bond> bonds = new ArrayList<>(molecule.bonds());
        Collections.shuffle(atoms, random);
        Collections.shuffle(bonds, random);

        Molecule.Builder builder = new Molecule.Builder();
        atoms.forEach(builder::addAtom);
        bonds.forEach(bond -> builder.addBond(bond.first(), bond.second(), bond.order()));
        return builder.build();
    }

    private static Ranking rank(Molecule molecule)
    {
        return new CanonicalRanker().rank(molecule);
    }

    private static List<String> elementsByAscendingRank(Molecule molecule)
    {
        return rank(molecule)
                .atomsByAscendingRank()
                .stream()
                .map(Atom::element)
                .toList();
    }
}
