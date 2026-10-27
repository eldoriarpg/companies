/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.orders.system;

import com.google.common.base.Suppliers;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.RulePrice;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Places the orders of {@code system-orders.yml} and removes them again when nobody takes them. Only the primary node
 * runs it.
 * <p>
 * A defined rule places an order when it is due, {@code everyHours} after its last one, and has fewer than
 * {@code maxOpen} orders open or in work. The price is found by {@link RulePricing}, the money by {@link Purchases}.
 * Each placed order stores its {@link DefinedRecord}, which is what budgets, caps and the schedule count. A rule that
 * cannot place its order is skipped until the next check, and the reason is logged once until it changes. A rule
 * without a usable id, or with the id {@value GuessedSettings#RULE} the guessed orders use, is logged by its position.
 * An order nobody claimed within {@code lifetimeHours} of its placement or its last extension is removed and gives its
 * money back to the account that paid it. Orders of a rule that is no longer in the file run out as player orders do.
 * <p>
 * Guessed orders are kept running by {@link GuessedOrders} after the defined rules.
 */
public final class SystemOrderService {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private final Supplier<SystemOrderSettings> settings;
    private final AOrderData orders;
    private final Placement placement;
    private final Clock clock;
    private final DoubleSupplier random;
    private final Logger logger;
    private final Purchases purchases;
    private final GuessedOrders guessed;

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
        this.clock = clock;
        this.random = random;
        this.logger = logger;
        purchases = new Purchases(orders, placement, funding, logger);
        guessed = new GuessedOrders(orders, placement, purchases, random, logger);
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
     * Removes the orders of defined rules past their lifetime and, while system orders are enabled, places the due
     * ones; then keeps the guessed orders running.
     *
     * @return what was removed and what each rule did
     */
    public Run run() {
        var current = settings.get();
        var now = LocalDateTime.now(clock);
        var removed = new ArrayList<Integer>();
        try {
            expire(current, now, removed);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not remove the system orders past their lifetime", e);
        }
        var results = new ArrayList<Result>();
        var dayStart = dayStart();
        if (current.enabled()) defined(current, now, dayStart, results);
        try {
            var placing = current.enabled() && current.guessed().enabled();
            guessed.run(current, placing, now, dayStart, removed, results);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not keep the guessed system orders running", e);
            results.add(new Result(GuessedSettings.RULE, Step.FAILED));
        }
        return new Run(removed, results);
    }

    private void defined(SystemOrderSettings current, LocalDateTime now, LocalDateTime dayStart, List<Result> results) {
        var seen = new HashSet<String>();
        var rules = current.rules();
        for (var index = 0; index < rules.size(); index++) {
            var rule = rules.get(index);
            var id = rule.id();
            if (!usable(id) || !seen.add(id)) {
                var position = "#" + (index + 1) + (id.isEmpty() ? "" : " (" + id + ")");
                results.add(new Result(position, purchases.note(position, Step.INVALID_ID, "")));
                continue;
            }
            Step step;
            try {
                step = handle(current, rule, now, dayStart);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Could not place the system order of rule " + id, e);
                step = Step.FAILED;
            }
            results.add(new Result(id, step));
        }
    }

    private static boolean usable(String id) {
        return !id.isEmpty()
                && id.length() <= SystemOrderRule.MAX_ID_LENGTH
                && !id.equalsIgnoreCase(GuessedSettings.RULE);
    }

    private void expire(SystemOrderSettings current, LocalDateTime now, List<Integer> removed) {
        Map<String, Integer> lifetimes = new LinkedHashMap<>();
        for (var rule : current.rules()) {
            if (usable(rule.id())) lifetimes.putIfAbsent(rule.id(), rule.lifetimeHours());
        }
        lifetimes.forEach((rule, hours) -> {
            for (var decision : orders.unclaimedSystemOrders(rule, now.minusHours(hours))) {
                if (purchases.remove(decision)) removed.add(decision.order());
            }
        });
    }

    private Step handle(SystemOrderSettings current, SystemOrderRule rule, LocalDateTime now, LocalDateTime dayStart) {
        var id = rule.id();
        if (!rule.enabled()) return Step.DISABLED;
        var entries = rule.entries();
        if (entries.isEmpty()) return purchases.note(id, Step.UNKNOWN_ITEM, "");
        var last = orders.lastSystemDecision(id);
        if (last.isPresent() && now.isBefore(last.get().decidedAt().plusHours(rule.everyHours()))) {
            return Step.NOT_DUE;
        }
        if (orders.activeSystemOrders(id) >= rule.maxOpen()) return Step.FULL;
        var start = RuleItems.pick(rule, entries.size(), last.map(this::entryOf).orElse(null), random);
        var resting = Suppliers.memoize(() -> orders.restingMaterials(now));
        RuleItems.Chosen chosen = null;
        Step skipped = null;
        var reason = "";
        for (var offset = 0; offset < entries.size() && chosen == null; offset++) {
            var index = (start + offset) % entries.size();
            var resolved = RuleItems.resolve(entries.get(index), index, placement::knows, resting, random);
            if (resolved instanceof RuleItems.Problem(var entry)) {
                if (skipped == null) {
                    skipped = Step.UNKNOWN_ITEM;
                    reason = entry;
                }
            } else if (resolved instanceof RuleItems.Chosen found
                    && found.material() == null
                    && rule.price().mode() == RulePrice.PriceMode.AVERAGE) {
                if (skipped == null) {
                    skipped = Step.NO_PRICE;
                    reason = found.label() + " needs a fixed price";
                }
            } else {
                chosen = (RuleItems.Chosen) resolved;
            }
        }
        if (chosen == null) return purchases.note(id, Objects.requireNonNull(skipped), reason);
        var picked = chosen;
        var material = picked.material();
        var average = material == null ? OptionalDouble.empty() : placement.average(material);
        var found = RulePricing.price(rule.price(), average, random);
        if (found.isEmpty()) return purchases.note(id, Step.NO_PRICE, "");
        var priced = found.get();
        var wanted = new Wanted(id, rule.name(), picked.label(), rule.amount(), priced.unit(), picked.line());
        return purchases
                .buy(
                        id,
                        wanted,
                        rule.source(),
                        current.mintedDailyCap(),
                        now,
                        dayStart,
                        DefinedRecord.KIND,
                        money -> new DefinedRecord(
                                id,
                                rule.name(),
                                picked.label(),
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
                                money.paid(),
                                money.source(),
                                money.account(),
                                money.budgetLeft(),
                                money.mintedLeft(),
                                picked.entry()))
                .step();
    }

    private @Nullable Integer entryOf(SystemDecision decision) {
        if (!DefinedRecord.KIND.equals(decision.kind())) return null;
        try {
            return MAPPER.readValue(decision.record(), DefinedRecord.class).entry();
        } catch (RuntimeException e) {
            return null;
        }
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
            public @Nullable String canonical(String material) {
                var item = item(material);
                return item == null ? null : item.name();
            }

            @Override
            public int stackSize(String material) {
                var item = item(material);
                return item == null ? 64 : item.getMaxStackSize();
            }

            @Override
            public CompletableFuture<Placed> place(Wanted wanted) {
                var ordered = wanted.line();
                var line = ordered == null
                        ? new OrderBuilder.DraftLine(
                                new ItemStack(Objects.requireNonNull(item(wanted.material()))),
                                false,
                                wanted.amount(),
                                wanted.unit())
                        : new OrderBuilder.DraftLine(
                                ordered.template(),
                                ordered.exact(),
                                wanted.amount(),
                                wanted.unit(),
                                ordered.criteria());
                var draft = OrderDraftService.toDraft(wanted.name(), List.of(line));
                return systemOrders.place(draft, wanted.rule()).thenApply(outcome -> switch (outcome) {
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
         * @param material a material name as a rule writes it
         * @return the material's name as order lines store it, null when it names no item
         */
        @Nullable
        String canonical(String material);

        /**
         * @param material a material name as a rule writes it
         * @return how many items of it fit into one stack
         */
        int stackSize(String material);

        /**
         * Places an order, already paid.
         *
         * @param wanted the order
         * @return the order, or why it was refused
         */
        CompletableFuture<Placed> place(Wanted wanted);

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
     * An order the server wants placed.
     *
     * @param rule     id of the rule placing it
     * @param name     name of the order
     * @param material material ordered, as a rule writes it; for another line what it orders, as admins see it
     * @param amount   items ordered
     * @param unit     price per item
     * @param line     the line when it is no plain material: an exact item or criteria
     */
    public record Wanted(
            String rule, String name, String material, int amount, double unit, RuleItems.@Nullable Line line) {
        /**
         * An order of a plain material.
         *
         * @param rule     id of the rule placing it
         * @param name     name of the order
         * @param material material ordered, as a rule writes it
         * @param amount   items ordered
         * @param unit     price per item
         */
        public Wanted(String rule, String name, String material, int amount, double unit) {
            this(rule, name, material, amount, unit, null);
        }
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
     * @param removed the orders removed: past their lifetime, repriced, or withdrawn for players
     * @param rules   what each rule did, in file order, then what each guessed placement did
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
         * Its id is missing, too long, reserved for guessed orders, or used by an earlier rule.
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

        String text(String detail) {
            var base = name().toLowerCase(Locale.ROOT).replace('_', ' ');
            return detail.isEmpty() ? base : base + " (" + detail + ")";
        }
    }
}
