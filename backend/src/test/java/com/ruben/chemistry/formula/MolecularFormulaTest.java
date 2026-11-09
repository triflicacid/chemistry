package com.ruben.chemistry.formula;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MolecularFormulaTest
{
    @Test
    void writes_nothing_for_an_empty_molecule()
    {
        MolecularFormula formula = MolecularFormula.of(new Molecule.Builder().build());

        assertThat(formula).hasToString("");
        assertThat(formula.toUnicode()).isEmpty();
    }

    @Test
    void counts_a_lone_atoms_implicit_hydrogens()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.organic("C"))
            .build();

        MolecularFormula formula = MolecularFormula.of(molecule);

        assertThat(formula).hasToString("CH4");
        assertThat(formula.toUnicode()).isEqualTo("CH₄");
    }

    @Test
    void omits_the_count_of_a_single_atom()
    {
        // C=O
        Atom c = Atom.organic("C");
        Atom o = Atom.organic("O");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c, o)
            .addBond(c, o, BondOrder.DOUBLE)
            .build();

        MolecularFormula formula = MolecularFormula.of(molecule);

        assertThat(formula).hasToString("CH2O");
        assertThat(formula.toUnicode()).isEqualTo("CH₂O");
    }

    @Test
    void writes_ethane()
    {
        // CC
        Atom c1 = Atom.organic("C");
        Atom c2 = Atom.organic("C");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(c1, c2)
            .addBond(c1, c2, BondOrder.SINGLE)
            .build();

        MolecularFormula formula = MolecularFormula.of(molecule);

        assertThat(formula).hasToString("C2H6");
        assertThat(formula.toUnicode()).isEqualTo("C₂H₆");
    }

    @Test
    void writes_acetic_acid()
    {
        // CC(=O)O
        Atom methyl = Atom.organic("C");
        Atom carbonyl = Atom.organic("C");
        Atom carbonylOxygen = Atom.organic("O");
        Atom hydroxyl = Atom.organic("O");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(methyl, carbonyl, carbonylOxygen, hydroxyl)
            .addBond(methyl, carbonyl, BondOrder.SINGLE)
            .addBond(carbonyl, carbonylOxygen, BondOrder.DOUBLE)
            .addBond(carbonyl, hydroxyl, BondOrder.SINGLE)
            .build();

        MolecularFormula formula = MolecularFormula.of(molecule);

        assertThat(formula).hasToString("C2H4O2");
        assertThat(formula.toUnicode()).isEqualTo("C₂H₄O₂");
    }

    @Test
    void counts_explicit_hydrogen_atoms_of_the_graph()
    {
        // [H]O[H]
        Atom o = Atom.organic("O");
        Atom h1 = Atom.organic("H");
        Atom h2 = Atom.organic("H");
        Molecule molecule = new Molecule.Builder()
            .addAtoms(o, h1, h2)
            .addBond(o, h1, BondOrder.SINGLE)
            .addBond(o, h2, BondOrder.SINGLE)
            .build();

        MolecularFormula formula = MolecularFormula.of(molecule);

        // a count of one is unwritten in either form
        assertThat(formula).hasToString("H2O");
        assertThat(formula.toUnicode()).isEqualTo("H₂O");
    }

    @Test
    void counts_a_declared_hydrogen_count()
    {
        Molecule molecule = new Molecule.Builder()
            .addAtom(Atom.builder("C").hydrogenCount(3).build())
            .build();

        MolecularFormula formula = MolecularFormula.of(molecule);

        assertThat(formula).hasToString("CH3");
        assertThat(formula.toUnicode()).isEqualTo("CH₃");
    }

    @Test
    void sums_the_formulae_of_disconnected_fragments()
    {
        // O.O
        Molecule molecule = new Molecule.Builder()
            .addAtoms(Atom.organic("O"), Atom.organic("O"))
            .build();

        MolecularFormula formula = MolecularFormula.of(molecule);

        assertThat(formula).hasToString("H4O2");
        assertThat(formula.toUnicode()).isEqualTo("H₄O₂");
    }

    @Test
    void counts_an_aromatic_rings_hydrogens()
    {
        // c1ccccc1
        Molecule.Builder builder = new Molecule.Builder();
        Atom[] carbons = new Atom[6];
        for (int i = 0; i < carbons.length; i++)
        {
            carbons[i] = Atom.aromatic("C");
            builder.addAtom(carbons[i]);
        }
        for (int i = 0; i < carbons.length; i++)
        {
            builder.addBond(carbons[i], carbons[(i + 1) % carbons.length], BondOrder.AROMATIC);
        }

        MolecularFormula formula = MolecularFormula.of(builder.build());

        assertThat(formula).hasToString("C6H6");
        assertThat(formula.toUnicode()).isEqualTo("C₆H₆");
    }

    @Nested
    class ElementOrdering
    {
        @Test
        void writes_carbon_then_hydrogen_then_the_rest_alphabetically()
        {
            // C(N)(O)S
            Atom c = Atom.organic("C");
            Atom n = Atom.organic("N");
            Atom o = Atom.organic("O");
            Atom s = Atom.organic("S");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, n, o, s)
                .addBond(c, n, BondOrder.SINGLE)
                .addBond(c, o, BondOrder.SINGLE)
                .addBond(c, s, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("CH5NOS");
            assertThat(formula.toUnicode()).isEqualTo("CH₅NOS");
        }

        @Test
        void writes_a_carbon_free_molecule_in_element_sequence_order()
        {
            // OS(=O)(=O)O
            Atom s = Atom.organic("S");
            Atom hydroxyl1 = Atom.organic("O");
            Atom hydroxyl2 = Atom.organic("O");
            Atom oxo1 = Atom.organic("O");
            Atom oxo2 = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(s, hydroxyl1, hydroxyl2, oxo1, oxo2)
                .addBond(s, hydroxyl1, BondOrder.SINGLE)
                .addBond(s, hydroxyl2, BondOrder.SINGLE)
                .addBond(s, oxo1, BondOrder.DOUBLE)
                .addBond(s, oxo2, BondOrder.DOUBLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("H2SO4");
            assertThat(formula.toUnicode()).isEqualTo("H₂SO₄");
        }

        @Test
        void writes_ammonia_with_its_nitrogen_first()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.organic("N"))
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("NH3");
            assertThat(formula.toUnicode()).isEqualTo("NH₃");
        }

        @Test
        void writes_wildcard_atoms_last_in_organic_molecule()
        {
            // C*
            Atom c = Atom.organic("C");
            Atom wildcard = Atom.organic("*");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c, wildcard)
                .addBond(c, wildcard, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("CH3*");
            assertThat(formula.toUnicode()).isEqualTo("CH₃*");
        }

        @Test
        void writes_wildcard_atoms_last_in_a_carbon_free_molecule()
        {
            // O*
            Atom o = Atom.organic("O");
            Atom wildcard = Atom.organic("*");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(o, wildcard)
                .addBond(o, wildcard, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("HO*");
            assertThat(formula.toUnicode()).isEqualTo("HO*");
        }
    }

    @Nested
    class Charges
    {
        @Test
        void appends_a_single_charge_without_its_magnitude()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.builder("N").charge(1).hydrogenCount(4).build())
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("NH4+");
            assertThat(formula.toUnicode()).isEqualTo("NH₄⁺");
        }

        @Test
        void appends_a_multiple_charges_magnitude_before_its_sign()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.builder("O").charge(-2).hydrogenCount(0).build())
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("O2-");
            assertThat(formula.toUnicode()).isEqualTo("O²⁻");
        }

        @Test
        void sums_opposing_charges_into_a_neutral_whole()
        {
            // [NH4+].[Cl-]
            Molecule molecule = new Molecule.Builder()
                .addAtoms(
                    Atom.builder("N").charge(1).hydrogenCount(4).build(),
                    Atom.builder("Cl").charge(-1).hydrogenCount(0).build())
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("NH4Cl");
            assertThat(formula.toUnicode()).isEqualTo("NH₄Cl");
        }
    }

    @Nested
    class StrictHillOrdering
    {
        @Test
        void alphabetises_a_carbon_free_molecule_hydrogen_included()
        {
            // OS(=O)(=O)O
            Atom s = Atom.organic("S");
            Atom hydroxyl1 = Atom.organic("O");
            Atom hydroxyl2 = Atom.organic("O");
            Atom oxo1 = Atom.organic("O");
            Atom oxo2 = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(s, hydroxyl1, hydroxyl2, oxo1, oxo2)
                .addBond(s, hydroxyl1, BondOrder.SINGLE)
                .addBond(s, hydroxyl2, BondOrder.SINGLE)
                .addBond(s, oxo1, BondOrder.DOUBLE)
                .addBond(s, oxo2, BondOrder.DOUBLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule, MolecularFormula.ElementOrder.HILL);

            assertThat(formula).hasToString("H2O4S");
            assertThat(formula.toUnicode()).isEqualTo("H₂O₄S");
        }

        @Test
        void writes_ammonia_with_its_hydrogens_first()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.organic("N"))
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule, MolecularFormula.ElementOrder.HILL);

            assertThat(formula).hasToString("H3N");
            assertThat(formula.toUnicode()).isEqualTo("H₃N");
        }

        @Test
        void drops_hydrogen_below_an_earlier_letter()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.organic("Cl"))
                .build();

            MolecularFormula hill = MolecularFormula.of(molecule, MolecularFormula.ElementOrder.HILL);
            MolecularFormula conventional = MolecularFormula.of(molecule, MolecularFormula.ElementOrder.CONVENTIONAL);

            assertThat(hill).hasToString("ClH");
            assertThat(hill.toUnicode()).isEqualTo("ClH");
            assertThat(conventional).hasToString("HCl");
            assertThat(conventional.toUnicode()).isEqualTo("HCl");
        }

        @Test
        void agrees_with_conventional_order_when_carbon_is_present()
        {
            // CC(=O)O
            Atom methyl = Atom.organic("C");
            Atom carbonyl = Atom.organic("C");
            Atom carbonylOxygen = Atom.organic("O");
            Atom hydroxyl = Atom.organic("O");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(methyl, carbonyl, carbonylOxygen, hydroxyl)
                .addBond(methyl, carbonyl, BondOrder.SINGLE)
                .addBond(carbonyl, carbonylOxygen, BondOrder.DOUBLE)
                .addBond(carbonyl, hydroxyl, BondOrder.SINGLE)
                .build();

            MolecularFormula hill = MolecularFormula.of(molecule, MolecularFormula.ElementOrder.HILL);
            MolecularFormula conventional = MolecularFormula.of(molecule, MolecularFormula.ElementOrder.CONVENTIONAL);

            assertThat(hill).hasToString("C2H4O2");
            assertThat(hill.toUnicode()).isEqualTo("C₂H₄O₂");
            assertThat(conventional).hasToString("C2H4O2");
            assertThat(conventional.toUnicode()).isEqualTo("C₂H₄O₂");
        }

        @Test
        void writes_wildcard_atoms_last()
        {
            // O*
            Atom o = Atom.organic("O");
            Atom wildcard = Atom.organic("*");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(o, wildcard)
                .addBond(o, wildcard, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule, MolecularFormula.ElementOrder.HILL);

            assertThat(formula).hasToString("HO*");
            assertThat(formula.toUnicode()).isEqualTo("HO*");
        }
    }

    @Nested
    class Empirical
    {
        @Test
        void reduces_a_formula_to_its_simplest_ratio()
        {
            // CC
            Atom c1 = Atom.organic("C");
            Atom c2 = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(c1, c2)
                .addBond(c1, c2, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule).empirical();

            assertThat(formula).hasToString("CH3");
            assertThat(formula.toUnicode()).isEqualTo("CH₃");
        }

        @Test
        void reduces_a_ratio_down_to_one_atom_of_each_element()
        {
            // c1ccccc1
            Molecule.Builder builder = new Molecule.Builder();
            Atom[] carbons = new Atom[6];
            for (int i = 0; i < carbons.length; i++)
            {
                carbons[i] = Atom.aromatic("C");
                builder.addAtom(carbons[i]);
            }
            for (int i = 0; i < carbons.length; i++)
            {
                builder.addBond(carbons[i], carbons[(i + 1) % carbons.length], BondOrder.AROMATIC);
            }

            MolecularFormula formula = MolecularFormula.of(builder.build()).empirical();

            assertThat(formula).hasToString("CH");
            assertThat(formula.toUnicode()).isEqualTo("CH");
        }

        @Test
        void keeps_a_formula_whose_counts_share_no_divisor()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.organic("O"))
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula.empirical()).isSameAs(formula);
            assertThat(formula.empirical()).hasToString("H2O");
            assertThat(formula.empirical().toUnicode()).isEqualTo("H₂O");
        }

        @Test
        void keeps_a_charged_formula_whole()
        {
            // [O-].[O-]
            Molecule molecule = new Molecule.Builder()
                .addAtoms(
                    Atom.builder("O").charge(-1).hydrogenCount(0).build(),
                    Atom.builder("O").charge(-1).hydrogenCount(0).build())
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule).empirical();

            assertThat(formula).hasToString("O22-");
            assertThat(formula.toUnicode()).isEqualTo("O₂²⁻");
        }
    }

    @Nested
    class Isotopes
    {
        @Test
        void counts_a_labelled_hydrogen_apart_from_its_unlabelled_siblings()
        {
            // CC, with one hydrogen of the second carbon deuterated
            Atom methyl = Atom.organic("C");
            Atom deuterated = Atom.builder("C").hydrogenIsotope(2, 1).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(methyl, deuterated)
                .addBond(methyl, deuterated, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("C2H5[2H]");
            assertThat(formula.toUnicode()).isEqualTo("C₂H₅²H");
        }

        @Test
        void names_heavy_hydrogen_isotopes_conventionally_when_asked()
        {
            Atom methyl = Atom.organic("C");
            Atom deuterated = Atom.builder("C").hydrogenIsotope(2, 1).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(methyl, deuterated)
                .addBond(methyl, deuterated, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula.toString(MolecularFormula.IsotopeStyle.HYDROGEN_ALIASES)).isEqualTo("C2H5D");
            assertThat(formula.toUnicode(MolecularFormula.IsotopeStyle.HYDROGEN_ALIASES)).isEqualTo("C₂H₅D");
        }

        @Test
        void writes_hydrogen_isotopes_after_unlabelled_hydrogen_in_ascending_mass_order()
        {
            // a single carbon carrying one protium, one deuterium and two tritium
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.builder("C").hydrogenIsotope(3, 2).hydrogenIsotope(2, 1).build())
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("CH[2H][3H]2");
            assertThat(formula.toString(MolecularFormula.IsotopeStyle.HYDROGEN_ALIASES)).isEqualTo("CHDT2");
        }

        @Test
        void counts_a_labelled_atom_apart_from_its_unlabelled_siblings()
        {
            // [13C]C
            Atom labelled = Atom.builder("C").isotope(13).build();
            Atom unlabelled = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(labelled, unlabelled)
                .addBond(labelled, unlabelled, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("C[13C]H6");
            assertThat(formula.toUnicode()).isEqualTo("C¹³CH₆");
        }

        @Test
        void treats_an_explicit_protium_label_as_plain_hydrogen()
        {
            Molecule molecule = new Molecule.Builder()
                .addAtom(Atom.builder("C").hydrogenIsotope(1, 4).build())
                .build();

            assertThat(MolecularFormula.of(molecule)).hasToString("CH4");
        }

        @Test
        void counts_a_nuclide_exactly_but_an_element_across_its_nuclides()
        {
            Atom methyl = Atom.organic("C");
            Atom deuterated = Atom.builder("C").hydrogenIsotope(2, 1).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(methyl, deuterated)
                .addBond(methyl, deuterated, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula.countOf("H")).isEqualTo(5);
            assertThat(formula.countOf("2H")).isEqualTo(1);
            assertThat(formula.countOfElement("H")).isEqualTo(6);
            assertThat(formula.elementCount()).isEqualTo(3);
            assertThat(formula.atomCount()).isEqualTo(8);
        }

        @Test
        void separates_an_isotopologue_from_the_molecule_it_labels()
        {
            Atom plainFirst = Atom.organic("C");
            Atom plainSecond = Atom.organic("C");
            Molecule ethane = new Molecule.Builder()
                .addAtoms(plainFirst, plainSecond)
                .addBond(plainFirst, plainSecond, BondOrder.SINGLE)
                .build();

            Atom methyl = Atom.organic("C");
            Atom deuterated = Atom.builder("C").hydrogenIsotope(2, 1).build();
            Molecule deuteroethane = new Molecule.Builder()
                .addAtoms(methyl, deuterated)
                .addBond(methyl, deuterated, BondOrder.SINGLE)
                .build();

            assertThat(MolecularFormula.of(ethane)).isNotEqualTo(MolecularFormula.of(deuteroethane));
        }

        @Test
        void counts_a_bonded_hydrogen_atom_as_its_own_nuclide()
        {
            // C[3H]: three attached protium, plus a tritium atom of its own
            Atom carbon = Atom.organic("C");
            Atom tritium = Atom.builder("H").isotope(3).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbon, tritium)
                .addBond(carbon, tritium, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("CH3[3H]");
            assertThat(formula.toUnicode()).isEqualTo("CH₃³H");
            assertThat(formula.countOf("H")).isEqualTo(3);
            assertThat(formula.countOf("3H")).isEqualTo(1);
            assertThat(formula.countOfElement("H")).isEqualTo(4);
        }

        @Test
        void gathers_a_bonded_hydrogen_atom_and_a_labelled_attached_one_under_the_same_nuclide()
        {
            // C([2H])[2H], one deuterium bonded as an atom and one labelled as an attached hydrogen
            Atom carbon = Atom.builder("C").hydrogenIsotope(2, 1).build();
            Atom deuterium = Atom.builder("H").isotope(2).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(carbon, deuterium)
                .addBond(carbon, deuterium, BondOrder.SINGLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("CH2[2H]2");
            assertThat(formula.toString(MolecularFormula.IsotopeStyle.HYDROGEN_ALIASES)).isEqualTo("CH2D2");
        }

        @Test
        void reduces_an_isotopologue_to_its_simplest_ratio()
        {
            // C(=C), each carbon carrying one protium and one deuterium
            Atom first = Atom.builder("C").hydrogenCount(2).hydrogenIsotope(2, 1).build();
            Atom second = Atom.builder("C").hydrogenCount(2).hydrogenIsotope(2, 1).build();
            Molecule molecule = new Molecule.Builder()
                .addAtoms(first, second)
                .addBond(first, second, BondOrder.DOUBLE)
                .build();

            MolecularFormula formula = MolecularFormula.of(molecule);

            assertThat(formula).hasToString("C2H2[2H]2");
            assertThat(formula.empirical()).hasToString("CH[2H]");
        }
    }
}
