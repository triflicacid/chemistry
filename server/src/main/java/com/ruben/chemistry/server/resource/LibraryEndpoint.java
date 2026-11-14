package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.http.Endpoint;
import com.ruben.chemistry.server.http.EndpointRegistry;
import com.ruben.chemistry.server.http.HttpMethod;
import com.ruben.chemistry.server.http.Request;
import com.ruben.chemistry.server.http.ResponseContext;
import com.ruben.chemistry.server.library.Library;
import com.ruben.chemistry.util.CachedValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The routes for a library: everything, a search, and the categories.
 *
 * @param <L> the library served
 */
public abstract class LibraryEndpoint<L extends Library<?>> extends Endpoint
{
    private final String path;
    private final CachedValue<L> library;

    /**
     * @param path the route the library answers on, with its search and categories below it
     * @param load reads the library, called at most once
     */
    protected LibraryEndpoint(String path, Supplier<L> load)
    {
        this.path = path;
        this.library = new CachedValue<>(load);
    }

    @Override
    public final void registerHandlers(EndpointRegistry registry)
    {
        registry.register(HttpMethod.GET, path, this::all);
        registry.register(HttpMethod.GET, path + "/search", this::search);
        registry.register(HttpMethod.GET, path + "/categories", this::categories);
    }

    private void all(Request request, ResponseContext responseContext) throws IOException
    {
        responseContext.respond(200, library.get().all());
    }

    private void categories(Request request, ResponseContext responseContext) throws IOException
    {
        responseContext.respond(200, library.get().categories());
    }

    private void search(Request request, ResponseContext responseContext) throws IOException
    {
        String name = request.queryParameter("name").orElse("");
        responseContext.respond(200, library.get().search(name, wanted(request)));
    }

    /**
     * @param request the request to read
     * @return the categories asked for, given either as repeated parameters or comma-separated
     */
    private static List<String> wanted(Request request)
    {
        List<String> categories = new ArrayList<>();
        for (String given : request.queryParameters("category"))
        {
            categories.addAll(List.of(given.split(",")));
        }
        return categories;
    }
}
