package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.library.QueryLibrary;

public final class QueryLibraryEndpoint extends LibraryEndpoint<QueryLibrary>
{
    private static final String RESOURCE = "/queries.psv";

    public QueryLibraryEndpoint()
    {
        super("/api/library/query", () -> QueryLibrary.load(RESOURCE));
    }
}
