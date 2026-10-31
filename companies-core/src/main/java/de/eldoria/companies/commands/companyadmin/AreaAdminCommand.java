/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.areas.AreaRef;
import de.eldoria.companies.areas.AreaServices;
import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.data.wrapper.area.AreaOffer;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /companyadmin area}: the plots and regions the server offers companies.
 * <ul>
 *     <li>{@code offer buy <price>} and {@code offer rent <price> <days>} offer the area the admin stands on; its
 *     plugin must be able to move the area to a company. Whoever owns the area now keeps it for the server and gets it
 *     back when a rent ends or the company sells it back or disbands.</li>
 *     <li>{@code offers} lists every offer with the company that has it;</li>
 *     <li>{@code withdraw <id>} withdraws an offer no company has.</li>
 * </ul>
 */
@Subcommand("area")
@Permission(Admin.AREAS)
@PlayerOnly
public final class AreaAdminCommand {
    private static final String PREFIX = "admin.area.";

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public AreaAdminCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Offers the area the admin stands on for sale.
     *
     * @param sender sender of the command
     * @param price  the price
     */
    @Executes("offer buy")
    public void offerBuy(CommandSender sender, @DoubleArg(min = 0, max = 1.0E12) double price) {
        offer(Senders.player(sender), AreaOffer.Kind.BUY, price, 0);
    }

    /**
     * Offers the area the admin stands on for rent.
     *
     * @param sender sender of the command
     * @param price  the rent per period
     * @param days   the days one period lasts
     */
    @Executes("offer rent")
    public void offerRent(
            CommandSender sender,
            @DoubleArg(min = 0, max = 1.0E12) double price,
            @IntArg(min = 1, max = 3650) int days) {
        offer(Senders.player(sender), AreaOffer.Kind.RENT, price, days);
    }

    private void offer(Player admin, AreaOffer.Kind kind, double price, int days) {
        var providers = areas().providers();
        var here = providers.areasAt(admin.getLocation());
        if (here.isEmpty()) {
            deps.messages().sendError(admin, PREFIX + "error.noArea");
            return;
        }
        var movable = here.stream()
                .filter(area -> providers.call(
                        area.provider(), provider -> provider.capabilities().contains(AreaCapability.TRANSFER), false))
                .findFirst();
        if (movable.isEmpty()) {
            deps.messages().sendError(admin, PREFIX + "error.notMovable");
            return;
        }
        var ref = movable.get();
        var keeper = providers.call(ref.provider(), provider -> keeper(provider.owners(ref.ref())), null);
        if (keeper == null && !providers.call(ref.provider(), AreaProvider::releasesToNobody, false)) {
            deps.messages().sendError(admin, PREFIX + "error.noKeeper");
            return;
        }
        var name = providers.call(ref.provider(), provider -> provider.name(ref.ref()), ref.ref());
        var world = admin.getWorld().getName();
        var amount = Money.floorDecimal(price).doubleValue();
        run(admin, () -> stored(ref, world, kind, amount, days, keeper), id -> {
            deps.plugin().getLogger().info("%s offered %s %s for %s".formatted(admin.getName(), ref, kind, amount));
            deps.messages()
                    .sendMessage(
                            admin,
                            PREFIX + "offered." + kind.name().toLowerCase(Locale.ROOT),
                            Values.number("id", id),
                            Values.text("area", name),
                            Values.text("amount", money(amount)),
                            Values.number("days", days));
        });
    }

    private static @Nullable UUID keeper(Set<UUID> owners) {
        return owners.stream().min(Comparator.naturalOrder()).orElse(null);
    }

    private int stored(AreaRef ref, String world, AreaOffer.Kind kind, double price, int days, @Nullable UUID keeper) {
        var store = areas().areas();
        if (store.byRef(ref.provider(), ref.ref()).isPresent()) throw CommandFailure.of(PREFIX + "error.inUse");
        var offer = new AreaOffer(
                0, ref.provider(), ref.ref(), world, areas().node(), kind, price, days, keeper, Times.now());
        return store.addOffer(offer).orElseThrow(() -> CommandFailure.of(PREFIX + "error.offered"));
    }

    /**
     * Lists every offer.
     *
     * @param sender sender of the command
     */
    @Executes("offers")
    public void offers(CommandSender sender) {
        var admin = Senders.player(sender);
        run(
                admin,
                () -> {
                    var store = areas().areas();
                    var open = store.openOffers().stream().map(AreaOffer::id).toList();
                    return store.offers().stream()
                            .map(offer -> new Listed(offer, !open.contains(offer.id())))
                            .toList();
                },
                found -> {
                    if (found.isEmpty()) {
                        deps.messages().sendMessage(admin, PREFIX + "none");
                        return;
                    }
                    deps.messages().sendMessage(admin, PREFIX + "title");
                    for (var listed : found) {
                        var offer = listed.offer();
                        deps.messages()
                                .sendMessage(
                                        admin,
                                        PREFIX + "entry." + offer.kind().name().toLowerCase(Locale.ROOT),
                                        Values.number("id", offer.id()),
                                        Values.text("area", name(offer.provider(), offer.ref())),
                                        Values.text("world", offer.world()),
                                        Values.text("amount", money(offer.price())),
                                        Values.number("days", offer.periodDays()),
                                        Values.component(
                                                "state",
                                                new MenuComponents(deps.messages(), admin)
                                                        .locale(PREFIX + (listed.taken() ? "taken" : "open"))));
                    }
                });
    }

    /**
     * An offer as listed, with whether a company has it.
     *
     * @param offer the offer
     * @param taken whether a company has the area
     */
    private record Listed(AreaOffer offer, boolean taken) {}

    /**
     * Withdraws an offer no company has.
     *
     * @param sender sender of the command
     * @param id     id of the offer
     */
    @Executes("withdraw")
    public void withdraw(CommandSender sender, @IntArg(min = 1) int id) {
        var admin = Senders.player(sender);
        run(
                admin,
                () -> {
                    var store = areas().areas();
                    if (store.removeOffer(id)) return id;
                    if (store.offer(id).isPresent()) throw CommandFailure.of(PREFIX + "error.taken");
                    throw CommandFailure.of(PREFIX + "error.unknown");
                },
                removed -> {
                    deps.plugin().getLogger().info("%s withdrew area offer %d".formatted(admin.getName(), removed));
                    deps.messages().sendMessage(admin, PREFIX + "withdrawn", Values.number("id", removed));
                });
    }

    private String name(String provider, String ref) {
        return areas().providers().call(provider, found -> found.name(ref), ref);
    }

    private String money(double amount) {
        return deps.services().treasury().format(BigDecimal.valueOf(amount));
    }

    private AreaServices areas() {
        return deps.services().areas();
    }

    private <T> void run(Player player, Supplier<T> query, Consumer<T> answer) {
        deps.requireReady();
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAcceptAsync(answer, Threading.mainThread(deps.plugin()))
                .exceptionally(err -> {
                    if (err.getCause() instanceof CommandFailure failure) {
                        failure.sendTo(player, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change an area offer", err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.failed");
                    return null;
                });
    }
}
