package com.elertan.panel.screens.main.unlockedItems.items;

import com.elertan.data.MembersDataProvider;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel.SortedBy;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HeaderViewViewModel implements AutoCloseable {

    public final Property<String> searchText;
    public final Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy;
    public final Property<Long> unlockedByAccountHash;
    public final Property<List<Long>> accountHashesFromAllUnlockedItems;
    public final Property<Map<Long, String>> accountHashToMemberNameMap;
    private final Runnable navigateToConfiguration;
    private final MembersDataProvider membersDataProvider;
    private final MembersDataProvider.MemberMapListener memberMapListener;

    private HeaderViewViewModel(
        Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText,
        Property<SortedBy> sortedBy, Property<Long> unlockedByAccountHash,
        MembersDataProvider membersDataProvider, Runnable navigateToConfiguration
    ) {
        this.membersDataProvider = membersDataProvider;
        this.searchText = searchText;
        this.sortedBy = sortedBy;
        this.unlockedByAccountHash = unlockedByAccountHash;
        this.navigateToConfiguration = navigateToConfiguration;

        accountHashesFromAllUnlockedItems = allUnlockedItems.deriveAsync(items ->
            items == null || items.isEmpty() ? new ArrayList<>()
                : items.stream().map(UnlockedItem::getAcquiredByAccountHash).distinct().collect(Collectors.toList()));

        accountHashToMemberNameMap = new Property<>(buildAccountHashToMemberNameMap());
        memberMapListener = new MembersDataProvider.MemberMapListener() {
            @Override public void onUpdate(Member newMember, Member oldMember) { accountHashToMemberNameMap.set(buildAccountHashToMemberNameMap()); }
            @Override public void onDelete(Member member) { accountHashToMemberNameMap.set(buildAccountHashToMemberNameMap()); }
        };
        membersDataProvider.addMemberMapListener(memberMapListener);
        membersDataProvider.await(null).whenComplete((__, throwable) -> {
            if (throwable != null) return;
            accountHashToMemberNameMap.set(buildAccountHashToMemberNameMap());
        });
    }

    @Override
    public void close() throws Exception {
        membersDataProvider.removeMemberMapListener(memberMapListener);
    }

    public void onOpenConfigurationClick() {
        navigateToConfiguration.run();
    }

    private Map<Long, String> buildAccountHashToMemberNameMap() {
        Map<Long, Member> membersMap = membersDataProvider.getMembersMap();
        if (membersMap == null) return Collections.emptyMap();
        Map<Long, String> result = new HashMap<>();
        for (Member member : membersMap.values()) result.put(member.getAccountHash(), member.getName());
        return result;
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        HeaderViewViewModel create(
            Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText,
            Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
            Property<Long> unlockedByAccountHash, Runnable navigateToConfiguration);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Inject private MembersDataProvider membersDataProvider;

        @Override
        public HeaderViewViewModel create(
            Property<List<UnlockedItem>> allUnlockedItems, Property<String> searchText,
            Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
            Property<Long> unlockedByAccountHash, Runnable navigateToConfiguration
        ) {
            return new HeaderViewViewModel(
                allUnlockedItems, searchText, sortedBy, unlockedByAccountHash,
                membersDataProvider, navigateToConfiguration);
        }
    }
}
