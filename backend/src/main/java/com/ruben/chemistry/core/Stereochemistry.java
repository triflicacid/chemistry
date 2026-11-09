package com.ruben.chemistry.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything one molecule declares about its stereochemistry, stated against atom indices.
 * <p>
 * A {@link Molecule} declares each configuration relative to the order it was built in, which two
 * builds of the same molecule need not agree on. This restates them against atom indices and reads
 * them back against any colouring of the atoms, so a caller working in atom classes or canonical
 * ranks never has to know the build order.
 * <p>
 * Reading one back means re-ordering its participants by colour and tracking what that does to the
 * configuration. A parity flips where sorting its centre's ligands by colour is an odd permutation
 * of the order the parity was declared against. A double bond's configuration is measured from the
 * lowest-coloured substituent at each end, an implicit hydrogen leading where there is one, and
 * flips where exactly one of those two differs from the substituent it was declared against.
 * <p>
 * Either reading needs the colouring to tell the participants apart. Ligands sharing a colour leave
 * their order undecided, as does a tie for lowest-coloured substituent, and the configuration stays
 * unreadable until a finer colouring separates them.
 */
public final class Stereochemistry
{
    /** Role bit marking a stereocentre. */
    public static final int STEREOCENTRE = 1;

    /** Role bit marking an end of a double bond carrying a declared configuration. */
    public static final int STEREO_BOND_ENDPOINT = 2;

    /** The answer {@link #doubleBondAt} gives for a pair of atoms with no configuration between them. */
    public static final int NO_DOUBLE_BOND = -1;

    /** The code of an atom or bond carrying no declared configuration. */
    private static final int NONE = 0;

    /** The code of a declared configuration the colouring cannot read. */
    private static final int UNREADABLE = 1;

    /** The code of the first readable configuration, which the configuration's own ordinal is added to. */
    private static final int READABLE = 2;

    /** How many ligands a tetrahedral parity describes the arrangement of. */
    private static final int LIGANDS = 4;

    /** Marks an end of a double bond a colouring leaves with no reference substituent. */
    private static final int NO_REFERENCE = Integer.MIN_VALUE;

    private final List<StereoDeclaration> declarations;

    private final int[] roleByAtom;

    /** Each stereocentre's declaration, or {@code null} where an atom declares no parity. */
    private final StereoDeclaration.Tetrahedral[] parityByCentre;

    /** Every declared double bond, in the order {@link #doubleBondAt} numbers them. */
    private final List<DeclaredDoubleBond> doubleBonds;

    /** Which declared double bond joins a pair of atoms, by {@link StereoDeclaration.DoubleBond#pairKey}. */
    private final Map<Long, Integer> doubleBondByPair;

    private Stereochemistry(int atomCount)
    {
        this.declarations = new ArrayList<>();
        this.roleByAtom = new int[atomCount];
        this.parityByCentre = new StereoDeclaration.Tetrahedral[atomCount];
        this.doubleBonds = new ArrayList<>();
        this.doubleBondByPair = new HashMap<>();
    }

    /**
     * @param atomCount how many atoms the molecule has
     * @return stereochemistry declaring nothing
     */
    public static Stereochemistry none(int atomCount)
    {
        return new Stereochemistry(atomCount);
    }

    /**
     * @param molecule the molecule to read
     * @return what {@code molecule} declares, indexed as its {@link Molecule#atoms()} are
     */
    static Stereochemistry of(Molecule molecule)
    {
        List<Atom> atoms = molecule.atoms();
        Map<Atom, Integer> indexOf = new IdentityHashMap<>();
        for (int atom = 0; atom < atoms.size(); atom++)
        {
            indexOf.put(atoms.get(atom), atom);
        }

        Stereochemistry stereochemistry = new Stereochemistry(atoms.size());
        for (int centre = 0; centre < atoms.size(); centre++)
        {
            stereochemistry.readParity(molecule, indexOf, atoms.get(centre), centre);
        }
        for (Bond bond : molecule.bonds())
        {
            // only a double bond can carry a configuration, so the rest need not be asked
            if (bond.order() == BondOrder.DOUBLE)
            {
                stereochemistry.readConfiguration(molecule, indexOf, bond);
            }
        }
        return stereochemistry;
    }

    /**
     * @return {@code true} if nothing at all is declared
     */
    public boolean isEmpty()
    {
        return declarations.isEmpty();
    }

    /**
     * @return every configuration declared, in a fixed order
     */
    public List<StereoDeclaration> declarations()
    {
        return Collections.unmodifiableList(declarations);
    }

    /**
     * @param atom an atom's index
     * @return a mask of the parts {@code atom} plays: {@link #STEREOCENTRE}, {@link #STEREO_BOND_ENDPOINT}, or zero for neither
     */
    public int roleOf(int atom)
    {
        return roleByAtom[atom];
    }

    /**
     * @param atom an atom's index
     * @return whether {@code atom} declares a parity this can read
     */
    public boolean declaresParity(int atom)
    {
        return parityByCentre[atom] != null;
    }

    /**
     * @param centre a stereocentre's atom index
     * @param colourOf each atom's colour, by atom index, no colour being negative
     * @return {@code centre}'s declared parity against that colouring, or empty if it declares none or the colouring cannot read it
     */
    public Optional<Chirality> parityOf(int centre, int[] colourOf)
    {
        StereoDeclaration.Tetrahedral declared = parityByCentre[centre];
        return declared == null ? Optional.empty() : declared.parityAgainst(colourOf);
    }

    /**
     * @param atom one bonded atom's index
     * @param neighbour the other bonded atom's index
     * @return which declared double bond joins them, or {@link #NO_DOUBLE_BOND} if none does
     */
    public int doubleBondAt(int atom, int neighbour)
    {
        return doubleBondByPair.getOrDefault(StereoDeclaration.DoubleBond.pairKey(atom, neighbour), NO_DOUBLE_BOND);
    }

    /**
     * Reads a double bond's configuration against the reference substituent each of its ends gets from a colouring
     *
     * @param doubleBond a declared double bond, as {@link #doubleBondAt} numbers them
     * @param colourOf each atom's colour, by atom index, no colour being negative
     * @return the configuration against those references, or empty if the colouring leaves either end's choice of reference open
     */
    public Optional<DoubleBondStereo> configurationOf(int doubleBond, int[] colourOf)
    {
        DeclaredDoubleBond declared = doubleBonds.get(doubleBond);
        int first = referenceAgainst(declared.first(), colourOf);
        int second = referenceAgainst(declared.second(), colourOf);
        if (first == NO_REFERENCE || second == NO_REFERENCE)
        {
            return Optional.empty();
        }
        return Optional.of(declared.declaration().configurationAgainst(first, second));
    }

    /**
     * Encodes {@link #parityOf} into a compact code for cheaply comparing parity.
     *
     * @param centre an atom's index
     * @param colourOf each atom's colour, by atom index, no colour being negative
     * @return a code for {@code centre}'s declared parity against that colouring
     */
    public int parityCodeOf(int centre, int[] colourOf)
    {
        if (!declaresParity(centre))
        {
            return NONE;
        }

        Optional<Chirality> parity = parityOf(centre, colourOf);
        return parity.map(chirality -> READABLE + chirality.ordinal()).orElse(UNREADABLE);
    }

    /**
     * Encodes {@link #configurationOf} the way {@link #parityCodeOf} encodes a parity, and takes
     * {@link #NO_DOUBLE_BOND} for a bond carrying no configuration.
     *
     * @param doubleBond a declared double bond, as {@link #doubleBondAt} numbers them, or {@link #NO_DOUBLE_BOND}
     * @param colourOf each atom's colour, by atom index, no colour being negative
     * @return a code for that bond's declared configuration against that colouring
     */
    public int configurationCodeOf(int doubleBond, int[] colourOf)
    {
        if (doubleBond == NO_DOUBLE_BOND)
        {
            return NONE;
        }

        Optional<DoubleBondStereo> configuration = configurationOf(doubleBond, colourOf);
        return configuration.map(doubleBondStereo -> READABLE + doubleBondStereo.ordinal()).orElse(UNREADABLE);
    }

    /**
     * @return how many distinct codes {@link #parityCodeOf} and {@link #configurationCodeOf} use
     */
    public static int codeCount()
    {
        return READABLE + Math.max(Chirality.values().length, DoubleBondStereo.values().length);
    }

    /**
     * Records one atom's declared parity, if it has one that can be read.
     *
     * @param molecule the molecule being read
     * @param indexOf each atom's index, by atom reference
     * @param atom the atom to read
     * @param centre {@code atom}'s index
     */
    private void readParity(Molecule molecule, Map<Atom, Integer> indexOf, Atom atom, int centre)
    {
        Optional<Chirality> declared = molecule.chiralityOf(atom);
        if (declared.isEmpty())
        {
            return;
        }

        int[] ligands = declaredLigands(molecule, indexOf, atom);
        // a parity is the arrangement of four ligands, and describes nothing without all four
        if (ligands.length != LIGANDS)
        {
            return;
        }

        StereoDeclaration.Tetrahedral declaration = new StereoDeclaration.Tetrahedral(centre, declared.get(), ligands);
        parityByCentre[centre] = declaration;
        roleByAtom[centre] |= STEREOCENTRE;
        declarations.add(declaration);
    }

    /**
     * Records one double bond's declared configuration, if it has one.
     *
     * @param molecule the molecule being read
     * @param indexOf each atom's index, by atom reference
     * @param bond the double bond to read
     */
    private void readConfiguration(Molecule molecule, Map<Atom, Integer> indexOf, Bond bond)
    {
        Optional<DoubleBondStereo> declared = molecule.doubleBondStereoOf(bond);
        if (declared.isEmpty())
        {
            return;
        }

        int firstAtom = indexOf.get(bond.first());
        int secondAtom = indexOf.get(bond.second());
        Endpoint first = endpointOf(molecule, indexOf, bond, bond.first());
        Endpoint second = endpointOf(molecule, indexOf, bond, bond.second());

        StereoDeclaration.DoubleBond declaration = new StereoDeclaration.DoubleBond(
            firstAtom,
            secondAtom,
            declared.get(),
            first.declaredReference(),
            second.declaredReference());

        doubleBondByPair.put(StereoDeclaration.DoubleBond.pairKey(firstAtom, secondAtom), doubleBonds.size());
        doubleBonds.add(new DeclaredDoubleBond(declaration, first, second));
        roleByAtom[firstAtom] |= STEREO_BOND_ENDPOINT;
        roleByAtom[secondAtom] |= STEREO_BOND_ENDPOINT;
        declarations.add(declaration);
    }

    /**
     * @param molecule the molecule being read
     * @param indexOf each atom's index, by atom reference
     * @param atom the stereocentre to read
     * @return {@code atom}'s neighbours in the order {@link Molecule#chiralityOf(Atom)} is relative
     *     to, any implicit hydrogen leading as {@link StereoDeclaration#IMPLICIT_HYDROGEN}
     */
    private static int[] declaredLigands(Molecule molecule, Map<Atom, Integer> indexOf, Atom atom)
    {
        List<NeighbourReference> declaredOrder = molecule.declaredNeighbourOrder(atom);
        int[] ligands = new int[declaredOrder.size()];
        for (int ligand = 0; ligand < ligands.length; ligand++)
        {
            ligands[ligand] = indexOf(indexOf, atom, declaredOrder.get(ligand));
        }
        return ligands;
    }

    /**
     * @param molecule the molecule being read
     * @param indexOf each atom's index, by atom reference
     * @param doubleBond the bond carrying the configuration
     * @param atom the end of {@code doubleBond} to describe
     * @return what reading {@code atom}'s end of the configuration takes
     */
    private static Endpoint endpointOf(Molecule molecule, Map<Atom, Integer> indexOf, Bond doubleBond, Atom atom)
    {
        List<Bond> bonds = molecule.bondsOf(atom);
        int[] substituents = new int[bonds.size() - 1];
        int at = 0;
        for (Bond bond : bonds)
        {
            if (bond != doubleBond)
            {
                substituents[at++] = indexOf.get(bond.getOther(atom));
            }
        }

        int declaredReference = indexOf(indexOf, atom, molecule.declaredReference(doubleBond, atom));
        return new Endpoint(declaredReference, molecule.hasImplicitHydrogenSlot(atom), substituents);
    }

    /**
     * @param indexOf each atom's index, by atom reference
     * @param atom the atom the slot belongs to
     * @param neighbour one of {@code atom}'s neighbour slots
     * @return the atom across that slot, or {@link StereoDeclaration#IMPLICIT_HYDROGEN} for an implicit hydrogen
     */
    private static int indexOf(Map<Atom, Integer> indexOf, Atom atom, NeighbourReference neighbour)
    {
        return neighbour instanceof Bond bond
            ? indexOf.get(bond.getOther(atom))
            : StereoDeclaration.IMPLICIT_HYDROGEN;
    }

    /**
     * Picks the substituent one end of a double bond is measured against, taking that end's
     * neighbours in the order the colouring puts them in.
     *
     * @param endpoint the end to measure
     * @param colourOf each atom's colour, by atom index
     * @return the chosen substituent's index, or {@link #NO_REFERENCE} if the colouring leaves the choice open
     */
    private static int referenceAgainst(Endpoint endpoint, int[] colourOf)
    {
        // an implicit hydrogen leads its atom's neighbours, so it is the reference wherever an atom has one
        if (endpoint.hasImplicitHydrogen())
        {
            return StereoDeclaration.IMPLICIT_HYDROGEN;
        }

        int reference = NO_REFERENCE;
        boolean tied = false;
        for (int substituent : endpoint.substituents())
        {
            if (reference == NO_REFERENCE || colourOf[substituent] < colourOf[reference])
            {
                reference = substituent;
                tied = false;
            }
            else if (colourOf[substituent] == colourOf[reference])
            {
                tied = true;
            }
        }
        return tied ? NO_REFERENCE : reference;
    }

    /**
     * One declared double bond, together with what reading it against a colouring takes.
     *
     * @param declaration the configuration as declared
     * @param first the bond's first end
     * @param second the bond's second end
     */
    private record DeclaredDoubleBond(StereoDeclaration.DoubleBond declaration, Endpoint first, Endpoint second)
    {
    }

    /**
     * One end of a double bond carrying a declared configuration.
     *
     * @param declaredReference the substituent the molecule declares the configuration against
     * @param hasImplicitHydrogen whether the end has an implicit hydrogen, which leads its substituents
     * @param substituents the end's neighbours other than the opposite end
     */
    private record Endpoint(int declaredReference, boolean hasImplicitHydrogen, int[] substituents)
    {
    }
}
