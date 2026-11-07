package com.ruben.chemistry.smiles;

/**
 * The lexical categories of a SMILES {@link Token}.
 */
public enum TokenType
{
    /** An organic-subset atom symbol written outside brackets, e.g. {@code C}, {@code Cl}, {@code c}, {@code *}. */
    ATOM,

    /** The bracket-atom opening character {@code [}. */
    BRACKET_OPEN,

    /** The bracket-atom closing character {@code ]}. */
    BRACKET_CLOSE,

    /** An element symbol inside a bracket atom, e.g. {@code C}, {@code Fe}, {@code se}, {@code *}. Not restricted to the organic subset. */
    ELEMENT,

    /** A run of digits inside a bracket atom, e.g. an isotope, hydrogen count or atom class. */
    NUMBER,

    /** A chirality specifier inside a bracket atom: {@code @} or {@code @@}. */
    CHIRALITY,

    /** A single {@code +} or {@code -} inside a bracket atom. */
    SIGN,

    /** The atom-class separator {@code :} inside a bracket atom. */
    COLON,

    /** A bond symbol: one of {@code - = # $ : / \}. */
    BOND,

    /** The branch-opening character {@code (}. */
    BRANCH_OPEN,

    /** The branch-closing character {@code )}. */
    BRANCH_CLOSE,

    /** A ring-closure number, either a single digit or a {@code %} followed by two digits. */
    RING_BOND,

    /** The disconnected-structure separator {@code .}. */
    DOT,

    /** The end-of-source marker, produced once the source is exhausted. */
    EOF
}
