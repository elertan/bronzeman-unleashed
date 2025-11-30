package com.elertan;

import com.elertan.event.PetDropBUEvent;
import com.elertan.models.ISOOffsetDateTime;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Service for detecting pet drops and emitting PetDropBUEvent.
 *
 * <p>Detection strategy:
 * <ol>
 *   <li><b>Collection log message</b> - When "New item added to your collection log: X" fires,
 *       we parse the pet name directly from the message. This only works for first-time drops.</li>
 *   <li><b>Follower check</b> - When a "funny feeling" message fires and the player didn't have
 *       a follower before, we check client.getFollower() to get the pet name.</li>
 *   <li><b>Inventory check</b> - When a "funny feeling" message fires and the player had a follower,
 *       we check for new pet items in inventory.</li>
 *   <li><b>Probita edge case</b> - When the player has a follower and full inventory, the pet
 *       goes to Probita (pet insurance NPC) and we cannot identify which pet it was.
 *       In this case, we emit the event with petName = null.</li>
 * </ol>
 *
 * <p>We track follower state pre-emptively on each game tick because we need to know
 * if the player had a follower BEFORE the pet drop message appeared.
 */
@Slf4j
@Singleton
public class PetDropService implements BUPluginLifecycle {

    // Chat message patterns
    private static final String PET_MESSAGE_FOLLOWING = "You have a funny feeling like you're being followed";
    private static final String PET_MESSAGE_BACKPACK = "You feel something weird sneaking into your backpack";
    private static final String PET_MESSAGE_DUPLICATE = "You have a funny feeling like you would have been followed";
    private static final Pattern COLLECTION_LOG_PATTERN = Pattern.compile(
        "New item added to your collection log: (.+)");

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private BUEventService buEventService;
    @Inject
    private ItemManager itemManager;
    @Inject
    private AccountConfigurationService accountConfigurationService;

    // All pet item IDs loaded from game enum
    private Set<Integer> petItemIds = new HashSet<>();

    // Track state for pet detection
    private boolean hadFollower = false;
    private Set<Integer> inventoryPetItemIds = new HashSet<>();
    // Previous tick's inventory pets - used to detect new pets added this tick
    private Set<Integer> previousInventoryPetItemIds = new HashSet<>();
    private boolean petsLoaded = false;

    @Override
    public void startUp() throws Exception {
        // Pets will be loaded on first game tick when client is ready
    }

    @Override
    public void shutDown() throws Exception {
        petItemIds.clear();
        inventoryPetItemIds.clear();
        previousInventoryPetItemIds.clear();
        petsLoaded = false;
        hadFollower = false;
    }

    /**
     * Load all pet item IDs from the game's PETS enum. Must be called on client thread when game
     * state is LOGGED_IN.
     */
    private void loadPetItemIds() {
        if (petsLoaded || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        try {
            EnumComposition petsEnum = client.getEnum(EnumID.PETS);
            petItemIds.clear();
            for (int i = 0; i < petsEnum.size(); i++) {
                int petItemId = petsEnum.getIntValue(i);
                petItemIds.add(petItemId);
            }
            petsLoaded = true;
            log.debug("Loaded {} pet item IDs", petItemIds.size());
        } catch (Exception e) {
            log.error("Failed to load pet item IDs", e);
        }
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
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        // Load pets on first tick after login
        if (!petsLoaded) {
            loadPetItemIds();
        }

        // Track follower state for pet detection
        hadFollower = client.getFollower() != null;

        // Track current pet items in inventory (save previous state first)
        updateInventoryPetItems();
    }

    private void updateInventoryPetItems() {
        // Save current state as previous before updating
        previousInventoryPetItemIds.clear();
        previousInventoryPetItemIds.addAll(inventoryPetItemIds);

        inventoryPetItemIds.clear();
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) {
            return;
        }

        for (Item item : inventory.getItems()) {
            if (item.getId() > 0 && petItemIds.contains(item.getId())) {
                inventoryPetItemIds.add(item.getId());
            }
        }
    }

    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM) {
            return;
        }

        if (!accountConfigurationService.isBronzemanEnabled()) {
            return;
        }

        String message = event.getMessage();

        // Check for collection log message first (gives exact pet name for first-time drops)
        Matcher collectionLogMatcher = COLLECTION_LOG_PATTERN.matcher(message);
        if (collectionLogMatcher.find()) {
            String itemName = collectionLogMatcher.group(1);
            // Check if this is a pet by looking up the item
            handleCollectionLogPet(itemName);
            return;
        }

        // Check for pet drop messages
        if (message.contains(PET_MESSAGE_FOLLOWING) ||
            message.contains(PET_MESSAGE_BACKPACK) ||
            message.contains(PET_MESSAGE_DUPLICATE)) {
            handlePetDropMessage(message);
        }
    }

    private void handleCollectionLogPet(String itemName) {
        // Check if this item name matches a pet
        // We need to verify it's actually a pet since collection log fires for all items
        for (int petItemId : petItemIds) {
            ItemComposition comp = itemManager.getItemComposition(petItemId);
            if (itemName.equals(comp.getName())) {
                log.debug("Pet drop detected from collection log: {}", itemName);
                emitPetDropEvent(itemName);
                return;
            }
        }
    }

    private void handlePetDropMessage(String message) {
        // Use clientThread.invokeLater to ensure game state has updated
        clientThread.invokeLater(() -> {
            String petName = identifyPet(message);
            log.debug("Pet drop detected: {} (name: {})", message, petName);
            emitPetDropEvent(petName);
        });
    }

    /**
     * Identify which pet was received based on current game state.
     *
     * @param message the chat message that triggered detection
     * @return pet name, or null if cannot be identified (Probita edge case)
     */
    @Nullable
    private String identifyPet(String message) {
        // Case 1: Player didn't have a follower - check for new follower
        if (!hadFollower) {
            NPC follower = client.getFollower();
            if (follower != null) {
                return follower.getName();
            }
        }

        // Case 2: Player had a follower - check inventory for new pet
        // Compare against previous tick's inventory to find newly added pet
        if (hadFollower || message.contains(PET_MESSAGE_BACKPACK)) {
            ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            if (inventory != null) {
                for (Item item : inventory.getItems()) {
                    int itemId = item.getId();
                    if (itemId > 0 && petItemIds.contains(itemId) &&
                        !previousInventoryPetItemIds.contains(itemId)) {
                        // Found a new pet in inventory (wasn't there last tick)
                        ItemComposition comp = itemManager.getItemComposition(itemId);
                        return comp.getName();
                    }
                }
            }
        }

        // Case 3: Probita edge case - had follower and full inventory
        // Cannot identify pet, return null
        log.debug("Could not identify pet - likely Probita edge case (follower + full inventory)");
        return null;
    }

    private void emitPetDropEvent(@Nullable String petName) {
        PetDropBUEvent event = new PetDropBUEvent(
            client.getAccountHash(),
            new ISOOffsetDateTime(OffsetDateTime.now()),
            petName
        );
        buEventService.publishEvent(event);
    }

    /**
     * Simulate a game message for testing pet detection.
     * This bypasses the actual ChatMessage event and directly processes the message.
     *
     * @param message the message to simulate
     */
    public void simulateGameMessage(String message) {
        log.debug("Simulating game message: {}", message);

        // Check for collection log message first
        Matcher collectionLogMatcher = COLLECTION_LOG_PATTERN.matcher(message);
        if (collectionLogMatcher.find()) {
            String itemName = collectionLogMatcher.group(1);
            handleCollectionLogPet(itemName);
            return;
        }

        // Check for pet drop messages
        if (message.contains(PET_MESSAGE_FOLLOWING) ||
            message.contains(PET_MESSAGE_BACKPACK) ||
            message.contains(PET_MESSAGE_DUPLICATE)) {
            handlePetDropMessage(message);
        }
    }
}
