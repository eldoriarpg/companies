/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.nodes.NodeMessage.OfferAnnounced;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

/**
 * Shows the announcements of event offers to every player online on this server, each in their own language: started
 * offers, reached goals, the first places of closed contests and cancelled contests.
 * <p>
 * Every node runs one. The primary sends what happened through the {@link de.eldoria.companies.nodes.OfferNetwork},
 * and each node reads the offer from the database when the announcement arrives, see {@link #handle}. The primary
 * also logs it.
 */
public final class OfferAnnouncer implements EventOfferService.Announcer {
    private static final int PLACES = 3;

    private final Plugin plugin;
    private final MessageSender messages;
    private final Economy economy;
    private final AOfferData offers;

    /**
     * @param plugin   the plugin
     * @param messages sends the announcement
     * @param economy  formats the prizes
     * @param offers   offer repository, read when an announcement arrives
     */
    public OfferAnnouncer(Plugin plugin, MessageSender messages, Economy economy, AOfferData offers) {
        this.plugin = plugin;
        this.messages = messages;
        this.economy = economy;
        this.offers = offers;
    }

    /**
     * Shows an announcement that arrived from the primary. Reads the offer, and for a closed contest its places and
     * prizes, from the database; call it off the main thread.
     *
     * @param message the announcement
     */
    public void handle(OfferAnnounced message) {
        var offer = offers.offer(message.offer()).orElse(null);
        if (offer == null) return;
        switch (message.event()) {
            case STARTED -> started(offer);
            case REACHED -> reached(offer);
            case CLOSED ->
                contestClosed(
                        offer,
                        offers.ranking(offer.id()).stream()
                                .filter(entry -> entry.delivered() > 0 && entry.name() != null)
                                .toList(),
                        offers.prizes(offer.id()));
            case CANCELLED -> contestCancelled(offer);
            default -> plugin.getLogger().fine("Unknown offer announcement " + message.event());
        }
    }

    /**
     * The event of an announcement that a goal or contest started.
     */
    public static final String STARTED = "STARTED";

    /**
     * The event of an announcement that a goal was reached.
     */
    public static final String REACHED = "REACHED";

    /**
     * The event of an announcement that a contest closed with a result.
     */
    public static final String CLOSED = "CLOSED";

    /**
     * The event of an announcement that a contest was cancelled.
     */
    public static final String CANCELLED = "CANCELLED";

    @Override
    public void started(EventOffer offer) {
        Threading.mainThread(plugin).execute(() -> {
            for (var player : plugin.getServer().getOnlinePlayers()) {
                messages.sendMessage(
                        player,
                        offer.kind() == OfferKind.CONTEST ? "offers.contestStarted" : "offers.goalStarted",
                        Values.text("offer_name", offer.name()),
                        Values.number("amount", offer.amount()),
                        Values.text("item", offer.line().label()),
                        Values.money("unit", economy, offer.unitPrice()));
            }
        });
    }

    @Override
    public void reached(EventOffer offer) {
        Threading.mainThread(plugin).execute(() -> {
            for (var player : plugin.getServer().getOnlinePlayers()) {
                messages.sendMessage(
                        player,
                        "offers.reached",
                        Values.text("offer_name", offer.name()),
                        Values.number("amount", offer.amount()),
                        Values.text("item", offer.line().label()));
            }
        });
    }

    /**
     * Announces the first places of a closed contest, with their items and the prize each company was paid, matched by
     * the company the stored prize went to.
     */
    @Override
    public void contestClosed(EventOffer offer, List<ContestEntry> ranking, List<ContestPrize> prizes) {
        var shown = ranking.subList(0, Math.min(PLACES, ranking.size()));
        Threading.mainThread(plugin).execute(() -> {
            for (var player : plugin.getServer().getOnlinePlayers()) {
                messages.sendMessage(
                        player,
                        "offers.contestClosed",
                        Values.text("offer_name", offer.name()),
                        Values.number("delivered", offer.delivered()),
                        Values.number("amount", offer.amount()),
                        Values.text("item", offer.line().label()));
                for (var index = 0; index < shown.size(); index++) {
                    var entry = shown.get(index);
                    var place = index + 1;
                    var prize = prizes.stream()
                            .filter(found -> found.paid() && Objects.equals(found.company(), entry.company()))
                            .findFirst();
                    var company =
                            Values.text("company", Objects.requireNonNullElse(entry.name(), "#" + entry.company()));
                    if (prize.isPresent()) {
                        messages.sendMessage(
                                player,
                                "offers.contestPrize",
                                Values.number("place", place),
                                company,
                                Values.number("items", entry.delivered()),
                                Values.money("prize", economy, prize.get().amount()));
                    } else {
                        messages.sendMessage(
                                player,
                                "offers.contestPlace",
                                Values.number("place", place),
                                company,
                                Values.number("items", entry.delivered()));
                    }
                }
            }
        });
    }

    /**
     * Announces that a contest was cancelled without prizes.
     */
    @Override
    public void contestCancelled(EventOffer offer) {
        Threading.mainThread(plugin).execute(() -> {
            for (var player : plugin.getServer().getOnlinePlayers()) {
                messages.sendMessage(player, "offers.contestCancelled", Values.text("offer_name", offer.name()));
            }
        });
    }
}
