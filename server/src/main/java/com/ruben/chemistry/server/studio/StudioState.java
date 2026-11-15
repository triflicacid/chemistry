package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.core.PiSystem;
import com.ruben.chemistry.render.ElementColours;
import com.ruben.chemistry.space.ElementData;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Vec3;
import com.ruben.chemistry.util.IdentitySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Everything a client needs to draw a session, as a {@code session.state} message.
 *
 * @param type always {@code session.state}
 * @param sequence the sequence number of the message this answers, or {@code null} if it answers none
 * @param sessionId the session this describes
 * @param version the version of the session this describes
 * @param sourceMoleculeId the stored molecule the session was opened from
 * @param savedMoleculeId the molecule the session last saved, or {@code null} if it has saved none
 * @param smiles the molecule as a chemist would write it, with hydrogens counted
 * @param atoms every atom of the working graph, counted hydrogens included
 * @param bonds every bond of the working graph
 * @param fragments the disconnected parts of the molecule
 * @param locked the atoms and bonds that refuse an edit
 * @param transform where the molecule sits in the scene; the atoms' positions are in the molecule's own frame
 * @param canUndo whether there is an edit to take back
 * @param canRedo whether there is an undone edit to make again
 */
public record StudioState(
        String type,
        Long sequence,
        String sessionId,
        long version,
        String sourceMoleculeId,
        String savedMoleculeId,
        String smiles,
        List<AtomState> atoms,
        List<BondState> bonds,
        List<FragmentState> fragments,
        List<String> locked,
        Pose transform,
        boolean canUndo,
        boolean canRedo)
{
    /** The type this message is sent as. */
    public static final String TYPE = "session.state";

    /**
     * One atom of the working graph.
     *
     * @param id what the session calls it
     * @param element its element symbol
     * @param isotope its mass number, or 0 if it has none of its own
     * @param charge its formal charge
     * @param aromatic whether it is written aromatic
     * @param position where it is, in angstroms
     * @param colour the usual colour for the element, as {@code #rrggbb}
     * @param radius the element's van der Waals radius, in angstroms
     * @param wasImplicit whether it is a hydrogen the source molecule counted rather than wrote
     */
    public record AtomState(
            String id,
            String element,
            int isotope,
            int charge,
            boolean aromatic,
            Vec3 position,
            String colour,
            double radius,
            boolean wasImplicit)
    {
    }

    /**
     * One bond of the working graph.
     *
     * @param id what the session calls it
     * @param first the ID of one endpoint atom
     * @param second the ID of the other endpoint atom
     * @param order the bond order
     * @param aromatic whether the bond lies in an aromatic system
     */
    public record BondState(String id, String first, String second, BondOrder order, boolean aromatic)
    {
    }

    /**
     * One disconnected part of the molecule.
     *
     * @param key what this part is called
     * @param name what to show it as
     * @param atoms the IDs of the atoms in it
     */
    public record FragmentState(String key, String name, List<String> atoms)
    {
    }

    /**
     * @param session the session to describe
     * @param sequence the sequence number of the message being answered, or {@code null} if none
     * @return its full state
     */
    public static StudioState of(StudioSession session, Long sequence)
    {
        Molecule molecule = session.molecule();
        Set<Bond> aromatic = aromaticBonds(molecule);

        List<AtomState> atoms = new ArrayList<>();
        for (Atom atom : molecule.atoms())
        {
            String id = session.atomId(atom);
            int number = ElementData.numberOf(atom.element());
            atoms.add(new AtomState(
                    id,
                    atom.element(),
                    atom.isotope().orElse(0),
                    atom.charge(),
                    atom.aromatic(),
                    session.positionOf(id),
                    ElementColours.of(atom.element()).hex(),
                    ElementData.vanDerWaalsRadius(number),
                    session.wasImplicit(id)));
        }

        List<BondState> bonds = new ArrayList<>();
        for (Bond bond : molecule.bonds())
        {
            bonds.add(new BondState(
                    session.bondId(bond),
                    session.atomId(bond.first()),
                    session.atomId(bond.second()),
                    bond.order(),
                    aromatic.contains(bond)));
        }

        List<Molecule> parts = molecule.fragments();
        List<FragmentState> fragments = new ArrayList<>();
        for (int part = 0; part < parts.size(); part++)
        {
            // fragments share their atoms with the molecule they came from, so the session
            // already knows what each one is called
            fragments.add(new FragmentState(
                    "f" + (part + 1),
                    "Fragment " + (part + 1),
                    parts.get(part).atoms().stream().map(session::atomId).toList()));
        }

        return new StudioState(
                TYPE,
                sequence,
                session.sessionId(),
                session.version(),
                session.sourceMoleculeId(),
                session.savedMoleculeId(),
                session.smiles(),
                atoms,
                bonds,
                fragments,
                List.copyOf(session.locked()),
                session.pose(),
                session.canUndo(),
                session.canRedo());
    }

    /**
     * @param molecule the working graph
     * @return the bonds lying in one of its aromatic systems, by reference
     */
    private static Set<Bond> aromaticBonds(Molecule molecule)
    {
        Set<Bond> aromatic = IdentitySet.of();

        for (PiSystem system : molecule.piSystems())
        {
            if (system.isAromatic())
            {
                aromatic.addAll(system.bonds());
            }
        }

        return aromatic;
    }
}
