/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.events.EventTimes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Locale;
import java.util.function.DoubleFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Texts of server events: their effect, their scope, their end and what is left of their budget, as the placeholders
 * {@code event_name}, {@code effect}, {@code scope}, {@code ends} and {@code budget} of the announcements.
 */
public final class EventTexts {
    private EventTexts() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * @param event the event
     * @return what the event changes, such as {@code ×1.5 rewards}
     */
    public static MenuText effect(ServerEvent event) {
        return MenuText.create()
                .locale("events.effect." + event.kind().name(), Values.text("effect_value", number(event.value())));
    }

    /**
     * What an event changes as plain text, such as {@code Reward multiplier ×1.5}, for the inbox and Discord.
     *
     * @param event the event
     * @param texts localizes a message key
     * @return the text
     */
    public static String plainEffect(ServerEvent event, UnaryOperator<String> texts) {
        var value = number(event.value());
        var shown =
                switch (event.kind()) {
                    case TAX_REDUCTION, FEE_REDUCTION -> "−" + value + " %";
                    case REWARD_MULTIPLIER -> "×" + value;
                };
        return texts.apply("events.kind." + event.kind().name()) + " " + shown;
    }

    /**
     * @param event the event
     * @return which orders the event applies to
     */
    public static MenuText scope(ServerEvent event) {
        var items = items(event);
        var levels = event.levels().stream().map(String::valueOf).collect(Collectors.joining(", "));
        if (event.materials().isEmpty() && event.levels().isEmpty())
            return MenuText.create().locale("events.scope.all");
        if (event.levels().isEmpty())
            return MenuText.create().locale("events.scope.items", Values.text("items", items));
        if (event.materials().isEmpty()) {
            return MenuText.create().locale("events.scope.levels", Values.text("levels", levels));
        }
        return MenuText.create()
                .locale("events.scope.both", Values.text("items", items), Values.text("levels", levels));
    }

    /**
     * @param event the event
     * @return the materials of its scope as players read them, such as {@code birch log, spruce log}
     */
    public static String items(ServerEvent event) {
        return event.materials().stream()
                .map(material -> material.toLowerCase(Locale.ROOT).replace('_', ' '))
                .collect(Collectors.joining(", "));
    }

    /**
     * @param event the event
     * @param money formats an amount of money
     * @return what is left of the budget of a multiplier, empty for one without a budget and for the other kinds
     */
    public static MenuText budget(ServerEvent event, DoubleFunction<String> money) {
        var left = event.remaining();
        if (!event.kind().funded() || left.isEmpty()) return MenuText.create();
        return MenuText.create().locale("events.budget.left", Values.text("budget", money.apply(left.getAsDouble())));
    }

    /**
     * @param event the event
     * @param zone  the server's time zone
     * @return when the event ends, as the server's clock shows it
     */
    public static String ends(ServerEvent event, ZoneId zone) {
        return EventTimes.show(event.ends(), zone);
    }

    /**
     * The placeholders of an announcement of an event, rendered for one player.
     *
     * @param texts renders for the player
     * @param event the event
     * @param money formats an amount of money
     * @param zone  the server's time zone
     * @return the placeholders {@code event_name}, {@code effect}, {@code scope}, {@code ends} and {@code budget}
     */
    public static TagResolver.Single[] values(
            MenuComponents texts, ServerEvent event, DoubleFunction<String> money, ZoneId zone) {
        var budget = budget(event, money);
        return new TagResolver.Single[] {
            Values.text("event_name", event.name()),
            Values.component("effect", texts.of(effect(event))),
            Values.component("scope", texts.of(scope(event))),
            Values.text("ends", ends(event, zone)),
            Values.component("budget", budget.build().isEmpty() ? Component.empty() : texts.of(budget))
        };
    }

    /**
     * @param value a number
     * @return the number without trailing zeros, such as {@code 1.5} or {@code 5}
     */
    public static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
