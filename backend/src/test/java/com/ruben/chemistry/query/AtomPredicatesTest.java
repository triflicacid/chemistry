package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtomPredicatesTest
{
    @Nested
    class Degree
    {
        @Test
        void matches_an_atom_with_exactly_that_many_bonds()
        {
            // acetone: CC(=O)C
            Molecule molecule = new SmilesParser("CC(=O)C").parse();
            Atom carbonylCarbon = secondAtom(molecule);

            assertThat(AtomPredicates.degree(3).matches(molecule, carbonylCarbon)).isTrue();
            assertThat(AtomPredicates.degree(2).matches(molecule, carbonylCarbon)).isFalse();
        }

        @Test
        void counts_each_bond_once_regardless_of_its_order()
        {
            // the carbonyl oxygen has a single double bond
            Molecule molecule = new SmilesParser("CC(=O)C").parse();
            Atom oxygen = thirdAtom(molecule);

            assertThat(AtomPredicates.degree(1).matches(molecule, oxygen)).isTrue();
        }

        @Test
        void matches_zero_for_an_unbonded_atom()
        {
            Molecule molecule = new SmilesParser("C").parse();
            Atom methane = firstAtom(molecule);

            assertThat(AtomPredicates.degree(0).matches(molecule, methane)).isTrue();
        }
    }

    @Nested
    class HydrogenCount
    {
        @Test
        void matches_an_organic_subset_atoms_inferred_count()
        {
            Molecule molecule = new SmilesParser("CC").parse(); // ethane: each carbon has 3 implicit H
            Atom carbon = firstAtom(molecule);

            assertThat(AtomPredicates.hydrogenCount(3).matches(molecule, carbon)).isTrue();
            assertThat(AtomPredicates.hydrogenCount(4).matches(molecule, carbon)).isFalse();
        }

        @Test
        void matches_a_bracket_atoms_explicit_count()
        {
            Molecule molecule = new SmilesParser("[CH2]=O").parse();
            Atom explicit = firstAtom(molecule);

            assertThat(AtomPredicates.hydrogenCount(2).matches(molecule, explicit)).isTrue();
        }

        @Test
        void distinguishes_a_carboxylic_acids_hydroxyl_oxygen_from_its_carbonyl_oxygen()
        {
            // tell an -OH apart from a =O with nothing but element and bond order
            Molecule aceticAcid = new SmilesParser("CC(=O)O").parse();
            Atom carbonylOxygen = thirdAtom(aceticAcid);
            Atom hydroxylOxygen = fourthAtom(aceticAcid);

            assertThat(AtomPredicates.hydrogenCount(0).matches(aceticAcid, carbonylOxygen)).isTrue();
            assertThat(AtomPredicates.hydrogenCount(1).matches(aceticAcid, hydroxylOxygen)).isTrue();
        }

        @Test
        void matches_zero_for_a_fused_aromatic_heteroatom_rather_than_the_naive_aromatic_weighting()
        {
            // thiophene: a 2-connected aromatic sulfur has no hydrogen, naive summing would say 1.5
            Molecule thiophene = new SmilesParser("c1ccsc1").parse();
            Atom sulfur = fourthAtom(thiophene);

            assertThat(AtomPredicates.hydrogenCount(0).matches(thiophene, sulfur)).isTrue();
        }
    }

    @Nested
    class Valence
    {
        @Test
        void matches_an_atoms_total_valence()
        {
            Molecule molecule = new SmilesParser("CC").parse(); // ethane: 1 bond + 3 implicit H
            Atom carbon = firstAtom(molecule);

            assertThat(AtomPredicates.valence(4).matches(molecule, carbon)).isTrue();
            assertThat(AtomPredicates.valence(3).matches(molecule, carbon)).isFalse();
        }

        @Test
        void matches_the_higher_real_total_for_a_two_connected_aromatic_sulfur()
        {
            // thiophene: c1ccsc1 - sulfur's two aromatic bonds alone already exceed its
            // only default valence, so its valence (3) is higher than that default (2)
            Molecule thiophene = new SmilesParser("c1ccsc1").parse();
            Atom sulfur = fourthAtom(thiophene);

            assertThat(AtomPredicates.valence(3).matches(thiophene, sulfur)).isTrue();
        }
    }

    @Nested
    class Connectivity
    {
        @Test
        void counts_bonds_and_hydrogens_together()
        {
            // acetone's carbonyl carbon: 3 bonds (2 C, 1 O), 0 hydrogens
            Molecule molecule = new SmilesParser("CC(=O)C").parse();
            Atom carbonylCarbon = secondAtom(molecule);

            assertThat(AtomPredicates.connectivity(3).matches(molecule, carbonylCarbon)).isTrue();
        }

        @Test
        void distinguishes_connectivity_from_degree_when_hydrogens_are_present()
        {
            Molecule molecule = new SmilesParser("CC").parse(); // ethane: 1 bond + 3 implicit H

            Atom carbon = firstAtom(molecule);

            assertThat(AtomPredicates.degree(1).matches(molecule, carbon)).isTrue();
            assertThat(AtomPredicates.connectivity(1).matches(molecule, carbon)).isFalse();
            assertThat(AtomPredicates.connectivity(4).matches(molecule, carbon)).isTrue();
        }
    }

    private static Atom firstAtom(Molecule molecule)
    {
        return molecule.atoms().iterator().next();
    }

    private static Atom secondAtom(Molecule molecule)
    {
        return nthAtom(molecule, 1);
    }

    private static Atom thirdAtom(Molecule molecule)
    {
        return nthAtom(molecule, 2);
    }

    private static Atom fourthAtom(Molecule molecule)
    {
        return nthAtom(molecule, 3);
    }

    private static Atom nthAtom(Molecule molecule, int index)
    {
        int i = 0;
        for (Atom atom : molecule.atoms())
        {
            if (i == index)
            {
                return atom;
            }
            i++;
        }
        throw new IllegalArgumentException("Molecule has no atom at index " + index);
    }
}
