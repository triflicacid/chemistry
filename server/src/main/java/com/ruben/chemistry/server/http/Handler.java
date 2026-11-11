package com.ruben.chemistry.server.http;

@FunctionalInterface
public interface Handler
{
    /**
     * @param request what was asked
     * @param context how to answer
     * @throws Exception on failure
     */
    void handle(Request request, Context context) throws Exception;
}
