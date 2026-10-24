/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.saved.SaveResult;
import de.eldoria.companies.data.wrapper.saved.SavedItem;
import org.bukkit.inventory.ItemStack;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * The items players saved to order again. Items are stored as JSON like the lines of an order. All futures complete
 * on the executor given to the constructor.
 */
public abstract class ASavedItemData {
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    /**
     * @param mapper   mapper able to read and write item stacks
     * @param executor executor for futures
     */
    protected ASavedItemData(ObjectMapper mapper, ExecutorService executor) {
        this.mapper = mapper;
        this.executor = executor;
    }

    /**
     * Saves an item of a player unless the name is taken or the player saved as many items as allowed.
     *
     * @param owner the player
     * @param name  the name, unique among the player's saved items
     * @param stack the template, with an amount of one
     * @param max   how many items the player may save
     * @param now   the current time, in UTC
     * @return how saving ended
     */
    public CompletableFuture<SaveResult> submitItem(
            UUID owner, String name, ItemStack stack, int max, LocalDateTime now) {
        return CompletableFuture.supplyAsync(() -> saveItem(owner, name, toJson(stack), max, now), executor);
    }

    protected abstract SaveResult saveItem(UUID owner, String name, String stack, int max, LocalDateTime now);

    /**
     * The saved items of a player, by name.
     *
     * @param owner the player
     * @return the items
     */
    public CompletableFuture<List<SavedItem>> retrieveItems(UUID owner) {
        return CompletableFuture.supplyAsync(() -> getItems(owner), executor);
    }

    protected abstract List<SavedItem> getItems(UUID owner);

    /**
     * A saved item of a player by name.
     *
     * @param owner the player
     * @param name  the name
     * @return the item, empty when the player saved none by that name
     */
    public CompletableFuture<Optional<SavedItem>> retrieveItem(UUID owner, String name) {
        return CompletableFuture.supplyAsync(() -> getItem(owner, name), executor);
    }

    protected abstract Optional<SavedItem> getItem(UUID owner, String name);

    /**
     * A saved item of a player by id.
     *
     * @param owner the player, so no one picks another player's item
     * @param id    the id
     * @return the item, empty when the player saved none with that id
     */
    public CompletableFuture<Optional<SavedItem>> retrieveItem(UUID owner, int id) {
        return CompletableFuture.supplyAsync(() -> getItem(owner, id), executor);
    }

    protected abstract Optional<SavedItem> getItem(UUID owner, int id);

    /**
     * Removes a saved item of a player.
     *
     * @param owner the player
     * @param name  the name
     * @return true when an item was removed
     */
    public CompletableFuture<Boolean> submitRemoval(UUID owner, String name) {
        return CompletableFuture.supplyAsync(() -> removeItem(owner, name), executor);
    }

    protected abstract boolean removeItem(UUID owner, String name);

    /**
     * @param json a stored item
     * @return the item
     * @throws SQLException when the text is no item
     */
    protected ItemStack readStack(String json) throws SQLException {
        try {
            return mapper.readValue(json, ItemStack.class);
        } catch (JacksonException e) {
            throw new SQLException("Could not read a saved item", e);
        }
    }

    private String toJson(ItemStack stack) {
        return mapper.writeValueAsString(stack);
    }
}
