package com.ruben.chemistry.server.library;

import java.util.List;

/**
 * A SMARTS pattern of the library.
 *
 * @param name the unique name
 * @param smarts the pattern itself
 * @param classes the lower-cased categories it belongs to
 * @param description what the pattern matches, in a sentence or two
 * @param example a molecule as SMILES that the pattern matches, or blank if none is given
 */
public record QueryEntry(String name, String smarts, List<String> classes, String description, String example)
{
}
