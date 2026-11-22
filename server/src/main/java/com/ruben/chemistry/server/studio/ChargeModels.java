package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.charges.ChargeConvention;
import com.ruben.chemistry.charges.FallbackCharges;
import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.charges.PartialCharges;
import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.population.RhfMullikenCharges;
import com.ruben.chemistry.quantum.population.RohfMullikenCharges;
import com.ruben.chemistry.quantum.population.UhfMullikenCharges;
import com.ruben.chemistry.quantum.scf.ScfListener;
import com.ruben.chemistry.quantum.scf.ScfSettings;
import com.ruben.chemistry.space.Structure3D;

import java.util.List;
import java.util.function.IntFunction;

/**
 * The ways the studio can assign partial charges.
 */
public final class ChargeModels
{
    /** Hartree-Fock with the spin the molecule has: closed-shell if its electrons pair up, otherwise unrestricted as a doublet. */
    public static final String MULLIKEN = "mulliken";

    /** Closed-shell restricted Hartree-Fock, which cannot be applied to a molecule with an odd number of electrons. */
    public static final String RHF = "rhf";

    /** Unrestricted Hartree-Fock, as a singlet if the electrons pair up and otherwise as a doublet. */
    public static final String UHF = "uhf";

    /** Restricted open-shell Hartree-Fock, as a singlet if the electrons pair up and otherwise as a doublet. */
    public static final String ROHF = "rohf";

    /** Every site's formal charge, with no calculation. */
    public static final String FORMAL = "formal";

    /** What a client gets if it does not choose. */
    public static final String DEFAULT = MULLIKEN;

    /** Every model a client can ask for. */
    public static final List<String> IDS = List.of(MULLIKEN, RHF, UHF, ROHF, FORMAL);

    private ChargeModels()
    {
    }

    /**
     * @param id a client's name for a model
     * @return whether there is a model of that name
     */
    public static boolean exists(String id)
    {
        return IDS.contains(id);
    }

    /**
     * @param id the name of the model, one of {@link #IDS}
     * @param listener told how a quantum calculation is getting on, and able to stop it
     * @return the model
     * @throws IllegalArgumentException if there is no model of that name
     */
    public static PartialChargeModel create(String id, ScfListener listener)
    {
        BasisLibrary library = BasisLibrary.cep31g();
        ScfSettings settings = ScfSettings.defaults().withListener(listener);
        return switch (id)
        {
            case MULLIKEN -> covered(library, new Automatic(library, settings));
            case RHF -> covered(library, new RhfMullikenCharges(library, settings));
            case UHF -> covered(library, new Spin("uhf-mulliken", library, multiplicity -> new UhfMullikenCharges(library, multiplicity, settings)));
            case ROHF -> covered(library, new Spin("rohf-mulliken", library, multiplicity -> new RohfMullikenCharges(library, multiplicity, settings)));
            case FORMAL -> new Formal();
            default -> throw new IllegalArgumentException("No charge model is called '" + id + "'");
        };
    }

    private static PartialChargeModel covered(BasisLibrary library, PartialChargeModel model)
    {
        return new FallbackCharges(model, library.symbols());
    }

    /**
     * @param structure the molecule
     * @param library the basis sets and core potentials, which decide how many electrons each atom brings
     * @return the electrons treated explicitly, after the molecule's charge and any core potentials
     */
    private static int electrons(Structure3D structure, BasisLibrary library)
    {
        int electrons = 0;
        for (Structure3D.Site site : structure.sites())
        {
            electrons += library.element(site.element()).effectiveNuclearCharge() - site.charge();
        }
        return electrons;
    }

    /**
     * Gives the model the lowest spin the electron count allows.
     */
    private static final class Spin implements PartialChargeModel
    {
        private final String name;
        private final BasisLibrary library;
        private final IntFunction<PartialChargeModel> model;

        private Spin(String name, BasisLibrary library, IntFunction<PartialChargeModel> model)
        {
            this.name = name;
            this.library = library;
            this.model = model;
        }

        @Override
        public String name()
        {
            return name;
        }

        @Override
        public ChargeConvention convention()
        {
            return ChargeConvention.MULLIKEN;
        }

        @Override
        public PartialCharges calculate(Structure3D structure, int conformer)
        {
            int multiplicity = electrons(structure, library) % 2 == 0 ? 1 : 2;
            return model.apply(multiplicity).calculate(structure, conformer);
        }
    }

    /**
     * Restricted Hartree-Fock where the electrons pair up, and unrestricted where they do not.
     */
    private static final class Automatic implements PartialChargeModel
    {
        private final BasisLibrary library;
        private final ScfSettings settings;

        private Automatic(BasisLibrary library, ScfSettings settings)
        {
            this.library = library;
            this.settings = settings;
        }

        @Override
        public String name()
        {
            return "mulliken";
        }

        @Override
        public ChargeConvention convention()
        {
            return ChargeConvention.MULLIKEN;
        }

        @Override
        public PartialCharges calculate(Structure3D structure, int conformer)
        {
            return electrons(structure, library) % 2 == 0
                    ? new RhfMullikenCharges(library, settings).calculate(structure, conformer)
                    : new UhfMullikenCharges(library, 2, settings).calculate(structure, conformer);
        }
    }

    /**
     * Every site's formal charge.
     */
    private static final class Formal implements PartialChargeModel
    {
        @Override
        public String name()
        {
            return "formal";
        }

        @Override
        public ChargeConvention convention()
        {
            return ChargeConvention.FORMAL;
        }

        @Override
        public PartialCharges calculate(Structure3D structure, int conformer)
        {
            return new PartialCharges(ChargeConvention.FORMAL, structure.sites().stream().map(site -> (double) site.charge()).toList());
        }
    }
}
