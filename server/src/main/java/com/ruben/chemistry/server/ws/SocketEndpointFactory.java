package com.ruben.chemistry.server.ws;

@FunctionalInterface
public interface SocketEndpointFactory
{
    SocketEndpoint create();
}
