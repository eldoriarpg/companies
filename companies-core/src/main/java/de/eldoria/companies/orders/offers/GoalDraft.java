/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.offers;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.configuration.elements.systemorders.RuleEntry;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.criteria.CriteriaRules;
import de.eldoria.companies.orders.criteria.CriteriaSyntax;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Checks what an admin entered for a new goal or contest and builds it.
 * <p>
 * A goal is named like an order, at most {@value EventOffer#MAX_NAME_LENGTH} characters, since its chunks carry its
 * name. Its money is {@code minted} or the name of an economy account; its budget is what its chunks cost at its
 * price, so a goal runs out of money only when a listener of the order creation raises the price of its chunks.
 * An admin may give a budget of its own instead, which must pay at least one item; a smaller budget than the cost
 * closes the goal as depleted early. It keeps {@value #OPEN_CHUNKS} chunks waiting at first, which
 * {@code /companyadmin offers open} changes. Start and run time are at most {@value #MAX_HOURS} hours each.
 */
public final class GoalDraft {
    /**
     * The money source that mints the money.
     */
    public static final String MINTED = "minted";

    /**
     * Chunks a new goal keeps waiting to be taken.
     */
    public static final int OPEN_CHUNKS = 3;

    /**
     * The most chunks of a goal that may wait to be taken at once.
     */
    public static final int MAX_OPEN_CHUNKS = 100;

    /**
     * The most hours a goal may wait for its start, and the most it may run: a year.
     */
    public static final int MAX_HOURS = 8760;

    /**
     * The word for a contest without prizes.
     */
    public static final String NO_PRIZES = "none";

    /**
     * The most places a contest pays prizes for.
     */
    public static final int MAX_PLACES = 10;

    /**
     * The highest price per item.
     */
    public static final double MAX_PRICE = 1_000_000;

    /**
     * The highest budget of a goal.
     */
    public static final double MAX_BUDGET = 1_000_000_000_000.0;

    private GoalDraft() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Builds a goal.
     *
     * @param name       name of the goal
     * @param money      {@value #MINTED} or the economy account paying the chunks
     * @param line       what it orders
     * @param amount     items asked for in all
     * @param unitPrice  price per item
     * @param chunkSize  items per chunk
     * @param startHours hours from now until it starts, 0 for now
     * @param hours      hours it runs from its start
     * @param budget     what the whole goal may spend, null for what its chunks cost
     * @param createdBy  who creates it
     * @param now        the current time
     * @return the goal, not stored yet
     * @throws CommandFailure when a value is not usable
     */
    public static EventOffer goal(
            String name,
            String money,
            OfferLine line,
            int amount,
            double unitPrice,
            int chunkSize,
            int startHours,
            int hours,
            @Nullable Double budget,
            String createdBy,
            LocalDateTime now) {
        return goal(
                name,
                money,
                line,
                amount,
                unitPrice,
                chunkSize,
                OPEN_CHUNKS,
                startHours,
                hours,
                budget,
                createdBy,
                now);
    }

    /**
     * Builds a goal with a number of chunks waiting to be taken at once of its own.
     *
     * @param name       name of the goal
     * @param money      {@value #MINTED} or the economy account paying the chunks
     * @param line       what it orders
     * @param amount     items asked for in all
     * @param unitPrice  price per item
     * @param chunkSize  items per chunk
     * @param openChunks chunks waiting to be taken at once, 1 to {@value #MAX_OPEN_CHUNKS}
     * @param startHours hours from now until it starts, 0 for now
     * @param hours      hours it runs from its start
     * @param budget     what the whole goal may spend, null for what its chunks cost
     * @param createdBy  who creates it
     * @param now        the current time
     * @return the goal, not stored yet
     * @throws CommandFailure when a value is not usable
     */
    public static EventOffer goal(
            String name,
            String money,
            OfferLine line,
            int amount,
            double unitPrice,
            int chunkSize,
            int openChunks,
            int startHours,
            int hours,
            @Nullable Double budget,
            String createdBy,
            LocalDateTime now) {
        var trimmed = checkName(name);
        if (openChunks < 1 || openChunks > MAX_OPEN_CHUNKS) {
            throw CommandFailure.of("companyadmin.offers.error.openChunks", Values.number("max", MAX_OPEN_CHUNKS));
        }
        if (amount < 1 || chunkSize < 1 || hours < 1 || startHours < 0) {
            throw CommandFailure.of("companyadmin.offers.error.numbers");
        }
        checkTimesAndPrice(unitPrice, startHours, hours);
        var chunk = Math.min(chunkSize, amount);
        var spend = budget == null ? budget(unitPrice, amount, chunk) : budget;
        if (!Double.isFinite(spend) || spend > MAX_BUDGET) {
            throw CommandFailure.of("companyadmin.offers.error.budgetHigh", Values.number("max", MAX_BUDGET));
        }
        if (spend < OrderBuilder.total(unitPrice, 1)) {
            throw CommandFailure.of(
                    "companyadmin.offers.error.budgetLow", Values.text("price", String.valueOf(unitPrice)));
        }
        var source = checkMoney(money);
        var minted = source.toLowerCase(Locale.ROOT).equals(MINTED);
        var starts = now.plusHours(startHours);
        return EventOffer.goal(
                trimmed,
                line,
                amount,
                unitPrice,
                chunk,
                openChunks,
                minted ? MoneySource.SourceType.MINTED : MoneySource.SourceType.ACCOUNT,
                minted ? null : source,
                spend,
                starts,
                starts.plusHours(hours),
                createdBy,
                now);
    }

    /**
     * Builds a contest. Its prizes are kept back from its budget, which by default is what its whole amount costs at
     * its price plus the prizes; a budget of its own must hold the prizes and one item.
     *
     * @param name       name of the contest
     * @param money      {@value #MINTED} or the economy account paying it
     * @param line       what it orders
     * @param amount     items asked for in all
     * @param unitPrice  price per delivered item
     * @param prizes     the money of the first places, first place first
     * @param startHours hours from now until it starts, 0 for now
     * @param hours      hours it runs from its start
     * @param budget     what the whole contest may spend, null for its cost and its prizes
     * @param createdBy  who creates it
     * @param now        the current time
     * @return the contest, not stored yet
     * @throws CommandFailure when a value is not usable
     */
    public static EventOffer contest(
            String name,
            String money,
            OfferLine line,
            int amount,
            double unitPrice,
            List<Double> prizes,
            int startHours,
            int hours,
            @Nullable Double budget,
            String createdBy,
            LocalDateTime now) {
        var trimmed = checkName(name);
        if (amount < 1 || hours < 1 || startHours < 0) throw CommandFailure.of("companyadmin.offers.error.numbers");
        checkTimesAndPrice(unitPrice, startHours, hours);
        var prizeTotal = BigDecimal.ZERO;
        for (var prize : prizes) prizeTotal = prizeTotal.add(BigDecimal.valueOf(prize));
        var spend = budget == null
                ? BigDecimal.valueOf(OrderBuilder.total(unitPrice, amount))
                        .add(prizeTotal)
                        .doubleValue()
                : budget;
        if (!Double.isFinite(spend) || spend > MAX_BUDGET) {
            throw CommandFailure.of("companyadmin.offers.error.budgetHigh", Values.number("max", MAX_BUDGET));
        }
        if (spend - prizeTotal.doubleValue() < OrderBuilder.total(unitPrice, 1) - 0.000_001) {
            throw CommandFailure.of(
                    "companyadmin.offers.error.budgetPrizes",
                    Values.text("prizes", prizeTotal.toPlainString()),
                    Values.text("price", String.valueOf(unitPrice)));
        }
        var source = checkMoney(money);
        var minted = source.toLowerCase(Locale.ROOT).equals(MINTED);
        var starts = now.plusHours(startHours);
        return EventOffer.contest(
                trimmed,
                line,
                amount,
                unitPrice,
                prizes,
                minted ? MoneySource.SourceType.MINTED : MoneySource.SourceType.ACCOUNT,
                minted ? null : source,
                spend,
                starts,
                starts.plusHours(hours),
                createdBy,
                now);
    }

    /**
     * Reads the prizes of a contest: {@value #NO_PRIZES}, or the money of the first places separated by commas or plus
     * signs, such as {@code 500+250+100}, at most {@value #MAX_PLACES} places.
     *
     * @param text the prizes as an admin typed them
     * @return the money of the places, first place first
     * @throws CommandFailure when they cannot be read
     */
    public static List<Double> prizes(String text) {
        var trimmed = text.trim();
        if (trimmed.equalsIgnoreCase(NO_PRIZES)) return List.of();
        var parts = trimmed.split("[,+]");
        if (parts.length > MAX_PLACES) {
            throw CommandFailure.of("companyadmin.offers.error.prizes", Values.number("max", MAX_PLACES));
        }
        List<Double> prizes = new ArrayList<>();
        for (var part : parts) {
            double value;
            try {
                value = new BigDecimal(part.trim())
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            } catch (NumberFormatException | ArithmeticException e) {
                throw CommandFailure.of("companyadmin.offers.error.prizes", Values.number("max", MAX_PLACES));
            }
            if (!(value > 0) || value > MAX_BUDGET) {
                throw CommandFailure.of("companyadmin.offers.error.prizes", Values.number("max", MAX_PLACES));
            }
            prizes.add(value);
        }
        return List.copyOf(prizes);
    }

    private static String checkName(String name) {
        var trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > EventOffer.MAX_NAME_LENGTH) {
            throw CommandFailure.of("companyadmin.offers.error.name", Values.number("max", EventOffer.MAX_NAME_LENGTH));
        }
        return trimmed;
    }

    private static void checkTimesAndPrice(double unitPrice, int startHours, int hours) {
        if (hours > MAX_HOURS || startHours > MAX_HOURS) {
            throw CommandFailure.of("companyadmin.offers.error.hours", Values.number("max", MAX_HOURS));
        }
        if (!(unitPrice > 0) || unitPrice > MAX_PRICE) {
            throw CommandFailure.of("companyadmin.offers.error.price", Values.number("max", MAX_PRICE));
        }
    }

    private static String checkMoney(String money) {
        var source = money.trim();
        if (source.isEmpty()) throw CommandFailure.of("companyadmin.offers.error.money");
        return source;
    }

    /**
     * What a goal costs when its chunks are placed as planned: each chunk's price is rounded to cents like any order's,
     * so the budget is the sum of the chunks rather than the rounded price of the whole amount.
     *
     * @param unitPrice price per item
     * @param amount    items asked for in all
     * @param chunkSize items per chunk, at least 1
     * @return the budget
     */
    static double budget(double unitPrice, int amount, int chunkSize) {
        var full = amount / chunkSize;
        var rest = amount % chunkSize;
        var total = BigDecimal.valueOf(OrderBuilder.total(unitPrice, chunkSize)).multiply(BigDecimal.valueOf(full));
        if (rest > 0) total = total.add(BigDecimal.valueOf(OrderBuilder.total(unitPrice, rest)));
        return total.doubleValue();
    }

    /**
     * @param material  a material as an admin typed it
     * @param canonical the material's name as order lines store it, empty when it names no item that can be ordered
     * @return a line ordering any item of it
     * @throws CommandFailure when it names no item
     */
    public static OfferLine material(String material, Function<String, Optional<String>> canonical) {
        return canonical
                .apply(material)
                .map(OfferLine::material)
                .orElseThrow(() ->
                        CommandFailure.of("companyadmin.offers.error.material", Values.text("material", material)));
    }

    /**
     * @param criteria criteria in the text form players type
     * @return a line accepting every item meeting them
     * @throws CommandFailure when they cannot be read or ordered
     */
    public static OfferLine criteria(String criteria) {
        var text = criteria.trim();
        var parsed = CriteriaSyntax.parse(text);
        CriteriaRules.problem(parsed).ifPresent(problem -> {
            throw problem;
        });
        return OfferLine.criteria(text, RuleEntry.Resolve.ANY);
    }
}
