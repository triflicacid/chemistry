package com.ruben.chemistry.server.http;

@FunctionalInterface
public interface EndpointFactory
{
    Endpoint create();
}
