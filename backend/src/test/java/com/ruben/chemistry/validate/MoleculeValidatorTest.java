package com.ruben.chemistry.validate;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoleculeValidatorTest
{
    @Test
    void finds_nothing_in_a_molecule_that_is_written_properly()
    {
        assertThat(rulesOf("CCO")).isEmpty();
        assertThat(rulesOf("c1ccccc1")).isEmpty();
        assertThat(rulesOf("C1=CC=CC=C1")).isEmpty();
        assertThat(rulesOf("O[C@H](C)CC")).isEmpty();
        assertThat(rulesOf("[13CH4]")).isEmpty();
    }

    @Nested
    class Structure
    {
        @Test
        void reports_a_molecule_with_no_atoms()
        {
            List<Finding> findings = new MoleculeValidator().validate(new Molecule.Builder().build());

            assertThat(findings).singleElement()
                .satisfies(finding -> assertThat(finding.rule()).isEqualTo(Rule.STRUCTURE_EMPTY))
                .satisfies(finding -> assertThat(finding.atoms()).isEmpty());
        }

        @Test
        void remarks_on_a_molecule_in_several_pieces_only_when_asked()
        {
            // a salt or a hydrate is in pieces quite legitimately, so this is off unless asked for
            assertThat(rulesOf("CCO.O")).doesNotContain(Rule.STRUCTURE_DISCONNECTED);
            assertThat(reported("CCO.O", Rule.STRUCTURE_DISCONNECTED)).containsExactly(Rule.STRUCTURE_DISCONNECTED);
            assertThat(messagesWith("CCO.O", Rule.STRUCTURE_DISCONNECTED))
                .anyMatch(message -> message.contains("2 separate pieces"));
            assertThat(reported("CCO", Rule.STRUCTURE_DISCONNECTED)).isEmpty();
        }

        @Test
        void reports_a_hydrogen_written_as_an_atom_of_its_own()
        {
            assertThat(rulesOf("[H]")).contains(Rule.STRUCTURE_LONE_HYDROGEN);
            assertThat(rulesOf("[2H]")).contains(Rule.STRUCTURE_LONE_HYDROGEN);
            // dihydrogen's hydrogens are bonded to each other, and an ion is meant as written
            assertThat(rulesOf("[H][H]")).doesNotContain(Rule.STRUCTURE_LONE_HYDROGEN);
            assertThat(rulesOf("[H-]")).doesNotContain(Rule.STRUCTURE_LONE_HYDROGEN);
            assertThat(rulesOf("[H+]")).doesNotContain(Rule.STRUCTURE_LONE_HYDROGEN);
            assertThat(rulesOf("[H]O[H]")).doesNotContain(Rule.STRUCTURE_LONE_HYDROGEN);
        }
    }

    @Nested
    class Elements
    {
        @Test
        void reports_a_mass_number_below_the_proton_count()
        {
            assertThat(rulesOf("[1C]")).contains(Rule.ISOTOPE_BELOW_ATOMIC_NUMBER);
            assertThat(messagesOf("[1C]")).anyMatch(message -> message.contains("C has 6 protons"));
            assertThat(rulesOf("[12C]")).doesNotContain(Rule.ISOTOPE_BELOW_ATOMIC_NUMBER);
        }

        @Test
        void reports_a_wildcard_standing_in_a_structure()
        {
            assertThat(rulesOf("*C")).contains(Rule.ELEMENT_WILDCARD_IN_MOLECULE);
            assertThat(rulesOf("CCO")).doesNotContain(Rule.ELEMENT_WILDCARD_IN_MOLECULE);
        }

        @Test
        void reports_what_is_written_on_a_wildcard_that_has_no_element_to_make_it_true()
        {
            assertThat(rulesOf("[13*]C")).contains(Rule.ISOTOPE_ON_WILDCARD);
            assertThat(rulesOf("[*+]C")).contains(Rule.CHARGE_ON_WILDCARD);
            assertThat(rulesOf("[*H3]C")).contains(Rule.HYDROGENS_ON_WILDCARD);
            // a bare wildcard is only the one finding
            assertThat(rulesOf("*C"))
                .doesNotContain(Rule.ISOTOPE_ON_WILDCARD, Rule.CHARGE_ON_WILDCARD, Rule.HYDROGENS_ON_WILDCARD);
        }

        @Test
        void reports_a_hydrogen_labelled_heavier_than_tritium()
        {
            // a label of this kind reaches a molecule through the builder rather than through SMILES
            Molecule heavy = new Molecule.Builder()
                .addAtom(Atom.builder("C").hydrogenCount(4).hydrogenIsotope(4, 1).build()).build();
            Molecule tritiated = new Molecule.Builder()
                .addAtom(Atom.builder("C").hydrogenCount(4).hydrogenIsotope(3, 1).build()).build();

            assertThat(rulesOf(heavy)).contains(Rule.ISOTOPE_HYDROGEN_IMPLAUSIBLE);
            assertThat(rulesOf(tritiated)).doesNotContain(Rule.ISOTOPE_HYDROGEN_IMPLAUSIBLE);
        }
    }

    @Nested
    class Valence
    {
        @Test
        void reports_a_valence_beyond_what_any_charge_allows()
        {
            assertThat(rulesOf("[CH5]")).contains(Rule.VALENCE_EXCEEDED);
            assertThat(rulesOf("[NH6]")).contains(Rule.VALENCE_EXCEEDED);
            assertThat(rulesOf("C(C)(C)(C)(C)C")).contains(Rule.VALENCE_EXCEEDED);
            assertThat(messagesOf("[CH5]")).anyMatch(message -> message.contains("beyond the 4 it allows"));
            // pentavalent nitrogen is no charge's doing: a nitro group is properly written charge-separated
            assertThat(rulesOf("O=N(=O)C")).contains(Rule.VALENCE_EXCEEDED);
            assertThat(rulesOf("[O-][N+](=O)C")).doesNotContain(Rule.VALENCE_EXCEEDED);
        }

        @Test
        void reports_a_valence_that_only_a_charge_accounts_for()
        {
            assertThat(rulesOf("[NH4]")).contains(Rule.VALENCE_CHARGE_DISALLOWS);
            assertThat(messagesOf("[NH4]")).anyMatch(message -> message.contains("only with a charge of +1"));
            assertThat(rulesOf("[OH3]")).contains(Rule.VALENCE_CHARGE_DISALLOWS);
            // tetravalent boron is borohydride's, which is an anion
            assertThat(messagesOf("[BH4]")).anyMatch(message -> message.contains("only with a charge of -1"));

            assertThat(rulesOf("[NH4+]")).doesNotContain(Rule.VALENCE_CHARGE_DISALLOWS);
            assertThat(rulesOf("[OH3+]")).doesNotContain(Rule.VALENCE_CHARGE_DISALLOWS);
            assertThat(rulesOf("[BH4-]")).doesNotContain(Rule.VALENCE_CHARGE_DISALLOWS);
        }

        @Test
        void leaves_an_under_coordinated_atom_alone()
        {
            // a radical, a carbene and a bare ion are all simply holding fewer bonds than they could
            assertThat(rulesOf("[CH3]")).doesNotContain(Rule.VALENCE_EXCEEDED, Rule.VALENCE_CHARGE_DISALLOWS);
            assertThat(rulesOf("[CH2]")).doesNotContain(Rule.VALENCE_EXCEEDED, Rule.VALENCE_CHARGE_DISALLOWS);
            assertThat(rulesOf("[Cl-]")).doesNotContain(Rule.VALENCE_EXCEEDED, Rule.VALENCE_CHARGE_DISALLOWS);
        }

        @Test
        void keeps_the_hypervalence_the_third_period_really_reaches()
        {
            assertThat(rulesOf("OS(=O)(=O)O")).isEmpty();
            assertThat(rulesOf("OP(=O)(O)O")).isEmpty();
            assertThat(rulesOf("OCl(=O)(=O)=O")).doesNotContain(Rule.VALENCE_EXCEEDED);
        }

        @Test
        void says_nothing_of_an_element_it_has_no_valences_for()
        {
            assertThat(rulesOf("[Fe+3]")).doesNotContain(Rule.VALENCE_UNCHECKED);
            assertThat(reported("[Fe+3]", Rule.VALENCE_UNCHECKED)).containsExactly(Rule.VALENCE_UNCHECKED);
        }
    }

    @Nested
    class Charge
    {
        @Test
        void reports_a_charge_past_what_an_outer_shell_can_carry()
        {
            assertThat(rulesOf("[C+5]")).contains(Rule.CHARGE_IMPLAUSIBLE);
            assertThat(messagesOf("[C+5]")).anyMatch(message -> message.contains("outer shell can carry"));
            assertThat(rulesOf("[C+4]")).doesNotContain(Rule.CHARGE_IMPLAUSIBLE);
            assertThat(rulesOf("[O-2]")).doesNotContain(Rule.CHARGE_IMPLAUSIBLE);
            assertThat(rulesOf("[O-3]")).contains(Rule.CHARGE_IMPLAUSIBLE);
        }

        @Test
        void reports_a_charge_larger_than_the_electron_count()
        {
            assertThat(rulesOf("[C+7]")).contains(Rule.CHARGE_BEYOND_ELECTRON_COUNT);
            assertThat(rulesOf("[C+4]")).doesNotContain(Rule.CHARGE_BEYOND_ELECTRON_COUNT);
        }

        @Test
        void remarks_on_an_odd_electron_count()
        {
            assertThat(rulesOf("[CH3]")).contains(Rule.ELECTRONS_ODD);
            assertThat(messagesOf("[CH3]")).anyMatch(message -> message.contains("9 electrons"));
            assertThat(rulesOf("[Fe+3].[Cl-].[Cl-].[Cl-]")).contains(Rule.ELECTRONS_ODD);
            assertThat(rulesOf("C")).doesNotContain(Rule.ELECTRONS_ODD);
            // a wildcard leaves the molecule's electrons uncountable rather than odd
            assertThat(rulesOf("*C")).doesNotContain(Rule.ELECTRONS_ODD);
        }

        @Test
        void remarks_on_a_net_charge_only_when_asked()
        {
            // a free ion carries one perfectly legitimately, so this is off unless asked for
            assertThat(rulesOf("[NH4+]")).doesNotContain(Rule.CHARGE_UNCOMPENSATED);
            assertThat(reported("[NH4+]", Rule.CHARGE_UNCOMPENSATED)).containsExactly(Rule.CHARGE_UNCOMPENSATED);
            assertThat(messagesWith("[NH4+]", Rule.CHARGE_UNCOMPENSATED))
                .anyMatch(message -> message.contains("net charge of +1"));
            // the charges of a salt cancel, whatever its pieces carry
            assertThat(reported("[NH4+].[Cl-]", Rule.CHARGE_UNCOMPENSATED)).isEmpty();
            assertThat(reported("CCO", Rule.CHARGE_UNCOMPENSATED)).isEmpty();
        }
    }

    @Nested
    class Aromaticity
    {
        @Test
        void reports_an_aromatic_bond_on_no_ring()
        {
            List<Atom> carbons = List.of(Atom.aromatic("C"), Atom.aromatic("C"));
            Molecule.Builder builder = new Molecule.Builder().addAtoms(carbons.toArray(Atom[]::new));
            builder.addBond(carbons.getFirst(), carbons.get(1), BondOrder.AROMATIC);

            assertThat(rulesOf(builder.build()))
                .contains(Rule.AROMATIC_BOND_NOT_IN_RING, Rule.AROMATIC_ATOM_NOT_IN_RING);
        }

        @Test
        void reports_an_aromatic_atom_on_no_ring()
        {
            List<Atom> atoms = List.of(Atom.aromatic("C"), Atom.organic("C"));
            Molecule.Builder builder = new Molecule.Builder().addAtoms(atoms.toArray(Atom[]::new));
            builder.addBond(atoms.getFirst(), atoms.get(1), BondOrder.SINGLE);

            assertThat(rulesOf(builder.build()))
                .contains(Rule.AROMATIC_ATOM_NOT_IN_RING)
                .doesNotContain(Rule.AROMATIC_BOND_NOT_IN_RING);
        }

        @Test
        void reports_a_system_written_aromatic_that_has_no_assignment()
        {
            assertThat(rulesOf("c1cccccc1")).contains(Rule.AROMATIC_SYSTEM_NOT_KEKULIZABLE);
            assertThat(messagesOf("c1cccccc1")).anyMatch(message -> message.contains("odd number"));
            // phenalenyl's thirteen carbons cannot pair, which no per-ring count would show: each of
            // its three rings comes to six
            assertThat(rulesOf("c1cc2cccc3cccc(c1)c23")).contains(Rule.AROMATIC_SYSTEM_NOT_KEKULIZABLE);
            // a pyrrole nitrogen without its hydrogen leaves five carbons needing four partners
            assertThat(rulesOf("c1ccnc1")).contains(Rule.AROMATIC_SYSTEM_NOT_KEKULIZABLE);
            assertThat(rulesOf("c1cc[nH]c1")).isEmpty();
        }

        @Test
        void leaves_an_aromatic_system_alone_when_its_bonds_can_be_assigned()
        {
            assertThat(rulesOf("c1ccccc1")).isEmpty();
            assertThat(rulesOf("c1ccc2ccccc2c1")).isEmpty();
            // azulene's rings hold five and seven electrons, and its system the ten that make it aromatic
            assertThat(rulesOf("c1cc2cccccc2c1")).isEmpty();
            assertThat(rulesOf("c1[cH-]c2cccc3cccc(c1)c23")).isEmpty();
            // pyrene holds 4n electrons and is aromatic all the same, so the count is not what decides it
            assertThat(rulesOf("c1cc2ccc3cccc4ccc(c1)c2c34")).isEmpty();
            // indane's saturated ring shares two of benzene's aromatic bonds without claiming them
            assertThat(rulesOf("C1Cc2ccccc2C1")).isEmpty();
            assertThat(rulesOf("C1CCc2ccccc2C1")).isEmpty();
        }

        @Test
        void reports_a_system_whose_rim_comes_to_four_n()
        {
            assertThat(rulesOf("c1ccc1")).contains(Rule.AROMATIC_SYSTEM_ANTIAROMATIC);
            assertThat(messagesOf("c1ccc1"))
                .anyMatch(message -> message.contains("4 π electrons around its rim make it antiaromatic"));
            assertThat(rulesOf("c1ccc1")).doesNotContain(Rule.AROMATIC_SYSTEM_NOT_KEKULIZABLE);
        }

        @Test
        void reports_a_ring_written_part_aromatic_and_part_alternating()
        {
            List<Atom> carbons = List.of(
                Atom.organic("C"), Atom.organic("C"), Atom.organic("C"),
                Atom.organic("C"), Atom.organic("C"), Atom.organic("C"));
            Molecule.Builder builder = new Molecule.Builder().addAtoms(carbons.toArray(Atom[]::new));
            builder.addBond(carbons.get(0), carbons.get(1), BondOrder.AROMATIC);
            builder.addBond(carbons.get(1), carbons.get(2), BondOrder.SINGLE);
            builder.addBond(carbons.get(2), carbons.get(3), BondOrder.DOUBLE);
            builder.addBond(carbons.get(3), carbons.get(4), BondOrder.SINGLE);
            builder.addBond(carbons.get(4), carbons.get(5), BondOrder.DOUBLE);
            builder.addBond(carbons.get(5), carbons.get(0), BondOrder.SINGLE);

            assertThat(rulesOf(builder.build())).contains(Rule.AROMATIC_RING_MIXED_NOTATION);
            // indane's saturated ring has aromatic bonds in it, but they are benzene's, not its own
            assertThat(rulesOf("C1Cc2ccccc2C1")).doesNotContain(Rule.AROMATIC_RING_MIXED_NOTATION);
            assertThat(rulesOf("c1ccccc1")).doesNotContain(Rule.AROMATIC_RING_MIXED_NOTATION);
        }
    }

    @Nested
    class Rings
    {
        @Test
        void reports_two_double_bonds_on_a_ring_atom_too_closely_hemmed_in()
        {
            assertThat(rulesOf("C1=C=CC=C1")).contains(Rule.RING_CUMULATED_DOUBLE_BONDS);
            assertThat(messagesOf("C1=C=CC=C1")).anyMatch(message -> message.contains("cannot come back round"));
            // a ring of nine has the room, 1,2-cyclononadiene being the first cyclic allene
            assertThat(rulesOf("C1=C=CCCCCCC1")).doesNotContain(Rule.RING_CUMULATED_DOUBLE_BONDS);
            // two double bonds on one atom are only cumulated when both lie in the ring
            assertThat(rulesOf("C1=CC=CC1=C")).doesNotContain(Rule.RING_CUMULATED_DOUBLE_BONDS);
        }

        @Test
        void refuses_a_triple_bond_a_small_ring_cannot_hold_and_only_remarks_on_seven()
        {
            assertThat(rulesOf("C1CC#CC1")).contains(Rule.RING_TRIPLE_BOND_IN_SMALL_RING);
            assertThat(rulesOf("C1CC#CCC1")).contains(Rule.RING_TRIPLE_BOND_IN_SMALL_RING);
            // cycloheptyne and benzyne are real transients, so seven is remarked on rather than refused
            assertThat(rulesOf("C1CC#CCCC1"))
                .contains(Rule.RING_TRIPLE_BOND_STRAINED)
                .doesNotContain(Rule.RING_TRIPLE_BOND_IN_SMALL_RING);
            // cyclooctyne is the first that can be bottled
            assertThat(rulesOf("C1CC#CCCCC1"))
                .doesNotContain(Rule.RING_TRIPLE_BOND_IN_SMALL_RING, Rule.RING_TRIPLE_BOND_STRAINED);
            assertThat(rulesOf("CC#CC")).doesNotContain(Rule.RING_TRIPLE_BOND_IN_SMALL_RING);
        }

        @Test
        void remarks_on_a_three_or_four_membered_ring()
        {
            // a ring's strain is about its size, and has nothing to do with aromaticity
            assertThat(Rule.RING_STRAINED.category()).isEqualTo(Category.RING);
            assertThat(Rule.RING_CUMULATED_DOUBLE_BONDS.category()).isEqualTo(Category.RING);
            assertThat(Rule.RING_TRIPLE_BOND_IN_SMALL_RING.category()).isEqualTo(Category.RING);
            assertThat(Rule.RING_TRIPLE_BOND_STRAINED.category()).isEqualTo(Category.RING);

            assertThat(rulesOf("C1CC1")).contains(Rule.RING_STRAINED);
            assertThat(rulesOf("C1CCC1")).contains(Rule.RING_STRAINED);
            assertThat(rulesOf("C1CO1")).contains(Rule.RING_STRAINED);
            // five and up are not strained, benzene and cyclohexane least of all
            assertThat(rulesOf("C1CCCC1")).doesNotContain(Rule.RING_STRAINED);
            assertThat(rulesOf("C1CCCCC1")).doesNotContain(Rule.RING_STRAINED);
            assertThat(rulesOf("c1ccccc1")).isEmpty();
            assertThat(rulesOf("C1=CC=CC=C1")).isEmpty();
        }
    }

    @Nested
    class Stereochemistry
    {
        @Test
        void reports_a_chirality_marker_with_too_few_substituents()
        {
            assertThat(rulesOf("C[C@H]")).contains(Rule.STEREO_CENTRE_TOO_FEW_NEIGHBOURS);
            assertThat(rulesOf("O[C@H](C)CC")).doesNotContain(Rule.STEREO_CENTRE_TOO_FEW_NEIGHBOURS);
        }
    }

    /** How the findings are reported, rather than what any one of them says. */
    @Nested
    class Reporting
    {
        @Test
        void puts_the_worst_findings_first()
        {
            List<Finding> findings = new MoleculeValidator().validate(Molecule.fromSmiles("[CH5]"));

            assertThat(findings).extracting(Finding::rule)
                .containsExactly(Rule.VALENCE_EXCEEDED, Rule.ELECTRONS_ODD);
            assertThat(findings).extracting(Finding::severity)
                .containsExactly(Severity.ERROR, Severity.INFO);
        }

        @Test
        void reports_only_the_rules_it_was_asked_for()
        {
            Molecule molecule = Molecule.fromSmiles("[CH5]");

            assertThat(new MoleculeValidator(ValidationOptions.only(Rule.ELECTRONS_ODD)).validate(molecule))
                .extracting(Finding::rule).containsExactly(Rule.ELECTRONS_ODD);
            assertThat(new MoleculeValidator(ValidationOptions.defaults().without(Rule.ELECTRONS_ODD)).validate(molecule))
                .extracting(Finding::rule).containsExactly(Rule.VALENCE_EXCEEDED);
        }

        @Test
        void reports_one_rule_of_a_check_responsible_for_two()
        {
            Molecule molecule = Molecule.fromSmiles("c1ccc1");

            assertThat(new MoleculeValidator(ValidationOptions.only(Rule.AROMATIC_SYSTEM_NOT_KEKULIZABLE)).validate(molecule))
                .isEmpty();
            assertThat(new MoleculeValidator(ValidationOptions.only(Rule.AROMATIC_SYSTEM_ANTIAROMATIC)).validate(molecule))
                .extracting(Finding::rule).containsExactly(Rule.AROMATIC_SYSTEM_ANTIAROMATIC);
        }

        @Test
        void separates_what_stops_a_caller_from_what_only_remarks()
        {
            MoleculeValidator validator = new MoleculeValidator();

            assertThat(validator.hasErrors(Molecule.fromSmiles("[CH5]"))).isTrue();
            assertThat(validator.hasErrors(Molecule.fromSmiles("[CH3]"))).isFalse();
            assertThat(validator.hasErrors(Molecule.fromSmiles("CCO"))).isFalse();
        }

        @Test
        void holds_the_atoms_a_finding_is_about_by_reference()
        {
            Molecule molecule = Molecule.fromSmiles("CC[CH5]");
            List<Finding> findings = new MoleculeValidator(ValidationOptions.only(Rule.VALENCE_EXCEEDED))
                .validate(molecule);

            assertThat(findings).singleElement()
                .satisfies(finding -> assertThat(finding.atoms()).containsExactly(molecule.atoms().get(2)));
        }
    }

    private static List<Rule> rulesOf(String smiles)
    {
        return rulesOf(Molecule.fromSmiles(smiles));
    }

    private static List<Rule> rulesOf(Molecule molecule)
    {
        return new MoleculeValidator().validate(molecule).stream().map(Finding::rule).toList();
    }

    private static List<String> messagesOf(String smiles)
    {
        return new MoleculeValidator().validate(Molecule.fromSmiles(smiles)).stream().map(Finding::message).toList();
    }

    /**
     * @param smiles a molecule as SMILES
     * @param rule the one rule to report
     * @return the rules found, which is that rule or nothing
     */
    private static List<Rule> reported(String smiles, Rule rule)
    {
        return new MoleculeValidator(ValidationOptions.only(rule)).validate(Molecule.fromSmiles(smiles))
            .stream().map(Finding::rule).toList();
    }

    /**
     * @param smiles a molecule as SMILES
     * @param rule the one rule to report
     * @return what it said about the molecule
     */
    private static List<String> messagesWith(String smiles, Rule rule)
    {
        return new MoleculeValidator(ValidationOptions.only(rule)).validate(Molecule.fromSmiles(smiles))
            .stream().map(Finding::message).toList();
    }
}
