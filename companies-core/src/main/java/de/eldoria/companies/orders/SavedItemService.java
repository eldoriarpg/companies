/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders;

import de.eldoria.companies.configuration.elements.ordersettings.SavedItemSettings;
import de.eldoria.companies.data.repository.ASavedItemData;
import de.eldoria.companies.data.wrapper.saved.SavedItem;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The items players save to order them again. Saving copies the item as the template an order line would request,
 * with an amount of one; the player keeps the item. An item the server does not let players order cannot be saved.
 * Names are letters, digits, {@code _} and {@code -}, unique per player; without a name the material's name is used.
 */
public final class SavedItemService {
    /**
     * The longest name of a saved item.
     */
    public static final int MAX_NAME_LENGTH = 32;

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_-]{1," + MAX_NAME_LENGTH + "}");

    private final ASavedItemData data;
    private final Function<ItemStack, Optional<OrderDraftService.RequestedItem>> requests;
    private final Supplier<SavedItemSettings> settings;

    /**
     * @param data     saved item repository
     * @param requests turns an item into what an order line requests, empty when it cannot be ordered
     * @param settings current saved item settings
     */
    public SavedItemService(
            ASavedItemData data,
            Function<ItemStack, Optional<OrderDraftService.RequestedItem>> requests,
            Supplier<SavedItemSettings> settings) {
        this.data = data;
        this.requests = requests;
        this.settings = settings;
    }

    /**
     * Saves an item of a player.
     *
     * @param owner the player
     * @param item  the item, usually in the player's hand
     * @param name  the name, empty for the material's name
     * @return the saved item, or why it was not saved
     */
    public CompletableFuture<ActionResult<SavedItem>> save(UUID owner, ItemStack item, Optional<String> name) {
        if (item.isEmpty()) return refused("order.draft.error.emptyHand");
        var max = settings.get().max();
        if (max == 0) return refused("order.items.error.disabled");
        var requested = requests.apply(item);
        if (requested.isEmpty()) return refused("order.draft.error.notOrderable");
        var chosen =
                name.map(String::strip).orElseGet(() -> item.getType().name().toLowerCase(Locale.ROOT));
        if (!NAME.matcher(chosen).matches()) {
            return CompletableFuture.completedFuture(ActionResult.refusedActionBar(
                    "order.items.error.invalidName", Placeholder.unparsed("max", String.valueOf(MAX_NAME_LENGTH))));
        }
        var template = requested.get().template().asOne();
        return data.submitItem(owner, chosen, template, max, Times.now()).thenCompose(result -> switch (result) {
            case SAVED ->
                data.retrieveItem(owner, chosen)
                        .thenApply(saved -> saved.<ActionResult<SavedItem>>map(ActionResult::done)
                                .orElseGet(() -> ActionResult.refusedActionBar("error.saveFailed")));
            case NAME_TAKEN ->
                CompletableFuture.completedFuture(ActionResult.refusedActionBar(
                        "order.items.error.nameTaken", Placeholder.unparsed("name", chosen)));
            case LIMIT_REACHED ->
                CompletableFuture.completedFuture(ActionResult.refusedActionBar(
                        "order.items.error.limit", Placeholder.unparsed("max", String.valueOf(max))));
            case FAILED -> refused("error.saveFailed");
        });
    }

    /**
     * @param owner the player
     * @return the player's saved items, by name
     */
    public CompletableFuture<List<SavedItem>> items(UUID owner) {
        return data.retrieveItems(owner);
    }

    /**
     * @param owner the player
     * @param name  the name
     * @return the saved item, or why there is none
     */
    public CompletableFuture<ActionResult<SavedItem>> item(UUID owner, String name) {
        return data.retrieveItem(owner, name).thenApply(item -> item.<ActionResult<SavedItem>>map(ActionResult::done)
                .orElseGet(() -> unknown(name)));
    }

    /**
     * @param owner the player
     * @param id    the id
     * @return the saved item, empty when the player saved none with that id
     */
    public CompletableFuture<Optional<SavedItem>> item(UUID owner, int id) {
        return data.retrieveItem(owner, id);
    }

    /**
     * Removes a saved item of a player.
     *
     * @param owner the player
     * @param name  the name
     * @return the name, or why nothing was removed
     */
    public CompletableFuture<ActionResult<String>> remove(UUID owner, String name) {
        return data.submitRemoval(owner, name).thenApply(removed -> removed ? ActionResult.done(name) : unknown(name));
    }

    private static <T> ActionResult<T> unknown(String name) {
        return ActionResult.refusedActionBar("order.items.error.unknown", Placeholder.unparsed("name", name));
    }

    private static <T> CompletableFuture<ActionResult<T>> refused(String key) {
        return CompletableFuture.completedFuture(ActionResult.refusedActionBar(key));
    }
}
