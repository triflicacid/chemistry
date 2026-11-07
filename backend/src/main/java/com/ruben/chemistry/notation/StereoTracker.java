package com.ruben.chemistry.notation;

import com.ruben.chemistry.core.BondDirection;
import com.ruben.chemistry.core.DoubleBondStereo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the double bonds and directional single bonds of a structure as it is parsed, and
 * declares each double bond's stereo configuration once both of its flanking directions are known.
 *
 * @param <A> the parsed atom type
 */
public final class StereoTracker<A>
{
    /**
     * Receives a double bond's configuration, once this tracker can work it out.
     *
     * @param <A> the parsed atom type
     */
    @FunctionalInterface
    public interface StereoDeclarer<A>
    {
        /**
         * @param first one endpoint of the double bond
         * @param second the other endpoint
         * @param stereo the configuration, relative to each endpoint's default reference substituent
         */
        void declare(A first, A second, DoubleBondStereo stereo);
    }

    private final StereoDeclarer<A> declarer;

    /** Each atom's neighbours in the order they were written, which is what stereo is declared against. */
    private final Map<A, List<A>> substituentOrderOf = new IdentityHashMap<>();

    /** The other endpoint of each double bond, by either of its atoms. */
    private final Map<A, A> doubleBondPartnerOf = new IdentityHashMap<>();

    /** The directional bond recorded against each atom, as one half of a double bond's stereo. */
    private final Map<A, DirectionalEdge<A>> directionalEdgeOf = new IdentityHashMap<>();

    /**
     * @param declarer where a completed configuration is sent
     */
    public StereoTracker(StereoDeclarer<A> declarer)
    {
        this.declarer = declarer;
    }

    /**
     * Records a bond just parsed between two atoms.
     *
     * @param writtenFirst the earlier-written of the bond's two atoms
     * @param writtenSecond the later-written of the bond's two atoms
     * @param isDoubleBond whether this is a double bond, and so one a configuration can be declared for
     */
    public void recordBond(A writtenFirst, A writtenSecond, boolean isDoubleBond)
    {
        recordSubstituent(writtenFirst, writtenSecond);
        recordSubstituent(writtenSecond, writtenFirst);

        if (isDoubleBond)
        {
            doubleBondPartnerOf.put(writtenFirst, writtenSecond);
            doubleBondPartnerOf.put(writtenSecond, writtenFirst);
        }
    }

    /**
     * Records one directional single bond between two just-bonded atoms, then declares the
     * configuration of either atom's double bond if it now has both directions.
     *
     * @param writtenFirst the earlier-written of the bond's two atoms
     * @param writtenSecond the later-written of the bond's two atoms
     * @param direction the direction as written
     * @return {@code false} if this contradicts a direction already recorded at either atom, in which case nothing is recorded
     */
    public boolean recordDirection(A writtenFirst, A writtenSecond, BondDirection direction)
    {
        DirectionalEdge<A> firstEdge = new DirectionalEdge<>(writtenSecond, direction, true);
        DirectionalEdge<A> secondEdge = new DirectionalEdge<>(writtenFirst, direction, false);
        if (!agreesWithRecorded(writtenFirst, firstEdge) || !agreesWithRecorded(writtenSecond, secondEdge))
        {
            return false;
        }

        directionalEdgeOf.put(writtenFirst, firstEdge);
        directionalEdgeOf.put(writtenSecond, secondEdge);

        reconcileIfDoubleBondAtom(writtenFirst);
        reconcileIfDoubleBondAtom(writtenSecond);
        return true;
    }

    /**
     * Records a direction written at one occurrence of a ring-closure bond, then declares the
     * configuration of either atom's double bond if it now has both directions.
     *
     * @param atom the atom whose occurrence carried the direction
     * @param other the atom at the other end of the ring-closure bond
     * @param direction the direction as written
     * @return {@code false} if this contradicts a direction already recorded at {@code atom}, in which case nothing is recorded
     */
    public boolean recordRingClosureDirection(A atom, A other, BondDirection direction)
    {
        // a ring closure's symbol sits beside its own atom's occurrence, so from that atom's point of view it is the one written first
        DirectionalEdge<A> edge = new DirectionalEdge<>(other, direction, true);
        if (!agreesWithRecorded(atom, edge))
        {
            return false;
        }

        directionalEdgeOf.put(atom, edge);

        reconcileIfDoubleBondAtom(atom);
        reconcileIfDoubleBondAtom(other);
        return true;
    }

    /**
     * Checks a direction about to be recorded for an atom against the one already recorded there.
     * Two of an atom's substituents lie on opposite sides of its double bond, so their directions
     * must differ, while a direction repeated for the same substituent must agree.
     *
     * @param atom the atom the direction is recorded for
     * @param edge the direction about to be recorded
     * @return {@code true} if {@code edge} can stand alongside whatever is recorded already
     */
    private boolean agreesWithRecorded(A atom, DirectionalEdge<A> edge)
    {
        DirectionalEdge<A> recorded = directionalEdgeOf.get(atom);
        if (recorded == null)
        {
            return true;
        }
        boolean sameSubstituent = recorded.other() == edge.other();
        return sameSubstituent == (outwardDirection(recorded) == outwardDirection(edge));
    }

    /**
     * @param edge one atom's end of a directional bond
     * @return {@code edge}'s direction as if written from that atom outwards
     */
    private BondDirection outwardDirection(DirectionalEdge<A> edge)
    {
        return edge.thisAtomWrittenFirst() ? edge.direction() : edge.direction().flip();
    }

    /**
     * Records {@code other} as one of {@code atom}'s substituents, in the order its bonds are parsed.
     *
     * @param atom the atom to record a substituent for
     * @param other the atom just bonded to it
     */
    private void recordSubstituent(A atom, A other)
    {
        substituentOrderOf.computeIfAbsent(atom, ignored -> new ArrayList<>()).add(other);
    }

    /**
     * If {@code atom} is one endpoint of a double bond, attempts to complete that double
     * bond's stereo declaration now that one more of its two reference substituents is known.
     *
     * @param atom the atom to check
     */
    private void reconcileIfDoubleBondAtom(A atom)
    {
        A partner = doubleBondPartnerOf.get(atom);
        if (partner != null)
        {
            tryReconcileDoubleBondStereo(atom, partner);
        }
    }

    /**
     * Reconciles the two directional edges flanking the double bond between {@code first}
     * and {@code second} into its declared stereo configuration.
     * The declared value is reprojected onto the default references.
     *
     * @param first one endpoint of the double bond
     * @param second the other endpoint
     */
    private void tryReconcileDoubleBondStereo(A first, A second)
    {
        DirectionalEdge<A> firstEdge = directionalEdgeOf.get(first);
        DirectionalEdge<A> secondEdge = directionalEdgeOf.get(second);
        // one side alone says nothing; the other may still be written further along the source
        if (firstEdge == null || secondEdge == null)
        {
            return;
        }

        // turn both into directions pointing away from the double bond before comparing them, after which agreeing directions mean the same side
        BondDirection firstOutward = outwardDirection(firstEdge);
        BondDirection secondOutward = outwardDirection(secondEdge);
        DoubleBondStereo relativeToEdges = firstOutward == secondOutward ? DoubleBondStereo.SAME_SIDE : DoubleBondStereo.OPPOSITE_SIDE;

        // the value above is relative to the two directional bonds, but it is stored relative
        // to each endpoint's default reference, so swapping one reference flips it
        A firstDefault = defaultSubstituentOf(first, second);
        A secondDefault = defaultSubstituentOf(second, first);
        boolean flipped = (firstEdge.other() != firstDefault) ^ (secondEdge.other() != secondDefault);

        declarer.declare(first, second, flipped ? relativeToEdges.flip() : relativeToEdges);
    }

    /**
     * @param atom an atom of a double bond
     * @param excluding the double bond's other atom, excluded as a candidate
     * @return the first of {@code atom}'s substituents, other than {@code excluding}, in
     *     parsed order, or {@code null} if it has none (its default reference is then its implicit hydrogen)
     */
    private A defaultSubstituentOf(A atom, A excluding)
    {
        return substituentOrderOf.getOrDefault(atom, List.of()).stream()
            .filter(candidate -> candidate != excluding)
            .findFirst()
            .orElse(null);
    }

    /**
     * One atom's end of a directional single bond.
     *
     * @param other the atom at the other end of this directional bond
     * @param direction the direction as written
     * @param thisAtomWrittenFirst whether the atom this edge is recorded for was written before {@code other}
     * @param <A> the parsed atom type
     */
    private record DirectionalEdge<A>(A other, BondDirection direction, boolean thisAtomWrittenFirst)
    {
    }
}
