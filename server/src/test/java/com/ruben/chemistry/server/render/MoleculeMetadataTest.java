package com.ruben.chemistry.server.render;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesParser;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.smiles.Span;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoleculeMetadataTest
{
    private static MoleculeMetadata of(String smiles)
    {
        SmilesParser parser = new SmilesParser(smiles);
        Molecule molecule = parser.parse();
        SmilesWriter writer = new SmilesWriter(molecule).setRanker(new CanonicalRanker());
        String canonical = writer.write();
        MoleculeMetadata.Notation source = new MoleculeMetadata.Notation(smiles, parser.atomSpans(), parser.bondSpans());
        return MoleculeMetadata.of(molecule, source,
            new MoleculeMetadata.Notation(canonical, writer.atomSpans(), writer.bondSpans()));
    }

    @Test
    void should_describe_atoms_and_bonds()
    {
        MoleculeMetadata metadata = of("CO");

        assertThat(metadata.atoms()).extracting(MoleculeMetadata.AtomInfo::element).containsExactly("C", "O");
        MoleculeMetadata.AtomInfo oxygen = metadata.atoms().get(1);
        assertThat(oxygen.atomicNumber()).isEqualTo(8);
        assertThat(oxygen.valence()).isEqualTo(2);
        assertThat(oxygen.hydrogens()).isEqualTo(1);
        assertThat(metadata.atoms().get(0).hydrogens()).isEqualTo(3);
        assertThat(oxygen.rings()).isEmpty();
        assertThat(metadata.bonds()).hasSize(1);
        assertThat(metadata.bonds().getFirst().order()).isEqualTo("single");
        assertThat(metadata.bonds().getFirst().first()).isEqualTo(0);
        assertThat(metadata.bonds().getFirst().second()).isEqualTo(1);
        assertThat(metadata.rings()).isEmpty();
    }

    @Test
    void should_list_ring_with_its_smiles_and_members()
    {
        MoleculeMetadata metadata = of("c1ccccc1C");

        assertThat(metadata.rings()).hasSize(1);
        MoleculeMetadata.RingInfo ring = metadata.rings().getFirst();
        assertThat(ring.size()).isEqualTo(6);
        assertThat(ring.smiles()).isNotBlank();
        assertThat(ring.atoms()).hasSize(6);
        assertThat(ring.bonds()).hasSize(6);

        // the methyl carbon is not in the ring; the ring's atoms and bonds each point back to it
        long inRing = metadata.atoms().stream().filter(atom -> atom.rings().contains(0)).count();
        assertThat(inRing).isEqualTo(6);
        assertThat(metadata.bonds().stream().filter(bond -> bond.rings().contains(0))).hasSize(6);
    }

    @Test
    void should_locate_atoms_in_the_source_and_ring_smiles()
    {
        MoleculeMetadata metadata = of("c1cc[nH]c1C");

        assertThat(metadata.smiles()).isEqualTo("c1cc[nH]c1C");
        MoleculeMetadata.AtomInfo first = metadata.atoms().get(0);
        assertThat(first.span().start()).isEqualTo(0);
        assertThat(first.span().end()).isEqualTo(1);
        MoleculeMetadata.AtomInfo nitrogen = metadata.atoms().get(3);
        assertThat(metadata.smiles().substring(nitrogen.span().start(), nitrogen.span().end())).isEqualTo("[nH]");
        assertThat(metadata.atoms().get(5).span().start()).isEqualTo(10);

        MoleculeMetadata.RingInfo ring = metadata.rings().getFirst();
        assertThat(ring.spans()).hasSize(5).doesNotContainNull();
        for (int index = 0; index < ring.atoms().size(); index++)
        {
            String text = ring.smiles().substring(ring.spans().get(index).start(), ring.spans().get(index).end());
            assertThat(text).isEqualTo(metadata.atoms().get(ring.atoms().get(index)).element().toLowerCase().equals("n") ? "[nH]" : "c");
        }
    }

    @Test
    void should_locate_atoms_in_the_canonical_smiles()
    {
        MoleculeMetadata metadata = of("OCC");

        assertThat(metadata.canonicalSmiles()).isNotBlank();
        assertThat(metadata.atoms()).extracting(MoleculeMetadata.AtomInfo::canonicalSpan).doesNotContainNull();
        for (MoleculeMetadata.AtomInfo atom : metadata.atoms())
        {
            String text = metadata.canonicalSmiles().substring(atom.canonicalSpan().start(), atom.canonicalSpan().end());
            assertThat(text).isEqualTo(atom.element());
        }
    }

    @Test
    void should_locate_written_bond_orders_in_the_source()
    {
        MoleculeMetadata metadata = of("CC=CC");

        assertThat(metadata.bonds()).extracting(MoleculeMetadata.BondInfo::spans)
            .containsExactly(List.of(), List.of(new Span(2, 3)), List.of());
    }

    @Test
    void should_locate_a_ring_bond_written_at_both_of_its_ends()
    {
        MoleculeMetadata metadata = of("C=1C=CC=CC=1");

        MoleculeMetadata.BondInfo closure = metadata.bonds().stream()
            .filter(bond -> bond.spans().size() == 2)
            .findFirst()
            .orElseThrow();
        assertThat(closure.spans()).containsExactly(new Span(1, 3), new Span(10, 12));
        for (Span span : closure.spans())
        {
            assertThat(metadata.smiles().substring(span.start(), span.end())).isEqualTo("=1");
        }
    }

    @Test
    void should_locate_written_bonds_in_the_canonical_smiles()
    {
        MoleculeMetadata metadata = of("CC=CC");

        MoleculeMetadata.BondInfo written = metadata.bonds().stream()
            .filter(bond -> !bond.canonicalSpans().isEmpty())
            .findFirst()
            .orElseThrow();
        assertThat(written.order()).isEqualTo("double");
        Span span = written.canonicalSpans().getFirst();
        assertThat(metadata.canonicalSmiles().substring(span.start(), span.end())).isEqualTo("=");
    }

    @Test
    void should_locate_a_ring_bond_in_the_canonical_smiles()
    {
        MoleculeMetadata metadata = of("c1ccccc1");

        MoleculeMetadata.BondInfo closure = metadata.bonds().stream()
            .filter(bond -> bond.canonicalSpans().size() == 2)
            .findFirst()
            .orElseThrow();
        for (Span span : closure.canonicalSpans())
        {
            assertThat(metadata.canonicalSmiles().substring(span.start(), span.end())).isEqualTo("1");
        }
    }

    @Test
    void should_list_each_ring_of_fused_system()
    {
        MoleculeMetadata metadata = of("c1ccc2ccccc2c1");

        assertThat(metadata.rings()).hasSize(2);
        assertThat(metadata.atoms().stream().filter(atom -> atom.rings().size() == 2)).hasSize(2);
    }
}
