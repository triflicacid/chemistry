package com.ruben.chemistry.validate;

/**
 * What part of a molecule a {@link Rule} is about.
 */
public enum Category
{
    /** The molecule as a whole: how many atoms it has, and how many pieces. */
    STRUCTURE,

    /** What the atoms are: their elements and isotopes. */
    ELEMENT,

    /** How many bonds and hydrogens an atom carries. */
    VALENCE,

    /** An atom's formal charge, and the molecule's electrons. */
    CHARGE,

    /** Aromaticity and the rings it is counted over. */
    AROMATICITY,

    /** A ring's size, and what its size leaves room for. */
    RING,

    /** Declared and undeclared stereochemistry. */
    STEREOCHEMISTRY,

    /** How the structure is written rather than what it is, so the finding is about the source text. */
    NOTATION
}
