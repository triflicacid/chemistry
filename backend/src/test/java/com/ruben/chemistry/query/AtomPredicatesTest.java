package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtomPredicatesTest
{
    @Nested
    class Any
    {
        @Test
        void matches_every_atom_regardless_of_element()
        {
            Molecule molecule = new SmilesParser("CO").parse();
            Atom carbon = firstAtom(molecule);
            Atom oxygen = secondAtom(molecule);

            assertThat(AtomPredicates.any().matches(molecule, carbon)).isTrue();
            assertThat(AtomPredicates.any().matches(molecule, oxygen)).isTrue();
        }
    }

    @Nested
    class Element
    {
        @Test
        void matches_atoms_of_exactly_that_element()
        {
            Molecule molecule = new SmilesParser("CO").parse();
            Atom carbon = firstAtom(molecule);
            Atom oxygen = secondAtom(molecule);

            assertThat(AtomPredicates.element("C").matches(molecule, carbon)).isTrue();
            assertThat(AtomPredicates.element("C").matches(molecule, oxygen)).isFalse();
        }
    }

    @Nested
    class AtomicNumber
    {
        @Test
        void matches_the_element_with_that_atomic_number()
        {
            Molecule molecule = new SmilesParser("C").parse();
            Atom carbon = firstAtom(molecule);

            assertThat(AtomPredicates.atomicNumber(6).matches(molecule, carbon)).isTrue();
            assertThat(AtomPredicates.atomicNumber(7).matches(molecule, carbon)).isFalse();
        }

        @Test
        void throws_for_an_unknown_atomic_number()
        {
            assertThatThrownBy(() -> AtomPredicates.atomicNumber(0))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Aromatic
    {
        @Test
        void matches_an_aromatic_atom_of_exactly_that_element()
        {
            Molecule benzene = new SmilesParser("c1ccccc1").parse();
            Atom aromaticCarbon = firstAtom(benzene);

            assertThat(AtomPredicates.aromatic("C").matches(benzene, aromaticCarbon)).isTrue();
        }

        @Test
        void does_not_match_a_non_aromatic_atom_of_the_same_element()
        {
            Molecule molecule = new SmilesParser("C").parse();
            Atom aliphaticCarbon = firstAtom(molecule);

            assertThat(AtomPredicates.aromatic("C").matches(molecule, aliphaticCarbon)).isFalse();
        }
    }

    @Nested
    class Charge
    {
        @Test
        void matches_an_atoms_formal_charge()
        {
            Atom charged = Atom.builder("N").charge(1).build();
            Molecule molecule = new Molecule.Builder().addAtom(charged).build();

            assertThat(AtomPredicates.charge(1).matches(molecule, charged)).isTrue();
            assertThat(AtomPredicates.charge(0).matches(molecule, charged)).isFalse();
        }

        @Test
        void matches_zero_for_an_atom_with_no_declared_charge()
        {
            Atom neutral = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(neutral).build();

            assertThat(AtomPredicates.charge(0).matches(molecule, neutral)).isTrue();
        }
    }

    @Nested
    class Isotope
    {
        @Test
        void matches_an_atoms_isotope_mass_number()
        {
            Atom labelled = Atom.builder("C").isotope(13).build();
            Molecule molecule = new Molecule.Builder().addAtom(labelled).build();

            assertThat(AtomPredicates.isotope(13).matches(molecule, labelled)).isTrue();
            assertThat(AtomPredicates.isotope(12).matches(molecule, labelled)).isFalse();
        }

        @Test
        void does_not_match_an_atom_with_no_declared_isotope()
        {
            Atom unlabelled = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(unlabelled).build();

            assertThat(AtomPredicates.isotope(12).matches(molecule, unlabelled)).isFalse();
        }
    }

    @Nested
    class AtomClass
    {
        @Test
        void matches_an_atoms_class_number()
        {
            Atom classified = Atom.builder("C").atomClass(1).build();
            Molecule molecule = new Molecule.Builder().addAtom(classified).build();

            assertThat(AtomPredicates.atomClass(1).matches(molecule, classified)).isTrue();
            assertThat(AtomPredicates.atomClass(2).matches(molecule, classified)).isFalse();
        }

        @Test
        void does_not_match_an_atom_with_no_declared_class()
        {
            Atom unclassified = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(unclassified).build();

            assertThat(AtomPredicates.atomClass(1).matches(molecule, unclassified)).isFalse();
        }
    }

    @Nested
    class InRing
    {
        @Test
        void matches_an_atom_with_a_ring_bond()
        {
            Molecule molecule = new SmilesParser("C1CC1").parse();
            Atom ringAtom = firstAtom(molecule);

            assertThat(AtomPredicates.inRing().matches(molecule, ringAtom)).isTrue();
        }

        @Test
        void does_not_match_an_acyclic_atom()
        {
            Molecule molecule = new SmilesParser("CCC").parse();
            Atom chainAtom = firstAtom(molecule);

            assertThat(AtomPredicates.inRing().matches(molecule, chainAtom)).isFalse();
        }
    }

    @Nested
    class NotInRing
    {
        @Test
        void matches_an_acyclic_atom()
        {
            Molecule molecule = new SmilesParser("CCC").parse();
            Atom chainAtom = firstAtom(molecule);

            assertThat(AtomPredicates.notInRing().matches(molecule, chainAtom)).isTrue();
        }

        @Test
        void does_not_match_an_atom_with_a_ring_bond()
        {
            Molecule molecule = new SmilesParser("C1CC1").parse();
            Atom ringAtom = firstAtom(molecule);

            assertThat(AtomPredicates.notInRing().matches(molecule, ringAtom)).isFalse();
        }
    }

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
    class ImplicitHydrogenCount
    {
        @Test
        void matches_an_organic_subset_atoms_inferred_count()
        {
            Molecule molecule = new SmilesParser("CC").parse(); // ethane: each carbon has 3 implicit H
            Atom carbon = firstAtom(molecule);

            assertThat(AtomPredicates.implicitHydrogenCount(3).matches(molecule, carbon)).isTrue();
        }

        @Test
        void matches_zero_for_a_bracket_atom_even_with_an_explicit_hydrogen_count()
        {
            Molecule molecule = new SmilesParser("[CH4]").parse();
            Atom explicit = firstAtom(molecule);

            assertThat(AtomPredicates.implicitHydrogenCount(0).matches(molecule, explicit)).isTrue();
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

    @Nested
    class RingCount
    {
        @Test
        void matches_zero_for_an_acyclic_atom()
        {
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2)
                .addBond(c1, c2, BondOrder.SINGLE)
                .build();

            assertThat(AtomPredicates.ringCount(0).matches(molecule, c1)).isTrue();
            assertThat(AtomPredicates.ringCount(1).matches(molecule, c1)).isFalse();
        }

        @Test
        void matches_one_for_an_atom_in_a_single_simple_ring()
        {
            // cyclopropane: C1CC1
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Atom c3 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2, c3)
                .addBond(c1, c2, BondOrder.SINGLE)
                .addBond(c2, c3, BondOrder.SINGLE)
                .addBond(c3, c1, BondOrder.SINGLE)
                .build();

            assertThat(AtomPredicates.ringCount(1).matches(molecule, c1)).isTrue();
        }

        @Test
        void matches_two_for_the_shared_atoms_of_two_fused_rings()
        {
            // C1C2CC12: two cyclopropane rings fused on the b-d edge
            Atom a = Atom.organic("C");
            Atom b = Atom.organic("C");
            Atom c = Atom.organic("C");
            Atom d = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a, b, c, d)
                .addBond(a, b, BondOrder.SINGLE)
                .addBond(b, c, BondOrder.SINGLE)
                .addBond(c, d, BondOrder.SINGLE)
                .addBond(d, a, BondOrder.SINGLE)
                .addBond(d, b, BondOrder.SINGLE)
                .build();

            assertThat(AtomPredicates.ringCount(2).matches(molecule, b)).isTrue();
            assertThat(AtomPredicates.ringCount(2).matches(molecule, d)).isTrue();
            assertThat(AtomPredicates.ringCount(1).matches(molecule, a)).isTrue();
            assertThat(AtomPredicates.ringCount(1).matches(molecule, c)).isTrue();
        }
    }

    @Nested
    class RingSize
    {
        @Test
        void matches_an_atoms_ring_size()
        {
            // cyclobutane: C1CCC1
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

            assertThat(AtomPredicates.ringSize(4).matches(molecule, c1)).isTrue();
            assertThat(AtomPredicates.ringSize(3).matches(molecule, c1)).isFalse();
        }

        @Test
        void does_not_match_an_acyclic_atom()
        {
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2)
                .addBond(c1, c2, BondOrder.SINGLE)
                .build();

            assertThat(AtomPredicates.ringSize(4).matches(molecule, c1)).isFalse();
        }
    }

    @Nested
    class Recursive
    {
        @Test
        void matches_an_atom_that_is_itself_the_anchor_of_a_matching_sub_pattern()
        {
            // $(C=O): a carbon double-bonded to oxygen - tests the carbon itself, without
            // pulling the oxygen into whatever outer match uses this predicate
            QueryAtom subCarbon = new QueryAtom(AtomPredicates.element("C"));
            QueryAtom subOxygen = new QueryAtom(AtomPredicates.element("O"));
            Query subPattern = new Query.Builder()
                .addAtom(subCarbon)
                .addAtom(subOxygen)
                .addBond(subCarbon, subOxygen, BondPredicates.order(BondOrder.DOUBLE))
                .build();

            AtomPredicate isCarbonylCarbon = AtomPredicates.recursive(subPattern);

            Molecule molecule = new SmilesParser("CC(=O)C").parse();
            Atom carbonylCarbon = secondAtom(molecule);
            Atom methylCarbon = firstAtom(molecule);
            Atom oxygen = thirdAtom(molecule);

            assertThat(isCarbonylCarbon.matches(molecule, carbonylCarbon)).isTrue();
            assertThat(isCarbonylCarbon.matches(molecule, methylCarbon)).isFalse();
            assertThat(isCarbonylCarbon.matches(molecule, oxygen)).isFalse();
        }

        @Test
        void throws_when_the_sub_pattern_has_no_atoms()
        {
            Query empty = new Query.Builder().build();

            assertThatThrownBy(() -> AtomPredicates.recursive(empty))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Exactly
    {
        @Test
        void matches_an_atom_with_the_same_element_and_aromaticity()
        {
            Atom template = Atom.organic("C");
            Atom sameShape = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(sameShape).build();

            assertThat(AtomPredicates.exactly(template).matches(molecule, sameShape)).isTrue();
        }

        @Test
        void does_not_match_a_different_element()
        {
            Atom template = Atom.organic("C");
            Atom nitrogen = Atom.organic("N");
            Molecule molecule = new Molecule.Builder().addAtom(nitrogen).build();

            assertThat(AtomPredicates.exactly(template).matches(molecule, nitrogen)).isFalse();
        }

        @Test
        void does_not_match_a_different_aromaticity()
        {
            Atom template = Atom.organic("C");
            Atom aromaticCarbon = Atom.organic("C", true);
            Molecule molecule = new Molecule.Builder().addAtom(aromaticCarbon).build();

            assertThat(AtomPredicates.exactly(template).matches(molecule, aromaticCarbon)).isFalse();
        }

        @Test
        void does_not_match_a_different_charge()
        {
            Atom template = Atom.organic("N");
            Atom charged = Atom.builder("N").charge(1).build();
            Molecule molecule = new Molecule.Builder().addAtom(charged).build();

            assertThat(AtomPredicates.exactly(template).matches(molecule, charged)).isFalse();
        }

        @Test
        void ignores_isotope_when_the_template_has_none()
        {
            Atom template = Atom.organic("C");
            Atom labelled = Atom.builder("C").isotope(13).build();
            Molecule molecule = new Molecule.Builder().addAtom(labelled).build();

            assertThat(AtomPredicates.exactly(template).matches(molecule, labelled)).isTrue();
        }

        @Test
        void requires_the_same_isotope_when_the_template_has_one()
        {
            Atom template = Atom.builder("C").isotope(13).build();
            Atom unlabelled = Atom.organic("C");
            Molecule molecule = new Molecule.Builder().addAtom(unlabelled).build();

            assertThat(AtomPredicates.exactly(template).matches(molecule, unlabelled)).isFalse();
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
