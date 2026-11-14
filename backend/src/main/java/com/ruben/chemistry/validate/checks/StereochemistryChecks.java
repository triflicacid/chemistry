package com.ruben.chemistry.validate.checks;

import com.ruben.chemistry.canonical.AtomClasses;
import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.IdentitySet;
import com.ruben.chemistry.validate.Check;
import com.ruben.chemistry.validate.Finding;
import com.ruben.chemistry.validate.Rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reports what a molecule says, or fails to say, about the arrangement of its atoms in space:
 * {@link Rule#STEREO_CENTRE_TOO_FEW_NEIGHBOURS}, {@link Rule#STEREO_CENTRE_NOT_A_CENTRE} and
 * {@link Rule#STEREO_CENTRE_MISSING} for a centre, then
 * {@link Rule#STEREO_DOUBLE_BOND_NOT_STEREOGENIC}, {@link Rule#STEREO_DOUBLE_BOND_IN_SMALL_RING}
 * and {@link Rule#STEREO_DOUBLE_BOND_MISSING} for a double bond.
 */
public final class StereochemistryChecks implements Check
{
    /** The substituents a tetrahedral centre needs before its two arrangements differ, a lone pair holding the fourth position. */
    private static final int MINIMUM_SUBSTITUENTS = 3;

    /** The ligands a centre needs before an unmarked arrangement is worth remarking on. */
    private static final int LIGANDS_OF_A_CENTRE = 4;

    /** The smallest ring that can hold a double bond either way round. */
    private static final int SMALLEST_RING_FOR_EITHER_GEOMETRY = 8;

    /** Electrons left over once an atom has bonded, enough to make a lone pair. */
    private static final int LONE_PAIR = 2;

    @Override
    public Set<Rule> rules()
    {
        return Set.of(
                Rule.STEREO_CENTRE_TOO_FEW_NEIGHBOURS,
                Rule.STEREO_CENTRE_NOT_A_CENTRE,
                Rule.STEREO_CENTRE_MISSING,
                Rule.STEREO_DOUBLE_BOND_NOT_STEREOGENIC,
                Rule.STEREO_DOUBLE_BOND_IN_SMALL_RING,
                Rule.STEREO_DOUBLE_BOND_MISSING);
    }

    @Override
    public void check(Molecule molecule, Consumer<Finding> findings)
    {
        // a ranker of its own, since one shared between calls would hold on to every molecule it ever classified
        AtomClasses classes = new CanonicalRanker().classify(molecule);
        for (Atom atom : molecule.atoms())
        {
            checkCentre(molecule, classes, atom, findings);
        }
        for (Bond bond : molecule.bonds())
        {
            if (bond.order() == BondOrder.DOUBLE)
            {
                checkDoubleBond(molecule, classes, bond, findings);
            }
        }
    }

    /**
     * @param molecule the molecule being checked
     * @param classes its atoms' classes
     * @param atom an atom of it
     * @param findings where to report what is found
     */
    private static void checkCentre(Molecule molecule, AtomClasses classes, Atom atom, Consumer<Finding> findings)
    {
        List<Integer> ligands = ligandClasses(molecule, classes, atom, Optional.empty());
        if (molecule.chiralityOf(atom).isEmpty())
        {
            // an aromatic atom lies in its ring's plane, which leaves it no two arrangements to choose between
            if (ligands.size() == LIGANDS_OF_A_CENTRE && !atom.aromatic() && areAllDifferent(ligands))
            {
                findings.accept(Finding.atAtom(Rule.STEREO_CENTRE_MISSING, atom,
                    atom.element() + " has four different substituents but no arrangement written, so this stands for both"));
            }
            return;
        }

        if (ligands.size() < MINIMUM_SUBSTITUENTS)
        {
            findings.accept(Finding.atAtom(Rule.STEREO_CENTRE_TOO_FEW_NEIGHBOURS, atom,
                atom.element() + " is marked as a stereocentre but has " + ligands.size()
                    + (ligands.size() == 1 ? " substituent" : " substituents") + ", too few to arrange"));
        }
        else if (!areAllDifferent(ligands))
        {
            findings.accept(Finding.atAtom(Rule.STEREO_CENTRE_NOT_A_CENTRE, atom,
                atom.element() + " is marked as a stereocentre but two of its substituents are alike, so the marker says nothing"));
        }
    }

    /**
     * @param molecule the molecule being checked
     * @param classes its atoms' classes
     * @param bond a double bond of it
     * @param findings where to report what is found
     */
    private static void checkDoubleBond(Molecule molecule, AtomClasses classes, Bond bond, Consumer<Finding> findings)
    {
        boolean sided = hasDistinctSides(molecule, classes, bond, bond.first())
            && hasDistinctSides(molecule, classes, bond, bond.second());
        Optional<Molecule> ring = Rings.smallestHolding(molecule, bond);
        boolean free = ring.map(Molecule::atomCount).orElse(Rings.UNRINGED) >= SMALLEST_RING_FOR_EITHER_GEOMETRY;

        if (molecule.doubleBondStereoOf(bond).isEmpty())
        {
            if (sided && free)
            {
                findings.accept(Finding.atBond(Rule.STEREO_DOUBLE_BOND_MISSING, bond,
                    "The double bond has two different substituents at each end but no geometry written, so this stands for both"));
            }
            return;
        }

        if (!sided)
        {
            findings.accept(Finding.atBond(Rule.STEREO_DOUBLE_BOND_NOT_STEREOGENIC, bond,
                "The double bond is given a geometry, but one of its ends has nothing to tell its two sides apart"));
        }
        else if (!free && ring.isPresent() && carriesRingToEitherSide(molecule, bond, ring.get()))
        {
            findings.accept(Finding.atBond(Rule.STEREO_DOUBLE_BOND_IN_SMALL_RING, bond,
                "The double bond is given a geometry that carries its ring off to either side, which a ring of "
                    + ring.get().atomCount() + " cannot come back round from"));
        }
    }

    /**
     * Collects the classes of one atom's ligands, hydrogens included, so that two of them being
     * alike shows up as two of the same class.
     *
     * @param molecule the molecule being checked
     * @param classes its atoms' classes
     * @param atom an atom of it
     * @param exclude a neighbour to leave out, for measuring one end of a bond against its own side only
     * @return one class per ligand, in no particular order
     */
    private static List<Integer> ligandClasses(Molecule molecule, AtomClasses classes, Atom atom, Optional<Atom> exclude)
    {
        List<Integer> ligands = new ArrayList<>();
        for (Bond bond : molecule.bondsOf(atom))
        {
            Atom neighbour = bond.getOther(atom);
            if (exclude.isEmpty() || neighbour != exclude.get())
            {
                ligands.add(classes.classOf(neighbour));
            }
        }
        for (Map.Entry<Integer, Integer> nuclide : molecule.hydrogenIsotopeCounts(atom).entrySet())
        {
            for (int hydrogen = 0; hydrogen < nuclide.getValue(); hydrogen++)
            {
                ligands.add(-nuclide.getKey());
            }
        }
        return ligands;
    }

    /**
     * @param ligands one class per ligand
     * @return whether no two of them are alike
     */
    private static boolean areAllDifferent(List<Integer> ligands)
    {
        return new HashSet<>(ligands).size() == ligands.size();
    }

    /**
     * @param molecule the molecule being checked
     * @param classes its atoms' classes
     * @param bond the double bond
     * @param end the end of it to judge
     * @return whether the end's two sides can be told apart
     */
    private static boolean hasDistinctSides(Molecule molecule, AtomClasses classes, Bond bond, Atom end)
    {
        // a second double bond leaves the end straight, so it has no two sides at all
        if (molecule.bondsOf(end).stream().anyMatch(other -> other != bond && other.order() == BondOrder.DOUBLE))
        {
            return false;
        }

        List<Integer> sides = ligandClasses(molecule, classes, end, Optional.of(bond.getOther(end)));
        // one substituent against a lone pair is as much a pair of sides as two substituents are, as an oxime shows
        return sides.size() == 1 ? hasLonePair(molecule, end) : sides.size() == 2 && areAllDifferent(sides);
    }

    /**
     * @param molecule the molecule being checked
     * @param atom an atom of it
     * @return whether the atom has electrons left over, once bonded, to hold a lone pair
     */
    private static boolean hasLonePair(Molecule molecule, Atom atom)
    {
        if (!Elements.isSymbol(atom.element()))
        {
            return false;
        }

        int electrons = Elements.valenceElectronsOf(Elements.atomicNumberOf(atom.element())) - atom.charge();
        return electrons - molecule.valence(atom) >= LONE_PAIR;
    }

    /**
     * @param molecule the molecule being checked
     * @param bond a double bond carrying a declared configuration
     * @param ring the smallest ring holding it
     * @return whether the declared geometry puts the ring's two continuations on opposite sides
     */
    private static boolean carriesRingToEitherSide(Molecule molecule, Bond bond, Molecule ring)
    {
        Set<Atom> members = IdentitySet.copyOf(ring.atoms());
        Optional<Bond> first = continuation(molecule, members, bond, bond.first());
        Optional<Bond> second = continuation(molecule, members, bond, bond.second());
        if (first.isEmpty() || second.isEmpty())
        {
            return false;
        }
        return molecule.doubleBondStereoOf(bond, first.get(), second.get())
            .filter(configuration -> configuration == DoubleBondStereo.OPPOSITE_SIDE)
            .isPresent();
    }

    /**
     * @param molecule the molecule being checked
     * @param members the ring's atoms
     * @param bond the double bond the ring runs along
     * @param end the end of it to leave the ring by
     * @return the bond carrying the ring on from that end, or empty if the ring does not go on from there
     */
    private static Optional<Bond> continuation(Molecule molecule, Set<Atom> members, Bond bond, Atom end)
    {
        return molecule.bondsOf(end).stream()
            .filter(other -> other != bond && members.contains(other.getOther(end)))
            .findFirst();
    }
}
