package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Chirality;
import com.ruben.chemistry.core.DoubleBondStereo;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.StereoDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A molecule restated for embedding.
 */
final class Topology
{
    /** The smallest angle, in degrees, hypervalent and metal centres allow between two ligands. */
    static final double HYPERVALENT_MINIMUM = 85;

    /**
     * A bond between two nodes.
     *
     * @param first one node
     * @param second the other node
     * @param order the bond order
     * @param source the index of the molecule bond this is, or -1 for a bond to an implied hydrogen
     */
    record Link(int first, int second, BondOrder order, int source)
    {
    }

    /**
     * A declared tetrahedral centre.
     *
     * @param centre the centre node
     * @param ligands four ligand nodes, so that the triple product of the last three, taken from the centre, has the sign of {@code sign}
     * @param sign the sign that triple product must have
     */
    record Centre(int centre, int[] ligands, int sign)
    {
    }

    /**
     * A double bond with a declared configuration.
     *
     * @param first one end
     * @param second the other end
     * @param firstReference the reference substituent of {@code first}
     * @param secondReference the reference substituent of {@code second}
     * @param sameSide whether the references lie on the same side
     */
    record Configured(int first, int second, int firstReference, int secondReference, boolean sameSide)
    {
    }

    /**
     * A ring of the molecule.
     *
     * @param nodes the nodes in the ring
     * @param planar whether every atom of the ring is planar, so the ring is flat
     */
    record Ring(Set<Integer> nodes, boolean planar)
    {
        int size()
        {
            return nodes.size();
        }
    }

    final Molecule molecule;

    final int count;

    final String[] element;

    final int[] atomicNumber;

    /** The mass number of each node, or 0 if unspecified. */
    final int[] isotope;

    /** The molecule atom each node is, or the atom a hydrogen hangs from. */
    final int[] owner;

    /** Whether each node is a hydrogen implied by a hydrogen count rather than a written atom. */
    final boolean[] implied;

    /** The formal charge of each node. */
    final int[] charge;

    final List<Link> links = new ArrayList<>();

    final int[][] neighbours;

    final Map<Long, Link> linkBetween = new HashMap<>();

    final int[] lonePairs;

    /** Whether the node's neighbours lie in a plane with it. */
    final boolean[] planar;

    /** Whether the node is a hypervalent or metal centre, whose angles are only bounded below. */
    final boolean[] flexible;

    /** The ideal angle at each node between two of its bonds, in degrees. */
    final double[] idealAngle;

    final List<Ring> rings = new ArrayList<>();

    final List<Centre> centres = new ArrayList<>();

    final List<Configured> configured = new ArrayList<>();

    /**
     * @param molecule the molecule to restate
     */
    Topology(Molecule molecule)
    {
        this.molecule = molecule;
        List<Atom> atoms = molecule.atoms();
        Map<Atom, Integer> indexOf = new IdentityHashMap<>();
        for (int atom = 0; atom < atoms.size(); atom++)
        {
            indexOf.put(atoms.get(atom), atom);
        }

        int implicit = 0;
        for (Atom atom : atoms)
        {
            implicit += molecule.totalHydrogenCount(atom);
        }
        count = atoms.size() + implicit;
        element = new String[count];
        atomicNumber = new int[count];
        isotope = new int[count];
        owner = new int[count];
        implied = new boolean[count];
        charge = new int[count];

        List<List<Integer>> adjacent = new ArrayList<>();
        for (int node = 0; node < count; node++)
        {
            adjacent.add(new ArrayList<>());
        }
        for (int atom = 0; atom < atoms.size(); atom++)
        {
            Atom source = atoms.get(atom);
            element[atom] = source.element();
            atomicNumber[atom] = ElementData.numberOf(source.element());
            isotope[atom] = source.isotope().orElse(0);
            owner[atom] = atom;
            charge[atom] = source.charge();
        }
        List<Bond> bonds = molecule.bonds();
        for (int bond = 0; bond < bonds.size(); bond++)
        {
            Bond source = bonds.get(bond);
            addLink(adjacent, new Link(indexOf.get(source.first()), indexOf.get(source.second()), source.order(), bond));
        }

        int next = atoms.size();
        int[] firstHydrogen = new int[atoms.size()];
        for (int atom = 0; atom < atoms.size(); atom++)
        {
            firstHydrogen[atom] = next;
            for (Map.Entry<Integer, Integer> isotopeCount : molecule.hydrogenIsotopeCounts(atoms.get(atom)).entrySet())
            {
                for (int each = 0; each < isotopeCount.getValue(); each++)
                {
                    element[next] = "H";
                    atomicNumber[next] = 1;
                    isotope[next] = isotopeCount.getKey() == 1 ? 0 : isotopeCount.getKey();
                    owner[next] = atom;
                    implied[next] = true;
                    addLink(adjacent, new Link(atom, next, BondOrder.SINGLE, -1));
                    next++;
                }
            }
        }

        neighbours = new int[count][];
        for (int node = 0; node < count; node++)
        {
            neighbours[node] = adjacent.get(node).stream().mapToInt(Integer::intValue).toArray();
        }

        lonePairs = new int[count];
        planar = new boolean[count];
        flexible = new boolean[count];
        idealAngle = new double[count];
        for (int node = 0; node < count; node++)
        {
            type(node, node < atoms.size() ? atoms.get(node) : null);
        }

        for (Molecule ring : molecule.rings())
        {
            Set<Integer> nodes = new java.util.TreeSet<>();
            boolean flat = true;
            for (Atom atom : ring.atoms())
            {
                Integer node = indexOf.get(atom);
                if (node == null)
                {
                    throw new IllegalStateException("A ring holds an atom the molecule does not");
                }
                nodes.add(node);
                flat &= planar[node];
            }
            rings.add(new Ring(nodes, flat));
        }

        readStereochemistry(firstHydrogen);
    }

    /**
     * @param first one node
     * @param second another node
     * @return a key identifying the pair, whichever way round it is given
     */
    static long pair(int first, int second)
    {
        return StereoDeclaration.DoubleBond.pairKey(first, second);
    }

    /**
     * @param first one node
     * @param second another node
     * @return the link joining them, or {@code null} if they are not bonded
     */
    Link link(int first, int second)
    {
        return linkBetween.get(pair(first, second));
    }

    /**
     * @param node a node
     * @return whether it is anything but hydrogen
     */
    boolean isHeavy(int node)
    {
        return atomicNumber[node] != 1;
    }

    private void addLink(List<List<Integer>> adjacent, Link link)
    {
        links.add(link);
        linkBetween.put(pair(link.first(), link.second()), link);
        adjacent.get(link.first()).add(link.second());
        adjacent.get(link.second()).add(link.first());
    }

    /**
     * Works out how many lone pairs a node has and so how its bonds are arranged.
     */
    private void type(int node, Atom source)
    {
        int bonds = neighbours[node].length;
        int number = atomicNumber[node];
        int electrons = ElementData.valenceElectrons(number);
        if (source != null && electrons >= 0)
        {
            lonePairs[node] = Math.max(0, (electrons - source.charge() - molecule.valence(source)) / 2);
        }
        else if (source == null)
        {
            lonePairs[node] = 0;
        }

        boolean aromatic = source != null && source.aromatic();
        int domains = bonds + lonePairs[node];
        int period = ElementData.period(number);

        // a lone pair on nitrogen or oxygen next to a multiple bond is shared with it, which flattens the atom
        if (!aromatic && electrons >= 0 && period == 2 && lonePairs[node] > 0 && domains == 4 && bonds <= 3
            && (number == 7 || number == 8) && neighbourHasMultipleBond(node))
        {
            domains = 3;
            lonePairs[node] = Math.max(0, 3 - bonds);
            planar[node] = true;
        }

        if (bonds <= 1)
        {
            idealAngle[node] = 180;
            return;
        }

        if (electrons < 0)
        {
            // metals: coordination decides, and only the tetrahedron is ideal enough to state
            if (bonds == 4)
            {
                idealAngle[node] = 109.47;
            }
            else
            {
                flexible[node] = true;
                idealAngle[node] = bonds == 2 ? 180 : 109.47;
            }
            return;
        }

        if (aromatic)
        {
            planar[node] = true;
            idealAngle[node] = 120;
            return;
        }

        boolean heavy = period >= 3;
        if (domains == 2 || (bonds == 2 && domains >= 5))
        {
            idealAngle[node] = 180;
        }
        else if (domains == 3)
        {
            planar[node] = true;
            idealAngle[node] = bonds == 2 ? (heavy ? 110 : 117) : 120;
        }
        else if (domains == 4)
        {
            idealAngle[node] = switch (lonePairs[node])
            {
                case 0 -> 109.47;
                case 1 -> heavy ? 98 : 107;
                default -> heavy ? 96 : 104.5;
            };
        }
        else
        {
            flexible[node] = true;
            idealAngle[node] = 90;
        }
    }

    private boolean neighbourHasMultipleBond(int node)
    {
        for (int neighbour : neighbours[node])
        {
            for (int other : neighbours[neighbour])
            {
                if (other == node)
                {
                    continue;
                }
                BondOrder order = link(neighbour, other).order();
                if (order != BondOrder.SINGLE)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void readStereochemistry(int[] firstHydrogen)
    {
        for (StereoDeclaration declaration : molecule.stereochemistry().declarations())
        {
            if (declaration instanceof StereoDeclaration.Tetrahedral tetrahedral)
            {
                if (tetrahedral.ligands().length != 4)
                {
                    continue;
                }
                int centre = tetrahedral.centre();
                int[] ligands = new int[4];
                for (int slot = 0; slot < 4; slot++)
                {
                    int ligand = tetrahedral.ligands()[slot];
                    ligands[slot] = ligand == StereoDeclaration.IMPLICIT_HYDROGEN ? firstHydrogen[centre] : ligand;
                }
                // a parity is the sense the last three ligands go round when seen from the first, and
                // anticlockwise from the viewer is a negative triple product of the three
                centres.add(new Centre(centre, ligands, tetrahedral.parity() == Chirality.CLOCKWISE ? 1 : -1));
            }
            else if (declaration instanceof StereoDeclaration.DoubleBond bond)
            {
                int firstReference = bond.firstReference() == StereoDeclaration.IMPLICIT_HYDROGEN
                    ? firstHydrogen[bond.firstAtom()] : bond.firstReference();
                int secondReference = bond.secondReference() == StereoDeclaration.IMPLICIT_HYDROGEN
                    ? firstHydrogen[bond.secondAtom()] : bond.secondReference();
                configured.add(new Configured(bond.firstAtom(), bond.secondAtom(), firstReference, secondReference,
                    bond.configuration() == DoubleBondStereo.SAME_SIDE));
            }
        }
    }
}
