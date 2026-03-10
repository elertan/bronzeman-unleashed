package com.elertan;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.runelite.api.ItemID;

/**
 * Resolves relationships between items for unlocking.
 *
 * - Equivalent item groups (e.g. all doses of a potion, clean/grimy herb variants, broken/normal armor).
 * - Recipe-style relationships (e.g. if ingredients A and B are unlocked, unlock result C).
 */
public final class RelatedItemsRegistry {

    private final Map<Integer, Set<Integer>> equivalenceGroups;
    private final Set<RecipeRule> recipeRules;

    public RelatedItemsRegistry(
        Map<Integer, Set<Integer>> equivalenceGroups,
        Set<RecipeRule> recipeRules
    ) {
        this.equivalenceGroups = equivalenceGroups;
        this.recipeRules = recipeRules;
    }

    /**
     * Creates a default registry with explicit recipe mappings and no equivalence expansion.
     */
    public static RelatedItemsRegistry createDefault() {
        Set<RecipeRule> recipes = new HashSet<>();
        registerRecipes(recipes);

        return new RelatedItemsRegistry(
            Collections.emptyMap(),
            Collections.unmodifiableSet(recipes)
        );
    }

    private static void registerRecipes(Set<RecipeRule> recipes) {
        // Note: In RuneLite gameval IDs, "Amulet of torture" is exposed as ZENYTE_AMULET_ENCHANTED.
        recipes.add(new RecipeRule(
            IntStream.of(ItemID.ARAXYTE_FANG, ItemID.ZENYTE_AMULET_ENCHANTED)
                .boxed()
                .collect(Collectors.toUnmodifiableSet()),
            Collections.singleton(ItemID.AMULET_OF_RANCOUR)
        ));
    }

    /**
     * Returns the full set of item IDs that are considered equivalent to the given item ID,
     * including the given ID itself. If no mapping exists, a singleton set containing only
     * {@code itemId} is returned.
     */
    public Set<Integer> getEquivalentItemIds(int itemId) {
        Set<Integer> group = equivalenceGroups.get(itemId);
        if (group == null || group.isEmpty()) {
            return Collections.singleton(itemId);
        }
        // Always include the queried ID to keep behavior predictable when the group was
        // registered under a different representative.
        if (group.contains(itemId)) {
            return Collections.unmodifiableSet(group);
        }
        Set<Integer> copy = new HashSet<>(group);
        copy.add(itemId);
        return Collections.unmodifiableSet(copy);
    }

    /**
     * Given the set of currently unlocked item IDs, returns the set of additional item IDs that
     * should become unlocked because all of their recipe ingredients are present in the unlocked
     * set.
     *
     * This method does not mutate the input. Callers are responsible for filtering out results
     * that are already unlocked if they want strictly new unlocks.
     */
    public Set<Integer> getRecipeResultItemIds(Set<Integer> unlockedItemIds) {
        if (recipeRules.isEmpty() || unlockedItemIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> results = new HashSet<>();
        for (RecipeRule rule : recipeRules) {
            if (unlockedItemIds.containsAll(rule.ingredients)) {
                results.addAll(rule.results);
            }
        }
        return Collections.unmodifiableSet(results);
    }

    public static final class RecipeRule {

        private final Set<Integer> ingredients;
        private final Set<Integer> results;

        public RecipeRule(Set<Integer> ingredients, Set<Integer> results) {
            this.ingredients = Collections.unmodifiableSet(new HashSet<>(ingredients));
            this.results = Collections.unmodifiableSet(new HashSet<>(results));
        }

        public Set<Integer> getIngredients() {
            return ingredients;
        }

        public Set<Integer> getResults() {
            return results;
        }
    }
}
