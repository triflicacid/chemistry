package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.charges.PartialCharges;

import java.util.ArrayList;
import java.util.List;

/**
 * The partial charge of every atom of a session.
 *
 * @param type always {@code charges.result}
 * @param sessionId the session this describes
 * @param version the version of the session whose geometry the charges were calculated from
 * @param requested the model the client asked for, which is not always the one that assigned the charges
 * @param status {@code complete}, or {@code failed} if no charges could be calculated
 * @param model the name of the model that assigned the charges, or {@code null} if the calculation failed
 * @param convention what the charges mean, which is {@code FORMAL} where no real model could be applied, or
 *     {@code null} if the calculation failed
 * @param charges the charge of each atom, in elementary charges, in atom order; empty if the calculation failed
 * @param elapsedMs how long the calculation took, in milliseconds
 * @param message why the calculation failed, or {@code null} if it did not
 */
public record ChargeReport(
        String type,
        String sessionId,
        long version,
        String requested,
        String status,
        String model,
        String convention,
        List<AtomCharge> charges,
        long elapsedMs,
        String message)
{
    /** The type this message is sent as. */
    public static final String TYPE = "charges.result";

    /**
     * One atom's partial charge.
     *
     * @param id the atom
     * @param charge its partial charge, in elementary charges
     */
    public record AtomCharge(String id, double charge)
    {
    }

    /**
     * @param session the session the geometry is of
     * @param described the geometry to calculate charges for, taken from {@code session}
     * @param requested the name the client asked for the model by
     * @param model assigns the charges
     * @return the charge of every atom, with no time taken yet: see {@link #elapsed}
     * @throws RuntimeException if the model cannot assign charges to this structure
     */
    public static ChargeReport of(StudioSession session, StudioSession.Described described, String requested, PartialChargeModel model)
    {
        PartialCharges charges = model.calculate(described.structure(), 0);

        List<AtomCharge> byAtom = new ArrayList<>(described.sites().size());
        for (int site = 0; site < described.sites().size(); site++)
        {
            byAtom.add(new AtomCharge(described.sites().get(site), charges.bySite().get(site)));
        }

        return new ChargeReport(TYPE, session.sessionId(), described.version(), requested, "complete", model.name(),
                charges.convention().name(), List.copyOf(byAtom), 0, null);
    }

    /**
     * @param milliseconds how long the calculation took
     * @return this report, saying it took that long
     */
    public ChargeReport elapsed(long milliseconds)
    {
        return new ChargeReport(type, sessionId, version, requested, status, model, convention, charges, milliseconds, message);
    }

    /**
     * @param session the session whose charges could not be calculated
     * @param version the version of the geometry
     * @param requested the name the client asked for the model by
     * @param elapsedMs how long it was tried for, in milliseconds
     * @param message why
     * @return a report that says so and nothing else
     */
    public static ChargeReport failed(StudioSession session, long version, String requested, long elapsedMs, String message)
    {
        return new ChargeReport(TYPE, session.sessionId(), version, requested, "failed", null, null, List.of(), elapsedMs, message);
    }
}
