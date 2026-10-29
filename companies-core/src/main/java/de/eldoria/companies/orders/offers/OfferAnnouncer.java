/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.ContestPrize;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Announces reached goals and the first places of closed contests to every player online on this server, each in
 * their own language, and in the log.
 * <p>
 * The primary announces, so in a network only the players on the primary see it; announcements on every server come
 * with server events, which bring broadcasts, inbox entries and Discord posts.
 */
public final class OfferAnnouncer implements EventOfferService.Announcer {
    private static final int PLACES = 3;

    private final Plugin plugin;
    private final MessageSender messages;
    private final Economy economy;

    /**
     * @param plugin   the plugin
     * @param messages sends the announcement
     * @param economy  formats the prizes
     */
    public OfferAnnouncer(Plugin plugin, MessageSender messages, Economy economy) {
        this.plugin = plugin;
        this.messages = messages;
        this.economy = economy;
    }

    @Override
    public void reached(EventOffer offer) {
        plugin.getLogger()
                .info("The event offer #" + offer.id() + " (" + offer.name() + ") reached its goal of " + offer.amount()
                        + " items.");
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
        plugin.getLogger()
                .info("The contest #" + offer.id() + " (" + offer.name() + ") closed as " + offer.state() + " with "
                        + offer.delivered() + " of " + offer.amount() + " items.");
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
                            .filter(found ->
                                    found.paid() && found.company() != null && found.company() == entry.company())
                            .findFirst();
                    var company = Values.text("company", entry.name() == null ? "#" + entry.company() : entry.name());
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
        plugin.getLogger().info("The contest #" + offer.id() + " (" + offer.name() + ") was cancelled.");
        Threading.mainThread(plugin).execute(() -> {
            for (var player : plugin.getServer().getOnlinePlayers()) {
                messages.sendMessage(player, "offers.contestCancelled", Values.text("offer_name", offer.name()));
            }
        });
    }
}
