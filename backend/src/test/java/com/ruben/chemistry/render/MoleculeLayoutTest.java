package com.ruben.chemistry.render;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.StereoDeclaration;
import com.ruben.chemistry.render.rings.LayoutQuality;
import com.ruben.chemistry.render.rings.RingRefinement;
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
     * @param smiles the molecule to lay out
     * @return its layout, drawn with every hydrogen and with straight chains
     */
    private static Layout depict(String smiles)
    {
        return MoleculeLayout.layout(Molecule.fromSmiles(smiles));
    }

    /**
     * @param smiles a molecule
     * @param hydrogens how its hydrogens should be drawn
     * @return it laid out that way
     */
    private static Layout depict(String smiles, HydrogenStyle hydrogens)
    {
        return MoleculeLayout.layout(Molecule.fromSmiles(smiles), DepictionOptions.defaults().withHydrogens(hydrogens));
    }

    /**
     * @param layout a layout
     * @param atom the index of one of the laid-out molecule's own atoms
     * @return where that atom sits
     */
    private static Point positionOf(Layout layout, int atom)
    {
        return layout.placementOfAtom(atom).position();
    }

    /**
     * @param layout a layout
     * @param atom the index of one of the laid-out molecule's own atoms
     * @return the hydrogens the layout made up for that atom
     */
    private static List<AtomPlacement> hydrogensOf(Layout layout, int atom)
    {
        Depiction depiction = layout.depiction();
        int centre = layout.placementOfAtom(atom).placementIndex();
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
     * @param layout a layout
     * @param corner the atom the angle is measured at
     * @param first one of its neighbours
     * @param second the other
     * @return the angle between the two bonds, in degrees, from zero to half a turn
     */
    private static double angleAt(Layout layout, int corner, int first, int second)
    {
        Point centre = positionOf(layout, corner);
        Point one = positionOf(layout, first).minus(centre);
        Point other = positionOf(layout, second).minus(centre);
        double dot = one.x() * other.x() + one.y() * other.y();
        return Math.toDegrees(Math.acos(dot / (one.length() * other.length())));
    }

    /**
     * Works out which side of a double bond a substituent falls.
     *
     * @param layout a layout
     * @param first the atom index of one end of the double bond
     * @param second the atom index of the other end
     * @param end the atom index of the end the substituent hangs off
     * @param substituent the substituent's atom index
     * @return a positive number one side of the bond and a negative number the other
     */
    private static double sideOf(Layout layout, int first, int second, int end, int substituent)
    {
        Point axis = positionOf(layout, second).minus(positionOf(layout, first));
        return axis.cross(positionOf(layout, substituent).minus(positionOf(layout, end)));
    }

    /**
     * Checks that every parity the molecule declares can be read back off the drawing.
     *
     * @param smiles the molecule to lay out
     */
    private static void assertParitiesAreDrawn(String smiles)
    {
        Molecule molecule = Molecule.fromSmiles(smiles);
        Layout layout = MoleculeLayout.layout(molecule);
        Depiction depiction = layout.depiction();
        List<StereoDeclaration.Tetrahedral> declarations = molecule.stereochemistry().declarations().stream()
            .filter(StereoDeclaration.Tetrahedral.class::isInstance)
            .map(StereoDeclaration.Tetrahedral.class::cast)
            .toList();
        assertThat(declarations).as("declared parities").isNotEmpty();

        for (StereoDeclaration.Tetrahedral declaration : declarations)
        {
            AtomPlacement centre = layout.placementOfAtom(declaration.centre());
            List<Point> ligands = new ArrayList<>();
            double[] depths = new double[Parity.LIGANDS];
            for (int slot = 0; slot < declaration.ligands().length; slot++)
            {
                int ligand = declaration.ligands()[slot];
                AtomPlacement placement = ligand == StereoDeclaration.IMPLICIT_HYDROGEN
                    ? hydrogensOf(layout, declaration.centre()).getFirst()
                    : layout.placementOfAtom(ligand);
                BondPlacement bond = depiction.bondBetween(centre.placementIndex(), placement.placementIndex());
                if (bond.style().isStereo())
                {
                    assertThat(bond.first()).as("stereo bonds are drawn from the stereocentre").isEqualTo(centre.placementIndex());
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
            assertThat(MoleculeLayout.layout(new Molecule.Builder().build()).depiction().isEmpty()).isTrue();
        }

        @Test
        void rejects_a_molecule_that_is_not_there()
        {
            assertThatThrownBy(() -> MoleculeLayout.layout(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejects_options_that_are_not_there()
        {
            Molecule methane = Molecule.fromSmiles("C");

            assertThatThrownBy(() -> MoleculeLayout.layout(methane, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void centres_a_single_part_molecule_on_the_origin()
        {
            Point middle = depict("CCO").depiction().bounds().centre();

            assertAt(middle, 0, 0);
        }

        @Test
        void draws_a_bond_one_bond_length_long_and_holds_a_hydrogen_closer_in()
        {
            Depiction depiction = depict("CC(C)C(=O)O").depiction();

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
            Molecule propanol = Molecule.fromSmiles("CCO");
            Depiction depiction = MoleculeLayout.layout(propanol).depiction();

            int hydrogens = propanol.atoms().stream().mapToInt(propanol::totalHydrogenCount).sum();
            assertThat(depiction.atoms()).hasSize(propanol.atoms().size() + hydrogens);
            assertThat(depiction.bonds()).hasSize(propanol.bonds().size() + hydrogens);
        }

        @Test
        void carries_each_atom_s_element_charge_and_isotope_across()
        {
            Atom carbanion = Atom.builder("C").isotope(13).charge(-1).hydrogenCount(3).build();
            Layout layout = MoleculeLayout.layout(new Molecule.Builder().addAtom(carbanion).build());
            Depiction depiction = layout.depiction();

            AtomPlacement placement = layout.placementOfAtom(0);
            assertThat(placement.element()).isEqualTo("C");
            assertThat(placement.isotope()).hasValue(13);
            assertThat(placement.charge()).isEqualTo(-1);
            assertThat(placement.aromatic()).isFalse();
            assertThat(placement.isGeneratedHydrogen()).isFalse();
        }

        @Test
        void numbers_its_atoms_from_zero_in_the_order_they_are_held()
        {
            Depiction depiction = depict("CCO").depiction();

            for (int index = 0; index < depiction.atoms().size(); index++)
            {
                assertThat(depiction.atom(index).placementIndex()).isEqualTo(index);
            }
        }
    }

    @Nested
    class Hydrogens
    {
        @Test
        void draws_methane_as_a_cross()
        {
            Layout layout = depict("C");
            Depiction depiction = layout.depiction();

            assertAt(positionOf(layout, 0), 0, 0);
            List<Double> directions = hydrogensOf(layout, 0).stream()
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
            Layout layout = MoleculeLayout.layout(new Molecule.Builder().addAtom(water).build());
            Depiction depiction = layout.depiction();

            assertThat(hydrogensOf(layout, 0)).extracting(AtomPlacement::isotope)
                .containsExactlyInAnyOrder(OptionalInt.empty(), OptionalInt.of(2));
        }

        @Test
        void leaves_hydrogens_out_when_asked()
        {
            Depiction depiction = MoleculeLayout.layout(
                    Molecule.fromSmiles("CCO"), DepictionOptions.defaults().withHydrogens(HydrogenStyle.NONE)).depiction();

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
            Layout layout = depict("CCC");
            Point first = positionOf(layout, 0);

            assertAt(positionOf(layout, 1).minus(first), 1, 0);
            assertAt(positionOf(layout, 2).minus(first), 2, 0);
        }

        @Test
        void zig_zags_a_chain_when_asked()
        {
            Layout layout = MoleculeLayout.layout(
                    Molecule.fromSmiles("CCC"), DepictionOptions.defaults().withChainGeometry(com.ruben.chemistry.render.ChainGeometry.ZIGZAG));
            Point first = positionOf(layout, 0);

            assertAt(positionOf(layout, 1).minus(first), Math.sqrt(3) / 2, -0.5);
            assertAt(positionOf(layout, 2).minus(first), Math.sqrt(3), 0);
        }

        @Test
        void keeps_a_triple_bond_and_the_atoms_at_each_end_of_it_in_line()
        {
            Layout layout = depict("CC#CC");
            Point first = positionOf(layout, 0);

            assertAt(positionOf(layout, 1).minus(first), 1, 0);
            assertAt(positionOf(layout, 2).minus(first), 2, 0);
            assertAt(positionOf(layout, 3).minus(first), 3, 0);
        }

        @Test
        void bends_a_straight_chain_at_a_double_bond_so_its_ends_are_trigonal()
        {
            Layout layout = depict("CC=CC");
            Depiction depiction = layout.depiction();

            assertThat(angleAt(layout, 1, 0, 2)).isCloseTo(120, within(1e-6));
            assertThat(angleAt(layout, 2, 1, 3)).isCloseTo(120, within(1e-6));
        }

        @Test
        void draws_each_bond_at_the_order_the_molecule_gives_it()
        {
            Layout layout = depict("CC=O");
            Depiction depiction = layout.depiction();

            assertThat(depiction.bondBetween(layout.placementOfAtom(0).placementIndex(), layout.placementOfAtom(1).placementIndex()).order())
                .isEqualTo(BondOrder.SINGLE);
            assertThat(depiction.bondBetween(layout.placementOfAtom(1).placementIndex(), layout.placementOfAtom(2).placementIndex()).order())
                .isEqualTo(BondOrder.DOUBLE);
        }
    }

    @Nested
    class Rings
    {
        @Test
        void lays_a_ring_out_as_a_regular_polygon()
        {
            Depiction depiction = depict("C1CCCCC1", HydrogenStyle.CONDENSED).depiction();

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
            Depiction depiction = depict("c1ccc2ccccc2c1", HydrogenStyle.CONDENSED).depiction();

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
                Depiction depiction = depict(smiles, HydrogenStyle.CONDENSED).depiction();
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
                Depiction depiction = depict(smiles).depiction();
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
            Depiction depiction = depict("C12(CCC1)CCC2", HydrogenStyle.CONDENSED).depiction();

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
                Depiction depiction = depict(smiles, HydrogenStyle.CONDENSED).depiction();
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
            Molecule benzene = Molecule.fromSmiles("c1ccccc1");

            assertThat(MoleculeLayout.layout(benzene, DepictionOptions.skeletal()).depiction().rings())
                .singleElement().extracting(RingPlacement::circle).isEqualTo(true);
            assertThat(MoleculeLayout.layout(benzene).depiction().rings())
                .singleElement().extracting(RingPlacement::circle).isEqualTo(false);
        }

        @Test
        void does_not_circle_a_ring_that_is_not_aromatic()
        {
            assertThat(MoleculeLayout.layout(Molecule.fromSmiles("C1CCCCC1"), DepictionOptions.skeletal()).depiction().rings())
                .singleElement().extracting(RingPlacement::circle).isEqualTo(false);
        }

        @Test
        void keeps_substituents_outside_the_ring()
        {
            Layout layout = MoleculeLayout.layout(Molecule.fromSmiles("Cc1ccccc1"), DepictionOptions.skeletal());
            Depiction depiction = layout.depiction();
            Point middle = new Point(
                depiction.rings().getFirst().atoms().stream().mapToDouble(atom -> depiction.atom(atom).position().x()).average().orElseThrow(),
                depiction.rings().getFirst().atoms().stream().mapToDouble(atom -> depiction.atom(atom).position().y()).average().orElseThrow());

            assertThat(positionOf(layout, 0).distanceTo(middle)).isCloseTo(1.0 + 1.0, within(TOLERANCE));
        }
    }

    @Nested
    class SubstituentsOfBridgedRings
    {
        private static Depiction skeletalDepiction(String smiles)
        {
            return MoleculeLayout.layout(Molecule.fromSmiles(smiles), DepictionOptions.skeletal()).depiction();
        }

        private static int crossingsOf(Depiction depiction)
        {
            Point[] points = depiction.atoms().stream().map(AtomPlacement::position).toArray(Point[]::new);
            int[][] bonds = depiction.bonds().stream().map(bond -> new int[] {bond.first(), bond.second()}).toArray(int[][]::new);
            return LayoutQuality.crossings(points, bonds);
        }

        @Test
        void keeps_the_methyl_of_tropane_from_crossing_the_ring()
        {
            assertThat(crossingsOf(skeletalDepiction("CN1C2CCC1CCC2"))).isZero();
        }

        @Test
        void keeps_the_methyl_of_tropane_clear_of_the_ring_atoms()
        {
            Depiction depiction = skeletalDepiction("CN1C2CCC1CCC2");
            Point[] points = depiction.atoms().stream().map(AtomPlacement::position).toArray(Point[]::new);
            int[][] bonds = depiction.bonds().stream().map(bond -> new int[] {bond.first(), bond.second()}).toArray(int[][]::new);

            assertThat(LayoutQuality.minimumAtomDistance(points, bonds)).isGreaterThan(0.5);
        }
    }

    @Nested
    class CagedRingSystems
    {
        private static final String CUBANE = "C12C3C4C1C5C2C3C45";

        /**
         * @param smiles a ring system of carbons only
         * @return its ring atoms, as drawn skeletally
         */
        private static Depiction skeletalDepiction(String smiles)
        {
            return MoleculeLayout.layout(Molecule.fromSmiles(smiles), DepictionOptions.skeletal()).depiction();
        }

        private static Point[] pointsOf(Depiction depiction)
        {
            return depiction.atoms().stream().map(AtomPlacement::position).toArray(Point[]::new);
        }

        private static int[][] bondsOf(Depiction depiction)
        {
            return depiction.bonds().stream().map(bond -> new int[] {bond.first(), bond.second()}).toArray(int[][]::new);
        }

        /**
         * @param depiction a drawing
         * @return how evenly it foreshortens: 1 when every bond is drawn the same length, falling towards 0 as one
         *     bond is drawn far shorter than another
         */
        private static double evennessOf(Depiction depiction)
        {
            Point[] points = pointsOf(depiction);
            double shortest = Double.POSITIVE_INFINITY;
            double longest = 0;
            for (int[] bond : bondsOf(depiction))
            {
                double length = points[bond[0]].distanceTo(points[bond[1]]);
                shortest = Math.min(shortest, length);
                longest = Math.max(longest, length);
            }
            return shortest / longest;
        }

        /**
         * @param depiction a drawing
         * @return the sharpest angle between two bonds at an atom, in degrees
         */
        private static double sharpestAngleOf(Depiction depiction)
        {
            Point[] points = pointsOf(depiction);
            List<List<Integer>> neighbours = new ArrayList<>();
            for (int atom = 0; atom < points.length; atom++)
            {
                neighbours.add(new ArrayList<>());
            }
            for (int[] bond : bondsOf(depiction))
            {
                neighbours.get(bond[0]).add(bond[1]);
                neighbours.get(bond[1]).add(bond[0]);
            }
            double sharpest = 180;
            for (int centre = 0; centre < points.length; centre++)
            {
                List<Integer> around = neighbours.get(centre);
                for (int first = 0; first < around.size(); first++)
                {
                    for (int second = first + 1; second < around.size(); second++)
                    {
                        Point one = points[around.get(first)].minus(points[centre]);
                        Point other = points[around.get(second)].minus(points[centre]);
                        double cosine = (one.x() * other.x() + one.y() * other.y()) / (one.length() * other.length());
                        sharpest = Math.min(sharpest, Math.toDegrees(Math.acos(Math.clamp(cosine, -1, 1))));
                    }
                }
            }
            return sharpest;
        }

        @Test
        void settles_for_the_least_pinched_angles_when_a_cage_has_to_be_drawn_flat()
        {
            // with no projection there is no drawing a molecule could have, so the flat search is judged on how little
            // it pinches an angle, and must not trade that away for bonds of one length however hard it looks
            Depiction depiction = MoleculeLayout.layout(
                    Molecule.fromSmiles("C1CC2CCC1CC2"),
                    DepictionOptions.skeletal().withProjectCages(false)
                            .withRingRefinement(RingRefinement.THOROUGH)).depiction();

            assertThat(sharpestAngleOf(depiction)).isGreaterThan(25);
        }

        @Test
        void draws_cubane_as_an_evenly_foreshortened_view_of_its_shape()
        {
            Depiction depiction = skeletalDepiction(CUBANE);

            assertThat(depiction.atoms()).hasSize(8);
            assertThat(evennessOf(depiction)).isGreaterThan(0.6);
            assertThat(LayoutQuality.minimumAtomDistance(pointsOf(depiction), bondsOf(depiction))).isGreaterThan(0.3);
        }

        @Test
        void never_draws_a_cage_flat_with_every_bond_one_bond_length_long()
        {
            // such a drawing needs bond angles no atom could hold, and the harder a flat search looks the likelier it
            // is to turn one up, so a cage is drawn as a view of its shape in space at any effort
            Depiction depiction = MoleculeLayout.layout(
                    Molecule.fromSmiles("C1CC2CCC1CC2"),
                    DepictionOptions.skeletal().withRingRefinement(RingRefinement.THOROUGH)).depiction();

            assertThat(LayoutQuality.lengthError(pointsOf(depiction), bondsOf(depiction), 1)).isGreaterThan(0.05);
            assertThat(LayoutQuality.minimumAtomDistance(pointsOf(depiction), bondsOf(depiction))).isGreaterThan(0.3);
        }

        @Test
        void never_draws_two_atoms_of_a_system_that_cannot_have_unit_bonds_on_top_of_each_other()
        {
            Depiction depiction = skeletalDepiction("C1C2CC1C2");

            assertThat(depiction.atoms()).hasSize(5);
            assertThat(LayoutQuality.minimumAtomDistance(pointsOf(depiction), bondsOf(depiction))).isGreaterThan(0.3);
        }

        @Test
        void draws_a_different_view_of_a_cage_for_each_ring_ranking_that_has_one()
        {
            Molecule molecule = Molecule.fromSmiles("C1CC2CCC1CC2");
            Depiction best = MoleculeLayout.layout(molecule, DepictionOptions.skeletal()).depiction();
            List<Layout> layouts = MoleculeLayout.layouts(molecule, DepictionOptions.skeletal(), 3);
            Depiction second = layouts.get(1).depiction();

            assertThat(layouts).hasSizeGreaterThanOrEqualTo(2);
            assertThat(layouts.getFirst().depiction().looksLike(best)).isTrue();
            assertThat(second.looksLike(best)).isFalse();
            assertThat(second.atoms()).hasSameSizeAs(best.atoms());
            assertThat(LayoutQuality.minimumAtomDistance(pointsOf(second), bondsOf(second))).isGreaterThan(0.3);
        }

        @Test
        void asks_for_at_least_one_layout()
        {
            assertThatThrownBy(() -> MoleculeLayout.layouts(Molecule.fromSmiles("CC"), DepictionOptions.skeletal(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void gathers_alternative_layouts_into_one_with_a_fragment_each()
        {
            Layout first = MoleculeLayout.layout(Molecule.fromSmiles(CUBANE), DepictionOptions.skeletal());
            Layout second = MoleculeLayout.layout(Molecule.fromSmiles(CUBANE), DepictionOptions.skeletal().withRingRanking(1));
            Depiction combined = Layout.sideBySide(List.of(first, second)).depiction();

            assertThat(combined.atoms()).hasSize(first.depiction().atoms().size() * 2);
            assertThat(combined.fragments()).hasSize(2);
            assertThat(combined.bonds()).hasSize(first.depiction().bonds().size() * 2);
        }

        @Test
        void rejects_a_negative_ring_ranking()
        {
            assertThatThrownBy(() -> DepictionOptions.skeletal().withRingRanking(-1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void leaves_a_cage_as_first_laid_out_when_ring_refinement_is_none()
        {
            Depiction refined = MoleculeLayout.layout(Molecule.fromSmiles(CUBANE), DepictionOptions.skeletal()).depiction();
            Depiction raw = MoleculeLayout.layout(Molecule.fromSmiles(CUBANE),
                DepictionOptions.skeletal().withRingRefinement(RingRefinement.NONE)).depiction();

            assertThat(LayoutQuality.minimumAtomDistance(pointsOf(refined), bondsOf(refined)))
                .isGreaterThan(LayoutQuality.minimumAtomDistance(pointsOf(raw), bondsOf(raw)));
        }

        @Test
        void keeps_cage_bonds_exactly_one_bond_length_when_projection_is_off()
        {
            Depiction depiction = MoleculeLayout.layout(Molecule.fromSmiles(CUBANE), DepictionOptions.skeletal().withProjectCages(false)).depiction();

            assertThat(LayoutQuality.lengthError(pointsOf(depiction), bondsOf(depiction), 1)).isLessThan(1e-6);
        }

        @Test
        void never_draws_two_cubane_atoms_on_top_of_each_other()
        {
            Depiction depiction = skeletalDepiction(CUBANE);

            assertThat(LayoutQuality.minimumAtomDistance(pointsOf(depiction), bondsOf(depiction))).isGreaterThan(0.4);
        }

        @Test
        void draws_cubane_with_the_crossings_a_cube_has()
        {
            Depiction depiction = skeletalDepiction(CUBANE);

            // a flat cube with equal edges has to show two of its four rear edges crossing front ones
            assertThat(LayoutQuality.crossings(pointsOf(depiction), bondsOf(depiction))).isLessThanOrEqualTo(8);
        }

        @Test
        void draws_the_same_cage_the_same_way_every_time()
        {
            assertThat(pointsOf(skeletalDepiction(CUBANE))).containsExactly(pointsOf(skeletalDepiction(CUBANE)));
        }
    }

    @Nested
    class DisconnectedParts
    {
        @Test
        void centres_each_part_of_a_molecule_on_the_origin_in_a_frame_of_its_own()
        {
            Layout layout = depict("C.C");
            Depiction depiction = layout.depiction();

            assertThat(depiction.fragments()).hasSize(2);
            for (FragmentFrame frame : depiction.fragments())
            {
                assertAt(frame.bounds().centre(), 0, 0);
            }
            assertThat(layout.placementOfAtom(0).fragment()).isEqualTo(0);
            assertThat(layout.placementOfAtom(1).fragment()).isEqualTo(1);
        }

        @Test
        void places_a_lone_atom_on_the_origin_and_describes_each_part_in_a_frame()
        {
            Layout layout = depict("CC.[Na+]");

            assertThat(layout.depiction().fragments()).extracting(FragmentFrame::index).containsExactly(0, 1);
            assertAt(layout.depiction().fragments().getFirst().bounds().centre(), 0, 0);
            assertAt(positionOf(layout, 2), 0, 0);
            assertThat(layout.placementOfAtom(2).fragment()).isEqualTo(1);
        }
    }

    @Nested
    class TetrahedralStereochemistry
    {
        @Test
        void draws_exactly_one_bond_out_of_the_page_at_a_stereocentre()
        {
            assertThat(depict("N[C@@H](C)C(=O)O").depiction().bonds())
                .filteredOn(bond -> bond.style().isStereo())
                .hasSize(1);
        }

        @Test
        void draws_no_stereo_bond_where_no_parity_is_declared()
        {
            assertThat(depict("NC(C)C(=O)O").depiction().bonds()).allMatch(bond -> bond.style() == BondStyle.PLAIN);
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
            Depiction depiction = depict("N[C@@H](C)C(=O)O", HydrogenStyle.NONE).depiction();

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
            Depiction shown = depict("N[C@@H](C)C(=O)O", HydrogenStyle.SEPARATE).depiction();
            Depiction hidden = depict("N[C@@H](C)C(=O)O", HydrogenStyle.NONE).depiction();

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
            return depict(smiles, hydrogens).depiction().bonds().stream()
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
            Layout layout = depict("F/C=C/F");
            Depiction depiction = layout.depiction();

            assertThat(sideOf(layout, 1, 2, 1, 0) * sideOf(layout, 1, 2, 2, 3)).isNegative();
        }

        @Test
        void draws_cis_substituents_on_the_same_side_of_the_bond()
        {
            Layout layout = depict("F/C=C\\F");
            Depiction depiction = layout.depiction();

            assertThat(sideOf(layout, 1, 2, 1, 0) * sideOf(layout, 1, 2, 2, 3)).isPositive();
        }

        @Test
        void draws_a_trans_alkene_with_its_chains_on_opposite_sides()
        {
            Layout layout = depict("C/C=C/C");
            Depiction depiction = layout.depiction();

            assertThat(sideOf(layout, 1, 2, 1, 0) * sideOf(layout, 1, 2, 2, 3)).isNegative();
        }

        @Test
        void draws_a_cis_alkene_with_its_chains_on_the_same_side()
        {
            Layout layout = depict("C/C=C\\C");
            Depiction depiction = layout.depiction();

            assertThat(sideOf(layout, 1, 2, 1, 0) * sideOf(layout, 1, 2, 2, 3)).isPositive();
        }

        @Test
        void keeps_the_substituents_clear_of_the_bond_they_are_measured_against()
        {
            Layout layout = depict("F/C=C/F");
            Depiction depiction = layout.depiction();

            assertThat(Math.abs(sideOf(layout, 1, 2, 1, 0))).isGreaterThan(0.1);
            assertThat(Math.abs(sideOf(layout, 1, 2, 2, 3))).isGreaterThan(0.1);
        }

        @Test
        void leaves_an_undeclared_double_bond_to_fall_where_it_may()
        {
            assertThat(depict("FC=CF").depiction().bonds()).isNotEmpty();
        }
    }

    @Nested
    class Skeletal
    {
        @Test
        void leaves_a_carbon_in_a_chain_as_a_bare_vertex()
        {
            Layout layout = skeletal("CCCC");
            Depiction depiction = layout.depiction();

            assertThat(depiction.atoms()).allMatch(AtomPlacement::vertex);
        }

        @Test
        void still_writes_a_heteroatom_out()
        {
            Layout layout = skeletal("CCO");

            assertThat(atomOf(layout, "O").vertex()).isFalse();
            assertThat(atomOf(layout, "C").vertex()).isTrue();
        }

        @Test
        void gathers_a_heteroatoms_hydrogens_into_its_own_label()
        {
            assertThat(atomOf(skeletal("CCO"), "O").hydrogens()).isEqualTo(1);
        }

        @Test
        void draws_no_hydrogen_of_its_own()
        {
            assertThat(skeletal("CCO").depiction().atoms()).noneMatch(AtomPlacement::isGeneratedHydrogen);
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
            assertThat(skeletal("CC[CH2-]").depiction().atoms()).anySatisfy(atom ->
            {
                assertThat(atom.charge()).isEqualTo(-1);
                assertThat(atom.vertex()).isFalse();
            });
        }

        @Test
        void writes_a_carbon_carrying_a_mass_number()
        {
            assertThat(skeletal("CC[13CH3]").depiction().atoms()).anySatisfy(atom ->
            {
                assertThat(atom.isotope()).hasValue(13);
                assertThat(atom.vertex()).isFalse();
            });
        }

        @Test
        void bends_a_chain_so_that_each_vertex_can_be_told_apart()
        {
            Layout layout = skeletal("CCCC");

            assertThat(sideOf(layout, 0, 1, 1, 2) * sideOf(layout, 1, 2, 2, 3)).isNotZero();
            assertThat(positionOf(layout, 0).y()).isNotEqualTo(positionOf(layout, 1).y());
        }

        @Test
        void keeps_the_bonds_the_molecule_has_and_adds_none()
        {
            Molecule propanol = Molecule.fromSmiles("CCO");

            assertThat(skeletal("CCO").depiction().bonds()).hasSize(propanol.bonds().size());
        }

        @Test
        void still_draws_a_stereocentre_out_of_the_plane()
        {
            assertThat(skeletal("N[C@@H](C)C(=O)O").depiction().bonds())
                .filteredOn(bond -> bond.style().isStereo())
                .hasSize(1);
        }

        /**
         * @param smiles a molecule
         * @return it laid out as a line-angle drawing
         */
        private static Layout skeletal(String smiles)
        {
            return MoleculeLayout.layout(Molecule.fromSmiles(smiles), DepictionOptions.skeletal());
        }

        /**
         * @param layout a layout
         * @param element an element symbol
         * @return the first atom of that element
         */
        private static AtomPlacement atomOf(Layout layout, String element)
        {
            return layout.depiction().atoms().stream()
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
            Depiction depiction = depict("CCO").depiction();
            Bounds bounds = depiction.bounds();

            for (AtomPlacement placement : depiction.atoms())
            {
                assertThat(placement.position().x()).isBetween(bounds.minX(), bounds.maxX());
                assertThat(placement.position().y()).isBetween(bounds.minY(), bounds.maxY());
            }
        }
    }
}
