package com.ruben.chemistry.formula;

import com.ruben.chemistry.canonical.MoleculeComparator;
import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@link Molecule}'s fragments gathered into the units written either side of a {@code .} in a
 * formula, as copper(II) sulfate pentahydrate is written {@code CuSO4.5H2O} rather than {@code CuH10SO9}.
 * <p>
 * This decides which fragments make up each unit and what order the units are written in.
 * How a unit's structure is then written is left to the {@link FormulaSettings} chosen for it.
 */
public final class FragmentedFormula
{
    private final List<FormulaUnit> units;

    /**
     * @param units the units, in the order they are to be written
     */
    private FragmentedFormula(List<FormulaUnit> units)
    {
        this.units = units;
    }

    /**
     * Gathers a molecule's fragments into units.
     * Fragments that are the same structure, stereochemistry included, are gathered into one unit with a count.
     *
     * @param molecule the molecule to gather
     * @param grouping how to gather the fragments into units
     * @return the fragmented formula
     */
    public static FragmentedFormula of(Molecule molecule, FragmentGrouping grouping)
    {
        return of(molecule, grouping, null);
    }

    /**
     * Gathers a molecule's fragments into units.
     * <p>
     * Fragments the comparator finds no difference between are gathered into one unit with a
     * count, so five waters give a single unit counted five times. Units are written in the order
     * their first fragment appears in the molecule.
     *
     * @param molecule the molecule to gather
     * @param grouping how to gather the fragments into units
     * @param structureComparator decides whether two fragments are the same structure, comparing
     *     them equal exactly when they are, or {@code null} to compare by the default
     *     {@link MoleculeComparator}
     * @return the fragmented formula
     */
    public static FragmentedFormula of(
        Molecule molecule,
        FragmentGrouping grouping,
        Comparator<Molecule> structureComparator)
    {
        if (grouping == FragmentGrouping.NONE)
        {
            return new FragmentedFormula(List.of(new FormulaUnit(molecule, 1)));
        }

        List<Molecule> fragments = molecule.fragments();
        if (fragments.isEmpty())
        {
            return new FragmentedFormula(List.of());
        }

        List<FormulaUnit> units = new ArrayList<>();
        if (grouping == FragmentGrouping.NEUTRAL_UNITS)
        {
            List<Molecule> ions = fragments.stream().filter(FragmentedFormula::isCharged).toList();
            if (!ions.isEmpty())
            {
                units.add(new FormulaUnit(combine(ions), 1));
                fragments = fragments.stream().filter(fragment -> !isCharged(fragment)).toList();
            }
        }
        units.addAll(gatherIdentical(fragments, structureComparator));

        return new FragmentedFormula(List.copyOf(units));
    }

    /**
     * @param fragment the fragment to test
     * @return whether the fragment's atoms carry a net formal charge
     */
    private static boolean isCharged(Molecule fragment)
    {
        return MolecularFormula.of(fragment).charge() != 0;
    }

    /**
     * Joins fragments into one disconnected structure.
     *
     * @param fragments the fragments to join, at least one
     * @return the fragments as a single structure, or the fragment itself if there is only one
     */
    private static Molecule combine(List<Molecule> fragments)
    {
        if (fragments.size() == 1)
        {
            return fragments.getFirst();
        }

        Molecule.Builder builder = new Molecule.Builder();
        fragments.forEach(fragment -> fragment.atoms().forEach(builder::addAtom));
        fragments.forEach(fragment -> fragment.bonds().forEach(builder::addBond));
        fragments.forEach(fragment -> copyStereochemistry(fragment, builder));
        return builder.build();
    }

    /**
     * @param fragment the fragment to copy from
     * @param builder the builder to copy onto
     */
    private static void copyStereochemistry(Molecule fragment, Molecule.Builder builder)
    {
        for (Atom atom : fragment.atoms())
        {
            fragment.chiralityOf(atom).ifPresent(chirality -> builder.setChirality(atom, chirality));
        }
        for (Bond bond : fragment.bonds())
        {
            fragment.doubleBondStereoOf(bond)
                .ifPresent(stereo -> builder.setDoubleBondStereo(bond.first(), bond.second(), stereo));
        }
    }

    /**
     * Gathers fragments the comparator finds no difference between into a single counted unit.
     *
     * @param fragments the fragments to gather
     * @param structureComparator decides whether two fragments are the same structure.
     * @return one unit per distinct structure
     */
    private static List<FormulaUnit> gatherIdentical(List<Molecule> fragments, Comparator<Molecule> structureComparator)
    {
        Comparator<Molecule> structure = structureComparator == null ? new MoleculeComparator() : structureComparator;

        List<Molecule> representatives = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Molecule fragment : fragments)
        {
            int index = indexOfMatching(representatives, fragment, structure);
            if (index == -1)
            {
                representatives.add(fragment);
                counts.add(1);
            }
            else
            {
                counts.set(index, counts.get(index) + 1);
            }
        }

        List<FormulaUnit> units = new ArrayList<>();
        for (int index = 0; index < representatives.size(); index++)
        {
            units.add(new FormulaUnit(representatives.get(index), counts.get(index)));
        }
        return units;
    }

    /**
     * @param representatives one fragment per distinct structure found so far
     * @param fragment the fragment to place
     * @param structure the ordering deciding whether two fragments are the same structure
     * @return the index of the representative {@code fragment} matches, or {@code -1} if it is a structure not yet seen
     */
    private static int indexOfMatching(List<Molecule> representatives, Molecule fragment, Comparator<Molecule> structure)
    {
        for (int index = 0; index < representatives.size(); index++)
        {
            if (structure.compare(representatives.get(index), fragment) == 0)
            {
                return index;
            }
        }
        return -1;
    }

    /**
     * @return the units, in the order they are written, unmodifiable
     */
    public List<FormulaUnit> units()
    {
        return units;
    }

    /**
     * @return the number of units written
     */
    public int unitCount()
    {
        return units.size();
    }

    /**
     * Writes the units, separated by a {@code .}, or by a middle dot where the style is Unicode.
     * Each unit is written by the settings {@code renderers} chooses for it.
     * <p>
     * If {@code style} is Unicode, each unit is written in Unicode, even if the settings {@code renderers} chooses for it are not.
     *
     * @param style the choices made about the formula as a whole
     * @param renderers how to write each unit, chosen per unit
     * @return the formula as text
     */
    public String render(FormulaStyle style, Function<FormulaUnit, FormulaSettings> renderers)
    {
        String separator = style.unicode() ? String.valueOf(Unicode.MIDDLE_DOT) : ".";
        return units.stream()
            .map(unit -> unit.render(inStyle(renderers.apply(unit), style)))
            .collect(Collectors.joining(separator));
    }

    /**
     * @param settings the settings chosen for a unit
     * @param style the choices made about the formula as a whole
     * @return the settings, writing in Unicode exactly when the formula as a whole does
     */
    private static FormulaSettings inStyle(FormulaSettings settings, FormulaStyle style)
    {
        return settings.withStyle(settings.style().withUnicode(style.unicode()));
    }

    /**
     * @return the formula with every unit written as a plain molecular formula, e.g. {@code CuSO4.5H2O}
     */
    @Override
    public String toString()
    {
        return render(FormulaStyle.PLAIN, unit -> FormulaSettings.molecular());
    }

    /**
     * @return the formula with every unit written as a Unicode molecular formula, e.g. {@code CuSO₄·5H₂O}
     */
    public String toUnicode()
    {
        return render(FormulaStyle.UNICODE, unit -> FormulaSettings.molecular());
    }
}
