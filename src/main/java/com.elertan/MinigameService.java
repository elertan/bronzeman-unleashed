package com.elertan;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import net.runelite.api.Client;

@Singleton
public class MinigameService {

    // Varbits - https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/Varbits.java
    private static final int LAST_MAN_STANDING_VARBIT_ID = 5314;
    private static final int IN_RAID_VARBIT = 5432;  // CoX
    private static final int TOB_VARBIT = 6440;      // ToB
    private static final int BA_VARBIT = 3923;       // Barbarian Assault

    // Region IDs - https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/screenshot/ScreenshotPlugin.java
    private static final int INFERNO_REGION = 9043;
    private static final int GAUNTLET_REGION = 7512;
    private static final int CORRUPTED_GAUNTLET_REGION = 7768;

    // ToA Region IDs - https://github.com/LlemonDuck/tombs-of-amascut
    private static final Set<Integer> TOA_REGIONS = ImmutableSet.of(
        13454,  // Lobby
        14160,  // Nexus
        15698,  // Crondis
        15700,  // Zebak
        14162,  // Scabaras
        14164,  // Kephri
        15186,  // Apmeken
        15188,  // Ba-Ba
        14674,  // Het
        14676,  // Akkha
        15184,  // Wardens P1
        15696,  // Wardens P2
        14672   // Tomb
    );

    // Fight Caves Region IDs - https://github.com/runelite/runelite/pull/2351
    private static final Set<Integer> FIGHT_CAVES_REGIONS = ImmutableSet.of(
        9294, 9295, 9296,
        9550, 9551, 9552,
        9806, 9807, 9808
    );

    @Inject
    private Client client;

    public boolean isPlayingLastManStanding() {
        return client.getVarbitValue(LAST_MAN_STANDING_VARBIT_ID) == 1;
    }

    public boolean isInMinigameOrInstance() {
        if (isPlayingLastManStanding()) {
            return true;
        }

        // Raids via varbit
        if (client.getVarbitValue(IN_RAID_VARBIT) > 0) {
            return true;
        }
        if (client.getVarbitValue(TOB_VARBIT) > 0) {
            return true;
        }
        if (client.getVarbitValue(BA_VARBIT) > 0) {
            return true;
        }

        // Region-based (Inferno, Gauntlet, ToA, Fight Caves)
        int[] regions = client.getMapRegions();
        if (regions != null) {
            for (int region : regions) {
                if (region == INFERNO_REGION ||
                    region == GAUNTLET_REGION ||
                    region == CORRUPTED_GAUNTLET_REGION ||
                    TOA_REGIONS.contains(region) ||
                    FIGHT_CAVES_REGIONS.contains(region)) {
                    return true;
                }
            }
        }

        return false;
    }
}
