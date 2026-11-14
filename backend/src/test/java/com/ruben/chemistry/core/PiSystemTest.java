package com.ruben.chemistry.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

class PiSystemTest
{
    /** A system of one ring, where the ring and the circuit its electrons run around are the same thing. */
    @Nested
    class LoneRings
    {
        @Test
        void counts_a_ring_over_its_own_atoms()
        {
            assertSystem("c1ccccc1", 6, 1, 6, PiSystem.Aromaticity.AROMATIC);
            assertSystem("C1=CC=CC=C1", 6, 1, 6, PiSystem.Aromaticity.AROMATIC);
            assertThat(electronsOf("c1cccccc1")).containsExactly(7);
        }

        @Test
        void finds_a_ring_whose_electrons_come_to_four_n_antiaromatic()
        {
            assertSystem("c1ccc1", 4, 1, 4, PiSystem.Aromaticity.ANTIAROMATIC);
            assertSystem("c1ccccccc1", 8, 1, 8, PiSystem.Aromaticity.ANTIAROMATIC);
            assertThat(verdictsOf("C1=CC=CC=CC=C1")).containsExactly(PiSystem.Aromaticity.ANTIAROMATIC);
        }

        @Test
        void counts_a_charge_as_an_electron_gone_or_arrived()
        {
            // cyclopentadienyl's carbanion lends its pair, leaving four carbons to pair off
            assertSystem("[cH-]1cccc1", 5, 1, 6, PiSystem.Aromaticity.AROMATIC);
            assertThat(systemOf("[cH-]1cccc1").kekule()).get().asInstanceOf(LIST).hasSize(2);

            // tropylium's cation has nothing left to lend, which leaves six
            assertSystem("[cH+]1cccccc1", 7, 1, 6, PiSystem.Aromaticity.AROMATIC);
            assertThat(systemOf("[cH+]1cccccc1").kekule()).get().asInstanceOf(LIST).hasSize(3);
        }

        @Test
        void counts_a_heterocycle_by_what_its_heteroatom_lends()
        {
            assertThat(electronsOf("c1cc[nH]c1")).containsExactly(6);
            assertThat(electronsOf("c1ccncc1")).containsExactly(6);
            assertThat(electronsOf("c1ccoc1")).containsExactly(6);
            assertThat(electronsOf("c1ccsc1")).containsExactly(6);
            // a bare nitrogen has no pair to lend, so the count is odd and nothing pairs off
            assertThat(electronsOf("c1ccnc1")).containsExactly(5);
        }

        @Test
        void keeps_an_exocyclic_carbonyl_out_of_the_count()
        {
            // 2H-pyran-2-one: the carbonyl carbon is trigonal but lends nothing to the ring
            assertSystem("O=C1C=CC=CO1", 6, 1, 6, PiSystem.Aromaticity.AROMATIC);
        }
    }

    /**
     * A system of rings fused into one plane, where the circuit the electrons run around is the
     * system's rim rather than any one ring.
     */
    @Nested
    class FusedSystems
    {
        @Test
        void counts_the_system_once_rather_than_ring_by_ring()
        {
            // naphthalene's ten carbons hold ten electrons, not the six apiece a per-ring count gives
            assertSystem("c1ccc2ccccc2c1", 10, 2, 10, PiSystem.Aromaticity.AROMATIC);
            assertSystem("C1=CC2=CC=CC=C2C=C1", 10, 2, 10, PiSystem.Aromaticity.AROMATIC);
            assertSystem("c1ccc2cc3ccccc3cc2c1", 14, 3, 14, PiSystem.Aromaticity.AROMATIC);
        }

        @Test
        void finds_azulene_aromatic_though_neither_of_its_rings_reaches_four_n_plus_two()
        {
            // its rings hold five and seven, and its system the ten that make it aromatic
            assertSystem("c1cc2cccccc2c1", 10, 2, 10, PiSystem.Aromaticity.AROMATIC);
            assertThat(verdictsOf("C1=CC2=CC=CC=CC2=C1")).containsExactly(PiSystem.Aromaticity.AROMATIC);
        }

        @Test
        void finds_a_system_whose_rim_comes_to_four_n_antiaromatic()
        {
            assertSystem("c1cc2cccc2c1", 8, 2, 8, PiSystem.Aromaticity.ANTIAROMATIC);
            assertSystem("c1ccc2cccccc2cc1", 12, 2, 12, PiSystem.Aromaticity.ANTIAROMATIC);
            // biphenylene's rim runs through all twelve of its atoms, by way of the four-ring
            assertSystem("c1ccc2c(c1)-c1ccccc1-2", 12, 3, 12, PiSystem.Aromaticity.ANTIAROMATIC);

            // every atom lies on the rim, so the rim's count and the whole system's are the same
            assertThat(systemOf("c1cc2cccc2c1").rimElectrons()).hasValue(8);
            assertThat(systemOf("c1ccc2cccccc2cc1").rimElectrons()).hasValue(12);
        }

        @Test
        void counts_four_n_around_the_rim_rather_than_the_whole_system()
        {
            // pyrene and coronene hold 4n electrons in all, two and six of their atoms lying inside
            // the rim. The rim is the circuit the rule counts, and it holds 4n+2: fourteen and eighteen
            assertSystem("c1cc2ccc3cccc4ccc(c1)c2c34", 16, 4, 16, PiSystem.Aromaticity.AROMATIC);
            assertThat(systemOf("c1cc2ccc3cccc4ccc(c1)c2c34").rimElectrons()).hasValue(14);

            String coronene = "c1cc2ccc3ccc4ccc5ccc6ccc1c1c2c3c4c5c61";
            assertSystem(coronene, 24, 7, 24, PiSystem.Aromaticity.AROMATIC);
            assertThat(systemOf(coronene).rimElectrons()).hasValue(18);
        }

        @Test
        void leaves_a_rim_that_is_no_single_circuit_to_its_assignment()
        {
            // acenaphthylene's rim runs through eleven of its twelve atoms, the twelfth lying inside,
            // and eleven is neither 4n nor 4n+2; a count that decides nothing takes nothing away
            assertSystem("C1=Cc2cccc3cccc1c23", 12, 3, 12, PiSystem.Aromaticity.AROMATIC);
            assertSystem("c1ccc2c(c1)-c1cccc3cccc2c13", 16, 4, 16, PiSystem.Aromaticity.AROMATIC);
        }

        @Test
        void counts_a_fused_heterocycle_by_what_its_heteroatom_lends()
        {
            assertSystem("c1ccc2[nH]ccc2c1", 9, 2, 10, PiSystem.Aromaticity.AROMATIC);
            assertSystem("c1ccc2ncccc2c1", 10, 2, 10, PiSystem.Aromaticity.AROMATIC);
        }

        @Test
        void takes_a_saturated_ring_out_and_leaves_the_rest()
        {
            // indane and tetralin: the methylenes break the conjugation, so benzene's six atoms are
            // the system and the ring they belong to is in none
            assertSystem("C1Cc2ccccc2C1", 6, 1, 6, PiSystem.Aromaticity.AROMATIC);
            assertSystem("C1CCc2ccccc2C1", 6, 1, 6, PiSystem.Aromaticity.AROMATIC);
        }
    }

    /** Pairing off the atoms that need a π bond, which is what decides whether a system can be read at all. */
    @Nested
    class Assignments
    {
        @Test
        void assigns_the_bonds_of_a_system_that_has_an_assignment()
        {
            assertThat(systemOf("c1ccccc1").kekule()).get().asInstanceOf(LIST).hasSize(3);
            assertThat(systemOf("c1ccc2ccccc2c1").kekule()).get().asInstanceOf(LIST).hasSize(5);
            // pyrrole's nitrogen lends its pair, so only its four carbons need pairing off
            assertThat(systemOf("c1cc[nH]c1").kekule()).get().asInstanceOf(LIST).hasSize(2);
            assertThat(systemOf("c1ccc1").kekule()).get().asInstanceOf(LIST).hasSize(2);
        }

        @Test
        void finds_no_assignment_where_the_atoms_needing_a_pi_bond_cannot_pair()
        {
            assertThat(systemOf("c1cccccc1").isKekulisable()).isFalse();
            assertThat(systemOf("c1ccnc1").isKekulisable()).isFalse();
            assertThat(systemOf("c1cc2cccc3cccc(c1)c23").isKekulisable()).isFalse();
        }

        @Test
        void counts_phenalenyl_odd_and_its_anion_aromatic()
        {
            // thirteen sp2 carbons hold thirteen electrons, which no assignment can pair
            assertSystem("c1cc2cccc3cccc(c1)c23", 13, 3, 13, PiSystem.Aromaticity.NONAROMATIC);

            // the carbanion lends its pair instead, which makes fourteen and leaves twelve to pair off
            assertSystem("c1[cH-]c2cccc3cccc(c1)c23", 13, 3, 14, PiSystem.Aromaticity.AROMATIC);
        }

        @Test
        void follows_where_a_charge_is_written_in_an_alternant_system()
        {
            // phenalenyl's thirteen atoms fall into two classes of seven and six, and pairing off the
            // other twelve needs six of each, so the anion has an assignment only where the charge is
            // written on one of the seven. The electron count is fourteen either way: delocalised
            // charge is the one thing this notation cannot say
            assertThat(systemOf("c1[cH-]c2cccc3cccc(c1)c23").isKekulisable()).isTrue();
            assertThat(systemOf("[cH-]1cc2cccc3cccc(c1)c23").isKekulisable()).isFalse();
            assertThat(electronsOf("[cH-]1cc2cccc3cccc(c1)c23")).containsExactly(14);
        }
    }

    /** Where one system ends and another begins, and where there is no system at all. */
    @Nested
    class Boundaries
    {
        @Test
        void keeps_rings_joined_by_a_single_bond_apart()
        {
            assertThat(systemsOf("c1ccccc1-c1ccccc1")).hasSize(2);
            assertThat(electronsOf("c1ccccc1-c1ccccc1")).containsExactly(6, 6);
        }

        @Test
        void finds_no_system_through_a_spiro_atom()
        {
            // a spiro atom holds four sigma bonds, so no pi system runs around either of its rings,
            // and rings sharing it alone are never joined into one
            assertThat(systemsOf("C1=CC2(C=C1)C=CC=C2")).isEmpty();
        }

        @Test
        void finds_no_system_where_a_ring_atom_cannot_join_one()
        {
            assertThat(systemsOf("C1CCCCC1")).isEmpty();
            assertThat(systemsOf("C1CC#CCCCC1")).isEmpty();
            assertThat(systemsOf("CCO")).isEmpty();
            // fulvene's exocyclic alkene is cross-conjugated, so its ring joins no system
            assertThat(systemsOf("C1=CC=CC1=C")).isEmpty();
        }

        @Test
        void holds_the_atoms_and_bonds_of_the_system()
        {
            Molecule molecule = Molecule.fromSmiles("c1ccc2ccccc2c1");
            PiSystem system = molecule.piSystems().getFirst();

            assertThat(system.atoms()).hasSize(10).containsExactlyElementsOf(molecule.atoms());
            assertThat(system.bonds()).hasSize(11);
            assertThat(system.contains(molecule.atoms().getFirst())).isTrue();
            assertThat(molecule.piSystemOf(molecule.atoms().getFirst())).contains(system);
        }
    }

    /**
     * Checks a molecule's only π system.
     *
     * @param smiles the molecule as SMILES
     * @param atoms how many atoms the system should hold
     * @param rings how many rings should have fused into it
     * @param piElectrons the electrons it should count over all of them
     * @param aromaticity what it should amount to
     */
    private static void assertSystem(String smiles, int atoms, int rings, int piElectrons,
        PiSystem.Aromaticity aromaticity)
    {
        PiSystem system = systemOf(smiles);

        assertThat(system.atoms()).as("%s atoms", smiles).hasSize(atoms);
        assertThat(system.rings()).as("%s rings", smiles).isEqualTo(rings);
        assertThat(system.piElectrons()).as("%s pi electrons", smiles).isEqualTo(piElectrons);
        assertThat(system.aromaticity()).as("%s aromaticity", smiles).isEqualTo(aromaticity);
    }

    /**
     * @param smiles a molecule as SMILES
     * @return its only π system
     */
    private static PiSystem systemOf(String smiles)
    {
        List<PiSystem> systems = systemsOf(smiles);
        assertThat(systems).as("%s has one system", smiles).hasSize(1);
        return systems.getFirst();
    }

    private static List<PiSystem> systemsOf(String smiles)
    {
        return Molecule.fromSmiles(smiles).piSystems();
    }

    private static List<Integer> electronsOf(String smiles)
    {
        return systemsOf(smiles).stream().map(PiSystem::piElectrons).toList();
    }

    private static List<PiSystem.Aromaticity> verdictsOf(String smiles)
    {
        return systemsOf(smiles).stream().map(PiSystem::aromaticity).toList();
    }
}
