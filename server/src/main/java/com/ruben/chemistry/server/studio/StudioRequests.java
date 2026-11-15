package com.ruben.chemistry.server.studio;

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
     * @param sessionId the session to edit
     * @param baseVersion the version the client was looking at when it asked
     * @param atoms the atoms to lock or unlock
     * @param locked {@code true} to lock them, {@code false} to unlock them
     */
    public record LockRequest(String sessionId, Long baseVersion, List<String> atoms, Boolean locked)
    {
    }

    /**
     * @param sessionId the session to undo or redo in
     * @param baseVersion the version the client was looking at, so that an undo over a changed session is refused
     */
    public record HistoryRequest(String sessionId, Long baseVersion)
    {
    }
}
