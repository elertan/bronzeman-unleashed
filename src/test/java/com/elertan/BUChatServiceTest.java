package com.elertan;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BUChatServiceTest {

    @Test
    public void resolveChatboxStyleUsesOpaqueColorInFixedMode() {
        assertEquals(
            BUChatService.ChatboxStyle.OPAQUE,
            BUChatService.resolveChatboxStyle(false, 1)
        );
    }

    @Test
    public void resolveChatboxStyleUsesOpaqueColorWhenResizableTransparencyDisabled() {
        assertEquals(
            BUChatService.ChatboxStyle.OPAQUE,
            BUChatService.resolveChatboxStyle(true, 0)
        );
    }

    @Test
    public void resolveChatboxStyleUsesTransparentColorWhenResizableTransparencyEnabled() {
        assertEquals(
            BUChatService.ChatboxStyle.TRANSPARENT,
            BUChatService.resolveChatboxStyle(true, 1)
        );
    }
}
