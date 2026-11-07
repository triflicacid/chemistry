package com.ruben.chemistry.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guards against multiple calls: throws an {@link IllegalStateException} if called more than once.
 * Thread-safe.
 */
public class CallGuard
{
    private final AtomicBoolean called = new AtomicBoolean(false);

    /**
     * Call the guard.
     * @throws IllegalStateException If the guard has been called before.
     */
    public void call()
    {
        if (!called.compareAndSet(false, true))
        {
            throw new IllegalStateException("CallGuard: method already called");
        }
    }

    /**
     * Try to call the guard. This counts as a successful call if possible.
     * @return {@code true} if successful, {@code false} otherwise.
     */
    public boolean tryCall()
    {
        return called.compareAndSet(false, true);
    }

    /**
     * @return Whether this guard has been called.
     */
    public boolean wasCalled()
    {
        return called.get();
    }
}
