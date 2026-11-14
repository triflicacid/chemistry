package com.ruben.chemistry.server.library;

import java.util.List;

/**
 * A molecule of the library.
 *
 * @param name the unique name
 * @param smiles the molecule as SMILES
 * @param classes the lower-cased categories it belongs to
 */
public record LibraryEntry(String name, String smiles, List<String> classes)
{
}
