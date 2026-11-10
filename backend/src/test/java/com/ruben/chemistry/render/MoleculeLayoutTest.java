package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.StereoDeclaration;
import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MoleculeLayoutTest
{
    private static final double TOLERANCE = 1e-9;

    /**
     * @param smiles the molecule to read
     * @return the molecule it describes
     */
    private static Molecule parse(String smiles)
    {
        return new SmilesParser(smiles).parse();
    }

    /**
     * @param smiles the molecule to lay out
     * @return its layout, drawn with every hydrogen and with straight chains
     */
    private static Depiction depict(String smiles)
    {
        return MoleculeLayout.layout(parse(smiles));
    }

    /**
     * @param smiles a molecule
     * @param hydrogens how its hydrogens should be drawn
     * @return it laid out that way
     */
    private static Depiction depict(String smiles, HydrogenStyle hydrogens)
    {
        return MoleculeLayout.layout(parse(smiles), DepictionOptions.defaults().withHydrogens(hydrogens));
    }

    /**
     * @param depiction a layout
     * @param atom the index of one of the laid-out molecule's own atoms
     * @return where that atom was placed
     */
    private static AtomPlacement placementOf(Depiction depiction, int atom)
    {
        return depiction.atoms().stream()
            .filter(placement -> placement.sourceAtom().equals(OptionalInt.of(atom)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Atom " + atom + " was not placed"));
    }

    /**
     * @param depiction a layout
     * @param atom the index of one of the laid-out molecule's own atoms
     * @return where that atom sits
     */
    private static Point positionOf(Depiction depiction, int atom)
    {
        return placementOf(depiction, atom).position();
    }

    /**
     * @param depiction a layout
     * @param first one placement's index
     * @param second the other placement's index
     * @return the bond drawn between them, whichever way round it is drawn
     */
    private static BondPlacement bondBetween(Depiction depiction, int first, int second)
    {
        return depiction.bonds().stream()
            .filter(bond -> (bond.first() == first && bond.second() == second)
                || (bond.first() == second && bond.second() == first))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No bond was drawn between " + first + " and " + second));
    }

    /**
     * @param depiction a layout
     * @param atom the index of one of the laid-out molecule's own atoms
     * @return the hydrogens the layout made up for that atom
     */
    private static List<AtomPlacement> hydrogensOf(Depiction depiction, int atom)
    {
        int centre = placementOf(depiction, atom).index();
        List<AtomPlacement> hydrogens = new ArrayList<>();
        for (BondPlacement bond : depiction.bonds())
        {
            if (bond.first() == centre && depiction.atom(bond.second()).isGeneratedHydrogen())
            {
                hydrogens.add(depiction.atom(bond.second()));
            }
            else if (bond.second() == centre && depiction.atom(bond.first()).isGeneratedHydrogen())
            {
                hydrogens.add(depiction.atom(bond.first()));
            }
        }
        return hydrogens;
    }

    /**
     * @param point where an atom was placed
     * @param x where it should sit across the page
     * @param y where it should sit up the page
     */
    private static void assertAt(Point point, double x, double y)
    {
        assertThat(point.x()).as("x").isCloseTo(x, within(TOLERANCE));
        assertThat(point.y()).as("y").isCloseTo(y, within(TOLERANCE));
    }

    /**
     * @param depiction a layout
     * @param corner the atom the angle is measured at
     * @param first one of its neighbours
     * @param second the other
     * @return the angle between the two bonds, in degrees, from zero to half a turn
     */
    private static double angleAt(Depiction depiction, int corner, int first, int second)
    {
        Point centre = positionOf(depiction, corner);
        Point one = positionOf(depiction, first).minus(centre);
        Point other = positionOf(depiction, second).minus(centre);
        double dot = one.x() * other.x() + one.y() * other.y();
        return Math.toDegrees(Math.acos(dot / (one.length() * other.length())));
    }

    /**
     * Works out which side of a double bond a substituent falls.
     *
     * @param depiction a layout
     * @param first the atom index of one end of the double bond
     * @param second the atom index of the other end
     * @param end the atom index of the end the substituent hangs off
     * @param substituent the substituent's atom index
     * @return a positive number one side of the bond and a negative number the other
     */
    private static double sideOf(Depiction depiction, int first, int second, int end, int substituent)
    {
        Point axis = positionOf(depiction, second).minus(positionOf(depiction, first));
        return axis.cross(positionOf(depiction, substituent).minus(positionOf(depiction, end)));
    }

    /**
     * Checks that every parity the molecule declares can be read back off the drawing.
     *
     * @param smiles the molecule to lay out
     */
    private static void assertParitiesAreDrawn(String smiles)
    {
        Molecule molecule = parse(smiles);
        Depiction depiction = MoleculeLayout.layout(molecule);
        List<StereoDeclaration.Tetrahedral> declarations = molecule.stereochemistry().declarations().stream()
            .filter(StereoDeclaration.Tetrahedral.class::isInstance)
            .map(StereoDeclaration.Tetrahedral.class::cast)
            .toList();
        assertThat(declarations).as("declared parities").isNotEmpty();

        for (StereoDeclaration.Tetrahedral declaration : declarations)
        {
            AtomPlacement centre = placementOf(depiction, declaration.centre());
            List<Point> ligands = new ArrayList<>();
            double[] depths = new double[Parity.LIGANDS];
            for (int slot = 0; slot < declaration.ligands().length; slot++)
            {
                int ligand = declaration.ligands()[slot];
                AtomPlacement placement = ligand == StereoDeclaration.IMPLICIT_HYDROGEN
                    ? hydrogensOf(depiction, declaration.centre()).getFirst()
                    : placementOf(depiction, ligand);
                BondPlacement bond = bondBetween(depiction, centre.index(), placement.index());
                if (bond.style().isStereo())
                {
                    assertThat(bond.first()).as("stereo bonds are drawn from the stereocentre").isEqualTo(centre.index());
                }
                ligands.add(placement.position());
                depths[slot] = Parity.depthOf(bond.style());
            }

            assertThat(Parity.read(centre.position(), ligands, depths))
                .as("the parity drawn at atom %d", declaration.centre())
                .contains(declaration.parity());
        }
    }

    @Nested
    class Basics
    {
        @Test
        void lays_out_nothing_for_a_molecule_with_no_atoms()
        {
            assertThat(MoleculeLayout.layout(new Molecule.Builder().build()).isEmpty()).isTrue();
        }

        @Test
        void rejects_a_molecule_that_is_not_there()
        {
            assertThatThrownBy(() -> MoleculeLayout.layout(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejects_options_that_are_not_there()
        {
            Molecule methane = parse("C");

            assertThatThrownBy(() -> MoleculeLayout.layout(methane, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void starts_a_single_part_molecule_at_the_origin()
        {
            assertAt(positionOf(depict("CCO"), 0), 0, 0);
        }

        @Test
        void draws_a_bond_one_bond_length_long_and_holds_a_hydrogen_closer_in()
        {
            Depiction depiction = depict("CC(C)C(=O)O");

            for (BondPlacement bond : depiction.bonds())
            {
                AtomPlacement first = depiction.atom(bond.first());
                AtomPlacement second = depiction.atom(bond.second());
                double expected = first.isGeneratedHydrogen() || second.isGeneratedHydrogen()
                    ? Depiction.HYDROGEN_BOND_LENGTH
                    : Depiction.BOND_LENGTH;

                assertThat(first.position().distanceTo(second.position())).isCloseTo(expected, within(TOLERANCE));
            }
        }

        @Test
        void draws_one_bond_for_every_bond_the_molecule_has_and_every_hydrogen_it_implies()
        {
            Molecule propanol = parse("CCO");
            Depiction depiction = MoleculeLayout.layout(propanol);

            int hydrogens = propanol.atoms().stream().mapToInt(propanol::totalHydrogenCount).sum();
            assertThat(depiction.atoms()).hasSize(propanol.atoms().size() + hydrogens);
            assertThat(depiction.bonds()).hasSize(propanol.bonds().size() + hydrogens);
        }

        @Test
        void carries_each_atom_s_element_charge_and_isotope_across()
        {
            Atom carbanion = Atom.builder("C").isotope(13).charge(-1).hydrogenCount(3).build();
            Depiction depiction = MoleculeLayout.layout(new Molecule.Builder().addAtom(carbanion).build());

            AtomPlacement placement = placementOf(depiction, 0);
            assertThat(placement.element()).isEqualTo("C");
            assertThat(placement.isotope()).hasValue(13);
            assertThat(placement.charge()).isEqualTo(-1);
            assertThat(placement.aromatic()).isFalse();
            assertThat(placement.isGeneratedHydrogen()).isFalse();
        }

        @Test
        void numbers_its_atoms_from_zero_in_the_order_they_are_held()
        {
            Depiction depiction = depict("CCO");

            for (int index = 0; index < depiction.atoms().size(); index++)
            {
                assertThat(depiction.atom(index).index()).isEqualTo(index);
            }
        }
    }

    @Nested
    class Hydrogens
    {
        @Test
        void draws_methane_as_a_cross()
        {
            Depiction depiction = depict("C");

            assertAt(positionOf(depiction, 0), 0, 0);
            List<Double> directions = hydrogensOf(depiction, 0).stream()
                .map(hydrogen -> Math.toDegrees(AngleFan.normalise(hydrogen.position().angle())))
                .sorted()
                .toList();
            assertThat(directions).hasSize(4);
            for (int slot = 0; slot < 4; slot++)
            {
                assertThat(directions.get(slot)).isCloseTo(90.0 * slot, within(TOLERANCE));
            }
        }

        @Test
        void names_every_made_up_hydrogen_as_hydrogen_belonging_to_no_atom()
        {
            for (AtomPlacement hydrogen : hydrogensOf(depict("C"), 0))
            {
                assertThat(hydrogen.element()).isEqualTo("H");
                assertThat(hydrogen.isGeneratedHydrogen()).isTrue();
                assertThat(hydrogen.sourceAtom()).isEmpty();
            }
        }

        @Test
        void labels_a_heavy_hydrogen_with_its_mass_number_and_leaves_protium_bare()
        {
            Atom water = Atom.builder("O").hydrogenCount(2).hydrogenIsotope(2, 1).build();
            Depiction depiction = MoleculeLayout.layout(new Molecule.Builder().addAtom(water).build());

            assertThat(hydrogensOf(depiction, 0)).extracting(AtomPlacement::isotope)
                .containsExactlyInAnyOrder(OptionalInt.empty(), OptionalInt.of(2));
        }

        @Test
        void leaves_hydrogens_out_when_asked()
        {
            Depiction depiction = MoleculeLayout.layout(
                parse("CCO"), DepictionOptions.defaults().withHydrogens(HydrogenStyle.NONE));

            assertThat(depiction.atoms()).hasSize(3)
                .allMatch(placement -> placement.sourceAtom().isPresent());
            assertThat(depiction.bonds()).hasSize(2);
        }
    }

    @Nested
    class ChainGeometry
    {
        @Test
        void draws_a_chain_of_single_bonds_straight()
        {
            Depiction depiction = depict("CCC");

            assertAt(positionOf(depiction, 0), 0, 0);
            assertAt(positionOf(depiction, 1), 1, 0);
            assertAt(positionOf(depiction, 2), 2, 0);
        }

        @Test
        void zig_zags_a_chain_when_asked()
        {
            Depiction depiction = MoleculeLayout.layout(
                parse("CCC"), DepictionOptions.defaults().withChainGeometry(com.ruben.chemistry.render.ChainGeometry.ZIGZAG));

            assertAt(positionOf(depiction, 0), 0, 0);
            assertAt(positionOf(depiction, 1), Math.sqrt(3) / 2, -0.5);
            assertAt(positionOf(depiction, 2), Math.sqrt(3), 0);
        }

        @Test
        void keeps_a_triple_bond_and_the_atoms_at_each_end_of_it_in_line()
        {
            Depiction depiction = depict("CC#CC");

            assertAt(positionOf(depiction, 0), 0, 0);
            assertAt(positionOf(depiction, 1), 1, 0);
            assertAt(positionOf(depiction, 2), 2, 0);
            assertAt(positionOf(depiction, 3), 3, 0);
        }

        @Test
        void bends_a_straight_chain_at_a_double_bond_so_its_ends_are_trigonal()
        {
            Depiction depiction = depict("CC=CC");

            assertThat(angleAt(depiction, 1, 0, 2)).isCloseTo(120, within(1e-6));
            assertThat(angleAt(depiction, 2, 1, 3)).isCloseTo(120, within(1e-6));
        }

        @Test
        void draws_each_bond_at_the_order_the_molecule_gives_it()
        {
            Depiction depiction = depict("CC=O");

            assertThat(bondBetween(depiction, placementOf(depiction, 0).index(), placementOf(depiction, 1).index()).order())
                .isEqualTo(BondOrder.SINGLE);
            assertThat(bondBetween(depiction, placementOf(depiction, 1).index(), placementOf(depiction, 2).index()).order())
                .isEqualTo(BondOrder.DOUBLE);
        }
    }

    @Nested
    class Rings
    {
        @Test
        void lays_a_ring_out_as_a_regular_polygon()
        {
            Depiction depiction = depict("C1CCCCC1", HydrogenStyle.CONDENSED);

            assertThat(depiction.atoms()).hasSize(6);
            assertThat(depiction.rings()).hasSize(1);
            for (BondPlacement bond : depiction.bonds())
            {
                assertThat(depiction.atom(bond.first()).position().distanceTo(depiction.atom(bond.second()).position()))
                    .isCloseTo(Depiction.BOND_LENGTH, within(TOLERANCE));
            }
        }

        @Test
        void fuses_rings_along_a_shared_bond()
        {
            Depiction depiction = depict("c1ccc2ccccc2c1", HydrogenStyle.CONDENSED);

            assertThat(depiction.atoms()).hasSize(10);
            assertThat(depiction.rings()).hasSize(2);
            for (BondPlacement bond : depiction.bonds())
            {
                assertThat(depiction.atom(bond.first()).position().distanceTo(depiction.atom(bond.second()).position()))
                    .isCloseTo(Depiction.BOND_LENGTH, within(TOLERANCE));
            }
        }

        @Test
        void fuses_three_and_four_rings_with_regular_bonds()
        {
            for (String smiles : List.of("c1ccc2cc3ccccc3cc2c1", "c1ccc2c(c1)ccc3ccccc23", "c1cc2ccc3cccc4ccc(c1)c2c34"))
            {
                Depiction depiction = depict(smiles, HydrogenStyle.CONDENSED);
                for (BondPlacement bond : depiction.bonds())
                {
                    assertThat(depiction.atom(bond.first()).position().distanceTo(depiction.atom(bond.second()).position()))
                        .as(smiles)
                        .isCloseTo(Depiction.BOND_LENGTH, within(1e-6));
                }
            }
        }

        @Test
        void draws_the_hydrogens_of_a_ring_atom_outside_the_ring()
        {
            for (String smiles : List.of("C1CC1", "C1CCCCC1", "C1CCCCCCCCCCC1"))
            {
                Depiction depiction = depict(smiles);
                double x = depiction.rings().getFirst().atoms().stream().mapToDouble(atom -> depiction.atom(atom).position().x()).average().orElseThrow();
                double y = depiction.rings().getFirst().atoms().stream().mapToDouble(atom -> depiction.atom(atom).position().y()).average().orElseThrow();
                Point middle = new Point(x, y);
                for (BondPlacement bond : depiction.bonds())
                {
                    AtomPlacement hydrogen = depiction.atom(bond.second());
                    if (hydrogen.element().equals("H"))
                    {
                        assertThat(hydrogen.position().distanceTo(middle))
                            .as(smiles)
                            .isGreaterThan(depiction.atom(bond.first()).position().distanceTo(middle));
                    }
                }
            }
        }

        @Test
        void lays_out_a_spiro_atom_shared_by_several_rings()
        {
            Depiction depiction = depict("C12(CCC1)CCC2", HydrogenStyle.CONDENSED);

            assertThat(depiction.rings()).hasSize(2);
            for (int first = 0; first < depiction.atoms().size(); first++)
            {
                for (int second = first + 1; second < depiction.atoms().size(); second++)
                {
                    assertThat(depiction.atom(first).position().distanceTo(depiction.atom(second).position())).isGreaterThan(0.3);
                }
            }
        }

        @Test
        void lays_out_spiro_and_bridged_systems_without_overlapping_atoms()
        {
            for (String smiles : List.of("C1CCC2(CC1)CCCC2", "C1CC2CCC1C2", "C1CC1", "OC1CCCCC1CC2CC2"))
            {
                Depiction depiction = depict(smiles, HydrogenStyle.CONDENSED);
                for (int first = 0; first < depiction.atoms().size(); first++)
                {
                    for (int second = first + 1; second < depiction.atoms().size(); second++)
                    {
                        assertThat(depiction.atom(first).position().distanceTo(depiction.atom(second).position()))
                            .as(smiles + " atoms " + first + " and " + second)
                            .isGreaterThan(0.3);
                    }
                }
            }
        }

        @Test
        void circles_an_aromatic_ring_only_when_skeletal()
        {
            Molecule benzene = parse("c1ccccc1");

            assertThat(MoleculeLayout.layout(benzene, DepictionOptions.skeletal()).rings())
                .singleElement().extracting(RingPlacement::circle).isEqualTo(true);
            assertThat(MoleculeLayout.layout(benzene).rings())
                .singleElement().extracting(RingPlacement::circle).isEqualTo(false);
        }

        @Test
        void does_not_circle_a_ring_that_is_not_aromatic()
        {
            assertThat(MoleculeLayout.layout(parse("C1CCCCC1"), DepictionOptions.skeletal()).rings())
                .singleElement().extracting(RingPlacement::circle).isEqualTo(false);
        }

        @Test
        void keeps_substituents_outside_the_ring()
        {
            Depiction depiction = MoleculeLayout.layout(parse("Cc1ccccc1"), DepictionOptions.skeletal());
            Point middle = new Point(
                depiction.rings().getFirst().atoms().stream().mapToDouble(atom -> depiction.atom(atom).position().x()).average().orElseThrow(),
                depiction.rings().getFirst().atoms().stream().mapToDouble(atom -> depiction.atom(atom).position().y()).average().orElseThrow());

            assertThat(positionOf(depiction, 0).distanceTo(middle)).isCloseTo(1.0 + 1.0, within(TOLERANCE));
        }
    }

    @Nested
    class DisconnectedParts
    {
        @Test
        void spreads_the_parts_of_a_molecule_apart()
        {
            Depiction depiction = depict("C.C");

            // each methane is two short hydrogen bonds wide, and the two are set a gap apart
            assertAt(positionOf(depiction, 0), 0.7, 0);
            assertAt(positionOf(depiction, 1), 3.6, 0);
        }
    }

    @Nested
    class TetrahedralStereochemistry
    {
        @Test
        void draws_exactly_one_bond_out_of_the_page_at_a_stereocentre()
        {
            assertThat(depict("N[C@@H](C)C(=O)O").bonds())
                .filteredOn(bond -> bond.style().isStereo())
                .hasSize(1);
        }

        @Test
        void draws_no_stereo_bond_where_no_parity_is_declared()
        {
            assertThat(depict("NC(C)C(=O)O").bonds()).allMatch(bond -> bond.style() == BondStyle.PLAIN);
        }

        @Test
        void draws_the_parity_it_is_given()
        {
            assertParitiesAreDrawn("N[C@@H](C)C(=O)O");
        }

        @Test
        void draws_the_opposite_parity_it_is_given()
        {
            assertParitiesAreDrawn("N[C@H](C)C(=O)O");
        }

        @Test
        void draws_a_stereocentre_with_four_heavy_ligands()
        {
            assertParitiesAreDrawn("N[C@@](F)(Cl)Br");
        }

        @Test
        void turns_the_bond_over_when_the_parity_turns_over()
        {
            BondStyle clockwise = stereoStyleOf("N[C@@H](C)C(=O)O");
            BondStyle anticlockwise = stereoStyleOf("N[C@H](C)C(=O)O");

            assertThat(clockwise).isNotEqualTo(anticlockwise);
            assertThat(List.of(clockwise, anticlockwise)).containsExactlyInAnyOrder(BondStyle.WEDGE, BondStyle.HASH);
        }

        @Test
        void draws_a_parity_whose_hydrogen_it_was_told_not_to_draw_on_another_ligand_instead()
        {
            Depiction depiction = depict("N[C@@H](C)C(=O)O", HydrogenStyle.NONE);

            assertThat(depiction.bonds()).filteredOn(bond -> bond.style().isStereo()).hasSize(1);
            assertThat(depiction.atoms()).noneMatch(AtomPlacement::isGeneratedHydrogen);
        }

        @Test
        void turns_that_bond_over_when_the_parity_turns_over()
        {
            for (HydrogenStyle hydrogens : List.of(HydrogenStyle.NONE, HydrogenStyle.CONDENSED))
            {
                BondStyle clockwise = stereoStyleOf("N[C@@H](C)C(=O)O", hydrogens);
                BondStyle anticlockwise = stereoStyleOf("N[C@H](C)C(=O)O", hydrogens);

                assertThat(List.of(clockwise, anticlockwise)).containsExactlyInAnyOrder(BondStyle.WEDGE, BondStyle.HASH);
            }
        }

        @Test
        void draws_the_same_arrangement_whether_or_not_the_hydrogen_is_drawn()
        {
            Depiction shown = depict("N[C@@H](C)C(=O)O", HydrogenStyle.SEPARATE);
            Depiction hidden = depict("N[C@@H](C)C(=O)O", HydrogenStyle.NONE);

            assertThat(shown.bonds()).filteredOn(bond -> bond.style().isStereo()).hasSize(1);
            assertThat(hidden.bonds()).filteredOn(bond -> bond.style().isStereo()).hasSize(1);
        }

        @Test
        void turns_an_undrawn_hydrogens_stereocentre_over_when_the_parity_turns_over()
        {
            for (HydrogenStyle hydrogens : List.of(HydrogenStyle.NONE, HydrogenStyle.CONDENSED))
            {
                BondStyle clockwise = stereoStyleOf("N[C@@H](C)C(=O)O", hydrogens);
                BondStyle anticlockwise = stereoStyleOf("N[C@H](C)C(=O)O", hydrogens);

                assertThat(List.of(clockwise, anticlockwise)).containsExactlyInAnyOrder(BondStyle.WEDGE, BondStyle.HASH);
            }
        }

        /**
         * @param smiles a molecule with exactly one stereo bond
         * @return how that bond leaves the plane of the page
         */
        private static BondStyle stereoStyleOf(String smiles)
        {
            return stereoStyleOf(smiles, HydrogenStyle.SEPARATE);
        }

        /**
         * @param smiles a molecule with exactly one stereo bond
         * @param hydrogens how its hydrogens should be drawn
         * @return how that bond leaves the plane of the page
         */
        private static BondStyle stereoStyleOf(String smiles, HydrogenStyle hydrogens)
        {
            return depict(smiles, hydrogens).bonds().stream()
                .map(BondPlacement::style)
                .filter(BondStyle::isStereo)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No stereo bond was drawn for " + smiles));
        }
    }

    @Nested
    class DoubleBondConfiguration
    {
        @Test
        void draws_trans_substituents_on_opposite_sides_of_the_bond()
        {
            Depiction depiction = depict("F/C=C/F");

            assertThat(sideOf(depiction, 1, 2, 1, 0) * sideOf(depiction, 1, 2, 2, 3)).isNegative();
        }

        @Test
        void draws_cis_substituents_on_the_same_side_of_the_bond()
        {
            Depiction depiction = depict("F/C=C\\F");

            assertThat(sideOf(depiction, 1, 2, 1, 0) * sideOf(depiction, 1, 2, 2, 3)).isPositive();
        }

        @Test
        void draws_a_trans_alkene_with_its_chains_on_opposite_sides()
        {
            Depiction depiction = depict("C/C=C/C");

            assertThat(sideOf(depiction, 1, 2, 1, 0) * sideOf(depiction, 1, 2, 2, 3)).isNegative();
        }

        @Test
        void draws_a_cis_alkene_with_its_chains_on_the_same_side()
        {
            Depiction depiction = depict("C/C=C\\C");

            assertThat(sideOf(depiction, 1, 2, 1, 0) * sideOf(depiction, 1, 2, 2, 3)).isPositive();
        }

        @Test
        void keeps_the_substituents_clear_of_the_bond_they_are_measured_against()
        {
            Depiction depiction = depict("F/C=C/F");

            assertThat(Math.abs(sideOf(depiction, 1, 2, 1, 0))).isGreaterThan(0.1);
            assertThat(Math.abs(sideOf(depiction, 1, 2, 2, 3))).isGreaterThan(0.1);
        }

        @Test
        void leaves_an_undeclared_double_bond_to_fall_where_it_may()
        {
            assertThat(depict("FC=CF").bonds()).isNotEmpty();
        }
    }

    @Nested
    class Skeletal
    {
        @Test
        void leaves_a_carbon_in_a_chain_as_a_bare_vertex()
        {
            Depiction depiction = skeletal("CCCC");

            assertThat(depiction.atoms()).allMatch(AtomPlacement::vertex);
        }

        @Test
        void still_writes_a_heteroatom_out()
        {
            Depiction depiction = skeletal("CCO");

            assertThat(atomOf(depiction, "O").vertex()).isFalse();
            assertThat(atomOf(depiction, "C").vertex()).isTrue();
        }

        @Test
        void gathers_a_heteroatoms_hydrogens_into_its_own_label()
        {
            assertThat(atomOf(skeletal("CCO"), "O").hydrogens()).isEqualTo(1);
        }

        @Test
        void draws_no_hydrogen_of_its_own()
        {
            assertThat(skeletal("CCO").atoms()).noneMatch(AtomPlacement::isGeneratedHydrogen);
        }

        @Test
        void writes_a_carbon_that_has_nothing_drawn_to_it()
        {
            assertThat(atomOf(skeletal("C"), "C").vertex()).isFalse();
        }

        @Test
        void writes_a_carbon_carrying_a_charge()
        {
            assertThat(atomOf(skeletal("CC[CH2-]"), "C").vertex()).isTrue();
            assertThat(skeletal("CC[CH2-]").atoms()).anySatisfy(atom ->
            {
                assertThat(atom.charge()).isEqualTo(-1);
                assertThat(atom.vertex()).isFalse();
            });
        }

        @Test
        void writes_a_carbon_carrying_a_mass_number()
        {
            assertThat(skeletal("CC[13CH3]").atoms()).anySatisfy(atom ->
            {
                assertThat(atom.isotope()).hasValue(13);
                assertThat(atom.vertex()).isFalse();
            });
        }

        @Test
        void bends_a_chain_so_that_each_vertex_can_be_told_apart()
        {
            Depiction depiction = skeletal("CCCC");

            assertThat(sideOf(depiction, 0, 1, 1, 2) * sideOf(depiction, 1, 2, 2, 3)).isNotZero();
            assertThat(positionOf(depiction, 0).y()).isNotEqualTo(positionOf(depiction, 1).y());
        }

        @Test
        void keeps_the_bonds_the_molecule_has_and_adds_none()
        {
            Molecule propanol = parse("CCO");

            assertThat(skeletal("CCO").bonds()).hasSize(propanol.bonds().size());
        }

        @Test
        void still_draws_a_stereocentre_out_of_the_plane()
        {
            assertThat(skeletal("N[C@@H](C)C(=O)O").bonds())
                .filteredOn(bond -> bond.style().isStereo())
                .hasSize(1);
        }

        /**
         * @param smiles a molecule
         * @return it laid out as a line-angle drawing
         */
        private static Depiction skeletal(String smiles)
        {
            return MoleculeLayout.layout(parse(smiles), DepictionOptions.skeletal());
        }

        /**
         * @param depiction a layout
         * @param element an element symbol
         * @return the first atom of that element
         */
        private static AtomPlacement atomOf(Depiction depiction, String element)
        {
            return depiction.atoms().stream()
                .filter(atom -> atom.element().equals(element))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nothing was drawn for " + element));
        }
    }

    @Nested
    class BoundingBox
    {
        @Test
        void surrounds_every_atom_it_drew()
        {
            Depiction depiction = depict("CCO");
            Bounds bounds = depiction.bounds();

            for (AtomPlacement placement : depiction.atoms())
            {
                assertThat(placement.position().x()).isBetween(bounds.minX(), bounds.maxX());
                assertThat(placement.position().y()).isBetween(bounds.minY(), bounds.maxY());
            }
        }
    }
}
