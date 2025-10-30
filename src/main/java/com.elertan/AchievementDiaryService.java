package com.elertan;

import com.elertan.event.DiaryCompletionAchievementBUEvent;
import com.elertan.models.AchievementDiaryArea;
import com.elertan.models.AchievementDiaryTier;
import com.elertan.models.ISOOffsetDateTime;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;

@Slf4j
public class AchievementDiaryService implements BUPluginLifecycle {

    // Bidirectional mapping: varbit id <-> (area, tier)
    private static final ImmutableBiMap<Integer, AchievementDiaryVarbitInfo> ACHIEVEMENT_DIARY_VARBIT_INFO_MAP =
        ImmutableBiMap.<Integer, AchievementDiaryVarbitInfo>builder()
            // Ardougne
            .put(
                VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Ardougne, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Ardougne, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Ardougne, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Ardougne, AchievementDiaryTier.Elite)
            )
            // Desert
            .put(
                VarbitID.DESERT_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Desert, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.DESERT_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Desert, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.DESERT_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Desert, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.DESERT_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Desert, AchievementDiaryTier.Elite)
            )
            // Falador
            .put(
                VarbitID.FALADOR_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Falador, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Falador, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.FALADOR_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Falador, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.FALADOR_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Falador, AchievementDiaryTier.Elite)
            )
            // Kandarin
            .put(
                VarbitID.KANDARIN_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Kandarin, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Kandarin, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.KANDARIN_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Kandarin, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.KANDARIN_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Kandarin, AchievementDiaryTier.Elite)
            )
            // Karamja (ATJUN_* are Karamja diary varbits)
            .put(
                VarbitID.ATJUN_EASY_DONE,
                info(AchievementDiaryArea.Karamja, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.ATJUN_MED_DONE,
                info(AchievementDiaryArea.Karamja, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.ATJUN_HARD_DONE,
                info(AchievementDiaryArea.Karamja, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.KARAMJA_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Karamja, AchievementDiaryTier.Elite)
            )
            // Kourend & Kebos
            .put(
                VarbitID.KOUREND_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Kourend, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Kourend, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.KOUREND_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Kourend, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.KOUREND_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Kourend, AchievementDiaryTier.Elite)
            )
            // Lumbridge & Draynor
            .put(
                VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Lumbridge, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Lumbridge, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Lumbridge, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Lumbridge, AchievementDiaryTier.Elite)
            )
            // Morytania
            .put(
                VarbitID.MORYTANIA_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Morytania, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Morytania, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.MORYTANIA_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Morytania, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Morytania, AchievementDiaryTier.Elite)
            )
            // Varrock
            .put(
                VarbitID.VARROCK_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Varrock, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Varrock, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.VARROCK_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Varrock, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.VARROCK_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Varrock, AchievementDiaryTier.Elite)
            )
            // Western Provinces
            .put(
                VarbitID.WESTERN_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Western, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Western, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.WESTERN_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Western, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.WESTERN_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Western, AchievementDiaryTier.Elite)
            )
            // Wilderness
            .put(
                VarbitID.WILDERNESS_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Wilderness, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Wilderness, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.WILDERNESS_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Wilderness, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Wilderness, AchievementDiaryTier.Elite)
            )
            // Fremennik
            .put(
                VarbitID.FREMENNIK_DIARY_EASY_COMPLETE,
                info(AchievementDiaryArea.Fremennik, AchievementDiaryTier.Easy)
            )
            .put(
                VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE,
                info(AchievementDiaryArea.Fremennik, AchievementDiaryTier.Medium)
            )
            .put(
                VarbitID.FREMENNIK_DIARY_HARD_COMPLETE,
                info(AchievementDiaryArea.Fremennik, AchievementDiaryTier.Hard)
            )
            .put(
                VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE,
                info(AchievementDiaryArea.Fremennik, AchievementDiaryTier.Elite)
            )
            .build();

    private Map<Integer, Boolean> diaryCompletedMap = buildDiaryCompletedMap();
    private long gameTickSinceLogin = -1;

    @Inject
    private Client client;
    @Inject
    private BUEventService buEventService;

    private static AchievementDiaryVarbitInfo info(AchievementDiaryArea area,
        AchievementDiaryTier tier) {
        return new AchievementDiaryVarbitInfo(area, tier);
    }

    @Override
    public void startUp() throws Exception {
    }

    @Override
    public void shutDown() throws Exception {

    }

    public void onVarbitChanged(VarbitChanged event) {
        int varbitId = event.getVarbitId();
        AchievementDiaryVarbitInfo info = getAchievementDiaryInfoForVarbit(varbitId);
        if (info == null) {
            return;
        }
        int value = event.getValue();
        boolean completed = value == 1;
        boolean previousCompleted = diaryCompletedMap.getOrDefault(varbitId, false);
        if (previousCompleted == completed) {
            return;
        }
        log.info("{} {} diary value changed to {}", info.tier, info.area, completed);
        diaryCompletedMap.put(varbitId, completed);

        long minTicksRequired = 8;
        boolean hasPassedVarbitInitializationWindow =
            client.getTickCount() - gameTickSinceLogin >= minTicksRequired;
        if (!hasPassedVarbitInitializationWindow) {
            log.info(
                "skipping diary completion event due to varbit initialization window not passed");
            return;
        }

        DiaryCompletionAchievementBUEvent buEvent = new DiaryCompletionAchievementBUEvent(
            client.getAccountHash(),
            new ISOOffsetDateTime(OffsetDateTime.now()),
            info.tier,
            info.area
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
        for (Integer varbitId : ACHIEVEMENT_DIARY_VARBIT_INFO_MAP.keySet()) {
            map.put(varbitId, false);
        }
        return diaryCompletedMap = map;
    }

    private AchievementDiaryVarbitInfo getAchievementDiaryInfoForVarbit(int varbitId) {
        return ACHIEVEMENT_DIARY_VARBIT_INFO_MAP.get(varbitId);
    }

    private Integer getVarbitFromAchievementDiaryInfo(AchievementDiaryArea area,
        AchievementDiaryTier tier) {
        BiMap<AchievementDiaryVarbitInfo, Integer> inverse = ACHIEVEMENT_DIARY_VARBIT_INFO_MAP.inverse();
        return inverse.get(new AchievementDiaryVarbitInfo(area, tier));
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    private static class AchievementDiaryVarbitInfo {

        @Getter
        private final AchievementDiaryArea area;
        @Getter
        private final AchievementDiaryTier tier;
    }
}
