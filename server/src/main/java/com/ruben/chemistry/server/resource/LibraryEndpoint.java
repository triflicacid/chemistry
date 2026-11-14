package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.http.Endpoint;
import com.ruben.chemistry.server.http.EndpointRegistry;
import com.ruben.chemistry.server.http.HttpMethod;
import com.ruben.chemistry.server.http.Request;
import com.ruben.chemistry.server.http.ResponseContext;
import com.ruben.chemistry.server.library.MoleculeLibrary;
import com.ruben.chemistry.util.CachedValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class LibraryEndpoint extends Endpoint
{
    private static final String RESOURCE = "/molecules.csv";

    private final CachedValue<MoleculeLibrary> library = new CachedValue<>(() -> MoleculeLibrary.load(RESOURCE));

    @Override
    public void registerHandlers(EndpointRegistry registry)
    {
        registry.register(HttpMethod.GET, "/api/library/search", this::search);
    }

    private void search(Request request, ResponseContext responseContext) throws IOException
    {
        String name = request.queryParameter("name").orElse("");

        List<String> categories = new ArrayList<>();
        for (String given : request.queryParameters("category"))
        {
            categories.addAll(List.of(given.split(",")));
        }

        responseContext.respond(200, library.get().search(name, categories));
    }
}
