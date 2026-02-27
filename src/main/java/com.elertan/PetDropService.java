package com.elertan;

import com.elertan.chat.ParsedGameMessage.CollectionLogUnlockParsedGameMessage;
import com.elertan.chat.GameMessageParser;
import com.elertan.event.BUEvent.PetDropBUEvent;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.utils.TextUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * Detects pet drops via collection log messages, follower checks, inventory checks,
 * and handles the Probita edge case (follower + full inventory = unknown pet).
 * Tracks follower state each tick to know pre-drop state.
 */
@Slf4j
@Singleton
public class PetDropService implements BUPluginLifecycle {
    private static final String PET_MSG_FOLLOWING = "You have a funny feeling like you're being followed";
    private static final String PET_MSG_BACKPACK = "You feel something weird sneaking into your backpack";
    private static final String PET_MSG_DUPLICATE = "You have a funny feeling like you would have been followed";

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private BUEventService buEventService;
    @Inject private ItemManager itemManager;
    @Inject private AccountConfigurationService accountConfigurationService;

    private final Set<Integer> petItemIds = new HashSet<>();
    private final Set<Integer> inventoryPetItemIds = new HashSet<>();
    private final Set<Integer> previousInventoryPetItemIds = new HashSet<>();
    private boolean hadFollower = false;
    private boolean petsLoaded = false;

    @Override
    public void startUp() throws Exception {}

    @Override
    public void shutDown() throws Exception {
        petItemIds.clear();
        inventoryPetItemIds.clear();
        previousInventoryPetItemIds.clear();
        petsLoaded = false;
        hadFollower = false;
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            petsLoaded = false;
            hadFollower = false;
            inventoryPetItemIds.clear();
            previousInventoryPetItemIds.clear();
        }
    }

    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (!petsLoaded) loadPetItemIds();
        hadFollower = client.getFollower() != null;
        updateInventoryPetItems();
    }

    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM) return;
        if (!accountConfigurationService.isBronzemanEnabled()) return;
        handleGameMessage(event.getMessage());
    }

    public void handleGameMessage(String message) {
        CollectionLogUnlockParsedGameMessage parsed =
            GameMessageParser.tryParseCollectionLogUnlock(message);
        if (parsed != null) {
            handleCollectionLogPet(parsed.getItemName());
            return;
        }
        if (message.contains(PET_MSG_FOLLOWING) || message.contains(PET_MSG_BACKPACK)
            || message.contains(PET_MSG_DUPLICATE)) {
            handlePetDropMessage(message);
        }
    }

    private void loadPetItemIds() {
        if (petsLoaded || client.getGameState() != GameState.LOGGED_IN) return;
        try {
            EnumComposition petsEnum = client.getEnum(EnumID.PETS);
            petItemIds.clear();
            for (int i = 0; i < petsEnum.size(); i++) petItemIds.add(petsEnum.getIntValue(i));
            petsLoaded = true;
        } catch (Exception e) {
            log.error("Failed to load pet item IDs", e);
        }
    }

    private void updateInventoryPetItems() {
        previousInventoryPetItemIds.clear();
        previousInventoryPetItemIds.addAll(inventoryPetItemIds);
        inventoryPetItemIds.clear();
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) return;
        for (Item item : inventory.getItems()) {
            if (item.getId() > 0 && petItemIds.contains(item.getId())) {
                inventoryPetItemIds.add(item.getId());
            }
        }
    }

    private void handleCollectionLogPet(String itemName) {
        String cleanName = TextUtils.sanitizeItemName(itemName);
        for (int petItemId : petItemIds) {
            String petName = TextUtils.sanitizeItemName(
                itemManager.getItemComposition(petItemId).getName());
            if (cleanName.equals(petName)) {
                emitPetDropEvent(petItemId, false);
                return;
            }
        }
    }

    private void handlePetDropMessage(String message) {
        clientThread.invokeLater(() -> {
            Integer petItemId = identifyPetItemId(message);
            boolean isDuplicate = message.contains(PET_MSG_DUPLICATE);
            log.debug("Pet drop detected: {} (itemId: {}, isDuplicate: {})", message, petItemId, isDuplicate);
            emitPetDropEvent(petItemId, isDuplicate);
        });
    }

    @Nullable
    private Integer identifyPetItemId(String message) {
        // Player didn't have a follower - check for new follower
        if (!hadFollower) {
            NPC follower = client.getFollower();
            if (follower != null) {
                String followerName = follower.getName();
                for (int petItemId : petItemIds) {
                    ItemComposition comp = itemManager.getItemComposition(petItemId);
                    if (TextUtils.sanitizeItemName(comp.getName())
                        .equals(TextUtils.sanitizeItemName(followerName))) {
                        return petItemId;
                    }
                }
                log.warn("Found follower '{}' but no matching pet item ID", followerName);
                return null;
            }
        }
        // Player had a follower or pet went to backpack - check inventory
        if (hadFollower || message.contains(PET_MSG_BACKPACK)) {
            ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            if (inventory != null) {
                for (Item item : inventory.getItems()) {
                    int id = item.getId();
                    if (id > 0 && petItemIds.contains(id) && !previousInventoryPetItemIds.contains(id)) {
                        return id;
                    }
                }
            }
        }
        // Probita edge case: had follower and full inventory
        log.info("Could not identify pet - likely Probita edge case (message: {})", message);
        return null;
    }

    private void emitPetDropEvent(@Nullable Integer petItemId, boolean isDuplicate) {
        buEventService.publishEvent(new PetDropBUEvent(
            client.getAccountHash(), new ISOOffsetDateTime(OffsetDateTime.now()),
            petItemId, isDuplicate));
    }
}
