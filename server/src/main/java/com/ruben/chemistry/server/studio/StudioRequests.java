package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.server.molecule.Camera;
import com.ruben.chemistry.space.Pose;
import com.ruben.chemistry.space.Quaternion;
import com.ruben.chemistry.space.Vec3;

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
