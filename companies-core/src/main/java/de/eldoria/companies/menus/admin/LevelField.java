/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.util.Texts;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The editable fields of a company level.
 * <p>
 * The {@link #key() key} is the field name of {@code /companyadmin level edit}, the key of the dialog input and the
 * placeholder key of the value. Input is parsed the same way wherever it is entered: counts are whole numbers, the
 * earned money a finite number, the order size limit, the delivery bonus, the alert and the area limit are at least
 * 0, and the
 * tax is a percentage from 0 to 100. The tax and the alert limit are cleared by {@code none} or an empty input, so the
 * global value applies again.
 */
public enum LevelField {
    NAME(Group.NAME, "words.name"),
    ORDER_COUNT(Group.REQUIREMENT, "level.orderCount"),
    MEMBER_COUNT(Group.REQUIREMENT, "level.memberCount"),
    EARNED_MONEY(Group.REQUIREMENT, "level.earnedMoney"),
    DELIVERED_ITEMS(Group.REQUIREMENT, "level.deliveredItems"),
    MAX_MEMBERS(Group.LIMIT, "level.maxMember"),
    MAX_ORDERS(Group.LIMIT, "level.maxOrders"),
    MAX_ORDER_SIZE(Group.LIMIT, "level.maxOrderSize"),
    DELIVERY_HOURS_BONUS(Group.LIMIT, "level.deliveryHoursBonus"),
    TAX_PERCENT(Group.LIMIT, "level.tax"),
    MAX_ALERTS(Group.LIMIT, "level.maxAlerts"),
    MAX_AREAS(Group.LIMIT, "level.maxAreas");

    private static final String NONE = "none";

    private final Group group;
    private final String labelKey;

    LevelField(Group group, String labelKey) {
        this.group = group;
        this.labelKey = labelKey;
    }

    /**
     * @param key field name, ignoring case
     * @return the field of that name
     */
    public static Optional<LevelField> byKey(String key) {
        return Arrays.stream(values())
                .filter(field -> field.key().equalsIgnoreCase(key.trim()))
                .findFirst();
    }

    /**
     * @return field name, such as {@code order_count}
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @return locale key of the label
     */
    public String labelKey() {
        return labelKey;
    }

    /**
     * @return the section the field belongs to
     */
    public Group group() {
        return group;
    }

    /**
     * The current value as it would be entered, so it can prefill an input. A level without a tax or alert limit
     * override gives an empty text.
     *
     * @param level the level
     * @return the value as input text
     */
    public String input(CompanyLevel level) {
        var requirement = level.requirement();
        var settings = level.settings();
        return switch (this) {
            case NAME -> level.levelName();
            case ORDER_COUNT -> String.valueOf(requirement.orderCount());
            case MEMBER_COUNT -> String.valueOf(requirement.memberCount());
            case EARNED_MONEY -> Texts.percent(requirement.earnedMoney());
            case DELIVERED_ITEMS -> String.valueOf(requirement.deliveredItems());
            case MAX_MEMBERS -> String.valueOf(settings.maxMembers());
            case MAX_ORDERS -> String.valueOf(settings.maxOrders());
            case MAX_ORDER_SIZE -> String.valueOf(settings.maxOrderSize());
            case DELIVERY_HOURS_BONUS -> String.valueOf(settings.deliveryHoursBonus());
            case TAX_PERCENT -> settings.taxPercent().map(Texts::percent).orElse("");
            case MAX_ALERTS -> settings.maxAlerts().map(String::valueOf).orElse("");
            case MAX_AREAS -> String.valueOf(settings.maxAreas());
        };
    }

    /**
     * The value as shown in a level description, or empty when the field is left out because it changes nothing:
     * no order size limit, no delivery bonus, no tax override or no alert limit override.
     *
     * @param level the level
     * @return the shown value
     */
    public Optional<String> shown(CompanyLevel level) {
        var settings = level.settings();
        return switch (this) {
            case MAX_ORDER_SIZE -> positive(settings.maxOrderSize()).map(String::valueOf);
            case DELIVERY_HOURS_BONUS -> positive(settings.deliveryHoursBonus()).map(hours -> "+" + hours + " h");
            case TAX_PERCENT -> settings.taxPercent().map(percent -> Texts.percent(percent) + " %");
            case MAX_ALERTS -> settings.maxAlerts().map(String::valueOf);
            default -> Optional.of(input(level));
        };
    }

    /**
     * Parses entered text into the change of this field, without changing anything yet.
     *
     * @param input entered text
     * @return applies the parsed value to a level
     * @throws de.eldoria.companies.commands.support.CommandFailure when the text is no valid value
     */
    public Consumer<CompanyLevel> parse(String input) {
        return switch (this) {
            case NAME -> level -> level.levelName(input);
            case ORDER_COUNT -> {
                var value = Checks.integer(input);
                yield level -> level.requirement().orderCount(value);
            }
            case MEMBER_COUNT -> {
                var value = Checks.integer(input);
                yield level -> level.requirement().memberCount(value);
            }
            case EARNED_MONEY -> {
                var value = Checks.finiteDouble(input);
                yield level -> level.requirement().earnedMoney(value);
            }
            case DELIVERED_ITEMS -> {
                var value = Checks.integer(input);
                yield level -> level.requirement().deliveredItems(value);
            }
            case MAX_MEMBERS -> {
                var value = Checks.integer(input);
                yield level -> level.settings().maxMembers(value);
            }
            case MAX_ORDERS -> {
                var value = Checks.integer(input);
                yield level -> level.settings().maxOrders(value);
            }
            case MAX_ORDER_SIZE -> {
                var value = Math.max(0, Checks.integer(input));
                yield level -> level.settings().maxOrderSize(value);
            }
            case DELIVERY_HOURS_BONUS -> {
                var value = Math.max(0, Checks.integer(input));
                yield level -> level.settings().deliveryHoursBonus(value);
            }
            case TAX_PERCENT -> {
                var value = taxPercent(input);
                yield level -> level.settings().taxPercent(value);
            }
            case MAX_ALERTS -> {
                var value = maxAlerts(input);
                yield level -> level.settings().maxAlerts(value);
            }
            case MAX_AREAS -> {
                var value = Math.max(0, Checks.integer(input));
                yield level -> level.settings().maxAreas(value);
            }
        };
    }

    /**
     * Parses entered text and changes the field of a level.
     *
     * @param level the level to change
     * @param input entered text
     * @throws de.eldoria.companies.commands.support.CommandFailure when the text is no valid value
     */
    public void apply(CompanyLevel level, String input) {
        parse(input).accept(level);
    }

    /**
     * Parses a tax override: {@code none} or nothing clears it, a number is clamped to 0 to 100.
     *
     * @param input entered text
     * @return percentage, or null to use the global tax
     */
    static Double taxPercent(String input) {
        var trimmed = input.trim();
        if (cleared(trimmed)) return null;
        return Math.clamp(Checks.finiteDouble(trimmed), 0.0, 100.0);
    }

    /**
     * Parses an alert limit override: {@code none} or nothing clears it, a number below 0 becomes 0, which turns
     * alerts off for the level.
     *
     * @param input entered text
     * @return alerts per company, or null to use the global limit
     */
    static Integer maxAlerts(String input) {
        var trimmed = input.trim();
        if (cleared(trimmed)) return null;
        return Math.max(0, Checks.integer(trimmed));
    }

    private static boolean cleared(String trimmed) {
        return trimmed.isEmpty() || NONE.equalsIgnoreCase(trimmed);
    }

    private static Optional<Integer> positive(int value) {
        return value > 0 ? Optional.of(value) : Optional.empty();
    }

    /**
     * The sections of a level.
     */
    public enum Group {
        /**
         * The name of the level.
         */
        NAME("words.name"),
        /**
         * What a company has to reach for the level.
         */
        REQUIREMENT("level.requirements"),
        /**
         * What the level allows a company.
         */
        LIMIT("level.limits");

        private final String headingKey;

        Group(String headingKey) {
            this.headingKey = headingKey;
        }

        /**
         * @return locale key of the heading of the section
         */
        public String headingKey() {
            return headingKey;
        }
    }
}
