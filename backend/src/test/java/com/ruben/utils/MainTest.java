package com.ruben.utils;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

class MainTest
{
    @Test
    void success()
    {
        assertEquals(1, 1);
    }

    @Test
    void failure()
    {
        assertEquals(1, 2);
    }

    @Test
    void list()
    {
        assertThat(IntStream.range(0, 10));
    }
}
