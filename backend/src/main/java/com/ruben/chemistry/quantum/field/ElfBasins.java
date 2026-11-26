package com.ruben.chemistry.quantum.field;

import com.ruben.chemistry.quantum.Units;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits the electron localisation function into its basins.
 */
public final class ElfBasins
{
    /** An atom is attached to a basin if its nucleus is within this many covalent radii of the basin's maximum. */
    private static final double ATTACHED = 1.35;

    /** An atom is a hydrogen, for the purpose of telling a bond to it from a lone pair on it, if its covalent radius is under this, in ångström. */
    private static final double HYDROGEN = 0.4;

    /** A hydrogen is bonded to an atom if their nuclei are within this many of the sum of their covalent radii. */
    private static final double BONDED = 1.3;

    private ElfBasins()
    {
    }

    /**
     * One basin.
     *
     * @param id which basin it is, counting from one
     * @param attractor where its maximum is, in ångström
     * @param elf the value of the function at its maximum
     * @param population how many electrons are in it
     * @param atoms the indices of the atoms it is attached to, in the order of the centres the partition was made with
     */
    public record Basin(int id, Vec3 attractor, double elf, double population, List<Integer> atoms)
    {
        /**
         * @return what kind of pair it is: a lone pair if it belongs to one atom, a bond if it joins two, a multicentre bond if it joins more, or
         *     unattached if no atom is near enough to claim it
         */
        public String kind()
        {
            return switch (atoms.size())
            {
                case 0 -> "unattached";
                case 1 -> "lone pair";
                case 2 -> "bond";
                default -> "multicentre bond";
            };
        }
    }

    /**
     * The basins of a function on a grid.
     *
     * @param labels which basin each point is in, as a whole number from one, or zero for a point that is in none, running along x fastest, then y,
     *     then z
     * @param basins the basins, from the most localised
     */
    public record Partition(float[] labels, List<Basin> basins)
    {
    }

    /**
     * @param elf the function at each point of the grid, running along x fastest, then y, then z, which is zero where it was not worked out
     * @param density the electron density at each point, in electrons per cubic bohr
     * @param grid where the points are, in ångström
     * @param centres where the atoms are, in ångström
     * @param radii the covalent radius of each atom, in ångström, which sets how near it must be to a basin to be attached to it
     * @param least the least value of the function at a basin's maximum for it to be a basin at all
     * @param smallest the fewest electrons a basin can hold and still be one, since the density is so thin in the tail that its function has maxima there
     *     that hold next to nothing
     * @param persistence how much a maximum must stand above the lowest pass to the next higher maximum to be a basin of its own: a lower one is
     *     taken as a ripple on the next, and the two are joined
     * @return the basins and which point is in which
     * @throws IllegalArgumentException if the fields are not the size of the grid, or there is not a radius for each atom
     */
    public static Partition partition(float[] elf, float[] density, FieldGrid grid, List<Vec3> centres, List<Double> radii, double least, double smallest, double persistence)
    {
        int size = grid.size();
        if (elf.length != size || density.length != size)
        {
            throw new IllegalArgumentException("expected " + size + " values for the grid, but got " + elf.length + " and " + density.length);
        }
        if (centres.size() != radii.size())
        {
            throw new IllegalArgumentException("expected a radius for each of " + centres.size() + " atoms, but got " + radii.size());
        }

        int[] root = ascend(elf, grid);
        Map<Integer, Integer> group = join(elf, grid, root, persistence);

        // the maxima that stand, from the most localised, each of which is a basin
        List<Integer> maxima = new ArrayList<>(new java.util.LinkedHashSet<>(group.values()));
        maxima.removeIf(top -> elf[top] < least);
        maxima.sort(Comparator.comparingDouble((Integer top) -> -elf[top]).thenComparingInt(top -> top));
        Map<Integer, Integer> number = new HashMap<>();
        for (int i = 0; i < maxima.size(); i++)
        {
            number.put(maxima.get(i), i + 1);
        }

        float[] labels = new float[size];
        double[] electrons = new double[maxima.size() + 1];
        double volume = Math.pow(Units.toBohr(grid.spacing()), 3);
        for (int at = 0; at < size; at++)
        {
            if (root[at] < 0)
            {
                continue;
            }
            Integer id = number.get(group.get(root[at]));
            if (id != null)
            {
                labels[at] = id;
                electrons[id] += density[at] * volume;
            }
        }

        List<Basin> basins = new ArrayList<>();
        int kept = 0;
        int[] renumber = new int[maxima.size() + 1];
        for (int top : maxima)
        {
            int id = number.get(top);
            if (electrons[id] < smallest)
            {
                continue;
            }
            renumber[id] = ++kept;
            int i = top % grid.nx();
            int j = (top / grid.nx()) % grid.ny();
            int k = top / (grid.nx() * grid.ny());
            Vec3 attractor = grid.pointAt(i, j, k);
            List<Integer> atoms = new ArrayList<>();
            for (int atom = 0; atom < centres.size(); atom++)
            {
                Vec3 centre = centres.get(atom);
                double distance = distanceBetween(attractor, centre);
                if (distance <= ATTACHED * radii.get(atom))
                {
                    atoms.add(atom);
                }
            }
            // a basin on a hydrogen is a bond to it, since a hydrogen has no core of its own to hold a pair that is not shared
            if (atoms.size() == 1 && radii.get(atoms.getFirst()) < HYDROGEN)
            {
                int hydrogen = atoms.getFirst();
                int partner = -1;
                double nearest = Double.MAX_VALUE;
                for (int atom = 0; atom < centres.size(); atom++)
                {
                    double distance = distanceBetween(centres.get(hydrogen), centres.get(atom));
                    if (atom != hydrogen && distance <= BONDED * (radii.get(hydrogen) + radii.get(atom)) && distance < nearest)
                    {
                        nearest = distance;
                        partner = atom;
                    }
                }
                if (partner >= 0)
                {
                    atoms.add(partner);
                }
            }
            basins.add(new Basin(renumber[id], attractor, elf[top], electrons[id], atoms));
        }
        // the points of a basin that was left out belong to none, and the others are numbered from one again
        for (int at = 0; at < size; at++)
        {
            labels[at] = labels[at] == 0 ? 0 : renumber[(int) labels[at]];
        }
        return new Partition(labels, basins);
    }

    private static double distanceBetween(Vec3 a, Vec3 b)
    {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    /**
     * @param elf the function at each point
     * @param grid where the points are
     * @return for each point, the point it climbs to by always stepping to the highest of its twenty-six neighbours, which is a maximum, or minus one
     *     for a point where the function is zero
     */
    private static int[] ascend(float[] elf, FieldGrid grid)
    {
        int size = grid.size();
        int[] next = new int[size];
        for (int k = 0; k < grid.nz(); k++)
        {
            for (int j = 0; j < grid.ny(); j++)
            {
                for (int i = 0; i < grid.nx(); i++)
                {
                    int at = grid.indexOf(i, j, k);
                    if (!(elf[at] > 0.0f))
                    {
                        next[at] = -1;
                        continue;
                    }
                    int best = at;
                    for (int dk = -1; dk <= 1; dk++)
                    {
                        for (int dj = -1; dj <= 1; dj++)
                        {
                            for (int di = -1; di <= 1; di++)
                            {
                                int ni = i + di;
                                int nj = j + dj;
                                int nk = k + dk;
                                if (ni < 0 || nj < 0 || nk < 0 || ni >= grid.nx() || nj >= grid.ny() || nk >= grid.nz())
                                {
                                    continue;
                                }
                                int other = grid.indexOf(ni, nj, nk);
                                // where two points are as high as each other the later one wins, so that every plateau climbs to one point
                                if (elf[other] > elf[best] || (elf[other] == elf[best] && other > best))
                                {
                                    best = other;
                                }
                            }
                        }
                    }
                    next[at] = best;
                }
            }
        }

        int[] root = new int[size];
        Arrays.fill(root, -2);
        int[] path = new int[size];
        for (int start = 0; start < size; start++)
        {
            if (root[start] != -2)
            {
                continue;
            }
            int length = 0;
            int at = start;
            int found;
            while (true)
            {
                if (root[at] != -2)
                {
                    found = root[at];
                    break;
                }
                if (next[at] < 0)
                {
                    found = -1;
                    break;
                }
                if (next[at] == at)
                {
                    found = at;
                    break;
                }
                path[length++] = at;
                at = next[at];
            }
            root[at] = found;
            for (int i = 0; i < length; i++)
            {
                root[path[i]] = found;
            }
        }
        return root;
    }

    /**
     * Joins maxima that are only ripples on a higher one, as the lowest pass between them is hardly lower than the lower of the two.
     *
     * @param elf the function at each point
     * @param grid where the points are
     * @param root the maximum each point climbs to, or minus one
     * @param persistence how much the lower maximum must stand above the pass between them to be kept apart
     * @return for each maximum, the highest maximum of the group it is joined to
     */
    private static Map<Integer, Integer> join(float[] elf, FieldGrid grid, int[] root, double persistence)
    {
        // the lowest pass between each pair of neighbouring basins is the highest value along their meeting, taking the lower side of each step
        Map<Long, Float> pass = new HashMap<>();
        int[][] steps = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int k = 0; k < grid.nz(); k++)
        {
            for (int j = 0; j < grid.ny(); j++)
            {
                for (int i = 0; i < grid.nx(); i++)
                {
                    int at = grid.indexOf(i, j, k);
                    if (root[at] < 0)
                    {
                        continue;
                    }
                    for (int[] step : steps)
                    {
                        int ni = i + step[0];
                        int nj = j + step[1];
                        int nk = k + step[2];
                        if (ni >= grid.nx() || nj >= grid.ny() || nk >= grid.nz())
                        {
                            continue;
                        }
                        int other = grid.indexOf(ni, nj, nk);
                        if (root[other] < 0 || root[other] == root[at])
                        {
                            continue;
                        }
                        long key = ((long) Math.min(root[at], root[other]) << 32) | Math.max(root[at], root[other]);
                        float level = Math.min(elf[at], elf[other]);
                        pass.merge(key, level, Math::max);
                    }
                }
            }
        }

        Map<Integer, Integer> parent = new HashMap<>();
        for (int top : root)
        {
            if (top >= 0)
            {
                parent.put(top, top);
            }
        }
        List<Map.Entry<Long, Float>> passes = new ArrayList<>(pass.entrySet());
        passes.sort(Map.Entry.<Long, Float>comparingByValue().reversed());
        for (Map.Entry<Long, Float> entry : passes)
        {
            int a = find(parent, (int) (entry.getKey() >> 32));
            int b = find(parent, (int) (entry.getKey() & 0xffffffffL));
            if (a == b)
            {
                continue;
            }
            double lower = Math.min(elf[a], elf[b]);
            if (lower - entry.getValue() < persistence)
            {
                // the higher maximum stands for the group
                if (elf[a] > elf[b] || (elf[a] == elf[b] && a > b))
                {
                    parent.put(b, a);
                }
                else
                {
                    parent.put(a, b);
                }
            }
        }

        Map<Integer, Integer> group = new HashMap<>();
        for (int top : parent.keySet())
        {
            group.put(top, find(parent, top));
        }
        return group;
    }

    private static int find(Map<Integer, Integer> parent, int top)
    {
        int at = top;
        while (parent.get(at) != at)
        {
            at = parent.get(at);
        }
        int walk = top;
        while (parent.get(walk) != at)
        {
            int up = parent.get(walk);
            parent.put(walk, at);
            walk = up;
        }
        return at;
    }
}
