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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import lombok.Value;
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
        this.itemManager = itemManager;

        allUnlockedItems.addListener(allUnlockedItemsListener);
        onNewUnlockedItems(allUnlockedItems.get());

        memberMapListener = new MembersDataProvider.MemberMapListener() {
            @Override public void onUpdate(Member newMember, Member oldMember) { membersMap.set(membersDataProvider.getMembersMap()); }
            @Override public void onDelete(Member member) { membersMap.set(membersDataProvider.getMembersMap()); }
        };
        membersDataProvider.addMemberMapListener(memberMapListener);
        membersMap = new Property<>(membersDataProvider.getMembersMap());

        membersDataProvider.await(null).whenComplete((__, throwable) -> {
            if (throwable != null) return;
            membersMap.set(membersDataProvider.getMembersMap());
        });

        unlockedItemListItems = Property.deriveManyAsync(
            Arrays.asList(allUnlockedItems, membersMap, npcIdToNameMap, searchText, sortedBy, unlockedByAccountHash),
            values -> {
                @SuppressWarnings("unchecked") List<UnlockedItem> items = (List<UnlockedItem>) values.get(0);
                @SuppressWarnings("unchecked") Map<Long, Member> members = (Map<Long, Member>) values.get(1);
                @SuppressWarnings("unchecked") Map<Integer, String> npcNames = (Map<Integer, String>) values.get(2);
                String searchTextVal = (String) values.get(3);
                UnlockedItemsScreenViewModel.SortedBy sort = (UnlockedItemsScreenViewModel.SortedBy) values.get(4);
                Long filterHash = (Long) values.get(5);

                if (items == null || members == null || npcNames == null) return null;

                String term = searchTextVal != null ? searchTextVal.toLowerCase().trim() : null;
                if (term != null && term.isEmpty()) term = null;
                final String searchTerm = term;

                return items.stream()
                    .filter(item -> (filterHash == null || item.getAcquiredByAccountHash() == filterHash)
                        && (searchTerm == null || item.getName().toLowerCase().contains(searchTerm)))
                    .sorted(buildSortComparator(sort, members))
                    .map(item -> new ListItem(
                        item, members.get(item.getAcquiredByAccountHash()),
                        getCachedIcon(item.getId()), npcNames.get(item.getDroppedByNPCId())))
                    .collect(Collectors.toList());
            });

        viewState = unlockedItemListItems.derive(list -> list == null ? ViewState.LOADING : ViewState.READY);
    }

    private static Comparator<UnlockedItem> buildSortComparator(
        UnlockedItemsScreenViewModel.SortedBy sortedBy, Map<Long, Member> membersMap
    ) {
        Function<UnlockedItem, String> memberName = i -> {
            Member m = membersMap.get(i.getAcquiredByAccountHash());
            return m != null ? m.getName() : null;
        };
        Comparator<UnlockedItem> primary;
        switch (sortedBy) {
            case UNLOCKED_AT_ASC:
                primary = (a, b) -> a.getAcquiredAt().getValue().compareTo(b.getAcquiredAt().getValue());
                break;
            case UNLOCKED_AT_DESC:
                primary = (a, b) -> b.getAcquiredAt().getValue().compareTo(a.getAcquiredAt().getValue());
                break;
            case ALPHABETICAL_ASC:
                primary = Comparator.comparing(UnlockedItem::getName, String.CASE_INSENSITIVE_ORDER);
                break;
            case ALPHABETICAL_DESC:
                primary = Comparator.comparing(UnlockedItem::getName, String.CASE_INSENSITIVE_ORDER).reversed();
                break;
            case PLAYER_ASC:
                primary = Comparator.comparing(memberName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                break;
            case PLAYER_DESC:
                primary = Comparator.<UnlockedItem, String>comparing(
                    memberName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed();
                break;
            default:
                primary = (a, b) -> 0;
                break;
        }
        return primary.thenComparing((a, b) -> b.getAcquiredAt().getValue().compareTo(a.getAcquiredAt().getValue()));
    }

    @Override
    public void close() throws Exception {
        membersDataProvider.removeMemberMapListener(memberMapListener);
        allUnlockedItems.removeListener(allUnlockedItemsListener);
    }

    public void removeFromUnlockedItems(ListItem listItem) {
        UnlockedItem unlockedItem = listItem.getItem();
        if (unlockedItem == null) return;

        int result = JOptionPane.showConfirmDialog(null,
            String.format("Removing '%s' from the unlocked items is a permanent action.\n\n", unlockedItem.getName())
                + "This means in order to unlock this item again, you will need to acquire it again.",
            "Confirm remove unlocked item", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) return;

        clientThread.invoke(() -> itemUnlockService.removeUnlockedItemById(unlockedItem.getId()));
    }

    @SuppressWarnings("unchecked")
    private void allUnlockedItemsListener(PropertyChangeEvent event) {
        onNewUnlockedItems((List<UnlockedItem>) event.getNewValue());
    }

    private void onNewUnlockedItems(List<UnlockedItem> newUnlockedItems) {
        clientThread.invokeLater(() -> {
            if (newUnlockedItems == null) { npcIdToNameMap.set(null); return; }
            Map<Integer, String> map = new HashMap<>();
            for (UnlockedItem item : newUnlockedItems) {
                Integer npcId = item.getDroppedByNPCId();
                if (npcId == null || map.containsKey(npcId)) continue;
                NPCComposition npc = client.getNpcDefinition(npcId);
                map.put(npcId, npc.getName());
            }
            npcIdToNameMap.set(map);
        });
    }

    private AsyncBufferedImage getCachedIcon(int id) {
        return iconCache.computeIfAbsent(id, itemManager::getImage);
    }

    public enum ViewState { LOADING, EMPTY, READY }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        MainViewViewModel create(
            Property<List<UnlockedItem>> allUnlockedItems,
            Property<String> searchText,
            Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
            Property<Long> unlockedByAccountHash);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private MembersDataProvider membersDataProvider;
        @Inject private ItemManager itemManager;
        @Inject private ClientThread clientThread;
        @Inject private Client client;
        @Inject private ItemUnlockService itemUnlockService;

        @Override
        public MainViewViewModel create(
            Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText,
            Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy, Property<Long> unlockedByAccountHash
        ) {
            return new MainViewViewModel(
                allUnlockedItems, searchText, sortedBy, unlockedByAccountHash,
                membersDataProvider, itemManager, clientThread, client, itemUnlockService);
        }
    }

    @Value
    public static class ListItem {
        UnlockedItem item;
        Member acquiredByMember;
        AsyncBufferedImage icon;
        String droppedByNPCName;
    }
}
