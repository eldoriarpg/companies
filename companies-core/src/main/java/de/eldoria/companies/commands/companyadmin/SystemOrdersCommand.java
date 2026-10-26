/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.GuessedSettings;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.configuration.elements.systemorders.RulePrice;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.system.DefinedRecord;
import de.eldoria.companies.orders.system.GuessedRecord;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.event.ClickEvent;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /companyadmin systemorders}: the orders the server placed itself.
 * <ul>
 *     <li>{@code list} shows every system order that is open or in work, with its rule, price, state and when it was
 *     placed;</li>
 *     <li>{@code info <id>} shows why a system order was placed: the rule and the numbers its price and money stood
 *     on. It works for ended orders as long as the order history keeps them.</li>
 * </ul>
 * Removing one is {@code /companyadmin order cancel}, which gives its money back to the account that paid it.
 */
@Subcommand("systemorders")
@Permission(Admin.ORDERS)
public final class SystemOrdersCommand {
    private static final DateTimeFormatter PLACED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String PREFIX = "companyadmin.systemorders.";

    private final CommandDependencies deps;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    /**
     * @param deps what the commands need
     */
    public SystemOrdersCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Lists the system orders that are open or in work.
     *
     * @param sender sender of the command
     */
    @Executes("list")
    public void list(CommandSender sender) {
        deps.requireReady();
        var orderData = deps.orderData();
        run(sender, () -> orderData.systemDecisions(OrderState.UNCLAIMED, OrderState.CLAIMED), decisions -> {
            if (decisions.isEmpty()) {
                deps.messages().sendMessage(sender, PREFIX + "none");
                return;
            }
            deps.messages()
                    .sendMessage(sender, MenuText.create().text("<heading>").locale(PREFIX + "title"));
            for (var decision : decisions) deps.messages().sendMessage(sender, entry(decision));
        });
    }

    /**
     * Shows why a system order was placed.
     *
     * @param sender sender of the command
     * @param id     id of the order
     */
    @Executes("info")
    public void info(CommandSender sender, @IntArg(min = 0) int id) {
        deps.requireReady();
        var orderData = deps.orderData();
        run(sender, () -> orderData.systemDecision(id), found -> {
            if (found.isEmpty()) {
                deps.messages().sendError(sender, PREFIX + "error.unknown", Values.number("id", id));
                return;
            }
            deps.adminLog().entry(sender, "system order info").with("id", id).write();
            for (var line : lines(found.get())) deps.messages().sendMessage(sender, line);
        });
    }

    private MenuText entry(SystemDecision decision) {
        var state =
                deps.orderData().retrieveOrderById(decision.order()).asFuture().join();
        var stateKey = Optional.ofNullable(state)
                .flatMap(order -> order)
                .map(SimpleOrder::state)
                .map(OrderState::translationKey);
        var text = MenuText.create()
                .locale(
                        PREFIX + "entry",
                        Values.number("id", decision.order()),
                        Values.text("rule", decision.rule()),
                        Values.money("total", economy(), decision.paid()),
                        Values.text("placed", PLACED.format(Times.local(decision.decidedAt()))))
                .space();
        if (stateKey.isPresent()) text.locale(stateKey.get());
        else text.text("-");
        var info = "/companyadmin systemorders info " + decision.order();
        return text.text(
                " <act_info><info>[<i18n:words.info>]</act_info>",
                Values.click("act_info", ClickEvent.runCommand(info)));
    }

    private List<MenuText> lines(SystemDecision decision) {
        var head = MenuText.create()
                .text("<heading>")
                .locale(
                        PREFIX + "info.title",
                        Values.number("id", decision.order()),
                        Values.text("rule", decision.rule()),
                        Values.text("placed", PLACED.format(Times.local(decision.decidedAt()))));
        if (DefinedRecord.KIND.equals(decision.kind())) {
            var record = read(decision, DefinedRecord.class);
            if (record != null) return List.of(head, item(record), price(record), paid(record), money(record));
        }
        if (GuessedRecord.KIND.equals(decision.kind())) {
            var record = read(decision, GuessedRecord.class);
            if (record != null) return guessed(head, record);
        }
        return List.of(
                head, MenuText.create().locale(PREFIX + "info.unreadable", Values.text("kind", decision.kind())));
    }

    private <T> @Nullable T read(SystemDecision decision, Class<T> type) {
        try {
            return mapper.readValue(decision.record(), type);
        } catch (JacksonException e) {
            return null;
        }
    }

    private List<MenuText> guessed(MenuText head, GuessedRecord record) {
        var lines = new ArrayList<MenuText>();
        lines.add(head);
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.guessed.candidate",
                        Values.text("material", record.material()),
                        Values.number("orders", record.orders()),
                        Values.text("score", "%.2f".formatted(record.score())),
                        Values.text("recent", "%.1f".formatted(record.recentRate())),
                        Values.text("long", "%.1f".formatted(record.longRate())),
                        Values.number("rank", record.rank()),
                        Values.number("candidates", record.candidates())));
        lines.add(competition(record));
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.guessed.price",
                        Values.money("average", economy(), record.average()),
                        Values.text("factor", "%.2f".formatted(record.factor())),
                        Values.number("step", record.step()),
                        Values.text("jitter", "%+.1f %%".formatted(record.jitter() * 100)),
                        Values.money("unit", economy(), record.unit()),
                        Values.money("total", economy(), record.total())));
        var replaces = record.replaces();
        if (replaces != null) {
            lines.add(MenuText.create().locale(PREFIX + "info.guessed.reprice", Values.number("replaces", replaces)));
        }
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.guessed.size",
                        Values.number("amount", record.amount()),
                        Values.text("share", "%.0f %%".formatted(record.sizeShare() * 100)),
                        Values.number("typical", record.typical())));
        lines.add(MenuText.create()
                .locale(
                        PREFIX + "info.guessed.cap",
                        Values.number("open", record.openGuessed()),
                        Values.number("target", record.target()),
                        Values.number("players", record.openPlayer())));
        lines.add(money(record.source(), record.account(), record.budgetLeft(), record.mintedLeft()));
        return lines;
    }

    private MenuText competition(GuessedRecord record) {
        var cap = record.cap();
        if (record.competition() == GuessedSettings.Competition.SKIP || cap == null) {
            return MenuText.create().locale(PREFIX + "info.guessed.skip");
        }
        var lowest = record.lowest();
        if (lowest == null) {
            return MenuText.create()
                    .locale(PREFIX + "info.guessed.underbidMarket", Values.money("cap", economy(), cap));
        }
        return MenuText.create()
                .locale(
                        PREFIX + "info.guessed.underbid",
                        Values.number("items", record.playerItems()),
                        Values.money("lowest", economy(), lowest),
                        Values.money("cap", economy(), cap));
    }

    private MenuText item(DefinedRecord record) {
        return MenuText.create()
                .locale(
                        PREFIX + "info.item",
                        Values.text("name", record.name()),
                        Values.number("amount", record.amount()),
                        Values.text("material", record.material()));
    }

    private MenuText price(DefinedRecord record) {
        var jitter = Values.text("jitter", "%+.1f %%".formatted(record.jitter() * 100));
        var average = record.average();
        if (record.mode() == RulePrice.PriceMode.FIXED || average == null) {
            return MenuText.create()
                    .locale(PREFIX + "info.fixed", Values.money("base", economy(), record.base()), jitter);
        }
        return MenuText.create()
                .locale(
                        PREFIX + "info.average",
                        Values.money("average", economy(), average),
                        Values.text("factor", "%.2f".formatted(record.factor())),
                        Values.money("base", economy(), record.base()),
                        jitter);
    }

    private MenuText paid(DefinedRecord record) {
        var unit = Values.money("unit", economy(), record.unit());
        var total = Values.money("total", economy(), record.total());
        if (!record.bounded()) return MenuText.create().locale(PREFIX + "info.paid", unit, total);
        var max = record.max() > 0 ? economy().format(record.max()) : "-";
        return MenuText.create()
                .locale(
                        PREFIX + "info.bounded",
                        unit,
                        total,
                        Values.money("min", economy(), record.min()),
                        Values.text("max", max));
    }

    private MenuText money(DefinedRecord record) {
        return money(record.source(), record.account(), record.budgetLeft(), record.mintedLeft());
    }

    private MenuText money(
            MoneySource.SourceType source, @Nullable String account, @Nullable Double budget, @Nullable Double minted) {
        MenuText text;
        if (source == MoneySource.SourceType.ACCOUNT) {
            text = MenuText.create().locale(PREFIX + "info.account", Values.text("account", String.valueOf(account)));
        } else {
            var left = minted == null ? 0 : minted;
            text = MenuText.create().locale(PREFIX + "info.minted", Values.money("left", economy(), left));
        }
        if (budget != null) {
            text.space().locale(PREFIX + "info.budget", Values.money("left", economy(), budget));
        }
        return text;
    }

    private Economy economy() {
        return deps.services().economy();
    }

    private <T> void run(CommandSender sender, Supplier<T> query, Consumer<T> answer) {
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAccept(answer)
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read the system orders", err);
                    deps.messages().sendError(sender, PREFIX + "error.failed");
                    return null;
                });
    }
}
