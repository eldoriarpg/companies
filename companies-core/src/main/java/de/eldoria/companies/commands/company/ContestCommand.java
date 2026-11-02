/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.offer.ContestDelivery;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.company.ContestMenu;
import de.eldoria.companies.menus.company.ContestsMenu;
import de.eldoria.companies.orders.DeliveryPart;
import de.eldoria.companies.orders.ItemSources;
import de.eldoria.companies.orders.offers.ContestService;
import de.eldoria.companies.util.Inventories;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /company contest}: the contests of event offers, which companies join and deliver into, each delivery paid
 * at once per item.
 * <ul>
 *     <li>{@code /company contest} lists the contests that have not closed;</li>
 *     <li>{@code info <id>} shows what a contest still misses, its time left, its prizes, the company's part and the
 *     ranking;</li>
 *     <li>{@code join <id>} and {@code leave <id>} take part or stop, with {@code manage_orders}; taking part uses one
 *     of the company's order slots;</li>
 *     <li>{@code deliver <id> <amount>} and {@code deliver <id> all} deliver from the inventory and from a shulker box
 *     in the main hand, like deliveries to orders.</li>
 * </ul>
 * The list and the info are menus, {@link ContestsMenu} and {@link ContestMenu}, dialogs where the client shows them;
 * their buttons join, leave and deliver everything and show the contest again.
 */
@Subcommand("contest")
@PlayerOnly
public final class ContestCommand {
    private static final String PREFIX = "company.contest.";
    private static final int RANKED = 10;

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public ContestCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Lists the contests that have not closed.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        list(Senders.player(sender));
    }

    /**
     * Shows a contest.
     *
     * @param sender sender of the command
     * @param id     id of the contest
     */
    @Executes("info")
    public void info(CommandSender sender, @IntArg(min = 0) int id) {
        info(Senders.player(sender), id);
    }

    /**
     * Shows the contests that have not closed to a player.
     *
     * @param player the player
     */
    public void list(Player player) {
        var offers = offers();
        run(
                player,
                () -> {
                    var company = company(player);
                    return new ContestsMenu.Model(
                            offers.contests(),
                            company.map(found -> Set.copyOf(offers.joined(found.id())))
                                    .orElse(Set.of()),
                            mayManage(company, player),
                            Times.now());
                },
                model -> {
                    if (model.contests().isEmpty()) {
                        deps.messages().sendMessage(player, PREFIX + "none");
                        return;
                    }
                    deps.services().menus().show(player, contestsMenu(), model);
                });
    }

    /**
     * Shows a contest to a player.
     *
     * @param player the player
     * @param id     id of the contest
     */
    public void info(Player player, int id) {
        info(player, id, Optional.empty());
    }

    private void info(Player player, int id, Optional<MenuText> notice) {
        var offers = offers();
        run(
                player,
                () -> contest(id).map(contest -> {
                    var company = company(player);
                    var ranking = offers.ranking(id);
                    return new ContestMenu.Model(
                            contest,
                            ranking.stream()
                                    .filter(entry -> entry.delivered() > 0 && entry.name() != null)
                                    .limit(RANKED)
                                    .toList(),
                            company.flatMap(found -> offers.entry(id, found.id())),
                            company.map(found -> EventsCommand.place(ranking, found.id()))
                                    .orElse(OptionalInt.empty()),
                            company.isPresent(),
                            mayManage(company, player),
                            Times.now(),
                            notice);
                }),
                found -> {
                    if (found.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, PREFIX + "error.unknown");
                        return;
                    }
                    deps.services().menus().show(player, contestMenu(), found.get());
                });
    }

    private ContestsMenu contestsMenu() {
        return new ContestsMenu(deps.messages(), economy()::format, new Buttons());
    }

    private ContestMenu contestMenu() {
        return new ContestMenu(deps.messages(), economy()::format, new Buttons());
    }

    /**
     * Lets the sender's company take part in a contest.
     *
     * @param sender sender of the command
     * @param id     id of the contest
     */
    @Executes("join")
    public void join(CommandSender sender, @IntArg(min = 0) int id) {
        join(Senders.player(sender), id, false);
    }

    private void join(Player player, int id, boolean show) {
        var answer = new Answer(player, id, show);
        run(
                answer,
                () -> company(player)
                        .map(company -> deps.services().contests().join(company, player.getUniqueId(), id)),
                result -> {
                    if (result.isPresent() && result.get() == ContestService.Joining.NOT_STARTED) {
                        var starts = contest(id).map(EventOffer::starts).orElse(Times.now());
                        answer.error(
                                PREFIX + "error.notStarted",
                                Values.number("id", id),
                                Values.text("time", pretty(Duration.between(Times.now(), starts))));
                        return;
                    }
                    joining(answer, id, result);
                });
    }

    /**
     * Lets the sender's company leave a contest.
     *
     * @param sender sender of the command
     * @param id     id of the contest
     */
    @Executes("leave")
    public void leave(CommandSender sender, @IntArg(min = 0) int id) {
        leave(Senders.player(sender), id, false);
    }

    private void leave(Player player, int id, boolean show) {
        var answer = new Answer(player, id, show);
        run(
                answer,
                () -> company(player)
                        .map(company -> deps.services().contests().leave(company, player.getUniqueId(), id)),
                result -> joining(answer, id, result));
    }

    /**
     * Delivers up to an amount of items into a contest.
     *
     * @param sender sender of the command
     * @param id     id of the contest
     * @param amount the most items to deliver
     */
    @Executes("deliver")
    public void deliver(CommandSender sender, @IntArg(min = 0) int id, @IntArg(min = 1) int amount) {
        deliver(Senders.player(sender), id, amount, false);
    }

    /**
     * Delivers every matching item into a contest, up to what it misses.
     *
     * @param sender sender of the command
     * @param id     id of the contest
     * @param all    the literal {@code all}
     */
    @Executes("deliver")
    public void deliverAll(CommandSender sender, @IntArg(min = 0) int id, @Literal("all") String all) {
        deliver(Senders.player(sender), id, Integer.MAX_VALUE, false);
    }

    private void deliver(Player player, int id, int amount, boolean show) {
        var answer = new Answer(player, id, show);
        run(
                answer,
                () -> company(player).flatMap(company -> contest(id).map(contest -> new Target(company, contest))),
                target -> {
                    if (target.isEmpty()) {
                        answer.error(PREFIX + "error.unknownOrNoCompany");
                        return;
                    }
                    Threading.mainThread(deps.plugin()).execute(() -> take(player, target.get(), amount, answer));
                });
    }

    /**
     * Takes the items on the main thread, records them off it and hands back what the contest did not take.
     */
    private void take(Player player, Target target, int amount, Answer answer) {
        if (!player.isOnline()) return;
        var contest = target.contest();
        if (contest.state() != OfferState.ACTIVE) {
            answer.error(PREFIX + "error.notActive");
            return;
        }
        var wanted = ContestService.wanted(contest);
        if (wanted.isEmpty()) {
            answer.error(PREFIX + "error.unknown");
            return;
        }
        var limit = Math.min(amount, contest.missing());
        var parts = ItemSources.take(
                ItemSources.collect(player),
                0,
                wanted.get().template(),
                wanted.get().criteria(),
                deps.services().deliveryService().matcher(),
                limit);
        var offered = parts.stream().mapToInt(DeliveryPart::amount).sum();
        if (offered == 0) {
            answer.error(PREFIX + "error.noItems");
            return;
        }
        CompletableFuture.supplyAsync(
                        () -> deps.services()
                                .contests()
                                .deliver(target.company(), player.getUniqueId(), contest.id(), offered),
                        Threading.VIRTUAL)
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not deliver into contest " + contest.id(), err);
                    return new ContestService.Paid(
                            ContestDelivery.refused(ContestDelivery.Outcome.BUSY, OfferState.ACTIVE), null);
                })
                .thenAcceptAsync(paid -> delivered(answer, contest, parts, paid), Threading.mainThread(deps.plugin()));
    }

    private void delivered(Answer answer, EventOffer contest, List<DeliveryPart> parts, ContestService.Paid paid) {
        var delivery = paid.delivery();
        giveBack(answer.player(), ContestService.surplus(parts, delivery.items()));
        if (delivery.outcome() != ContestDelivery.Outcome.DELIVERED) {
            answer.error(PREFIX + "error." + key(delivery.outcome()));
            return;
        }
        var payout = paid.payout();
        var text = MenuText.create()
                .text("<neutral>")
                .locale(
                        PREFIX + "delivered",
                        Values.number("items", delivery.items()),
                        Values.text("offer_name", contest.name()),
                        Values.money("net", economy(), payout == null ? 0 : payout.net()),
                        Values.money("gross", economy(), delivery.gross()));
        if (delivery.state().closed()) text.newLine().text("<neutral>").locale(PREFIX + "closedNow");
        answer.done(text);
    }

    private void giveBack(Player player, List<DeliveryPart> surplus) {
        if (surplus.isEmpty()) return;
        if (player.isOnline()) {
            surplus.forEach(part -> Inventories.give(player, Inventories.split(part.stack(), part.amount())));
            return;
        }
        var stacks = surplus.stream()
                .map(part -> new ItemsReturnedEvent.ReturnedStack(part.stack(), part.amount()))
                .toList();
        deps.services()
                .itemReturnService()
                .storeAll(
                        player.getUniqueId(),
                        ReturnKind.WORKER,
                        stacks,
                        ItemsReturnedEvent.Reason.DELIVERY_OVERFLOW,
                        null);
    }

    private static String key(ContestDelivery.Outcome outcome) {
        return switch (outcome) {
            case DELIVERED -> "delivered";
            case UNKNOWN -> "unknown";
            case NOT_ACTIVE -> "notActive";
            case NOT_JOINED -> "notJoined";
            case FULL -> "full";
            case BUSY -> "busy";
        };
    }

    private void joining(Answer answer, int id, Optional<ContestService.Joining> result) {
        if (result.isEmpty()) {
            answer.error("error.noMember");
            return;
        }
        var number = Values.number("id", id);
        switch (result.get()) {
            case JOINED -> answer.done(MenuText.create().text("<neutral>").locale(PREFIX + "joined", number));
            case LEFT -> answer.done(MenuText.create().text("<neutral>").locale(PREFIX + "left", number));
            case ALREADY -> answer.error(PREFIX + "error.already", number);
            case NOT_JOINED -> answer.error(PREFIX + "error.notJoined", number);
            case NO_PERMISSION -> answer.error(PREFIX + "error.permission");
            case UNKNOWN -> answer.error(PREFIX + "error.unknown");
            case CLOSED -> answer.error(PREFIX + "error.closed", number);
            case LIMIT -> answer.error("error.orderLimit");
            case NOT_STARTED -> answer.error(PREFIX + "error.notStarted", number, Values.text("time", "-"));
        }
    }

    private static String pretty(Duration duration) {
        return Texts.prettyDuration(duration.isNegative() ? Duration.ZERO : duration);
    }

    private Optional<CompanyProfile> company(Player player) {
        var found = deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .join();
        return found == null ? Optional.empty() : found;
    }

    private static boolean mayManage(Optional<CompanyProfile> company, Player player) {
        return company.flatMap(found -> found.member(player.getUniqueId()))
                .map(member -> member.hasPermission(CompanyPermission.MANAGE_ORDERS))
                .orElse(false);
    }

    private Optional<EventOffer> contest(int id) {
        return offers().offer(id).filter(found -> found.kind() == OfferKind.CONTEST);
    }

    private AOfferData offers() {
        return deps.services().offerData();
    }

    private Economy economy() {
        return deps.services().economy();
    }

    private <T> void run(Answer answer, Supplier<T> query, Consumer<T> then) {
        deps.requireReady();
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL).thenAccept(then).exceptionally(err -> {
            if (err.getCause() instanceof CommandFailure failure) {
                answer.failure(failure);
                return null;
            }
            deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change a contest", err);
            answer.error(PREFIX + "error.failed");
            return null;
        });
    }

    private <T> void run(Player player, Supplier<T> query, Consumer<T> answer) {
        deps.requireReady();
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAccept(answer)
                .exceptionally(err -> {
                    if (err.getCause() instanceof CommandFailure failure) {
                        failure.sendTo(player, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change a contest", err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.failed");
                    return null;
                });
    }

    /**
     * The buttons of the contest menus: a change of the company's part shows the contest again.
     */
    private final class Buttons implements ContestMenu.Handlers, ContestsMenu.Handlers {
        @Override
        public void info(Player player, EventOffer contest) {
            ContestCommand.this.info(player, contest.id());
        }

        @Override
        public void join(Player player, EventOffer contest) {
            ContestCommand.this.join(player, contest.id(), true);
        }

        @Override
        public void leave(Player player, EventOffer contest) {
            ContestCommand.this.leave(player, contest.id(), true);
        }

        @Override
        public void deliverAll(Player player, EventOffer contest) {
            deliver(player, contest.id(), Integer.MAX_VALUE, true);
        }

        @Override
        public void list(Player player) {
            ContestCommand.this.list(player);
        }
    }

    /**
     * Where the outcome of joining, leaving or delivering goes: to the player as a message after a command, or on top
     * of the contest shown again after a button of its menu, so the dialog that waits for an answer gets one.
     */
    private final class Answer {
        private final Player player;
        private final int id;
        private final boolean show;

        /**
         * @param player the player who acted
         * @param id     id of the contest
         * @param show   whether the contest is shown again
         */
        private Answer(Player player, int id, boolean show) {
            this.player = player;
            this.id = id;
            this.show = show;
        }

        private Player player() {
            return player;
        }

        private void done(MenuText text) {
            if (show) info(player, id, Optional.of(text));
            else deps.messages().sendMessage(player, text);
        }

        private void error(String key, TagResolver.Single... values) {
            if (show)
                info(player, id, Optional.of(MenuText.create().text("<remove>").locale(key, values)));
            else deps.messages().sendErrorActionBar(player, key, values);
        }

        private void failure(CommandFailure failure) {
            if (!show) {
                failure.sendTo(player, deps.messages());
                return;
            }
            var rendered = failure.render(player, deps.messages());
            info(
                    player,
                    id,
                    Optional.of(MenuText.create().text("<remove><failure>", Values.component("failure", rendered))));
        }
    }

    /**
     * Where a delivery goes.
     *
     * @param company the worker's company
     * @param contest the contest
     */
    private record Target(CompanyProfile company, EventOffer contest) {}
}
