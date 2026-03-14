package com.elertan.remote;

import com.elertan.event.BUEvent;
import com.elertan.models.GameRules;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.models.Member;
import com.elertan.models.UnlockedItem;

public interface StorageSession extends AutoCloseable {

    KeyValueStoragePort<Long, Member> getMembersStoragePort();

    KeyValueStoragePort<Integer, UnlockedItem> getUnlockedItemsStoragePort();

    ObjectStoragePort<GameRules> getGameRulesStoragePort();

    ObjectListStoragePort<BUEvent> getLastEventStoragePort();

    KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> getGroundItemOwnedByStoragePort();
}
