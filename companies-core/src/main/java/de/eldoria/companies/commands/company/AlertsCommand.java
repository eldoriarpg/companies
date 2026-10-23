/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.order.search.OrderSearch;
import de.eldoria.companies.commands.suggest.AlertSuggestions;
import de.eldoria.companies.commands.suggest.CompanyAlertNames;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.alert.OrderAlert;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.company.AlertsMenu;
import de.eldoria.companies.services.alerts.AlertCompanies;
import de.eldoria.companies.services.alerts.AlertService;
import de.eldoria.companies.services.alerts.AlertService.Done;
import de.eldoria.companies.services.alerts.AlertService.Outcome;
import de.eldoria.companies.services.alerts.AlertService.Refused;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /company alerts}: the order alerts of the sender's company, saved searches that notify the members who
 * manage orders about new matching orders.
 * <p>
 * Every member may list the alerts and show the orders they find; {@code add}, {@code remove}, {@code on} and
 * {@code off} need {@link de.eldoria.companies.components.company.CompanyPermission#MANAGE_ORDERS}, which
 * {@link AlertService} checks.
 */
@Subcommand("alerts")
@PlayerOnly
public final class AlertsCommand {
    private final CommandDependencies deps;
    private final OrderSearch search;

    /**
     * @param deps   what the commands need
     * @param search the order search, which shows the orders of an alert
     */
    public AlertsCommand(CommandDependencies deps, OrderSearch search) {
        this.deps = deps;
        this.search = search;
    }

    /**
     * Lists the alerts of the sender's company.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("list")
    public void list(CommandSender sender) {
        show(Senders.player(sender), Optional.empty());
    }

    /**
     * Saves the sender's current order search as an alert.
     *
     * @param sender sender of the command
     * @param name   name of the alert
     */
    @Executes("add")
    public void add(CommandSender sender, String name) {
        var player = Senders.player(sender);
        var filter = search.query(player).filter();
        reply(player, service().add(player, name, filter), added -> {
            AlertSuggestions.invalidate(player.getUniqueId());
            send(player, notice("company.alerts.added", added));
        });
    }

    /**
     * Removes an alert.
     *
     * @param sender sender of the command
     * @param name   name of the alert
     */
    @Executes("remove")
    public void remove(CommandSender sender, @CompanyAlertNames String name) {
        var player = Senders.player(sender);
        reply(player, removal(player, name), removed -> send(player, notice("company.alerts.removed", removed)));
    }

    /**
     * Turns an alert on.
     *
     * @param sender sender of the command
     * @param name   name of the alert
     */
    @Executes("on")
    public void on(CommandSender sender, @CompanyAlertNames String name) {
        var player = Senders.player(sender);
        reply(player, service().enable(player, name, true), on -> send(player, switched(on, true)));
    }

    /**
     * Turns an alert off.
     *
     * @param sender sender of the command
     * @param name   name of the alert
     */
    @Executes("off")
    public void off(CommandSender sender, @CompanyAlertNames String name) {
        var player = Senders.player(sender);
        reply(player, service().enable(player, name, false), off -> send(player, switched(off, false)));
    }

    /**
     * Shows the orders an alert finds now, as a search.
     *
     * @param sender sender of the command
     * @param name   name of the alert
     */
    @Executes("show")
    public void show(CommandSender sender, @CompanyAlertNames String name) {
        var player = Senders.player(sender);
        reply(player, service().find(player, name), alert -> showOrders(player, alert));
    }

    /**
     * Shows the alerts of a player's company.
     *
     * @param player viewing member
     * @param notice line shown on top
     */
    public void show(Player player, Optional<MenuText> notice) {
        var services = deps.services();
        var menu = new AlertsMenu(deps.messages(), services.economy(), new Buttons());
        reply(player, service().overview(player), overview -> services.menus()
                .show(
                        player,
                        menu,
                        new AlertsMenu.Model(
                                overview.alerts(),
                                overview.limit(),
                                overview.mayManage(),
                                LocalDateTime.now(),
                                notice)));
    }

    private void showOrders(Player player, OrderAlert alert) {
        Threading.mainThread(deps.plugin()).execute(() -> search.showFilter(player, alert.filter()));
    }

    private CompletableFuture<Outcome<String>> removal(Player player, String name) {
        return service().remove(player, name).thenApply(outcome -> {
            if (outcome instanceof Done<String>) AlertSuggestions.invalidate(player.getUniqueId());
            return outcome;
        });
    }

    private AlertService service() {
        deps.requireReady();
        return new AlertService(
                deps.economy().alerts(),
                AlertCompanies.of(deps.companyData(), deps.orderData()),
                deps.configuration()::companySettings,
                Clock.systemDefaultZone());
    }

    private <T> void reply(Player player, CompletableFuture<Outcome<T>> outcome, Consumer<T> done) {
        outcome.thenAccept(result -> {
                    switch (result) {
                        case Done<T> success -> done.accept(success.value());
                        case Refused<T> refused -> refused.failure().sendTo(player, deps.messages());
                    }
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void send(Player player, MenuText text) {
        deps.messages().sendMessage(player, text);
    }

    private static MenuText notice(String key, String name) {
        return MenuText.create().text("<neutral>").locale(key, Values.text("alert_name", name));
    }

    private static MenuText switched(String name, boolean enabled) {
        return notice(enabled ? "company.alerts.enabled" : "company.alerts.disabled", name);
    }

    /**
     * The buttons of the alerts menu: a change shows the menu again with its result on top.
     */
    private final class Buttons implements AlertsMenu.Handlers {
        @Override
        public void show(Player player, OrderAlert alert) {
            reply(player, service().find(player, alert.name()), found -> showOrders(player, found));
        }

        @Override
        public void toggle(Player player, OrderAlert alert) {
            var enable = !alert.enabled();
            reply(
                    player,
                    service().enable(player, alert.name(), enable),
                    name -> AlertsCommand.this.show(player, Optional.of(switched(name, enable))));
        }

        @Override
        public void remove(Player player, OrderAlert alert) {
            reply(
                    player,
                    removal(player, alert.name()),
                    name -> AlertsCommand.this.show(player, Optional.of(notice("company.alerts.removed", name))));
        }

        @Override
        public void search(Player player) {
            search.showForm(player);
        }
    }
}
