package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EmbedderTest
{
    private static Structure3D embed(String smiles)
    {
        return Embedder.embed(Molecule.fromSmiles(smiles));
    }

    private static Vec3 at(Structure3D structure, int node)
    {
        return structure.conformers().get(0).positions().get(node);
    }

    private static double distance(Structure3D structure, int first, int second)
    {
        return at(structure, first).distanceTo(at(structure, second));
    }

    private static double angle(Structure3D structure, int first, int middle, int last)
    {
        return Vec3.angle(at(structure, first), at(structure, middle), at(structure, last));
    }

    private static double torsion(Structure3D structure, int a, int b, int c, int d)
    {
        return Vec3.torsion(at(structure, a), at(structure, b), at(structure, c), at(structure, d));
    }

    /** The nodes bonded to a node, in index order. */
    private static List<Integer> neighbours(Structure3D structure, int node)
    {
        return structure.connections().stream()
            .filter(connection -> connection.first() == node || connection.second() == node)
            .map(connection -> connection.first() == node ? connection.second() : connection.first())
            .sorted()
            .toList();
    }

    @Test
    void should_give_methane_tetrahedral_implied_hydrogens()
    {
        Structure3D methane = embed("C");

        assertThat(methane.sites()).hasSize(5);
        assertThat(methane.sites().stream().filter(Structure3D.Site::implied)).hasSize(4);
        for (int hydrogen = 1; hydrogen <= 4; hydrogen++)
        {
            assertThat(distance(methane, 0, hydrogen)).isCloseTo(1.07, within(0.05));
            for (int other = hydrogen + 1; other <= 4; other++)
            {
                assertThat(angle(methane, hydrogen, 0, other)).isCloseTo(109.47, within(6.0));
            }
        }
    }

    @Test
    void should_make_ethene_flat_with_trigonal_angles()
    {
        Structure3D ethene = embed("C=C");

        assertThat(distance(ethene, 0, 1)).isCloseTo(1.33, within(0.05));
        assertThat(angle(ethene, 1, 0, 2)).isCloseTo(120, within(6.0));
        double turn = Math.abs(torsion(ethene, 2, 0, 1, 4));
        assertThat(Math.min(turn, 180 - turn)).isLessThan(8);
    }

    @Test
    void should_make_ethyne_linear()
    {
        Structure3D ethyne = embed("C#C");

        assertThat(angle(ethyne, 2, 0, 1)).isCloseTo(180, within(4.0));
        assertThat(distance(ethyne, 0, 1)).isCloseTo(1.20, within(0.05));
    }

    @Test
    void should_make_benzene_a_flat_hexagon()
    {
        Structure3D benzene = embed("c1ccccc1");

        for (int atom = 0; atom < 6; atom++)
        {
            int next = (atom + 1) % 6;
            int after = (atom + 2) % 6;
            int third = (atom + 3) % 6;
            assertThat(distance(benzene, atom, next)).isCloseTo(1.39, within(0.06));
            assertThat(angle(benzene, atom, next, after)).isCloseTo(120, within(6.0));
            assertThat(Math.abs(torsion(benzene, atom, next, after, third))).isLessThan(8);
        }
    }

    @Test
    void should_keep_cyclohexane_angles_near_tetrahedral()
    {
        Structure3D ring = embed("C1CCCCC1");

        for (int atom = 0; atom < 6; atom++)
        {
            assertThat(distance(ring, atom, (atom + 1) % 6)).isCloseTo(1.52, within(0.05));
            assertThat(angle(ring, atom, (atom + 1) % 6, (atom + 2) % 6)).isCloseTo(111, within(7.0));
        }
    }

    @Test
    void should_embed_trans_and_cis_alkenes_as_declared()
    {
        assertThat(Math.abs(torsion(embed("F/C=C/F"), 0, 1, 2, 3))).isGreaterThan(150);
        assertThat(Math.abs(torsion(embed("F/C=C\\F"), 0, 1, 2, 3))).isLessThan(30);
    }

    @Test
    void should_make_ring_double_bonds_cis()
    {
        Structure3D cyclohexene = embed("C1=CCCCC1");

        assertThat(Math.abs(torsion(cyclohexene, 5, 0, 1, 2))).isLessThan(30);
    }

    @Test
    void should_embed_enantiomers_as_mirror_images()
    {
        // L-alanine is S: with the hydrogen pointing away, nitrogen, carboxyl, methyl run anticlockwise
        Structure3D levo = embed("N[C@@H](C)C(=O)O");
        Structure3D dextro = embed("N[C@H](C)C(=O)O");

        double levoHandedness = Vec3.handedness(at(levo, 1), at(levo, 0), at(levo, 3), at(levo, 2));
        double dextroHandedness = Vec3.handedness(at(dextro, 1), at(dextro, 0), at(dextro, 3), at(dextro, 2));
        assertThat(levoHandedness).isGreaterThan(0.4);
        assertThat(dextroHandedness).isLessThan(-0.4);
    }

    @Test
    void should_honour_every_stereocentre_of_a_multi_centre_molecule()
    {
        Molecule glucose = Molecule.fromSmiles("OC[C@H]1OC(O)[C@H](O)[C@@H](O)[C@@H]1O");
        Structure3D structure = Embedder.embed(glucose);
        Topology topology = new Topology(glucose);

        assertThat(topology.centres).hasSize(4);
        for (Topology.Centre centre : topology.centres)
        {
            int[] ligands = centre.ligands();
            double handedness = Vec3.handedness(at(structure, centre.centre()),
                at(structure, ligands[1]), at(structure, ligands[2]), at(structure, ligands[3]));
            assertThat(centre.sign() * handedness).isGreaterThan(0.2);
        }
    }

    @Test
    void should_give_the_same_coordinates_for_the_same_molecule()
    {
        assertThat(embed("CC(C)C[C@H](N)C(=O)O").conformers()).isEqualTo(embed("CC(C)C[C@H](N)C(=O)O").conformers());
    }

    @Test
    void should_rank_conformers_by_strain()
    {
        Structure3D hexane = Embedder.embed(Molecule.fromSmiles("CCCCCC"), Embedder.Options.DEFAULT.withConformers(8));

        assertThat(hexane.conformers()).isNotEmpty();
        for (int at = 1; at < hexane.conformers().size(); at++)
        {
            assertThat(hexane.conformers().get(at).strain()).isGreaterThanOrEqualTo(hexane.conformers().get(at - 1).strain());
        }
    }

    @Test
    void should_make_naphthalene_flat()
    {
        Structure3D naphthalene = embed("c1ccc2ccccc2c1");

        for (int atom = 0; atom < 10; atom++)
        {
            for (int other : neighbours(naphthalene, atom))
            {
                for (int far : neighbours(naphthalene, other))
                {
                    for (int last : neighbours(naphthalene, far))
                    {
                        if (atom != far && other != last && atom != last && naphthalene.sites().get(last).element().equals("C")
                            && naphthalene.sites().get(atom).element().equals("C"))
                        {
                            double turn = Math.abs(torsion(naphthalene, atom, other, far, last));
                            assertThat(Math.min(turn, 180 - turn)).isLessThan(10);
                        }
                    }
                }
            }
        }
    }

    @Test
    void should_embed_awkward_molecules_with_sensible_bonds()
    {
        String[] smiles = {
            "O", "[NH4+]", "CC(=O)Oc1ccccc1C(=O)O", "Cn1cnc2c1c(=O)n(C)c(=O)n2C", "CC(C)Cc1ccc(cc1)[C@@H](C)C(=O)O",
            "C[C@H]1CC[C@H]2[C@@H](C1)CC[C@@H]3[C@@H]2CC[C@]4(C)[C@H]3CC[C@@H]4O", "[O-]S(=O)(=O)[O-]", "C1CC2CCC1C2",
            "CC(=O)NC", "C=CC=C", "O=C=O", "[Fe+2].[Cl-].[Cl-]"
        };
        for (String each : smiles)
        {
            Structure3D structure = embed(each);
            for (Structure3D.Connection connection : structure.connections())
            {
                double ideal = Bounds.length(new Topology(Molecule.fromSmiles(each)),
                    new Topology.Link(connection.first(), connection.second(), connection.order(), connection.bond()));
                assertThat(distance(structure, connection.first(), connection.second()))
                    .as(each).isCloseTo(ideal, within(0.15));
            }
        }
    }

    @Test
    void should_make_sulfur_hexafluoride_octahedral()
    {
        Structure3D sulfurHexafluoride = embed("FS(F)(F)(F)(F)F");

        int right = 0;
        int straight = 0;
        for (int first = 0; first < 7; first++)
        {
            for (int second = first + 1; second < 7; second++)
            {
                if (first == 1 || second == 1)
                {
                    continue;
                }
                double angle = angle(sulfurHexafluoride, first, 1, second);
                right += Math.abs(angle - 90) < 15 ? 1 : 0;
                straight += angle > 160 ? 1 : 0;
            }
        }
        assertThat(right).isEqualTo(12);
        assertThat(straight).isEqualTo(3);
    }
}
