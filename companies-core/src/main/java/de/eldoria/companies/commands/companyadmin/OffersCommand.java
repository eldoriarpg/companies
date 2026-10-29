/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.dashboard.views.PlainText;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.wrapper.offer.Contributor;
import de.eldoria.companies.data.wrapper.offer.EventOffer;
import de.eldoria.companies.data.wrapper.offer.OfferLine;
import de.eldoria.companies.data.wrapper.offer.OfferState;
import de.eldoria.companies.data.wrapper.offer.OpenChunk;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.OrderDraftService;
import de.eldoria.companies.orders.offers.GoalDraft;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Literal;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /companyadmin offers}: the event offers of the server. Only goals exist so far.
 * <ul>
 *     <li>{@code goal material|hand|criteria <name> <money> <amount> <price> <chunk> <start> <hours> ...} creates a
 *     goal: its name, {@code minted} or the account paying it, the items asked for in all, the price per item, the
 *     items per chunk, the hours until it starts and the hours it runs. It orders a material, the item held in the
 *     main hand (exactly, when the item has data) or every item meeting criteria;</li>
 *     <li>{@code list} shows the newest offers with their state and progress;</li>
 *     <li>{@code info <id>} shows who created an offer, its money, its progress and its top contributors;</li>
 *     <li>{@code open <id> <count>} changes how many chunks wait to be taken at once;</li>
 *     <li>{@code pause}, {@code resume} and {@code cancel <id>} stop, restart and close an offer.</li>
 * </ul>
 * Changes are stored at once; the primary server places and withdraws the chunks within a minute, at once when the
 * command runs on the primary.
 */
@Subcommand("offers")
@Permission(Admin.OFFERS)
public final class OffersCommand {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String PREFIX = "companyadmin.offers.";
    private static final int LISTED = 20;
    private static final int CONTRIBUTORS = 5;

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public OffersCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Creates a goal ordering any item of a material, with the budget its chunks cost.
     *
     * @param sender   sender of the command
     * @param name     name of the goal
     * @param money    {@code minted} or the paying account
     * @param amount   items asked for in all
     * @param price    price per item
     * @param chunk    items per chunk
     * @param start    hours until it starts
     * @param hours    hours it runs
     * @param material the material
     */
    @Executes("goal material")
    public void goalMaterial(
            CommandSender sender,
            @StringArg(StringArgType.STRING) String name,
            @StringArg(StringArgType.STRING) String money,
            @IntArg(min = 1) int amount,
            @DoubleArg(min = 0, max = GoalDraft.MAX_PRICE) double price,
            @IntArg(min = 1) int chunk,
            @IntArg(min = 0, max = GoalDraft.MAX_HOURS) int start,
            @IntArg(min = 1, max = GoalDraft.MAX_HOURS) int hours,
            @StringArg(StringArgType.WORD) String material) {
        material(sender, name, money, amount, price, chunk, start, hours, material, null);
    }

    /**
     * Creates a goal ordering any item of a material, with a budget of its own.
     *
     * @param sender   sender of the command
     * @param name     name of the goal
     * @param money    {@code minted} or the paying account
     * @param amount   items asked for in all
     * @param price    price per item
     * @param chunk    items per chunk
     * @param start    hours until it starts
     * @param hours    hours it runs
     * @param material the material
     * @param budget   what the whole goal may spend
     */
    @Executes("goal material")
    public void goalMaterial(
            CommandSender sender,
            @StringArg(StringArgType.STRING) String name,
            @StringArg(StringArgType.STRING) String money,
            @IntArg(min = 1) int amount,
            @DoubleArg(min = 0, max = GoalDraft.MAX_PRICE) double price,
            @IntArg(min = 1) int chunk,
            @IntArg(min = 0, max = GoalDraft.MAX_HOURS) int start,
            @IntArg(min = 1, max = GoalDraft.MAX_HOURS) int hours,
            @StringArg(StringArgType.WORD) String material,
            @DoubleArg(min = 0, max = GoalDraft.MAX_BUDGET) double budget) {
        material(sender, name, money, amount, price, chunk, start, hours, material, budget);
    }

    private void material(
            CommandSender sender,
            String name,
            String money,
            int amount,
            double price,
            int chunk,
            int start,
            int hours,
            String material,
            @Nullable Double budget) {
        deps.requireReady();
        var line = GoalDraft.material(material, OffersCommand::canonical);
        create(sender, name, money, line, amount, price, chunk, start, hours, budget);
    }

    /**
     * Creates a goal ordering the item in the sender's main hand: exactly when it has data, otherwise any item of its
     * material. Its budget is what its chunks cost.
     *
     * @param sender   sender of the command, holding the item
     * @param name     name of the goal
     * @param money    {@code minted} or the paying account
     * @param amount   items asked for in all
     * @param price    price per item
     * @param chunk    items per chunk
     * @param start    hours until it starts
     * @param hours    hours it runs
     */
    @Executes("goal hand")
    public void goalHand(
            CommandSender sender,
            @StringArg(StringArgType.STRING) String name,
            @StringArg(StringArgType.STRING) String money,
            @IntArg(min = 1) int amount,
            @DoubleArg(min = 0, max = GoalDraft.MAX_PRICE) double price,
            @IntArg(min = 1) int chunk,
            @IntArg(min = 0, max = GoalDraft.MAX_HOURS) int start,
            @IntArg(min = 1, max = GoalDraft.MAX_HOURS) int hours) {
        hand(sender, name, money, amount, price, chunk, start, hours, null);
    }

    /**
     * Creates a goal ordering the item in the sender's main hand, with a budget of its own.
     *
     * @param sender   sender of the command, holding the item
     * @param name     name of the goal
     * @param money    {@code minted} or the paying account
     * @param amount   items asked for in all
     * @param price    price per item
     * @param chunk    items per chunk
     * @param start    hours until it starts
     * @param hours    hours it runs
     * @param budget   what the whole goal may spend
     */
    @Executes("goal hand")
    public void goalHand(
            CommandSender sender,
            @StringArg(StringArgType.STRING) String name,
            @StringArg(StringArgType.STRING) String money,
            @IntArg(min = 1) int amount,
            @DoubleArg(min = 0, max = GoalDraft.MAX_PRICE) double price,
            @IntArg(min = 1) int chunk,
            @IntArg(min = 0, max = GoalDraft.MAX_HOURS) int start,
            @IntArg(min = 1, max = GoalDraft.MAX_HOURS) int hours,
            @DoubleArg(min = 0, max = GoalDraft.MAX_BUDGET) double budget) {
        hand(sender, name, money, amount, price, chunk, start, hours, budget);
    }

    private void hand(
            CommandSender sender,
            String name,
            String money,
            int amount,
            double price,
            int chunk,
            int start,
            int hours,
            @Nullable Double budget) {
        deps.requireReady();
        var player = Senders.player(sender);
        var held = player.getInventory().getItemInMainHand();
        var requested = held.isEmpty()
                ? Optional.<OrderDraftService.RequestedItem>empty()
                : deps.drafts().request(held);
        if (requested.isEmpty()) throw CommandFailure.of("order.draft.error.notOrderable");
        var template = requested.get().template();
        var line = requested.get().exact()
                ? OfferLine.item(template, PlainText.of(template.effectiveName()))
                : OfferLine.material(template.getType().name());
        create(sender, name, money, line, amount, price, chunk, start, hours, budget);
    }

    /**
     * Creates a goal accepting every item meeting criteria, with the budget its chunks cost.
     *
     * @param sender   sender of the command
     * @param name     name of the goal
     * @param money    {@code minted} or the paying account
     * @param amount   items asked for in all
     * @param price    price per item
     * @param chunk    items per chunk
     * @param start    hours until it starts
     * @param hours    hours it runs
     * @param criteria the criteria in the text form players type
     */
    @Executes("goal criteria")
    public void goalCriteria(
            CommandSender sender,
            @StringArg(StringArgType.STRING) String name,
            @StringArg(StringArgType.STRING) String money,
            @IntArg(min = 1) int amount,
            @DoubleArg(min = 0, max = GoalDraft.MAX_PRICE) double price,
            @IntArg(min = 1) int chunk,
            @IntArg(min = 0, max = GoalDraft.MAX_HOURS) int start,
            @IntArg(min = 1, max = GoalDraft.MAX_HOURS) int hours,
            @StringArg(StringArgType.GREEDY) String criteria) {
        deps.requireReady();
        create(sender, name, money, GoalDraft.criteria(criteria), amount, price, chunk, start, hours, null);
    }

    /**
     * Creates a goal accepting every item meeting criteria, with a budget of its own. The criteria come last, so the
     * budget is named: {@code ... <hours> budget <budget> <criteria>}.
     *
     * @param sender   sender of the command
     * @param name     name of the goal
     * @param money    {@code minted} or the paying account
     * @param amount   items asked for in all
     * @param price    price per item
     * @param chunk    items per chunk
     * @param start    hours until it starts
     * @param hours    hours it runs
     * @param named    the word {@code budget}
     * @param budget   what the whole goal may spend
     * @param criteria the criteria in the text form players type
     */
    @Executes("goal criteria")
    public void goalCriteria(
            CommandSender sender,
            @StringArg(StringArgType.STRING) String name,
            @StringArg(StringArgType.STRING) String money,
            @IntArg(min = 1) int amount,
            @DoubleArg(min = 0, max = GoalDraft.MAX_PRICE) double price,
            @IntArg(min = 1) int chunk,
            @IntArg(min = 0, max = GoalDraft.MAX_HOURS) int start,
            @IntArg(min = 1, max = GoalDraft.MAX_HOURS) int hours,
            @Literal("budget") String named,
            @DoubleArg(min = 0, max = GoalDraft.MAX_BUDGET) double budget,
            @StringArg(StringArgType.GREEDY) String criteria) {
        deps.requireReady();
        create(sender, name, money, GoalDraft.criteria(criteria), amount, price, chunk, start, hours, budget);
    }

    private void create(
            CommandSender sender,
            String name,
            String money,
            OfferLine line,
            int amount,
            double price,
            int chunk,
            int start,
            int hours,
            @Nullable Double budget) {
        var goal = GoalDraft.goal(
                name,
                money,
                line,
                amount,
                price,
                chunk,
                start,
                hours,
                budget,
                sender.getName(),
                LocalDateTime.now(Times.UTC));
        run(sender, () -> offers().create(goal), created -> {
            if (created.isEmpty()) {
                deps.messages().sendError(sender, PREFIX + "error.failed");
                return;
            }
            var id = created.get();
            deps.adminLog()
                    .entry(sender, "offer goal create")
                    .with("id", id)
                    .with("name", goal.name())
                    .with("item", line.label())
                    .with("amount", goal.amount())
                    .with("price", goal.unitPrice())
                    .with("money", goal.account() == null ? GoalDraft.MINTED : goal.account())
                    .with("budget", goal.budget())
                    .write();
            deps.messages()
                    .sendMessage(
                            sender,
                            PREFIX + "created",
                            Values.number("id", id),
                            Values.text("name", goal.name()),
                            Values.text("starts", time(goal.starts())));
            checkSoon();
        });
    }

    /**
     * Lists the newest offers.
     *
     * @param sender sender of the command
     */
    @Executes("list")
    public void list(CommandSender sender) {
        deps.requireReady();
        run(sender, () -> offers().offers(LISTED), found -> {
            if (found.isEmpty()) {
                deps.messages().sendMessage(sender, PREFIX + "none");
                return;
            }
            deps.messages()
                    .sendMessage(sender, MenuText.create().text("<heading>").locale(PREFIX + "title"));
            for (var offer : found) deps.messages().sendMessage(sender, entry(offer));
        });
    }

    /**
     * Shows an offer: who created it, its money, its progress and who delivered most.
     *
     * @param sender sender of the command
     * @param id     id of the offer
     */
    @Executes("info")
    public void info(CommandSender sender, @IntArg(min = 0) int id) {
        deps.requireReady();
        var data = offers();
        run(
                sender,
                () -> data.offer(id)
                        .map(offer -> new Details(offer, data.openChunks(id), data.contributors(id, CONTRIBUTORS))),
                found -> {
                    if (found.isEmpty()) {
                        deps.messages().sendError(sender, PREFIX + "error.unknown", Values.number("id", id));
                        return;
                    }
                    deps.adminLog().entry(sender, "offer info").with("id", id).write();
                    for (var line : lines(found.get())) deps.messages().sendMessage(sender, line);
                });
    }

    /**
     * Changes how many chunks of a goal wait to be taken at once.
     *
     * @param sender sender of the command
     * @param id     id of the offer
     * @param count  chunks kept waiting
     */
    @Executes("open")
    public void open(CommandSender sender, @IntArg(min = 0) int id, @IntArg(min = 1, max = 100) int count) {
        deps.requireReady();
        run(sender, () -> limit(offers(), id, count), result -> {
            switch (result) {
                case Transition.UNKNOWN ->
                    deps.messages().sendError(sender, PREFIX + "error.unknown", Values.number("id", id));
                case Transition.REFUSED ->
                    deps.messages().sendError(sender, PREFIX + "error.open", Values.number("id", id));
                case Transition.DONE -> {
                    deps.adminLog()
                            .entry(sender, "offer open")
                            .with("id", id)
                            .with("count", count)
                            .write();
                    deps.messages()
                            .sendMessage(
                                    sender, PREFIX + "opened", Values.number("id", id), Values.number("count", count));
                    checkSoon();
                }
            }
        });
    }

    /**
     * Changes how many chunks of an offer that still runs wait to be taken at once.
     *
     * @param data  offer repository
     * @param id    id of the offer
     * @param count chunks kept waiting
     * @return what came of it: refused for a closed offer
     */
    static Transition limit(AOfferData data, int id, int count) {
        var offer = data.offer(id);
        if (offer.isEmpty()) return Transition.UNKNOWN;
        if (offer.get().state().closed()) return Transition.REFUSED;
        return data.chunkLimit(id, count) ? Transition.DONE : Transition.UNKNOWN;
    }

    /**
     * Pauses a running offer: its waiting chunks are withdrawn, taken ones may still be delivered.
     *
     * @param sender sender of the command
     * @param id     id of the offer
     */
    @Executes("pause")
    public void pause(CommandSender sender, @IntArg(min = 0) int id) {
        change(sender, id, "pause", List.of(OfferState.SCHEDULED, OfferState.ACTIVE), OfferState.PAUSED);
    }

    /**
     * Resumes a paused offer.
     *
     * @param sender sender of the command
     * @param id     id of the offer
     */
    @Executes("resume")
    public void resume(CommandSender sender, @IntArg(min = 0) int id) {
        change(sender, id, "resume", List.of(OfferState.PAUSED), OfferState.ACTIVE);
    }

    /**
     * Closes an offer for good: its waiting chunks are withdrawn, taken ones may still be delivered.
     *
     * @param sender sender of the command
     * @param id     id of the offer
     */
    @Executes("cancel")
    public void cancel(CommandSender sender, @IntArg(min = 0) int id) {
        change(sender, id, "cancel", List.copyOf(OfferState.RUNNING), OfferState.CANCELLED);
    }

    private void change(CommandSender sender, int id, String action, List<OfferState> from, OfferState to) {
        deps.requireReady();
        var data = offers();
        run(sender, () -> transition(data, id, from, to), result -> {
            switch (result) {
                case Transition.UNKNOWN ->
                    deps.messages().sendError(sender, PREFIX + "error.unknown", Values.number("id", id));
                case Transition.REFUSED ->
                    deps.messages().sendError(sender, PREFIX + "error." + action, Values.number("id", id));
                case Transition.DONE -> {
                    deps.adminLog()
                            .entry(sender, "offer " + action)
                            .with("id", id)
                            .write();
                    deps.messages()
                            .sendMessage(
                                    sender,
                                    MenuText.create()
                                            .locale(PREFIX + "changed", Values.number("id", id))
                                            .space()
                                            .locale(state(to)));
                    checkSoon();
                }
            }
        });
    }

    /**
     * Moves an offer into a state from one of the states it may come from. A paused offer whose start has not come
     * yet goes back to waiting for it.
     *
     * @param data offer repository
     * @param id   id of the offer
     * @param from the states it may come from
     * @param to   the new state
     * @return what came of it
     */
    static Transition transition(AOfferData data, int id, List<OfferState> from, OfferState to) {
        var offer = data.offer(id);
        if (offer.isEmpty()) return Transition.UNKNOWN;
        var current = offer.get().state();
        if (!from.contains(current)) return Transition.REFUSED;
        var target = to;
        if (current == OfferState.PAUSED
                && to == OfferState.ACTIVE
                && LocalDateTime.now(Times.UTC).isBefore(offer.get().starts())) {
            target = OfferState.SCHEDULED;
        }
        return data.state(id, current, target, LocalDateTime.now(Times.UTC)) ? Transition.DONE : Transition.REFUSED;
    }

    private MenuText entry(EventOffer offer) {
        var info = "/companyadmin offers info " + offer.id();
        return MenuText.create()
                .locale(
                        PREFIX + "entry",
                        Values.number("id", offer.id()),
                        Values.text("name", offer.name()),
                        Values.number("delivered", offer.delivered()),
                        Values.number("amount", offer.amount()),
                        Values.number("percent", offer.percent()),
                        Values.text("ends", time(offer.ends())))
                .space()
                .locale(state(offer.state()))
                .text(
                        " <act_info><info>[<i18n:words.info>]</act_info>",
                        Values.click("act_info", ClickEvent.runCommand(info)));
    }

    private List<MenuText> lines(Details details) {
        var offer = details.offer();
        var lines = new ArrayList<MenuText>();
        lines.add(MenuText.create()
                .text("<heading>")
                .locale(PREFIX + "info.title", Values.number("id", offer.id()), Values.text("name", offer.name()))
                .space()
                .locale(state(offer.state())));
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.goal",
                        Values.number("amount", offer.amount()),
                        Values.text("item", offer.line().label()),
                        Values.money("unit", economy(), offer.unitPrice()),
                        Values.number("chunk", offer.chunkSize()),
                        Values.number("open", offer.openChunks())));
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.time",
                        Values.text("starts", time(offer.starts())),
                        Values.text("ends", time(offer.ends())),
                        Values.text("by", offer.createdBy()),
                        Values.text("created", time(offer.created()))));
        var closed = offer.closed();
        if (closed != null) {
            lines.add(MenuText.create().locale(PREFIX + "info.closed", Values.text("closed", time(closed))));
        }
        var money = offer.source() == MoneySource.SourceType.ACCOUNT
                ? MenuText.create()
                        .locale(PREFIX + "info.account", Values.text("account", String.valueOf(offer.account())))
                : MenuText.create().locale(PREFIX + "info.minted");
        lines.add(money.space()
                .locale(
                        PREFIX + "info.budget",
                        Values.money("budget", economy(), offer.budget()),
                        Values.money("spent", economy(), Math.max(0, offer.spent())),
                        Values.money("left", economy(), offer.budgetLeft())));
        var open = details.open();
        var waiting = open.stream()
                .filter(OpenChunk::unclaimed)
                .mapToInt(OpenChunk::amount)
                .sum();
        var taken = open.stream()
                .filter(chunk -> !chunk.unclaimed())
                .mapToInt(OpenChunk::amount)
                .sum();
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.progress",
                        Values.number("delivered", offer.delivered()),
                        Values.number("amount", offer.amount()),
                        Values.number("percent", offer.percent()),
                        Values.number("waiting", waiting),
                        Values.number("taken", taken)));
        if (details.contributors().isEmpty()) {
            lines.add(MenuText.create().locale(PREFIX + "info.noContributors"));
        } else {
            lines.add(MenuText.create().locale(PREFIX + "info.contributors"));
            var rank = 1;
            for (var contributor : details.contributors()) lines.add(contributor(rank++, contributor));
        }
        lines.add(actions(offer));
        return lines;
    }

    private MenuText contributor(int rank, Contributor contributor) {
        var worker = contributor.soloWorker();
        var items = Values.number("items", contributor.delivered());
        var chunks = Values.number("chunks", contributor.chunks());
        if (worker != null) {
            var player = deps.plugin().getServer().getOfflinePlayer(worker).getName();
            return MenuText.create()
                    .locale(
                            PREFIX + "info.contributorAlone",
                            Values.number("rank", rank),
                            Values.text("name", player == null ? worker.toString() : player),
                            items,
                            chunks);
        }
        var name = contributor.name() == null ? "#" + contributor.company() : contributor.name();
        return MenuText.create()
                .locale(
                        PREFIX + "info.contributor",
                        Values.number("rank", rank),
                        Values.text("name", name),
                        items,
                        chunks);
    }

    private MenuText actions(EventOffer offer) {
        var text = MenuText.create();
        var id = offer.id();
        if (offer.state() == OfferState.PAUSED) {
            text.text(
                    "<act_resume><add>[<i18n:" + PREFIX + "resume>]</act_resume> ",
                    Values.click("act_resume", ClickEvent.runCommand("/companyadmin offers resume " + id)));
        } else if (!offer.state().closed()) {
            text.text(
                    "<act_pause><add>[<i18n:" + PREFIX + "pause>]</act_pause> ",
                    Values.click("act_pause", ClickEvent.runCommand("/companyadmin offers pause " + id)));
        }
        if (!offer.state().closed()) {
            text.text(
                    "<act_cancel><remove>[<i18n:" + PREFIX + "cancel>]</act_cancel>",
                    Values.click("act_cancel", ClickEvent.suggestCommand("/companyadmin offers cancel " + id)));
        }
        return text;
    }

    private static String state(OfferState state) {
        return PREFIX + "state." + state.name().toLowerCase(Locale.ROOT);
    }

    private static Optional<String> canonical(String material) {
        var found = Material.matchMaterial(material);
        return found != null && found.isItem() && !found.isAir() ? Optional.of(found.name()) : Optional.empty();
    }

    private static String time(LocalDateTime utc) {
        return TIME.format(Times.local(utc));
    }

    private AOfferData offers() {
        return deps.services().offerData();
    }

    private void checkSoon() {
        if (deps.servicesReady()) deps.services().offers().checkSoon();
    }

    private Economy economy() {
        return deps.services().economy();
    }

    private <T> void run(CommandSender sender, Supplier<T> query, Consumer<T> answer) {
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAccept(answer)
                .exceptionally(err -> {
                    if (err.getCause() instanceof CommandFailure failure) {
                        failure.sendTo(sender, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change the event offers", err);
                    deps.messages().sendError(sender, PREFIX + "error.failed");
                    return null;
                });
    }

    /**
     * What came of moving an offer into another state.
     */
    enum Transition {
        /**
         * There is no such offer.
         */
        UNKNOWN,
        /**
         * The offer is in a state it may not leave that way.
         */
        REFUSED,
        /**
         * The offer is in its new state.
         */
        DONE
    }

    /**
     * An offer with what its info shows.
     *
     * @param offer        the offer
     * @param open         its open chunks
     * @param contributors who delivered most
     */
    private record Details(EventOffer offer, List<OpenChunk> open, List<Contributor> contributors) {}
}
