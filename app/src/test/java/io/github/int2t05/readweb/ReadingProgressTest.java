package io.github.int2t05.readweb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ReadingProgressTest {
    @Test
    public void reportsBeginningMiddleAndEndOfAScrollablePage() {
        assertEquals(0, ReadingProgress.percent(0, 2000, 1f, 1000));
        assertEquals(50, ReadingProgress.percent(500, 2000, 1f, 1000));
        assertEquals(100, ReadingProgress.percent(1000, 2000, 1f, 1000));
    }

    @Test
    public void appliesWebViewScaleAndClampsOverscroll() {
        assertEquals(50, ReadingProgress.percent(1500, 2000, 2f, 1000));
        assertEquals(100, ReadingProgress.percent(9000, 2000, 1f, 1000));
        assertEquals(0, ReadingProgress.percent(-12, 2000, 1f, 1000));
    }

    @Test
    public void handlesContentThatFitsAndUnavailableMeasurements() {
        assertEquals(100, ReadingProgress.percent(0, 500, 1f, 1000));
        assertEquals(0, ReadingProgress.percent(0, 0, 1f, 1000));
        assertEquals(0, ReadingProgress.percent(0, 2000, 1f, 0));
        assertEquals(0, ReadingProgress.percent(100, 2000, 0f, 1000));
    }
}
