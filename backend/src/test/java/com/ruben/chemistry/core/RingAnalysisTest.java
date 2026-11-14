package com.ruben.chemistry.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RingAnalysisTest
{
    @Test
    void reads_a_ring_written_with_aromatic_bonds()
    {
        assertThat(notationOf("c1ccccc1")).isEqualTo(RingAnalysis.Notation.AROMATIC);
        assertThat(notationsOf("c1ccc2ccccc2c1"))
            .containsExactly(RingAnalysis.Notation.AROMATIC, RingAnalysis.Notation.AROMATIC);
    }

    @Test
    void reads_a_ring_written_without_aromatic_bonds()
    {
        assertThat(notationOf("C1=CC=CC=C1")).isEqualTo(RingAnalysis.Notation.PLAIN);
        assertThat(notationOf("C1CCCCC1")).isEqualTo(RingAnalysis.Notation.PLAIN);
    }

    @Test
    void reads_a_ring_written_aromatic_only_in_part()
    {
        List<Atom> carbons = List.of(
            Atom.organic("C"), Atom.organic("C"), Atom.organic("C"),
            Atom.organic("C"), Atom.organic("C"), Atom.organic("C"));
        Molecule.Builder builder = new Molecule.Builder().addAtoms(carbons.toArray(Atom[]::new));
        // one aromatic bond stands in for the double bond the alternation would otherwise put here
        builder.addBond(carbons.get(0), carbons.get(1), BondOrder.AROMATIC);
        builder.addBond(carbons.get(1), carbons.get(2), BondOrder.SINGLE);
        builder.addBond(carbons.get(2), carbons.get(3), BondOrder.DOUBLE);
        builder.addBond(carbons.get(3), carbons.get(4), BondOrder.SINGLE);
        builder.addBond(carbons.get(4), carbons.get(5), BondOrder.DOUBLE);
        builder.addBond(carbons.get(5), carbons.get(0), BondOrder.SINGLE);
        Molecule molecule = builder.build();

        RingAnalysis analysis = RingAnalysis.of(molecule, carbons);
        assertThat(analysis.notation()).isEqualTo(RingAnalysis.Notation.MIXED);
        assertThat(analysis.isAromatic()).isTrue();
    }

    @Test
    void takes_its_aromaticity_from_the_system_the_ring_belongs_to()
    {
        assertThat(analysisOf("c1ccccc1").isAromatic()).isTrue();
        assertThat(analysisOf("C1CCCCC1").isAromatic()).isFalse();
        // azulene's rings are aromatic by belonging to an aromatic system, not on their own account
        assertThat(analysesOf("c1cc2cccccc2c1")).allMatch(RingAnalysis::isAromatic);
        assertThat(analysesOf("c1ccc2ccccc2c1")).allMatch(RingAnalysis::isAromatic);
    }

    @Test
    void reports_a_ring_written_out_as_a_kekule_ring()
    {
        assertThat(analysisOf("C1=CC=CC=C1").isKekule()).isTrue();
        assertThat(analysisOf("c1ccccc1").isKekule()).isFalse();
        assertThat(analysisOf("C1CCCCC1").isKekule()).isFalse();
        assertThat(analysesOf("C1=CC2=CC=CC=C2C=C1")).allMatch(RingAnalysis::isKekule);
    }

    @Test
    void reports_a_ring_written_aromatic_that_the_count_does_not_bear_out()
    {
        assertThat(analysisOf("c1cccccc1").isMisdeclared()).isTrue();
        assertThat(analysisOf("c1ccc1").isMisdeclared()).isTrue();
        assertThat(analysisOf("c1ccccc1").isMisdeclared()).isFalse();
        // a ring written out makes no claim to be aromatic, so it cannot misdeclare one
        assertThat(analysisOf("C1=CC=CC=CC1").isMisdeclared()).isFalse();
        // azulene and the phenalenyl anion are aromatic systems, whatever their rings count alone
        assertThat(analysesOf("c1cc2cccccc2c1")).noneMatch(RingAnalysis::isMisdeclared);
        assertThat(analysesOf("c1[cH-]c2cccc3cccc(c1)c23")).noneMatch(RingAnalysis::isMisdeclared);
    }

    @Test
    void finds_no_system_for_a_ring_whose_atoms_cannot_all_join_one()
    {
        assertThat(analysisOf("C1CCCCC1").system()).isEmpty();
        assertThat(analysisOf("c1ccccc1").system()).isPresent();
        // indane's saturated ring joins no system, while the benzene it is fused to is one
        assertThat(analysesOf("C1Cc2ccccc2C1")).anySatisfy(analysis -> assertThat(analysis.system()).isEmpty());
        assertThat(analysesOf("C1Cc2ccccc2C1")).anySatisfy(analysis -> assertThat(analysis.system()).isPresent());
    }

    private static RingAnalysis analysisOf(String smiles)
    {
        Molecule molecule = Molecule.fromSmiles(smiles);
        return RingAnalysis.of(molecule, molecule.rings().getFirst().atoms());
    }

    private static RingAnalysis.Notation notationOf(String smiles)
    {
        return analysisOf(smiles).notation();
    }

    private static List<RingAnalysis> analysesOf(String smiles)
    {
        Molecule molecule = Molecule.fromSmiles(smiles);
        return molecule.rings().stream().map(ring -> RingAnalysis.of(molecule, ring.atoms())).toList();
    }

    private static List<RingAnalysis.Notation> notationsOf(String smiles)
    {
        return analysesOf(smiles).stream().map(RingAnalysis::notation).toList();
    }
}
