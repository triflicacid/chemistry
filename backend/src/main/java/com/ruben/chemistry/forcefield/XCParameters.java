package com.ruben.chemistry.forcefield;

import static com.ruben.chemistry.forcefield.Validation.finite;

/**
 * The table S3 coefficients of the exchange-correlation fit.
 *
 * @param a the electron-electron coefficients
 * @param b the electron-nuclear coefficients
 */
public record XCParameters(double[] a, double[] b)
{
    public XCParameters
    {
        a = a.clone();
        b = b.clone();
        if (a.length == 0 || a.length != b.length)
        {
            throw new IllegalArgumentException("xc arrays must have equal nonzero length");
        }
        for (double value : a)
        {
            finite(value, "xc a coefficient");
        }
        for (double value : b)
        {
            finite(value, "xc b coefficient");
        }
    }

    /**
     * @param order the functional order
     * @return the electron-electron coefficient of that order
     */
    double a(int order)
    {
        return a[order];
    }

    /**
     * @param order the functional order
     * @return the electron-nuclear coefficient of that order
     */
    double b(int order)
    {
        return b[order];
    }

    /**
     * @return the number of fitted functional orders
     */
    int orderCount()
    {
        return a.length;
    }
}
