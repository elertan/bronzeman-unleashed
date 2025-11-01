package com.elertan;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

@Singleton
public class BUSoundHelper {

    private static final int DISABLED_SOUND_EFFECT_ID = 2277;

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;

    public void playDisabledSound() {
        clientThread.invoke(() -> {
            client.playSoundEffect(DISABLED_SOUND_EFFECT_ID);
        });
    }
}
