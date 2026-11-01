/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyNames;
import de.eldoria.companies.commands.suggest.KnownPlayers;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.admin.AdminOrderActions;
import de.eldoria.companies.menus.admin.AdminOrderListMenu;
import de.eldoria.companies.menus.admin.AdminOrderMenu;
import de.eldoria.companies.orders.SystemOwner;
import de.eldoria.companies.services.AdminOrderLookup;
import de.eldoria.companies.services.AdminOrderService;
import de.eldoria.companies.services.AdminOrderService.Result;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.PlayerNames;
import de.eldoria.companies.util.Threading;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /companyadmin order}: inspects and changes any order.
 * <ul>
 *     <li>{@code info <id>} shows both sides, times, money, the deliveries of every worker and the history;</li>
 *     <li>{@code search player <player>} and {@code search company <company>} list the orders of a player or a
 *     company;</li>
 *     <li>{@code unclaim <id>}, {@code cancel <id> refund|keep} and {@code deliver <id>} ask for a confirmation,
 *     given with the button or the typed {@code … confirm};</li>
 *     <li>{@code extend <id> <hours>} gives a claimed order more delivery time or an open order more time before it
 *     is removed.</li>
 * </ul>
 * Every action is written to the server log with the admin's name.
 */
@Subcommand("order")
@Permission(Admin.ORDERS)
@PlayerOnly
public final class AdminOrderCommand implements AdminOrderActions {
    /**
     * Orders on one page of a search.
     */
    static final int PAGE_SIZE = 10;

    private final CommandDependencies deps;
    private final ConfirmFlow<Integer> unclaims;
    private final ConfirmFlow<CancelRequest> cancels;
    private final ConfirmFlow<Integer> deliveries;

    /**
     * @param deps what the commands need
     */
    public AdminOrderCommand(CommandDependencies deps) {
        this.deps = deps;
        unclaims = ConfirmFlow.create(deps.plugin(), this::unclaimConfirmed);
        cancels = ConfirmFlow.create(deps.plugin(), this::cancelConfirmed);
        deliveries = ConfirmFlow.create(deps.plugin(), this::deliverConfirmed);
    }

    /**
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes("info")
    public void info(CommandSender sender, @IntArg(min = 0) int id) {
        info(Senders.player(sender), id);
    }

    /**
     * Lists the orders of a player.
     *
     * @param sender sender of the command
     * @param player name of the owner
     */
    @Executes("search player")
    public void searchPlayer(CommandSender sender, @KnownPlayers String player) {
        var admin = Senders.player(sender);
        deps.requireReady();
        var target = deps.plugin().getServer().getOfflinePlayerIfCached(player);
        if (target == null) throw CommandFailure.of("error.unknownPlayer");
        var search = new Search(String.valueOf(target.getName()), target.getUniqueId(), null);
        deps.adminLog()
                .entry(admin, "order search")
                .with("player", search.label())
                .write();
        page(admin, search, 1);
    }

    /**
     * Lists the orders of a company.
     *
     * @param sender  sender of the command
     * @param company name of the company
     */
    @Executes("search company")
    public void searchCompany(CommandSender sender, @CompanyNames @StringArg(StringArgType.GREEDY) String company) {
        var admin = Senders.player(sender);
        deps.requireReady();
        deps.companyData()
                .retrieveCompanyByName(company)
                .asFuture()
                .thenAccept(found -> {
                    if (found.isEmpty()) {
                        deps.messages().sendErrorActionBar(admin, "error.unknownCompany");
                        return;
                    }
                    var search =
                            new Search(found.get().name(), null, found.get().id());
                    deps.adminLog()
                            .entry(admin, "order search")
                            .with("company", search.label())
                            .with("company_id", search.company())
                            .write();
                    page(admin, search, 1);
                })
                .exceptionally(this::logged);
    }

    /**
     * Asks to take a claimed order away from its company.
     *
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes("unclaim")
    public void unclaim(CommandSender sender, @IntArg(min = 0) int id) {
        unclaim(Senders.player(sender), id);
    }

    /**
     * Takes the order away the sender was asked about.
     *
     * @param sender sender of the command
     */
    @Executes("unclaim confirm")
    public void unclaimConfirm(CommandSender sender) {
        var admin = Senders.player(sender);
        deps.requireReady();
        unclaims.confirm(admin);
    }

    /**
     * Asks to delete an order.
     *
     * @param sender sender of the command
     * @param id     id of the order
     * @param mode   {@code refund} to return price and fee to the owner, {@code keep} to return nothing
     */
    @Executes("cancel")
    public void cancel(CommandSender sender, @IntArg(min = 0) int id, @Literal({"refund", "keep"}) String mode) {
        cancel(Senders.player(sender), id, "refund".equals(mode));
    }

    /**
     * Deletes the order the sender was asked about.
     *
     * @param sender sender of the command
     */
    @Executes("cancel confirm")
    public void cancelConfirm(CommandSender sender) {
        var admin = Senders.player(sender);
        deps.requireReady();
        cancels.confirm(admin);
    }

    /**
     * Asks to mark a claimed order delivered.
     *
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes("deliver")
    public void deliver(CommandSender sender, @IntArg(min = 0) int id) {
        deliver(Senders.player(sender), id);
    }

    /**
     * Marks the order delivered the sender was asked about.
     *
     * @param sender sender of the command
     */
    @Executes("deliver confirm")
    public void deliverConfirm(CommandSender sender) {
        var admin = Senders.player(sender);
        deps.requireReady();
        deliveries.confirm(admin);
    }

    /**
     * Gives an order more time.
     *
     * @param sender sender of the command
     * @param id     id of the order
     * @param hours  hours to add
     */
    @Executes("extend")
    public void extend(CommandSender sender, @IntArg(min = 0) int id, @IntArg(min = 1, max = 8760) int hours) {
        var admin = Senders.player(sender);
        deps.requireReady();
        extend(admin, id, hours);
    }

    @Override
    public void info(Player viewer, int order) {
        if (!mayManage(viewer)) return;
        deps.adminLog().entry(viewer, "order info").with("id", order).write();
        showInfo(viewer, order, Optional.empty());
    }

    @Override
    public void unclaim(Player viewer, int order) {
        if (!mayManage(viewer)) return;
        ask(
                viewer,
                order,
                OrderState.CLAIMED,
                full -> unclaims.ask(
                        deps.services().menus(),
                        viewer,
                        order,
                        MenuText.create()
                                .locale("companyadmin.order.unclaimConfirm", Values.text("order_name", full.name()))));
    }

    @Override
    public void cancel(Player viewer, int order, boolean refund) {
        if (!mayManage(viewer)) return;
        var economy = deps.services().economy();
        ask(viewer, order, null, full -> {
            var money = AdminOrderService.cancelMoney(full, refund);
            var question = SystemOwner.is(full.owner())
                    ? MenuText.create()
                            .locale(
                                    "companyadmin.order.cancelSystemConfirm",
                                    Values.text("order_name", full.name()),
                                    Values.money("amount", economy, money.refund()))
                    : !full.charged()
                            ? MenuText.create()
                                    .locale(
                                            "companyadmin.order.cancelUnchargedConfirm",
                                            Values.text("order_name", full.name()))
                            : refund
                                    ? MenuText.create()
                                            .locale(
                                                    "companyadmin.order.cancelRefundConfirm",
                                                    Values.text("order_name", full.name()),
                                                    Values.money("amount", economy, money.refund()))
                                    : MenuText.create()
                                            .locale(
                                                    "companyadmin.order.cancelKeepConfirm",
                                                    Values.text("order_name", full.name()),
                                                    Values.money("amount", economy, money.collected()));
            cancels.ask(deps.services().menus(), viewer, new CancelRequest(order, refund), question);
        });
    }

    @Override
    public void deliver(Player viewer, int order) {
        if (!mayManage(viewer)) return;
        var economy = deps.services().economy();
        ask(
                viewer,
                order,
                OrderState.CLAIMED,
                full -> deliveries.ask(
                        deps.services().menus(),
                        viewer,
                        order,
                        MenuText.create()
                                .locale(
                                        SystemOwner.is(full.owner())
                                                ? "companyadmin.order.deliverSystemConfirm"
                                                : "companyadmin.order.deliverConfirm",
                                        Values.text("order_name", full.name()),
                                        Values.money("amount", economy, AdminOrderService.undeliveredRefund(full)))));
    }

    @Override
    public void extend(Player viewer, int order, @Nullable String entered) {
        if (!mayManage(viewer)) return;
        var hours = AdminOrderMenu.hours(entered);
        if (hours.isEmpty()) {
            deps.messages().sendErrorActionBar(viewer, "companyadmin.order.error.invalidHours");
            return;
        }
        extend(viewer, order, hours.getAsInt());
    }

    private void extend(Player viewer, int order, int hours) {
        perform(viewer, () -> deps.adminOrders().extend(order, hours), extended -> {
            var simple = extended.order();
            log(viewer, "order extend", simple).with("hours", hours).write();
            showInfo(
                    viewer,
                    order,
                    Optional.of(notice(
                            "companyadmin.order.extended",
                            Values.text("order_name", simple.name()),
                            Values.number("hours", hours))));
        });
    }

    private void unclaimConfirmed(Player viewer, int order) {
        perform(viewer, () -> deps.adminOrders().unclaim(order), unclaimed -> {
            var full = unclaimed.order();
            log(viewer, "order unclaim", full)
                    .with("workers", unclaimed.workers().size())
                    .write();
            showInfo(
                    viewer,
                    order,
                    Optional.of(notice(
                            "companyadmin.order.unclaimed",
                            Values.text("order_name", full.name()),
                            Values.number("count", unclaimed.workers().size()))));
        });
    }

    private void cancelConfirmed(Player viewer, CancelRequest request) {
        var economy = deps.services().economy();
        perform(viewer, () -> deps.adminOrders().cancel(request.order(), request.refund()), cancelled -> {
            var full = cancelled.order();
            var money = cancelled.money();
            log(viewer, "order cancel", full)
                    .with("refund", request.refund())
                    .with("refunded", money.refund())
                    .with("collected", money.collected())
                    .with("workers", cancelled.workers().size())
                    .write();
            var system = SystemOwner.is(full.owner());
            var key = system
                    ? "companyadmin.order.cancelledSystem"
                    : !full.charged()
                            ? "companyadmin.order.cancelledUncharged"
                            : request.refund()
                                    ? "companyadmin.order.cancelledRefund"
                                    : "companyadmin.order.cancelledKept";
            var amount = system || request.refund() ? money.refund() : money.collected();
            var text = notice(key, Values.text("order_name", full.name()), Values.money("amount", economy, amount));
            deps.services().menus().close(viewer).thenRun(() -> deps.messages().sendMessage(viewer, text));
        });
    }

    private void deliverConfirmed(Player viewer, int order) {
        var economy = deps.services().economy();
        perform(viewer, () -> deps.adminOrders().deliver(order), delivered -> {
            var full = delivered.completion().order();
            log(viewer, "order deliver", full)
                    .with("delivered", full.delivered())
                    .with("amount", full.amount())
                    .with("paid_workers", delivered.completion().payouts().size())
                    .with("refunded", delivered.refund())
                    .write();
            showInfo(
                    viewer,
                    order,
                    Optional.of(notice(
                            SystemOwner.is(full.owner())
                                    ? "companyadmin.order.deliveredSystem"
                                    : "companyadmin.order.delivered",
                            Values.text("order_name", full.name()),
                            Values.money("amount", economy, delivered.refund()))));
        });
    }

    private <T> void perform(Player viewer, Supplier<CompletableFuture<Result<T>>> action, Consumer<T> done) {
        CompletableFuture<Result<T>> running;
        try {
            running = action.get();
        } catch (CommandFailure failure) {
            failure.sendTo(viewer, deps.messages());
            return;
        }
        running.thenAccept(result -> {
                    switch (result) {
                        case Result.Done<T> performed -> done.accept(performed.value());
                        case Result.Refused<T> refused ->
                            deps.messages()
                                    .sendErrorActionBar(
                                            viewer, refused.refusal().key());
                    }
                })
                .exceptionally(this::logged);
    }

    /**
     * Loads an order fresh and asks the admin about it when it is in the expected state.
     *
     * @param viewer   viewing admin
     * @param id       id of the order
     * @param expected state the order has to be in, or null for an open or claimed order
     * @param question asks the admin about the loaded order
     */
    private void ask(Player viewer, int id, @Nullable OrderState expected, Consumer<FullOrder> question) {
        var orderData = deps.orderData();
        orderData
                .retrieveOrderById(id)
                .asFuture()
                .thenApplyAsync(found -> found.map(orderData::toFullOrder), Threading.VIRTUAL)
                .thenAccept(found -> {
                    if (found.isEmpty()) {
                        deps.messages().sendErrorActionBar(viewer, "error.unknownOrder");
                        return;
                    }
                    var full = found.get();
                    if (expected != null && full.state() != expected) {
                        deps.messages().sendErrorActionBar(viewer, "companyadmin.order.error.notClaimed");
                        return;
                    }
                    if (expected == null && !isOpen(full)) {
                        deps.messages().sendErrorActionBar(viewer, "companyadmin.order.error.notOpen");
                        return;
                    }
                    question.accept(full);
                })
                .exceptionally(this::logged);
    }

    /**
     * Shows the admin view of an order, loaded past the order cache.
     *
     * @param viewer viewing admin
     * @param id     id of the order
     * @param notice line shown on top
     */
    void showInfo(Player viewer, int id, Optional<MenuText> notice) {
        var services = deps.services();
        var menu = new AdminOrderMenu(deps.messages(), services.economy(), this);
        CompletableFuture.supplyAsync(() -> model(id), Threading.VIRTUAL)
                .thenAccept(model -> {
                    if (model.isEmpty()) {
                        deps.messages().sendErrorActionBar(viewer, "error.unknownOrder");
                        return;
                    }
                    var shown = notice.map(model.get()::withNotice).orElse(model.get());
                    services.menus().show(viewer, menu, shown);
                })
                .exceptionally(this::logged);
    }

    private Optional<AdminOrderMenu.Model> model(int id) {
        return lookup().details(id)
                .map(details -> new AdminOrderMenu.Model(
                        details.order(),
                        details.ownerName(),
                        details.companyName(),
                        details.deadline()
                                .map(deadline -> new AdminOrderMenu.Deadline(deadline.at(), deadline.removal())),
                        details.names(),
                        details.history(),
                        Optional.empty()));
    }

    private AdminOrderLookup lookup() {
        return new AdminOrderLookup(
                deps.orderData(),
                deps.companyData(),
                deps.information().history(),
                deps.configuration(),
                this::playerName);
    }

    private void page(Player viewer, Search search, int page) {
        var number = Math.max(1, page);
        var services = deps.services();
        var menu = new AdminOrderListMenu(deps.messages(), this, (clicker, target) -> page(clicker, search, target));
        CompletableFuture.supplyAsync(
                        () -> lookup().page(search.owner(), search.company(), number, PAGE_SIZE).stream()
                                .map(row ->
                                        new AdminOrderListMenu.Row(row.order(), row.ownerName(), row.claimantName()))
                                .toList(),
                        Threading.VIRTUAL)
                .thenAccept(rows -> services.menus()
                        .show(viewer, menu, AdminOrderListMenu.Model.of(search.label(), number, rows, PAGE_SIZE)))
                .exceptionally(this::logged);
    }

    private String playerName(UUID player) {
        return SystemOwner.name(player, PlayerNames::of);
    }

    private AdminLog.Entry log(Player admin, String action, SimpleOrder order) {
        return deps.adminLog()
                .entry(admin, action)
                .with("id", order.id())
                .with("order", order.name())
                .with("owner", playerName(order.owner()))
                .with("company", order.state() == OrderState.UNCLAIMED || order.isSolo() ? null : order.company())
                .with("solo_worker", order.soloWorker().map(this::playerName).orElse(null));
    }

    private boolean mayManage(Player viewer) {
        if (viewer.hasPermission(Admin.ORDERS)) return true;
        deps.messages().sendErrorActionBar(viewer, "companyadmin.error.permission");
        return false;
    }

    private static boolean isOpen(SimpleOrder order) {
        return order.state() == OrderState.UNCLAIMED || order.state() == OrderState.CLAIMED;
    }

    private static MenuText notice(String key, TagResolver.Single... values) {
        return MenuText.create().text("<active>").locale(key, values);
    }

    private <T> T logged(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "An admin order action failed", err);
        return null;
    }

    /**
     * What an admin searched orders for: one owner or one company.
     *
     * @param label   name of the owner or the company
     * @param owner   owner to filter by, or null
     * @param company company to filter by, or null
     */
    record Search(
            String label, @Nullable UUID owner, @Nullable Integer company) {}

    /**
     * A deletion waiting for its confirmation.
     *
     * @param order  id of the order
     * @param refund whether price and fee are refunded
     */
    record CancelRequest(int order, boolean refund) {}
}
