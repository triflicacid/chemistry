package com.ruben.chemistry.server.render;

import com.ruben.chemistry.canonical.CanonicalRanker;
import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.Elements;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.render.MoleculeSnapshot;
import com.ruben.chemistry.smiles.Lexer;
import com.ruben.chemistry.smiles.SmilesWriter;
import com.ruben.chemistry.smiles.SmilesWriter.Span;
import com.ruben.chemistry.smiles.Token;
import com.ruben.chemistry.smiles.TokenType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * @param smiles the SMILES the molecule was parsed from; atoms' spans are positions in it
 * @param canonicalSmiles the molecule as canonical SMILES; atoms' canonical spans are positions in it
 * @param atoms one entry per atom, at its index in {@link Molecule#atoms()}
 * @param bonds one entry per bond, at its index in {@link Molecule#bonds()}
 * @param rings the rings of the molecule
 */
public record MoleculeMetadata(String smiles, String canonicalSmiles, List<AtomInfo> atoms, List<BondInfo> bonds, List<RingInfo> rings)
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
     */
    public record BondInfo(int index, int first, int second, String order, List<Integer> rings)
    {
    }

    /**
     * @param index the ring's position in {@link MoleculeMetadata#rings()}
     * @param size how many atoms the ring has
     * @param atoms the indices of its atoms
     * @param bonds the indices of its bonds
     * @param smiles the ring on its own, as canonical SMILES
     * @param spans where each of {@code atoms} is written in {@code smiles}, in the same order
     */
    public record RingInfo(int index, int size, List<Integer> atoms, List<Integer> bonds, String smiles, List<Span> spans)
    {
    }

    /**
     * @param molecule a molecule
     * @param smiles the SMILES it was parsed from
     * @param canonicalSmiles the molecule as canonical SMILES
     * @param canonicalSpanOf where an atom of the molecule is written in {@code canonicalSmiles}
     * @return the metadata of every atom, bond and ring in it
     */
    public static MoleculeMetadata of(Molecule molecule, String smiles, String canonicalSmiles, Function<Atom, Span> canonicalSpanOf)
    {
        MoleculeSnapshot snapshot = MoleculeSnapshot.of(molecule);
        List<Span> sourceSpans = sourceSpans(smiles);
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
            // a ring's bonds are copies, but its atoms are the molecule's own, so find each original bond by its atoms
            List<Integer> bonds = ring.bonds().stream()
                    .map(copy -> molecule.bondsOf(copy.first()).stream()
                            .filter(own -> own.getOther(copy.first()) == copy.second())
                            .findFirst().orElseThrow())
                    .map(indexOfBond::get)
                    .toList();
            atoms.forEach(atom -> ringsOfAtom.get(atom).add(index));
            bonds.forEach(bond -> ringsOfBond.get(bond).add(index));
            SmilesWriter writer = new SmilesWriter(ring).setRanker(new CanonicalRanker());
            String ringSmiles = writer.write();
            List<Span> spans = ring.atoms().stream().map(writer.atomSpans()::get).toList();
            rings.add(new RingInfo(index, atoms.size(), atoms, bonds, ringSmiles, spans));
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
                    sourceSpans.size() == moleculeAtoms.size() ? sourceSpans.get(index) : null,
                    canonicalSpanOf.apply(atom)));
        }
        List<BondInfo> bonds = new ArrayList<>();
        for (int index = 0; index < moleculeBonds.size(); index++)
        {
            Bond bond = moleculeBonds.get(index);
            bonds.add(new BondInfo(index, indexOfAtom.get(bond.first()), indexOfAtom.get(bond.second()),
                    bond.order().name().toLowerCase(Locale.ROOT), ringsOfBond.get(index)));
        }
        return new MoleculeMetadata(smiles, canonicalSmiles, atoms, bonds, rings);
    }

    /**
     * @param smiles a SMILES string
     * @return where each atom is written in it, in the order the parser creates them
     */
    private static List<Span> sourceSpans(String smiles)
    {
        List<Span> spans = new ArrayList<>();
        int bracketStart = -1;
        for (Token token : new Lexer(smiles).tokenize())
        {
            if (token.type() == TokenType.ATOM)
            {
                spans.add(new Span(token.position(), token.position() + token.text().length()));
            }
            else if (token.type() == TokenType.BRACKET_OPEN)
            {
                bracketStart = token.position();
            }
            else if (token.type() == TokenType.BRACKET_CLOSE)
            {
                spans.add(new Span(bracketStart, token.position() + 1));
            }
        }
        return spans;
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
