package com.ruben.chemistry.server.render;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesParser;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.util.Span;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoleculeMetadataTest
{
    private static final String COPPER_SULFATE_PENTAHYDRATE = "[Cu+2].[O-]S(=O)(=O)[O-].O.O.O.O.O";

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
    void should_write_the_molecular_and_empirical_formulae()
    {
        MoleculeMetadata.FormulaInfo formula = of("c1ccccc1").formula();

        assertThat(formula.molecular()).isEqualTo("C₆H₆");
        assertThat(formula.empirical()).isEqualTo("CH");
    }

    @Test
    void should_write_the_condensed_structural_formula()
    {
        assertThat(of("CCO").formula().condensed()).isEqualTo("CH₃CH₂OH");
        assertThat(of("CCCCCC").formula().condensed()).isEqualTo("CH₃(CH₂)₄CH₃");
    }

    @Test
    void should_group_fragments_into_components_and_neutral_units()
    {
        MoleculeMetadata.FormulaInfo formula = of(COPPER_SULFATE_PENTAHYDRATE).formula();

        assertThat(formula.molecular()).isEqualTo("CuH₁₀SO₉");
        assertThat(formula.components().text()).isEqualTo("Cu²⁺·SO₄²⁻·5H₂O");
        assertThat(formula.neutralUnits().text()).isEqualTo("CuSO₄·5H₂O");
    }

    @Test
    void should_locate_each_unit_in_a_fragmented_formula()
    {
        MoleculeMetadata.FragmentedInfo fragmented = of(COPPER_SULFATE_PENTAHYDRATE).formula().neutralUnits();

        assertThat(fragmented.units()).hasSize(2);
        for (MoleculeMetadata.UnitInfo unit : fragmented.units())
        {
            assertThat(fragmented.text().substring(unit.span().start(), unit.span().end())).isNotBlank();
        }
        MoleculeMetadata.UnitInfo water = fragmented.units().get(1);
        assertThat(fragmented.text().substring(water.span().start(), water.span().end())).isEqualTo("5H₂O");
    }

    @Test
    void should_say_which_fragments_each_unit_stands_for()
    {
        MoleculeMetadata.FormulaInfo formula = of(COPPER_SULFATE_PENTAHYDRATE).formula();

        MoleculeMetadata.UnitInfo salt = formula.neutralUnits().units().getFirst();
        assertThat(salt.count()).isEqualTo(1);
        assertThat(salt.fragments()).containsExactly(0, 1);

        MoleculeMetadata.UnitInfo water = formula.neutralUnits().units().get(1);
        assertThat(water.count()).isEqualTo(5);
        assertThat(water.fragments()).containsExactly(2, 3, 4, 5, 6);
    }

    @Test
    void should_describe_each_connected_fragment()
    {
        MoleculeMetadata metadata = of(COPPER_SULFATE_PENTAHYDRATE);

        assertThat(metadata.fragments()).hasSize(7);
        assertThat(metadata.fragments()).extracting(MoleculeMetadata.FragmentInfo::charge)
            .containsExactly(2, -2, 0, 0, 0, 0, 0);
        assertThat(metadata.fragments()).extracting(MoleculeMetadata.FragmentInfo::formula)
            .containsExactly("Cu²⁺", "SO₄²⁻", "H₂O", "H₂O", "H₂O", "H₂O", "H₂O");

        MoleculeMetadata.FragmentInfo sulfate = metadata.fragments().get(1);
        assertThat(sulfate.atoms()).containsExactly(1, 2, 3, 4, 5);
        assertThat(sulfate.bonds()).containsExactly(0, 1, 2, 3);
        assertThat(metadata.fragments().get(2).smiles()).isEqualTo("O");
    }

    @Test
    void should_write_a_ring_formula_as_if_the_ring_stood_alone()
    {
        MoleculeMetadata metadata = of("c1ccccc1C");

        assertThat(metadata.rings().getFirst().formula()).isEqualTo("C₆H₆");
    }

    @Test
    void should_list_each_ring_of_fused_system()
    {
        MoleculeMetadata metadata = of("c1ccc2ccccc2c1");

        assertThat(metadata.rings()).hasSize(2);
        assertThat(metadata.atoms().stream().filter(atom -> atom.rings().size() == 2)).hasSize(2);
    }
}
