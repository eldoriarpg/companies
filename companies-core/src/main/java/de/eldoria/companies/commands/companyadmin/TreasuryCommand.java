/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.QuotedCompanyNames;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.company.TreasuryTexts;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.util.Permission.Admin;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /companyadmin treasury}: inspects and corrects company treasuries.
 * <ul>
 *     <li>{@code <company>} shows the balance, the payout share and the latest transactions;</li>
 *     <li>{@code give <company> <amount>} adds money;</li>
 *     <li>{@code take <company> <amount>} removes money, at most the balance.</li>
 * </ul>
 * Changes are written to the treasury log with the admin as actor and to the server log. A company name with spaces is
 * quoted.
 */
@Subcommand("treasury")
@Permission(Admin.TREASURY)
@PlayerOnly
public final class TreasuryCommand {
    private static final int LOG_ENTRIES = 10;

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public TreasuryCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows a company's treasury.
     *
     * @param sender  sender of the command
     * @param company name of the company
     */
    @Executes
    public void show(CommandSender sender, @QuotedCompanyNames @StringArg(StringArgType.STRING) String company) {
        var admin = Senders.player(sender);
        var economy = deps.services().economy();
        company(company)
                .thenCompose(found -> treasury()
                        .treasury(found.id())
                        .thenCombine(
                                deps.economy().treasury().retrieveLog(found.id(), 1, LOG_ENTRIES),
                                (treasury, entries) -> {
                                    var text = MenuText.create()
                                            .text("<neutral>")
                                            .locale(
                                                    "companyadmin.treasury.info",
                                                    Values.text("company_name", found.name()),
                                                    Values.money(
                                                            "balance",
                                                            economy,
                                                            treasury.balance().doubleValue()),
                                                    Values.number("percent", treasury.payoutShare()))
                                            .newLine()
                                            .append(TreasuryTexts.entries("entry", entries, names(entries), economy));
                                    deps.messages().sendMessage(admin, text);
                                    return null;
                                }))
                .exceptionally(err -> fail(admin, err));
    }

    /**
     * Adds money to a company's treasury.
     *
     * @param sender  sender of the command
     * @param company name of the company
     * @param amount  amount to add
     */
    @Executes("give")
    public void give(
            CommandSender sender,
            @QuotedCompanyNames @StringArg(StringArgType.STRING) String company,
            @DoubleArg(min = 0.01, max = 1.0E12) double amount) {
        adjust(Senders.player(sender), company, true, BigDecimal.valueOf(amount));
    }

    /**
     * Removes money from a company's treasury.
     *
     * @param sender  sender of the command
     * @param company name of the company
     * @param amount  amount to remove
     */
    @Executes("take")
    public void take(
            CommandSender sender,
            @QuotedCompanyNames @StringArg(StringArgType.STRING) String company,
            @DoubleArg(min = 0.01, max = 1.0E12) double amount) {
        adjust(Senders.player(sender), company, false, BigDecimal.valueOf(amount));
    }

    private void adjust(Player admin, String name, boolean give, BigDecimal amount) {
        deps.requireReady();
        company(name)
                .thenCompose(company -> treasury()
                        .adminAdjust(company, admin.getUniqueId(), give, amount)
                        .thenAccept(result -> {
                            switch (result.outcome()) {
                                case DONE -> {
                                    deps.adminLog()
                                            .entry(admin, give ? "treasury give" : "treasury take")
                                            .with("company", company.name())
                                            .with("company_id", company.id())
                                            .with("amount", amount.toPlainString())
                                            .with("balance", result.balance().toPlainString())
                                            .write();
                                    deps.messages()
                                            .sendMessage(
                                                    admin,
                                                    MenuText.create()
                                                            .text("<neutral>")
                                                            .locale(
                                                                    give
                                                                            ? "companyadmin.treasury.given"
                                                                            : "companyadmin.treasury.taken",
                                                                    Values.text(
                                                                            "amount",
                                                                            treasury()
                                                                                    .format(amount)),
                                                                    Values.text("company_name", company.name()),
                                                                    Values.text(
                                                                            "balance",
                                                                            treasury()
                                                                                    .format(result.balance()))));
                                }
                                case TREASURY_FUNDS ->
                                    deps.messages().sendError(admin, "company.bank.error.insufficient");
                                case INVALID_AMOUNT -> deps.messages().sendError(admin, "error.invalidNumber");
                                default -> deps.messages().sendError(admin, "error.saveFailed");
                            }
                        }))
                .exceptionally(err -> fail(admin, err));
    }

    private CompletableFuture<SimpleCompany> company(String name) {
        return deps.companyData()
                .retrieveCompanyByName(name)
                .asFuture()
                .thenApply(company -> company.orElseThrow(() -> CommandFailure.of("error.unknownCompany")));
    }

    private Map<UUID, String> names(List<TreasuryLogEntry> entries) {
        Map<UUID, String> names = new HashMap<>();
        for (var entry : entries) {
            entry.actorId()
                    .ifPresent(id -> names.computeIfAbsent(
                            id,
                            uuid -> Objects.requireNonNullElse(
                                    deps.plugin()
                                            .getServer()
                                            .getOfflinePlayer(uuid)
                                            .getName(),
                                    "?")));
        }
        return names;
    }

    private TreasuryService treasury() {
        return deps.services().treasury();
    }

    private Void fail(Player admin, Throwable err) {
        var cause = err.getCause() != null ? err.getCause() : err;
        if (cause instanceof CommandFailure failure) {
            failure.sendTo(admin, deps.messages());
            return null;
        }
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
    }
}
