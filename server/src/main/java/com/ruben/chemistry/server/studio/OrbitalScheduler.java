package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.field.ElectronDensity;
import com.ruben.chemistry.quantum.field.FieldGrid;
import com.ruben.chemistry.quantum.field.MolecularOrbitals;
import com.ruben.chemistry.quantum.field.OrbitalComposition;
import com.ruben.chemistry.quantum.field.OrbitalField;
import com.ruben.chemistry.quantum.population.RhfMullikenCharges;
import com.ruben.chemistry.quantum.population.RohfMullikenCharges;
import com.ruben.chemistry.quantum.population.UhfMullikenCharges;
import com.ruben.chemistry.quantum.scf.ScfListener;
import com.ruben.chemistry.quantum.scf.ScfSettings;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Works out the molecular orbitals of a session, or of some of its molecules each on its own, and their values on grids.
 */
public final class OrbitalScheduler implements AutoCloseable
{
    /** How long a session must be left alone before its orbitals are calculated. */
    public static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(300);

    /** How far beyond the atoms the grid reaches, in ångström, which is far enough for an orbital to have faded away. */
    public static final double MARGIN = 4.0;

    /** The most points along the longest side of the grid, which keeps the field small enough to send. */
    public static final int LONGEST_SIDE = 48;

    /** The least spacing of the grid, in ångström. */
    public static final double FINEST = 0.2;

    /** The least density, in electrons per cubic bohr, at which the electrostatic potential is found, which is below any surface worth drawing. */
    public static final double POTENTIAL_FROM = 0.0005;

    /** The spin density, in electrons per cubic bohr, below which a calculation is taken to have none anywhere. */
    public static final double NO_SPIN = 1.0e-6;

    /** The greatest density at which it is found, which is above any surface worth drawing. */
    public static final double POTENTIAL_TO = 0.03;

    /** The kind of calculation that is closed-shell where the electrons pair up and unrestricted where they do not. */
    public static final String AUTOMATIC = ChargeModels.MULLIKEN;

    /** The kinds of calculation a client can ask for. */
    public static final List<String> MODELS = List.of(AUTOMATIC, ChargeModels.RHF, ChargeModels.UHF, ChargeModels.ROHF);

    /**
     * Makes a Hartree-Fock calculation, which tests replace with something they can count.
     */
    @FunctionalInterface
    public interface Wavefunctions
    {
        /**
         * @param model the kind of calculation: one of {@link #MODELS} other than {@link #AUTOMATIC}
         * @param structure the molecule, with positions in ångström
         * @param multiplicity the spin multiplicity, or {@code null} for the lowest the electron count allows
         * @param listener told how it is getting on, and able to stop it
         * @return the orbitals
         * @throws IllegalArgumentException if the kind of calculation cannot be made of this molecule, or not at this multiplicity
         */
        MolecularOrbitals calculate(String model, Structure3D structure, Integer multiplicity, ScfListener listener);
    }

    /**
     * One orbital to evaluate.
     *
     * @param fragment an atom of the molecule it is of, or {@code null} for the whole session calculated as one
     * @param spin which set of orbitals: {@code restricted}, {@code alpha} or {@code beta}
     * @param orbital which orbital of that set, counting from the lowest in energy at zero
     */
    public record Target(String fragment, String spin, int orbital)
    {
    }

    /** A calculation, and the version of the session it is of. */
    private record Cached(long version, MolecularOrbitals orbitals, String model, Structure3D structure, List<String> atoms)
    {
    }

    private final JobScheduler<OrbitalReport> levels;
    private final JobScheduler<List<FieldReport>> fields;
    private final JobScheduler<InteractionReport> interactions;
    private final JobScheduler<EspReport> potentials;
    private final JobScheduler<DensityReport> densities;
    private final JobScheduler<DensityReport> spins;
    private final JobScheduler<DensityReport> differences;
    private final Wavefunctions wavefunctions;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    /**
     * Calculates with Hartree-Fock in the basis the studio assigns its charges with.
     *
     * @param debounce how long a session must be left alone before its orbitals are calculated
     */
    public OrbitalScheduler(Duration debounce)
    {
        this(debounce, OrbitalScheduler::hartreeFock);
    }

    /**
     * @param debounce how long a session must be left alone before its orbitals are calculated
     * @param wavefunctions makes the calculations
     */
    public OrbitalScheduler(Duration debounce, Wavefunctions wavefunctions)
    {
        this.levels = new JobScheduler<>(debounce);
        this.fields = new JobScheduler<>(debounce);
        this.interactions = new JobScheduler<>(debounce);
        this.potentials = new JobScheduler<>(debounce);
        this.densities = new JobScheduler<>(debounce);
        this.spins = new JobScheduler<>(debounce);
        this.differences = new JobScheduler<>(debounce);
        this.wavefunctions = Objects.requireNonNull(wavefunctions, "wavefunctions");
    }

    /**
     * @param requested the kind of calculation a client asked for, if it did
     * @return the one of {@link #MODELS} to use, which is the automatic choice for one that is absent or not an orbital model
     */
    public static String resolve(String requested)
    {
        return requested != null && MODELS.contains(requested) ? requested : AUTOMATIC;
    }

    /**
     * Asks for the orbital energies, once the session has been left alone, replacing any such request for it that is waiting.
     *
     * @param session the session
     * @param requested the kind of calculation, as {@link #resolve} reads it
     * @param fragments the molecules to calculate each on its own, as an atom of each; empty or {@code null} for the whole session as one
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void levels(StudioSession session, String requested, List<String> fragments, Consumer<OrbitalReport> deliver)
    {
        String model = resolve(requested);
        List<String> asked = fragments == null ? List.of() : List.copyOf(fragments);
        levels.schedule(session,
                (described, run) ->
                {
                    try
                    {
                        List<OrbitalReport.Piece> pieces = new ArrayList<>();
                        // an empty list is the whole session, which is one calculation with no molecule named
                        for (String fragment : asked.isEmpty() ? Collections.<String>singletonList(null) : asked)
                        {
                            Cached made = calculation(session, described, model, fragment, run);
                            pieces.add(new OrbitalReport.Piece(fragment, made.model(), made.orbitals(), made.atoms(), positionsOf(made.structure())));
                        }
                        return OrbitalReport.of(session, described, model, asked, pieces).elapsed(run.elapsedMs());
                    }
                    catch (IllegalArgumentException unsuitable)
                    {
                        return OrbitalReport.unavailable(session, described.version(), model, asked, unsuitable.getMessage());
                    }
                },
                (described, elapsedMs, message) -> OrbitalReport.failed(session, described.version(), model, asked, elapsedMs, message),
                deliver);
    }

    /**
     * Asks for the values of some orbitals on grids round the molecules they are of, once the session has been left alone,
     * replacing any such request for it that is waiting.
     *
     * @param session the session
     * @param requested the kind of calculation, as {@link #resolve} reads it
     * @param targets the orbitals to evaluate
     * @param deliver told the results, one for each target in order, from another thread, only if they are not out of date
     */
    public void orbitals(StudioSession session, String requested, List<Target> targets, Consumer<List<FieldReport>> deliver)
    {
        String model = resolve(requested);
        List<Target> asked = List.copyOf(targets);
        fields.schedule(session,
                (described, run) ->
                {
                    List<FieldReport> reports = new ArrayList<>();
                    for (Target target : asked)
                    {
                        reports.add(field(session, described, model, target, run));
                    }
                    return List.copyOf(reports);
                },
                (described, elapsedMs, message) -> asked.stream()
                        .map(target -> FieldReport.failed(session, described.version(), target.orbital(), target.spin(), target.fragment(), elapsedMs, message))
                        .toList(),
                deliver);
    }

    /**
     * Asks for how the orbitals of everything together are made of the orbitals of the molecules on their own, once the session
     * has been left alone, replacing any such request for it that is waiting.
     *
     * @param session the session
     * @param requested the kind of calculation, as {@link #resolve} reads it
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void interaction(StudioSession session, String requested, Consumer<InteractionReport> deliver)
    {
        String model = resolve(requested);
        interactions.schedule(session,
                (described, run) ->
                {
                    List<List<Integer>> parts = ForceReport.connectedParts(described.structure());
                    if (parts.size() < 2)
                    {
                        return InteractionReport.unavailable(session, described.version(), model, "An interaction diagram needs at least two molecules, and this session has only one");
                    }

                    try
                    {
                        Cached whole = calculation(session, described, model, null, run);
                        List<Cached> molecules = new ArrayList<>();
                        for (List<Integer> part : parts)
                        {
                            molecules.add(calculation(session, described, model, described.sites().get(part.getFirst()), run));
                        }

                        List<InteractionReport.Link> links = new ArrayList<>();
                        for (int m = 0; m < parts.size(); m++)
                        {
                            links.addAll(links(whole, molecules.get(m), described.sites().get(parts.get(m).getFirst()), parts.get(m)));
                            run.check();
                        }
                        return InteractionReport.of(session, described, model, whole.orbitals(), whole.model(), molecules.stream().map(Cached::orbitals).toList(),
                                molecules.stream().map(Cached::model).toList(), parts.stream().map(part -> described.sites().get(part.getFirst())).toList(), links).elapsed(run.elapsedMs());
                    }
                    catch (IllegalArgumentException unsuitable)
                    {
                        return InteractionReport.unavailable(session, described.version(), model, unsuitable.getMessage());
                    }
                },
                (described, elapsedMs, message) -> InteractionReport.failed(session, described.version(), model, elapsedMs, message),
                deliver);
    }

    /**
     * @param whole the calculation of everything together
     * @param molecule the calculation of one molecule on its own
     * @param fragment an atom of the molecule
     * @param sites the sites of the molecule among the whole's, in site order
     * @return how much of each orbital of the whole is each orbital of the molecule, of the sets that have the same spin
     */
    private static List<InteractionReport.Link> links(Cached whole, Cached molecule, String fragment, List<Integer> sites)
    {
        // the molecule's basis functions are the whole's at its sites, in the same order
        List<Integer> functions = new ArrayList<>();
        for (int function = 0; function < whole.orbitals().basis().size(); function++)
        {
            if (sites.contains(whole.orbitals().basis().siteOfFunction().get(function)))
            {
                functions.add(function);
            }
        }

        List<InteractionReport.Link> links = new ArrayList<>();
        for (MolecularOrbitals.Channel together : whole.orbitals().channels())
        {
            MolecularOrbitals.Channel alone = molecule.orbitals().channel(together.spin());
            if (alone == null)
            {
                continue;
            }

            double[][] overlaps = OrbitalComposition.overlaps(whole.orbitals(), together, functions, molecule.orbitals(), alone);
            for (int k = 0; k < overlaps.length; k++)
            {
                for (int j = 0; j < overlaps[k].length; j++)
                {
                    double weight = overlaps[k][j] * overlaps[k][j];
                    if (weight >= InteractionReport.LEAST_WEIGHT)
                    {
                        links.add(new InteractionReport.Link(together.spin(), k, fragment, j, Math.round(weight * 1.0e4) / 1.0e4));
                    }
                }
            }
        }
        return links;
    }

    /**
     * Asks for the electron density of the session, or of one of its molecules, with its electrostatic potential beside it, on a grid round the
     * atoms, once the session has been left alone, replacing any such request for it that is waiting. The potential is found only where the
     * density is near enough to the surfaces it can be drawn on to be of use.
     *
     * @param session the session
     * @param requested the kind of calculation, as {@link #resolve} reads it
     * @param fragment an atom of the molecule to calculate, or {@code null} for the whole session
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void potential(StudioSession session, String requested, String fragment, Integer multiplicity, Consumer<EspReport> deliver)
    {
        String model = resolve(requested);
        potentials.schedule(session,
                (described, run) ->
                {
                    try
                    {
                        Cached made = calculation(session, described, model, fragment, multiplicity, run);
                        FieldGrid grid = FieldGrid.around(positionsOf(made.structure()), MARGIN, LONGEST_SIDE, FINEST);
                        float[] density = ElectronDensity.evaluate(made.orbitals(), grid, run::check);
                        float[] potential = ElectronDensity.potential(made.orbitals(), grid, density, POTENTIAL_FROM, POTENTIAL_TO, run::check);
                        return EspReport.of(session, described.version(), fragment, grid, density, potential).elapsed(run.elapsedMs());
                    }
                    catch (IllegalArgumentException unsuitable)
                    {
                        return EspReport.failed(session, described.version(), fragment, run.elapsedMs(), unsuitable.getMessage());
                    }
                },
                (described, elapsedMs, message) -> EspReport.failed(session, described.version(), fragment, elapsedMs, message),
                deliver);
    }

    /**
     * Asks for the electron density of the session, or of one of its molecules, on a grid round the atoms, once the session has been left
     * alone, replacing any such request for it that is waiting. It is the cheaper half of {@link #potential}, for a surface drawn in one colour.
     *
     * @param session the session
     * @param requested the kind of calculation, as {@link #resolve} reads it
     * @param fragment an atom of the molecule to calculate, or {@code null} for the whole session
     * @param multiplicity the spin multiplicity to calculate it at, or {@code null} for the lowest the electron count allows
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void density(StudioSession session, String requested, String fragment, Integer multiplicity, Consumer<DensityReport> deliver)
    {
        schedule(densities, DensityReport.DENSITY, session, resolve(requested), fragment, deliver,
                (described, model, run) ->
                {
                    Cached made = calculation(session, described, model, fragment, multiplicity, run);
                    FieldGrid grid = FieldGrid.around(positionsOf(made.structure()), MARGIN, LONGEST_SIDE, FINEST);
                    return new Evaluated(grid, ElectronDensity.evaluate(made.orbitals(), grid, run::check));
                });
    }

    /**
     * Asks for the spin density on a grid around the atoms.
     *
     * @param session the session
     * @param requested the kind of calculation, as {@link #resolve} reads it
     * @param fragment an atom of the molecule to calculate, or {@code null} for the whole session
     * @param multiplicity the spin multiplicity to calculate it at, or {@code null} for the lowest the electron count allows
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void spin(StudioSession session, String requested, String fragment, Integer multiplicity, Consumer<DensityReport> deliver)
    {
        schedule(spins, DensityReport.SPIN, session, resolve(requested), fragment, deliver,
                (described, model, run) ->
                {
                    Cached made = calculation(session, described, model, fragment, multiplicity, run);
                    FieldGrid grid = FieldGrid.around(positionsOf(made.structure()), MARGIN, LONGEST_SIDE, FINEST);
                    float[] spin = ElectronDensity.spin(made.orbitals(), grid, run::check);
                    float greatest = 0.0f;
                    for (float value : spin)
                    {
                        greatest = Math.max(greatest, Math.abs(value));
                    }
                    if (greatest < NO_SPIN)
                    {
                        throw new IllegalArgumentException("This " + (fragment == null ? "system" : "molecule") + " has no unpaired electrons, so its spin density is zero."
                                + " Try a radical, such as [CH3] or [O] (the oxygen atom)");
                    }
                    return new Evaluated(grid, spin);
                });
    }

    /**
     * Asks for the difference density on a grid around the atoms.
     *
     * @param session the session
     * @param requested the kind of calculation, as {@link #resolve} reads it
     * @param multiplicity the spin multiplicity to calculate the whole at, or {@code null} for the lowest the electron count allows; the
     *     molecules each on its own are calculated at the lowest
     * @param deliver told the result, from another thread, only if it is not out of date
     */
    public void difference(StudioSession session, String requested, Integer multiplicity, Consumer<DensityReport> deliver)
    {
        schedule(differences, DensityReport.DIFFERENCE, session, resolve(requested), null, deliver,
                (described, model, run) ->
                {
                    List<List<Integer>> parts = ForceReport.connectedParts(described.structure());
                    if (parts.size() < 2)
                    {
                        throw new IllegalArgumentException("The difference density needs two or more molecules in the session");
                    }

                    Cached whole = calculation(session, described, model, null, multiplicity, run);
                    FieldGrid grid = FieldGrid.around(positionsOf(whole.structure()), MARGIN, LONGEST_SIDE, FINEST);
                    float[] difference = ElectronDensity.evaluate(whole.orbitals(), grid, run::check);
                    for (List<Integer> part : parts)
                    {
                        Cached alone = calculation(session, described, model, described.sites().get(part.getFirst()), run);
                        float[] density = ElectronDensity.evaluate(alone.orbitals(), grid, run::check);
                        for (int at = 0; at < difference.length; at++)
                        {
                            difference[at] -= density[at];
                        }
                    }
                    return new Evaluated(grid, difference);
                });
    }

    /** Some density on a grid. */
    private record Evaluated(FieldGrid grid, float[] values)
    {
    }

    /** Works out a density, from calculations that it asks for. */
    @FunctionalInterface
    private interface Evaluation
    {
        /**
         * @param described the geometry
         * @param model the kind of calculation, already resolved
         * @param run lets the work be stopped when it is out of date
         * @return the density, on the grid it was worked out on
         * @throws IllegalArgumentException if there is no such density to be had
         */
        Evaluated evaluate(StudioSession.Described described, String model, JobScheduler.Run run);
    }

    /**
     * Schedules a density once the session has been left alone, answering with a report that says why if it cannot be had.
     *
     * @param scheduler the queue for this kind of density
     * @param type what the density is
     * @param session the session
     * @param model the kind of calculation, already resolved
     * @param fragment an atom of the molecule it is of, or {@code null} for the whole session
     * @param deliver told the result, from another thread, only if it is not out of date
     * @param evaluation works it out
     */
    private void schedule(JobScheduler<DensityReport> scheduler, String type, StudioSession session, String model, String fragment, Consumer<DensityReport> deliver, Evaluation evaluation)
    {
        scheduler.schedule(session,
                (described, run) ->
                {
                    try
                    {
                        Evaluated made = evaluation.evaluate(described, model, run);
                        return DensityReport.of(type, session, described.version(), fragment, made.grid(), made.values()).elapsed(run.elapsedMs());
                    }
                    catch (IllegalArgumentException unsuitable)
                    {
                        return DensityReport.failed(type, session, described.version(), fragment, run.elapsedMs(), unsuitable.getMessage());
                    }
                },
                (described, elapsedMs, message) -> DensityReport.failed(type, session, described.version(), fragment, elapsedMs, message),
                deliver);
    }

    /** Stops calculating anything, and abandons what is waiting. */
    @Override
    public void close()
    {
        levels.close();
        fields.close();
        interactions.close();
        potentials.close();
        densities.close();
        spins.close();
        differences.close();
    }

    /**
     * @param session the session
     * @param described the geometry
     * @param model the kind of calculation, already resolved
     * @param target the orbital to evaluate
     * @param run lets the work be stopped when it is out of date
     * @return the orbital's values on a grid, or a failure that says why it cannot be
     */
    private FieldReport field(StudioSession session, StudioSession.Described described, String model, Target target, JobScheduler.Run run)
    {
        Cached made = calculation(session, described, model, target.fragment(), run);
        MolecularOrbitals.Channel channel = made.orbitals().channel(target.spin());
        if (channel == null)
        {
            return FieldReport.failed(session, described.version(), target.orbital(), target.spin(), target.fragment(), run.elapsedMs(),
                    "This calculation has no " + target.spin() + " orbitals; it has " + made.orbitals().channels().stream().map(MolecularOrbitals.Channel::spin).toList());
        }
        if (target.orbital() < 0 || target.orbital() >= channel.energies().length)
        {
            return FieldReport.failed(session, described.version(), target.orbital(), target.spin(), target.fragment(), run.elapsedMs(),
                    "There is no orbital " + target.orbital() + "; there are " + channel.energies().length);
        }

        List<Vec3> positions = made.structure().conformers().getFirst().positions();
        FieldGrid grid = FieldGrid.around(positions, MARGIN, LONGEST_SIDE, FINEST);
        float[] values = OrbitalField.evaluate(made.orbitals().basis(), channel.coefficients().column(target.orbital()), grid, run::check);
        return FieldReport.of(session, described.version(), target.orbital(), target.spin(), target.fragment(), grid, values).elapsed(run.elapsedMs());
    }

    /**
     * @param session the session
     * @param described the geometry
     * @param model the kind of calculation, already resolved
     * @param fragment an atom of the molecule to calculate, or {@code null} for the whole session
     * @param run lets the calculation be stopped when it is out of date
     * @return the calculation of that geometry, which is the one kept if it is of the same version
     * @throws IllegalArgumentException if no molecule has the atom
     */
    private Cached calculation(StudioSession session, StudioSession.Described described, String model, String fragment, JobScheduler.Run run)
    {
        return calculation(session, described, model, fragment, null, run);
    }

    /**
     * @param session the session
     * @param described the geometry
     * @param model the kind of calculation, already resolved
     * @param fragment an atom of the molecule to calculate, or {@code null} for the whole session
     * @param multiplicity the spin multiplicity, or {@code null} for the lowest the electron count allows
     * @param run lets the calculation be stopped when it is out of date
     * @return the calculation of that geometry, which is the one kept if it is of the same version
     * @throws IllegalArgumentException if no molecule has the atom, or the electrons cannot have the multiplicity
     */
    private Cached calculation(StudioSession session, StudioSession.Described described, String model, String fragment, Integer multiplicity, JobScheduler.Run run)
    {
        // one calculation at a time, so that a request for the levels and one for an orbital do not both run it
        synchronized (lock)
        {
            String key = session.sessionId() + "|" + model + "|" + (multiplicity == null ? "" : multiplicity) + "|" + (fragment == null ? "" : fragment);
            Cached held = cache.get(key);
            if (held != null && held.version() == described.version())
            {
                return held;
            }

            List<Integer> part = fragment == null ? null : partOf(described, fragment);
            Structure3D structure = part == null ? described.structure() : ForceReport.alone(described.structure(), part);
            List<String> atoms = part == null ? described.sites() : part.stream().map(described.sites()::get).toList();
            MolecularOrbitals made = wavefunctions.calculate(model, structure, multiplicity, new ScfListener()
            {
                @Override
                public void stage(String stage)
                {
                    run.check();
                }

                @Override
                public void iteration(int iteration, double energy, double error)
                {
                    run.check();
                }
            });
            Cached fresh = new Cached(described.version(), made, kind(model, made), structure, atoms);
            cache.put(key, fresh);
            return fresh;
        }
    }

    /**
     * @param structure a molecule
     * @return where each of its sites is, in site order, in ångström
     */
    private static List<Vec3> positionsOf(Structure3D structure)
    {
        List<Vec3> positions = structure.conformers().getFirst().positions();
        return structure.sites().stream().map(site -> positions.get(site.index())).toList();
    }

    /**
     * @param described the whole session's geometry
     * @param atom an atom of one of its molecules
     * @return the sites of that molecule, in site order
     * @throws IllegalArgumentException if no molecule has the atom
     */
    private static List<Integer> partOf(StudioSession.Described described, String atom)
    {
        for (List<Integer> part : ForceReport.connectedParts(described.structure()))
        {
            if (part.stream().anyMatch(site -> described.sites().get(site).equals(atom)))
            {
                return part;
            }
        }
        throw new IllegalArgumentException("No molecule has the atom " + atom);
    }

    /**
     * @param model the kind of calculation that was asked for, already resolved
     * @param orbitals the orbitals of the calculation
     * @return the name of the kind of calculation it was, which the automatic choice gives away by whether it made two sets of orbitals
     */
    private static String kind(String model, MolecularOrbitals orbitals)
    {
        if (!AUTOMATIC.equals(model))
        {
            return model;
        }
        return orbitals.channels().size() == 2 ? ChargeModels.UHF : ChargeModels.RHF;
    }

    /**
     * Makes the calculation the studio assigns its charges with, with the lowest spin the electron count allows.
     *
     * @param model the kind of calculation, with {@link #AUTOMATIC} meaning closed-shell where the electrons pair up
     * @param structure the molecule
     * @param requested the spin multiplicity, or {@code null} for the lowest the electron count allows
     * @param listener told how it is getting on
     * @return the orbitals
     * @throws IllegalArgumentException if the electrons cannot have the multiplicity, or the kind of calculation cannot have it
     */
    private static MolecularOrbitals hartreeFock(String model, Structure3D structure, Integer requested, ScfListener listener)
    {
        BasisLibrary library = BasisLibrary.cep31g();
        ScfSettings settings = ScfSettings.defaults().withListener(listener);
        int electrons = ChargeModels.electrons(structure, library);
        int multiplicity = requested == null ? (electrons % 2 == 0 ? 1 : 2) : requested;
        // the unpaired electrons are the multiplicity less one, and the rest pair up, so they must be as many fewer than the electrons as is even
        if (multiplicity < 1 || multiplicity > electrons + 1 || (multiplicity - 1) % 2 != electrons % 2)
        {
            throw new IllegalArgumentException("A multiplicity of " + multiplicity + " is not possible with " + electrons + " electrons; it must be "
                    + (electrons % 2 == 0 ? "odd: 1, 3, 5…" : "even: 2, 4, 6…"));
        }
        if (ChargeModels.RHF.equals(model) && multiplicity != 1)
        {
            throw new IllegalArgumentException("Closed-shell restricted Hartree-Fock can only be a singlet; choose unrestricted or restricted open-shell for a multiplicity of " + multiplicity);
        }
        return switch (model)
        {
            case ChargeModels.RHF -> MolecularOrbitals.of(new RhfMullikenCharges(library, settings).run(structure, 0));
            case ChargeModels.UHF -> MolecularOrbitals.of(new UhfMullikenCharges(library, multiplicity, settings).run(structure, 0));
            case ChargeModels.ROHF -> MolecularOrbitals.of(new RohfMullikenCharges(library, multiplicity, settings).run(structure, 0));
            default -> multiplicity == 1
                    ? MolecularOrbitals.of(new RhfMullikenCharges(library, settings).run(structure, 0))
                    : MolecularOrbitals.of(new UhfMullikenCharges(library, multiplicity, settings).run(structure, 0));
        };
    }
}
