package com.ruben.chemistry.smiles;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmilesParserTest
{
    private static Molecule parse(String smiles)
    {
        return new SmilesParser(smiles).parse();
    }

    private static List<Atom> atomsOf(Molecule molecule)
    {
        List<Atom> atoms = new ArrayList<>();
        molecule.atoms().forEach(atoms::add);
        return atoms;
    }

    private static BondOrder orderBetween(Molecule molecule, Atom first, Atom second)
    {
        for (Bond bond : molecule.bondsOf(first))
        {
            if (bond.getOther(first) == second)
            {
                return bond.order();
            }
        }
        throw new AssertionError("No bond between given atoms");
    }

    @Nested
    class OrganicAtoms
    {
        @Test
        void parses_a_single_atom()
        {
            Molecule molecule = parse("C");

            assertThat(molecule.atomCount()).isEqualTo(1);
            assertThat(atomsOf(molecule).getFirst().element()).isEqualTo("C");
        }

        @Test
        void parses_a_chain_with_default_single_bonds()
        {
            Molecule molecule = parse("CCO");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(atoms).extracting(Atom::element).containsExactly("C", "C", "O");
            assertThat(molecule.bondCount()).isEqualTo(2);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, atoms.get(1), atoms.get(2))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void parses_two_character_organic_atoms()
        {
            Molecule molecule = parse("ClBr");

            assertThat(atomsOf(molecule)).extracting(Atom::element).containsExactly("Cl", "Br");
        }

        @Test
        void parses_a_wildcard_atom()
        {
            Molecule molecule = parse("*");

            assertThat(atomsOf(molecule).getFirst().element()).isEqualTo("*");
        }

        @Test
        void parses_lowercase_organic_atoms_as_aromatic()
        {
            Molecule molecule = parse("c");

            assertThat(atomsOf(molecule).getFirst().aromatic()).isTrue();
        }

        @Test
        void infers_an_aromatic_bond_between_two_aromatic_atoms()
        {
            Molecule molecule = parse("cc");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.AROMATIC);
        }

        @Test
        void infers_a_single_bond_between_a_non_aromatic_atom_and_an_aromatic_atom()
        {
            Molecule molecule = parse("Cc");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.SINGLE);
        }
    }

    @Nested
    class ExplicitBonds
    {
        @Test
        void parses_a_double_bond()
        {
            Molecule molecule = parse("C=C");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.DOUBLE);
        }

        @Test
        void parses_a_triple_bond()
        {
            Molecule molecule = parse("C#C");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.TRIPLE);
        }

        @Test
        void parses_a_quadruple_bond()
        {
            Molecule molecule = parse("C$C");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.QUADRUPLE);
        }

        @Test
        void parses_an_explicit_aromatic_bond()
        {
            Molecule molecule = parse("C:C");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.AROMATIC);
        }

        @Test
        void an_explicit_single_bond_overrides_aromatic_inference()
        {
            Molecule molecule = parse("c-c");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void parses_a_directional_bond_as_a_single_bond()
        {
            // the stereo configuration this '/' implies is covered by DoubleBondStereoParsing;
            // here we're just confirming it doesn't change the bond order itself
            Molecule molecule = parse("C/C");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void throws_when_a_bond_symbol_is_not_followed_by_an_atom()
        {
            assertThatThrownBy(() -> parse("C="))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Bond symbol not followed by an atom");
        }

        @Test
        void throws_when_a_bond_symbol_has_no_preceding_atom()
        {
            assertThatThrownBy(() -> parse("=C"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Bond symbol not preceded by an atom");
        }

        @Test
        void throws_when_a_bond_symbol_is_immediately_followed_by_an_unclosed_branch()
        {
            // the pending '=' is never attached to an atom, but the unmatched '(' is
            // detected first since it is still open when the input is exhausted
            assertThatThrownBy(() -> parse("C=("))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unmatched '('");
        }

        @Test
        void throws_when_a_bond_symbol_is_followed_by_an_empty_branch()
        {
            // the branch opens and closes with no atom in between, so the pending '='
            // is still dangling once the branch is closed and the input is exhausted
            assertThatThrownBy(() -> parse("C=()"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Bond symbol not followed by an atom");
        }
    }

    @Nested
    class Branches
    {
        @Test
        void parses_a_simple_branch()
        {
            // CC(O)C : propan-2-ol skeleton
            Molecule molecule = parse("CC(O)C");

            List<Atom> atoms = atomsOf(molecule);
            Atom c1 = atoms.get(0);
            Atom c2 = atoms.get(1);
            Atom o = atoms.get(2);
            Atom c3 = atoms.get(3);

            assertThat(molecule.bondCount()).isEqualTo(3);
            assertThat(orderBetween(molecule, c1, c2)).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, c2, o)).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, c2, c3)).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void parses_multiple_sibling_branches()
        {
            // C(F)(Cl)Br
            Molecule molecule = parse("C(F)(Cl)Br");

            List<Atom> atoms = atomsOf(molecule);
            Atom c = atoms.get(0);
            Atom f = atoms.get(1);
            Atom cl = atoms.get(2);
            Atom br = atoms.get(3);

            assertThat(molecule.bondCount()).isEqualTo(3);
            assertThat(orderBetween(molecule, c, f)).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, c, cl)).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, c, br)).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void parses_nested_branches()
        {
            // CC(C(F)Cl)O
            Molecule molecule = parse("CC(C(F)Cl)O");

            List<Atom> atoms = atomsOf(molecule);
            Atom c1 = atoms.get(0);
            Atom c2 = atoms.get(1);
            Atom c3 = atoms.get(2);
            Atom f = atoms.get(3);
            Atom cl = atoms.get(4);
            Atom o = atoms.get(5);

            assertThat(orderBetween(molecule, c1, c2)).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, c2, c3)).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, c3, f)).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, c3, cl)).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, c2, o)).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void parses_a_branch_with_an_explicit_bond()
        {
            Molecule molecule = parse("C(=O)O");

            List<Atom> atoms = atomsOf(molecule);
            Atom c = atoms.get(0);
            Atom o1 = atoms.get(1);
            Atom o2 = atoms.get(2);

            assertThat(orderBetween(molecule, c, o1)).isEqualTo(BondOrder.DOUBLE);
            assertThat(orderBetween(molecule, c, o2)).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void throws_when_a_branch_opens_with_no_preceding_atom()
        {
            assertThatThrownBy(() -> parse("(C)C"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Branch opened with no preceding atom");
        }

        @Test
        void throws_for_an_unmatched_closing_parenthesis()
        {
            assertThatThrownBy(() -> parse("C)C"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unmatched ')'");
        }

        @Test
        void throws_for_an_unmatched_opening_parenthesis()
        {
            assertThatThrownBy(() -> parse("C(C"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unmatched '('");
        }
    }

    @Nested
    class DisconnectedStructures
    {
        @Test
        void parses_a_dot_as_disconnecting_two_fragments()
        {
            Molecule molecule = parse("C.O");

            assertThat(molecule.atomCount()).isEqualTo(2);
            assertThat(molecule.bondCount()).isZero();
        }

        @Test
        void resumes_bonding_within_the_fragment_following_a_dot()
        {
            Molecule molecule = parse("CC.OC");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(molecule.bondCount()).isEqualTo(2);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, atoms.get(2), atoms.get(3))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void throws_when_a_pending_bond_symbol_precedes_a_dot()
        {
            assertThatThrownBy(() -> parse("C=.C"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Bond symbol not followed by an atom");
        }
    }

    @Nested
    class BracketAtoms
    {
        @Test
        void parses_a_bare_bracket_atom()
        {
            Molecule molecule = parse("[C]");

            Atom atom = atomsOf(molecule).getFirst();
            assertThat(atom.element()).isEqualTo("C");
            assertThat(atom.charge()).isZero();
            assertThat(atom.isotope()).isEmpty();
            assertThat(atom.hydrogenCount()).hasValue(0);
            assertThat(atom.atomClass()).isEmpty();
        }

        @Test
        void parses_an_isotope()
        {
            Molecule molecule = parse("[13C]");

            assertThat(atomsOf(molecule).getFirst().isotope()).hasValue(13);
        }

        @Test
        void parses_an_implicit_single_hydrogen()
        {
            Molecule molecule = parse("[CH]");

            assertThat(atomsOf(molecule).getFirst().hydrogenCount()).hasValue(1);
        }

        @Test
        void parses_an_explicit_hydrogen_count()
        {
            Molecule molecule = parse("[CH4]");

            assertThat(atomsOf(molecule).getFirst().hydrogenCount()).hasValue(4);
        }

        @Test
        void parses_a_charge_from_a_single_sign_and_a_number()
        {
            Molecule molecule = parse("[N+3]");

            assertThat(atomsOf(molecule).getFirst().charge()).isEqualTo(3);
        }

        @Test
        void parses_a_charge_from_a_single_sign_with_no_number()
        {
            Molecule molecule = parse("[NH4+]");

            assertThat(atomsOf(molecule).getFirst().charge()).isEqualTo(1);
        }

        @Test
        void parses_a_negative_charge()
        {
            Molecule molecule = parse("[O-]");

            assertThat(atomsOf(molecule).getFirst().charge()).isEqualTo(-1);
        }

        @Test
        void parses_a_charge_from_repeated_signs()
        {
            Molecule molecule = parse("[Fe++]");

            assertThat(atomsOf(molecule).getFirst().charge()).isEqualTo(2);
        }

        @Test
        void parses_an_atom_class()
        {
            Molecule molecule = parse("[CH4:1]");

            assertThat(atomsOf(molecule).getFirst().atomClass()).hasValue(1);
        }

        @Test
        void parses_all_bracket_fields_together_in_order()
        {
            Molecule molecule = parse("[13CH3+:5]");

            Atom atom = atomsOf(molecule).getFirst();
            assertThat(atom.isotope()).hasValue(13);
            assertThat(atom.element()).isEqualTo("C");
            assertThat(atom.hydrogenCount()).hasValue(3);
            assertThat(atom.charge()).isEqualTo(1);
            assertThat(atom.atomClass()).hasValue(5);
        }

        @Test
        void parses_a_multi_letter_element_symbol()
        {
            Molecule molecule = parse("[Fe]");

            assertThat(atomsOf(molecule).getFirst().element()).isEqualTo("Fe");
        }

        @Test
        void parses_a_bracketed_aromatic_extension_element()
        {
            Molecule molecule = parse("[se]");

            Atom atom = atomsOf(molecule).getFirst();
            assertThat(atom.element()).isEqualTo("Se");
            assertThat(atom.aromatic()).isTrue();
        }

        @Test
        void parses_a_bracketed_wildcard_atom()
        {
            Molecule molecule = parse("[*]");

            assertThat(atomsOf(molecule).getFirst().element()).isEqualTo("*");
        }

        @Test
        void bonds_a_bracket_atom_to_the_preceding_atom()
        {
            Molecule molecule = parse("C[O-]");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(molecule.bondCount()).isEqualTo(1);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(1))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void throws_when_the_bracket_is_not_closed()
        {
            assertThatThrownBy(() -> parse("[C"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected ']' to close bracket atom");
        }

        @Test
        void throws_when_no_element_symbol_is_present()
        {
            assertThatThrownBy(() -> parse("[+]"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected element symbol in bracket atom");
        }

        @Test
        void throws_when_an_atom_class_colon_is_not_followed_by_a_number()
        {
            assertThatThrownBy(() -> parse("[CH4:]"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected a number after ':' in atom class");
        }

        @Test
        void throws_when_bracket_contents_are_not_followed_by_a_closing_bracket()
        {
            assertThatThrownBy(() -> parse("[CH4"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected ']' to close bracket atom");
        }
    }

    @Nested
    class Chirality_
    {
        @Test
        void stores_chirality_unchanged_for_a_root_stereocentre_with_no_implicit_hydrogen()
        {
            // the root atom has no preceding atom, so the written order already matches
            // the stored order and no flip should occur
            Molecule molecule = parse("[C@](N)(Br)(Cl)F");

            Atom c = atomsOf(molecule).getFirst();
            assertThat(molecule.chiralityOf(c)).contains(Chirality.ANTICLOCKWISE);
        }

        @Test
        void flips_chirality_when_an_implicit_hydrogen_is_displaced_by_a_preceding_atom()
        {
            // a non-root stereocentre with an implicit hydrogen: the hydrogen moves from
            // first (as written) to its fixed storage slot after the preceding atom's
            // bond is accounted for, an odd reordering
            Molecule molecule = parse("N[C@H](Br)Cl");

            List<Atom> atoms = atomsOf(molecule);
            Atom c = atoms.get(1);
            assertThat(molecule.chiralityOf(c)).contains(Chirality.CLOCKWISE);
        }

        @Test
        void parses_the_clockwise_chirality_marker()
        {
            Molecule molecule = parse("[C@@](N)(Br)(Cl)F");

            Atom c = atomsOf(molecule).getFirst();
            assertThat(molecule.chiralityOf(c)).contains(Chirality.CLOCKWISE);
        }

        @Test
        void does_not_record_chirality_when_no_marker_is_present()
        {
            Molecule molecule = parse("[CH4]");

            Atom c = atomsOf(molecule).getFirst();
            assertThat(molecule.chiralityOf(c)).isEmpty();
        }
    }

    @Nested
    class DoubleBondStereoParsing
    {
        @Test
        void parses_opposite_side_substituents_as_a_trans_configuration()
        {
            Molecule molecule = parse("F/C=C/F");

            List<Atom> atoms = atomsOf(molecule);
            Bond doubleBond = molecule.bondBetween(atoms.get(1), atoms.get(2)).orElseThrow();
            assertThat(molecule.doubleBondStereoOf(doubleBond)).contains(DoubleBondStereo.OPPOSITE_SIDE);
        }

        @Test
        void parses_same_side_substituents_as_a_cis_configuration()
        {
            Molecule molecule = parse("F/C=C\\F");

            List<Atom> atoms = atomsOf(molecule);
            Bond doubleBond = molecule.bondBetween(atoms.get(1), atoms.get(2)).orElseThrow();
            assertThat(molecule.doubleBondStereoOf(doubleBond)).contains(DoubleBondStereo.SAME_SIDE);
        }

        @Test
        void parses_an_alternating_chain_as_a_consistent_configuration_on_every_double_bond()
        {
            // F/C=C\C=C/F
            Molecule molecule = parse("F/C=C\\C=C/F");

            List<Atom> atoms = atomsOf(molecule);
            Bond firstDoubleBond = molecule.bondBetween(atoms.get(1), atoms.get(2)).orElseThrow();
            Bond secondDoubleBond = molecule.bondBetween(atoms.get(3), atoms.get(4)).orElseThrow();
            assertThat(molecule.doubleBondStereoOf(firstDoubleBond)).contains(DoubleBondStereo.SAME_SIDE);
            assertThat(molecule.doubleBondStereoOf(secondDoubleBond)).contains(DoubleBondStereo.SAME_SIDE);
        }

        @Test
        void parses_directional_bonds_through_a_ring_closure()
        {
            // https://github.com/timvdm/OpenSMILES/blob/master/opensmiles.asciidoc
            Molecule molecule = parse("C/1=C\\C=C/C=C\\1");

            List<Atom> atoms = atomsOf(molecule);
            Atom a1 = atoms.get(0);
            Atom a2 = atoms.get(1);
            Atom a3 = atoms.get(2);
            Atom a4 = atoms.get(3);
            Atom a5 = atoms.get(4);
            Atom a6 = atoms.get(5);

            assertThat(molecule.doubleBondStereoOf(molecule.bondBetween(a1, a2).orElseThrow())).contains(DoubleBondStereo.OPPOSITE_SIDE);
            assertThat(molecule.doubleBondStereoOf(molecule.bondBetween(a3, a4).orElseThrow())).contains(DoubleBondStereo.SAME_SIDE);
            assertThat(molecule.doubleBondStereoOf(molecule.bondBetween(a5, a6).orElseThrow())).contains(DoubleBondStereo.SAME_SIDE);
        }

        @Test
        void parses_a_directional_ring_closure_bond_as_the_only_reference_on_one_side()
        {
            Molecule molecule = parse("C/1=C/CC\\1");

            List<Atom> atoms = atomsOf(molecule);
            Bond doubleBond = molecule.bondBetween(atoms.get(0), atoms.get(1)).orElseThrow();
            assertThat(molecule.doubleBondStereoOf(doubleBond)).contains(DoubleBondStereo.SAME_SIDE);
        }

        @Test
        void parses_a_redundant_second_direction_that_agrees_with_the_first()
        {
            // the direction on Cl repeats what the one on F already said
            Molecule molecule = parse("F/C(/Cl)=C/F");

            List<Atom> atoms = atomsOf(molecule);
            Bond doubleBond = molecule.bondBetween(atoms.get(1), atoms.get(3)).orElseThrow();
            assertThat(molecule.doubleBondStereoOf(doubleBond)).contains(DoubleBondStereo.OPPOSITE_SIDE);
        }

        @Test
        void throws_when_two_directions_written_at_one_atom_contradict_each_other()
        {
            // F and Cl would both be on the same side of their carbon
            assertThatThrownBy(() -> parse("F/C(\\Cl)=C/F"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("contradicts a direction already written at the same atom");
        }

        @Test
        void throws_when_a_ring_closure_bonds_direction_symbol_matches_its_opening_symbol()
        {
            assertThatThrownBy(() -> parse("C/1=CCC/1"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("must be the opposite");
        }
    }

    @Nested
    class RingClosures
    {
        @Test
        void parses_a_single_digit_ring_closure()
        {
            Molecule molecule = parse("C1CC1");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(molecule.atomCount()).isEqualTo(3);
            assertThat(molecule.bondCount()).isEqualTo(3);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(2))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void parses_cyclohexane()
        {
            Molecule molecule = parse("C1CCCCC1");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(molecule.atomCount()).isEqualTo(6);
            assertThat(molecule.bondCount()).isEqualTo(6);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(5))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void parses_a_percent_prefixed_ring_closure()
        {
            Molecule molecule = parse("C%12CC%12");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(molecule.atomCount()).isEqualTo(3);
            assertThat(molecule.bondCount()).isEqualTo(3);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(2))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void infers_an_aromatic_ring_closure_bond_between_two_aromatic_atoms()
        {
            Molecule molecule = parse("c1ccccc1");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(5))).isEqualTo(BondOrder.AROMATIC);
        }

        @Test
        void uses_the_bond_symbol_written_at_the_opening_end()
        {
            Molecule molecule = parse("C=1CCCCC1");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(5))).isEqualTo(BondOrder.DOUBLE);
        }

        @Test
        void uses_the_bond_symbol_written_at_the_closing_end()
        {
            Molecule molecule = parse("C1CCCCC=1");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(5))).isEqualTo(BondOrder.DOUBLE);
        }

        @Test
        void accepts_matching_bond_symbols_at_both_ends()
        {
            Molecule molecule = parse("C=1CCCCC=1");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(5))).isEqualTo(BondOrder.DOUBLE);
        }

        @Test
        void throws_for_mismatched_bond_symbols_at_each_end()
        {
            assertThatThrownBy(() -> parse("C=1CCCCC-1"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("does not match");
        }

        @Test
        void throws_for_a_ring_closure_bonding_an_atom_to_itself()
        {
            assertThatThrownBy(() -> parse("C11"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("bonds an atom to itself");
        }

        @Test
        void throws_for_a_ring_closure_with_no_preceding_atom()
        {
            assertThatThrownBy(() -> parse("1CC1"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Ring closure not preceded by an atom");
        }

        @Test
        void throws_for_an_unpaired_ring_closure_digit()
        {
            assertThatThrownBy(() -> parse("C1"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unpaired ring closure digit");
        }

        @Test
        void reuses_a_ring_closure_digit_within_the_same_connected_molecule()
        {
            Molecule molecule = parse("C1CC1C1CC1");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(molecule.atomCount()).isEqualTo(6);
            assertThat(molecule.bondCount()).isEqualTo(7);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(2))).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, atoms.get(2), atoms.get(3))).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, atoms.get(3), atoms.get(5))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void reuses_a_ring_closure_digit_in_a_disconnected_fragment()
        {
            Molecule molecule = parse("C1CC1.C1CC1");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(molecule.atomCount()).isEqualTo(6);
            assertThat(molecule.bondCount()).isEqualTo(6);
            assertThat(orderBetween(molecule, atoms.get(0), atoms.get(2))).isEqualTo(BondOrder.SINGLE);
            assertThat(orderBetween(molecule, atoms.get(3), atoms.get(5))).isEqualTo(BondOrder.SINGLE);
        }
    }

    @Nested
    class Errors
    {
        @Test
        void throws_for_an_empty_string()
        {
            assertThatThrownBy(() -> parse(""))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("SMILES string must not be empty");
        }

        @Test
        void throws_for_an_unexpected_character()
        {
            assertThatThrownBy(() -> parse("?"))
                .isInstanceOf(InvalidNotationException.class);
        }

        @Test
        void exception_carries_the_offending_source_position()
        {
            InvalidNotationException exception = (InvalidNotationException) catchParseException("CC)C");

            assertThat(exception.position()).isEqualTo(2);
            assertThat(exception.source()).isEqualTo("CC)C");
        }

        @Test
        void throws_for_a_bond_symbol_immediately_following_another_one()
        {
            assertThatThrownBy(() -> parse("C-(=C)C"))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("immediately follows another bond symbol");
        }

        @Test
        void throws_when_parse_is_called_a_second_time()
        {
            SmilesParser parser = new SmilesParser("CCO");
            parser.parse();

            assertThatThrownBy(parser::parse).isInstanceOf(IllegalStateException.class);
        }

        private Throwable catchParseException(String smiles)
        {
            try
            {
                parse(smiles);
                throw new AssertionError("Expected parsing to throw");
            }
            catch (InvalidNotationException e)
            {
                return e;
            }
        }
    }

    @Nested
    class RealWorldMolecules
    {
        @Test
        void parses_ethanol()
        {
            Molecule molecule = parse("CCO");

            assertThat(molecule.atomCount()).isEqualTo(3);
            assertThat(molecule.bondCount()).isEqualTo(2);
        }

        @Test
        void parses_acetic_acid()
        {
            Molecule molecule = parse("CC(=O)O");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(atoms).extracting(Atom::element).containsExactly("C", "C", "O", "O");
            assertThat(orderBetween(molecule, atoms.get(1), atoms.get(2))).isEqualTo(BondOrder.DOUBLE);
            assertThat(orderBetween(molecule, atoms.get(1), atoms.get(3))).isEqualTo(BondOrder.SINGLE);
        }

        @Test
        void parses_benzene_without_ring_closure_as_a_chain_of_aromatic_atoms()
        {
            Molecule molecule = parse("cccccc");

            assertThat(molecule.atomCount()).isEqualTo(6);
            assertThat(molecule.bondCount()).isEqualTo(5);
        }

        @Test
        void parses_triflic_acid()
        {
            Molecule molecule = parse("C(F)(F)(F)S(=O)(=O)O");

            List<Atom> atoms = atomsOf(molecule);
            assertThat(atoms).extracting(Atom::element)
                .containsExactly("C", "F", "F", "F", "S", "O", "O", "O");
            assertThat(molecule.bondCount()).isEqualTo(7);
        }
    }
}
