package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.server.molecule.Camera;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Quaternion;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class StudioRequests
{
    private StudioRequests()
    {
    }

    /**
     * @param moleculeId the stored molecule to open a session on
     * @param conformers how many conformers to embed if it has no shape yet (optional, default one)
     */
    public record OpenRequest(String moleculeId, Integer conformers)
    {
    }

    /**
     * @param sessionId the session to send state for
     * @param sinceVersion the version the client holds, which it reports so a gap is visible in the logs
     */
    public record ResyncRequest(String sessionId, Long sinceVersion)
    {
    }

    /**
     * @param sessionId the session to rebuild
     * @param baseVersion the version the client was looking at when it wrote the SMILES, so that a rebuild over a changed session is refused
     * @param smiles the molecule to rebuild the session on, read as written
     */
    public record SubmitRequest(String sessionId, Long baseVersion, String smiles)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the patch
     * @param commit whether this ends the edit it belongs to (optional, default true)
     * @param atoms the atoms to change, each with only the fields that change
     */
    public record PatchRequest(String sessionId, Long baseVersion, Boolean commit, List<AtomPatch> atoms)
    {
    }

    /**
     * @param id the atom to change
     * @param position where to put it, in angstroms
     */
    public record AtomPatch(String id, Vec3 position)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param commit whether this ends the edit it belongs to (optional, default true)
     * @param position where the molecule's origin goes in the scene, in angstroms
     * @param rotation how the molecule is turned about its origin, as a quaternion
     */
    public record TransformRequest(String sessionId, Long baseVersion, Boolean commit, Vec3 position, Quaternion rotation)
    {
        /**
         * @return the pose this asks for
         */
        public Pose pose()
        {
            return new Pose(position, rotation);
        }
    }

    /**
     * @param sessionId the session whose camera this is
     * @param position where the camera is in the scene, in angstroms
     * @param target the point it looks at, in angstroms
     * @param fov its vertical field of view, in degrees
     * @param roll how far the picture is turned about the way it looks, in degrees
     */
    public record CameraRequest(String sessionId, Vec3 position, Vec3 target, Double fov, Double roll)
    {
        /**
         * @return the camera this describes
         * @throws IllegalArgumentException if any of it is missing or not a usable camera
         */
        public Camera camera()
        {
            if (fov == null || roll == null)
            {
                throw new IllegalArgumentException("position, target, fov and roll are required");
            }
            return new Camera(position, target, fov, roll);
        }
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it asked
     * @param atoms the atoms to lock or unlock
     * @param locked {@code true} to lock them, {@code false} to unlock them
     */
    public record LockRequest(String sessionId, Long baseVersion, List<String> atoms, Boolean locked)
    {
    }

    /**
     * @param sessionId the session to optimise
     * @param baseVersion the version the client was looking at, so that an optimisation of a structure that has since changed is refused
     */
    public record OptimiseRequest(String sessionId, Long baseVersion)
    {
    }

    /**
     * @param sessionId the session whose atoms' partial charges to calculate
     * @param model the name of the model to assign them with (optional; {@link ChargeModels#DEFAULT} if absent)
     */
    public record ChargesRequest(String sessionId, String model)
    {
    }

    /**
     * @param sessionId the session whose atoms' forces to calculate
     */
    public record ForcesRequest(String sessionId)
    {
    }

    /**
     * @param sessionId the session whose molecules to scan along a hydrogen bond
     */
    public record ScanRequest(String sessionId)
    {
    }

    /**
     * @param sessionId the session whose molecular orbitals to calculate
     * @param model the kind of Hartree-Fock calculation: {@code rhf}, {@code uhf} or {@code rohf}; or {@code mulliken} or absent for
     *     the studio's automatic choice, which is closed-shell where the electrons pair up and unrestricted where they do not
     * @param fragments the molecules to calculate each on its own, as an atom of each; absent or empty for the whole session as one
     */
    public record OrbitalsRequest(String sessionId, String model, List<String> fragments)
    {
    }

    /**
     * @param sessionId the session whose orbitals to relate to those of its molecules on their own
     * @param model the kind of Hartree-Fock calculation, as for {@link OrbitalsRequest}
     */
    public record InteractionRequest(String sessionId, String model)
    {
    }

    /**
     * @param sessionId the session whose electrostatic potential to calculate
     * @param model the kind of Hartree-Fock calculation, as for {@link OrbitalsRequest}
     * @param fragment an atom of the molecule to calculate, or absent for the whole session
     */
    public record EspRequest(String sessionId, String model, String fragment)
    {
    }

    /**
     * @param sessionId the session whose electron density to calculate
     * @param model the kind of Hartree-Fock calculation, as for {@link OrbitalsRequest}
     * @param fragment an atom of the molecule to calculate, or absent for the whole session
     */
    public record DensityRequest(String sessionId, String model, String fragment)
    {
    }

    /**
     * One orbital to evaluate.
     *
     * @param fragment an atom of the molecule it is of, or absent for the whole session calculated as one
     * @param spin which set of orbitals: {@code restricted}, {@code alpha} or {@code beta}, as the levels named it
     * @param orbital which orbital of that set, counting from the lowest in energy at zero
     */
    public record FieldTarget(String fragment, String spin, Integer orbital)
    {
    }

    /**
     * @param sessionId the session to calculate fields of
     * @param kind what the fields are; only {@code orbital} is supported
     * @param model the kind of calculation, as for {@link OrbitalsRequest}
     * @param targets the orbitals to evaluate, each of which is answered by a field
     */
    public record FieldRequest(String sessionId, String kind, String model, List<FieldTarget> targets)
    {
    }

    /**
     * @param sessionId the session whose optimisation to stop
     */
    public record CancelOptimiseRequest(String sessionId)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param bond the bond to give a length
     * @param length the length to give it, in angstroms
     * @param hold the atom of the bond to leave where it is (optional; none held if absent)
     * @param commit whether this ends the edit it belongs to (optional, default true)
     */
    public record BondLengthRequest(String sessionId, Long baseVersion, String bond, Double length, String hold, Boolean commit)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param bond the bond to turn about
     * @param from an atom bonded to the bond's first atom, which the torsion is measured from
     * @param to an atom bonded to the bond's second atom, which the torsion is measured to
     * @param angle the torsion to give it, in degrees
     * @param hold the atom of the bond whose side stays where it is (optional; none held if absent)
     * @param commit whether this ends the edit it belongs to (optional, default true)
     */
    public record BondTorsionRequest(String sessionId, Long baseVersion, String bond, String from, String to, Double angle, String hold, Boolean commit)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param centre the atom the angle is at
     * @param from one atom bonded to {@code centre}
     * @param to another atom bonded to {@code centre}
     * @param angle the angle to give it, in degrees
     * @param hold {@code from} or {@code to}, whose side stays where it is (optional; none held if absent)
     * @param commit whether this ends the edit it belongs to (optional, default true)
     */
    public record AngleRequest(String sessionId, Long baseVersion, String centre, String from, String to, Double angle, String hold, Boolean commit)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param atom the atom to change
     * @param element its new element symbol (optional; unchanged if absent)
     * @param charge its new formal charge, where 0 is none (optional; unchanged if absent)
     * @param isotope its new mass number, where 0 is natural abundance (optional; unchanged if absent)
     */
    public record AtomSetRequest(String sessionId, Long baseVersion, String atom, String element, Integer charge, Integer isotope)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param element the new atom's element symbol
     * @param attach the atom to bond it to (optional; without {@code position} the server places the new atom, and without it {@code position} is required)
     * @param order the order of the bond to {@code attach} (optional, default single)
     * @param position where to put the atom, in angstroms in the molecule's own frame (optional with {@code attach}, required without)
     */
    public record AtomAddRequest(String sessionId, Long baseVersion, String element, String attach, BondOrder order, Vec3 position)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param atoms the atoms to delete
     * @param hydrogens whether to delete too each hydrogen bonded only to a deleted atom (optional, default false)
     */
    public record AtomDeleteRequest(String sessionId, Long baseVersion, List<String> atoms, Boolean hydrogens)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param atoms the atoms to give the hydrogens that fill their valence
     */
    public record AtomFillRequest(String sessionId, Long baseVersion, List<String> atoms)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param first one atom to bond
     * @param second the other
     * @param order the bond's order (optional, default single)
     */
    public record BondAddRequest(String sessionId, Long baseVersion, String first, String second, BondOrder order)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param bonds the bonds to delete
     */
    public record BondDeleteRequest(String sessionId, Long baseVersion, List<String> bonds)
    {
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it composed the change
     * @param bond the bond to change (optional if {@code bonds} names them)
     * @param bonds the bonds to change, all in one edit (optional if {@code bond} names one)
     * @param order their new order
     */
    public record BondOrderRequest(String sessionId, Long baseVersion, String bond, List<String> bonds, BondOrder order)
    {
        /**
         * @return every bond the request names, once each
         */
        public List<String> named()
        {
            List<String> all = new ArrayList<>();
            if (bond != null)
            {
                all.add(bond);
            }
            if (bonds != null)
            {
                all.addAll(bonds);
            }
            return all.stream().distinct().toList();
        }
    }

    /**
     * @param sessionId the session to undo or redo in
     * @param baseVersion the version the client was looking at, so that an undo over a changed session is refused
     */
    public record HistoryRequest(String sessionId, Long baseVersion)
    {
    }

    /**
     * @param sessionId the session to move through the history of
     * @param baseVersion the version the client was looking at, so that a move over a changed session is refused
     * @param position how many edits to have in effect, from 0 (none) to the length of the history
     * @param discard whether to forget the edits after that point instead of keeping them to redo (optional, default false)
     */
    public record RewindRequest(String sessionId, Long baseVersion, Integer position, Boolean discard)
    {
    }
}
