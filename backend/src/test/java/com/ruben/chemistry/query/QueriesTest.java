package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Molecule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueriesTest
{
    @Test
    void a_molecule_converted_to_a_query_matches_as_a_substructure_of_a_larger_one()
    {
        Molecule fragment = Molecule.fromSmiles("CCO"); // ethanol
        Molecule target = Molecule.fromSmiles("CCCO"); // propan-1-ol

        Query query = Queries.fromMolecule(fragment);

        assertThat(new SubstructureMatcher(target).findMatches(query)).isNotEmpty();
    }

    @Test
    void a_molecule_converted_to_a_query_does_not_match_a_molecule_without_that_substructure()
    {
        Molecule fragment = Molecule.fromSmiles("CCO"); // ethanol: has an oxygen
        Molecule target = Molecule.fromSmiles("CCC"); // propane: no oxygen at all

        Query query = Queries.fromMolecule(fragment);

        assertThat(new SubstructureMatcher(target).findMatches(query)).isEmpty();
    }

    @Test
    void a_molecule_converted_to_a_query_distinguishes_the_double_bond_stereo_it_was_declared_with()
    {
        // same constitution, opposite configuration
        Molecule trans = Molecule.fromSmiles("F/C=C/F");
        Molecule cis = Molecule.fromSmiles("F/C=C\\F");

        Query transQuery = Queries.fromMolecule(trans);

        // matches itself
        assertThat(new SubstructureMatcher(trans).findMatches(transQuery)).isNotEmpty();
        // but not the cis isomer, despite having exactly the same atoms and bond orders
        assertThat(new SubstructureMatcher(cis).findMatches(transQuery)).isEmpty();
    }

    @Test
    void a_molecule_converted_to_a_query_does_not_match_a_different_isotope()
    {
        Molecule plain = Molecule.fromSmiles("C");
        Molecule labelled = Molecule.fromSmiles("[13CH4]");

        Query plainQuery = Queries.fromMolecule(plain);
        Query labelledQuery = Queries.fromMolecule(labelled);

        // a plain, unspecified atom doesn't care about isotope either way
        assertThat(new SubstructureMatcher(labelled).findMatches(plainQuery)).isNotEmpty();
        // but an explicitly labelled one requires that exact isotope
        assertThat(new SubstructureMatcher(plain).findMatches(labelledQuery)).isEmpty();
    }

    @Test
    void a_molecule_converted_to_a_query_matches_a_stereocentre_bearing_an_implicit_hydrogen()
    {
        // same constitution, opposite configuration, with the hydrogen as a fourth neighbour
        Molecule anticlockwise = Molecule.fromSmiles("N[C@H](F)Cl");
        Molecule clockwise = Molecule.fromSmiles("N[C@@H](F)Cl");

        Query anticlockwiseQuery = Queries.fromMolecule(anticlockwise);

        assertThat(new SubstructureMatcher(anticlockwise).findMatches(anticlockwiseQuery)).isNotEmpty();
        assertThat(new SubstructureMatcher(clockwise).findMatches(anticlockwiseQuery)).isEmpty();
    }
}
