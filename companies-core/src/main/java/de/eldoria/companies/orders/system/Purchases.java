/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.system.SystemOrderService.Created;
import de.eldoria.companies.orders.system.SystemOrderService.Funding;
import de.eldoria.companies.orders.system.SystemOrderService.Placed;
import de.eldoria.companies.orders.system.SystemOrderService.Placement;
import de.eldoria.companies.orders.system.SystemOrderService.Refused;
import de.eldoria.companies.orders.system.SystemOrderService.Step;
import de.eldoria.companies.orders.system.SystemOrderService.Wanted;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pays for and places system orders, and removes them again with their money.
 * <p>
 * An order's price must fit its rule's daily budget and, for minted money, the daily cap all rules share; an account
 * must hold it and pays it at once. When a listener of the order's creation changes the price, an account pays the
 * raise or gets the cut back, and a raise beyond the budget or the cap removes the order again. An order whose decision
 * cannot be stored is removed again, so no order exists that budgets and refunds do not know. A reason an order was not
 * placed is logged once per label until it changes.
 */
public final class Purchases {
    private final AOrderData orders;
    private final Placement placement;
    private final Funding funding;
    private final Logger logger;
    private final String subject;
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Map<String, String> notes = new ConcurrentHashMap<>();

    /**
     * @param orders    order repository
     * @param placement places and removes the orders
     * @param funding   moves the money of accounts
     * @param logger    reports skipped orders and failures
     */
    public Purchases(AOrderData orders, Placement placement, Funding funding, Logger logger) {
        this(orders, placement, funding, logger, "system order rule");
    }

    /**
     * @param orders    order repository
     * @param placement places and removes the orders
     * @param funding   moves the money of accounts
     * @param logger    reports skipped orders and failures
     * @param subject   what the labels of the log name, such as {@code event offer}
     */
    public Purchases(AOrderData orders, Placement placement, Funding funding, Logger logger, String subject) {
        this.orders = orders;
        this.placement = placement;
        this.funding = funding;
        this.logger = logger;
        this.subject = subject;
    }

    /**
     * Pays for an order of a rule and places it, within the rule's daily budget and, for minted money, the daily cap
     * all rules share.
     *
     * @param label      names the order in the log
     * @param wanted     the order
     * @param source     where its money comes from
     * @param mintedCap  minted money all rules may spend per day
     * @param now        the current time
     * @param dayStart   start of the current day of the server's calendar
     * @param kind       the kind of decision stored with it
     * @param record     the decision record of the money it was paid with
     * @return what came of it, with the order when it was placed
     */
    Bought buy(
            String label,
            Wanted wanted,
            MoneySource source,
            double mintedCap,
            LocalDateTime now,
            LocalDateTime dayStart,
            String kind,
            Function<Money, Object> record) {
        var total = OrderBuilder.total(wanted.unit(), wanted.amount());
        Double budget = null;
        if (source.dailyBudget() > 0) {
            budget = source.dailyBudget() - orders.systemSpent(wanted.rule(), dayStart);
            if (exceeds(total, budget)) return skipped(label, Step.BUDGET, "");
        }
        Double minted = null;
        if (source.type() == MoneySource.SourceType.MINTED) {
            minted = mintedCap - orders.mintedSpent(dayStart);
            if (exceeds(total, minted)) return skipped(label, Step.MINTED_CAP, "");
        }
        return buy(label, wanted, source.type(), source.account(), budget, minted, now, kind, record);
    }

    /**
     * Pays for an order and places it, within the budget and the minted money left that the caller counted.
     *
     * @param label      names the order in the log
     * @param wanted     the order
     * @param type       where its money comes from
     * @param account    the economy account paying it, used for {@link MoneySource.SourceType#ACCOUNT}
     * @param budget     what may still be spent, null for no budget
     * @param minted     minted money that may still be spent, null for no cap; used for minted money only
     * @param now        the current time
     * @param kind       the kind of decision stored with it
     * @param record     the decision record of the money it was paid with
     * @return what came of it, with the order and its price when it was placed
     */
    public Bought buy(
            String label,
            Wanted wanted,
            MoneySource.SourceType type,
            String account,
            @Nullable Double budget,
            @Nullable Double minted,
            LocalDateTime now,
            String kind,
            Function<Money, Object> record) {
        var total = OrderBuilder.total(wanted.unit(), wanted.amount());
        if (budget != null && exceeds(total, budget)) return skipped(label, Step.BUDGET, "");
        String paying = null;
        if (type == MoneySource.SourceType.MINTED) {
            if (minted != null && exceeds(total, minted)) return skipped(label, Step.MINTED_CAP, "");
        } else {
            minted = null;
            paying = account.trim();
            if (paying.isEmpty()) return skipped(label, Step.NO_ACCOUNT, "");
            if (!funding.withdraw(paying, total)) return skipped(label, Step.CANNOT_PAY, paying);
        }
        return place(label, wanted, type, paying, budget, minted, total, now, kind, record);
    }

    private Bought place(
            String label,
            Wanted wanted,
            MoneySource.SourceType type,
            @Nullable String account,
            @Nullable Double budget,
            @Nullable Double minted,
            double total,
            LocalDateTime now,
            String kind,
            Function<Money, Object> record) {
        Placed placed;
        try {
            placed = placement.place(wanted).join();
        } catch (RuntimeException e) {
            giveBackAfter(e, account, total);
            throw e;
        }
        if (placed instanceof Refused refused) {
            giveBack(account, total);
            return skipped(label, Step.REFUSED, refused.reason());
        }
        var created = (Created) placed;
        var order = created.order();
        var paid = created.price();
        if (account != null && !settle(account, total, paid)) {
            undo(order, account, total);
            return skipped(label, Step.CANNOT_PAY, account);
        }
        if (budget != null && exceeds(paid, budget)) {
            undo(order, account, paid);
            return skipped(label, Step.BUDGET, "raised by a listener");
        }
        if (minted != null && exceeds(paid, minted)) {
            undo(order, account, paid);
            return skipped(label, Step.MINTED_CAP, "raised by a listener");
        }
        var money = new Money(
                paid, type, account, budget == null ? null : budget - paid, minted == null ? null : minted - paid);
        var decision = new SystemDecision(
                order, wanted.rule(), kind, now, account, paid, mapper.writeValueAsString(record.apply(money)));
        if (!store(decision)) {
            undo(order, account, paid);
            return skipped(label, Step.FAILED, "decision not stored, order removed");
        }
        notes.remove(label);
        return new Bought(Step.PLACED, OptionalInt.of(order), paid);
    }

    /**
     * Removes an order nobody claimed and gives its money back to the account that paid it.
     *
     * @param decision the decision of the order
     * @return true when it was unclaimed and is now removed
     */
    public boolean remove(SystemDecision decision) {
        try {
            if (!placement.remove(decision.order())) return false;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not remove the system order " + decision.order(), e);
            return false;
        }
        decision.paidFrom().ifPresent(account -> funding.deposit(account, decision.paid()));
        return true;
    }

    /**
     * Reads a stored decision record.
     *
     * @param decision the decision
     * @param type     the record type of its kind
     * @param <T>      the record type
     * @return the record, null when it cannot be read
     */
    <T> @Nullable T read(SystemDecision decision, Class<T> type) {
        try {
            return mapper.readValue(decision.record(), type);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Logs why nothing was placed, once until the reason for the label changes.
     *
     * @param label  names what placed nothing
     * @param step   what happened
     * @param detail what it concerned, may be empty
     * @return the step
     */
    public Step note(String label, Step step, String detail) {
        var text = step.text(detail);
        if (!Objects.equals(notes.put(label, text), text)) {
            logger.info("The " + subject + " " + label + " placed nothing: " + text);
        }
        return step;
    }

    private Bought skipped(String label, Step step, String detail) {
        return new Bought(note(label, step, detail), OptionalInt.empty(), 0);
    }

    /**
     * Settles the difference when a listener of the order's creation changed its price: the account pays a raise or
     * gets a cut back.
     *
     * @param account   the account that paid
     * @param withdrawn what it paid
     * @param price     the price the order was saved with
     * @return false when the account cannot pay a raise
     */
    private boolean settle(String account, double withdrawn, double price) {
        var difference = price - withdrawn;
        if (Math.abs(difference) < 0.005) return true;
        if (difference < 0) {
            funding.deposit(account, -difference);
            return true;
        }
        return funding.withdraw(account, difference);
    }

    /**
     * Removes an order placed in this check again and gives back what its account paid for it. An order that cannot
     * be removed keeps the money, which is logged.
     *
     * @param order   id of the order
     * @param account the account that paid, null for minted money
     * @param amount  what the account paid
     */
    private void undo(int order, @Nullable String account, double amount) {
        boolean removed;
        try {
            removed = placement.remove(order);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not remove the system order " + order + " again", e);
            removed = false;
        }
        if (removed) giveBack(account, amount);
        else if (account != null) lost(order, account, amount);
        else logger.severe("The system order " + order + " could not be removed after a failure and stays open.");
    }

    private boolean store(SystemDecision decision) {
        try {
            if (orders.saveSystemDecision(decision)) return true;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not store why the system order " + decision.order() + " was placed", e);
        }
        return false;
    }

    private void giveBack(@Nullable String account, double amount) {
        if (account != null) funding.deposit(account, amount);
    }

    private void giveBackAfter(RuntimeException failure, @Nullable String account, double amount) {
        try {
            giveBack(account, amount);
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    private void lost(int order, String account, double amount) {
        logger.severe("The system order " + order + " could not be removed after a failure. The " + amount
                + " the account " + account + " paid is not given back when the order ends.");
    }

    private static boolean exceeds(double total, double left) {
        return total > left + 0.000_001;
    }

    /**
     * What came of buying an order.
     *
     * @param step  what happened
     * @param order the placed order, when it was placed
     * @param paid  what was put into the placed order, 0 when none was placed
     */
    public record Bought(Step step, OptionalInt order, double paid) {}

    /**
     * The money an order was paid with.
     *
     * @param paid       what was put into the order
     * @param source     where it came from
     * @param account    the economy account that paid, null for minted money
     * @param budgetLeft what the rule may still spend that day, null without a budget
     * @param mintedLeft minted money all rules may still spend that day, null for an account
     */
    public record Money(
            double paid,
            MoneySource.SourceType source,
            @Nullable String account,
            @Nullable Double budgetLeft,
            @Nullable Double mintedLeft) {}
}
