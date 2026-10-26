/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.systemorders.MoneySource;
import de.eldoria.companies.configuration.elements.systemorders.RulePrice;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.data.wrapper.order.SystemDecision;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.orders.system.DefinedRecord;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.format.DateTimeFormatter;
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
        DefinedRecord record;
        try {
            record = DefinedRecord.KIND.equals(decision.kind())
                    ? mapper.readValue(decision.record(), DefinedRecord.class)
                    : null;
        } catch (JacksonException e) {
            record = null;
        }
        if (record == null) {
            return List.of(
                    head, MenuText.create().locale(PREFIX + "info.unreadable", Values.text("kind", decision.kind())));
        }
        return List.of(head, item(record), price(record), paid(record), money(record));
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
        MenuText text;
        if (record.source() == MoneySource.SourceType.ACCOUNT) {
            text = MenuText.create()
                    .locale(PREFIX + "info.account", Values.text("account", String.valueOf(record.account())));
        } else {
            var minted = record.mintedLeft();
            var left = minted == null ? 0 : minted;
            text = MenuText.create().locale(PREFIX + "info.minted", Values.money("left", economy(), left));
        }
        var budget = record.budgetLeft();
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
