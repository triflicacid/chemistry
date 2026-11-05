package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
            .addAtom(first)
            .addAtom(second)
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
            .addAtom(first)
            .addAtom(second)
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
            .addAtom(a)
            .addAtom(b)
            .addBond(a, b, BondPredicates.ringBond())
            .build();

        QueryAtom c = new QueryAtom(AtomPredicates.any());
        QueryAtom d = new QueryAtom(AtomPredicates.any());
        Query chainBond = new Query.Builder()
            .addAtom(c)
            .addAtom(d)
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
            .addAtom(qBr).addAtom(qC).addAtom(qN).addAtom(qCl).addAtom(qF)
            .addBond(qC, qBr, BondPredicates.any())
            .addBond(qC, qN, BondPredicates.any())
            .addBond(qC, qCl, BondPredicates.any())
            .addBond(qC, qF, BondPredicates.any())
            .setChirality(qC, Chirality.CLOCKWISE)
            .build();
        assertThat(new SubstructureMatcher(molecule).findMatches(matching)).hasSize(1);

        Query nonMatching = new Query.Builder()
            .addAtom(qBr).addAtom(qC).addAtom(qN).addAtom(qCl).addAtom(qF)
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
            .addAtom(qCa).addAtom(qBr).addAtom(qCl).addAtom(qCb).addAtom(qF)
            .addBond(qCa, qBr, BondPredicates.any())
            .addBond(qCa, qCl, BondPredicates.any())
            .addBond(qCa, qCb, BondPredicates.order(BondOrder.DOUBLE))
            .addBond(qCb, qF, BondPredicates.any())
            .setDoubleBondStereo(qCa, qCb, DoubleBondStereo.SAME_SIDE)
            .build();
        assertThat(new SubstructureMatcher(molecule).findMatches(matching)).hasSize(1);

        Query nonMatching = new Query.Builder()
            .addAtom(qCa).addAtom(qBr).addAtom(qCl).addAtom(qCb).addAtom(qF)
            .addBond(qCa, qBr, BondPredicates.any())
            .addBond(qCa, qCl, BondPredicates.any())
            .addBond(qCa, qCb, BondPredicates.order(BondOrder.DOUBLE))
            .addBond(qCb, qF, BondPredicates.any())
            .setDoubleBondStereo(qCa, qCb, DoubleBondStereo.OPPOSITE_SIDE)
            .build();
        assertThat(new SubstructureMatcher(molecule).findMatches(nonMatching)).isEmpty();
    }
}
