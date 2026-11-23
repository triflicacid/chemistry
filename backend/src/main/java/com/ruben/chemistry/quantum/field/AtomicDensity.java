package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.quantum.basis.BasisLibrary;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.population.RhfMullikenCharges;
import com.ruben.chemistry.quantum.population.UhfMullikenCharges;
import com.ruben.chemistry.quantum.scf.ScfSettings;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The electron density of a free atom.
 */
public final class AtomicDensity
{
    /** The most valence electrons an atom can have for a reference to be made of it: those of the main-group elements, which fill an s and a p shell. */
    private static final int MOST_VALENCE_ELECTRONS = 8;

    /** The distance between the radii the average is worked out at, in bohr. */
    private static final double STEP = 0.02;

    /** How far from the nucleus the average is worked out, in bohr, beyond which the density of an atom is nothing. */
    private static final double REACH = 14.0;

    /** How many directions across the poles the average is taken over, by Gauss-Legendre quadrature, which is exact for the shapes a p shell makes. */
    private static final int POLAR_POINTS = 10;

    /** How many directions round the axis the average is taken over, evenly spaced. */
    private static final int AZIMUTHAL_POINTS = 12;

    private final double[] table;

    private AtomicDensity(double[] table)
    {
        this.table = table;
    }

    /**
     * Works out the density of a free atom.
     *
     * @param element the symbol of the element
     * @param library the basis sets and core potentials to calculate it with
     * @return its density averaged over every direction
     * @throws IllegalArgumentException if the element is not in the library, has no reference atom because it is not a main-group element, or its
     *     calculation does not converge
     */
    public static AtomicDensity of(String element, BasisLibrary library)
    {
        int valence = library.element(element).effectiveNuclearCharge();
        int multiplicity = groundStateMultiplicity(element, valence);
        Structure3D atom = new Structure3D(
                List.of(new Structure3D.Site(0, element, 0, 0, false, 0)),
                List.of(),
                List.of(new Structure3D.Conformer(0.0, List.of(Vec3.ZERO))));

        MolecularOrbitals orbitals;
        try
        {
            orbitals = multiplicity == 1
                    ? MolecularOrbitals.of(new RhfMullikenCharges(library).run(atom, 0))
                    : MolecularOrbitals.of(new UhfMullikenCharges(library, multiplicity, ScfSettings.defaults()).run(atom, 0));
        }
        catch (IllegalStateException unconverged)
        {
            throw new IllegalArgumentException("The free " + element + " atom could not be calculated: " + unconverged.getMessage(), unconverged);
        }
        return average(orbitals);
    }

    /**
     * @param element the symbol of the element, for the message
     * @param valence how many electrons the atom has that are treated explicitly
     * @return the spin multiplicity of the lowest-energy state of the atom by Hund's rule: as many unpaired electrons as the s and p shells allow
     * @throws IllegalArgumentException if the atom has no valence electrons, or more than a filled s and p shell holds, as a transition metal does
     */
    static int groundStateMultiplicity(String element, int valence)
    {
        if (valence < 1 || valence > MOST_VALENCE_ELECTRONS)
        {
            throw new IllegalArgumentException("The deformation density has no reference atom for " + element + ", which has " + valence
                    + " valence electrons; it is made only for atoms whose valence electrons fill an s and a p shell");
        }
        int unpaired = switch (valence)
        {
            case 1, 3, 7 -> 1;
            case 4, 6 -> 2;
            case 5 -> 3;
            default -> 0;
        };
        return unpaired + 1;
    }

    /**
     * @param distance the distance from the nucleus, in bohr
     * @return the density there, in electrons per cubic bohr, which is nothing beyond the reach the atom was worked out to
     */
    public double at(double distance)
    {
        double place = distance / STEP;
        int below = (int) place;
        if (below < 0 || below >= table.length - 1)
        {
            return 0.0;
        }
        double fraction = place - below;
        return table[below] * (1.0 - fraction) + table[below + 1] * fraction;
    }

    /**
     * @param orbitals the calculation of the atom
     * @return its density averaged over every direction
     */
    private static AtomicDensity average(MolecularOrbitals orbitals)
    {
        List<ContractedGaussian> functions = orbitals.basis().functions();
        Matrix density = orbitals.density();
        int n = functions.size();
        double[][] p = new double[n][n];
        for (int row = 0; row < n; row++)
        {
            for (int column = 0; column < n; column++)
            {
                p[row][column] = density.get(row, column);
            }
        }
        Vec3 centre = orbitals.nuclei().getFirst().position();

        double[][] quadrature = gaussLegendre(POLAR_POINTS);
        int radii = (int) Math.round(REACH / STEP) + 1;
        double[] table = new double[radii];
        IntStream.range(0, radii).parallel().forEach(i ->
        {
            double radius = i * STEP;
            double[] chi = new double[n];
            double total = 0.0;
            for (int polar = 0; polar < POLAR_POINTS; polar++)
            {
                double cosine = quadrature[0][polar];
                double sine = Math.sqrt(1.0 - cosine * cosine);
                for (int azimuth = 0; azimuth < AZIMUTHAL_POINTS; azimuth++)
                {
                    double angle = 2.0 * Math.PI * azimuth / AZIMUTHAL_POINTS;
                    double x = centre.x() + radius * sine * Math.cos(angle);
                    double y = centre.y() + radius * sine * Math.sin(angle);
                    double z = centre.z() + radius * cosine;
                    for (int mu = 0; mu < n; mu++)
                    {
                        chi[mu] = functions.get(mu).valueAt(x, y, z);
                    }
                    double rho = 0.0;
                    for (int mu = 0; mu < n; mu++)
                    {
                        double row = 0.0;
                        for (int nu = 0; nu < n; nu++)
                        {
                            row += p[mu][nu] * chi[nu];
                        }
                        rho += chi[mu] * row;
                    }
                    total += quadrature[1][polar] * rho;
                }
            }
            // the weights add up to two, across the whole range of the cosine, and the azimuthal points are equally weighted
            table[i] = total / (2.0 * AZIMUTHAL_POINTS);
        });
        return new AtomicDensity(table);
    }

    /**
     * @param count how many points
     * @return the nodes and then the weights of Gauss-Legendre quadrature of that many points on the range from minus one to one
     */
    static double[][] gaussLegendre(int count)
    {
        double[] nodes = new double[count];
        double[] weights = new double[count];
        for (int i = 0; i < (count + 1) / 2; i++)
        {
            // a first guess for the root, which Newton's method sharpens
            double x = Math.cos(Math.PI * (i + 0.75) / (count + 0.5));
            double derivative = 1.0;
            for (int iteration = 0; iteration < 100; iteration++)
            {
                double previous = 1.0;
                double current = x;
                for (int degree = 2; degree <= count; degree++)
                {
                    double next = ((2.0 * degree - 1.0) * x * current - (degree - 1.0) * previous) / degree;
                    previous = current;
                    current = next;
                }
                derivative = count * (x * current - previous) / (x * x - 1.0);
                double step = current / derivative;
                x -= step;
                if (Math.abs(step) < 1.0e-15)
                {
                    break;
                }
            }
            nodes[i] = -x;
            nodes[count - 1 - i] = x;
            weights[i] = 2.0 / ((1.0 - x * x) * derivative * derivative);
            weights[count - 1 - i] = weights[i];
        }
        return new double[][]{nodes, weights};
    }
}
