package com.ruben.chemistry.server.render;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.formula.FormulaSettings;
import com.ruben.chemistry.formula.FormulaStyle;
import com.ruben.chemistry.formula.FragmentGrouping;
import com.ruben.chemistry.formula.FragmentedFormula;
import com.ruben.chemistry.formula.MolecularFormula;
import com.ruben.chemistry.render.MoleculeSnapshot;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.util.Span;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * @param smiles the SMILES the molecule was parsed from; atoms' spans are positions in it
 * @param canonicalSmiles the molecule as canonical SMILES; atoms' canonical spans are positions in it
 * @param formula the molecule's formulae
 * @param atoms one entry per atom, at its index in {@link Molecule#atoms()}
 * @param bonds one entry per bond, at its index in {@link Molecule#bonds()}
 * @param rings the rings of the molecule
 * @param fragments one entry per connected fragment, at its index in {@link Molecule#fragments()}
 */
public record MoleculeMetadata(String smiles, String canonicalSmiles, FormulaInfo formula, List<AtomInfo> atoms,
                               List<BondInfo> bonds, List<RingInfo> rings, List<FragmentInfo> fragments)
{
    /**
     * @param index the atom's index in the molecule
     * @param element the element symbol
     * @param atomicNumber the atomic number, or null if the element has none (e.g. a wildcard)
     * @param isotope the isotope mass number, or null if unspecified
     * @param charge the formal charge
     * @param hydrogens the atom's total hydrogens
     * @param valence the total valence
     * @param aromatic whether the atom is aromatic
     * @param rings the indices (into {@link MoleculeMetadata#rings()}) of the rings the atom is in
     * @param fragment the index of the connected fragment it is in
     * @param span where the atom is written in {@link MoleculeMetadata#smiles()}
     * @param canonicalSpan where the atom is written in {@link MoleculeMetadata#canonicalSmiles()}
     */
    public record AtomInfo(
            int index,
            String element,
            Integer atomicNumber,
            Integer isotope,
            int charge,
            int hydrogens,
            int valence,
            boolean aromatic,
            List<Integer> rings,
            int fragment,
            Span span,
            Span canonicalSpan)
    {
    }

    /**
     * @param index the bond's index in the molecule
     * @param first the index of one end atom
     * @param second the index of the other end atom
     * @param order {@code single}, {@code double}, {@code triple}, {@code quadruple} or {@code aromatic}
     * @param rings the indices (into {@link MoleculeMetadata#rings()}) of the rings the bond is in
     * @param spans where the bond is written in {@link MoleculeMetadata#smiles()}, which is empty for a
     *     chain bond written without a symbol, and has one entry per end for a ring-closure bond
     * @param canonicalSpans the same, in {@link MoleculeMetadata#canonicalSmiles()}
     */
    public record BondInfo(int index, int first, int second, String order, List<Integer> rings, List<Span> spans,
                           List<Span> canonicalSpans)
    {
    }

    /**
     * @param index the ring's position in {@link MoleculeMetadata#rings()}
     * @param size how many atoms the ring has
     * @param atoms the indices of its atoms
     * @param bonds the indices of its bonds
     * @param smiles the ring on its own, as canonical SMILES
     * @param formula the ring on its own, as a molecular formula in Unicode
     * @param spans where each of {@code atoms} is written in {@code smiles}, in the same order
     */
    public record RingInfo(int index, int size, List<Integer> atoms, List<Integer> bonds, String smiles, String formula,
                           List<Span> spans)
    {
    }

    /**
     * A molecule's formulae, each written in Unicode.
     *
     * @param molecular every atom counted together, e.g. glucose's {@code C₆H₁₂O₆}
     * @param empirical the molecular formula in its simplest whole-number ratio, e.g. glucose's {@code CH₂O}
     * @param condensed the condensed structural formula, e.g. ethanol's {@code CH₃CH₂OH}
     * @param components one unit per connected fragment, e.g. {@code Cu²⁺·SO₄²⁻·5H₂O}
     * @param neutralUnits as {@code components}, every charged fragment combined into one uncharged unit, e.g. {@code CuSO₄·5H₂O}
     */
    public record FormulaInfo(String molecular, String empirical, String condensed, FragmentedInfo components,
                              FragmentedInfo neutralUnits)
    {
    }

    /**
     * A formula written as units either side of a middle dot.
     *
     * @param text the formula
     * @param units the units written in it, in the order they appear
     */
    public record FragmentedInfo(String text, List<UnitInfo> units)
    {
    }

    /**
     * One unit of a fragmented formula.
     *
     * @param span where the unit is written in its formula's text, its count included
     * @param count how many of the unit there are
     * @param fragments the indices (into {@link MoleculeMetadata#fragments()}) of the fragments the unit stands for
     */
    public record UnitInfo(Span span, int count, List<Integer> fragments)
    {
    }

    /**
     * @param index the fragment's position in {@link MoleculeMetadata#fragments()}
     * @param atoms the indices of its atoms
     * @param bonds the indices of its bonds
     * @param smiles the fragment on its own, as canonical SMILES
     * @param formula the fragment on its own, as a molecular formula in Unicode
     * @param charge its net formal charge
     */
    public record FragmentInfo(int index, List<Integer> atoms, List<Integer> bonds, String smiles, String formula, int charge)
    {
    }

    /**
     * Where a molecule's atoms and bonds are written in one notation of it.
     *
     * @param smiles the notation
     * @param atomSpans where each atom is written in it
     * @param bondSpans where each bond is written in it
     */
    public record Notation(String smiles, Map<Atom, Span> atomSpans, Map<Bond, List<Span>> bondSpans)
    {
    }

    /**
     * @param molecule a molecule
     * @param source the SMILES it was parsed from, and where its atoms and bonds are written in it
     * @param canonical the molecule as canonical SMILES, and where they are written in that
     * @return the metadata of every atom, bond and ring in it
     */
    public static MoleculeMetadata of(Molecule molecule, Notation source, Notation canonical)
    {
        MoleculeSnapshot snapshot = MoleculeSnapshot.of(molecule);
        List<Atom> moleculeAtoms = molecule.atoms();
        List<Bond> moleculeBonds = molecule.bonds();

        Map<Atom, Integer> indexOfAtom = new IdentityHashMap<>();
        for (int index = 0; index < moleculeAtoms.size(); index++)
        {
            indexOfAtom.put(moleculeAtoms.get(index), index);
        }
        Map<Bond, Integer> indexOfBond = new IdentityHashMap<>();
        for (int index = 0; index < moleculeBonds.size(); index++)
        {
            indexOfBond.put(moleculeBonds.get(index), index);
        }

        List<List<Integer>> ringsOfAtom = emptyLists(moleculeAtoms.size());
        List<List<Integer>> ringsOfBond = emptyLists(moleculeBonds.size());
        List<RingInfo> rings = new ArrayList<>();
        for (Molecule ring : molecule.rings())
        {
            int index = rings.size();
            List<Integer> atoms = ring.atoms().stream().map(indexOfAtom::get).toList();
            List<Integer> bonds = bondIndices(molecule, indexOfBond, ring.bonds());
            atoms.forEach(atom -> ringsOfAtom.get(atom).add(index));
            bonds.forEach(bond -> ringsOfBond.get(bond).add(index));
            SmilesWriter writer = new SmilesWriter(ring).setRanker(new CanonicalRanker());
            String ringSmiles = writer.write();
            List<Span> spans = ring.atoms().stream().map(writer.atomSpans()::get).toList();
            rings.add(new RingInfo(index, atoms.size(), atoms, bonds, ringSmiles,
                    MolecularFormula.of(ring).toUnicode(), spans));
        }

        List<AtomInfo> atoms = new ArrayList<>();
        for (int index = 0; index < moleculeAtoms.size(); index++)
        {
            Atom atom = moleculeAtoms.get(index);
            atoms.add(new AtomInfo(
                    index,
                    atom.element(),
                    atomicNumberOf(atom.element()),
                    boxed(atom.isotope()),
                    atom.charge(),
                    molecule.totalHydrogenCount(atom),
                    molecule.valence(atom),
                    atom.aromatic(),
                    ringsOfAtom.get(index),
                    snapshot.getFragmentIndexOf(index),
                    source.atomSpans().get(atom),
                    canonical.atomSpans().get(atom)));
        }
        List<BondInfo> bonds = new ArrayList<>();
        for (int index = 0; index < moleculeBonds.size(); index++)
        {
            Bond bond = moleculeBonds.get(index);
            bonds.add(new BondInfo(index, indexOfAtom.get(bond.first()), indexOfAtom.get(bond.second()),
                    bond.order().name().toLowerCase(Locale.ROOT), ringsOfBond.get(index),
                    source.bondSpans().getOrDefault(bond, List.of()),
                    canonical.bondSpans().getOrDefault(bond, List.of())));
        }
        return new MoleculeMetadata(source.smiles(), canonical.smiles(), formulaOf(molecule), atoms, bonds, rings,
                fragmentsOf(molecule, snapshot, canonical, indexOfAtom, indexOfBond));
    }

    /**
     * Finds where a sub-molecule's bonds are in the molecule it came from.
     *
     * @param molecule the molecule the sub-molecule came from
     * @param indexOfBond each of the molecule's bonds' indices
     * @param bonds a sub-molecule's bonds
     * @return the indices of the molecule's own bonds between the same atoms, in the same order
     */
    private static List<Integer> bondIndices(Molecule molecule, Map<Bond, Integer> indexOfBond, List<Bond> bonds)
    {
        return bonds.stream()
                .map(copy -> molecule.bondsOf(copy.first()).stream()
                        .filter(own -> own.getOther(copy.first()) == copy.second())
                        .findFirst().orElseThrow())
                .map(indexOfBond::get)
                .toList();
    }

    /**
     * @param molecule a molecule
     * @return formulae for that molecule
     */
    private static FormulaInfo formulaOf(Molecule molecule)
    {
        MolecularFormula molecular = MolecularFormula.of(molecule);
        return new FormulaInfo(
                molecular.toUnicode(),
                molecular.empirical().toUnicode(),
                FormulaSettings.condensed().withStyle(FormulaStyle.UNICODE).write(molecule),
                fragmentedOf(molecule, FragmentGrouping.COMPONENTS),
                fragmentedOf(molecule, FragmentGrouping.NEUTRAL_UNITS));
    }

    /**
     * @param molecule a molecule
     * @param grouping how to gather its fragments into units
     * @return the fragmented formula
     */
    private static FragmentedInfo fragmentedOf(Molecule molecule, FragmentGrouping grouping)
    {
        FragmentedFormula formula = FragmentedFormula.of(molecule, grouping);
        FragmentedFormula.Rendered rendered = formula.renderWithSpans(FormulaStyle.UNICODE, unit -> FormulaSettings.molecular());

        List<UnitInfo> units = new ArrayList<>();
        for (int index = 0; index < formula.unitCount(); index++)
        {
            units.add(new UnitInfo(rendered.unitSpans().get(index), formula.units().get(index).count(), formula.fragmentsOf(index)));
        }
        return new FragmentedInfo(rendered.text(), units);
    }

    /**
     * @param molecule the molecule its fragments came from
     * @param snapshot the molecule, with its fragments to hand
     * @param canonical the molecule as canonical SMILES
     * @param indexOfAtom each of the molecule's atoms' indices
     * @param indexOfBond each of the molecule's bonds' indices
     * @return the metadata of every connected fragment, in the order the molecule holds them
     */
    private static List<FragmentInfo> fragmentsOf(
            Molecule molecule,
            MoleculeSnapshot snapshot,
            Notation canonical,
            Map<Atom, Integer> indexOfAtom,
            Map<Bond, Integer> indexOfBond)
    {
        int count = snapshot.getFragmentCount();
        List<FragmentInfo> fragments = new ArrayList<>();
        for (int index = 0; index < count; index++)
        {
            Molecule fragment = snapshot.getFragment(index);
            MolecularFormula formula = MolecularFormula.of(fragment);

            // a molecule of one fragment is that fragment, whose canonical SMILES is written already
            String smiles = count == 1
                    ? canonical.smiles()
                    : new SmilesWriter(fragment).setRanker(new CanonicalRanker()).write();
            fragments.add(new FragmentInfo(
                    index,
                    fragment.atoms().stream().map(indexOfAtom::get).toList(),
                    bondIndices(molecule, indexOfBond, fragment.bonds()),
                    smiles,
                    formula.toUnicode(),
                    formula.charge()));
        }
        return fragments;
    }

    private static List<List<Integer>> emptyLists(int count)
    {
        List<List<Integer>> lists = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            lists.add(new ArrayList<>());
        }
        return lists;
    }

    private static Integer atomicNumberOf(String element)
    {
        try
        {
            return Elements.atomicNumberOf(element);
        }
        catch (IllegalArgumentException unknown)
        {
            return null;
        }
    }

    private static Integer boxed(OptionalInt value)
    {
        return value.isPresent() ? value.getAsInt() : null;
    }
}
