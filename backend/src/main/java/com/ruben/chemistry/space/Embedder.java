package com.ruben.chemistry.space;

import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.smiles.SmilesWriter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;

/**
 * Builds three-dimensional coordinates for a molecule by distance geometry.
 * <p>
 * The molecule's bonding gives a range for each pairwise distance ({@link Bounds}). A random
 * distance within each range is turned into coordinates in four dimensions, then those are
 * relaxed under the bounds, the declared handedness of each stereocentre, and the planarity of flat
 * atoms, before the fourth dimension is squeezed out. The result is checked against every declared
 * tetrahedral centre and double bond configuration, and tried again with a new seed if it fails.
 * Where the molecule declares nothing, nothing is imposed.
 */
public final class Embedder
{
    private static final int ATTEMPTS = 12;

    private static final int MAXIMUM_ITERATIONS = 700;

    /** How handed a centre must be, as the triple product of its unit ligand directions. */
    private static final double MINIMUM_HANDEDNESS = 0.4;

    private Embedder()
    {
    }

    /**
     * @param conformers how many conformers to try for
     * @param seed the seed to vary the conformers from, derived if empty
     * @param duplicateRmsd conformers whose heavy atoms lie within this many angstroms of a lower-strain one are dropped
     */
    public record Options(int conformers, OptionalLong seed, double duplicateRmsd)
    {
        public static final Options DEFAULT = new Options(1, OptionalLong.empty(), 0.3);

        /**
         * @param conformers how many conformers to try for
         * @return these options with that many conformers
         */
        public Options withConformers(int conformers)
        {
            return new Options(conformers, seed, duplicateRmsd);
        }

        /**
         * @param seed the seed to use
         * @return these options with that seed
         */
        public Options withSeed(long seed)
        {
            return new Options(conformers, OptionalLong.of(seed), duplicateRmsd);
        }
    }

    /**
     * @param molecule the molecule to embed
     * @return its three-dimensional structure, with one conformer
     * @throws IllegalStateException if no arrangement satisfying the declared stereochemistry was found
     */
    public static Structure3D embed(Molecule molecule)
    {
        return embed(molecule, Options.DEFAULT);
    }

    /**
     * @param molecule the molecule to embed
     * @param options how many conformers to find and how to seed them
     * @return its three-dimensional structure, lowest-strain conformer first
     * @throws IllegalStateException if no arrangement satisfying the declared stereochemistry was found
     */
    public static Structure3D embed(Molecule molecule, Options options)
    {
        Topology topology = new Topology(molecule);
        long seed = options.seed().orElseGet(() -> SmilesWriter.writeCanonical(molecule).hashCode());

        List<Structure3D.Conformer> found = new ArrayList<>();
        for (int conformer = 0; conformer < options.conformers(); conformer++)
        {
            Structure3D.Conformer built = null;
            for (int attempt = 0; attempt < ATTEMPTS && built == null; attempt++)
            {
                built = attempt(topology, new Random(seed + 7919L * conformer + 104729L * attempt));
            }
            if (built != null)
            {
                found.add(built);
            }
        }
        if (found.isEmpty())
        {
            throw new IllegalStateException("Could not find coordinates satisfying the molecule's stereochemistry");
        }

        found.sort(Comparator.comparingDouble(Structure3D.Conformer::strain));
        List<Structure3D.Conformer> kept = new ArrayList<>();
        for (Structure3D.Conformer candidate : found)
        {
            boolean duplicate = false;
            for (Structure3D.Conformer existing : kept)
            {
                duplicate |= rmsd(topology, existing, candidate) < options.duplicateRmsd();
            }
            if (!duplicate)
            {
                kept.add(candidate);
            }
        }

        List<Structure3D.Site> sites = new ArrayList<>();
        for (int node = 0; node < topology.count; node++)
        {
            sites.add(new Structure3D.Site(node, topology.element[node], topology.isotope[node], topology.owner[node], topology.implied[node], topology.charge[node]));
        }
        List<Structure3D.Connection> connections = new ArrayList<>();
        for (Topology.Link link : topology.links)
        {
            connections.add(new Structure3D.Connection(link.first(), link.second(), link.order(), link.source()));
        }
        return new Structure3D(List.copyOf(sites), List.copyOf(connections), List.copyOf(kept));
    }

    /**
     * @return one embedding, or {@code null} if it breaks a declared stereochemistry
     */
    private static Structure3D.Conformer attempt(Topology topology, Random random)
    {
        Bounds bounds = Bounds.of(topology, random);
        int count = topology.count;

        double[] x = metricEmbedding(bounds, count, random);
        Strain loose = new Strain(topology, bounds, 4, 0.05);
        Minimiser.minimise(loose, x, MAXIMUM_ITERATIONS);
        loose.flatten = 1;
        Minimiser.minimise(loose, x, MAXIMUM_ITERATIONS);

        double[] flat = new double[count * 3];
        for (int node = 0; node < count; node++)
        {
            System.arraycopy(x, node * 4, flat, node * 3, 3);
        }
        Strain strain = new Strain(topology, bounds, 3, 0);
        double energy = Minimiser.minimise(strain, flat, MAXIMUM_ITERATIONS * 2);

        List<Vec3> positions = centred(flat, count);
        return respectsStereochemistry(topology, positions) ? new Structure3D.Conformer(energy, positions) : null;
    }

    private static List<Vec3> centred(double[] flat, int count)
    {
        double cx = 0;
        double cy = 0;
        double cz = 0;
        for (int node = 0; node < count; node++)
        {
            cx += flat[node * 3];
            cy += flat[node * 3 + 1];
            cz += flat[node * 3 + 2];
        }
        cx /= count;
        cy /= count;
        cz /= count;
        List<Vec3> positions = new ArrayList<>(count);
        for (int node = 0; node < count; node++)
        {
            positions.add(new Vec3(flat[node * 3] - cx, flat[node * 3 + 1] - cy, flat[node * 3 + 2] - cz));
        }
        return List.copyOf(positions);
    }

    /**
     * Turns random distances within the bounds into four-dimensional coordinates, by taking the
     * four largest eigenvectors of the metric matrix they imply.
     */
    private static double[] metricEmbedding(Bounds bounds, int count, Random random)
    {
        double[][] squared = new double[count][count];
        double total = 0;
        double[] rowSum = new double[count];
        for (int first = 0; first < count; first++)
        {
            for (int second = first + 1; second < count; second++)
            {
                double low = bounds.lower[first][second];
                double distance = low + random.nextDouble() * (bounds.upper[first][second] - low);
                squared[first][second] = squared[second][first] = distance * distance;
                total += distance * distance;
                rowSum[first] += distance * distance;
                rowSum[second] += distance * distance;
            }
        }

        double[] fromCentre = new double[count];
        for (int node = 0; node < count; node++)
        {
            fromCentre[node] = rowSum[node] / count - total / ((double) count * count);
        }
        double[][] metric = new double[count][count];
        double shift = 0;
        for (int first = 0; first < count; first++)
        {
            double sum = 0;
            for (int second = 0; second < count; second++)
            {
                metric[first][second] = 0.5 * (fromCentre[first] + fromCentre[second] - squared[first][second]);
                sum += Math.abs(metric[first][second]);
            }
            shift = Math.max(shift, sum);
        }

        double[] coordinates = new double[count * 4];
        double[][] found = new double[4][];
        for (int axis = 0; axis < 4; axis++)
        {
            double[] vector = new double[count];
            for (int node = 0; node < count; node++)
            {
                vector[node] = random.nextDouble() - 0.5;
            }
            double value = 0;
            for (int iteration = 0; iteration < 150; iteration++)
            {
                for (int earlier = 0; earlier < axis; earlier++)
                {
                    projectOut(vector, found[earlier]);
                }
                double[] next = new double[count];
                for (int first = 0; first < count; first++)
                {
                    double sum = shift * vector[first];
                    for (int second = 0; second < count; second++)
                    {
                        sum += metric[first][second] * vector[second];
                    }
                    next[first] = sum;
                }
                double length = 0;
                for (double component : next)
                {
                    length += component * component;
                }
                length = Math.sqrt(length);
                if (length < 1e-12)
                {
                    break;
                }
                value = length - shift;
                for (int node = 0; node < count; node++)
                {
                    vector[node] = next[node] / length;
                }
            }
            found[axis] = vector;
            double scale = value > 1e-6 ? Math.sqrt(value) : 0.1;
            for (int node = 0; node < count; node++)
            {
                coordinates[node * 4 + axis] = vector[node] * scale;
            }
        }
        return coordinates;
    }

    private static void projectOut(double[] vector, double[] direction)
    {
        double overlap = 0;
        for (int at = 0; at < vector.length; at++)
        {
            overlap += vector[at] * direction[at];
        }
        for (int at = 0; at < vector.length; at++)
        {
            vector[at] -= overlap * direction[at];
        }
    }

    /**
     * @return whether every declared tetrahedral centre and double bond is as declared
     */
    private static boolean respectsStereochemistry(Topology topology, List<Vec3> positions)
    {
        for (Topology.Centre centre : topology.centres)
        {
            int[] ligands = centre.ligands();
            double handedness = Vec3.handedness(positions.get(centre.centre()),
                positions.get(ligands[1]), positions.get(ligands[2]), positions.get(ligands[3]));
            if (centre.sign() * handedness < MINIMUM_HANDEDNESS)
            {
                return false;
            }
        }
        for (Topology.Configured bond : topology.configured)
        {
            double torsion = Vec3.torsion(positions.get(bond.firstReference()), positions.get(bond.first()),
                positions.get(bond.second()), positions.get(bond.secondReference()));
            if ((Math.abs(torsion) < 90) != bond.sameSide())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the root-mean-square distance between the heavy atoms of two conformers once optimally uperposed (without reflection)
     */
    static double rmsd(Topology topology, Structure3D.Conformer first, Structure3D.Conformer second)
    {
        List<Vec3> a = new ArrayList<>();
        List<Vec3> b = new ArrayList<>();
        for (int node = 0; node < topology.count; node++)
        {
            if (topology.isHeavy(node))
            {
                a.add(first.positions().get(node));
                b.add(second.positions().get(node));
            }
        }
        if (a.isEmpty())
        {
            return 0;
        }
        return Superposition.rmsd(a, b);
    }
}
