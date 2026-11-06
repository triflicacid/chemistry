package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueriesTest
{
    @Test
    void a_molecule_converted_to_a_query_matches_as_a_substructure_of_a_larger_one()
    {
        Molecule fragment = new SmilesParser("CCO").parse(); // ethanol
        Molecule target = new SmilesParser("CCCO").parse(); // propan-1-ol

        Query query = Queries.fromMolecule(fragment);

        assertThat(new SubstructureMatcher(target).findMatches(query)).isNotEmpty();
    }

    @Test
    void a_molecule_converted_to_a_query_does_not_match_a_molecule_without_that_substructure()
    {
        Molecule fragment = new SmilesParser("CCO").parse(); // ethanol: has an oxygen
        Molecule target = new SmilesParser("CCC").parse(); // propane: no oxygen at all

        Query query = Queries.fromMolecule(fragment);

        assertThat(new SubstructureMatcher(target).findMatches(query)).isEmpty();
    }

    @Test
    void a_molecule_converted_to_a_query_distinguishes_the_double_bond_stereo_it_was_declared_with()
    {
        // same constitution, opposite configuration
        Molecule trans = new SmilesParser("F/C=C/F").parse();
        Molecule cis = new SmilesParser("F/C=C\\F").parse();

        Query transQuery = Queries.fromMolecule(trans);

        // matches itself
        assertThat(new SubstructureMatcher(trans).findMatches(transQuery)).isNotEmpty();
        // but not the cis isomer, despite having exactly the same atoms and bond orders
        assertThat(new SubstructureMatcher(cis).findMatches(transQuery)).isEmpty();
    }

    @Test
    void a_molecule_converted_to_a_query_does_not_match_a_different_isotope()
    {
        Molecule plain = new SmilesParser("C").parse();
        Molecule labelled = new SmilesParser("[13CH4]").parse();

        Query plainQuery = Queries.fromMolecule(plain);
        Query labelledQuery = Queries.fromMolecule(labelled);

        // a plain, unspecified atom doesn't care about isotope either way
        assertThat(new SubstructureMatcher(labelled).findMatches(plainQuery)).isNotEmpty();
        // but an explicitly labelled one requires that exact isotope
        assertThat(new SubstructureMatcher(plain).findMatches(labelledQuery)).isEmpty();
    }
}
