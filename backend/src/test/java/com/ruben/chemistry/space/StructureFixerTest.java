package com.ruben.chemistry.space;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesWriter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructureFixerTest
{
    private static final String[] STEREO_MOLECULES = {
        "N[C@@H](C)C(=O)O",
        "N[C@H](C)C(=O)O",
        "C/C=C/C",
        "C/C=C\\C",
        "C[C@H](O)[C@@H](O)C",
        "C/C=C/C=C/C",
        "CC(C)[C@@H]1CC[C@@H](C)C[C@H]1O",
    };

    private static String canonical(Molecule molecule)
    {
        return new SmilesWriter(molecule).setRanker(new CanonicalRanker()).write();
    }

    private static String canonical(String smiles)
    {
        return canonical(Molecule.fromSmiles(smiles));
    }

    private static String read(Structure3D structure, int conformer)
    {
        return canonical(StructureReader.read(structure, conformer).molecule());
    }

    private static Structure3D embed(String smiles)
    {
        return Embedder.embed(Molecule.fromSmiles(smiles));
    }

    private static Structure3D noisy(Structure3D structure, double amplitude, Random random)
    {
        List<Structure3D.Conformer> conformers = new ArrayList<>();
        for (Structure3D.Conformer conformer : structure.conformers())
        {
            List<Vec3> positions = new ArrayList<>();
            for (Vec3 position : conformer.positions())
            {
                positions.add(position.plus(new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).times(amplitude)));
            }
            conformers.add(new Structure3D.Conformer(conformer.strain(), positions));
        }
        return new Structure3D(structure.sites(), structure.connections(), conformers);
    }

    private static double strainOf(Structure3D structure, int conformer)
    {
        return StructureFixer.fix(structure, StructureFixer.Constraints.NONE,
            new StructureFixer.Options(0, 0, StructureReader.Options.DEFAULT)).reports().get(conformer).strainBefore();
    }

    @Test
    void a_clean_structure_should_come_back_unchanged()
    {
        Structure3D structure = embed("N[C@@H](C)C(=O)O");

        StructureFixer.Fixed fixed = StructureFixer.fix(structure);

        assertThat(fixed.reports().getFirst().outcome()).isNotEqualTo(StructureFixer.Outcome.FAILED);
        assertThat(read(fixed.structure(), 0)).isEqualTo(canonical("N[C@@H](C)C(=O)O"));
        assertThat(fixed.reports().getFirst().strainAfter()).isLessThanOrEqualTo(fixed.reports().getFirst().strainBefore());
    }

    @Test
    void noisy_structures_should_keep_their_stereochemistry_and_lose_strain()
    {
        Random random = new Random(11);
        for (String smiles : STEREO_MOLECULES)
        {
            Structure3D rough = noisy(embed(smiles), 0.08, random);

            StructureFixer.Fixed fixed = StructureFixer.fix(rough);

            StructureFixer.Report report = fixed.reports().getFirst();
            assertThat(report.outcome()).as(smiles).isNotEqualTo(StructureFixer.Outcome.FAILED);
            assertThat(read(fixed.structure(), 0)).as(smiles).isEqualTo(canonical(smiles));
            assertThat(report.strainAfter()).as(smiles).isLessThanOrEqualTo(report.strainBefore());
        }
    }

    @Test
    void a_noisy_structure_should_be_fixed_to_lower_strain()
    {
        Structure3D rough = noisy(embed("CC(C)[C@@H]1CC[C@@H](C)C[C@H]1O"), 0.12, new Random(5));

        StructureFixer.Fixed fixed = StructureFixer.fix(rough);

        StructureFixer.Report report = fixed.reports().getFirst();
        assertThat(report.outcome()).isEqualTo(StructureFixer.Outcome.FIXED);
        assertThat(report.strainAfter()).isLessThan(report.strainBefore());
        assertThat(fixed.structure().conformers().getFirst().strain()).isEqualTo(report.strainAfter());
    }

    @Test
    void a_stereoisomer_should_not_be_turned_into_its_mirror_image()
    {
        Random random = new Random(3);
        for (String smiles : new String[] {"F[C@](Cl)(Br)I", "F[C@@](Cl)(Br)I"})
        {
            Structure3D fixed = StructureFixer.fix(noisy(embed(smiles), 0.1, random)).structure();

            assertThat(read(fixed, 0)).isEqualTo(canonical(smiles));
        }
    }

    @Test
    void a_set_centre_should_be_imposed()
    {
        Structure3D structure = embed("N[C@@H](C)C(=O)O");

        StructureFixer.Fixed fixed = StructureFixer.fix(structure,
            StructureFixer.Constraints.NONE.withCentre(1, Chirality.CLOCKWISE), StructureFixer.Options.DEFAULT);

        assertThat(fixed.reports().getFirst().outcome()).isEqualTo(StructureFixer.Outcome.FIXED);
        assertThat(read(fixed.structure(), 0)).isEqualTo(canonical("N[C@H](C)C(=O)O"));
    }

    @Test
    void a_set_double_bond_should_be_imposed()
    {
        Structure3D structure = embed("C/C=C/C");

        StructureFixer.Fixed fixed = StructureFixer.fix(structure,
            StructureFixer.Constraints.NONE.withDoubleBond(1, 2, DoubleBondStereo.SAME_SIDE), StructureFixer.Options.DEFAULT);

        assertThat(fixed.reports().getFirst().outcome()).isEqualTo(StructureFixer.Outcome.FIXED);
        assertThat(read(fixed.structure(), 0)).isEqualTo(canonical("C/C=C\\C"));
    }

    @Test
    void a_double_bond_with_nothing_declared_should_keep_the_side_it_is_on()
    {
        for (String smiles : new String[] {"CC=CC", "C/C=C\\C", "C/C=C/C"})
        {
            Structure3D structure = noisy(embed(smiles), 0.05, new Random(2));
            String before = read(structure, 0);

            Structure3D fixed = StructureFixer.fix(structure).structure();

            assertThat(read(fixed, 0)).as(smiles).isEqualTo(before);
        }
    }

    @Test
    void a_freed_centre_should_not_be_held()
    {
        Structure3D structure = embed("N[C@@H](C)C(=O)O");

        StructureFixer.Fixed fixed = StructureFixer.fix(structure, StructureFixer.Constraints.NONE.freeing(1), StructureFixer.Options.DEFAULT);

        assertThat(fixed.reports().getFirst().outcome()).isNotEqualTo(StructureFixer.Outcome.FAILED);
        assertThat(fixed.reports().getFirst().conflicts()).isEmpty();
    }

    @Test
    void a_centre_too_flat_to_read_should_be_reported_and_left_free()
    {
        List<Structure3D.Site> sites = new ArrayList<>();
        String[] elements = {"C", "F", "Cl", "Br", "I"};
        for (int index = 0; index < elements.length; index++)
        {
            sites.add(new Structure3D.Site(index, elements[index], 0, index, false, 0));
        }
        List<Structure3D.Connection> connections = new ArrayList<>();
        for (int index = 1; index < 5; index++)
        {
            connections.add(new Structure3D.Connection(0, index, com.ruben.chemistry.core.BondOrder.SINGLE, index - 1));
        }
        List<Vec3> positions = List.of(new Vec3(0, 0, 0), new Vec3(1.4, 0, 0), new Vec3(0, 1.8, 0), new Vec3(-1.9, 0, 0), new Vec3(0, -2.1, 0.01));
        Structure3D structure = new Structure3D(sites, connections, List.of(new Structure3D.Conformer(0, positions)));

        StructureFixer.Fixed fixed = StructureFixer.fix(structure);

        assertThat(fixed.reports().getFirst().unread()).extracting(StructureReader.Issue::kind).containsExactly(StructureReader.Kind.FLAT_CENTRE);
        assertThat(fixed.reports().getFirst().outcome()).isNotEqualTo(StructureFixer.Outcome.FAILED);
    }

    @Test
    void a_pinned_atom_should_stay_put_with_its_hydrogens()
    {
        Structure3D structure = noisy(embed("CCO"), 0.1, new Random(8));
        List<Vec3> before = structure.conformers().getFirst().positions();

        StructureFixer.Fixed fixed = StructureFixer.fix(structure, StructureFixer.Constraints.NONE.pinning(2), StructureFixer.Options.DEFAULT);

        List<Double> movement = fixed.reports().getFirst().movement();
        for (Structure3D.Site site : structure.sites())
        {
            if (site.atom() == 2)
            {
                assertThat(movement.get(site.index())).as("site " + site.index()).isLessThan(0.02);
            }
        }
        assertThat(movement.stream().mapToDouble(Double::doubleValue).max().orElse(0)).isGreaterThan(0.02);
        assertThat(before).hasSize(structure.sites().size());
    }

    @Test
    void a_pinned_site_should_stay_put()
    {
        Structure3D structure = noisy(embed("CCO"), 0.1, new Random(8));

        StructureFixer.Fixed fixed = StructureFixer.fix(structure, StructureFixer.Constraints.NONE.pinningSite(0), StructureFixer.Options.DEFAULT);

        assertThat(fixed.reports().getFirst().movement().getFirst()).isLessThan(0.02);
    }

    @Test
    void a_soft_pin_should_hold_less_than_a_hard_one()
    {
        Structure3D structure = noisy(embed("CC(C)C(=O)O"), 0.15, new Random(4));

        double hard = StructureFixer.fix(structure, StructureFixer.Constraints.NONE.pinning(0), StructureFixer.Options.DEFAULT)
            .reports().getFirst().movement().getFirst();
        double soft = StructureFixer.fix(structure, StructureFixer.Constraints.NONE.pinning(0, 0.1), StructureFixer.Options.DEFAULT)
            .reports().getFirst().movement().getFirst();

        assertThat(soft).isGreaterThanOrEqualTo(hard);
    }

    @Test
    void constraints_that_do_not_apply_should_be_reported_and_ignored()
    {
        Structure3D structure = embed("N[C@@H](C)C(=O)O");

        StructureFixer.Constraints constraints = StructureFixer.Constraints.NONE
            .withCentre(2, Chirality.CLOCKWISE)
            .withDoubleBond(0, 1, DoubleBondStereo.SAME_SIDE)
            .withCentre(99, Chirality.CLOCKWISE)
            .pinning(99)
            .pinningSite(99)
            .freeing(99);
        StructureFixer.Fixed fixed = StructureFixer.fix(structure, constraints, StructureFixer.Options.DEFAULT);

        assertThat(fixed.reports().getFirst().conflicts()).hasSize(6);
        assertThat(read(fixed.structure(), 0)).isEqualTo(canonical("N[C@@H](C)C(=O)O"));
    }

    @Test
    void a_centre_with_alike_ligands_should_not_be_settable()
    {
        Structure3D structure = embed("CC(C)(C)C");

        StructureFixer.Fixed fixed = StructureFixer.fix(structure, StructureFixer.Constraints.NONE.withCentre(1, Chirality.CLOCKWISE), StructureFixer.Options.DEFAULT);

        assertThat(fixed.reports().getFirst().conflicts()).hasSize(1);
    }

    @Test
    void every_conformer_should_be_fixed_and_reported()
    {
        Structure3D structure = Embedder.embed(Molecule.fromSmiles("CC(C)[C@@H]1CC[C@@H](C)C[C@H]1O"), Embedder.Options.DEFAULT.withConformers(4).withSeed(1));
        assertThat(structure.conformers().size()).isGreaterThan(1);

        StructureFixer.Fixed fixed = StructureFixer.fix(noisy(structure, 0.05, new Random(6)));

        assertThat(fixed.structure().conformers()).hasSameSizeAs(structure.conformers());
        assertThat(fixed.reports()).hasSameSizeAs(structure.conformers());
        for (int conformer = 0; conformer < structure.conformers().size(); conformer++)
        {
            assertThat(read(fixed.structure(), conformer)).isEqualTo(canonical("CC(C)[C@@H]1CC[C@@H](C)C[C@H]1O"));
            assertThat(fixed.reports().get(conformer).disagrees()).isFalse();
        }
    }

    @Test
    void a_conformer_of_a_different_stereoisomer_should_be_reported_and_pulled_to_the_first()
    {
        Structure3D left = embed("N[C@@H](C)C(=O)O");
        Structure3D right = embed("N[C@H](C)C(=O)O");
        Structure3D mixed = new Structure3D(left.sites(), left.connections(),
            List.of(left.conformers().getFirst(), right.conformers().getFirst()));

        StructureFixer.Fixed fixed = StructureFixer.fix(mixed);

        assertThat(fixed.reports().get(0).disagrees()).isFalse();
        assertThat(fixed.reports().get(1).disagrees()).isTrue();
        assertThat(read(fixed.structure(), 0)).isEqualTo(canonical("N[C@@H](C)C(=O)O"));
        assertThat(read(fixed.structure(), 1)).as(fixed.reports().get(1).toString()).isEqualTo(canonical("N[C@@H](C)C(=O)O"));
    }

    @Test
    void structure_that_is_left_alone_should_be_as_it_came()
    {
        Structure3D structure = embed("CCO");

        StructureFixer.Fixed fixed = StructureFixer.fix(structure);

        if (fixed.reports().getFirst().outcome() != StructureFixer.Outcome.FIXED)
        {
            assertThat(fixed.structure().conformers().getFirst().positions()).isEqualTo(structure.conformers().getFirst().positions());
        }
        assertThat(fixed.structure().sites()).isEqualTo(structure.sites());
        assertThat(fixed.structure().connections()).isEqualTo(structure.connections());
    }

    @Test
    void movement_should_be_reported_for_every_site()
    {
        Structure3D structure = noisy(embed("CCO"), 0.1, new Random(1));

        StructureFixer.Report report = StructureFixer.fix(structure).reports().getFirst();

        assertThat(report.movement()).hasSize(structure.sites().size());
        assertThat(report.movement()).allMatch(distance -> distance >= 0);
    }

    @Test
    void fixing_a_fixed_structure_should_change_nothing_more()
    {
        Structure3D rough = noisy(embed("CC(C)[C@@H]1CC[C@@H](C)C[C@H]1O"), 0.1, new Random(9));

        Structure3D once = StructureFixer.fix(rough).structure();
        StructureFixer.Report again = StructureFixer.fix(once).reports().getFirst();

        assertThat(again.outcome()).isNotEqualTo(StructureFixer.Outcome.FAILED);
        assertThat(again.strainBefore() - again.strainAfter()).isLessThan(0.05);
    }

    @Test
    void a_structure_with_no_conformers_should_be_refused()
    {
        Structure3D structure = embed("CCO");
        Structure3D empty = new Structure3D(structure.sites(), structure.connections(), List.of());

        assertThatThrownBy(() -> StructureFixer.fix(empty)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void strain_should_be_measurable_without_relaxing()
    {
        Structure3D structure = embed("CCO");

        assertThat(strainOf(structure, 0)).isGreaterThanOrEqualTo(0);
    }
}
