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
}
