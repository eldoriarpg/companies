/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.offer.Contributor;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.OpenChunk;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Event offers and the chunks of goals.
 * <p>
 * An offer keeps what its chunks hold ({@code spent}) and what was delivered to them ({@code delivered}) in its own row,
 * changed in the same transaction as the chunk, so the progress and the money of an offer survive the purge of its
 * orders. Every change of a chunk is conditional on the chunk still being open, so the node completing an order and the
 * primary checking the chunks count a delivery once. Each order of a chunk carries the offer's id in
 * {@code orders.offer_id}. Methods block; call them off the main thread.
 */
public abstract class AOfferData {
    private final ObjectMapper mapper;

    /**
     * @param mapper mapper for the exact items of offers, able to read and write item stacks
     */
    protected AOfferData(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Stores a new offer.
     *
     * @param offer the offer; its id, spent and delivered amounts are ignored
     * @return the id of the stored offer, empty when it could not be stored
     */
    public abstract Optional<Integer> create(EventOffer offer);

    /**
     * @param id id of the offer
     * @return the offer, empty when there is none
     */
    public abstract Optional<EventOffer> offer(int id);

    /**
     * @param limit how many to return at most
     * @return the newest offers, newest first
     */
    public abstract List<EventOffer> offers(int limit);

    /**
     * The offers the primary looks after: those still running, and closed ones whose chunks are not all settled yet.
     *
     * @return the offers, oldest first
     */
    public abstract List<EventOffer> working();

    /**
     * Moves an offer from one state to another, once. A final state notes when the offer was closed.
     *
     * @param offer id of the offer
     * @param from  the state it must be in
     * @param to    the new state
     * @param at    the current time
     * @return true when it was in {@code from} and is now in {@code to}
     */
    public abstract boolean state(int offer, OfferState from, OfferState to, LocalDateTime at);

    /**
     * Changes how many chunks of a goal wait to be taken at once.
     *
     * @param offer id of the offer
     * @param open  chunks kept waiting
     * @return true when the offer exists
     */
    public abstract boolean chunkLimit(int offer, int open);

    /**
     * Notes a placed chunk: stores it open, marks its order with the offer and adds what it holds to the offer's spent
     * money.
     *
     * @param offer   id of the offer
     * @param order   id of the chunk's order
     * @param amount  items it asks for
     * @param paid    what was put into it
     * @param account economy account that paid it, null for minted money
     * @param at      when it was placed
     * @return true when it was stored
     */
    public abstract boolean addChunk(
            int offer, int order, int amount, double paid, @Nullable String account, LocalDateTime at);

    /**
     * @param offer id of the offer
     * @return its open chunks with the states of their orders, oldest first
     */
    public abstract List<OpenChunk> openChunks(int offer);

    /**
     * Settles an open chunk whose order was delivered: the items delivered to it count for the offer and are credited
     * to the company or the player working alone that delivered it. A chunk delivered short, such as one an admin
     * closed as delivered, keeps only the money of what was delivered, so the rest can be placed again.
     *
     * @param order id of the chunk's order
     * @param at    the current time
     * @return true when the chunk was open, its order delivered, and it is now settled
     */
    public abstract boolean chunkDelivered(int order, LocalDateTime at);

    /**
     * Settles an open chunk whose order no longer exists without being delivered: what it held leaves the offer's spent
     * money, and its amount can be placed again.
     *
     * @param order id of the chunk's order
     * @param at    the current time
     * @return true when the chunk was open, its order is gone, and it is now settled
     */
    public abstract boolean chunkReturned(int order, LocalDateTime at);

    /**
     * @param offer id of the offer
     * @param limit how many to return at most
     * @return the companies and players working alone that delivered chunks of the offer, most items first
     */
    public abstract List<Contributor> contributors(int offer, int limit);

    /**
     * @param orders ids of orders
     * @return the offer of each order that belongs to one, by order id
     */
    public abstract Map<Integer, OfferMark> marks(Collection<Integer> orders);

    /**
     * @param item an item
     * @return the item as JSON
     */
    protected String toJson(ItemStack item) {
        try {
            return mapper.writeValueAsString(item);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not write the item of an offer as json", e);
        }
    }

    /**
     * @param json an item as JSON, may be null
     * @return the item, null for null
     * @throws SQLException when the JSON is no item
     */
    protected @Nullable ItemStack toItem(@Nullable String json) throws SQLException {
        if (json == null) return null;
        try {
            return mapper.readValue(json, ItemStack.class);
        } catch (JacksonException e) {
            throw new SQLException("Could not read the item of an offer", e);
        }
    }
}
