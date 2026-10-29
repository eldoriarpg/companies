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
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.offer.ContestDelivery;
import de.eldoria.companies.data.wrapper.offer.ContestEntry;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferKind;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.events.player.ItemsReturnedEvent;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.DeliveryPart;
import de.eldoria.companies.orders.ItemSources;
import de.eldoria.companies.orders.offers.ContestService;
import de.eldoria.companies.util.Inventories;
import de.eldoria.companies.util.Texts;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        var player = Senders.player(sender);
        var offers = offers();
        run(
                player,
                () -> new Listing(offers.contests(), company(player).map(found -> offers.joined(found.id()))),
                found -> {
                    if (found.contests().isEmpty()) {
                        deps.messages().sendMessage(player, PREFIX + "none");
                        return;
                    }
                    deps.messages()
                            .sendMessage(
                                    player, MenuText.create().text("<heading>").locale(PREFIX + "title"));
                    var joined = found.joined().orElse(List.of());
                    for (var contest : found.contests()) {
                        deps.messages().sendMessage(player, entry(contest, joined.contains(contest.id())));
                    }
                });
    }

    /**
     * Shows a contest.
     *
     * @param sender sender of the command
     * @param id     id of the contest
     */
    @Executes("info")
    public void info(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        var offers = offers();
        run(
                player,
                () -> contest(id)
                        .map(contest -> new Shown(
                                contest,
                                offers.ranking(id),
                                company(player).flatMap(company -> offers.entry(id, company.id())))),
                found -> {
                    if (found.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, PREFIX + "error.unknown");
                        return;
                    }
                    for (var line : lines(found.get())) deps.messages().sendMessage(player, line);
                });
    }

    /**
     * Lets the sender's company take part in a contest.
     *
     * @param sender sender of the command
     * @param id     id of the contest
     */
    @Executes("join")
    public void join(CommandSender sender, @IntArg(min = 0) int id) {
        var player = Senders.player(sender);
        run(
                player,
                () -> company(player)
                        .map(company -> deps.services().contests().join(company, player.getUniqueId(), id)),
                result -> {
                    if (result.isPresent() && result.get() == ContestService.Joining.NOT_STARTED) {
                        var starts = contest(id).map(EventOffer::starts).orElse(Times.now());
                        deps.messages()
                                .sendErrorActionBar(
                                        player,
                                        PREFIX + "error.notStarted",
                                        Values.number("id", id),
                                        Values.text("time", pretty(Duration.between(Times.now(), starts))));
                        return;
                    }
                    joining(player, id, result);
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
        var player = Senders.player(sender);
        run(
                player,
                () -> company(player)
                        .map(company -> deps.services().contests().leave(company, player.getUniqueId(), id)),
                result -> joining(player, id, result));
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
        deliver(Senders.player(sender), id, amount);
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
        deliver(Senders.player(sender), id, Integer.MAX_VALUE);
    }

    private void deliver(Player player, int id, int amount) {
        run(
                player,
                () -> company(player).flatMap(company -> contest(id).map(contest -> new Target(company, contest))),
                target -> {
                    if (target.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, PREFIX + "error.unknownOrNoCompany");
                        return;
                    }
                    Threading.mainThread(deps.plugin()).execute(() -> take(player, target.get(), amount));
                });
    }

    /**
     * Takes the items on the main thread, records them off it and hands back what the contest did not take.
     */
    private void take(Player player, Target target, int amount) {
        if (!player.isOnline()) return;
        var contest = target.contest();
        if (contest.state() != OfferState.ACTIVE) {
            deps.messages().sendErrorActionBar(player, PREFIX + "error.notActive");
            return;
        }
        var wanted = ContestService.wanted(contest);
        if (wanted.isEmpty()) {
            deps.messages().sendErrorActionBar(player, PREFIX + "error.unknown");
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
            deps.messages().sendErrorActionBar(player, PREFIX + "error.noItems");
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
                .thenAcceptAsync(paid -> delivered(player, contest, parts, paid), Threading.mainThread(deps.plugin()));
    }

    private void delivered(Player player, EventOffer contest, List<DeliveryPart> parts, ContestService.Paid paid) {
        var delivery = paid.delivery();
        giveBack(player, ContestService.surplus(parts, delivery.items()));
        if (delivery.outcome() != ContestDelivery.Outcome.DELIVERED) {
            deps.messages().sendErrorActionBar(player, PREFIX + "error." + key(delivery.outcome()));
            return;
        }
        var payout = paid.payout();
        deps.messages()
                .sendMessage(
                        player,
                        PREFIX + "delivered",
                        Values.number("items", delivery.items()),
                        Values.text("offer_name", contest.name()),
                        Values.money("net", economy(), payout == null ? 0 : payout.net()),
                        Values.money("gross", economy(), delivery.gross()));
        if (delivery.state().closed()) deps.messages().sendMessage(player, PREFIX + "closedNow");
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

    private void joining(Player player, int id, Optional<ContestService.Joining> result) {
        if (result.isEmpty()) {
            deps.messages().sendErrorActionBar(player, "error.noMember");
            return;
        }
        var number = Values.number("id", id);
        switch (result.get()) {
            case JOINED -> deps.messages().sendMessage(player, PREFIX + "joined", number);
            case LEFT -> deps.messages().sendMessage(player, PREFIX + "left", number);
            case ALREADY -> deps.messages().sendErrorActionBar(player, PREFIX + "error.already", number);
            case NOT_JOINED -> deps.messages().sendErrorActionBar(player, PREFIX + "error.notJoined", number);
            case NO_PERMISSION -> deps.messages().sendErrorActionBar(player, PREFIX + "error.permission");
            case UNKNOWN -> deps.messages().sendErrorActionBar(player, PREFIX + "error.unknown");
            case CLOSED -> deps.messages().sendErrorActionBar(player, PREFIX + "error.closed", number);
            case LIMIT -> deps.messages().sendErrorActionBar(player, "error.orderLimit");
            case NOT_STARTED ->
                deps.messages()
                        .sendErrorActionBar(player, PREFIX + "error.notStarted", number, Values.text("time", "-"));
        }
    }

    private MenuText entry(EventOffer contest, boolean joined) {
        var id = contest.id();
        var text = MenuText.create()
                .locale(
                        PREFIX + "entry",
                        Values.number("id", id),
                        Values.text("offer_name", contest.name()),
                        Values.text("item", contest.line().label()),
                        Values.number("delivered", contest.delivered()),
                        Values.number("amount", contest.amount()),
                        Values.money("unit", economy(), contest.unitPrice()))
                .space()
                .append(timeText(contest))
                .text(
                        " <act_info><show>[<i18n:words.info>]</act_info>",
                        Values.click("act_info", ClickEvent.runCommand("/company contest info " + id)));
        if (!joined && contest.started() != null) {
            text.text(
                    " <act_join><add>[<i18n:" + PREFIX + "join>]</act_join>",
                    Values.click("act_join", ClickEvent.runCommand("/company contest join " + id)));
        }
        return text;
    }

    private MenuText timeText(EventOffer contest) {
        var now = Times.now();
        return switch (contest.state()) {
            case SCHEDULED ->
                MenuText.create()
                        .locale(
                                PREFIX + "startsIn",
                                Values.text("time", pretty(Duration.between(now, contest.starts()))));
            case PAUSED -> MenuText.create().locale(PREFIX + "paused");
            case ACTIVE ->
                MenuText.create()
                        .locale(
                                PREFIX + "timeLeft",
                                Values.text("time", pretty(Duration.between(now, contest.ends()))));
            default -> MenuText.create().locale(PREFIX + "closed");
        };
    }

    private static String pretty(Duration duration) {
        return Texts.prettyDuration(duration.isNegative() ? Duration.ZERO : duration);
    }

    private List<MenuText> lines(Shown shown) {
        var contest = shown.contest();
        var id = contest.id();
        List<MenuText> lines = new ArrayList<>();
        lines.add(MenuText.create()
                .text("<heading>")
                .locale(PREFIX + "info.title", Values.number("id", id), Values.text("offer_name", contest.name()))
                .space()
                .append(timeText(contest)));
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.wanted",
                        Values.number("missing", contest.missing()),
                        Values.number("amount", contest.amount()),
                        Values.text("item", contest.line().label()),
                        Values.money("unit", economy(), contest.unitPrice())));
        if (contest.prizes().isEmpty()) {
            lines.add(MenuText.create().locale(PREFIX + "info.noPrizes"));
        } else {
            var prizes = new ArrayList<String>();
            for (var index = 0; index < contest.prizes().size(); index++) {
                prizes.add(
                        (index + 1) + ". " + economy().format(contest.prizes().get(index)));
            }
            lines.add(
                    MenuText.create().locale(PREFIX + "info.prizes", Values.text("prizes", String.join(", ", prizes))));
        }
        var entry = shown.own();
        if (entry.isPresent()) {
            var place = shown.ranking().stream()
                            .map(ContestEntry::company)
                            .toList()
                            .indexOf(entry.get().company())
                    + 1;
            lines.add(MenuText.create()
                    .locale(
                            entry.get().active() ? PREFIX + "info.own" : PREFIX + "info.ownLeft",
                            Values.number("items", entry.get().delivered()),
                            Values.money("paid", economy(), entry.get().paid()),
                            Values.number("place", place)));
        } else {
            lines.add(MenuText.create().locale(PREFIX + "info.notJoined"));
        }
        var ranked = shown.ranking().stream()
                .filter(found -> found.delivered() > 0)
                .limit(RANKED)
                .toList();
        if (ranked.isEmpty()) {
            lines.add(MenuText.create().locale(PREFIX + "info.noDeliveries"));
        } else {
            lines.add(MenuText.create().locale(PREFIX + "info.ranking"));
            for (var index = 0; index < ranked.size(); index++) {
                var found = ranked.get(index);
                lines.add(MenuText.create()
                        .locale(
                                PREFIX + "info.place",
                                Values.number("place", index + 1),
                                Values.text(
                                        "company_name",
                                        Objects.requireNonNullElse(found.name(), "#" + found.company())),
                                Values.number("items", found.delivered())));
            }
        }
        lines.add(buttons(contest, entry.filter(ContestEntry::active).isPresent()));
        return lines;
    }

    private MenuText buttons(EventOffer contest, boolean joined) {
        var id = contest.id();
        var text = MenuText.create();
        if (contest.state().closed()) return text;
        if (joined) {
            if (contest.state() == OfferState.ACTIVE) {
                text.text(
                        "<act_deliver><add>[<i18n:" + PREFIX + "deliverAll>]</act_deliver> ",
                        Values.click("act_deliver", ClickEvent.runCommand("/company contest deliver " + id + " all")));
            }
            text.text(
                    "<act_leave><remove>[<i18n:" + PREFIX + "leave>]</act_leave>",
                    Values.click("act_leave", ClickEvent.suggestCommand("/company contest leave " + id)));
        } else if (contest.started() != null) {
            text.text(
                    "<act_join><add>[<i18n:" + PREFIX + "join>]</act_join>",
                    Values.click("act_join", ClickEvent.runCommand("/company contest join " + id)));
        }
        return text;
    }

    private Optional<CompanyProfile> company(Player player) {
        var found = deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .join();
        return found == null ? Optional.empty() : found;
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
     * The contests listed, with those the player's company takes part in.
     *
     * @param contests the contests that have not closed
     * @param joined   the ids of those the company takes part in, empty without a company
     */
    private record Listing(List<EventOffer> contests, Optional<List<Integer>> joined) {}

    /**
     * A contest as its info shows it.
     *
     * @param contest the contest
     * @param ranking its places
     * @param own     the entry of the player's company, empty when it never took part
     */
    private record Shown(EventOffer contest, List<ContestEntry> ranking, Optional<ContestEntry> own) {}

    /**
     * Where a delivery goes.
     *
     * @param company the worker's company
     * @param contest the contest
     */
    private record Target(CompanyProfile company, EventOffer contest) {}
}
