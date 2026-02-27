package com.elertan;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import net.runelite.api.Client;

@Singleton
public class MinigameService {
    private static final int LMS_VARBIT = 5314;
    private static final int COX_VARBIT = 5432;
    private static final int TOB_VARBIT = 6440;
    private static final int BA_VARBIT = 3923;
    private static final int INFERNO_REGION = 9043;
    private static final int GAUNTLET_REGION = 7512;
    private static final int CORRUPTED_GAUNTLET_REGION = 7768;
    private static final Set<Integer> TOA_REGIONS = ImmutableSet.of(
        13454, 14160, 15698, 15700, 14162, 14164, 15186, 15188, 14674, 14676, 15184, 15696, 14672);
    private static final Set<Integer> FIGHT_CAVES_REGIONS = ImmutableSet.of(
        9294, 9295, 9296, 9550, 9551, 9552, 9806, 9807, 9808);
    private static final Set<Integer> INSTANCE_REGIONS = ImmutableSet.<Integer>builder()
        .add(INFERNO_REGION, GAUNTLET_REGION, CORRUPTED_GAUNTLET_REGION)
        .addAll(TOA_REGIONS).addAll(FIGHT_CAVES_REGIONS).build();

    @Inject private Client client;

    public boolean isPlayingLastManStanding() {
        return client.getVarbitValue(LMS_VARBIT) == 1;
    }

    public boolean isInMinigameOrInstance() {
        if (isPlayingLastManStanding()) return true;
        if (client.getVarbitValue(COX_VARBIT) > 0) return true;
        if (client.getVarbitValue(TOB_VARBIT) > 0) return true;
        if (client.getVarbitValue(BA_VARBIT) > 0) return true;
        int[] regions = client.getTopLevelWorldView().getMapRegions();
        if (regions != null) {
            for (int region : regions) {
                if (INSTANCE_REGIONS.contains(region)) return true;
            }
        }
        return false;
    }
}
