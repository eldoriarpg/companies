/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import de.eldoria.companies.components.order.IFullOrder;
import de.eldoria.companies.configuration.elements.DiscordSettings;
import de.eldoria.companies.events.company.CompanyLevelUpEvent;
import de.eldoria.companies.events.order.OrderCreatedEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * Turns the plugin's events into webhook embeds.
 * <p>
 * Each event fires on exactly one node, so every node listens and nothing is posted twice. Handlers only build the
 * embed and queue it; sending happens on the webhook's own thread.
 */
public final class DiscordListener implements Listener {
    private final Supplier<DiscordSettings> settings;
    private final DiscordEmbeds embeds;
    private final DoubleFunction<String> prices;
    private final Consumer<Embed> queue;

    /**
     * @param settings the webhook settings
     * @param embeds   builds the embeds
     * @param prices   formats an amount of money
     * @param queue    receives the embeds to post
     */
    public DiscordListener(
            Supplier<DiscordSettings> settings,
            DiscordEmbeds embeds,
            DoubleFunction<String> prices,
            Consumer<Embed> queue) {
        this.settings = settings;
        this.embeds = embeds;
        this.prices = prices;
        this.queue = queue;
    }

    /**
     * Posts a new order.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (!settings.get().posts(WebhookEvent.ORDER_CREATED)) return;
        var order = event.order();
        queue.accept(embeds.orderCreated(order.name(), DiscordEmbeds.lines(order), prices.apply(order.price())));
    }

    /**
     * Posts a fully delivered order.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOrderDone(OrderDoneEvent event) {
        if (!settings.get().posts(WebhookEvent.ORDER_COMPLETED)) return;
        var order = event.order();
        if (order instanceof IFullOrder full) {
            queue.accept(embeds.orderCompleted(
                    full.name(), DiscordEmbeds.lines(full), Optional.of(prices.apply(full.price()))));
            return;
        }
        queue.accept(embeds.orderCompleted(order.name(), List.of(), Optional.empty()));
    }

    /**
     * Posts a company reaching a higher level.
     *
     * @param event the event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelUp(CompanyLevelUpEvent event) {
        if (!settings.get().posts(WebhookEvent.LEVEL_UP)) return;
        var level = MiniMessage.miniMessage().stripTags(event.newLevel().levelName());
        queue.accept(embeds.levelUp(event.company().name(), level));
    }
}
