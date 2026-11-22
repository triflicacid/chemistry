package com.ruben.chemistry.forcefield;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HydrogenBondScanTest
{
    private static final double HARTREE_TO_KCAL = 627.5095;

    private static ForceFieldMolecule water(ForceField field, String name, double shift)
    {
        return ElectronDensityForceField.buildMolecule(field, name, List.of(
                new ForceFieldAtom("O", 0.0, 0.0, shift, -0.741330),
                new ForceFieldAtom("H", 0.0, 0.0, 1.8 + shift, 0.370665),
                new ForceFieldAtom("H", 1.74, 0.0, -0.55 + shift, 0.370665)));
    }

    private static double distance(ForceFieldAtom a, ForceFieldAtom b)
    {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    @Test
    void should_find_two_donors_and_one_acceptor_in_water()
    {
        ForceFieldMolecule molecule = water(ElectronDensityForceField.forceField(ModelType.ECP), "w", 0.0);

        assertEquals(List.of(new HydrogenBondScan.Donor(0, 1), new HydrogenBondScan.Donor(0, 2)), HydrogenBondScan.donors(molecule));
        assertEquals(List.of(0), HydrogenBondScan.acceptors(molecule));
    }

    @Test
    void should_not_treat_a_hydrogen_atom_or_ion_pair_as_a_donor()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.FULL);
        ForceFieldMolecule chloride = ElectronDensityForceField.buildMolecule(field, "Cl", List.of(new ForceFieldAtom("Cl", 0, 0, 0, -1.0)));

        assertTrue(HydrogenBondScan.donors(chloride).isEmpty());
        assertTrue(HydrogenBondScan.acceptors(chloride).isEmpty());
    }

    @Test
    void should_place_the_acceptor_at_the_requested_distance_on_the_bond_axis()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        ForceFieldMolecule donorMolecule = water(field, "a", 0.0);
        ForceFieldMolecule acceptorMolecule = water(field, "b", 7.0);

        ForceFieldMolecule moved = HydrogenBondScan.placeAcceptor(donorMolecule, new HydrogenBondScan.Donor(0, 1), acceptorMolecule, 0, field, 3.5);

        assertEquals(3.5, distance(donorMolecule.atoms().get(1), moved.atoms().get(0)), 1.0e-9);
        // the O-H bond of the donor lies along z, so the acceptor oxygen sits straight above the hydrogen
        assertEquals(0.0, moved.atoms().get(0).x(), 1.0e-9);
        assertEquals(0.0, moved.atoms().get(0).y(), 1.0e-9);
        assertEquals(5.3, moved.atoms().get(0).z(), 1.0e-9);
    }

    @Test
    void should_keep_the_acceptor_rigid_when_placing_it()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        ForceFieldMolecule acceptorMolecule = water(field, "b", 7.0);

        ForceFieldMolecule moved = HydrogenBondScan.placeAcceptor(water(field, "a", 0.0), new HydrogenBondScan.Donor(0, 2), acceptorMolecule, 0, field, 3.0);

        for (int i = 1; i < 3; i++)
        {
            assertEquals(distance(acceptorMolecule.atoms().get(0), acceptorMolecule.atoms().get(i)), distance(moved.atoms().get(0), moved.atoms().get(i)), 1.0e-9);
        }
        assertEquals(distance(acceptorMolecule.atoms().get(1), acceptorMolecule.atoms().get(2)), distance(moved.atoms().get(1), moved.atoms().get(2)), 1.0e-9);
    }

    @Test
    void should_point_the_acceptor_lone_pair_at_the_hydrogen()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        ForceFieldMolecule donorMolecule = water(field, "a", 0.0);

        ForceFieldMolecule moved = HydrogenBondScan.placeAcceptor(donorMolecule, new HydrogenBondScan.Donor(0, 1), water(field, "b", 7.0), 0, field, 3.5);

        // both hydrogens of the acceptor are on the far side of its oxygen from the donor hydrogen
        double oxygenToDonor = distance(moved.atoms().get(0), donorMolecule.atoms().get(1));
        assertTrue(distance(moved.atoms().get(1), donorMolecule.atoms().get(1)) > oxygenToDonor);
        assertTrue(distance(moved.atoms().get(2), donorMolecule.atoms().get(1)) > oxygenToDonor);
    }

    @Test
    void should_handle_an_acceptor_that_already_points_the_wrong_way()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        // the lone pair of this water points along +z, exactly against the -z direction it must be turned onto
        ForceFieldMolecule flipped = ElectronDensityForceField.buildMolecule(field, "b", List.of(
                new ForceFieldAtom("O", 0.0, 0.0, 0.0, -0.741330),
                new ForceFieldAtom("H", 1.4, 0.0, -1.1, 0.370665),
                new ForceFieldAtom("H", -1.4, 0.0, -1.1, 0.370665)));

        ForceFieldMolecule moved = HydrogenBondScan.placeAcceptor(water(field, "a", 0.0), new HydrogenBondScan.Donor(0, 1), flipped, 0, field, 3.5);

        assertEquals(3.5, distance(moved.atoms().get(0), water(field, "a", 0.0).atoms().get(1)), 1.0e-9);
        assertEquals(distance(flipped.atoms().get(0), flipped.atoms().get(1)), distance(moved.atoms().get(0), moved.atoms().get(1)), 1.0e-9);
    }

    @Test
    void should_be_repulsive_at_short_range_and_attractive_further_out_for_water_dimer()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);

        HydrogenBondScan.Result result = HydrogenBondScan.scan(water(field, "a", 0.0), new HydrogenBondScan.Donor(0, 1), water(field, "b", 7.0), 0, field, 2.0, 9.0, 15);

        assertTrue(result.points().get(0).energy().total() > 0.0);
        assertTrue(result.minimumEnergy() < 0.0, "minimum was " + result.minimumEnergy() * HARTREE_TO_KCAL + " kcal/mol");
        assertTrue(result.minimumDistance() > 2.0 && result.minimumDistance() < 9.0);
    }

    @Test
    void should_return_the_requested_number_of_points_in_increasing_order()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);

        HydrogenBondScan.Result result = HydrogenBondScan.scan(water(field, "a", 0.0), new HydrogenBondScan.Donor(0, 1), water(field, "b", 7.0), 0, field, 2.5, 8.5, 7);

        assertEquals(7, result.points().size());
        assertEquals(2.5, result.points().get(0).distance(), 1.0e-12);
        assertEquals(8.5, result.points().get(6).distance(), 1.0e-12);
    }

    private static double angle(ForceFieldAtom first, ForceFieldAtom centre, ForceFieldAtom third)
    {
        double[] u = {first.x() - centre.x(), first.y() - centre.y(), first.z() - centre.z()};
        double[] v = {third.x() - centre.x(), third.y() - centre.y(), third.z() - centre.z()};
        double dot = u[0] * v[0] + u[1] * v[1] + u[2] * v[2];
        return Math.toDegrees(Math.acos(dot / (distance(first, centre) * distance(third, centre))));
    }

    @Test
    void should_place_the_acceptor_at_the_requested_angle_and_distance()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        ForceFieldMolecule donorMolecule = water(field, "a", 0.0);

        ForceFieldMolecule moved = HydrogenBondScan.placeAcceptorAtAngle(donorMolecule, new HydrogenBondScan.Donor(0, 1), water(field, "b", 7.0), 0, field, 3.5, 120.0);

        assertEquals(3.5, distance(donorMolecule.atoms().get(1), moved.atoms().get(0)), 1.0e-9);
        assertEquals(120.0, angle(donorMolecule.atoms().get(0), donorMolecule.atoms().get(1), moved.atoms().get(0)), 1.0e-6);
    }

    @Test
    void should_keep_the_angled_acceptor_in_the_plane_of_the_donor_molecule()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        ForceFieldMolecule donorMolecule = water(field, "a", 0.0);

        ForceFieldMolecule moved = HydrogenBondScan.placeAcceptorAtAngle(donorMolecule, new HydrogenBondScan.Donor(0, 1), water(field, "b", 7.0), 0, field, 3.5, 135.0);

        // the donor water lies in the xz plane
        assertEquals(0.0, moved.atoms().get(0).y(), 1.0e-9);
    }

    @Test
    void should_agree_with_the_linear_placement_at_180_degrees()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        ForceFieldMolecule donorMolecule = water(field, "a", 0.0);
        HydrogenBondScan.Donor donor = new HydrogenBondScan.Donor(0, 1);

        ForceFieldMolecule linear = HydrogenBondScan.placeAcceptor(donorMolecule, donor, water(field, "b", 7.0), 0, field, 3.5);
        ForceFieldMolecule angled = HydrogenBondScan.placeAcceptorAtAngle(donorMolecule, donor, water(field, "b", 7.0), 0, field, 3.5, 180.0);

        for (int i = 0; i < 3; i++)
        {
            assertEquals(0.0, distance(linear.atoms().get(i), angled.atoms().get(i)), 1.0e-9);
        }
    }

    @Test
    void should_prefer_a_near_linear_hydrogen_bond_at_the_equilibrium_distance_for_water_dimer()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);

        HydrogenBondScan.AngularResult result = HydrogenBondScan.angularScan(water(field, "a", 0.0), new HydrogenBondScan.Donor(0, 1), water(field, "b", 7.0), 0, field, 3.5, 90.0, 180.0, 10);

        assertEquals(10, result.points().size());
        assertEquals(90.0, result.points().get(0).angleDegrees(), 1.0e-12);
        assertTrue(result.lowest().angleDegrees() > 150.0, "lowest angle was " + result.lowest().angleDegrees());
    }

    @Test
    void should_place_the_acceptor_at_the_requested_heavy_atom_distance_and_angle()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        ForceFieldMolecule donorMolecule = water(field, "a", 0.0);

        for (double degrees : new double[] {90.0, 120.0, 150.0, 180.0})
        {
            ForceFieldMolecule moved = HydrogenBondScan.placeAcceptorAtHeavyDistance(donorMolecule, new HydrogenBondScan.Donor(0, 1), water(field, "b", 7.0), 0, field, 5.5, degrees);

            assertEquals(5.5, distance(donorMolecule.atoms().get(0), moved.atoms().get(0)), 1.0e-9, "X...A at " + degrees);
            assertEquals(degrees, angle(donorMolecule.atoms().get(0), donorMolecule.atoms().get(1), moved.atoms().get(0)), 1.0e-6, "angle at " + degrees);
        }
    }

    @Test
    void should_reject_a_heavy_atom_distance_shorter_than_the_bond_allows()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);

        // at 90 degrees the acceptor is at least the O-H length (1.8 bohr) from the donor oxygen
        assertThrows(IllegalArgumentException.class, () -> HydrogenBondScan.placeAcceptorAtHeavyDistance(water(field, "a", 0.0), new HydrogenBondScan.Donor(0, 1), water(field, "b", 7.0), 0, field, 1.5, 90.0));
    }

    @Test
    void should_prefer_a_near_linear_hydrogen_bond_at_fixed_oxygen_separation_for_water_dimer()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);

        HydrogenBondScan.AngularResult result = HydrogenBondScan.angularScanAtHeavyDistance(water(field, "a", 0.0), new HydrogenBondScan.Donor(0, 1), water(field, "b", 7.0), 0, field, 5.5, 90.0, 180.0, 10);

        assertEquals(10, result.points().size());
        assertTrue(result.lowest().angleDegrees() > 150.0, "lowest angle was " + result.lowest().angleDegrees());
    }

    @Test
    void should_reject_an_angular_scan_with_too_few_points_or_a_bad_range()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        ForceFieldMolecule a = water(field, "a", 0.0);
        ForceFieldMolecule b = water(field, "b", 7.0);
        HydrogenBondScan.Donor donor = new HydrogenBondScan.Donor(0, 1);

        assertThrows(IllegalArgumentException.class, () -> HydrogenBondScan.angularScan(a, donor, b, 0, field, 5.0, 90.0, 180.0, 1));
        assertThrows(IllegalArgumentException.class, () -> HydrogenBondScan.angularScan(a, donor, b, 0, field, 5.0, 180.0, 90.0, 5));
    }

    @Test
    void should_reject_a_scan_with_too_few_points_or_a_bad_range()
    {
        ForceField field = ElectronDensityForceField.forceField(ModelType.ECP);
        ForceFieldMolecule a = water(field, "a", 0.0);
        ForceFieldMolecule b = water(field, "b", 7.0);
        HydrogenBondScan.Donor donor = new HydrogenBondScan.Donor(0, 1);

        assertThrows(IllegalArgumentException.class, () -> HydrogenBondScan.scan(a, donor, b, 0, field, 2.0, 9.0, 2));
        assertThrows(IllegalArgumentException.class, () -> HydrogenBondScan.scan(a, donor, b, 0, field, 9.0, 2.0, 5));
        assertThrows(IllegalArgumentException.class, () -> HydrogenBondScan.scan(a, donor, b, 0, field, 0.0, 2.0, 5));
    }
}
