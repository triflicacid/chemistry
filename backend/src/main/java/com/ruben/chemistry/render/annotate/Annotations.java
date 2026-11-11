package com.ruben.chemistry.render.annotate;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.util.IdentityMap;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-atom and per-bond restyling of a depiction.
 */
public final class Annotations
{
    private static final Annotations EMPTY = new Builder().build();

    private final Map<Atom, AtomLook> atoms;
    private final Map<Bond, BondLook> bonds;
    private final AtomLook defaultAtom;
    private final BondLook defaultBond;
    private final Optional<AnnotationStyle> style;

    private Annotations(Map<Atom, AtomLook> atoms, Map<Bond, BondLook> bonds, AtomLook defaultAtom, BondLook defaultBond, Optional<AnnotationStyle> style)
    {
        this.atoms = IdentityMap.copyOf(atoms);
        this.bonds = IdentityMap.copyOf(bonds);
        this.defaultAtom = defaultAtom;
        this.defaultBond = defaultBond;
        this.style = style;
    }

    /**
     * @return annotations that change nothing
     */
    public static Annotations none()
    {
        return EMPTY;
    }

    /**
     * @return how halos and badges are drawn, which is {@link AnnotationStyle#defaults()} unless a style was given
     */
    public AnnotationStyle style()
    {
        return style.orElseGet(AnnotationStyle::defaults);
    }

    /**
     * @param atom an atom, by reference
     * @return the look of {@code atom}, resolved against the default atom look
     */
    public AtomLook lookOf(Atom atom)
    {
        return atoms.getOrDefault(atom, AtomLook.NONE).over(defaultAtom);
    }

    /**
     * @param bond a bond, by reference
     * @return the look of {@code bond}, resolved against the default bond look
     */
    public BondLook lookOf(Bond bond)
    {
        return bonds.getOrDefault(bond, BondLook.NONE).over(defaultBond);
    }

    /**
     * @return the atoms with a look of their own
     */
    public Set<Atom> annotatedAtoms()
    {
        return atoms.keySet();
    }

    /**
     * @return the bonds with a look of their own
     */
    public Set<Bond> annotatedBonds()
    {
        return bonds.keySet();
    }

    /**
     * @param other annotations to combine with these
     * @return annotations in which each aspect set by {@code other} wins over one set by these, including the style
     */
    public Annotations merge(Annotations other)
    {
        Builder builder = toBuilder();
        other.atoms.forEach(builder::atom);
        other.bonds.forEach(builder::bond);
        other.style.ifPresent(builder::style);
        return builder.defaultAtom(other.defaultAtom).defaultBond(other.defaultBond).build();
    }

    /**
     * @return a builder holding these annotations
     */
    public Builder toBuilder()
    {
        Builder builder = new Builder();
        atoms.forEach(builder::atom);
        bonds.forEach(builder::bond);
        style.ifPresent(builder::style);
        return builder.defaultAtom(defaultAtom).defaultBond(defaultBond);
    }

    public static final class Builder
    {
        private final Map<Atom, AtomLook> atoms = new IdentityHashMap<>();
        private final Map<Bond, BondLook> bonds = new IdentityHashMap<>();
        private AtomLook defaultAtom = AtomLook.NONE;
        private BondLook defaultBond = BondLook.NONE;
        private Optional<AnnotationStyle> style = Optional.empty();

        /**
         * @param style how halos and badges are drawn
         * @return this builder
         */
        public Builder style(AnnotationStyle style)
        {
            this.style = Optional.of(style);
            return this;
        }

        /**
         * @param atom the atom to restyle
         * @param look how to restyle it, layered over any look already given
         * @return this builder
         */
        public Builder atom(Atom atom, AtomLook look)
        {
            atoms.merge(atom, look, (earlier, later) -> later.over(earlier));
            return this;
        }

        /**
         * @param bond the bond to restyle
         * @param look how to restyle it, layered over any look already given
         * @return this builder
         */
        public Builder bond(Bond bond, BondLook look)
        {
            bonds.merge(bond, look, (earlier, later) -> later.over(earlier));
            return this;
        }

        /**
         * @param look the look of every atom without its own, layered over any default already given
         * @return this builder
         */
        public Builder defaultAtom(AtomLook look)
        {
            defaultAtom = look.over(defaultAtom);
            return this;
        }

        /**
         * @param look the look of every bond without its own, layered over any default already given
         * @return this builder
         */
        public Builder defaultBond(BondLook look)
        {
            defaultBond = look.over(defaultBond);
            return this;
        }

        /**
         * @param atom the atom to label
         * @param text the label, shown as the atom's badge
         * @return this builder
         */
        public Builder label(Atom atom, String text)
        {
            return atom(atom, AtomLook.NONE.withBadge(Badge.of(text)));
        }

        /**
         * @param bond the bond to label
         * @param text the label, shown as the bond's badge
         * @return this builder
         */
        public Builder label(Bond bond, String text)
        {
            return bond(bond, BondLook.NONE.withBadge(Badge.of(text)));
        }

        /**
         * @return the annotations built so far
         */
        public Annotations build()
        {
            return new Annotations(atoms, bonds, defaultAtom, defaultBond, style);
        }
    }
}
