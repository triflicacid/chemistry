package com.ruben.chemistry.quantum.population;

import com.ruben.chemistry.quantum.basis.AngularMomentum;
import com.ruben.chemistry.quantum.basis.ContractedGaussian;
import com.ruben.chemistry.quantum.basis.GaussianPrimitive;
import com.ruben.chemistry.quantum.integrals.PointCharge;
import com.ruben.chemistry.quantum.linalg.Matrix;
import com.ruben.chemistry.quantum.scf.ClosedShellScf;
import com.ruben.chemistry.quantum.scf.ScfResult;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MullikenAnalysisTest
{
    private static final AngularMomentum S = new AngularMomentum(0, 0, 0);
    private static final double[] SLATER_1S = {0.15432897, 0.53532814, 0.44463454};

    private static ContractedGaussian contracted(Vec3 centre, AngularMomentum angular, double[] exponents, double[] coefficients)
    {
        List<GaussianPrimitive> primitives = new ArrayList<>();
        for (int i = 0; i < exponents.length; i++)
        {
            primitives.add(new GaussianPrimitive(exponents[i], coefficients[i]));
        }
        return new ContractedGaussian(centre, angular, primitives);
    }

    private static ContractedGaussian hydrogen(Vec3 centre)
    {
        return contracted(centre, S, new double[] {3.42525091, 0.62391373, 0.16885540}, SLATER_1S);
    }

    private static ContractedGaussian helium(Vec3 centre)
    {
        return contracted(centre, S, new double[] {6.36242139, 1.15892300, 0.31364979}, SLATER_1S);
    }

    private static ScfResult water(List<Integer> sites)
    {
        Vec3 o = new Vec3(0.0, -0.143225816552, 0.0);
        Vec3 h1 = new Vec3(1.638036840407, 1.136548822547, 0.0);
        Vec3 h2 = new Vec3(-1.638036840407, 1.136548822547, 0.0);
        double[] valence = {5.0331513, 1.1695961, 0.3803890};
        List<ContractedGaussian> basis = new ArrayList<>();
        basis.add(contracted(o, S, new double[] {130.7093200, 23.8088610, 6.4436083}, SLATER_1S));
        basis.add(contracted(o, S, valence, new double[] {-0.09996723, 0.39951283, 0.70011547}));
        for (AngularMomentum p : AngularMomentum.shell(1))
        {
            basis.add(contracted(o, p, valence, new double[] {0.15591627, 0.60768372, 0.39195739}));
        }
        basis.add(hydrogen(h1));
        basis.add(hydrogen(h2));
        sites.addAll(List.of(0, 0, 0, 0, 0, 1, 2));
        return ClosedShellScf.run(basis, List.of(new PointCharge(o, 8.0), new PointCharge(h1, 1.0), new PointCharge(h2, 1.0)), 10);
    }

    @Test
    void should_give_a_symmetric_molecule_zero_charge_on_each_atom()
    {
        Vec3 second = new Vec3(0, 0, 1.4);
        ScfResult result = ClosedShellScf.run(List.of(hydrogen(Vec3.ZERO), hydrogen(second)), List.of(new PointCharge(Vec3.ZERO, 1.0), new PointCharge(second, 1.0)), 2);

        double[] charges = MullikenAnalysis.charges(result, List.of(0, 1), new double[] {1.0, 1.0});

        assertThat(charges[0]).isCloseTo(0.0, within(1e-10));
        assertThat(charges[1]).isCloseTo(0.0, within(1e-10));
    }

    @Test
    void should_conserve_electrons_and_charge_and_make_water_polar()
    {
        List<Integer> sites = new ArrayList<>();
        ScfResult result = water(sites);

        double[] populations = MullikenAnalysis.populations(result, sites, 3);
        double[] charges = MullikenAnalysis.charges(result, sites, new double[] {8.0, 1.0, 1.0});

        assertThat(populations[0] + populations[1] + populations[2]).isCloseTo(10.0, within(1e-10));
        assertThat(charges[0] + charges[1] + charges[2]).isCloseTo(0.0, within(1e-10));
        assertThat(charges[1]).isCloseTo(charges[2], within(1e-9));
        assertThat(charges[0]).isNegative();
        assertThat(charges[1]).isPositive();
    }

    @Test
    void should_give_a_cation_a_total_charge_of_one()
    {
        Vec3 hydrogen = new Vec3(0, 0, 1.4632);
        ScfResult result = ClosedShellScf.run(List.of(helium(Vec3.ZERO), hydrogen(hydrogen)), List.of(new PointCharge(Vec3.ZERO, 2.0), new PointCharge(hydrogen, 1.0)), 2);

        double[] charges = MullikenAnalysis.charges(result, List.of(0, 1), new double[] {2.0, 1.0});

        assertThat(charges[0] + charges[1]).isCloseTo(1.0, within(1e-10));
        assertThat(charges[1]).isGreaterThan(charges[0]);
    }

    @Test
    void should_count_the_whole_population_on_one_atom_when_every_function_is_assigned_to_it()
    {
        List<Integer> sites = new ArrayList<>();
        ScfResult result = water(sites);

        double[] populations = MullikenAnalysis.populations(result, List.of(0, 0, 0, 0, 0, 0, 0), 1);

        assertThat(populations[0]).isCloseTo(10.0, within(1e-10));
    }

    @Test
    void should_use_the_diagonal_of_the_density_times_overlap_not_a_row_sum()
    {
        // a hand-made result: two functions overlapping by 0.5, density chosen so P S has unequal diagonal and row sums
        Matrix overlap = Matrix.of(new double[][] {{1.0, 0.5}, {0.5, 1.0}});
        Matrix density = Matrix.of(new double[][] {{1.5, 0.2}, {0.2, 0.5}});
        ScfResult result = new ScfResult(true, 1, 0.0, 0.0, 0.0, new double[0], Matrix.identity(2), density, overlap);

        double[] populations = MullikenAnalysis.populations(result, List.of(0, 1), 2);

        // (P S)_00 = 1.5 * 1 + 0.2 * 0.5 = 1.6 and (P S)_11 = 0.2 * 0.5 + 0.5 * 1 = 0.6
        assertThat(populations[0]).isCloseTo(1.6, within(1e-14));
        assertThat(populations[1]).isCloseTo(0.6, within(1e-14));
    }
}
