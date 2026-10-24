/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.OwnSeriesIds;
import de.eldoria.companies.commands.suggest.SeriesSources;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.series.OrderSeries;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.order.SeriesActions;
import de.eldoria.companies.menus.order.SeriesInfoMenu;
import de.eldoria.companies.menus.order.SeriesListMenu;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /order series …}: the sender's repeating orders. Without a subcommand it lists them; {@code info} shows one,
 * {@code stop} stops one after a confirmation and keeps its current copy, {@code resume} lets a stopped one post again.
 */
@Subcommand("series")
@PlayerOnly
public final class SeriesCommand implements SeriesActions {
    private final CommandDependencies deps;
    private final OwnerOrderViews views;
    private final ConfirmFlow<Integer> stops;

    /**
     * @param deps  what the commands need
     * @param views shows the current copy of a series
     */
    public SeriesCommand(CommandDependencies deps, OwnerOrderViews views) {
        this.deps = deps;
        this.views = views;
        this.stops = ConfirmFlow.create(deps.plugin(), this::stopConfirmed);
    }

    /**
     * Lists the sender's repeating orders.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        list(player);
    }

    /**
     * Shows one repeating order of the sender.
     *
     * @param sender sender of the command
     * @param id     id of the series
     */
    @Executes("info")
    public void info(CommandSender sender, @OwnSeriesIds int id) {
        var player = Senders.player(sender);
        deps.requireReady();
        info(player, id);
    }

    /**
     * Asks the sender to confirm stopping a repeating order.
     *
     * @param sender sender of the command
     * @param id     id of the series
     */
    @Executes("stop")
    public void stop(CommandSender sender, @OwnSeriesIds int id) {
        var player = Senders.player(sender);
        deps.requireReady();
        stop(player, id);
    }

    /**
     * Stops the repeating order the sender confirmed.
     *
     * @param sender sender of the command
     */
    @Executes("stop confirm")
    public void confirmStop(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        stops.confirm(player);
    }

    /**
     * Resumes a stopped repeating order of the sender.
     *
     * @param sender sender of the command
     * @param id     id of the series
     */
    @Executes("resume")
    public void resume(CommandSender sender, @OwnSeriesIds int id) {
        var player = Senders.player(sender);
        deps.requireReady();
        resume(player, id);
    }

    @Override
    public void list(Player player) {
        deps.economy()
                .series()
                .retrieveByOwner(player.getUniqueId(), false)
                .thenAccept(series -> show(player, series))
                .exceptionally(err -> failed(player, err));
    }

    @Override
    public void info(Player player, int id) {
        load(player, id, series -> {
            var services = deps.services();
            var fee = deps.configuration().orderSetting().fee();
            var model = SeriesInfoMenu.Model.of(series, fee::of, LocalDateTime.now());
            services.menus().show(player, new SeriesInfoMenu(this, deps.messages(), services.economy()), model);
        });
    }

    @Override
    public void stop(Player player, int id) {
        load(player, id, series -> {
            var question =
                    MenuText.create().locale("order.series.confirmStop", Values.text("series_name", series.name()));
            stops.ask(deps.services().menus(), player, id, question);
        });
    }

    @Override
    public void resume(Player player, int id) {
        deps.services()
                .series()
                .resume(player, id)
                .thenAccept(series -> changed(player, series, "order.series.resumed"))
                .exceptionally(err -> failed(player, err));
    }

    @Override
    public void order(Player player, int id) {
        views.showOrder(player, id);
    }

    private void stopConfirmed(Player player, int id) {
        deps.services()
                .series()
                .stop(player.getUniqueId(), id)
                .thenAccept(series -> changed(player, series, "order.series.stopped"))
                .exceptionally(err -> failed(player, err));
    }

    private void changed(Player player, OrderSeries series, String localeKey) {
        SeriesSources.invalidate(player.getUniqueId());
        deps.messages()
                .sendMessage(player, MenuText.create().locale(localeKey, Values.text("series_name", series.name())));
        info(player, series.id());
    }

    private void show(Player player, List<OrderSeries> series) {
        var menu = new SeriesListMenu(this, deps.messages());
        deps.services().menus().show(player, menu, SeriesListMenu.Model.of(series, LocalDateTime.now()));
    }

    private void load(Player player, int id, Consumer<OrderSeries> action) {
        deps.economy()
                .series()
                .retrieveSeries(id)
                .thenApply(found -> found.filter(series -> series.owner().equals(player.getUniqueId()))
                        .orElseThrow(() -> CommandFailure.of("order.series.error.unknown")))
                .thenAcceptAsync(action, Threading.mainThread(deps.plugin()))
                .exceptionally(err -> failed(player, err));
    }

    private Void failed(Player player, Throwable err) {
        var cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
        if (cause instanceof CommandFailure failure) {
            failure.sendTo(player, deps.messages());
            return null;
        }
        deps.plugin().getLogger().log(Level.SEVERE, "A repeating order action failed", cause);
        deps.messages().sendError(player, "error.saveFailed");
        return null;
    }
}
