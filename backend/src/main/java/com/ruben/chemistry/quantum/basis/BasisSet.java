package com.ruben.chemistry.quantum.basis;

import com.ruben.chemistry.quantum.Units;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The basis functions of one molecule in one shape.
 *
 * @param functions the basis functions, atom by atom in site order and shell by shell in library order
 * @param siteOfFunction the site index of the atom each function is centred on
 */
public record BasisSet(List<ContractedGaussian> functions, List<Integer> siteOfFunction)
{
    public BasisSet
    {
        if (functions.size() != siteOfFunction.size())
        {
            throw new IllegalArgumentException("every function needs a site");
        }
        functions = List.copyOf(functions);
        siteOfFunction = List.copyOf(siteOfFunction);
    }

    /**
     * @param structure the molecule, with positions in ångström
     * @param conformer the index of the shape to use
     * @param library the basis set to take each element's shells from
     * @return the basis functions, centred on the atoms with positions converted to bohr
     * @throws IllegalArgumentException if an element is not in the library or the conformer does not exist
     */
    public static BasisSet build(Structure3D structure, int conformer, BasisLibrary library)
    {
        if (conformer < 0 || conformer >= structure.conformers().size())
        {
            throw new IllegalArgumentException("no conformer " + conformer);
        }
        List<Vec3> positions = structure.conformers().get(conformer).positions();
        List<ContractedGaussian> functions = new ArrayList<>();
        List<Integer> siteOfFunction = new ArrayList<>();
        for (Structure3D.Site site : structure.sites())
        {
            ElementBasis element = library.element(site.element());
            Vec3 angstrom = positions.get(site.index());
            Vec3 centre = new Vec3(Units.toBohr(angstrom.x()), Units.toBohr(angstrom.y()), Units.toBohr(angstrom.z()));
            for (Shell shell : element.shells())
            {
                for (ContractedGaussian function : shell.functionsAt(centre))
                {
                    functions.add(function);
                    siteOfFunction.add(site.index());
                }
            }
        }
        return new BasisSet(functions, siteOfFunction);
    }

    /**
     * @return the number of basis functions
     */
    public int size()
    {
        return functions.size();
    }
}
