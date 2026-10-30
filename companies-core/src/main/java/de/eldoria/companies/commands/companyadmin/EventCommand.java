/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.repository.AServerEventData;
import de.eldoria.companies.data.wrapper.event.ServerEvent;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.admin.EventForm;
import de.eldoria.companies.menus.admin.EventFormMenu;
import de.eldoria.companies.menus.order.EventTexts;
import de.eldoria.companies.services.events.EventTimes;
import de.eldoria.companies.services.events.ServerEventService;
import de.eldoria.companies.services.events.ServerEventService.Change;
import de.eldoria.companies.util.AdminLog;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /companyadmin event}: the server events.
 * <ul>
 *     <li>{@code create <name>} opens the form of a new event, see {@link EventForm}; {@code set <field> <value>}
 *     changes one of its fields from chat, {@code save} creates it and {@code discard} throws it away;</li>
 *     <li>{@code list} shows the newest events with their state;</li>
 *     <li>{@code info <id>} shows an event: its effect, scope, times, money and who created and closed it;</li>
 *     <li>{@code end <id>} ends a running event, {@code cancel <id>} cancels one that waits or runs;</li>
 *     <li>{@code extend <id> <duration>} moves its end later, such as by {@code 12h};</li>
 *     <li>{@code budget <id> <amount|none>} sets the budget of a reward multiplier.</li>
 * </ul>
 * The form of each admin is kept on this server until it is saved or discarded. Changes are stored at once and every
 * server hears of them through the node bus; the primary starts and ends events at their times.
 */
@Subcommand("event")
@Permission(Admin.EVENTS)
public final class EventCommand implements EventFormMenu.Actions {
    private static final String PREFIX = "companyadmin.event.";
    private static final int LISTED = 20;

    private final CommandDependencies deps;
    private final Map<UUID, EventForm> forms = new ConcurrentHashMap<>();
    private final AtomicBoolean listening = new AtomicBoolean();

    /**
     * @param deps what the commands need
     */
    public EventCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Opens the form of a new event.
     *
     * @param sender sender of the command, a player
     * @param name   name of the event
     */
    @Executes("create")
    public void create(CommandSender sender, @StringArg(StringArgType.GREEDY) String name) {
        deps.requireReady();
        var player = Senders.player(sender);
        if (name.isBlank() || name.strip().length() > ServerEvent.MAX_NAME_LENGTH) {
            throw CommandFailure.of(PREFIX + "problem.NAME");
        }
        var form = EventForm.of(name.strip());
        listen();
        forms.put(player.getUniqueId(), form);
        show(player, form, null);
    }

    /**
     * Changes a field of the sender's form.
     *
     * @param sender sender of the command, a player
     * @param field  the field
     * @param value  its new text, empty to empty it
     */
    @Executes("set")
    public void set(
            CommandSender sender,
            @StringArg(StringArgType.WORD) String field,
            @StringArg(StringArgType.GREEDY) String value) {
        deps.requireReady();
        var player = Senders.player(sender);
        var form = form(player);
        var chosen = EventForm.Field.of(field)
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.unknownField", Values.text("field", field)));
        var changed = form.with(chosen, value);
        forms.put(player.getUniqueId(), changed);
        show(player, changed, null);
    }

    /**
     * Clears a field of the sender's form.
     *
     * @param sender sender of the command, a player
     * @param field  the field
     */
    @Executes("set")
    public void clear(CommandSender sender, @StringArg(StringArgType.WORD) String field) {
        set(sender, field, "");
    }

    /**
     * Creates the event of the sender's form.
     *
     * @param sender sender of the command, a player
     */
    @Executes("save")
    public void saveCommand(CommandSender sender) {
        deps.requireReady();
        var player = Senders.player(sender);
        save(player, form(player));
    }

    /**
     * Throws the sender's form away.
     *
     * @param sender sender of the command, a player
     */
    @Executes("discard")
    public void discardCommand(CommandSender sender) {
        discard(Senders.player(sender));
    }

    @Override
    public void save(Player player, EventForm form) {
        forms.put(player.getUniqueId(), form);
        var collector = deps.configuration().economySettings().collectorAccount();
        var log = deps.adminLog();
        run(
                player,
                () -> save(
                        service(), log, player.getName(), form, Times.zone(), LocalDateTime.now(Times.UTC), collector),
                saved -> {
                    switch (saved) {
                        case Saved.Refused refused ->
                            Threading.mainThread(deps.plugin())
                                    .execute(() ->
                                            show(player, form, MenuText.create().locale(refused.key())));
                        case Saved.Created created -> {
                            forms.remove(player.getUniqueId());
                            Threading.mainThread(deps.plugin())
                                    .execute(() -> deps.services().menus().close(player));
                            deps.messages()
                                    .sendMessage(
                                            player,
                                            PREFIX + "created",
                                            Values.number("id", created.id()),
                                            Values.text(
                                                    "event_name",
                                                    created.event().name()),
                                            Values.text(
                                                    "starts",
                                                    EventTimes.show(
                                                            created.event().starts(), Times.zone())));
                        }
                    }
                });
    }

    @Override
    public void create(Player player) {
        var form = forms.get(player.getUniqueId());
        if (form == null) {
            deps.messages().sendError(player, PREFIX + "error.noForm");
            return;
        }
        save(player, form);
    }

    /**
     * Creates the event of a form and writes it to the admin log. Blocks on the database.
     *
     * @param service   the server events
     * @param log       the admin log
     * @param admin     who creates it
     * @param form      the form
     * @param zone      the server's time zone
     * @param now       the current time in UTC
     * @param collector the collector account
     * @return the created event, or the locale key of why it was refused
     */
    public static Saved save(
            ServerEventService service,
            AdminLog log,
            String admin,
            EventForm form,
            ZoneId zone,
            LocalDateTime now,
            String collector) {
        var parsed = form.parse(zone, now, collector, admin);
        if (parsed instanceof EventForm.Invalid invalid)
            return new Saved.Refused(invalid.field().errorKey());
        var draft = ((EventForm.Valid) parsed).event();
        var created = service.create(draft);
        if (!created.stored()) return new Saved.Refused(PREFIX + "problem." + created.problem());
        var stored = service.event(created.id()).orElse(draft);
        log.entry(admin, "event create")
                .with("id", created.id())
                .with("name", stored.name())
                .with("kind", stored.kind())
                .with("value", stored.value())
                .with("materials", String.join(",", stored.materials()))
                .with("levels", stored.levels().toString())
                .with("starts", stored.starts())
                .with("ends", stored.ends())
                .with("funding", stored.funding())
                .with("account", stored.account())
                .with("budget", stored.budget())
                .write();
        return new Saved.Created(created.id(), stored);
    }

    /**
     * What came of saving a form.
     */
    public sealed interface Saved {
        /**
         * The event was created.
         *
         * @param id    its id
         * @param event the event as stored
         */
        record Created(int id, ServerEvent event) implements Saved {}

        /**
         * The form was refused.
         *
         * @param key the locale key of why
         */
        record Refused(String key) implements Saved {}
    }

    @Override
    public void discard(Player player) {
        forms.remove(player.getUniqueId());
        deps.services().menus().close(player);
        deps.messages().sendMessage(player, PREFIX + "discarded");
    }

    /**
     * Lists the newest events.
     *
     * @param sender sender of the command
     */
    @Executes("list")
    public void list(CommandSender sender) {
        deps.requireReady();
        run(sender, () -> data().events(LISTED), found -> {
            if (found.isEmpty()) {
                deps.messages().sendMessage(sender, PREFIX + "none");
                return;
            }
            deps.messages()
                    .sendMessage(sender, MenuText.create().text("<heading>").locale(PREFIX + "title"));
            for (var event : found) deps.messages().sendMessage(sender, entry(event));
        });
    }

    /**
     * Shows an event.
     *
     * @param sender sender of the command
     * @param id     id of the event
     */
    @Executes("info")
    public void info(CommandSender sender, @IntArg(min = 0) int id) {
        deps.requireReady();
        run(sender, () -> data().event(id), found -> {
            if (found.isEmpty()) {
                deps.messages().sendError(sender, PREFIX + "error.unknown", Values.number("id", id));
                return;
            }
            deps.adminLog().entry(sender, "event info").with("id", id).write();
            for (var line : lines(found.get())) deps.messages().sendMessage(sender, line);
        });
    }

    /**
     * Ends a running event early.
     *
     * @param sender sender of the command
     * @param id     id of the event
     */
    @Executes("end")
    public void end(CommandSender sender, @IntArg(min = 0) int id) {
        deps.requireReady();
        change(sender, id, "end", () -> service().end(id, sender.getName()), Map.of());
    }

    /**
     * Cancels an event that waits or runs.
     *
     * @param sender sender of the command
     * @param id     id of the event
     */
    @Executes("cancel")
    public void cancel(CommandSender sender, @IntArg(min = 0) int id) {
        deps.requireReady();
        change(sender, id, "cancel", () -> service().cancel(id, sender.getName()), Map.of());
    }

    /**
     * Moves the end of an event later.
     *
     * @param sender   sender of the command
     * @param id       id of the event
     * @param duration how much later, such as {@code 12h}
     */
    @Executes("extend")
    public void extend(CommandSender sender, @IntArg(min = 0) int id, @StringArg(StringArgType.WORD) String duration) {
        deps.requireReady();
        var parsed = EventTimes.duration(duration)
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.duration", Values.text("duration", duration)));
        change(sender, id, "extend", () -> service().extend(id, parsed), Map.of("by", duration));
    }

    /**
     * Sets the budget of a reward multiplier.
     *
     * @param sender sender of the command
     * @param id     id of the event
     * @param amount the budget, or {@code none} for none
     */
    @Executes("budget")
    public void budget(CommandSender sender, @IntArg(min = 0) int id, @StringArg(StringArgType.WORD) String amount) {
        deps.requireReady();
        var budget = budgetOf(amount)
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.amount", Values.text("amount", amount)));
        change(sender, id, "budget", () -> service().budget(id, budget.orElse(null)), Map.of("budget", amount));
    }

    /**
     * Reads a budget as the command takes it.
     *
     * @param text a positive amount, or {@code none}
     * @return the budget, empty inside for none; empty when the text is neither
     */
    public static Optional<Optional<Double>> budgetOf(String text) {
        if (text.strip().equalsIgnoreCase("none")) return Optional.of(Optional.empty());
        var amount = EventForm.number(text);
        if (amount == null || !(amount > 0)) return Optional.empty();
        return Optional.of(Optional.of(amount));
    }

    private void change(
            CommandSender sender, int id, String action, Supplier<Change> change, Map<String, String> details) {
        var log = deps.adminLog();
        var replies = new Replies() {
            @Override
            public void message(String key, TagResolver.Single... values) {
                deps.messages().sendMessage(sender, key, values);
            }

            @Override
            public void error(String key, TagResolver.Single... values) {
                deps.messages().sendError(sender, key, values);
            }
        };
        run(sender, change, result -> changed(log, replies, sender.getName(), id, action, result, details));
    }

    /**
     * Answers a change of an event and writes a done change to the admin log.
     *
     * @param log     the admin log
     * @param replies answers the admin
     * @param admin   who changed it
     * @param id      id of the event
     * @param action  what was done: {@code end}, {@code cancel}, {@code extend} or {@code budget}
     * @param result  what came of it
     * @param details the values of the change for the admin log
     */
    public static void changed(
            AdminLog log,
            Replies replies,
            String admin,
            int id,
            String action,
            Change result,
            Map<String, String> details) {
        var values = Values.number("id", id);
        switch (result) {
            case DONE -> {
                var entry = log.entry(admin, "event " + action).with("id", id);
                details.forEach(entry::with);
                entry.write();
                replies.message(PREFIX + "done." + action, values);
            }
            case UNKNOWN -> replies.error(PREFIX + "error.unknown", values);
            case CLOSED -> replies.error(PREFIX + "error.closed", values);
            case REFUSED -> replies.error(PREFIX + "error.refused." + action, values);
            case FAILED -> replies.error(PREFIX + "error.failed");
        }
    }

    /**
     * Answers an admin.
     */
    public interface Replies {
        /**
         * @param key    locale key of the message
         * @param values its placeholders
         */
        void message(String key, TagResolver.Single... values);

        /**
         * @param key    locale key of the error
         * @param values its placeholders
         */
        void error(String key, TagResolver.Single... values);
    }

    private MenuText entry(ServerEvent event) {
        return MenuText.create()
                .locale(
                        PREFIX + "entry",
                        Values.number("id", event.id()),
                        Values.text("event_name", event.name()),
                        Values.text("starts", EventTimes.show(event.starts(), Times.zone())),
                        Values.text("ends", EventTimes.show(event.ends(), Times.zone())))
                .space()
                .locale(PREFIX + "state." + event.state().name())
                .text(
                        " <act_info><info>[<i18n:words.info>]</act_info>",
                        Values.click("act_info", ClickEvent.runCommand("/companyadmin event info " + event.id())));
    }

    private List<MenuText> lines(ServerEvent event) {
        var economy = deps.services().economy();
        List<MenuText> lines = new ArrayList<>();
        lines.add(MenuText.create()
                .text("<heading>")
                .locale(PREFIX + "info.title", Values.number("id", event.id()), Values.text("event_name", event.name()))
                .space()
                .locale(PREFIX + "state." + event.state().name()));
        lines.add(MenuText.create().locale(PREFIX + "info.effect").space().append(EventTexts.effect(event)));
        lines.add(MenuText.create().locale(PREFIX + "info.scope").space().append(EventTexts.scope(event)));
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.times",
                        Values.text("starts", EventTimes.show(event.starts(), Times.zone())),
                        Values.text("ends", EventTimes.show(event.ends(), Times.zone()))));
        if (event.kind().funded()) {
            var funding = event.funding();
            var account = event.account();
            var budget = event.budget();
            lines.add(MenuText.create()
                    .locale(
                            PREFIX + "info.money",
                            Values.text("funding", funding == null ? "-" : funding.name()),
                            Values.text("account", account == null ? "-" : account),
                            Values.text("budget", budget == null ? "-" : economy.format(budget)),
                            Values.text("spent", economy.format(event.spent()))));
        }
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.created",
                        Values.text("by", event.createdBy()),
                        Values.text("template", Objects.requireNonNullElse(event.template(), "-"))));
        var closed = event.closed();
        if (closed != null) {
            lines.add(MenuText.create()
                    .locale(PREFIX + "info.closed", Values.text("closed", EventTimes.show(closed, Times.zone())))
                    .space()
                    .append(closedBy(event.closedBy())));
        }
        return lines;
    }

    /**
     * @param closedBy who closed an event, see {@link ServerEvent#closedBy()}
     * @return the text naming it: an admin, its time or money, or the primary for a missed or withdrawn event
     */
    public static MenuText closedBy(@Nullable String closedBy) {
        if (closedBy == null) return MenuText.create().locale(PREFIX + "closedBy.time");
        if (closedBy.equals(ServerEvent.MISSED_BY)) return MenuText.create().locale(PREFIX + "closedBy.missed");
        if (closedBy.equals(ServerEvent.TEMPLATE_BY)) return MenuText.create().locale(PREFIX + "closedBy.template");
        return MenuText.create().locale(PREFIX + "closedBy.admin", Values.text("by", closedBy));
    }

    /**
     * Drops the form of a player who leaves, once a form was opened on this server.
     */
    private void listen() {
        if (!listening.compareAndSet(false, true)) return;
        deps.plugin()
                .getServer()
                .getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            public void onQuit(PlayerQuitEvent event) {
                                forget(event.getPlayer().getUniqueId());
                            }
                        },
                        deps.plugin());
    }

    /**
     * Drops the form of a player.
     *
     * @param player the player
     */
    public void forget(UUID player) {
        forms.remove(player);
    }

    private EventForm form(Player player) {
        var form = forms.get(player.getUniqueId());
        if (form == null) throw CommandFailure.of(PREFIX + "error.noForm");
        return form;
    }

    private void show(Player player, EventForm form, @Nullable MenuText error) {
        deps.services()
                .menus()
                .show(player, new EventFormMenu(deps.messages(), this), new EventFormMenu.View(form, error));
    }

    private ServerEventService service() {
        return deps.services().serverEvents();
    }

    private AServerEventData data() {
        return deps.services().serverEventData();
    }

    private <T> void run(CommandSender sender, Supplier<T> query, Consumer<T> answer) {
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAccept(answer)
                .exceptionally(err -> {
                    if (err.getCause() instanceof CommandFailure failure) {
                        failure.sendTo(sender, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change the server events", err);
                    deps.messages().sendError(sender, PREFIX + "error.failed");
                    return null;
                });
    }
}
