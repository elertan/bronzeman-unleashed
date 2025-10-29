package com.elertan.panel.screens.main.unlockedItems.items;

import com.elertan.data.MembersDataProvider;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.*;
import java.util.stream.Collectors;

public class HeaderViewViewModel implements AutoCloseable {
    public final Property<String> searchText;
    public final Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy;
    public final Property<Long> unlockedByAccountHash;
    public final Property<List<Long>> accountHashesFromAllUnlockedItems;
    public final Property<Map<Long, String>> accountHashToMemberNameMap;
    private final MembersDataProvider membersDataProvider;
    private final MembersDataProvider.MemberMapListener memberMapListener;

    private HeaderViewViewModel(
            Property<List<UnlockedItem>> allUnlockedItems,
            Property<String> searchText,
            Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
            Property<Long> unlockedByAccountHash,
            MembersDataProvider membersDataProvider
    ) {
        this.membersDataProvider = membersDataProvider;

        this.searchText = searchText;
        this.sortedBy = sortedBy;
        this.unlockedByAccountHash = unlockedByAccountHash;

        accountHashesFromAllUnlockedItems = allUnlockedItems.deriveAsync(items -> {
            if (items == null || items.isEmpty()) {
                return new ArrayList<>();
            }

            return items.stream().map(UnlockedItem::getAcquiredByAccountHash).distinct().collect(Collectors.toList());
        });

        accountHashToMemberNameMap = new Property<>(buildAccountHashToMemberNameMap());

        memberMapListener = new MembersDataProvider.MemberMapListener() {
            @Override
            public void onUpdate(Member newMember, Member oldMember) {
                accountHashToMemberNameMap.set(buildAccountHashToMemberNameMap());
            }

            @Override
            public void onDelete(Member member) {
                accountHashToMemberNameMap.set(buildAccountHashToMemberNameMap());
            }
        };
        membersDataProvider.addMemberMapListener(memberMapListener);

        membersDataProvider.waitUntilReady(null).whenComplete((__, throwable) -> {
            if (throwable != null) {
                return;
            }
            accountHashToMemberNameMap.set(buildAccountHashToMemberNameMap());
        });
    }

    @Override
    public void close() throws Exception {
        membersDataProvider.removeMemberMapListener(memberMapListener);
    }

    public void onOpenConfigurationClick() {

    }

    private Map<Long, String> buildAccountHashToMemberNameMap() {
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null) {
            return Collections.emptyMap();
        }

        Map<Long, String> accountHashToMemberNameMap = new HashMap<>();
        for (Member member : membersMap.values()) {
            accountHashToMemberNameMap.put(member.getAccountHash(), member.getName());
        }
        return accountHashToMemberNameMap;
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        HeaderViewViewModel create(
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

        @Override
        public HeaderViewViewModel create(
                Property<List<UnlockedItem>> allUnlockedItems,
                Property<String> searchText,
                Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
                Property<Long> unlockedByAccountHash
        ) {
            return new HeaderViewViewModel(
                    allUnlockedItems,
                    searchText,
                    sortedBy,
                    unlockedByAccountHash,
                    membersDataProvider
            );
        }
    }
}
