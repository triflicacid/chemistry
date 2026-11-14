package com.ruben.chemistry.server.library;

import java.io.UncheckedIOException;
import java.util.List;

/**
 * The library of molecules, searchable by name and by category.
 */
public final class MoleculeLibrary extends Library<LibraryEntry>
{
    private static final char SEPARATOR = ',';
    private static final int COLUMNS = 3;

    private MoleculeLibrary(List<LibraryEntry> entries)
    {
        super(entries, LibraryEntry::name, LibraryEntry::classes);
    }

    /**
     * @param resource the classpath location of a {@code name,smiles,classes} CSV file
     * @return the library it holds
     * @throws IllegalStateException if the resource is missing
     * @throws UncheckedIOException if it cannot be read
     * @throws IllegalArgumentException if it is malformed
     */
    public static MoleculeLibrary load(String resource)
    {
        return new MoleculeLibrary(LibraryTable.load(resource, SEPARATOR, COLUMNS, MoleculeLibrary::entry));
    }

    /**
     * @param text the contents of a {@code name,smiles,classes} CSV file
     * @return the library it holds
     * @throws IllegalArgumentException if it is malformed
     */
    public static MoleculeLibrary parse(String text)
    {
        return new MoleculeLibrary(LibraryTable.parse(text, SEPARATOR, COLUMNS, MoleculeLibrary::entry));
    }

    /**
     * @param columns a line's {@code name,smiles,classes} columns
     * @return the molecule they describe
     * @throws IllegalArgumentException if the name or the SMILES is missing
     */
    private static LibraryEntry entry(String[] columns)
    {
        if (columns[0].isEmpty() || columns[1].isEmpty())
        {
            throw new IllegalArgumentException("not name,smiles");
        }
        return new LibraryEntry(columns[0], columns[1], LibraryTable.tags(columns[2]));
    }
}
