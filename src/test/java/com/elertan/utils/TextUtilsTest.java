package com.elertan.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TextUtilsTest {

    @Test
    public void formatStackSizeMatchesTheGame() {
        assertEquals("1086", TextUtils.formatStackSize(1086));
        assertEquals("99999", TextUtils.formatStackSize(99_999));
        assertEquals("100K", TextUtils.formatStackSize(100_000));
        assertEquals("9999K", TextUtils.formatStackSize(9_999_999));
        assertEquals("10M", TextUtils.formatStackSize(10_000_000));
        assertEquals("2147M", TextUtils.formatStackSize(Integer.MAX_VALUE));
    }
}
