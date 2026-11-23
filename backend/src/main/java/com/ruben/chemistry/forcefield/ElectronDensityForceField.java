package com.ruben.chemistry.forcefield;

import com.ruben.chemistry.space.Vec3;
import com.ruben.chemistry.util.CachedValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Electron-density force field.
 *
 * <p>Based on the 2024 paper by Romero, Limao-Vieira, Hermansson and Probst, Journal of Physical Chemistry A,
 * volume 128, pages 1163-1172, doi 10.1021/acs.jpca.3c06724.
 *
 * <p>Atomic parameters are distributed by the authors at
 * https://github.com/JoseRodriguezRomero/ElectronDensityForceModel. Retain the original attribution and the
 * applicable GPL-3.0 terms when redistributing.
 */
public final class ElectronDensityForceField
{
    private static final double SQRT_PI = Math.sqrt(Math.PI);
    private static final double FOUR_OVER_SQRT_PI = 4.0 / SQRT_PI;
    private static final double SMALL_R = 1.0e-10;

    /** How many coefficient and exponent pairs each line of a coefficient file holds. */
    private static final int PAIRS_PER_LINE = 3;

    /** One lazily loaded force field per parameterization, so each file is read at most once. */
    private static final Map<ModelType, Supplier<ForceField>> FORCE_FIELDS = new EnumMap<>(ModelType.class);

    static
    {
        for (ModelType type : ModelType.values())
        {
            FORCE_FIELDS.put(type, new CachedValue<>(() -> loadForceField(type)));
        }
    }

    private ElectronDensityForceField()
    {
    }

    /**
     * Reads a bundled coefficient file. Each file starts with a header line, then one record per element: a line
     * of symbol, atomic number and effective nuclear charge followed by three coefficient and exponent pairs.
     * The ECP file has just that line per element. The full-electron file adds two lines of three further pairs,
     * and pads elements that need fewer Gaussians with all-zero pairs.
     *
     * @param type the parameterization
     * @return the atomic parameters from the bundled coefficient file, by element symbol
     * @throws IOException if the resource is missing, cannot be read, or does not have the expected layout
     */
    private static Map<String, AtomicParameters> loadAtomicParameters(ModelType type) throws IOException
    {
        List<String> lines;
        try (InputStream stream = ElectronDensityForceField.class.getResourceAsStream("/" + type.resource()))
        {
            if (stream == null)
            {
                throw new IOException("missing resource " + type.resource());
            }
            lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        }
        if (lines.isEmpty() || !lines.get(0).startsWith("Element"))
        {
            throw new IOException(type.resource() + " must start with an Element header line");
        }
        List<String> records = lines.subList(1, lines.size());
        int linesPerElement = type == ModelType.ECP ? 1 : 3;
        if (records.isEmpty() || records.size() % linesPerElement != 0)
        {
            throw new IOException(type.resource() + " must hold whole " + linesPerElement + "-line records, found " + records.size() + " lines");
        }

        Map<String, AtomicParameters> result = new HashMap<>();
        for (int start = 0; start < records.size(); start += linesPerElement)
        {
            AtomicParameters parameters = parseElement(records.subList(start, start + linesPerElement));
            if (result.put(parameters.element(), parameters) != null)
            {
                throw new IOException("duplicate record for " + parameters.element() + " in " + type.resource());
            }
        }
        return Map.copyOf(result);
    }

    /**
     * @param record the lines of one element's record, the first holding the symbol, atomic number and effective
     *     nuclear charge before its pairs
     * @return the element's parameters, without the all-zero padding pairs
     * @throws IOException if a line has the wrong number of fields, an exponent is not positive, or the
     *     coefficients do not sum to the effective nuclear charge
     */
    private static AtomicParameters parseElement(List<String> record) throws IOException
    {
        String[] head = record.get(0).split("\s+");
        String symbol = head[0];
        List<Double> coefficients = new ArrayList<>();
        List<Double> exponents = new ArrayList<>();
        int z = 0;
        double zeff = 0.0;
        for (int row = 0; row < record.size(); row++)
        {
            String line = record.get(row);
            String[] fields = line.split("\s+");
            int first = row == 0 ? 3 : 0;
            if (fields.length != first + PAIRS_PER_LINE * 2)
            {
                throw new IOException("expected " + (first + PAIRS_PER_LINE * 2) + " fields in: " + line);
            }
            if (row == 0)
            {
                z = parseInt(fields[1], line);
                zeff = parseDouble(fields[2], line);
            }
            for (int i = first; i < fields.length; i += 2)
            {
                double coefficient = parseDouble(fields[i], line);
                double exponent = parseDouble(fields[i + 1], line);
                // elements needing fewer Gaussians are padded with zeros, which carry no density
                if (coefficient == 0.0 && exponent == 0.0)
                {
                    continue;
                }
                if (!(exponent > 0.0))
                {
                    throw new IOException("nonpositive exponent in: " + line);
                }
                coefficients.add(coefficient);
                exponents.add(exponent);
            }
        }

        double sum = coefficients.stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - zeff) > 5.0e-3)
        {
            throw new IOException(symbol + ": gaussian coefficients sum to " + sum + ", expected effective charge " + zeff);
        }
        return new AtomicParameters(symbol, z, zeff, coefficients.stream().mapToDouble(Double::doubleValue).toArray(), exponents.stream().mapToDouble(Double::doubleValue).toArray());
    }

    /**
     * @param type the parameterization
     * @return the published table S3 fit for {@code type}
     */
    public static XCParameters xcParameters(ModelType type)
    {
        // the fitting code solves for the electron-nuclear weights first, so the first row of table S3 multiplies
        // the electron-nuclear kernels and the second row the electron-electron kernels
        if (type == ModelType.FULL)
        {
            return new XCParameters(
                    new double[] {2.5106e-2, 2.0293e-1, -5.7248e-2, -8.7052e-3, -3.6683e-4},
                    new double[] {-1.2626e-2, -1.5394e-1, -8.0226e-2, -1.6789e-3, -1.0836e-4});
        }
        return new XCParameters(
                new double[] {3.9926e-3, -1.4886, 1.9678e-1, 5.6710e-3, -1.1361e-3, -1.2207e-4, -1.2274e-5},
                new double[] {-2.0439e-3, 8.5734e-1, 3.6408e-3, 9.2417e-3, 8.7046e-4, 1.3776e-5, 6.1397e-7});
    }

    public static ForceField forceField(ModelType type)
    {
        return FORCE_FIELDS.get(type).get();
    }
    
    private static ForceField loadForceField(ModelType type)
    {
        try
        {
            return new ForceField(type, loadAtomicParameters(type), xcParameters(type));
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException("Cannot load " + type + " force field", exception);
        }
    }

    /**
     * @param forceField the force field to build the density with
     * @param name the molecule's name
     * @param atoms the atoms, with positions in bohr
     * @return the molecule
     * @throws IllegalArgumentException if an element has no parameters
     */
    public static ForceFieldMolecule buildMolecule(ForceField forceField, String name, List<ForceFieldAtom> atoms)
    {
        return forceField.molecule(name, atoms);
    }

    /**
     * @param a the first molecule
     * @param b the second molecule
     * @param xc the exchange-correlation fit
     * @return the pair energy, without calculating forces
     */
    public static Energy interaction(ForceFieldMolecule a, ForceFieldMolecule b, XCParameters xc)
    {
        return interactionWithForces(a, b, xc).energy();
    }

    /**
     * @param a the first molecule
     * @param b the second molecule
     * @param xc the exchange-correlation fit
     * @return the pair energy and the analytical forces on both molecules
     * @throws IllegalArgumentException if two nuclei coincide
     */
    public static InteractionResult interactionWithForces(ForceFieldMolecule a, ForceFieldMolecule b, XCParameters xc)
    {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        Objects.requireNonNull(xc);
        double nn = 0.0;
        double ne = 0.0;
        double ee = 0.0;
        double xcee = 0.0;
        double xcen = 0.0;
        double[][] forceA = new double[a.atoms().size()][3];
        double[][] forceB = new double[b.atoms().size()][3];

        // nuclear-nuclear coulomb interactions
        for (int ia = 0; ia < a.atoms().size(); ia++)
        {
            ForceFieldAtom atomA = a.atoms().get(ia);
            for (int ib = 0; ib < b.atoms().size(); ib++)
            {
                ForceFieldAtom atomB = b.atoms().get(ib);
                double r = distance(atomA.x(), atomA.y(), atomA.z(), atomB.x(), atomB.y(), atomB.z());
                if (r < SMALL_R)
                {
                    throw new IllegalArgumentException("coincident nuclei");
                }
                double charge = a.nuclearCharges().get(ia) * b.nuclearCharges().get(ib);
                nn += charge / r;
                addPairForce(forceA, ia, forceB, ib, atomA.x(), atomA.y(), atomA.z(), atomB.x(), atomB.y(), atomB.z(), -charge / (r * r));
            }
        }

        // nuclei in a interact with electron clouds in b
        for (int ia = 0; ia < a.atoms().size(); ia++)
        {
            ForceFieldAtom atomA = a.atoms().get(ia);
            for (Gaussian cloudB : b.density())
            {
                double r = distance(atomA.x(), atomA.y(), atomA.z(), cloudB.x(), cloudB.y(), cloudB.z());
                double charge = a.nuclearCharges().get(ia) * cloudB.population();
                ne -= charge * erfOverR(cloudB.exponent(), r);
                double derivative = -charge * erfOverRDerivative(cloudB.exponent(), r);
                addPairForce(forceA, ia, forceB, cloudB.atomIndex(), atomA.x(), atomA.y(), atomA.z(), cloudB.x(), cloudB.y(), cloudB.z(), derivative);
            }
        }

        // nuclei in b interact with electron clouds in a
        for (int ib = 0; ib < b.atoms().size(); ib++)
        {
            ForceFieldAtom atomB = b.atoms().get(ib);
            for (Gaussian cloudA : a.density())
            {
                double r = distance(atomB.x(), atomB.y(), atomB.z(), cloudA.x(), cloudA.y(), cloudA.z());
                double charge = b.nuclearCharges().get(ib) * cloudA.population();
                ne -= charge * erfOverR(cloudA.exponent(), r);
                double derivative = -charge * erfOverRDerivative(cloudA.exponent(), r);
                addPairForce(forceA, cloudA.atomIndex(), forceB, ib, cloudA.x(), cloudA.y(), cloudA.z(), atomB.x(), atomB.y(), atomB.z(), derivative);
            }
        }

        // electron-electron coulomb interactions
        for (Gaussian cloudA : a.density())
        {
            for (Gaussian cloudB : b.density())
            {
                double r = distance(cloudA.x(), cloudA.y(), cloudA.z(), cloudB.x(), cloudB.y(), cloudB.z());
                double lambda = combinedExponent(cloudA.exponent(), cloudB.exponent());
                double population = cloudA.population() * cloudB.population();
                ee += population * erfOverR(lambda, r);
                addPairForce(forceA, cloudA.atomIndex(), forceB, cloudB.atomIndex(), cloudA.x(), cloudA.y(), cloudA.z(), cloudB.x(), cloudB.y(), cloudB.z(), population * erfOverRDerivative(lambda, r));
            }
        }

        // fitted electron-electron exchange-correlation terms
        for (Gaussian cloudA : a.density())
        {
            for (Gaussian cloudB : b.density())
            {
                double r = distance(cloudA.x(), cloudA.y(), cloudA.z(), cloudB.x(), cloudB.y(), cloudB.z());
                double lambda = combinedExponent(cloudA.exponent(), cloudB.exponent());
                double population = cloudA.population() * cloudB.population();
                for (int k = 0; k < xc.orderCount(); k++)
                {
                    double coefficient = xc.a(k) * population;
                    xcee += coefficient * xcKernel(k, lambda, r);
                    addPairForce(forceA, cloudA.atomIndex(), forceB, cloudB.atomIndex(), cloudA.x(), cloudA.y(), cloudA.z(), cloudB.x(), cloudB.y(), cloudB.z(), coefficient * xcKernelDerivative(k, lambda, r));
                }
            }
        }

        // fitted electron-nuclear terms for nuclei in b and clouds in a
        for (int ib = 0; ib < b.atoms().size(); ib++)
        {
            ForceFieldAtom atomB = b.atoms().get(ib);
            double zB = b.nuclearCharges().get(ib);
            for (Gaussian cloudA : a.density())
            {
                double r = distance(atomB.x(), atomB.y(), atomB.z(), cloudA.x(), cloudA.y(), cloudA.z());
                double base = zB * cloudA.population();
                for (int k = 0; k < xc.orderCount(); k++)
                {
                    double coefficient = xc.b(k) * base;
                    xcen += coefficient * xcKernel(k, cloudA.exponent(), r);
                    addPairForce(forceA, cloudA.atomIndex(), forceB, ib, cloudA.x(), cloudA.y(), cloudA.z(), atomB.x(), atomB.y(), atomB.z(), coefficient * xcKernelDerivative(k, cloudA.exponent(), r));
                }
            }
        }

        // fitted electron-nuclear terms for nuclei in a and clouds in b
        for (int ia = 0; ia < a.atoms().size(); ia++)
        {
            ForceFieldAtom atomA = a.atoms().get(ia);
            double zA = a.nuclearCharges().get(ia);
            for (Gaussian cloudB : b.density())
            {
                double r = distance(atomA.x(), atomA.y(), atomA.z(), cloudB.x(), cloudB.y(), cloudB.z());
                double base = zA * cloudB.population();
                for (int k = 0; k < xc.orderCount(); k++)
                {
                    double coefficient = xc.b(k) * base;
                    xcen += coefficient * xcKernel(k, cloudB.exponent(), r);
                    addPairForce(forceA, ia, forceB, cloudB.atomIndex(), atomA.x(), atomA.y(), atomA.z(), cloudB.x(), cloudB.y(), cloudB.z(), coefficient * xcKernelDerivative(k, cloudB.exponent(), r));
                }
            }
        }

        Energy energy = new Energy(nn, ne, ee, xcee, xcen);
        return new InteractionResult(energy, vectors(forceA), vectors(forceB));
    }

    /**
     * @param molecules the molecules in the system
     * @param xc the exchange-correlation fit
     * @return the sum of every pair interaction energy
     */
    public static double totalEnergy(List<ForceFieldMolecule> molecules, XCParameters xc)
    {
        double energy = 0.0;
        for (int i = 0; i < molecules.size(); i++)
        {
            for (int j = i + 1; j < molecules.size(); j++)
            {
                energy += interaction(molecules.get(i), molecules.get(j), xc).total();
            }
        }
        return energy;
    }

    /**
     * @param molecules the molecules in the system
     * @param xc the exchange-correlation fit
     * @return the total intermolecular energy and the force on every atom
     * @throws IllegalArgumentException if there are no molecules
     */
    public static SystemResult systemEnergyAndForces(List<ForceFieldMolecule> molecules, XCParameters xc)
    {
        Objects.requireNonNull(molecules);
        Objects.requireNonNull(xc);
        if (molecules.isEmpty())
        {
            throw new IllegalArgumentException("system must contain molecules");
        }
        List<double[][]> accumulated = new ArrayList<>();
        for (ForceFieldMolecule molecule : molecules)
        {
            accumulated.add(new double[molecule.atoms().size()][3]);
        }
        double energy = 0.0;

        // each pair is summed once, and its forces go to both molecules
        for (int i = 0; i < molecules.size(); i++)
        {
            for (int j = i + 1; j < molecules.size(); j++)
            {
                InteractionResult pair = interactionWithForces(molecules.get(i), molecules.get(j), xc);
                energy += pair.energy().total();
                addForces(accumulated.get(i), pair.forcesA());
                addForces(accumulated.get(j), pair.forcesB());
            }
        }
        List<List<Vec3>> forces = new ArrayList<>();
        for (double[][] values : accumulated)
        {
            forces.add(vectors(values));
        }
        return new SystemResult(energy, forces);
    }

    /**
     * Minimises the rigid translations of the molecules using limited-memory BFGS.
     *
     * @param initial the molecules at their starting positions
     * @param forceField the force field
     * @param options the optimiser settings
     * @return the molecules at the final geometry and the convergence history
     * @throws IllegalArgumentException if there are no molecules, or a fixed molecule is not in the system
     */
    public static OptimisationResult optimise(List<ForceFieldMolecule> initial, ForceField forceField, OptimiserOptions options)
    {
        Objects.requireNonNull(initial);
        Objects.requireNonNull(forceField);
        Objects.requireNonNull(options);
        if (initial.isEmpty())
        {
            throw new IllegalArgumentException("system must contain molecules");
        }

        List<ForceFieldMolecule> templates = List.copyOf(initial);
        boolean[] fixed = new boolean[templates.size()];
        for (MoleculeRef ref : options.fixedMolecules())
        {
            if (ref.moleculeIndex() >= templates.size())
            {
                throw new IllegalArgumentException("fixed molecule index is outside the system");
            }
            fixed[ref.moleculeIndex()] = true;
        }

        // only rigid translations are optimised, so each monomer keeps its geometry and orientation
        double[] x = new double[templates.size() * 3];
        List<ForceFieldMolecule> current = rebuildSystem(templates, x, forceField);
        SystemResult evaluation = systemEnergyAndForces(current, forceField.xc());
        double[] gradient = translationGradient(evaluation.forces(), fixed);
        double energy = evaluation.energy();
        List<Double> history = new ArrayList<>();
        history.add(energy);
        List<double[]> sHistory = new ArrayList<>();
        List<double[]> yHistory = new ArrayList<>();
        List<Double> rhoHistory = new ArrayList<>();
        double maxForce = maximumMolecularForce(evaluation.forces(), fixed);
        int completed = 0;
        boolean converged = maxForce <= options.forceTolerance();

        for (int iteration = 0; iteration < options.maxIterations() && !converged; iteration++)
        {
            double[] direction = lbfgsDirection(gradient, sHistory, yHistory, rhoHistory);
            double slope = dot(gradient, direction);
            if (!(slope < 0.0) || !Double.isFinite(slope))
            {
                // the approximate inverse hessian is unsuitable, so fall back to steepest descent
                direction = negate(gradient);
                slope = -dot(gradient, gradient);
                sHistory.clear();
                yHistory.clear();
                rhoHistory.clear();
            }

            double step = options.initialStep();
            double[] trialX = null;
            List<ForceFieldMolecule> trialMolecules = null;
            SystemResult trialEvaluation = null;
            boolean accepted = false;

            // an Armijo backtracking search keeps each step downhill
            for (int search = 0; search < options.maxLineSearch(); search++)
            {
                trialX = addScaled(x, direction, step);
                trialMolecules = rebuildSystem(templates, trialX, forceField);
                trialEvaluation = systemEnergyAndForces(trialMolecules, forceField.xc());
                if (Double.isFinite(trialEvaluation.energy()) && trialEvaluation.energy() <= energy + 1.0e-4 * step * slope)
                {
                    accepted = true;
                    break;
                }
                step *= 0.5;
            }
            if (!accepted)
            {
                break;
            }

            double[] trialGradient = translationGradient(trialEvaluation.forces(), fixed);
            double[] s = subtract(trialX, x);
            double[] y = subtract(trialGradient, gradient);
            double ys = dot(y, s);
            if (ys > 1.0e-12)
            {
                if (sHistory.size() == options.memory())
                {
                    sHistory.remove(0);
                    yHistory.remove(0);
                    rhoHistory.remove(0);
                }
                sHistory.add(s);
                yHistory.add(y);
                rhoHistory.add(1.0 / ys);
            }

            double previousEnergy = energy;
            x = trialX;
            current = trialMolecules;
            evaluation = trialEvaluation;
            gradient = trialGradient;
            energy = evaluation.energy();
            history.add(energy);
            completed = iteration + 1;
            maxForce = maximumMolecularForce(evaluation.forces(), fixed);
            converged = maxForce <= options.forceTolerance() && Math.abs(previousEnergy - energy) <= options.energyTolerance();
        }

        return new OptimisationResult(current, energy, completed, converged, maxForce, history);
    }

    /**
     * @param templates the molecules before any translation
     * @param translations the x, y and z translation of each molecule in turn
     * @param forceField the force field to rebuild the densities with
     * @return the molecules with the translations applied
     */
    private static List<ForceFieldMolecule> rebuildSystem(List<ForceFieldMolecule> templates, double[] translations, ForceField forceField)
    {
        List<ForceFieldMolecule> rebuilt = new ArrayList<>();
        for (int m = 0; m < templates.size(); m++)
        {
            ForceFieldMolecule template = templates.get(m);
            double dx = translations[3 * m];
            double dy = translations[3 * m + 1];
            double dz = translations[3 * m + 2];
            List<ForceFieldAtom> atoms = new ArrayList<>();
            for (ForceFieldAtom atom : template.atoms())
            {
                atoms.add(new ForceFieldAtom(atom.element(), atom.x() + dx, atom.y() + dy, atom.z() + dz, atom.partialCharge()));
            }
            rebuilt.add(forceField.molecule(template.name(), atoms));
        }
        return List.copyOf(rebuilt);
    }

    /**
     * @param forces the force on each atom, listed by molecule
     * @param fixed whether each molecule is fixed
     * @return the energy gradient of the translation of each molecule in turn, zero for fixed molecules
     */
    private static double[] translationGradient(List<List<Vec3>> forces, boolean[] fixed)
    {
        double[] result = new double[fixed.length * 3];
        for (int m = 0; m < forces.size(); m++)
        {
            if (fixed[m])
            {
                continue;
            }
            for (Vec3 force : forces.get(m))
            {
                result[3 * m] -= force.x();
                result[3 * m + 1] -= force.y();
                result[3 * m + 2] -= force.z();
            }
        }
        return result;
    }

    /**
     * @param forces the force on each atom, listed by molecule
     * @param fixed whether each molecule is fixed
     * @return the largest net force magnitude on a movable molecule
     */
    private static double maximumMolecularForce(List<List<Vec3>> forces, boolean[] fixed)
    {
        double maximum = 0.0;
        for (int m = 0; m < forces.size(); m++)
        {
            if (fixed[m])
            {
                continue;
            }
            double fx = 0.0;
            double fy = 0.0;
            double fz = 0.0;
            for (Vec3 force : forces.get(m))
            {
                fx += force.x();
                fy += force.y();
                fz += force.z();
            }
            maximum = Math.max(maximum, Math.sqrt(fx * fx + fy * fy + fz * fz));
        }
        return maximum;
    }

    /**
     * Applies the two-loop limited-memory BFGS recursion.
     *
     * @param gradient the current gradient
     * @param sHistory the recent position changes
     * @param yHistory the recent gradient changes
     * @param rhoHistory the reciprocal of each position change dotted with its gradient change
     * @return the search direction
     */
    private static double[] lbfgsDirection(double[] gradient, List<double[]> sHistory, List<double[]> yHistory, List<Double> rhoHistory)
    {
        double[] q = gradient.clone();
        int count = sHistory.size();
        double[] alpha = new double[count];
        for (int i = count - 1; i >= 0; i--)
        {
            alpha[i] = rhoHistory.get(i) * dot(sHistory.get(i), q);
            axpy(q, yHistory.get(i), -alpha[i]);
        }
        double gamma = 1.0;
        if (count > 0)
        {
            double[] lastS = sHistory.get(count - 1);
            double[] lastY = yHistory.get(count - 1);
            double yy = dot(lastY, lastY);
            if (yy > 0.0)
            {
                gamma = dot(lastS, lastY) / yy;
            }
        }
        for (int i = 0; i < q.length; i++)
        {
            q[i] *= gamma;
        }
        for (int i = 0; i < count; i++)
        {
            double beta = rhoHistory.get(i) * dot(yHistory.get(i), q);
            axpy(q, sHistory.get(i), alpha[i] - beta);
        }
        return negate(q);
    }

    /**
     * Adds a scaled vector in place.
     *
     * @param target the vector to add to
     * @param vector the vector to add
     * @param scale what {@code vector} is multiplied by first
     */
    private static void axpy(double[] target, double[] vector, double scale)
    {
        for (int i = 0; i < target.length; i++)
        {
            target[i] += scale * vector[i];
        }
    }

    /**
     * @param a the first vector
     * @param b the second vector
     * @return the dot product
     */
    private static double dot(double[] a, double[] b)
    {
        double value = 0.0;
        for (int i = 0; i < a.length; i++)
        {
            value += a[i] * b[i];
        }
        return value;
    }

    /**
     * @param values a vector
     * @return the vector with every component negated
     */
    private static double[] negate(double[] values)
    {
        double[] result = values.clone();
        for (int i = 0; i < result.length; i++)
        {
            result[i] = -result[i];
        }
        return result;
    }

    /**
     * @param a the first vector
     * @param b the second vector
     * @return {@code a} less {@code b}, component by component
     */
    private static double[] subtract(double[] a, double[] b)
    {
        double[] result = new double[a.length];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = a[i] - b[i];
        }
        return result;
    }

    /**
     * @param a the first vector
     * @param b the second vector
     * @param scale what {@code b} is multiplied by
     * @return {@code a} plus {@code b} scaled, component by component
     */
    private static double[] addScaled(double[] a, double[] b, double scale)
    {
        double[] result = new double[a.length];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = a[i] + scale * b[i];
        }
        return result;
    }

    /**
     * Accumulates one molecule's pair forces into its system force array.
     *
     * @param target the system force on each atom, as x, y and z
     * @param source the pair force on each atom
     */
    private static void addForces(double[][] target, List<Vec3> source)
    {
        for (int i = 0; i < target.length; i++)
        {
            target[i][0] += source.get(i).x();
            target[i][1] += source.get(i).y();
            target[i][2] += source.get(i).z();
        }
    }

    /**
     * @param alpha the exponent of the first Gaussian
     * @param beta the exponent of the second Gaussian
     * @return the exponent of the pair
     */
    private static double combinedExponent(double alpha, double beta)
    {
        return alpha * beta / (alpha + beta);
    }

    /**
     * @param lambda the combined Gaussian exponent
     * @param r the distance
     * @return the radial coulomb kernel erf(sqrt(lambda) r) / r
     */
    private static double erfOverR(double lambda, double r)
    {
        if (r < SMALL_R)
        {
            return 2.0 * Math.sqrt(lambda) / SQRT_PI;
        }
        return erf(Math.sqrt(lambda) * r) / r;
    }

    /**
     * @param lambda the combined Gaussian exponent
     * @param r the distance
     * @return the derivative of {@link #erfOverR} with respect to {@code r}
     */
    private static double erfOverRDerivative(double lambda, double r)
    {
        if (r < SMALL_R)
        {
            return 0.0;
        }
        double t = Math.sqrt(lambda) * r;
        return (2.0 * Math.sqrt(lambda) / SQRT_PI * Math.exp(-t * t) * r - erf(t)) / (r * r);
    }

    /**
     * @param k the order of the kernel
     * @param lambda the combined Gaussian exponent
     * @param r the distance
     * @return the kth exchange-correlation radial kernel
     */
    private static double xcKernel(int k, double lambda, double r)
    {
        return k == 0 ? erfOverR(lambda, r) : laplacianKernel(k, lambda, r);
    }

    /**
     * @param k the order of the kernel
     * @param lambda the combined Gaussian exponent
     * @param r the distance
     * @return the radial derivative of the kth exchange-correlation kernel
     */
    private static double xcKernelDerivative(int k, double lambda, double r)
    {
        return k == 0 ? erfOverRDerivative(lambda, r) : laplacianKernelDerivative(k, lambda, r);
    }

    /**
     * @param k the number of Laplacians applied, at least one
     * @param lambda the combined Gaussian exponent
     * @param r the distance
     * @return the kth repeated Laplacian of the Gaussian coulomb kernel
     */
    private static double laplacianKernel(int k, double lambda, double r)
    {
        double x = lambda * r * r;
        double[] polynomial = laplacianPolynomial(k - 1);
        return -FOUR_OVER_SQRT_PI * Math.pow(lambda, k + 0.5) * Math.exp(-x) * evaluate(polynomial, x);
    }

    /**
     * @param k the number of Laplacians applied, at least one
     * @param lambda the combined Gaussian exponent
     * @param r the distance
     * @return the radial derivative of {@link #laplacianKernel}
     */
    private static double laplacianKernelDerivative(int k, double lambda, double r)
    {
        double x = lambda * r * r;
        double[] polynomial = laplacianPolynomial(k - 1);
        double value = evaluate(polynomial, x);
        double derivative = evaluateDerivative(polynomial, x);
        return -FOUR_OVER_SQRT_PI * Math.pow(lambda, k + 0.5) * Math.exp(-x) * 2.0 * lambda * r * (derivative - value);
    }

    /**
     * @param order the number of further Laplacians applied
     * @return the coefficients, in ascending order, of the polynomial the repeated radial Laplacians generate
     */
    private static double[] laplacianPolynomial(int order)
    {
        double[] p = {1.0};
        for (int n = 0; n < order; n++)
        {
            double[] q = new double[p.length + 1];
            for (int j = 0; j < p.length; j++)
            {
                // the three-dimensional radial Laplacian of exp(-x) times the polynomial, term by term
                q[j] -= (8.0 * j + 6.0) * p[j];
                q[j + 1] += 4.0 * p[j];
                if (j > 0)
                {
                    q[j - 1] += (4.0 * j * j + 2.0 * j) * p[j];
                }
            }
            p = q;
        }
        return p;
    }

    /**
     * @param coefficients the polynomial's coefficients, in ascending order
     * @param x where to evaluate
     * @return the polynomial's value at {@code x}
     */
    private static double evaluate(double[] coefficients, double x)
    {
        double value = 0.0;
        for (int i = coefficients.length - 1; i >= 0; i--)
        {
            value = value * x + coefficients[i];
        }
        return value;
    }

    /**
     * @param coefficients the polynomial's coefficients, in ascending order
     * @param x where to evaluate
     * @return the polynomial's derivative at {@code x}
     */
    private static double evaluateDerivative(double[] coefficients, double x)
    {
        double value = 0.0;
        for (int i = coefficients.length - 1; i >= 1; i--)
        {
            value = value * x + i * coefficients[i];
        }
        return value;
    }

    /**
     * Accumulates equal and opposite forces for one radial pair interaction.
     *
     * @param forceA the force on each atom of the first molecule, as x, y and z
     * @param indexA the atom of the first molecule the first point belongs to
     * @param forceB the force on each atom of the second molecule, as x, y and z
     * @param indexB the atom of the second molecule the second point belongs to
     * @param ax the x coordinate of the first point
     * @param ay the y coordinate of the first point
     * @param az the z coordinate of the first point
     * @param bx the x coordinate of the second point
     * @param by the y coordinate of the second point
     * @param bz the z coordinate of the second point
     * @param radialDerivative the derivative of the pair energy with respect to the distance
     */
    private static void addPairForce(double[][] forceA, int indexA, double[][] forceB, int indexB, double ax, double ay, double az, double bx, double by, double bz, double radialDerivative)
    {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (r < SMALL_R)
        {
            return;
        }
        double scale = radialDerivative / r;
        double fx = scale * dx;
        double fy = scale * dy;
        double fz = scale * dz;
        forceA[indexA][0] += fx;
        forceA[indexA][1] += fy;
        forceA[indexA][2] += fz;
        forceB[indexB][0] -= fx;
        forceB[indexB][1] -= fy;
        forceB[indexB][2] -= fz;
    }

    /**
     * @param values the force on each atom, as x, y and z
     * @return the forces as an immutable list of vectors
     */
    private static List<Vec3> vectors(double[][] values)
    {
        List<Vec3> result = new ArrayList<>(values.length);
        for (double[] v : values)
        {
            result.add(new Vec3(v[0], v[1], v[2]));
        }
        return List.copyOf(result);
    }

    /**
     * @param ax the x coordinate of the first point
     * @param ay the y coordinate of the first point
     * @param az the z coordinate of the first point
     * @param bx the x coordinate of the second point
     * @param by the y coordinate of the second point
     * @param bz the z coordinate of the second point
     * @return the distance between the points
     */
    private static double distance(double ax, double ay, double az, double bx, double by, double bz)
    {
        return Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
    }

    /**
     * @param x a number
     * @return the error function of {@code x}, from the Cephes rational approximations
     */
    private static double erf(double x)
    {
        if (Double.isNaN(x))
        {
            return Double.NaN;
        }
        if (x == 0.0)
        {
            return 0.0;
        }
        if (Math.abs(x) > 1.0)
        {
            return Math.copySign(1.0 - erfc(Math.abs(x)), x);
        }
        double z = x * x;
        double[] t = {9.60497373987051638749E0, 9.00260197203842689217E1, 2.23200534594684319226E3, 7.00332514112805075473E3, 5.55923013010394962768E4};
        double[] u = {3.35617141647503099647E1, 5.21357949780152679795E2, 4.59432382970980127987E3, 2.26290000613890934246E4, 4.92673942608635921086E4};
        return x * polevl(z, t) / p1evl(z, u);
    }

    /**
     * @param x a number
     * @return the complementary error function of {@code x}
     */
    private static double erfc(double x)
    {
        if (x < 1.0)
        {
            return 1.0 - erf(x);
        }
        if (x > 27.0)
        {
            return 0.0;
        }
        double[] p = {2.46196981473530512524E-10, 5.64189564831068821977E-1, 7.46321056442269912687E0, 4.86371970985681366614E1, 1.96520832956077098242E2, 5.26445194995477358631E2, 9.34528527171957607540E2, 1.02755188689515710272E3, 5.57535335369399327526E2};
        double[] q = {1.32281951154744992508E1, 8.67072140885989742329E1, 3.54937778887819891062E2, 9.75708501743205489753E2, 1.82390916687909736289E3, 2.24633760818710981792E3, 1.65666309194161350182E3, 5.57535340817727675546E2};
        double[] r = {5.64189583547755073984E-1, 1.27536670759978104416E0, 5.01905042251180477414E0, 6.16021097993053585195E0, 7.40974269950448939160E0, 2.97886665372100240670E0};
        double[] s = {2.26052863220117276590E0, 9.39603524938001434673E0, 1.20489539808096656605E1, 1.70814450747565897222E1, 9.60896809063285878198E0, 3.36907645100081516050E0};
        double ratio = x < 8.0 ? polevl(x, p) / p1evl(x, q) : polevl(1.0 / x, r) / p1evl(1.0 / x, s);
        return Math.exp(-x * x) * ratio;
    }

    /**
     * @param x where to evaluate
     * @param coefficients the polynomial's coefficients, leading coefficient first
     * @return the polynomial's value at {@code x}
     */
    private static double polevl(double x, double[] coefficients)
    {
        double value = coefficients[0];
        for (int i = 1; i < coefficients.length; i++)
        {
            value = value * x + coefficients[i];
        }
        return value;
    }

    /**
     * @param x where to evaluate
     * @param coefficients the coefficients after the leading one, highest first
     * @return the value at {@code x} of the polynomial whose leading coefficient is one
     */
    private static double p1evl(double x, double[] coefficients)
    {
        double value = x + coefficients[0];
        for (int i = 1; i < coefficients.length; i++)
        {
            value = value * x + coefficients[i];
        }
        return value;
    }

    /**
     * @param value the field to parse
     * @param source the line the field came from, for the error message
     * @return the integer
     * @throws IOException if {@code value} is not an integer
     */
    private static int parseInt(String value, String source) throws IOException
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException exception)
        {
            throw new IOException("invalid integer in: " + source, exception);
        }
    }

    /**
     * @param value the field to parse
     * @param source the line the field came from, for the error message
     * @return the number
     * @throws IOException if {@code value} is not a finite number
     */
    private static double parseDouble(String value, String source) throws IOException
    {
        try
        {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed))
            {
                throw new NumberFormatException();
            }
            return parsed;
        }
        catch (NumberFormatException exception)
        {
            throw new IOException("invalid number in: " + source, exception);
        }
    }

    public static void main(String[] args)
    {
        ModelType type = args.length > 0 && args[0].equalsIgnoreCase("ECP") ? ModelType.ECP : ModelType.FULL;
        ForceField forceField = forceField(type);
        System.out.printf(Locale.ROOT, "loaded %s parameters for %d elements, xc orders 0-%d%n", type, forceField.atomic().size(), forceField.xc().orderCount() - 1);

        // this geometry is only a runnable smoke test, not a reference water dimer
        List<ForceFieldAtom> waterAAtoms = List.of(
                new ForceFieldAtom("O", 0.0, 0.0, 0.0, 0.0),
                new ForceFieldAtom("H", 1.430, 0.0, 1.107, 0.0),
                new ForceFieldAtom("H", -1.430, 0.0, 1.107, 0.0));
        List<ForceFieldAtom> waterBAtoms = waterAAtoms.stream()
                .map(atom -> new ForceFieldAtom(atom.element(), atom.x(), atom.y(), atom.z() + 5.0, atom.partialCharge()))
                .toList();
        ForceFieldMolecule moleculeA = buildMolecule(forceField, "water-a", waterAAtoms);
        ForceFieldMolecule moleculeB = buildMolecule(forceField, "water-b", waterBAtoms);
        InteractionResult result = interactionWithForces(moleculeA, moleculeB, forceField.xc());
        Energy energy = result.energy();
        System.out.printf(Locale.ROOT, "nn:       % .10f eh%n", energy.nuclearNuclear());
        System.out.printf(Locale.ROOT, "ne:       % .10f eh%n", energy.nuclearElectron());
        System.out.printf(Locale.ROOT, "ee:       % .10f eh%n", energy.electronElectron());
        System.out.printf(Locale.ROOT, "xc ee:    % .10f eh%n", energy.xcElectronElectron());
        System.out.printf(Locale.ROOT, "xc en:    % .10f eh%n", energy.xcElectronNuclear());
        System.out.printf(Locale.ROOT, "total:    % .10f eh%n", energy.total());
        System.out.println("forces on molecule a (hartree/bohr):");
        for (int i = 0; i < result.forcesA().size(); i++)
        {
            System.out.println("  " + i + ": " + result.forcesA().get(i));
        }
        System.out.println("supply isolated-monomer mulliken charges for scientific calculations");

        // the first molecule stays put so the second settles against it
        OptimiserOptions options = new OptimiserOptions(300, 8, 1.0e-5, 1.0e-10, 30, 0.2, Set.of(new MoleculeRef(0)));
        OptimisationResult optimised = optimise(List.of(moleculeA, moleculeB), forceField, options);
        System.out.printf(Locale.ROOT, "optimiser: converged=%s iterations=%d energy=% .10f eh max-force=% .3e eh/bohr%n", optimised.converged(), optimised.iterations(), optimised.energy(), optimised.maximumForce());
    }
}
