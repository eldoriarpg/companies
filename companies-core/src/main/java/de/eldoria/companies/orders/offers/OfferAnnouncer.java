/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.MessageSender;
import org.bukkit.plugin.Plugin;

/**
 * Announces reached goals to every player online on this server, each in their own language, and in the log.
 * <p>
 * The primary announces, so in a network only the players on the primary see it; announcements on every server come
 * with server events, which bring broadcasts, inbox entries and Discord posts.
 */
public final class OfferAnnouncer implements EventOfferService.Announcer {
    private final Plugin plugin;
    private final MessageSender messages;

    /**
     * @param plugin   the plugin
     * @param messages sends the announcement
     */
    public OfferAnnouncer(Plugin plugin, MessageSender messages) {
        this.plugin = plugin;
        this.messages = messages;
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
                        Values.text("name", offer.name()),
                        Values.number("amount", offer.amount()),
                        Values.text("item", offer.line().label()));
            }
        });
    }
}
