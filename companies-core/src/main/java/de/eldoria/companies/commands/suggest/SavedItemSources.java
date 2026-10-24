/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.suggest;

import de.eldoria.companies.data.repository.ASavedItemData;
import de.eldoria.companies.data.wrapper.saved.SavedItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Where the suggestions of saved items read their data from: the saved items of each player, cached like other
 * database data.
 * <p>
 * The plugin hands the sources in when it enables and takes them back when it disables.
 */
public final class SavedItemSources {
    private static volatile Optional<SavedItemSources> current = Optional.empty();

    private final SuggestionCache<UUID, List<SavedItem>> items;

    /**
     * @param data     saved item repository
     * @param executor executor loading data from the database
     */
    public SavedItemSources(ASavedItemData data, Executor executor) {
        items = new SuggestionCache<>(SuggestionSources.LIFETIME, executor, player -> data.retrieveItems(player)
                .join());
    }

    /**
     * @param sources the sources the providers read from
     */
    public static void use(SavedItemSources sources) {
        current = Optional.of(sources);
    }

    /**
     * Stops suggesting data of the plugin.
     */
    public static void clear() {
        current = Optional.empty();
    }

    /**
     * @return the sources, empty while the plugin is disabled
     */
    public static Optional<SavedItemSources> current() {
        return current;
    }

    /**
     * Drops the cached saved items of a player, after they changed.
     *
     * @param player id of the player
     */
    public static void invalidate(UUID player) {
        current.ifPresent(sources -> sources.items().invalidate(player));
    }

    /**
     * @return the saved items of a player, by the player's id
     */
    public SuggestionCache<UUID, List<SavedItem>> items() {
        return items;
    }
}
