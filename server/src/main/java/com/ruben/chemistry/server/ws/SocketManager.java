package com.ruben.chemistry.server.ws;

import com.ruben.chemistry.server.http.PathPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Routes WebSocket connections to {@link WebSocketListener}s by path.
 * <p>
 * A route's path is a series of segments; a segment written {@code :name} matches any one
 * segment and exposes it as {@link WebSocketSession#pathParameter(String)}, e.g.
 * <pre>{@code
 * manager.register("/ws/studio/:moleculeId", StudioSocket::new);
 * }</pre>
 */
final class SocketManager implements SocketRegistry
{
    private final List<Route> routes = new ArrayList<>();

    /**
     * Register all the given endpoints with this manager.
     *
     * @param endpoints the endpoints to register
     * @return this
     */
    SocketManager registerAll(Iterable<? extends SocketEndpoint> endpoints)
    {
        for (SocketEndpoint endpoint : endpoints)
        {
            endpoint.registerHandlers(this);
        }
        return this;
    }

    @Override
    public void register(String path, SocketListenerFactory listeners)
    {
        PathPattern pattern = PathPattern.compile(path);

        for (Route existing : routes)
        {
            if (existing.pattern.sameShape(pattern))
            {
                throw new IllegalArgumentException(path + " is already registered");
            }
        }

        routes.add(new Route(pattern, listeners));
    }

    /**
     * @param path the path a connection was opened on
     * @return the route that answers it, or {@code null} if no route matches
     */
    Match match(String path)
    {
        List<String> segments = PathPattern.segments(path);

        for (Route route : routes)
        {
            Map<String, String> parameters = route.pattern.match(segments);
            if (parameters != null)
            {
                return new Match(route.listeners.create(), parameters);
            }
        }
        return null;
    }

    /**
     * @return every path registered, for logging what the server serves
     */
    List<String> paths()
    {
        return routes.stream().map(route -> route.pattern.toString()).toList();
    }

    /**
     * A matched route, ready to serve one connection.
     *
     * @param listener the listener made for this connection
     * @param pathParameters the {@code :name} segments the path filled in
     */
    record Match(WebSocketListener listener, Map<String, String> pathParameters)
    {
    }

    private record Route(PathPattern pattern, SocketListenerFactory listeners)
    {
    }
}
