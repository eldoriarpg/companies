/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.offer.ContestDelivery;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.Contributor;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.GoalReward;
import de.eldoria.companies.data.wrapper.offer.OfferMark;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.OpenChunk;
import de.eldoria.companies.data.wrapper.offer.PrizeState;
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
import java.util.UUID;

/**
 * Event offers, the chunks of goals and the entries, deliveries and prizes of contests.
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
     * The offers the primary looks after: those still running, closed ones whose chunks are not all settled yet, and
     * closed contests that did not give back their rest yet.
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
     * @param event id of a server event
     * @return the offers of the event, oldest first
     */
    public abstract List<EventOffer> ofEvent(int event);

    /**
     * Puts an offer that has not closed on a server event, or takes it off one.
     *
     * @param offer id of the offer
     * @param event id of the event, null for none
     * @return true when the offer had not closed and is changed
     */
    public abstract boolean link(int offer, @Nullable Integer event);

    /**
     * Moves the start and end of an offer that has not closed, as the server event it belongs to moved.
     *
     * @param offer  id of the offer
     * @param starts its new start
     * @param ends   its new end
     * @return true when the offer had not closed and is changed
     */
    public abstract boolean times(int offer, LocalDateTime starts, LocalDateTime ends);

    /**
     * @param offer id of a goal
     * @return its reward, empty when it has none
     */
    public abstract Optional<GoalReward> reward(int offer);

    /**
     * Sets or removes the reward of a goal.
     *
     * @param offer  id of the goal
     * @param reward the reward, null for none
     * @return true when the offer is a goal and is changed
     */
    public abstract boolean reward(int offer, @Nullable GoalReward reward);

    /**
     * What {@code reward_event_id} holds for a reward that failed for good, such as one whose event would be refused or
     * whose time passed before it could be created.
     */
    public static final int REWARD_FAILED = 0;

    /**
     * What {@code reward_event_id} holds while the primary creates a reward's event; a goal still holding it on a later
     * check, since the server stopped in between, is tried again.
     */
    public static final int REWARD_CREATING = -1;

    /**
     * @return the goals that were reached and carry a reward whose event was neither created nor failed, oldest first
     */
    public abstract List<EventOffer> unrewarded();

    /**
     * @param offer id of a goal
     * @return what is noted of its reward's event: the event's id, {@link #REWARD_FAILED} or {@link #REWARD_CREATING};
     *     empty when nothing is noted yet
     */
    public abstract Optional<Integer> rewardEvent(int offer);

    /**
     * Claims creating a goal's reward: notes {@link #REWARD_CREATING} while nothing is noted, or while it is noted
     * already from an attempt that did not finish.
     *
     * @param offer id of the goal
     * @return true when the reward may be created now
     */
    public abstract boolean claimReward(int offer);

    /**
     * Notes what came of creating a claimed reward: the event's id or {@link #REWARD_FAILED}; only while nothing is
     * noted or it is being created.
     *
     * @param offer id of the goal
     * @param event id of the event, or {@link #REWARD_FAILED}
     * @return true when it is noted now
     */
    public abstract boolean rewarded(int offer, int event);

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
     * @return the goals and contests that run, wait for their start or are paused, the soonest end first
     */
    public abstract List<EventOffer> running();

    /**
     * Notes that the progress of a goal reached a threshold, once: a threshold is noted only when it is above the one
     * noted before, so every threshold is announced at most once, also across restarts.
     *
     * @param offer     id of the goal
     * @param threshold the threshold in percent
     * @return true when the threshold is above the one noted before and is noted now
     */
    public abstract boolean progressPosted(int offer, int threshold);

    /**
     * Counts the chunks of goals waiting to be taken, for the boards of event offers.
     *
     * @param offer id of the goal, 0 for every goal
     * @return the unclaimed orders that are chunks of the goal or of any goal
     */
    public abstract int unclaimedChunks(int offer);

    /**
     * @return the contests that run, wait for their start or are paused, oldest first
     */
    public abstract List<EventOffer> contests();

    /**
     * Lets a company take part in a contest that has not closed, or take part again after it left. Its delivered items
     * stay.
     *
     * @param offer   id of the contest
     * @param company id of the company
     * @param at      the current time
     * @return true when the company takes part now and did not before
     */
    public abstract boolean join(int offer, int company, LocalDateTime at);

    /**
     * Lets a company leave a contest. It keeps its delivered items and its place, and its order slot is free again.
     *
     * @param offer   id of the contest
     * @param company id of the company
     * @param at      the current time
     * @return true when the company took part and left now
     */
    public abstract boolean leave(int offer, int company, LocalDateTime at);

    /**
     * @param offer   id of the contest
     * @param company id of the company
     * @return the company's entry, also after it left; empty when it never joined
     */
    public abstract Optional<ContestEntry> entry(int offer, int company);

    /**
     * @param company id of the company
     * @return the ids of the contests that have not closed and the company takes part in
     */
    public abstract List<Integer> joined(int company);

    /**
     * The places of a contest: every company that joined, most items first; of two with as many items the one that
     * got there first ranks higher, then the one that joined first.
     *
     * @param offer id of the contest
     * @return the entries in order of their places
     */
    public abstract List<ContestEntry> ranking(int offer);

    /**
     * Takes items of a worker of a company into a running contest, as many as it misses and its money, less the
     * prizes kept back, can pay; at most what was offered. The contest's delivered items and spent money, the company's
     * entry and a delivery row change together, and only when nobody delivered meanwhile, so two companies delivering
     * the last items at once never take more than the contest misses: the one that lost the race reads again and takes
     * what is left. A delivery that fills the contest closes it as {@link OfferState#REACHED}; one that leaves less
     * money than one more item costs closes it as {@link OfferState#DEPLETED}.
     *
     * @param offer   id of the contest
     * @param company id of the delivering company
     * @param worker  the delivering player
     * @param offered items offered
     * @param at      the current time
     * @return what was taken and what it is paid
     */
    public abstract ContestDelivery deliver(int offer, int company, UUID worker, int offered, LocalDateTime at);

    /**
     * Starts a scheduled offer that never ran, noting when it started and, for a contest, the money its account paid
     * in.
     *
     * @param offer id of the offer
     * @param held  what the account of a contest paid in, 0 for minted money and goals
     * @param at    the current time
     * @return true when it waited and runs now
     */
    public abstract boolean activate(int offer, double held, LocalDateTime at);

    /**
     * Settles a closed contest once across all nodes: claims it, writes its prizes and reserves the money of those
     * waiting to be paid, all in one transaction, so a contest is either settled with all its prizes or not at all.
     *
     * @param offer  id of the contest
     * @param prizes its prizes, each {@link PrizeState#PENDING} with its company or {@link PrizeState#NONE}
     * @param at     the current time
     * @return true when this call settled it
     */
    public abstract boolean settle(int offer, List<ContestPrize> prizes, LocalDateTime at);

    /**
     * Claims a waiting prize for paying, once.
     *
     * @param offer id of the contest
     * @param place the place
     * @return true when it waited and is claimed now
     */
    public abstract boolean claimPrize(int offer, int place);

    /**
     * Notes how paying a claimed prize went. A refused prize gives its reserved money back to the contest.
     *
     * @param offer id of the contest
     * @param place the place
     * @param paid  whether the money reached the company
     * @return true when the prize was claimed and is noted now
     */
    public abstract boolean prizeOutcome(int offer, int place, boolean paid);

    /**
     * Claims giving back the rest of a settled contest and announcing its result, once.
     *
     * @param offer  id of the contest
     * @param amount what goes back to its account
     * @param at     the current time
     * @return true when this call claimed it
     */
    public abstract boolean refund(int offer, double amount, LocalDateTime at);

    /**
     * @param offer id of the contest
     * @return the prizes noted when it was settled, first place first
     */
    public abstract List<ContestPrize> prizes(int offer);

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
