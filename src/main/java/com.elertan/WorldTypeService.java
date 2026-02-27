package com.elertan;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.WorldType;

@Singleton
public class WorldTypeService implements BUPluginLifecycle {

    private static final Set<WorldType> SUPPORTED_WORLD_TYPES = Set.of(
        WorldType.MEMBERS,
        WorldType.PVP,
        WorldType.BOUNTY,
        WorldType.SKILL_TOTAL,
        WorldType.HIGH_RISK,
        WorldType.FRESH_START_WORLD,
        WorldType.LAST_MAN_STANDING
    );

    @Inject
    private Client client;

    @Override
    public void startUp() throws Exception {
    }

    @Override
    public void shutDown() throws Exception {
    }

    /**
     * Checks if the current world type is supported for bronzeman features.
     * Must be called from client thread.
     *
     * @return true if policies and unlocks should apply, false otherwise
     */
    public boolean isCurrentWorldSupported() {
        EnumSet<WorldType> worldTypes = client.getWorldType();
        // Empty set returns true (vacuous truth via allMatch), which is expected
        return worldTypes.stream().allMatch(SUPPORTED_WORLD_TYPES::contains);
    }
}
