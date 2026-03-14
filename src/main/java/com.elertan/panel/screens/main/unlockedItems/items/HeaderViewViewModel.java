package com.elertan.panel.screens.main.unlockedItems.items;

import com.elertan.AccountConfigurationService;
import com.elertan.models.AccountConfiguration;
import com.elertan.models.AccountConfiguration.StorageMode;
import com.elertan.data.MembersDataProvider;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel;
import com.elertan.panel.screens.main.UnlockedItemsScreenViewModel.SortedBy;
import com.elertan.ui.Property;
import com.elertan.utils.Subscription;
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
    public final Property<List<UnlockedItemsScreenViewModel.SortedBy>> sortedByOptions;
    public final Property<Boolean> showUnlockedByFilter;
    public final Property<List<Long>> accountHashesFromAllUnlockedItems;
    public final Property<Map<Long, String>> accountHashToMemberNameMap;
    private final Runnable navigateToConfiguration;
    private final MembersDataProvider membersDataProvider;
    private final MembersDataProvider.MemberMapListener memberMapListener;
    private final Subscription accountConfigurationSubscription;

    private HeaderViewViewModel(
        Property<List<UnlockedItem>> allUnlockedItems,
        Property<String> searchText,
        Property<SortedBy> sortedBy,
        Property<Long> unlockedByAccountHash,
        AccountConfigurationService accountConfigurationService,
        MembersDataProvider membersDataProvider,
        Runnable navigateToConfiguration) {
        this.membersDataProvider = membersDataProvider;

        this.searchText = searchText;
        this.sortedBy = sortedBy;
        this.unlockedByAccountHash = unlockedByAccountHash;
        this.sortedByOptions = new Property<>(buildSortedByOptions(false));
        this.showUnlockedByFilter = new Property<>(true);

        accountHashesFromAllUnlockedItems = allUnlockedItems.deriveAsync(items -> {
            if (items == null || items.isEmpty()) {
                return new ArrayList<>();
            }

            return items.stream()
                .map(UnlockedItem::getAcquiredByAccountHash)
                .distinct()
                .collect(Collectors.toList());
        });
        this.navigateToConfiguration = navigateToConfiguration;

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

        membersDataProvider.await(null).whenComplete((__, throwable) -> {
            if (throwable != null) {
                return;
            }
            accountHashToMemberNameMap.set(buildAccountHashToMemberNameMap());
        });

        accountConfigurationSubscription = accountConfigurationService.currentAccountConfiguration()
            .subscribeImmediate((accountConfiguration, __) ->
                onAccountConfigurationChanged(accountConfiguration)
            );
    }

    @Override
    public void close() throws Exception {
        accountConfigurationSubscription.dispose();
        membersDataProvider.removeMemberMapListener(memberMapListener);
    }

    public void onOpenConfigurationClick() {
        navigateToConfiguration.run();
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

    private void onAccountConfigurationChanged(AccountConfiguration accountConfiguration) {
        boolean isLocalMode = accountConfiguration != null
            && accountConfiguration.getStorageMode() == StorageMode.LOCAL;
        sortedByOptions.set(buildSortedByOptions(isLocalMode));
        showUnlockedByFilter.set(!isLocalMode);

        if (isLocalMode) {
            // Normalize hidden multiplayer-only state when switching into local mode.
            SortedBy sortedByValue = sortedBy.get();
            if (sortedByValue == SortedBy.PLAYER_ASC || sortedByValue == SortedBy.PLAYER_DESC) {
                sortedBy.set(SortedBy.UNLOCKED_AT_DESC);
            }
            unlockedByAccountHash.set(null);
        }
    }

    private List<SortedBy> buildSortedByOptions(boolean isLocalMode) {
        List<SortedBy> options = new ArrayList<>();
        options.add(SortedBy.UNLOCKED_AT_ASC);
        options.add(SortedBy.ALPHABETICAL_ASC);
        if (!isLocalMode) {
            options.add(SortedBy.PLAYER_ASC);
        }
        options.add(SortedBy.UNLOCKED_AT_DESC);
        options.add(SortedBy.ALPHABETICAL_DESC);
        if (!isLocalMode) {
            options.add(SortedBy.PLAYER_DESC);
        }
        return options;
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        HeaderViewViewModel create(
            Property<List<UnlockedItem>> allUnlockedItems,
            Property<String> searchText,
            Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
            Property<Long> unlockedByAccountHash,
            Runnable navigateToConfiguration
        );
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Inject
        private AccountConfigurationService accountConfigurationService;
        @Inject
        private MembersDataProvider membersDataProvider;

        @Override
        public HeaderViewViewModel create(
            Property<List<UnlockedItem>> allUnlockedItems,
            Property<String> searchText,
            Property<UnlockedItemsScreenViewModel.SortedBy> sortedBy,
            Property<Long> unlockedByAccountHash,
            Runnable navigateToConfiguration
        ) {
            return new HeaderViewViewModel(
                allUnlockedItems,
                searchText,
                sortedBy,
                unlockedByAccountHash,
                accountConfigurationService,
                membersDataProvider,
                navigateToConfiguration
            );
        }
    }
}
