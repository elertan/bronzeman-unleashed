package com.elertan.utils;

import java.time.Duration;

public class TickUtils {

    private static final int SECONDS_PER_TICK_NUM = 3;  // 0.6 seconds per tick = 3/5
    private static final int SECONDS_PER_TICK_DEN = 5;

    public static Duration ticksToDuration(long ticks) {
        long q = ticks / SECONDS_PER_TICK_DEN;
        long r = ticks % SECONDS_PER_TICK_DEN;
        long seconds = q * SECONDS_PER_TICK_NUM + (r * (long) SECONDS_PER_TICK_NUM) / SECONDS_PER_TICK_DEN;
        int nanos = (int) (((r * (long) SECONDS_PER_TICK_NUM) % SECONDS_PER_TICK_DEN) * 200_000_000L);
        return Duration.ofSeconds(seconds, nanos);
    }
}
