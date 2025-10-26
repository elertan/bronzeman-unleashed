package com.elertan;

import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BUSoundHelper {
    private static final int DISABLED_SOUND_EFFECT_ID = 2277;

    @Inject
    private Client client;

    public void playDisabledSound() {
        client.playSoundEffect(DISABLED_SOUND_EFFECT_ID);
    }
}
