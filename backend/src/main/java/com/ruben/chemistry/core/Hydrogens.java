package com.ruben.chemistry.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Moves a molecule's hydrogens between being counted on the atom they hang off and being atoms of their own.
 * <p>
 * This is not the distinction {@link Molecule#implicitHydrogenCount(Atom)} draws, which is whether a count was
 * written or inferred. A hydrogen can be an atom of its own, a written count, or an inferred one.
 */
public final class Hydrogens
{
    private Hydrogens()
    {
    }

    /**
     * Rewrites a molecule so that every hydrogen it counted is an atom bonded to its owner.
     *
     * @param molecule the molecule to rewrite
     * @return the molecule with its hydrogens as atoms, or {@code molecule} itself if it counted none
     * @throws IllegalArgumentException if an atom declares a chirality while counting a number of hydrogens no neighbour order can express, which is any number but one
     */
    public static Molecule asAtoms(Molecule molecule)
    {
        List<Atom> atoms = molecule.atoms();

        Map<Atom, Integer> counted = new IdentityHashMap<>();
        int total = 0;
        for (Atom atom : atoms)
        {
            int count = molecule.totalHydrogenCount(atom);
            counted.put(atom, count);
            total += count;
        }

        if (total == 0)
        {
            return molecule;
        }

        Map<Atom, Atom> replacements = new IdentityHashMap<>();
        for (Atom atom : atoms)
        {
            replacements.put(atom, counted.get(atom) == 0 ? atom : emptied(atom));
        }

        List<Atom> hydrogens = new ArrayList<>(total);
        List<Atom> owners = new ArrayList<>(total);
        for (Atom atom : atoms)
        {
            molecule.hydrogenIsotopeCounts(atom).forEach((massNumber, count) ->
            {
                for (int each = 0; each < count; each++)
                {
                    hydrogens.add(hydrogen(massNumber));
                    owners.add(atom);
                }
            });
        }

        Molecule.Builder builder = new Molecule.Builder();
        atoms.forEach(atom -> builder.addAtom(replacements.get(atom)));
        hydrogens.forEach(builder::addAtom);

        for (Bond bond : molecule.bonds())
        {
            builder.addBond(replacements.get(bond.first()), replacements.get(bond.second()), bond.order());
        }

        // the new bonds trail every original one, which is what leaves each double bond's
        // reference substituent, and so its declared configuration, where it was
        for (int each = 0; each < hydrogens.size(); each++)
        {
            builder.addBond(replacements.get(owners.get(each)), hydrogens.get(each), BondOrder.SINGLE);
        }

        for (Atom atom : atoms)
        {
            Optional<Chirality> declared = molecule.chiralityOf(atom);
            declared.ifPresent(chirality -> builder.setChirality(replacements.get(atom), moved(molecule, atom, counted.get(atom), chirality)));
        }

        for (Bond bond : molecule.bonds())
        {
            molecule.doubleBondStereoOf(bond).ifPresent(stereo -> builder.setDoubleBondStereo(replacements.get(bond.first()), replacements.get(bond.second()), stereo));
        }

        return builder.build();
    }

    /**
     * Finds the hydrogens {@link #asCounts(Molecule)} would absorb, and what each would be counted on.
     *
     * @param molecule the molecule to look in
     * @return each atom that would take a count, by reference, with the hydrogens that would become it
     */
    public static Map<Atom, List<Atom>> absorbedBy(Molecule molecule)
    {
        Map<Atom, List<Atom>> absorbed = new IdentityHashMap<>();
        for (Atom atom : molecule.atoms())
        {
            Atom owner = ownerIfCountable(molecule, atom);
            if (owner != null)
            {
                absorbed.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(atom);
            }
        }

        return absorbed;
    }

    /**
     * Rewrites a molecule so that the hydrogens a count can account for are no longer atoms.
     * <p>
     * A hydrogen is absorbed only where a count says everything the atom did: it must be uncharged, unlabelled by
     * an isotope or atom class, hold no hydrogens of its own, declare no chirality, and hang off exactly one
     * non-hydrogen by a single bond. A hydride, a bridging hydrogen and both halves of dihydrogen stay atoms, and
     * so does a deuterium: SMILES writes a labelled hydrogen as an atom whatever the molecule calls it, so
     * counting it would cost the atom its identity and buy nothing.
     * <p>
     * A count is written out only where leaving it to the valence would say something else: where the owner needs
     * the neighbour slot for a chirality, where a charge, isotope or atom class puts it in brackets that keep only
     * the hydrogens they are told of, or where the valence infers a different number.
     *
     * @param molecule the molecule to rewrite
     * @return the molecule with its countable hydrogens as counts, or {@code molecule} itself if it has none
     * @throws IllegalArgumentException if absorbing a hydrogen would leave a stereocentre declaring a chirality against more than one counted hydrogen, which no neighbour order can express
     */
    public static Molecule asCounts(Molecule molecule)
    {
        Map<Atom, List<Atom>> absorbed = absorbedBy(molecule);
        Map<Atom, Atom> ownerOf = new IdentityHashMap<>();
        absorbed.forEach((owner, hydrogens) -> hydrogens.forEach(hydrogen -> ownerOf.put(hydrogen, owner)));

        if (ownerOf.isEmpty())
        {
            return molecule;
        }

        List<Atom> kept = molecule.atoms().stream().filter(atom -> !ownerOf.containsKey(atom)).toList();

        // what the valence infers once the hydrogens have gone, which is the only way to tell
        // whether writing a count out would say anything the molecule did not already know
        Map<Atom, Atom> inferring = new IdentityHashMap<>();
        kept.forEach(atom -> inferring.put(atom, inferring(atom)));
        Molecule inferred = rebuilding(molecule, kept, inferring, ownerOf).build();

        Map<Atom, Atom> replacements = new IdentityHashMap<>();
        for (Atom atom : kept)
        {
            int total = molecule.totalHydrogenCount(atom) + absorbed.getOrDefault(atom, List.of()).size();

            // the atom's own isotope, charge and class put it in brackets, which keep only the
            // hydrogens they are told of; a labelled hydrogen does not, being written as an atom
            boolean bracketed = atom.charge() != 0 || atom.isotope().isPresent() || atom.atomClass().isPresent() || molecule.chiralityOf(atom).isPresent();
            boolean inferrable = !bracketed && total == inferred.implicitHydrogenCount(inferring.get(atom));

            replacements.put(atom, counting(atom, inferrable ? OptionalInt.empty() : OptionalInt.of(total), atom.hydrogenIsotopes()));
        }

        Molecule.Builder builder = rebuilding(molecule, kept, replacements, ownerOf);

        for (Atom atom : kept)
        {
            Optional<Chirality> declared = molecule.chiralityOf(atom);
            declared.ifPresent(chirality -> builder.setChirality(replacements.get(atom), returned(molecule, atom, absorbed.getOrDefault(atom, List.of()), chirality)));
        }

        for (Bond bond : molecule.bonds())
        {
            molecule.doubleBondStereoOf(bond).ifPresent(stereo -> builder.setDoubleBondStereo(replacements.get(bond.first()), replacements.get(bond.second()), remeasured(molecule, bond, ownerOf, stereo)));
        }

        return builder.build();
    }

    /**
     * Adds the atoms a rewrite keeps and the bonds between them, leaving them in the order they were in, which is
     * the order a chirality is declared against.
     *
     * @param molecule the molecule being rewritten
     * @param kept its atoms that are not being absorbed, in their original order
     * @param mapping what each kept atom is being replaced by
     * @param absorbing the hydrogens being absorbed, as keys
     * @return a builder holding the atoms and bonds, with no stereochemistry declared yet
     */
    private static Molecule.Builder rebuilding(Molecule molecule, List<Atom> kept, Map<Atom, Atom> mapping, Map<Atom, Atom> absorbing)
    {
        Molecule.Builder builder = new Molecule.Builder();
        kept.forEach(atom -> builder.addAtom(mapping.get(atom)));

        for (Bond bond : molecule.bonds())
        {
            if (!absorbing.containsKey(bond.first()) && !absorbing.containsKey(bond.second()))
            {
                builder.addBond(mapping.get(bond.first()), mapping.get(bond.second()), bond.order());
            }
        }
        return builder;
    }

    /**
     * @param molecule the molecule {@code atom} belongs to
     * @param atom an atom that may be countable on its neighbour
     * @return the atom it would be counted on, or {@code null} if a count cannot say everything this atom does
     */
    private static Atom ownerIfCountable(Molecule molecule, Atom atom)
    {
        if (!atom.element().equals("H") || atom.aromatic() || atom.charge() != 0 || atom.isotope().isPresent() || atom.atomClass().isPresent() || molecule.chiralityOf(atom).isPresent())
        {
            return null;
        }

        List<Bond> bonds = molecule.bondsOf(atom);
        if (bonds.size() != 1 || bonds.getFirst().order() != BondOrder.SINGLE || molecule.totalHydrogenCount(atom) != 0)
        {
            return null;
        }

        Atom owner = bonds.getFirst().getOther(atom);

        // absorbing both halves of dihydrogen would leave nothing to count them on
        return owner.element().equals("H") ? null : owner;
    }

    /**
     * @param atom an atom whose counted hydrogens are becoming atoms
     * @return the same atom counting no hydrogens
     */
    private static Atom emptied(Atom atom)
    {
        // a declared zero, not an absent count - we don't want implicit hydrogen to appear!
        return new Atom(atom.element(), atom.aromatic(), atom.charge(), atom.isotope(), OptionalInt.of(0), Map.of(), atom.atomClass());
    }

    /**
     * @param atom an atom whose hydrogen count is to be left to the valence
     * @return the same atom counting no hydrogens of its own
     */
    private static Atom inferring(Atom atom)
    {
        return new Atom(atom.element(), atom.aromatic(), atom.charge(), atom.isotope(), OptionalInt.empty(), Map.of(), atom.atomClass());
    }

    /**
     * @param atom an atom taking hydrogens back as a count
     * @param hydrogenCount the count to carry, or empty to leave it to the valence
     * @param hydrogenIsotopes how many of the hydrogens carry each mass number above one
     * @return the same atom carrying that count
     */
    private static Atom counting(Atom atom, OptionalInt hydrogenCount, Map<Integer, Integer> hydrogenIsotopes)
    {
        return new Atom(atom.element(), atom.aromatic(), atom.charge(), atom.isotope(), hydrogenCount, hydrogenIsotopes, atom.atomClass());
    }

    /**
     * @param massNumber the mass number of the hydrogen to make
     * @return a hydrogen atom of that nuclide
     */
    private static Atom hydrogen(int massNumber)
    {
        Atom.Builder hydrogen = Atom.builder("H");
        return massNumber == 1 ? hydrogen.build() : hydrogen.isotope(massNumber).build();
    }

    /**
     * Re-expresses a stereocentre's declared chirality for the neighbour order it will have
     * once its counted hydrogen is a bond.
     *
     * @param molecule the molecule the stereocentre belongs to
     * @param atom the stereocentre
     * @param hydrogens how many hydrogens {@code atom} counts
     * @param declared {@code atom}'s chirality as {@code molecule} stores it
     * @return the chirality to store against the rewritten molecule
     * @throws IllegalArgumentException if the declaration cannot be expressed once the hydrogens are atoms
     */
    private static Chirality moved(Molecule molecule, Atom atom, int hydrogens, Chirality declared)
    {
        if (hydrogens == 0)
        {
            return declared;
        }

        if (hydrogens > 1 || !molecule.hasImplicitHydrogenSlot(atom))
        {
            throw new IllegalArgumentException("Atom declares a chirality that its " + hydrogens + " counted hydrogens cannot be expressed against: " + atom);
        }

        // the hydrogen led the declared order and its bond now trails the atom's others, so
        // it has crossed one neighbour per bond, and a parity turns over on every crossing
        return molecule.bondsOf(atom).size() % 2 == 0 ? declared : declared.flip();
    }

    /**
     * Re-expresses a stereocentre's declared chirality for the neighbour order it will have once the hydrogen
     * bonded to it is a count.
     *
     * @param molecule the molecule the stereocentre belongs to
     * @param atom the stereocentre
     * @param absorbed the hydrogen atoms of {@code atom} becoming a count
     * @param declared {@code atom}'s chirality as {@code molecule} stores it
     * @return the chirality to store against the rewritten molecule
     * @throws IllegalArgumentException if the declaration cannot be expressed once the hydrogens are a count
     */
    private static Chirality returned(Molecule molecule, Atom atom, List<Atom> absorbed, Chirality declared)
    {
        if (absorbed.isEmpty())
        {
            return declared;
        }

        int total = molecule.totalHydrogenCount(atom) + absorbed.size();
        if (total > 1)
        {
            throw new IllegalArgumentException("Atom declares a chirality that its " + total + " counted hydrogens would not be expressible against: " + atom);
        }

        List<Bond> bonds = molecule.bondsOf(atom);
        int from = 0;
        while (bonds.get(from).getOther(atom) != absorbed.getFirst())
        {
            from++;
        }

        // the slot the hydrogen becomes leads the order, so it crosses one neighbour per place, flipping parity on each 'move'
        return from % 2 == 0 ? declared : declared.flip();
    }

    /**
     * Re-expresses a double bond's declared configuration for the substituents it will be measured from once the
     * hydrogens are counts.
     *
     * @param molecule the molecule the bond belongs to
     * @param bond the double bond
     * @param absorbing the hydrogens being absorbed, as keys
     * @param declared the configuration as {@code molecule} stores it
     * @return the configuration to store against the rewritten molecule
     */
    private static DoubleBondStereo remeasured(Molecule molecule, Bond bond, Map<Atom, Atom> absorbing, DoubleBondStereo declared)
    {
        boolean first = measuresFromElsewhere(molecule, bond, bond.first(), absorbing);
        boolean second = measuresFromElsewhere(molecule, bond, bond.second(), absorbing);

        // measuring one end from its other substituent swaps the configuration and measuring
        // both swaps it back, so only an odd number of changed ends turns it over
        return first ^ second ? declared.flip() : declared;
    }

    /**
     * @param molecule the molecule the bond belongs to
     * @param doubleBond the double bond being resolved
     * @param endpoint one of its endpoints
     * @param absorbing the hydrogens being absorbed, as keys
     * @return whether the endpoint's configuration will be measured from a different substituent once the hydrogens are counts
     */
    private static boolean measuresFromElsewhere(Molecule molecule, Bond doubleBond, Atom endpoint, Map<Atom, Atom> absorbing)
    {
        NeighbourReference measured = molecule.declaredReference(doubleBond, endpoint);

        for (Bond candidate : molecule.bondsOf(endpoint))
        {
            if (candidate != doubleBond && !absorbing.containsKey(candidate.getOther(endpoint)))
            {
                return measured != candidate;
            }
        }

        // every substituent but the hydrogen has gone, and the hydrogen is what the endpoint
        // was measured from already, whether as a bond of its own or as a count
        return false;
    }
}
