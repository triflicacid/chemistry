package com.ruben.chemistry.server.studio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

final class FieldEncoding
{
    private FieldEncoding()
    {
    }

    /**
     * @param values a number at each point of a grid
     * @return them as little-endian 32-bit floats in base64
     */
    static String encode(float[] values)
    {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values)
        {
            bytes.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(bytes.array());
    }

    /**
     * @param values a number at each point of a grid
     * @return the least of them
     */
    static double least(float[] values)
    {
        double least = Double.MAX_VALUE;
        for (float value : values)
        {
            least = Math.min(least, value);
        }
        return least;
    }

    /**
     * @param values a number at each point of a grid
     * @return the greatest of them
     */
    static double greatest(float[] values)
    {
        double greatest = -Double.MAX_VALUE;
        for (float value : values)
        {
            greatest = Math.max(greatest, value);
        }
        return greatest;
    }
}
