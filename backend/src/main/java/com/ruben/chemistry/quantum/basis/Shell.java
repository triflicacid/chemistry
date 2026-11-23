package com.ruben.chemistry.quantum.basis;

import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A shell of an atomic basis set.
 * <p>
 * E.g., a p shell with three primitives stands for the three functions p<sub>x</sub>, p<sub>y</sub> and p<sub>z</sub>
 *
 * @param angularMomentum the total angular momentum: 0 for s, 1 for p, 2 for d and so on
 * @param primitives the exponents and the coefficients tabulated for normalised primitives
 */
public record Shell(int angularMomentum, List<GaussianPrimitive> primitives)
{
    public Shell
    {
        if (angularMomentum < 0)
        {
            throw new IllegalArgumentException("angular momentum must not be negative, was " + angularMomentum);
        }
        if (primitives.isEmpty())
        {
            throw new IllegalArgumentException("a shell needs at least one primitive");
        }
        primitives = List.copyOf(primitives);
    }

    /**
     * @param centre the position of the atom the shell belongs to, in bohr
     * @return one normalised basis function per Cartesian component
     * @see AngularMomentum#shell(int)
     */
    public List<ContractedGaussian> functionsAt(Vec3 centre)
    {
        List<ContractedGaussian> functions = new ArrayList<>();
        for (AngularMomentum component : AngularMomentum.shell(angularMomentum))
        {
            functions.add(new ContractedGaussian(centre, component, primitives));
        }
        return functions;
    }
}
