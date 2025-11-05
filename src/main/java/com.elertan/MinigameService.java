package com.elertan;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.runelite.api.Client;

@Singleton
public class MinigameService {

    private static final int LAST_MAN_STANDING_VARBIT_ID = 5314;

    @Inject
    private Client client;

    public boolean isPlayingLastManStanding() {
        return client.getVarbitValue(LAST_MAN_STANDING_VARBIT_ID) == 1;
    }
}
