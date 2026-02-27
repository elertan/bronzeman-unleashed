package com.elertan;

import com.elertan.event.BUEvent.DiaryCompletionAchievementBUEvent;
import com.elertan.models.AchievementDiaryArea;
import com.elertan.models.AchievementDiaryTier;
import com.elertan.models.ISOOffsetDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;

@Slf4j
public class AchievementDiaryService implements BUPluginLifecycle {

    private static final AchievementDiaryArea[] AREAS = AchievementDiaryArea.values();
    private static final AchievementDiaryTier[] TIERS = AchievementDiaryTier.values();

    // Rows = areas (enum order), Cols = tiers (Easy, Medium, Hard, Elite)
    private static final int[][] VARBIT_IDS = {
        /* Ardougne   */ { VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE,  VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,  VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE,  VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE  },
        /* Desert     */ { VarbitID.DESERT_DIARY_EASY_COMPLETE,    VarbitID.DESERT_DIARY_MEDIUM_COMPLETE,    VarbitID.DESERT_DIARY_HARD_COMPLETE,    VarbitID.DESERT_DIARY_ELITE_COMPLETE    },
        /* Falador    */ { VarbitID.FALADOR_DIARY_EASY_COMPLETE,   VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE,   VarbitID.FALADOR_DIARY_HARD_COMPLETE,   VarbitID.FALADOR_DIARY_ELITE_COMPLETE   },
        /* Kandarin   */ { VarbitID.KANDARIN_DIARY_EASY_COMPLETE,  VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,  VarbitID.KANDARIN_DIARY_HARD_COMPLETE,  VarbitID.KANDARIN_DIARY_ELITE_COMPLETE  },
        /* Karamja    */ { VarbitID.ATJUN_EASY_DONE,               VarbitID.ATJUN_MED_DONE,                  VarbitID.ATJUN_HARD_DONE,               VarbitID.KARAMJA_DIARY_ELITE_COMPLETE   },
        /* Kourend    */ { VarbitID.KOUREND_DIARY_EASY_COMPLETE,   VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,   VarbitID.KOUREND_DIARY_HARD_COMPLETE,   VarbitID.KOUREND_DIARY_ELITE_COMPLETE   },
        /* Lumbridge  */ { VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE,  VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,  VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE,  VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE  },
        /* Morytania  */ { VarbitID.MORYTANIA_DIARY_EASY_COMPLETE,  VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,  VarbitID.MORYTANIA_DIARY_HARD_COMPLETE,  VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE  },
        /* Varrock    */ { VarbitID.VARROCK_DIARY_EASY_COMPLETE,    VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,    VarbitID.VARROCK_DIARY_HARD_COMPLETE,    VarbitID.VARROCK_DIARY_ELITE_COMPLETE    },
        /* Western    */ { VarbitID.WESTERN_DIARY_EASY_COMPLETE,    VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,    VarbitID.WESTERN_DIARY_HARD_COMPLETE,    VarbitID.WESTERN_DIARY_ELITE_COMPLETE    },
        /* Wilderness */ { VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE },
        /* Fremennik  */ { VarbitID.FREMENNIK_DIARY_EASY_COMPLETE,  VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE,  VarbitID.FREMENNIK_DIARY_HARD_COMPLETE,  VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE  },
    };

    private static final Map<Integer, int[]> VARBIT_TO_INDEX = new HashMap<>();
    static {
        for (int a = 0; a < VARBIT_IDS.length; a++) {
            for (int t = 0; t < VARBIT_IDS[a].length; t++) {
                VARBIT_TO_INDEX.put(VARBIT_IDS[a][t], new int[]{a, t});
            }
        }
    }

    private Map<Integer, Boolean> diaryCompletedMap = buildDiaryCompletedMap();
    private long gameTickSinceLogin = -1;

    @Inject
    private Client client;
    @Inject
    private BUEventService buEventService;
    @Inject
    private AccountConfigurationService accountConfigurationService;

    @Override
    public void startUp() throws Exception {
    }

    @Override
    public void shutDown() throws Exception {
    }

    public void onVarbitChanged(VarbitChanged event) {
        int varbitId = event.getVarbitId();
        int[] idx = VARBIT_TO_INDEX.get(varbitId);
        if (idx == null) {
            return;
        }
        AchievementDiaryArea area = AREAS[idx[0]];
        AchievementDiaryTier tier = TIERS[idx[1]];

        int value = event.getValue();
        boolean completed = value == 1;
        boolean previousCompleted = diaryCompletedMap.getOrDefault(varbitId, false);
        if (previousCompleted == completed) {
            return;
        }
        log.debug("{} {} diary value changed to {}", tier, area, completed);
        diaryCompletedMap.put(varbitId, completed);

        long minTicksRequired = 8;
        boolean hasPassedVarbitInitializationWindow =
            client.getTickCount() - gameTickSinceLogin >= minTicksRequired;
        if (!hasPassedVarbitInitializationWindow) {
            log.debug(
                "skipping diary completion event due to varbit initialization window not passed");
            return;
        }

        if (!accountConfigurationService.isReady()
            || accountConfigurationService.getCurrentAccountConfiguration() == null) {
            return;
        }

        DiaryCompletionAchievementBUEvent buEvent = new DiaryCompletionAchievementBUEvent(
            client.getAccountHash(),
            new ISOOffsetDateTime(OffsetDateTime.now()),
            tier,
            area
        );
        buEventService.publishEvent(buEvent);
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            gameTickSinceLogin = -1;
            buildDiaryCompletedMap();
        } else if (event.getGameState() == GameState.LOGGED_IN) {
            gameTickSinceLogin = client.getTickCount();
        }
    }

    private Map<Integer, Boolean> buildDiaryCompletedMap() {
        Map<Integer, Boolean> map = new HashMap<>();
        for (int[] row : VARBIT_IDS) {
            for (int varbitId : row) {
                map.put(varbitId, false);
            }
        }
        return diaryCompletedMap = map;
    }

    public int getVarbitForDiary(AchievementDiaryArea area, AchievementDiaryTier tier) {
        return VARBIT_IDS[area.ordinal()][tier.ordinal()];
    }
}
