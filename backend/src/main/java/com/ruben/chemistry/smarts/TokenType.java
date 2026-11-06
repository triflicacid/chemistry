package com.ruben.chemistry.smarts;

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

    /** The atomic-number marker {@code #} inside a bracket atom, as in {@code #6}; always followed by a {@link #NUMBER}. */
    HASH,

    NUMBER,

    /** A chirality specifier inside a bracket atom: {@code @} or {@code @@}. */
    CHIRALITY,

    /** A single {@code +} or {@code -} inside a bracket atom. */
    SIGN,

    /** The atom-class separator {@code :} inside a bracket atom. */
    COLON,

    /** A single-letter structural primitive inside a bracket atom. */
    STRUCTURAL,

    /** A recursive-SMARTS primitive's {@code $(...)} contents, lexed whole: the text is everything between the parens. */
    EXPRESSION,

    /** A bond symbol: one of {@code - = # $ : / \ ~ @}. */
    BOND,

    /** The low-precedence logical AND, {@code ;}: combines atom primitives or bond terms. */
    SEMI,

    /** The high-precedence logical AND, {@code &}: combines atom primitives or bond terms. */
    AND,

    /** The logical OR, {@code ,}: combines atom primitives or bond terms. */
    OR,

    /** The logical NOT, {@code !}: negates the single primitive that follows it. */
    NOT,

    /** The branch-opening character {@code (}. */
    BRANCH_OPEN,

    /** The branch-closing character {@code )}. */
    BRANCH_CLOSE,

    /** A ring-closure number, either a single digit or a {@code %} followed by two digits. */
    RING_BOND,

    /** The disconnected-structure separator {@code .}. */
    DOT,

    EOF
}
