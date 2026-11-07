package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smarts.SmartsParser;
import com.ruben.chemistry.smiles.SmilesParser;
import com.ruben.chemistry.util.IdentitySet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryStereoTest
{
    private static Query query(String smarts)
    {
        return new SmartsParser(smarts).parseQuery();
    }

    private static Molecule molecule(String smiles)
    {
        return new SmilesParser(smiles).parse();
    }

    @Nested
    class Reading
    {
        @Test
        void a_query_declaring_no_stereochemistry_has_no_position_reading_anything()
        {
            Query query = query("CC(F)(Cl)C=CF");
            QueryStereo stereo = QueryStereo.of(query);

            for (QueryAtom position : query.atoms())
            {
                assertThat(stereo.reading(position)).isEmpty();
            }
        }

        @Test
        void a_declared_chirality_is_read_by_its_stereocentre_and_every_neighbour()
        {
            Query query = query("[C@](F)(Cl)(Br)N");
            QueryStereo stereo = QueryStereo.of(query);

            QueryStereo.Constraint constraint = only(stereo.reading(nthAtom(query, 0)));
            assertThat(constraint).isInstanceOf(QueryStereo.ChiralityConstraint.class);
            assertDependenciesAre(constraint, query.atoms().toArray(QueryAtom[]::new));

            for (QueryAtom neighbour : query.atoms().subList(1, query.atomCount()))
            {
                assertThat(only(stereo.reading(neighbour))).isSameAs(constraint);
            }
        }

        @Test
        void a_declared_chirality_names_the_stereocentre_it_was_declared_at()
        {
            Query query = query("F[C@H](Cl)Br");
            QueryStereo.ChiralityConstraint constraint = chirality(only(QueryStereo.of(query).reading(nthAtom(query, 0))));

            assertThat(constraint.stereocentre()).isSameAs(nthAtom(query, 1));
        }

        @Test
        void a_declared_chirality_carries_the_parity_and_hydrogen_slot_it_was_declared_with()
        {
            Query fullySubstituted = query("[C@](F)(Cl)(Br)N");
            Query withHydrogen = query("[C@@H](F)(Cl)N");

            assertThat(chirality(only(QueryStereo.of(fullySubstituted).reading(nthAtom(fullySubstituted, 0)))).declared())
                .isEqualTo(new Query.DeclaredChirality(Chirality.ANTICLOCKWISE, false));
            assertThat(chirality(only(QueryStereo.of(withHydrogen).reading(nthAtom(withHydrogen, 0)))).declared())
                .isEqualTo(new Query.DeclaredChirality(Chirality.CLOCKWISE, true));
        }

        @Test
        void a_position_the_stereocentre_does_not_touch_reads_nothing()
        {
            Query query = query("[C@H](F)(Cl)CO");

            // the O hangs off a neighbour of the stereocentre, so the chirality never looks at it
            assertThat(QueryStereo.of(query).reading(nthAtom(query, 4))).isEmpty();
        }

        @Test
        void a_chirality_declared_on_a_position_with_no_neighbours_is_read_by_that_position_alone()
        {
            QueryAtom lone = new QueryAtom(AtomPredicates.element("C"));
            Query query = new Query.Builder()
                .addAtom(lone)
                .setChirality(lone, Chirality.CLOCKWISE)
                .build();

            assertDependenciesAre(only(QueryStereo.of(query).reading(lone)), lone);
        }

        @Test
        void a_position_shared_by_two_stereocentres_reads_both_of_their_requirements()
        {
            Query query = query("N[C@](Br)(Cl)[C@](F)(I)O");
            QueryStereo stereo = QueryStereo.of(query);

            // the two centres are bonded, so each is a neighbour of the other as well as a centre
            assertThat(stereo.reading(nthAtom(query, 1))).hasSize(2);
            assertThat(stereo.reading(nthAtom(query, 4))).hasSize(2);

            // a substituent belongs to one centre only
            assertThat(stereo.reading(nthAtom(query, 0))).hasSize(1);
            assertThat(stereo.reading(nthAtom(query, 7))).hasSize(1);
        }

        @Test
        void a_declared_double_bond_stereo_is_read_by_both_endpoints_and_their_reference_substituents()
        {
            Query query = query("F/C=C/F");
            QueryStereo stereo = QueryStereo.of(query);

            QueryStereo.Constraint constraint = only(stereo.reading(nthAtom(query, 1)));
            assertThat(constraint).isInstanceOf(QueryStereo.DoubleBondConstraint.class);
            assertDependenciesAre(constraint, query.atoms().toArray(QueryAtom[]::new));

            for (QueryAtom position : query.atoms())
            {
                assertThat(only(stereo.reading(position))).isSameAs(constraint);
            }
        }

        @Test
        void a_declared_double_bond_stereo_names_the_bond_and_configuration_it_was_declared_with()
        {
            Query query = query("F/C=C/F");
            QueryStereo.DoubleBondConstraint constraint = doubleBond(only(QueryStereo.of(query).reading(nthAtom(query, 1))));

            assertThat(constraint.stereoBond()).isSameAs(query.bondBetween(nthAtom(query, 1), nthAtom(query, 2)).orElseThrow());
            assertThat(constraint.declared()).isEqualTo(DoubleBondStereo.OPPOSITE_SIDE);
            assertThat(constraint.declared()).isEqualTo(query.doubleBondStereoOf(constraint.stereoBond()).orElseThrow());
        }

        @Test
        void a_substituent_the_configuration_is_not_measured_against_is_not_read()
        {
            Query query = query("F/C(Cl)=C/F");
            QueryStereo stereo = QueryStereo.of(query);

            // F was written first, so it is that endpoint's reference and the Cl is never looked at
            assertDependenciesAre(only(stereo.reading(nthAtom(query, 1))), nthAtom(query, 0), nthAtom(query, 1), nthAtom(query, 3), nthAtom(query, 4));
            assertThat(stereo.reading(nthAtom(query, 2))).isEmpty();
        }

        @Test
        void the_reference_substituents_read_are_the_ones_the_query_itself_names()
        {
            Query query = query("F/C(Cl)=C(I)/F");
            QueryStereo.DoubleBondConstraint constraint = doubleBond(only(QueryStereo.of(query).reading(nthAtom(query, 1))));
            QueryBond stereoBond = constraint.stereoBond();

            QueryAtom firstReference = referenceAtom(query, stereoBond, stereoBond.first());
            QueryAtom secondReference = referenceAtom(query, stereoBond, stereoBond.second());

            assertDependenciesAre(constraint, stereoBond.first(), stereoBond.second(), firstReference, secondReference);
        }

        @Test
        void a_stereo_bond_with_only_one_reference_substituent_reads_its_endpoints_and_that_one()
        {
            QueryAtom qC = new QueryAtom(AtomPredicates.element("C"));
            QueryAtom qCa = new QueryAtom(AtomPredicates.element("C"));
            QueryAtom qCb = new QueryAtom(AtomPredicates.element("C"));
            Query query = new Query.Builder()
                .addAtoms(qC, qCa, qCb)
                .addBond(qC, qCa, BondPredicates.any())
                .addBond(qCa, qCb, BondPredicates.order(BondOrder.DOUBLE))
                .setDoubleBondStereo(qCa, qCb, DoubleBondStereo.SAME_SIDE)
                .build();

            // qCb has nothing to measure against, so it contributes no reference position
            // so the set stays one a search can complete
            assertDependenciesAre(only(QueryStereo.of(query).reading(qCa)), qC, qCa, qCb);
        }

        @Test
        void a_query_declaring_both_kinds_of_stereochemistry_reads_each_against_its_own_positions()
        {
            Query query = query("F[C@H](Cl)/C=C/F");
            QueryStereo stereo = QueryStereo.of(query);

            // the first F only hangs off the stereocentre, and the last only references the double bond
            assertThat(only(stereo.reading(nthAtom(query, 0)))).isInstanceOf(QueryStereo.ChiralityConstraint.class);
            assertThat(only(stereo.reading(nthAtom(query, 5)))).isInstanceOf(QueryStereo.DoubleBondConstraint.class);

            // the stereocentre is the reference the configuration is measured against at its end of
            // the double bond, and that endpoint is in turn one of its neighbours, so both read both
            assertThat(stereo.reading(nthAtom(query, 1))).hasSize(2);
            assertThat(stereo.reading(nthAtom(query, 3))).hasSize(2);
        }

        @Test
        void positions_are_told_apart_by_identity_and_not_by_equality()
        {
            // a record, so two positions built from one predicate instance are equal but not the same
            AtomPredicate shared = AtomPredicates.element("C");
            QueryAtom declared = new QueryAtom(shared);
            QueryAtom other = new QueryAtom(shared);
            assertThat(declared).isEqualTo(other);

            Query query = new Query.Builder()
                .addAtoms(declared, other)
                .setChirality(declared, Chirality.CLOCKWISE)
                .build();
            QueryStereo stereo = QueryStereo.of(query);

            assertThat(only(stereo.reading(declared))).isInstanceOf(QueryStereo.ChiralityConstraint.class);
            assertThat(stereo.reading(other)).isEmpty();
        }

        @Test
        void a_position_of_another_query_entirely_reads_nothing()
        {
            Query other = query("[C@](F)(Cl)(Br)N");

            assertThat(QueryStereo.of(query("[C@](F)(Cl)(Br)N")).reading(nthAtom(other, 0))).isEmpty();
        }
    }

    @Nested
    class IsDecidable
    {
        @Test
        void a_requirement_is_undecided_until_every_position_it_reads_is_mapped()
        {
            Query query = query("[C@](F)(Cl)(Br)N");
            Molecule molecule = molecule("N[C@](Br)(Cl)F");
            QueryStereo.Constraint constraint = only(QueryStereo.of(query).reading(nthAtom(query, 0)));

            Map<QueryAtom, Atom> assignment = new IdentityHashMap<>();
            for (int position = 0; position < query.atomCount(); position++)
            {
                assertThat(constraint.isDecidable(assignment)).isFalse();
                assignment.put(nthAtom(query, position), molecule.atoms().get(position));
            }

            assertThat(constraint.isDecidable(assignment)).isTrue();
        }

        @Test
        void a_requirement_is_decided_by_the_positions_it_reads_and_no_others()
        {
            Query query = query("[C@H](F)(Cl)CO");
            Molecule molecule = molecule("OC[C@H](F)Cl");
            QueryStereo.Constraint constraint = only(QueryStereo.of(query).reading(nthAtom(query, 0)));

            Map<QueryAtom, Atom> assignment = new IdentityHashMap<>();
            for (int position = 0; position < query.atomCount() - 1; position++)
            {
                assignment.put(nthAtom(query, position), molecule.atoms().get(position));
            }

            // the O left unmapped but the chirality does not read it
            assertThat(assignment).hasSize(query.atomCount() - 1);
            assertThat(constraint.isDecidable(assignment)).isTrue();
        }
    }

    private static QueryStereo.Constraint only(List<QueryStereo.Constraint> constraints)
    {
        assertThat(constraints).hasSize(1);
        return constraints.getFirst();
    }

    private static QueryStereo.ChiralityConstraint chirality(QueryStereo.Constraint constraint)
    {
        return (QueryStereo.ChiralityConstraint) constraint;
    }

    private static QueryStereo.DoubleBondConstraint doubleBond(QueryStereo.Constraint constraint)
    {
        return (QueryStereo.DoubleBondConstraint) constraint;
    }

    private static void assertDependenciesAre(QueryStereo.Constraint constraint, QueryAtom... expected)
    {
        assertThat(constraint.dependencies()).hasSize(expected.length);
        assertThat(IdentitySet.copyOf(constraint.dependencies())).isEqualTo(IdentitySet.copyOf(List.of(expected)));
    }

    /**
     * @return the position {@code bond}'s configuration is measured against at {@code endpoint}
     */
    private static QueryAtom referenceAtom(Query query, QueryBond bond, QueryAtom endpoint)
    {
        return query.referenceBondOf(bond, endpoint).orElseThrow().getOther(endpoint);
    }

    private static QueryAtom nthAtom(Query query, int index)
    {
        return query.atoms().get(index);
    }
}
