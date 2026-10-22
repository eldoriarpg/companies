/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.SuggestionSources;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.data.wrapper.order.MaterialPrice;
import de.eldoria.companies.menus.MenuMode;
import de.eldoria.companies.menus.order.OrderBuilderMenu.Screen;
import de.eldoria.companies.menus.order.OrderBuilderMenu.View;
import de.eldoria.companies.orders.OrderBuilder;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.Replacement;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Runs the order builder: shows the draft of a player and performs what the player clicked or typed on it, through
 * {@link OrderDraftService}, which holds every rule of a draft.
 * <p>
 * After an action the builder is shown again. A refused action shows the error: on top of the dialog that was used,
 * or in chat followed by the builder. An action on a draft that expired closes the menu and says so.
 */
public final class OrderBuilderController {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public OrderBuilderController(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the builder of a draft.
     *
     * @param player owner of the draft
     * @param draft  the draft
     */
    public void show(Player player, OrderBuilder draft) {
        show(player, draft, new Screen.Main(), Optional.empty());
    }

    /**
     * Performs a typed action on the player's draft and shows the builder again, also when the action was refused.
     *
     * @param player owner of the draft
     * @param action the action, refusing with a {@link CommandFailure}
     * @throws CommandFailure when the action was refused, after the builder was shown
     */
    public void edit(Player player, BiConsumer<Player, OrderBuilder> action) {
        var draft = drafts().draft(player.getUniqueId());
        if (draft.isEmpty()) {
            expired(player);
            return;
        }
        try {
            action.accept(player, draft.get());
        } finally {
            show(player, draft.get());
        }
    }

    /**
     * Performs a clicked step on the player's draft and shows the screen it leads to. A refused step shows
     * {@code retry} with the error.
     *
     * @param player owner of the draft
     * @param retry  screen shown again when the step is refused
     * @param step   the step
     */
    void apply(Player player, Screen retry, Step step) {
        var draft = drafts().draft(player.getUniqueId());
        if (draft.isEmpty()) {
            expired(player);
            return;
        }
        Screen next;
        try {
            next = step.run(player, draft.get());
        } catch (CommandFailure failure) {
            refuse(player, draft.get(), retry, failure);
            return;
        }
        if (next instanceof Screen.Done) {
            done(player);
            return;
        }
        show(player, draft.get(), next, Optional.empty());
    }

    /**
     * Fills the market average of the item on the add screen into its unit price, once the market price is known.
     *
     * @param player owner of the draft
     * @param screen the add screen as entered
     */
    void marketPrice(Player player, Screen.Add screen) {
        String material;
        try {
            material = marketMaterial(screen);
        } catch (CommandFailure failure) {
            apply(player, screen, failing(failure));
            return;
        }
        deps.orderData()
                .retrieveMaterialPrice(material)
                .asFuture()
                .thenAcceptAsync(
                        price -> apply(player, screen, (p, draft) -> screen.withUnitPrice(average(price))),
                        Threading.mainThread(deps.plugin()));
    }

    /**
     * Charges the player and creates the order. Only the first of several clicks goes on.
     *
     * @param player owner of the draft
     */
    public void done(Player player) {
        var economy = deps.services().economy();
        drafts().done(player, economy)
                .whenCompleteAsync(
                        (created, err) -> {
                            if (err != null) {
                                failed(player, err);
                                return;
                            }
                            SuggestionSources.current()
                                    .ifPresent(sources -> sources.ownOrders().invalidate(player.getUniqueId()));
                            close(player).whenComplete((unused, error) -> sendCreated(player, created, economy));
                        },
                        Threading.mainThread(deps.plugin()));
    }

    /**
     * Drops the player's draft.
     *
     * @param player owner of the draft
     */
    public void cancel(Player player) {
        if (drafts().draft(player.getUniqueId()).isEmpty()) {
            expired(player);
            return;
        }
        drafts().cancel(player.getUniqueId());
        close(player).thenRun(() -> deps.messages().sendMessage(player, "words.aborted"));
    }

    /**
     * Reports why a draft action failed: the error of a {@link CommandFailure}, otherwise a failed save.
     *
     * @param sender sender of the action
     * @param err    the failure, possibly wrapped by a future
     */
    public void report(CommandSender sender, Throwable err) {
        var cause = unwrap(err);
        if (cause instanceof CommandFailure failure) {
            failure.sendTo(sender, deps.messages());
            return;
        }
        deps.plugin().getLogger().log(Level.SEVERE, "An order draft action failed", cause);
        deps.messages().sendError(sender, "error.saveFailed");
    }

    private void show(Player player, OrderBuilder draft, Screen screen, Optional<CommandFailure> error) {
        var services = deps.services();
        var view = DraftView.of(draft, deps.configuration().orderSetting(), material -> deps.orderData()
                .getMaterialPrice(material));
        var menu = new OrderBuilderMenu(this, services.economy(), deps.messages());
        services.menus().show(player, menu, new View(view, screen, error));
    }

    private void refuse(Player player, OrderBuilder draft, Screen retry, CommandFailure failure) {
        if (dialogs(player)) {
            show(player, draft, retry, Optional.of(failure));
            return;
        }
        failure.sendTo(player, deps.messages());
        show(player, draft);
    }

    private void failed(Player player, Throwable err) {
        var draft = drafts().draft(player.getUniqueId());
        if (draft.isPresent() && dialogs(player) && unwrap(err) instanceof CommandFailure failure) {
            show(player, draft.get(), new Screen.Main(), Optional.of(failure));
            return;
        }
        close(player).thenRun(() -> report(player, err));
    }

    private void expired(Player player) {
        close(player).thenRun(() -> deps.messages().sendError(player, "order.create.draftExpired"));
    }

    private CompletableFuture<Void> close(Player player) {
        return deps.services().menus().close(player);
    }

    private boolean dialogs(Player player) {
        return deps.services().menus().mode(player, true) == MenuMode.DIALOG;
    }

    private void sendCreated(Player player, OrderDraftService.Created created, Economy economy) {
        if (created.fee() > 0) {
            deps.messages()
                    .sendMessage(
                            player,
                            "order.create.createdWithFee",
                            Replacement.create("price", economy.format(created.price())),
                            Replacement.create("fee", economy.format(created.fee())),
                            Replacement.create("total", economy.format(created.price() + created.fee())));
            return;
        }
        deps.messages().sendMessage(player, MessageComposer.escape("order.create.created"));
    }

    private String marketMaterial(Screen.Add screen) {
        return drafts().request(screen.item())
                .filter(requested -> !requested.exact())
                .map(requested -> requested.template().getType().name().toLowerCase(Locale.ROOT))
                .orElseThrow(OrderBuilderController::noMarketPrice);
    }

    private static Step failing(CommandFailure failure) {
        return (player, draft) -> {
            throw failure;
        };
    }

    private static double average(MaterialPrice price) {
        return OrderBuilder.offeredMarketPrice(false, price)
                .orElseThrow(OrderBuilderController::noMarketPrice)
                .avgPrice();
    }

    private static CommandFailure noMarketPrice() {
        return CommandFailure.actionBar("order.draft.error.noMarketPrice");
    }

    private static Throwable unwrap(Throwable err) {
        return err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
    }

    /**
     * @return the drafts of the players
     */
    OrderDraftService drafts() {
        return deps.drafts();
    }

    /**
     * A clicked step on a draft.
     */
    @FunctionalInterface
    interface Step {
        /**
         * @param player owner of the draft
         * @param draft  the draft
         * @return the screen to show next
         * @throws CommandFailure when the step is refused
         */
        Screen run(Player player, OrderBuilder draft);
    }
}
