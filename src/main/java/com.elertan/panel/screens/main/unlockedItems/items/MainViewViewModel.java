package com.elertan.panel.screens.main.unlockedItems.items;

import com.elertan.ItemUnlockService;
import com.elertan.data.MembersDataProvider;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.NPCComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

public class MainViewViewModel implements AutoCloseable {

    public final Property<ViewState> viewState;
    public final Property<List<ListItem>> unlockedItemListItems;
    private final Property<List<UnlockedItem>> allUnlockedItems;
    private final Property<Map<Long, Member>> membersMap;
    private final Property<Map<Integer, String>> npcIdToNameMap = new Property<>(null);
    private final Map<Integer, AsyncBufferedImage> iconCache = new ConcurrentHashMap<>();
    private final MembersDataProvider membersDataProvider;
    private final ClientThread clientThread;
    private final Client client;
    private final ItemUnlockService itemUnlockService;
    private final PropertyChangeListener allUnlockedItemsListener = this::allUnlockedItemsListener;
    private final MembersDataProvider.MemberMapListener memberMapListener;
    private final ItemManager itemManager;

    private MainViewViewModel(
        Property<List<UnlockedItem>> allUnlockedItems,
        Property<String> searchText,
        Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
        Property<Long> unlockedByAccountHash,
        MembersDataProvider membersDataProvider,
        ItemManager itemManager,
        ClientThread clientThread,
        Client client,
        ItemUnlockService itemUnlockService
    ) {
        this.allUnlockedItems = allUnlockedItems;
        this.membersDataProvider = membersDataProvider;
        this.clientThread = clientThread;
        this.client = client;
        this.itemUnlockService = itemUnlockService;

        allUnlockedItems.addListener(allUnlockedItemsListener);
        onNewUnlockedItems(allUnlockedItems.get());

        memberMapListener = new MembersDataProvider.MemberMapListener() {
            @Override
            public void onUpdate(Member newMember, Member oldMember) {
                membersMap.set(membersDataProvider.getMembersMap());
            }

            @Override
            public void onDelete(Member member) {
                membersMap.set(membersDataProvider.getMembersMap());
            }
        };
        this.itemManager = itemManager;
        membersDataProvider.addMemberMapListener(memberMapListener);
        membersMap = new Property<>(membersDataProvider.getMembersMap());

        membersDataProvider.waitUntilReady(null).whenComplete((__, throwable) -> {
            if (throwable != null) {
                return;
            }
            membersMap.set(membersDataProvider.getMembersMap());
        });

        unlockedItemListItems = Property.deriveManyAsync(
            Arrays.asList(
                allUnlockedItems,
                membersMap,
                npcIdToNameMap,
                searchText,
                sortedBy,
                unlockedByAccountHash
            ),
            (values) -> {
                @SuppressWarnings("unchecked")
                List<UnlockedItem> allUnlockedItemsValue = (List<UnlockedItem>) values.get(0);
                @SuppressWarnings("unchecked")
                Map<Long, Member> membersMapValue = (Map<Long, Member>) values.get(1);
                @SuppressWarnings("unchecked")
                Map<Integer, String> npcIdToNameMapValue = (Map<Integer, String>) values.get(2);
                String searchTextValue = (String) values.get(3);
                UnlockedItemsScreenViewModel.SortedBy sortedByValue = (UnlockedItemsScreenViewModel.SortedBy) values.get(
                    4);
                Long unlockedByAccountHashValue = (Long) values.get(5);

                if (allUnlockedItemsValue == null || membersMapValue == null
                    || npcIdToNameMapValue == null) {
                    return null;
                }

                String searchTextLowerCase =
                    searchTextValue == null ? null : searchTextValue.toLowerCase().trim();
                if (searchTextLowerCase != null && searchTextLowerCase.isEmpty()) {
                    searchTextLowerCase = null;
                }

                final String searchTerm = searchTextLowerCase;
                return allUnlockedItemsValue.stream()
                    .filter(item -> {
                        if (unlockedByAccountHashValue != null
                            && item.getAcquiredByAccountHash() != unlockedByAccountHashValue) {
                            return false;
                        }
                        return searchTerm == null || item.getName()
                            .toLowerCase()
                            .contains(searchTerm);
                    })
                    .sorted((left, right) -> {
                        int value = 0;

                        switch (sortedByValue) {
                            case UNLOCKED_AT_ASC:
                                value = left.getAcquiredAt()
                                    .getValue()
                                    .compareTo(right.getAcquiredAt().getValue());
                                break;
                            case ALPHABETICAL_ASC:
                                value = left.getName().compareToIgnoreCase(right.getName());
                                break;
                            case PLAYER_ASC: {
                                Member leftMember = membersMapValue.get(left.getAcquiredByAccountHash());
                                Member rightMember = membersMapValue.get(right.getAcquiredByAccountHash());
                                if (leftMember != null && rightMember != null) {
                                    value = leftMember.getName()
                                        .compareToIgnoreCase(rightMember.getName());
                                } else if (leftMember == null) {
                                    value = 1;
                                } else {
                                    value = -1;
                                }
                                break;
                            }
                            case UNLOCKED_AT_DESC:
                                value = right.getAcquiredAt()
                                    .getValue()
                                    .compareTo(left.getAcquiredAt().getValue());
                                break;
                            case ALPHABETICAL_DESC:
                                value = right.getName().compareToIgnoreCase(left.getName());
                                break;
                            case PLAYER_DESC: {
                                Member leftMember = membersMapValue.get(left.getAcquiredByAccountHash());
                                Member rightMember = membersMapValue.get(right.getAcquiredByAccountHash());
                                if (leftMember != null && rightMember != null) {
                                    value = rightMember.getName()
                                        .compareToIgnoreCase(leftMember.getName());
                                } else if (leftMember == null) {
                                    value = -1;
                                } else {
                                    value = 1;
                                }
                                break;
                            }
                        }
                        if (value == 0) {
                            // Default to unlocked at descending for consistency when items are equal
                            return right.getAcquiredAt()
                                .getValue()
                                .compareTo(left.getAcquiredAt().getValue());
                        }
                        return value;
                    })
                    .map((unlockedItem) -> {
                        Member acquiredByMember = membersMapValue.get(unlockedItem.getAcquiredByAccountHash());
                        AsyncBufferedImage icon = getCachedIcon(unlockedItem.getId());
                        String droppedByNPCName = npcIdToNameMapValue.get(unlockedItem.getDroppedByNPCId());
                        return new ListItem(
                            unlockedItem,
                            acquiredByMember,
                            icon,
                            droppedByNPCName
                        );
                    })
                    .collect(Collectors.toList());
            }
        );
        viewState = unlockedItemListItems.derive(list -> list == null ? ViewState.LOADING
            : ViewState.READY);
    }

    @Override
    public void close() throws Exception {
        membersDataProvider.removeMemberMapListener(memberMapListener);
        allUnlockedItems.removeListener(allUnlockedItemsListener);
    }

    public void removeFromUnlockedItems(ListItem listItem) {
        UnlockedItem unlockedItem = listItem.getItem();
        if (unlockedItem == null) {
            return;
        }

        int result = JOptionPane.showConfirmDialog(
            null,
            String.format(
                "Removing '%s' from the unlocked items is a permanent action.\n\n",
                unlockedItem.getName()
            )
                + "This means in order to unlock this item again, you will need to acquire it again.",
            "Confirm remove unlocked item",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        int itemId = unlockedItem.getId();
        clientThread.invoke(() -> itemUnlockService.removeUnlockedItemById(itemId));

    }

    private void allUnlockedItemsListener(PropertyChangeEvent propertyChangeEvent) {
        List<UnlockedItem> newUnlockedItems = (List<UnlockedItem>) propertyChangeEvent.getNewValue();
        onNewUnlockedItems(newUnlockedItems);
    }

    private void onNewUnlockedItems(List<UnlockedItem> newUnlockedItems) {
        clientThread.invokeLater(() -> {
            if (newUnlockedItems == null) {
                npcIdToNameMap.set(null);
                return;
            }

            Map<Integer, String> npcIdToNameMapValue = new HashMap<>();
            for (UnlockedItem unlockedItem : newUnlockedItems) {
                Integer npcId = unlockedItem.getDroppedByNPCId();
                if (npcId == null || npcIdToNameMapValue.containsKey(npcId)) {
                    continue;
                }
                NPCComposition npcComposition = client.getNpcDefinition(unlockedItem.getDroppedByNPCId());
                npcIdToNameMapValue.put(npcId, npcComposition.getName());
            }
            npcIdToNameMap.set(npcIdToNameMapValue);
        });
    }

    private AsyncBufferedImage getCachedIcon(int id) {
        return iconCache.computeIfAbsent(id, itemManager::getImage);
    }

    public enum ViewState {
        LOADING,
        EMPTY,
        READY
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        MainViewViewModel create(
            Property<List<UnlockedItem>> allUnlockedItems,
            Property<String> searchText,
            Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
            Property<Long> unlockedByAccountHash
        );
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Inject
        private MembersDataProvider membersDataProvider;
        @Inject
        private ItemManager itemManager;
        @Inject
        private ClientThread clientThread;
        @Inject
        private Client client;
        @Inject
        private ItemUnlockService itemUnlockService;

        @Override
        public MainViewViewModel create(
            Property<List<UnlockedItem>> allUnlockedItems,
            Property<String> searchText,
            Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
            Property<Long> unlockedByAccountHash
        ) {
            return new MainViewViewModel(
                allUnlockedItems,
                searchText,
                sortedBy,
                unlockedByAccountHash,
                membersDataProvider,
                itemManager,
                clientThread,
                client,
                itemUnlockService
            );
        }
    }

    public static class ListItem {

        @Getter
        private final UnlockedItem item;
        @Getter
        private final Member acquiredByMember;
        @Getter
        private final AsyncBufferedImage icon;
        @Getter
        private final String droppedByNPCName;

        private ListItem(UnlockedItem item, Member acquiredByMember, AsyncBufferedImage icon,
            String droppedByNPCName) {
            this.item = item;
            this.acquiredByMember = acquiredByMember;
            this.icon = icon;
            this.droppedByNPCName = droppedByNPCName;
        }
    }
}
