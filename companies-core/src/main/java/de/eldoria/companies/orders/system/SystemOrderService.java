/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderRule;
import de.eldoria.companies.configuration.elements.systemorders.SystemOrderSettings;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderCreationService;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.OrderOutcomes;
import de.eldoria.companies.orders.SystemOrders;
import de.eldoria.companies.util.Times;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Places the orders of the defined rules of {@code system-orders.yml} and removes them again when nobody took them
 * within their lifetime. Only the primary node runs it.
 * <p>
 * A rule places an order when it is due, {@code everyHours} after its last one, and has fewer than {@code maxOpen}
 * orders open or in work. The price is found by {@link RulePricing}. The order's price must fit the rule's daily
 * budget and, for minted money, the daily cap all rules share; an account must hold it and pays it at once. A day is
 * one of the server's calendar. Each placed order stores its {@link DefinedRecord}, which is what budgets, caps and the
 * schedule count.
 * <p>
 * When a listener of the order's creation changes the price, an account pays the raise or gets the cut back; an order
 * whose decision cannot be stored is removed again, so no order exists that budgets and refunds do not know.
 * <p>
 * A rule that cannot place its order is skipped until the next check, and the reason is logged once until it changes.
 * A rule without a usable id is logged by its position. An order nobody claimed within {@code lifetimeHours} of its
 * placement or its last extension is removed and gives its money back to the account that paid it. Orders of a rule
 * that is no longer in the file run out as player orders do.
 */
public final class SystemOrderService {
    private final Supplier<SystemOrderSettings> settings;
    private final AOrderData orders;
    private final Placement placement;
    private final Funding funding;
    private final Clock clock;
    private final DoubleSupplier random;
    private final Logger logger;
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Map<String, String> notes = new ConcurrentHashMap<>();

    /**
     * @param settings  current settings of the system orders
     * @param orders    order repository
     * @param placement places and removes the orders
     * @param funding   moves the money of accounts
     * @param clock     clock giving the current time in UTC; days start in the server's zone of {@link Times#zone()}
     * @param random    a random number from 0 included to 1 excluded
     * @param logger    reports skipped rules and failures
     */
    public SystemOrderService(
            Supplier<SystemOrderSettings> settings,
            AOrderData orders,
            Placement placement,
            Funding funding,
            Clock clock,
            DoubleSupplier random,
            Logger logger) {
        this.settings = settings;
        this.orders = orders;
        this.placement = placement;
        this.funding = funding;
        this.clock = clock;
        this.random = random;
        this.logger = logger;
    }

    /**
     * Runs a check every {@link SystemOrderSettings#checkMinutes()} minutes.
     *
     * @param executor executor running the checks
     */
    public void schedule(ScheduledExecutorService executor) {
        var minutes = settings.get().checkMinutes();
        executor.scheduleWithFixedDelay(this::run, 1L, minutes, TimeUnit.MINUTES);
    }

    /**
     * Removes the orders past their lifetime, then, while system orders are enabled, places the due ones.
     *
     * @return what was removed and what each rule did
     */
    public Run run() {
        var current = settings.get();
        var now = LocalDateTime.now(clock);
        List<Integer> removed = List.of();
        try {
            removed = expire(current, now);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not remove the system orders past their lifetime", e);
        }
        if (!current.enabled()) return new Run(removed, List.of());
        var dayStart = dayStart();
        var seen = new HashSet<String>();
        var results = new ArrayList<Result>();
        var rules = current.rules();
        for (var index = 0; index < rules.size(); index++) {
            var rule = rules.get(index);
            var id = rule.id();
            if (id.isEmpty() || id.length() > SystemOrderRule.MAX_ID_LENGTH || !seen.add(id)) {
                var position = "#" + (index + 1) + (id.isEmpty() ? "" : " (" + id + ")");
                results.add(new Result(position, note(position, Step.INVALID_ID, "")));
                continue;
            }
            var label = id;
            Step step;
            try {
                step = handle(current, rule, label, now, dayStart);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not place the system order of rule " + label, e);
                step = Step.FAILED;
            }
            results.add(new Result(label, step));
        }
        return new Run(removed, results);
    }

    private List<Integer> expire(SystemOrderSettings current, LocalDateTime now) {
        Map<String, Integer> lifetimes = new LinkedHashMap<>();
        for (var rule : current.rules()) lifetimes.putIfAbsent(rule.id(), rule.lifetimeHours());
        var removed = new ArrayList<Integer>();
        lifetimes.forEach((rule, hours) -> {
            for (var decision : orders.unclaimedSystemOrders(rule, now.minusHours(hours))) {
                try {
                    if (!placement.remove(decision.order())) continue;
                    decision.paidFrom().ifPresent(account -> funding.deposit(account, decision.paid()));
                    removed.add(decision.order());
                } catch (RuntimeException e) {
                    logger.log(Level.SEVERE, "Could not remove the system order " + decision.order(), e);
                }
            }
        });
        return removed;
    }

    private Step handle(
            SystemOrderSettings current,
            SystemOrderRule rule,
            String label,
            LocalDateTime now,
            LocalDateTime dayStart) {
        var id = rule.id();
        if (!rule.enabled()) return Step.DISABLED;
        if (!placement.knows(rule.material())) return note(label, Step.UNKNOWN_ITEM, rule.material());
        var last = orders.lastSystemPlacement(id);
        if (last.isPresent() && now.isBefore(last.get().plusHours(rule.everyHours()))) return Step.NOT_DUE;
        if (orders.activeSystemOrders(id) >= rule.maxOpen()) return Step.FULL;
        var found = RulePricing.price(rule.price(), placement.average(rule.material()), random);
        if (found.isEmpty()) return note(label, Step.NO_PRICE, "");
        var priced = found.get();
        var total = OrderBuilder.total(priced.unit(), rule.amount());
        var source = rule.source();
        Double budget = null;
        if (source.dailyBudget() > 0) {
            budget = source.dailyBudget() - orders.systemSpent(id, dayStart);
            if (exceeds(total, budget)) return note(label, Step.BUDGET, "");
        }
        Double minted = null;
        String account = null;
        if (source.type() == MoneySource.SourceType.MINTED) {
            minted = current.mintedDailyCap() - orders.mintedSpent(dayStart);
            if (exceeds(total, minted)) return note(label, Step.MINTED_CAP, "");
        } else {
            account = source.account();
            if (account.isEmpty()) return note(label, Step.NO_ACCOUNT, "");
            if (!funding.withdraw(account, total)) return note(label, Step.CANNOT_PAY, account);
        }
        Placed placed;
        try {
            placed = placement.place(rule, priced.unit()).join();
        } catch (RuntimeException e) {
            giveBackAfter(e, account, total);
            throw e;
        }
        if (placed instanceof Refused refused) {
            giveBack(account, total);
            return note(label, Step.REFUSED, refused.reason());
        }
        var created = (Created) placed;
        var order = created.order();
        var paid = created.price();
        if (account != null && !settle(account, total, paid)) {
            undo(order, account, total);
            return note(label, Step.CANNOT_PAY, account);
        }
        if (budget != null && exceeds(paid, budget)) {
            undo(order, account, paid);
            return note(label, Step.BUDGET, "raised by a listener");
        }
        if (minted != null && exceeds(paid, minted)) {
            undo(order, account, paid);
            return note(label, Step.MINTED_CAP, "raised by a listener");
        }
        var record = new DefinedRecord(
                id,
                rule.name(),
                rule.material(),
                rule.amount(),
                rule.price().mode(),
                priced.average(),
                rule.price().factor(),
                priced.base(),
                priced.jitter(),
                rule.price().min(),
                rule.price().max(),
                priced.bounded(),
                priced.unit(),
                paid,
                source.type(),
                account,
                budget == null ? null : budget - paid,
                minted == null ? null : minted - paid);
        var decision = new SystemDecision(
                order, id, DefinedRecord.KIND, now, account, paid, mapper.writeValueAsString(record));
        if (!store(decision)) {
            undo(order, account, paid);
            return note(label, Step.FAILED, "decision not stored, order removed");
        }
        notes.remove(label);
        return Step.PLACED;
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

    private Step note(String label, Step step, String detail) {
        var text = step.text(detail);
        if (!Objects.equals(notes.put(label, text), text)) {
            logger.info("The system order rule " + label + " placed nothing: " + text);
        }
        return step;
    }

    private static boolean exceeds(double total, double left) {
        return total > left + 0.000_001;
    }

    private LocalDateTime dayStart() {
        return Times.startOfDay(LocalDate.now(clock.withZone(Times.zone())));
    }

    /**
     * Placing for a running plugin: materials are Bukkit materials, prices are the market averages, orders are placed
     * through {@link SystemOrders} and removed as purged.
     *
     * @param orderData    order repository
     * @param systemOrders places the orders
     * @param outcomes     removes the orders
     * @return the placement
     */
    public static Placement placement(AOrderData orderData, SystemOrders systemOrders, OrderOutcomes outcomes) {
        return new Placement() {
            @Override
            public boolean knows(String material) {
                return item(material) != null;
            }

            @Override
            public OptionalDouble average(String material) {
                var item = item(material);
                if (item == null) return OptionalDouble.empty();
                var price =
                        orderData.retrieveMaterialPrice(item.name()).asFuture().join();
                return price != null && price.avgPrice() > 0
                        ? OptionalDouble.of(price.avgPrice())
                        : OptionalDouble.empty();
            }

            @Override
            public CompletableFuture<Placed> place(SystemOrderRule rule, double unitPrice) {
                var item = Objects.requireNonNull(item(rule.material()));
                var line = new OrderBuilder.DraftLine(new ItemStack(item), false, rule.amount(), unitPrice);
                var draft = OrderDraftService.toDraft(rule.name(), List.of(line));
                return systemOrders.place(draft, rule.id()).thenApply(outcome -> switch (outcome) {
                    case OrderCreationService.Created created ->
                        new Created(created.order().id(), created.price());
                    case OrderCreationService.Rejected rejected ->
                        new Refused(rejected.reason().name());
                });
            }

            @Override
            public boolean remove(int order) {
                var simple = orderData.retrieveOrderById(order).asFuture().join();
                if (simple == null || simple.isEmpty() || simple.get().state() != OrderState.UNCLAIMED) return false;
                var full = orderData.retrieveFullOrder(simple.get()).asFuture().join();
                return full != null && Boolean.TRUE.equals(outcomes.purge(full).join());
            }

            private static @Nullable Material item(String material) {
                var found = Material.matchMaterial(material);
                return found != null && found.isItem() && !found.isAir() ? found : null;
            }
        };
    }

    /**
     * Places and removes system orders.
     */
    public interface Placement {
        /**
         * @param material a material name as a rule writes it
         * @return true when it names an item that can be ordered
         */
        boolean knows(String material);

        /**
         * @param material a material name as a rule writes it
         * @return the market average per item, empty when there is none
         */
        OptionalDouble average(String material);

        /**
         * Places the order of a rule, already paid.
         *
         * @param rule      the rule
         * @param unitPrice price per item
         * @return the order, or why it was refused
         */
        CompletableFuture<Placed> place(SystemOrderRule rule, double unitPrice);

        /**
         * Removes an order nobody claimed.
         *
         * @param order id of the order
         * @return true when it was unclaimed and is now removed
         */
        boolean remove(int order);
    }

    /**
     * Moves the money of economy accounts.
     */
    public interface Funding {
        /**
         * @param account the account
         * @param amount  amount to take
         * @return true when the account held the amount and it was taken
         */
        boolean withdraw(String account, double amount);

        /**
         * @param account the account
         * @param amount  amount to give back
         */
        void deposit(String account, double amount);
    }

    /**
     * What came of placing an order.
     */
    public sealed interface Placed permits Created, Refused {}

    /**
     * @param order id of the placed order
     * @param price price of the order as it was saved, after the listeners of its creation
     */
    public record Created(int order, double price) implements Placed {}

    /**
     * @param reason why the order was not placed
     */
    public record Refused(String reason) implements Placed {}

    /**
     * What a check did.
     *
     * @param removed the orders removed after their lifetime
     * @param rules   what each rule did, in file order
     */
    public record Run(List<Integer> removed, List<Result> rules) {}

    /**
     * @param rule the rule's id, or its position when it has none
     * @param step what it did
     */
    public record Result(String rule, Step step) {}

    /**
     * What a rule did in a check.
     */
    public enum Step {
        /**
         * It placed an order.
         */
        PLACED,
        /**
         * It is switched off.
         */
        DISABLED,
        /**
         * Its next order is not due yet.
         */
        NOT_DUE,
        /**
         * It has as many orders open or in work as it may.
         */
        FULL,
        /**
         * Its id is missing, too long, or used by an earlier rule.
         */
        INVALID_ID,
        /**
         * Its material is no item.
         */
        UNKNOWN_ITEM,
        /**
         * Its price follows the market average, and the material has none yet, or the price came out at 0.
         */
        NO_PRICE,
        /**
         * The order would exceed the rule's budget of the day.
         */
        BUDGET,
        /**
         * The order would exceed the minted money of the day.
         */
        MINTED_CAP,
        /**
         * It is paid from an account, and none is set.
         */
        NO_ACCOUNT,
        /**
         * Its account cannot pay the order.
         */
        CANNOT_PAY,
        /**
         * The order was refused, such as by an integration or the order limits.
         */
        REFUSED,
        /**
         * Something went wrong; it is logged.
         */
        FAILED;

        private String text(String detail) {
            var base = name().toLowerCase(Locale.ROOT).replace('_', ' ');
            return detail.isEmpty() ? base : base + " (" + detail + ")";
        }
    }
}
