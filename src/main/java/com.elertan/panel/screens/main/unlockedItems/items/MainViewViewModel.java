package com.elertan.panel.screens.main.unlockedItems.items;

import com.elertan.MemberService;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainViewViewModel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        MainViewViewModel create(Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText, Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy, Property<Long> unlockedByAccountHash);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject
        private MemberService memberService;

        @Override
        public MainViewViewModel create(Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText, Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy, Property<Long> unlockedByAccountHash) {
            return new MainViewViewModel(allUnlockedItems, searchText, sortedBy, unlockedByAccountHash, memberService);
        }
    }

    public enum ViewState {
        LOADING,
        EMPTY,
        READY
    }

    public final Property<ViewState> viewState;
    public final Property<List<UnlockedItem>> unlockedItems;

    private MainViewViewModel(Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText, Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy, Property<Long> unlockedByAccountHash, MemberService memberService) {
        unlockedItems = Property.deriveManyAsync(
                Arrays.asList(allUnlockedItems, searchText, sortedBy, unlockedByAccountHash),
                (values) -> {
                    @SuppressWarnings("unchecked")
                    List<UnlockedItem> allUnlockedItemsValue = (List<UnlockedItem>) values.get(0);
                    String searchTextValue = (String) values.get(1);
                    UnlockedItemsScreenViewModel.SortedBy sortedByValue = (UnlockedItemsScreenViewModel.SortedBy) values.get(2);
                    Long unlockedByAccountHashValue = (Long) values.get(3);

                    if (allUnlockedItemsValue == null) {
                        return null;
                    }

                    String searchTextLowerCase = searchTextValue == null ? null : searchTextValue.toLowerCase().trim();
                    if (searchTextLowerCase != null && searchTextLowerCase.isEmpty()) {
                        searchTextLowerCase = null;
                    }

                    final String searchTerm = searchTextLowerCase;
                    return allUnlockedItemsValue.stream()
                            .filter(item -> {
                                if (unlockedByAccountHashValue != null && item.getAcquiredByAccountHash() != unlockedByAccountHashValue) {
                                    return false;
                                }
                                return searchTerm == null || item.getName().toLowerCase().contains(searchTerm);
                            })
                            .sorted((left, right) -> {
                                int value = 0;

                                switch (sortedByValue) {
                                    case UNLOCKED_AT_ASC:
                                        value = left.getAcquiredAt().getValue().compareTo(right.getAcquiredAt().getValue());
                                        break;
                                    case ALPHABETICAL_ASC:
                                        value = left.getName().compareToIgnoreCase(right.getName());
                                        break;
                                    case PLAYER_ASC: {
                                        Member leftMember = null;
                                        try {
                                            leftMember = memberService.getMemberByAccountHash(left.getAcquiredByAccountHash());
                                        } catch (Exception ignored) {}
                                        Member rightMember = null;
                                        try {
                                            rightMember = memberService.getMemberByAccountHash(right.getAcquiredByAccountHash());
                                        } catch (Exception ignored) {}
                                        if (leftMember != null && rightMember != null) {
                                            value = leftMember.getName().compareToIgnoreCase(rightMember.getName());
                                        } else if (leftMember == null) {
                                            value = 1;
                                        } else {
                                            value = -1;
                                        }
                                        break;
                                    }
                                    case UNLOCKED_AT_DESC:
                                        value = right.getAcquiredAt().getValue().compareTo(left.getAcquiredAt().getValue());
                                        break;
                                    case ALPHABETICAL_DESC:
                                        value = right.getName().compareToIgnoreCase(left.getName());
                                        break;
                                    case PLAYER_DESC: {
                                        Member leftMember = null;
                                        try {
                                            leftMember = memberService.getMemberByAccountHash(left.getAcquiredByAccountHash());
                                        } catch (Exception ignored) {}
                                        Member rightMember = null;
                                        try {
                                            rightMember = memberService.getMemberByAccountHash(right.getAcquiredByAccountHash());
                                        } catch (Exception ignored) {}
                                        if (leftMember != null && rightMember != null) {
                                            value = rightMember.getName().compareToIgnoreCase(leftMember.getName());
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
                                    return right.getAcquiredAt().getValue().compareTo(left.getAcquiredAt().getValue());
                                }
                                return value;
                            })
                            .collect(Collectors.toList());
                }
        );
        viewState = unlockedItems.derive(list -> list == null ? ViewState.LOADING : ViewState.READY);
    }

    @Override
    public void close() throws Exception {

    }
}
