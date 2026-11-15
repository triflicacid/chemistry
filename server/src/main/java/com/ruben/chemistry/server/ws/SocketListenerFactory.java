package com.ruben.chemistry.server.ws;

@FunctionalInterface
public interface SocketListenerFactory
{
    /**
     * @return a listener for one new connection
     */
    WebSocketListener create();
}
