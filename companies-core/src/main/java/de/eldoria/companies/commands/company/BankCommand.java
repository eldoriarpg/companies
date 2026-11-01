/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.TreasuryAmounts;
import de.eldoria.companies.commands.suggest.TreasurySources;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.treasury.TreasuryLogEntry;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.company.TreasuryLogMenu;
import de.eldoria.companies.menus.company.TreasuryMenu;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.services.TreasuryService.Result;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.PlayerNames;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * {@code /company bank}: the treasury of the player's company.
 * <ul>
 *     <li>without arguments the treasury menu: balance, payout share, the latest transactions and the actions;</li>
 *     <li>{@code deposit <amount>} pays money in, any member may;</li>
 *     <li>{@code withdraw <amount>} takes money out, with the treasury permission;</li>
 *     <li>{@code share <percent>} sets the share of each payout kept in the treasury, with the treasury permission;</li>
 *     <li>{@code log [page]} the transactions, any member may read them.</li>
 * </ul>
 * Answers of the menu's buttons show the menu again with the result on top.
 */
@Subcommand("bank")
@PlayerOnly
public final class BankCommand {
    private static final int RECENT_ENTRIES = 5;

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public BankCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the treasury menu.
     *
     * @param sender sender of the command
     */
    @Executes
    public void show(CommandSender sender) {
        var player = Senders.player(sender);
        requireEnabled();
        company(player)
                .thenAccept(profile -> show(player, profile, Optional.empty()))
                .exceptionally(this::logError);
    }

    /**
     * Pays money into the treasury.
     *
     * @param sender sender of the command
     * @param amount amount to pay in
     */
    @Executes("deposit")
    public void deposit(CommandSender sender, @DoubleArg(min = 0.01, max = 1.0E12) double amount) {
        var player = Senders.player(sender);
        requireEnabled();
        var value = BigDecimal.valueOf(amount);
        act(
                player,
                false,
                profile -> treasury().deposit(profile, player, value),
                result -> moved("company.bank.deposited", value, result));
    }

    /**
     * Takes money out of the treasury.
     *
     * @param sender sender of the command
     * @param amount amount to take out
     */
    @Executes("withdraw")
    public void withdraw(CommandSender sender, @TreasuryAmounts @DoubleArg(min = 0.01, max = 1.0E12) double amount) {
        var player = Senders.player(sender);
        requireEnabled();
        var value = BigDecimal.valueOf(amount);
        act(
                player,
                false,
                profile -> treasury().withdraw(profile, player, value),
                result -> moved("company.bank.withdrawn", value, result));
    }

    /**
     * Sets the share of each payout after tax that goes to the treasury.
     *
     * @param sender  sender of the command
     * @param percent share in percent
     */
    @Executes("share")
    public void share(CommandSender sender, @IntArg(min = 0, max = 100) int percent) {
        var player = Senders.player(sender);
        requireEnabled();
        act(player, false, profile -> treasury().setShare(profile, player, percent), result -> shareChanged(percent));
    }

    /**
     * Shows the newest page of the log.
     *
     * @param sender sender of the command
     */
    @Executes("log")
    public void log(CommandSender sender) {
        log(sender, 1);
    }

    /**
     * Shows a page of the log.
     *
     * @param sender sender of the command
     * @param page   page, starting at 1
     */
    @Executes("log")
    public void log(CommandSender sender, @IntArg(min = 1) int page) {
        var player = Senders.player(sender);
        requireEnabled();
        log(player, page);
    }

    /**
     * Shows a page of the log of the player's company.
     *
     * @param player viewing member
     * @param page   page, starting at 1
     */
    public void log(Player player, int page) {
        var services = deps.services();
        var menu = new TreasuryLogMenu(deps.messages(), services.economy(), new LogButtons());
        company(player)
                .thenCompose(profile -> treasury().log(profile.id(), page).thenAccept(entries -> services.menus()
                        .show(
                                player,
                                menu,
                                TreasuryLogMenu.Model.of(
                                        profile.name(),
                                        page,
                                        entries,
                                        names(entries),
                                        treasury().settings().logPageSize()))))
                .exceptionally(this::logError);
    }

    private void show(Player player, CompanyProfile profile, Optional<MenuText> notice) {
        var services = deps.services();
        var menu = new TreasuryMenu(deps.messages(), services.economy(), new MenuButtons());
        var data = deps.economy().treasury();
        var manage = profile.member(player)
                .map(member -> member.hasPermission(CompanyPermission.TREASURY))
                .orElse(false);
        var cap = treasury().settings().maxPayoutShare();
        treasury()
                .treasury(profile.id())
                .thenCombine(
                        data.retrieveLog(profile.id(), 1, RECENT_ENTRIES),
                        (treasury, recent) -> new TreasuryMenu.Model(
                                profile.name(), treasury, cap, manage, recent, names(recent), notice))
                .thenAccept(model -> services.menus().show(player, menu, model))
                .exceptionally(this::logError);
    }

    private void act(
            Player player,
            boolean fromMenu,
            Function<CompanyProfile, CompletableFuture<Result>> action,
            Function<Result, Reply> success) {
        company(player)
                .thenCompose(profile -> action.apply(profile).thenAccept(result -> {
                    TreasurySources.invalidate(player.getUniqueId());
                    var reply = result.done() ? success.apply(result) : failure(result);
                    if (fromMenu) {
                        show(player, profile, Optional.of(reply.notice()));
                        return;
                    }
                    reply.send(deps, player);
                }))
                .exceptionally(this::logError);
    }

    private CompletableFuture<CompanyProfile> company(Player player) {
        return deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenCompose(profile -> {
                    if (profile.isPresent()) return CompletableFuture.completedFuture(profile.get());
                    deps.messages().sendErrorActionBar(player, "error.noMember");
                    return new CompletableFuture<>();
                });
    }

    private Reply moved(String key, BigDecimal amount, Result result) {
        return Reply.success(
                key,
                Values.text("amount", treasury().format(amount)),
                Values.text("balance", treasury().format(result.balance())));
    }

    private static Reply shareChanged(int percent) {
        return Reply.success("company.bank.shareChanged", Values.number("percent", percent));
    }

    private Reply failure(Result result) {
        var refusal =
                treasury().explain(result, Money.currencyName(deps.services().economy()));
        return Reply.error(refusal.key(), refusal.values().toArray(TagResolver.Single[]::new));
    }

    private Reply invalidAmount() {
        return failure(Result.of(TreasuryService.Outcome.INVALID_AMOUNT));
    }

    private Map<UUID, String> names(List<TreasuryLogEntry> entries) {
        Map<UUID, String> names = new HashMap<>();
        for (var entry : entries) {
            entry.actorId()
                    .ifPresent(id ->
                            names.computeIfAbsent(id, uuid -> Objects.requireNonNullElse(PlayerNames.of(uuid), "?")));
        }
        return names;
    }

    private void requireEnabled() {
        deps.requireReady();
        Checks.isTrue(treasury().settings().enabled(), "company.bank.error.disabled");
    }

    private TreasuryService treasury() {
        return deps.services().treasury();
    }

    private Void logError(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
    }

    private static Optional<BigDecimal> amount(String text) {
        try {
            return Optional.of(new BigDecimal(text.trim().replace(',', '.')));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * An answer to a treasury action.
     *
     * @param error  whether the action failed
     * @param key    locale key of the answer
     * @param values placeholders of the answer
     */
    private record Reply(boolean error, String key, List<TagResolver.Single> values) {
        Reply {
            values = List.copyOf(values);
        }

        static Reply success(String key, TagResolver.Single... values) {
            return new Reply(false, key, List.of(values));
        }

        static Reply error(String key, TagResolver.Single... values) {
            return new Reply(true, key, List.of(values));
        }

        MenuText notice() {
            return MenuText.create()
                    .text(error ? "<remove>" : "<neutral>")
                    .locale(key, values.toArray(TagResolver.Single[]::new));
        }

        void send(CommandDependencies deps, Player player) {
            if (error) {
                deps.messages().sendError(player, key, values.toArray(TagResolver[]::new));
                return;
            }
            deps.messages().sendMessage(player, notice());
        }
    }

    /**
     * The buttons of the treasury menu: every action shows the menu again with its result on top.
     */
    private final class MenuButtons implements TreasuryMenu.Handlers {
        @Override
        public void deposit(Player player, String amount) {
            money(player, amount, "company.bank.deposited", (profile, value) -> treasury()
                    .deposit(profile, player, value));
        }

        @Override
        public void withdraw(Player player, String amount) {
            money(player, amount, "company.bank.withdrawn", (profile, value) -> treasury()
                    .withdraw(profile, player, value));
        }

        @Override
        public void share(Player player, int percent) {
            act(
                    player,
                    true,
                    profile -> treasury().setShare(profile, player, percent),
                    result -> shareChanged(percent));
        }

        @Override
        public void log(Player player, int page) {
            BankCommand.this.log(player, page);
        }

        private void money(
                Player player,
                String text,
                String key,
                BiFunction<CompanyProfile, BigDecimal, CompletableFuture<Result>> action) {
            var value = amount(text);
            if (value.isEmpty()) {
                company(player)
                        .thenAccept(profile -> show(
                                player, profile, Optional.of(invalidAmount().notice())));
                return;
            }
            act(player, true, profile -> action.apply(profile, value.get()), result -> moved(key, value.get(), result));
        }
    }

    /**
     * The buttons of the log: paging and back to the treasury.
     */
    private final class LogButtons implements TreasuryLogMenu.Handlers {
        @Override
        public void page(Player player, int page) {
            log(player, page);
        }

        @Override
        public void back(Player player) {
            company(player).thenAccept(profile -> show(player, profile, Optional.empty()));
        }
    }
}
