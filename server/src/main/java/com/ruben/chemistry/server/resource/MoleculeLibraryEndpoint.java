package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.library.MoleculeLibrary;

public final class MoleculeLibraryEndpoint extends LibraryEndpoint<MoleculeLibrary>
{
    private static final String RESOURCE = "/molecules.csv";

    public MoleculeLibraryEndpoint()
    {
        super("/api/library/molecule", () -> MoleculeLibrary.load(RESOURCE));
    }
}
