package com.ruben.chemistry.query;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smarts.SmartsParser;
import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubstructureMatcherTest
{
    @Test
    void finds_every_ordered_occurrence_of_a_two_atom_pattern()
    {
        // propan-1-ol (CCCO): the C-C pattern should match both adjacent carbon pairs,
        // once for each of the two ways to assign "first"/"second" to the pair's two atoms
        Molecule molecule = new SmilesParser("CCCO").parse();

        QueryAtom first = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom second = new QueryAtom(AtomPredicates.element("C"));
        Query query = new Query.Builder()
            .addAtoms(first, second)
            .addBond(first, second, BondPredicates.order(BondOrder.SINGLE))
            .build();

        List<Match> matches = new SubstructureMatcher(molecule).findMatches(query);

        assertThat(matches).hasSize(4);
    }

    @Test
    void the_wildcard_atom_matches_every_atom()
    {
        Molecule molecule = new SmilesParser("CO").parse();

        Query query = new Query.Builder().addAtom(new QueryAtom(AtomPredicates.any())).build();

        List<Match> matches = new SubstructureMatcher(molecule).findMatches(query);

        assertThat(matches).hasSize(2);
    }

    @Test
    void does_not_match_when_no_atom_satisfies_the_predicate()
    {
        Molecule molecule = new SmilesParser("CC").parse();

        Query query = new Query.Builder().addAtom(new QueryAtom(AtomPredicates.element("N"))).build();

        assertThat(new SubstructureMatcher(molecule).findMatches(query)).isEmpty();
    }

    @Test
    void does_not_match_atoms_that_are_not_actually_bonded()
    {
        // OCCO: both oxygens satisfy the atom predicate, but they're not bonded to each
        // other (each is bonded to a carbon instead), so the O-O pattern finds no match
        Molecule molecule = new SmilesParser("OCCO").parse();

        QueryAtom first = new QueryAtom(AtomPredicates.element("O"));
        QueryAtom second = new QueryAtom(AtomPredicates.element("O"));
        Query query = new Query.Builder()
            .addAtoms(first, second)
            .addBond(first, second, BondPredicates.any())
            .build();

        assertThat(new SubstructureMatcher(molecule).findMatches(query)).isEmpty();
    }

    @Test
    void the_ring_membership_atom_predicate_matches_only_ring_atoms()
    {
        Molecule ring = new SmilesParser("C1CC1").parse();
        Molecule chain = new SmilesParser("CCC").parse();
        Query query = new Query.Builder().addAtom(new QueryAtom(AtomPredicates.inRing())).build();

        assertThat(new SubstructureMatcher(ring).findMatches(query)).hasSize(3);
        assertThat(new SubstructureMatcher(chain).findMatches(query)).isEmpty();
    }

    @Test
    void the_ring_bond_and_chain_bond_predicates_distinguish_the_connecting_bond()
    {
        // bicyclopropyl: two 3-membered rings (6 ring bonds) joined by one bond that is
        // itself not part of either ring
        Molecule molecule = new SmilesParser("C1CC1C1CC1").parse();
        SubstructureMatcher matcher = new SubstructureMatcher(molecule);

        QueryAtom a = new QueryAtom(AtomPredicates.any());
        QueryAtom b = new QueryAtom(AtomPredicates.any());
        Query ringBond = new Query.Builder()
            .addAtoms(a, b)
            .addBond(a, b, BondPredicates.ringBond())
            .build();

        QueryAtom c = new QueryAtom(AtomPredicates.any());
        QueryAtom d = new QueryAtom(AtomPredicates.any());
        Query chainBond = new Query.Builder()
            .addAtoms(c, d)
            .addBond(c, d, BondPredicates.chainBond())
            .build();

        assertThat(matcher.findMatches(ringBond)).hasSize(12); // 6 ring bonds, both orderings
        assertThat(matcher.findMatches(chainBond)).hasSize(2); // 1 connecting bond, both orderings
    }

    @Test
    void the_chirality_requirement_is_reprojected_onto_the_query_declared_order()
    {
        // N[C@](Br)(Cl)F: fully substituted (no implicit hydrogen), stored chirality is
        // ANTICLOCKWISE relative to the real bond order [N, Br, Cl, F]
        Molecule molecule = new SmilesParser("N[C@](Br)(Cl)F").parse();

        QueryAtom qBr = new QueryAtom(AtomPredicates.element("Br"));
        QueryAtom qC = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom qN = new QueryAtom(AtomPredicates.element("N"));
        QueryAtom qCl = new QueryAtom(AtomPredicates.element("Cl"));
        QueryAtom qF = new QueryAtom(AtomPredicates.element("F"));

        // Br and N swapped relative to the real [N, Br, Cl, F] order - a single
        // transposition, so the query must declare the flip of the real value to match
        Query matching = new Query.Builder()
            .addAtoms(qBr, qC, qN, qCl, qF)
            .addBond(qC, qBr, BondPredicates.any())
            .addBond(qC, qN, BondPredicates.any())
            .addBond(qC, qCl, BondPredicates.any())
            .addBond(qC, qF, BondPredicates.any())
            .setChirality(qC, Chirality.CLOCKWISE)
            .build();
        assertThat(new SubstructureMatcher(molecule).findMatches(matching)).hasSize(1);

        Query nonMatching = new Query.Builder()
            .addAtoms(qBr, qC, qN, qCl, qF)
            .addBond(qC, qBr, BondPredicates.any())
            .addBond(qC, qN, BondPredicates.any())
            .addBond(qC, qCl, BondPredicates.any())
            .addBond(qC, qF, BondPredicates.any())
            .setChirality(qC, Chirality.ANTICLOCKWISE)
            .build();
        assertThat(new SubstructureMatcher(molecule).findMatches(nonMatching)).isEmpty();
    }

    @Test
    void the_double_bond_stereo_requirement_is_reprojected_onto_the_query_declared_references()
    {
        // Cl/C(Br)=C/F: stored configuration is OPPOSITE_SIDE relative to the real default
        // references [Cl, F] (Cl is Ca's first-added substituent)
        Molecule molecule = new SmilesParser("Cl/C(Br)=C/F").parse();

        QueryAtom qCl = new QueryAtom(AtomPredicates.element("Cl"));
        QueryAtom qCa = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom qBr = new QueryAtom(AtomPredicates.element("Br"));
        QueryAtom qCb = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom qF = new QueryAtom(AtomPredicates.element("F"));

        // qBr added as Ca's reference bond before qCl, the opposite of the real molecule's
        // default (Cl), so the query must declare the flip of the real value to match
        Query matching = new Query.Builder()
            .addAtoms(qCa, qBr, qCl, qCb, qF)
            .addBond(qCa, qBr, BondPredicates.any())
            .addBond(qCa, qCl, BondPredicates.any())
            .addBond(qCa, qCb, BondPredicates.order(BondOrder.DOUBLE))
            .addBond(qCb, qF, BondPredicates.any())
            .setDoubleBondStereo(qCa, qCb, DoubleBondStereo.SAME_SIDE)
            .build();
        assertThat(new SubstructureMatcher(molecule).findMatches(matching)).hasSize(1);

        Query nonMatching = new Query.Builder()
            .addAtoms(qCa, qBr, qCl, qCb, qF)
            .addBond(qCa, qBr, BondPredicates.any())
            .addBond(qCa, qCl, BondPredicates.any())
            .addBond(qCa, qCb, BondPredicates.order(BondOrder.DOUBLE))
            .addBond(qCb, qF, BondPredicates.any())
            .setDoubleBondStereo(qCa, qCb, DoubleBondStereo.OPPOSITE_SIDE)
            .build();
        assertThat(new SubstructureMatcher(molecule).findMatches(nonMatching)).isEmpty();
    }

    @Test
    void findDistinctMatches_collapses_a_symmetric_querys_several_mappings_of_one_ring_into_one()
    {
        // c1ccccc1: a 6-fold symmetric benzene query maps onto the one real ring 12 ways
        // (6 rotations, each in 2 directions), all of them covering the same 6 real atoms
        Molecule molecule = new SmilesParser("c1ccccc1").parse();

        QueryAtom[] ring = new QueryAtom[6];
        for (int i = 0; i < ring.length; i++)
        {
            ring[i] = new QueryAtom(AtomPredicates.aromatic("C"));
        }
        Query.Builder builder = new Query.Builder();
        for (QueryAtom atom : ring)
        {
            builder.addAtom(atom);
        }
        for (int i = 0; i < ring.length; i++)
        {
            builder.addBond(ring[i], ring[(i + 1) % ring.length], BondPredicates.order(BondOrder.AROMATIC));
        }
        Query benzene = builder.build();

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);

        assertThat(matcher.findMatches(benzene)).hasSize(12);
        assertThat(matcher.findDistinctMatches(benzene)).hasSize(1);
    }

    @Test
    void findDistinctMatches_keeps_matches_that_cover_different_atoms()
    {
        // propan-1-ol (CCCO): the two C-C matches are symmetric in ORDER but cover different atoms
        Molecule molecule = new SmilesParser("CCCO").parse();

        QueryAtom first = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom second = new QueryAtom(AtomPredicates.element("C"));
        Query query = new Query.Builder()
            .addAtoms(first, second)
            .addBond(first, second, BondPredicates.order(BondOrder.SINGLE))
            .build();

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);

        assertThat(matcher.findMatches(query)).hasSize(4);
        assertThat(matcher.findDistinctMatches(query)).hasSize(2);
    }

    @Test
    void anchored_findMatches_only_considers_matches_with_queryAtom_bound_to_the_given_atom()
    {
        // acetone: CC(=O)C
        Molecule molecule = new SmilesParser("CC(=O)C").parse();
        Atom carbonylCarbon = nthAtom(molecule, 1);
        Atom methylCarbon = nthAtom(molecule, 0);

        QueryAtom qc = new QueryAtom(AtomPredicates.any());
        QueryAtom qo = new QueryAtom(AtomPredicates.element("O"));
        Query query = new Query.Builder()
            .addAtoms(qc, qo)
            .addBond(qc, qo, BondPredicates.order(BondOrder.DOUBLE))
            .build();

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);

        assertThat(matcher.findMatches(query, qc, carbonylCarbon)).hasSize(1);
        assertThat(matcher.findMatches(query, qc, methylCarbon)).isEmpty();
    }

    @Test
    void anchored_findMatches_rejects_an_anchor_that_fails_queryAtoms_own_predicate()
    {
        Molecule molecule = new SmilesParser("CC(=O)C").parse();
        Atom carbon = nthAtom(molecule, 0);

        QueryAtom qo = new QueryAtom(AtomPredicates.element("O"));
        QueryAtom qAny = new QueryAtom(AtomPredicates.any());
        Query query = new Query.Builder()
            .addAtoms(qo, qAny)
            .addBond(qo, qAny, BondPredicates.any())
            .build();

        assertThat(new SubstructureMatcher(molecule).findMatches(query, qo, carbon)).isEmpty();
    }

    @Test
    void anchored_findMatches_throws_when_queryAtom_is_not_part_of_query()
    {
        Molecule molecule = new SmilesParser("C").parse();
        Atom carbon = nthAtom(molecule, 0);

        QueryAtom inQuery = new QueryAtom(AtomPredicates.any());
        QueryAtom notInQuery = new QueryAtom(AtomPredicates.any());
        Query query = new Query.Builder().addAtom(inQuery).build();

        assertThatThrownBy(() -> new SubstructureMatcher(molecule).findMatches(query, notInQuery, carbon))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void substructure_is_a_standalone_molecule_over_the_matched_atoms_and_bonds()
    {
        Molecule molecule = new SmilesParser("CCO").parse();

        QueryAtom first = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom second = new QueryAtom(AtomPredicates.element("C"));
        Query query = new Query.Builder()
            .addAtoms(first, second)
            .addBond(first, second, BondPredicates.any())
            .build();

        Match match = new SubstructureMatcher(molecule).findMatches(query).getFirst();
        Molecule substructure = match.substructure();

        assertThat(substructure.atomCount()).isEqualTo(2);
        assertThat(substructure.bondCount()).isEqualTo(1);
    }

    @Test
    void substructure_includes_every_real_bond_between_matched_atoms_not_just_the_querys()
    {
        // cyclopropane: all three atoms are pairwise bonded, but the query only chains
        // first-second-third, leaving the first-third ring bond undeclared
        Molecule cyclopropane = new SmilesParser("C1CC1").parse();

        QueryAtom first = new QueryAtom(AtomPredicates.any());
        QueryAtom second = new QueryAtom(AtomPredicates.any());
        QueryAtom third = new QueryAtom(AtomPredicates.any());
        Query query = new Query.Builder()
            .addAtoms(first, second, third)
            .addBond(first, second, BondPredicates.any())
            .addBond(second, third, BondPredicates.any())
            .build();

        Match match = new SubstructureMatcher(cyclopropane).findMatches(query).getFirst();
        Molecule substructure = match.substructure();

        assertThat(substructure.atomCount()).isEqualTo(3);
        assertThat(substructure.bondCount()).isEqualTo(3); // the ring-closing bond is included too
    }

    @Test
    void every_declared_stereocentre_must_be_satisfied__not_just_one_of_them()
    {
        // two adjacent fully substituted stereocentres, each stored ANTICLOCKWISE relative to
        // its own real bond order: [N, Br, Cl, Cb] for Ca and [Ca, F, I, O] for Cb
        Molecule molecule = new SmilesParser("N[C@](Br)(Cl)[C@](F)(I)O").parse();

        QueryAtom qN = new QueryAtom(AtomPredicates.element("N"));
        QueryAtom qCa = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom qBr = new QueryAtom(AtomPredicates.element("Br"));
        QueryAtom qCl = new QueryAtom(AtomPredicates.element("Cl"));
        QueryAtom qCb = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom qF = new QueryAtom(AtomPredicates.element("F"));
        QueryAtom qI = new QueryAtom(AtomPredicates.element("I"));
        QueryAtom qO = new QueryAtom(AtomPredicates.element("O"));

        // bonds added in each centre's real order, so no reprojection needed
        Query.Builder skeleton = new Query.Builder()
            .addAtoms(qN, qCa, qBr, qCl, qCb, qF, qI, qO)
            .addBond(qCa, qN, BondPredicates.any())
            .addBond(qCa, qBr, BondPredicates.any())
            .addBond(qCa, qCl, BondPredicates.any())
            .addBond(qCa, qCb, BondPredicates.any())
            .addBond(qCb, qF, BondPredicates.any())
            .addBond(qCb, qI, BondPredicates.any())
            .addBond(qCb, qO, BondPredicates.any());

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);

        assertThat(matcher.findMatches(skeleton
            .setChirality(qCa, Chirality.ANTICLOCKWISE)
            .setChirality(qCb, Chirality.ANTICLOCKWISE)
            .build())).hasSize(1);

        assertThat(matcher.findMatches(skeleton
            .setChirality(qCa, Chirality.CLOCKWISE)
            .setChirality(qCb, Chirality.ANTICLOCKWISE)
            .build())).isEmpty();

        assertThat(matcher.findMatches(skeleton
            .setChirality(qCa, Chirality.ANTICLOCKWISE)
            .setChirality(qCb, Chirality.CLOCKWISE)
            .build())).isEmpty();
    }

    @Test
    void a_double_bond_stereo_requirement_is_unsatisfied_when_an_endpoint_has_no_reference_substituent()
    {
        Molecule molecule = new SmilesParser("C/C=C/C").parse();

        QueryAtom qC = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom qCa = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom qCb = new QueryAtom(AtomPredicates.element("C"));
        Query.Builder skeleton = new Query.Builder()
            .addAtoms(qC, qCa, qCb)
            .addBond(qC, qCa, BondPredicates.any())
            .addBond(qCa, qCb, BondPredicates.order(BondOrder.DOUBLE));

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);

        assertThat(matcher.findMatches(skeleton.build())).isNotEmpty();
        assertThat(matcher.findMatches(skeleton
            .setDoubleBondStereo(qCa, qCb, DoubleBondStereo.OPPOSITE_SIDE) // missing second substituent, so no reference
            .build())).isEmpty();
    }

    @Test
    void matches_stops_at_the_first_match_while_findMatches_enumerates_them_all()
    {
        // benzene: symmetric, so finds 12 matches
        Molecule benzene = new SmilesParser("c1ccccc1").parse();

        AtomicInteger predicateCalls = new AtomicInteger();
        AtomPredicate countedAromaticCarbon = (molecule, atom) ->
        {
            predicateCalls.incrementAndGet();
            return AtomPredicates.aromatic("C").matches(molecule, atom);
        };

        List<QueryAtom> ring = new ArrayList<>();
        Query.Builder builder = new Query.Builder();
        for (int i = 0; i < 6; i++)
        {
            QueryAtom atom = new QueryAtom(countedAromaticCarbon);
            ring.add(atom);
            builder.addAtom(atom);
        }
        for (int i = 0; i < 6; i++)
        {
            builder.addBond(ring.get(i), ring.get((i + 1) % 6), BondPredicates.any());
        }
        Query query = builder.build();
        SubstructureMatcher matcher = new SubstructureMatcher(benzene);

        predicateCalls.set(0);
        assertThat(matcher.findMatches(query)).hasSize(12);
        int whenEnumerating = predicateCalls.get();

        predicateCalls.set(0);
        assertThat(matcher.matches(query)).isTrue();
        int whenStoppingEarly = predicateCalls.get();

        assertThat(whenStoppingEarly).isLessThan(whenEnumerating);
    }

    @Test
    void findDistinctMatches_agrees_with_enumerating_every_match_and_collapsing_afterwards()
    {
        // findDistinctMatches prunes symmetric mappings mid-search, as an optimisation over
        // enumerating every match and collapsing them by matched atom set afterwards.
        // This test demonstrated the two agreeing, and therefore that the optimisation is correct.
        String[] molecules =
        {
            "c1ccccc1", "c1ccc2ccccc2c1", "c1ccc2c(c1)ccc1c2cccc1", "Cc1ccc(C)cc1",
            "C1CCCCC1C1CCCCC1", "CC(C)(C)C", "CC(C)CC(C)C", "ClC(Cl)(Cl)Cl",
            "CC(=O)Nc1ccc(O)cc1", "CCCCCCCC", "OC(=O)c1ccccc1C(=O)O", "C1CC2CCC1CC2",
            "N[C@](Br)(Cl)F", "C/C=C/C", "C[C@H](N)C(=O)O", "OCCO",
        };
        String[] patterns =
        {
            "C", "*", "CC", "C~C", "CCC", "ccc", "c1ccccc1", "C1CCCCC1", "CC(C)C",
            "CC(C)(C)C", "[R]", "[R][R][R]", "[#6][#6][#6]", "C(=O)O", "*(*)(*)*",
            "C=C", "[D3]", "c1ccc2ccccc2c1", "OCC",
        };

        for (String smiles : molecules)
        {
            Molecule molecule = new SmilesParser(smiles).parse();
            for (String smarts : patterns)
            {
                Query query = new SmartsParser(smarts).parseQuery();
                SubstructureMatcher matcher = new SubstructureMatcher(molecule);
                List<Match> distinct = matcher.findDistinctMatches(query);
                Set<Set<Atom>> pruned = atomSets(distinct);

                assertThat(pruned).as(smiles + " / " + smarts).isEqualTo(atomSets(matcher.findMatches(query)));
                // one match per atom set, not merely the right number of them
                assertThat(distinct).as(smiles + " / " + smarts).hasSameSizeAs(pruned);
            }
        }
    }

    @Test
    void findDistinctMatches_does_not_prune_a_match_only_a_permuted_mapping_satisfies()
    {
        // does not prune stereochemistry
        Molecule molecule = new SmilesParser("N[C@](Br)(Cl)F").parse();

        QueryAtom qC = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom qAny1 = new QueryAtom(AtomPredicates.any());
        QueryAtom qAny2 = new QueryAtom(AtomPredicates.any());
        QueryAtom qAny3 = new QueryAtom(AtomPredicates.any());
        QueryAtom qAny4 = new QueryAtom(AtomPredicates.any());
        Query query = new Query.Builder()
            .addAtoms(qC, qAny1, qAny2, qAny3, qAny4)
            .addBond(qC, qAny1, BondPredicates.any())
            .addBond(qC, qAny2, BondPredicates.any())
            .addBond(qC, qAny3, BondPredicates.any())
            .addBond(qC, qAny4, BondPredicates.any())
            .setChirality(qC, Chirality.CLOCKWISE)
            .build();

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);

        assertThat(atomSets(matcher.findMatches(query))).hasSize(1);
        assertThat(matcher.findDistinctMatches(query)).hasSize(1);
    }

    @Test
    void an_unsatisfiable_chirality_is_rejected_before_the_positions_it_does_not_read_are_tried()
    {
        // CN[C@](Br)(Cl)F: stored ANTICLOCKWISE relative to the real bond order [N, Br, Cl, F]
        Molecule molecule = new SmilesParser("CN[C@](Br)(Cl)F").parse();

        AtomicInteger trailingCalls = new AtomicInteger();
        AtomPredicate countedCarbon = (counted, atom) ->
        {
            trailingCalls.incrementAndGet();
            return AtomPredicates.element("C").matches(counted, atom);
        };

        QueryAtom qC = new QueryAtom(AtomPredicates.element("C"));
        QueryAtom qN = new QueryAtom(AtomPredicates.element("N"));
        QueryAtom qBr = new QueryAtom(AtomPredicates.element("Br"));
        QueryAtom qCl = new QueryAtom(AtomPredicates.element("Cl"));
        QueryAtom qF = new QueryAtom(AtomPredicates.element("F"));
        QueryAtom qTrailing = new QueryAtom(countedCarbon);

        // bonds added in the centre's real order, so no reprojection
        Query.Builder skeleton = new Query.Builder()
            .addAtoms(qC, qN, qBr, qCl, qF, qTrailing)
            .addBond(qC, qN, BondPredicates.any())
            .addBond(qC, qBr, BondPredicates.any())
            .addBond(qC, qCl, BondPredicates.any())
            .addBond(qC, qF, BondPredicates.any())
            .addBond(qN, qTrailing, BondPredicates.any());

        SubstructureMatcher matcher = new SubstructureMatcher(molecule);

        assertThat(matcher.matches(skeleton.setChirality(qC, Chirality.ANTICLOCKWISE).build())).isTrue();
        assertThat(trailingCalls.get()).isPositive();

        trailingCalls.set(0);
        assertThat(matcher.matches(skeleton.setChirality(qC, Chirality.CLOCKWISE).build())).isFalse();

        // the declared chirality is settled once the centre and its four substituents are placed,
        // so the branch dies without the trailing position ever being tried
        assertThat(trailingCalls.get()).isZero();
    }

    private static Set<Set<Atom>> atomSets(List<Match> matches)
    {
        Set<Set<Atom>> sets = new HashSet<>();
        matches.forEach(match -> sets.add(match.atoms()));
        return sets;
    }

    private static Atom nthAtom(Molecule molecule, int index)
    {
        int i = 0;
        for (Atom atom : molecule.atoms())
        {
            if (i == index)
            {
                return atom;
            }
            i++;
        }
        throw new IllegalArgumentException("Molecule has no atom at index " + index);
    }
}
