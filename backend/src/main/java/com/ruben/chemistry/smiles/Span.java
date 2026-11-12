package com.ruben.chemistry.smiles;

/**
 * A range of characters in a SMILES string.
 *
 * @param start the index of the first character
 * @param end the index after the last character
 */
public record Span(int start, int end)
{
}
