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
     * @return the model, at the lowest spin the electron count allows
     * @throws IllegalArgumentException if there is no model of that name
     */
    public static PartialChargeModel create(String id, ScfListener listener)
    {
        return create(id, null, listener);
    }

    /**
     * @param id the name of the model, one of {@link #IDS}
     * @param multiplicity the spin multiplicity to calculate at, or {@code null} for the lowest the electron count allows; it means nothing to the
     *     formal charges, which are not calculated
     * @param listener told how a quantum calculation is getting on, and able to stop it
     * @return the model
     * @throws IllegalArgumentException if there is no model of that name
     */
    public static PartialChargeModel create(String id, Integer multiplicity, ScfListener listener)
    {
        BasisLibrary library = BasisLibrary.cep31g();
        ScfSettings settings = ScfSettings.defaults().withListener(listener);
        return switch (id)
        {
            case MULLIKEN -> covered(library, new Automatic(library, settings, multiplicity));
            case RHF -> covered(library, new Closed(library, new RhfMullikenCharges(library, settings), multiplicity));
            case UHF -> covered(library, new Spin("uhf-mulliken", library, multiplicity, spin -> new UhfMullikenCharges(library, spin, settings)));
            case ROHF -> covered(library, new Spin("rohf-mulliken", library, multiplicity, spin -> new RohfMullikenCharges(library, spin, settings)));
            case FORMAL -> new Formal();
            default -> throw new IllegalArgumentException("No charge model is called '" + id + "'");
        };
    }

    /**
     * @param electrons how many electrons there are
     * @param multiplicity the spin multiplicity asked for, or {@code null} for the lowest the electron count allows
     * @return the multiplicity to calculate at
     * @throws IllegalArgumentException if the electrons cannot have the multiplicity
     */
    static int multiplicityFor(int electrons, Integer multiplicity)
    {
        int chosen = multiplicity == null ? (electrons % 2 == 0 ? 1 : 2) : multiplicity;
        // the unpaired electrons are the multiplicity less one, and the rest pair up, so they must be as many fewer than the electrons as is even
        if (chosen < 1 || chosen > electrons + 1 || (chosen - 1) % 2 != electrons % 2)
        {
            throw new IllegalArgumentException("A multiplicity of " + chosen + " is not possible with " + electrons + " electrons; it must be "
                    + (electrons % 2 == 0 ? "odd: 1, 3, 5…" : "even: 2, 4, 6…"));
        }
        return chosen;
    }

    private static PartialChargeModel covered(BasisLibrary library, PartialChargeModel model)
    {
        return new FallbackCharges(new Bare(library, model), library.symbols());
    }

    /**
     * @param structure the molecule
     * @param library the basis sets and core potentials, which decide how many electrons each atom brings
     * @return the electrons treated explicitly, after the molecule's charge and any core potentials
     */
    static int electrons(Structure3D structure, BasisLibrary library)
    {
        int electrons = 0;
        for (Structure3D.Site site : structure.sites())
        {
            electrons += library.element(site.element()).effectiveNuclearCharge() - site.charge();
        }
        return electrons;
    }

    /**
     * Leaves a molecule with no electrons treated explicitly, such as a bare sodium ion, to its formal charges, which are
     * then also its Mulliken charges, since there is nothing to share out.
     */
    private static final class Bare implements PartialChargeModel
    {
        private final BasisLibrary library;
        private final PartialChargeModel model;

        private Bare(BasisLibrary library, PartialChargeModel model)
        {
            this.library = library;
            this.model = model;
        }

        @Override
        public String name()
        {
            return model.name();
        }

        @Override
        public ChargeConvention convention()
        {
            return model.convention();
        }

        @Override
        public PartialCharges calculate(Structure3D structure, int conformer)
        {
            return electrons(structure, library) == 0
                    ? new PartialCharges(ChargeConvention.MULLIKEN, structure.sites().stream().map(site -> (double) site.charge()).toList())
                    : model.calculate(structure, conformer);
        }
    }

    /**
     * Closed-shell restricted Hartree-Fock, which is a singlet and so refuses any other spin.
     */
    private static final class Closed implements PartialChargeModel
    {
        private final BasisLibrary library;
        private final PartialChargeModel model;
        private final Integer multiplicity;

        private Closed(BasisLibrary library, PartialChargeModel model, Integer multiplicity)
        {
            this.library = library;
            this.model = model;
            this.multiplicity = multiplicity;
        }

        @Override
        public String name()
        {
            return model.name();
        }

        @Override
        public ChargeConvention convention()
        {
            return model.convention();
        }

        @Override
        public PartialCharges calculate(Structure3D structure, int conformer)
        {
            if (multiplicity != null && multiplicity != 1)
            {
                throw new IllegalArgumentException("Closed-shell restricted Hartree-Fock can only be a singlet; choose unrestricted or restricted open-shell for a multiplicity of "
                        + multiplicity);
            }
            multiplicityFor(electrons(structure, library), multiplicity);
            return model.calculate(structure, conformer);
        }
    }

    /**
     * Gives the model the spin it was asked for, or the lowest the electron count allows.
     */
    private static final class Spin implements PartialChargeModel
    {
        private final String name;
        private final BasisLibrary library;
        private final Integer requested;
        private final IntFunction<PartialChargeModel> model;

        private Spin(String name, BasisLibrary library, Integer requested, IntFunction<PartialChargeModel> model)
        {
            this.name = name;
            this.library = library;
            this.requested = requested;
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
            return model.apply(multiplicityFor(electrons(structure, library), requested)).calculate(structure, conformer);
        }
    }

    /**
     * Restricted Hartree-Fock where the electrons pair up, and unrestricted where they do not or where another spin is asked for.
     */
    private static final class Automatic implements PartialChargeModel
    {
        private final BasisLibrary library;
        private final ScfSettings settings;
        private final Integer requested;

        private Automatic(BasisLibrary library, ScfSettings settings, Integer requested)
        {
            this.library = library;
            this.settings = settings;
            this.requested = requested;
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
            int multiplicity = multiplicityFor(electrons(structure, library), requested);
            return multiplicity == 1
                    ? new RhfMullikenCharges(library, settings).calculate(structure, conformer)
                    : new UhfMullikenCharges(library, multiplicity, settings).calculate(structure, conformer);
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
