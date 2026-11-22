package com.ruben.chemistry.cli;

import com.ruben.chemistry.charges.PartialChargeModel;
import com.ruben.chemistry.charges.UserSuppliedCharges;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.forcefield.ElectronDensityForceField;
import com.ruben.chemistry.forcefield.ForceField;
import com.ruben.chemistry.forcefield.ForceFieldAtom;
import com.ruben.chemistry.forcefield.ForceFieldAtoms;
import com.ruben.chemistry.forcefield.ForceFieldMolecule;
import com.ruben.chemistry.forcefield.ModelType;
import com.ruben.chemistry.forcefield.SystemResult;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.space.Embedder;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Works out the electron-density force field's forces between the fragments of a molecule written in SMILES.
 * <p>
 * Run it through Gradle, e.g.
 * <pre>{@code
 * ./gradlew :backend:forces --args="O.O full"
 * ./gradlew :backend:forces --args="O.O ecp --conformer=2 --out=forces.txt"
 * }</pre>
 * Without an {@code --out} the forces go to standard output.
 */
final class FragmentForces
{
    static final String STANDARD_OUTPUT = "-";

    /** How far apart, in angstroms, the nearest points of neighbouring fragments start out. */
    private static final double FRAGMENT_GAP = 3.0;

    private static final double BOHR_PER_ANGSTROM = 1.0 / 0.529177210903;

    static final CliOption<String> OUT = CliOption.string(
            "out", "path", "where to write the forces, or - for standard output", STANDARD_OUTPUT);

    private static final CliOption<Integer> CONFORMER = CliOption.parsed(
            "conformer", "n", "which of the shapes found for each fragment to use, counting from 1, the least strained",
            FragmentForces::positive, () -> 1);

    static final List<CliOption<?>> DEFINITIONS = List.of(OUT, CONFORMER, CommandOptions.HELP);

    private static final String USAGE = CliParser.usage("Usage: forces <smiles> <full|ecp> [options]", DEFINITIONS);

    private FragmentForces()
    {
    }

    public static void main(String[] args) throws IOException
    {
        String destination;
        String report;

        try
        {
            CliOptions options = CliParser.parse(List.of(args), DEFINITIONS);
            if (options.get(CommandOptions.HELP))
            {
                System.out.println(USAGE);
                System.exit(0);
            }

            destination = options.get(OUT);
            report = forces(options);
        }
        catch (IllegalArgumentException wrong)
        {
            System.err.println(wrong.getMessage());
            System.err.println();
            System.err.println(USAGE);
            System.exit(2);
            return;
        }

        if (destination.equals(STANDARD_OUTPUT))
        {
            System.out.print(report);
        }
        else
        {
            Path path = Path.of(destination);
            Files.writeString(path, report, StandardCharsets.UTF_8);
            System.out.println("Wrote " + path.toAbsolutePath());
        }
    }

    public static String forces(List<String> arguments)
    {
        return forces(CliParser.parse(arguments, DEFINITIONS));
    }

    private static String forces(CliOptions options)
    {
        if (options.subjects().size() != 2)
        {
            throw new IllegalArgumentException("Expected a SMILES string and a model type, but got " + options.subjects().size() + " arguments");
        }
        String smiles = options.subjects().getFirst();
        ModelType type = modelType(options.subjects().get(1));
        ForceField forceField = ElectronDensityForceField.forceField(type);

        List<Molecule> fragments = Molecule.fromSmiles(smiles).fragments();
        List<ForceFieldMolecule> molecules = new ArrayList<>();
        List<List<ForceFieldAtom>> atomLists = new ArrayList<>();

        // no charge source is wired in yet, so every atom is neutral; a charged fragment is rejected
        PartialChargeModel chargeModel = new UserSuppliedCharges(type.chargeConvention(), Map.of());
        double previousEdge = 0;
        for (int index = 0; index < fragments.size(); index++)
        {
            Structure3D structure = embed(fragments.get(index), options.get(CONFORMER));
            Structure3D.Conformer conformer = structure.conformers().get(options.get(CONFORMER) - 1);

            // each fragment is centred on the origin, so this slides it clear of the one before
            double offset = index == 0 ? 0 : previousEdge + FRAGMENT_GAP + conformer.radius();
            previousEdge = offset + conformer.radius();

            int shape = options.get(CONFORMER) - 1;
            List<ForceFieldAtom> atoms = new ArrayList<>();
            for (ForceFieldAtom atom : ForceFieldAtoms.of(structure, shape, chargeModel.calculate(structure, shape), type))
            {
                atoms.add(new ForceFieldAtom(atom.element(), atom.x(), atom.y(), atom.z() + offset * BOHR_PER_ANGSTROM, atom.partialCharge()));
            }
            atomLists.add(atoms);
            molecules.add(forceField.molecule("fragment " + (index + 1), atoms));
        }

        SystemResult result = ElectronDensityForceField.systemEnergyAndForces(molecules, forceField.xc());
        return report(type, fragments, atomLists, result);
    }

    private static Structure3D embed(Molecule fragment, int conformer)
    {
        Structure3D structure = Embedder.embed(fragment, Embedder.Options.DEFAULT.withConformers(conformer));
        if (conformer > structure.conformers().size())
        {
            throw new IllegalArgumentException("--conformer is " + conformer + ", but only " + structure.conformers().size() + " found");
        }
        return structure;
    }

    private static String report(ModelType type, List<Molecule> fragments, List<List<ForceFieldAtom>> atomLists, SystemResult result)
    {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "model %s, %d fragment%s%n", type, fragments.size(), fragments.size() == 1 ? "" : "s"));
        text.append(String.format(Locale.ROOT, "energy (hartree): % .10f%n", result.energy()));
        text.append("forces (hartree/bohr)").append(System.lineSeparator());
        for (int fragment = 0; fragment < fragments.size(); fragment++)
        {
            text.append(String.format(Locale.ROOT, "fragment %d: %s%n", fragment + 1, SmilesWriter.write(fragments.get(fragment))));
            List<ForceFieldAtom> atoms = atomLists.get(fragment);
            for (int atom = 0; atom < atoms.size(); atom++)
            {
                Vec3 force = result.forces().get(fragment).get(atom);
                text.append(String.format(Locale.ROOT, "  %2d %-2s % .10f % .10f % .10f%n", atom, atoms.get(atom).element(), force.x(), force.y(), force.z()));
            }
        }
        if (fragments.size() == 1)
        {
            text.append("A single fragment has nothing to act on it; separate fragments with dots, as in O.O").append(System.lineSeparator());
        }
        return text.toString();
    }

    private static ModelType modelType(String input)
    {
        try
        {
            return ModelType.valueOf(input.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException unknown)
        {
            throw new IllegalArgumentException("'" + input + "' is not a model type; use full or ecp");
        }
    }

    private static int positive(String input)
    {
        try
        {
            int value = Integer.parseInt(input);
            if (value >= 1)
            {
                return value;
            }
        }
        catch (NumberFormatException invalid)
        {
            // falls through to the message below
        }
        throw new IllegalArgumentException("--conformer must be a whole number of at least 1, but was '" + input + "'");
    }
}
