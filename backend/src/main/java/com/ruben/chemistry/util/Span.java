package com.ruben.chemistry.util;

/**
 * A range of characters in a string of text.
 *
 * @param start the index of the first character
 * @param end the index after the last character
 */
public record Span(int start, int end)
{
}
