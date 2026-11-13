package com.ruben.chemistry.formula;

import com.ruben.chemistry.canonical.MoleculeComparator;
import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.util.Span;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

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
    private final List<List<Integer>> fragmentsOfUnit;

    /**
     * @param gathered the units, in the order they are to be written
     */
    private FragmentedFormula(List<Gathered> gathered)
    {
        this.units = gathered.stream().map(Gathered::unit).toList();
        this.fragmentsOfUnit = gathered.stream().map(Gathered::fragments).toList();
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
     *
     * @param molecule the molecule to gather
     * @param grouping how to gather the fragments into units
     * @param structureComparator decides whether two fragments are the same structure
     * @return the fragmented formula
     */
    public static FragmentedFormula of(
        Molecule molecule,
        FragmentGrouping grouping,
        Comparator<Molecule> structureComparator)
    {
        if (grouping == FragmentGrouping.NONE)
        {
            FormulaUnit whole = new FormulaUnit(molecule, 1);
            return new FragmentedFormula(List.of(new Gathered(whole, indices(molecule.fragmentCount()))));
        }

        List<Molecule> fragments = molecule.fragments();
        if (fragments.isEmpty())
        {
            return new FragmentedFormula(List.of());
        }

        List<Gathered> gathered = new ArrayList<>();
        List<Integer> remaining = indices(fragments.size());
        if (grouping == FragmentGrouping.NEUTRAL_UNITS)
        {
            List<Integer> ions = new ArrayList<>();
            List<Integer> neutral = new ArrayList<>();
            for (int position : remaining)
            {
                if (isCharged(fragments.get(position)))
                {
                    ions.add(position);
                }
                else
                {
                    neutral.add(position);
                }
            }
            if (!ions.isEmpty())
            {
                FormulaUnit unit = new FormulaUnit(combine(ions.stream().map(fragments::get).toList()), 1);
                gathered.add(new Gathered(unit, List.copyOf(ions)));

                // the ions are written as the one unit, so only the uncharged fragments are left to gather
                remaining = neutral;
            }
        }
        gathered.addAll(gatherIdentical(fragments, remaining, structureComparator));

        return new FragmentedFormula(gathered);
    }

    /**
     * @param count how many indices to list
     * @return the indices {@code 0} to {@code count - 1}
     */
    private static List<Integer> indices(int count)
    {
        return IntStream.range(0, count).boxed().toList();
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
     * @param fragments every one of the molecule's fragments, indexed as the molecule holds them
     * @param positions the indices of the fragments to gather, which may be some of {@code fragments} only
     * @param structureComparator decides whether two fragments are the same structure.
     * @return one unit per distinct structure, each with the fragments it was gathered from
     */
    private static List<Gathered> gatherIdentical(
        List<Molecule> fragments,
        List<Integer> positions,
        Comparator<Molecule> structureComparator)
    {
        Comparator<Molecule> structure = structureComparator == null ? new MoleculeComparator() : structureComparator;

        List<Molecule> representatives = new ArrayList<>();
        List<List<Integer>> members = new ArrayList<>();
        for (int position : positions)
        {
            Molecule fragment = fragments.get(position);
            int index = indexOfMatching(representatives, fragment, structure);
            if (index == -1)
            {
                representatives.add(fragment);
                members.add(new ArrayList<>(List.of(position)));
            }
            else
            {
                members.get(index).add(position);
            }
        }

        List<Gathered> gathered = new ArrayList<>();
        for (int index = 0; index < representatives.size(); index++)
        {
            List<Integer> gatheredFrom = List.copyOf(members.get(index));
            gathered.add(new Gathered(new FormulaUnit(representatives.get(index), gatheredFrom.size()), gatheredFrom));
        }
        return gathered;
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
     * Says which of the molecule's fragments a unit was gathered from, a fragment's index being
     * its position in {@link Molecule#fragments()}. Every fragment belongs to exactly one unit.
     * <p>
     * A unit counted more than once returns each fragment it stands for, so the {@code 5H2O} of
     * copper(II) sulfate pentahydrate returns all five waters.
     *
     * @param unitIndex a unit's position in {@link #units()}
     * @return the indices of the fragments the unit was gathered from
     * @throws IndexOutOfBoundsException if there is no such unit
     */
    public List<Integer> fragmentsOf(int unitIndex)
    {
        return fragmentsOfUnit.get(unitIndex);
    }

    /**
     * A fragmented formula as text, with each of its units located in it.
     *
     * @param text the formula
     * @param unitSpans where each unit is written in {@code text}, one per unit, in the order they are written
     */
    public record Rendered(String text, List<Span> unitSpans)
    {
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
        return renderWithSpans(style, renderers).text();
    }

    /**
     * Writes the formula as {@link #render} does, reporting where each unit landed in it.
     *
     * @param style the choices made about the formula as a whole
     * @param renderers how to write each unit, chosen per unit
     * @return the formula as text, and each unit's position in it
     */
    public Rendered renderWithSpans(FormulaStyle style, Function<FormulaUnit, FormulaSettings> renderers)
    {
        String separator = style.unicode() ? String.valueOf(Unicode.MIDDLE_DOT) : ".";
        StringBuilder text = new StringBuilder();
        List<Span> spans = new ArrayList<>();
        for (int index = 0; index < units.size(); index++)
        {
            if (index > 0)
            {
                text.append(separator);
            }
            int start = text.length();
            FormulaUnit unit = units.get(index);
            text.append(unit.render(inStyle(renderers.apply(unit), style)));
            spans.add(new Span(start, text.length()));
        }
        return new Rendered(text.toString(), List.copyOf(spans));
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

    /**
     * One unit of a fragmented formula, paired with the fragments it was gathered from.
     *
     * @param unit the unit
     * @param fragments the indices of the fragments it was gathered from
     */
    private record Gathered(FormulaUnit unit, List<Integer> fragments)
    {
    }
}
