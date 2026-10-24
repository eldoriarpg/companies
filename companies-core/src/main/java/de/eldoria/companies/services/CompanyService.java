/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.TreasuryKind;
import de.eldoria.companies.configuration.elements.CompanySettings;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.PreCompanyCreateEvent;
import de.eldoria.companies.orders.OrderCreationService.Wallet;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.messages.Replacement;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Founding and renaming companies.
 * <p>
 * The actor is given by id and does not need to be online: Vault works for offline players. The checks and the
 * database run on the executor, Vault and {@link PreCompanyCreateEvent} on the main thread. Every action checks again
 * what its confirmation showed, since the name may have been taken meanwhile.
 * <p>
 * {@link PreCompanyCreateEvent} carries an online {@link Player}, so it fires only while the founder is online on this
 * server; a founder who is offline here founds without it. The Bukkit permission to found a company is checked by the
 * caller.
 */
public final class CompanyService {
    /**
     * Names of companies have to stay below this length.
     */
    public static final int MAX_NAME_LENGTH = 32;

    private static final String NAME_USED = "error.companyNameUsed";
    private static final String SAVE_FAILED = "error.saveFailed";

    private final ACompanyData companyData;
    private final CompanyChanges changes;
    private final TreasuryService treasury;
    private final Wallet wallet;
    private final Supplier<CompanySettings> settings;
    private final Function<UUID, OfflinePlayer> players;
    private final Function<UUID, Optional<Player>> online;
    private final Executor mainThread;
    private final Executor executor;
    private final Logger logger;

    /**
     * @param companyData company repository
     * @param changes     fires the events of founding and renaming
     * @param treasury    pays a rename from the treasury
     * @param wallet      charges and refunds players
     * @param settings    current company settings
     * @param players     looks up a player by id
     * @param online      looks up a player online on this server by id
     * @param mainThread  runs Vault and the pre-event on the main thread
     * @param executor    runs the checks and the database work
     * @param logger      logs failed saves
     */
    public CompanyService(
            ACompanyData companyData,
            CompanyChanges changes,
            TreasuryService treasury,
            Wallet wallet,
            Supplier<CompanySettings> settings,
            Function<UUID, OfflinePlayer> players,
            Function<UUID, Optional<Player>> online,
            Executor mainThread,
            Executor executor,
            Logger logger) {
        this.companyData = companyData;
        this.changes = changes;
        this.treasury = treasury;
        this.wallet = wallet;
        this.settings = settings;
        this.players = players;
        this.online = online;
        this.mainThread = mainThread;
        this.executor = executor;
        this.logger = logger;
    }

    /**
     * The service of a running plugin, paying through Vault.
     *
     * @param plugin      the plugin
     * @param companyData company repository
     * @param treasury    treasury service
     * @param wallet      charges and refunds players
     * @param settings    current company settings
     * @return the service
     */
    public static CompanyService create(
            Plugin plugin,
            ACompanyData companyData,
            TreasuryService treasury,
            Wallet wallet,
            Supplier<CompanySettings> settings) {
        var server = plugin.getServer();
        return new CompanyService(
                companyData,
                CompanyChanges.create(plugin, companyData),
                treasury,
                wallet,
                settings,
                server::getOfflinePlayer,
                uuid -> Optional.ofNullable(server.getPlayer(uuid)),
                Threading.mainThread(plugin),
                Threading.VIRTUAL,
                plugin.getLogger());
    }

    /**
     * @return the current founding price
     */
    public double foundingPrice() {
        return settings.get().foundingPrice();
    }

    /**
     * @return the current rename price
     */
    public double renamePrice() {
        return settings.get().renamePrice();
    }

    /**
     * Checks whether the actor could found a company of that name, without founding it or charging anything.
     *
     * @param actor player who wants to found a company
     * @param name  name of the company
     * @return the name, or why the founding is refused
     */
    public CompletableFuture<ActionResult<String>> foundable(UUID actor, String name) {
        return CompletableFuture.supplyAsync(() -> checkFoundable(actor, name), executor);
    }

    /**
     * Founds a company with the actor as owner and charges the founding price. Integrations may refuse it through
     * {@link PreCompanyCreateEvent} before the founder is charged, which only fires while the founder is online on
     * this server. A company that could not be saved refunds the price.
     *
     * @param actor player who founds the company
     * @param name  name of the company
     * @return the id of the new company, or why it was not founded
     */
    public CompletableFuture<ActionResult<Integer>> found(UUID actor, String name) {
        return CompletableFuture.supplyAsync(() -> foundNow(actor, name), executor);
    }

    private ActionResult<String> checkFoundable(UUID actor, String name) {
        if (name.length() >= MAX_NAME_LENGTH) return invalidLength();
        var current = companyData
                .retrievePlayerCompany(players.apply(actor))
                .asFuture()
                .join();
        if (current.isPresent()) return ActionResult.refusedActionBar("error.alreadyMember");
        var company = companyData.retrieveCompanyByName(name).asFuture().join();
        if (company.isPresent()) return ActionResult.refusedActionBar(NAME_USED);
        return ActionResult.done(name);
    }

    private ActionResult<Integer> foundNow(UUID actor, String name) {
        var checked = checkFoundable(actor, name);
        if (checked instanceof ActionResult.Refused<String>(var failure)) return ActionResult.refused(failure);
        var founder = players.apply(actor);
        var price = foundingPrice();
        var refusal = onMain(() -> online.apply(actor)
                .flatMap(player -> changes.mayFound(player, name, price))
                .or(() -> wallet.withdraw(founder, price) ? Optional.empty() : Optional.of(insufficient(price))));
        if (refusal.isPresent()) return ActionResult.refused(refusal.get());
        var id = companyData
                .submitCompanyCreation(name, settings.get().joining().defaultMode())
                .asFuture()
                .exceptionally(err -> {
                    logger.log(Level.SEVERE, "Something went wrong", err);
                    return -1;
                })
                .join();
        if (id == -1) {
            onMain(() -> {
                wallet.deposit(founder, price);
                return null;
            });
            return ActionResult.refusedActionBar(SAVE_FAILED);
        }
        companyData
                .submitMemberUpdate(CompanyMember.forCompanyId(id, founder).addPermission(CompanyPermission.OWNER))
                .asFuture()
                .join();
        changes.founded(id, founder);
        return ActionResult.done(id);
    }

    /**
     * Checks whether the actor could rename their company, without renaming it or charging anything. Only the owner
     * renames.
     *
     * @param actor owner who wants to rename the company
     * @param name  new name
     * @return the company, the price and the treasury balance when the rename may be paid from it, or why the rename
     *         is refused
     */
    public CompletableFuture<ActionResult<RenameQuote>> renamable(UUID actor, String name) {
        return CompletableFuture.supplyAsync(
                () -> switch (checkRenamable(actor, name)) {
                    case ActionResult.Refused<CompanyProfile>(var failure) -> ActionResult.refused(failure);
                    case ActionResult.Done<CompanyProfile>(var company) ->
                        ActionResult.done(new RenameQuote(company, renamePrice(), treasuryBalance(company)));
                },
                executor);
    }

    /**
     * Renames the company of the actor, who has to be its owner. The owner pays the rename price, or the treasury
     * when the server allows it. A name taken meanwhile gives the price back.
     *
     * @param actor   owner who renames the company
     * @param name    new name
     * @param payment who pays the price
     * @return the company before the rename, or why it was not renamed
     */
    public CompletableFuture<ActionResult<CompanyProfile>> rename(UUID actor, String name, Payment payment) {
        return CompletableFuture.supplyAsync(
                () -> switch (checkRenamable(actor, name)) {
                    case ActionResult.Refused<CompanyProfile> refused -> refused;
                    case ActionResult.Done<CompanyProfile>(var company) ->
                        payment == Payment.TREASURY
                                ? renameFromTreasury(actor, company, name)
                                : renamePaid(actor, company, name);
                },
                executor);
    }

    private ActionResult<CompanyProfile> checkRenamable(UUID actor, String name) {
        if (name.length() >= MAX_NAME_LENGTH) return invalidLength();
        var optProfile = companyData
                .retrievePlayerCompanyProfile(players.apply(actor))
                .asFuture()
                .join();
        if (optProfile.isEmpty()) return ActionResult.refusedActionBar("error.noMember");
        var profile = optProfile.get();
        var owner = profile.member(actor)
                .map(member -> member.hasPermissions(CompanyPermission.OWNER))
                .orElse(false);
        if (!owner) return ActionResult.refusedActionBar("error.noOwner");
        var company = companyData.retrieveCompanyByName(name).join();
        if (company == null) return ActionResult.refusedActionBar(SAVE_FAILED);
        if (company.isPresent()) return ActionResult.refusedActionBar(NAME_USED);
        return ActionResult.done(profile);
    }

    private Optional<BigDecimal> treasuryBalance(CompanyProfile company) {
        var treasurySettings = treasury.settings();
        if (!treasurySettings.enabled() || !treasurySettings.payRename()) return Optional.empty();
        if (!(renamePrice() > 0)) return Optional.empty();
        return Optional.of(treasury.treasury(company.id()).join().balance());
    }

    private ActionResult<CompanyProfile> renamePaid(UUID actor, CompanyProfile company, String name) {
        var owner = players.apply(actor);
        var price = renamePrice();
        if (!onMain(() -> wallet.withdraw(owner, price))) return ActionResult.refused(insufficient(price));
        var renamed = companyData
                .updateCompanyName(company, name)
                .asFuture()
                .exceptionally(err -> {
                    logger.log(Level.SEVERE, "Something went wrong", err);
                    return false;
                })
                .join();
        if (!renamed) {
            onMain(() -> {
                wallet.deposit(owner, price);
                return null;
            });
            return ActionResult.refusedActionBar(NAME_USED);
        }
        changes.renamed(company.id(), company.name(), actor, false);
        return ActionResult.done(company);
    }

    private ActionResult<CompanyProfile> renameFromTreasury(UUID actor, CompanyProfile company, String name) {
        var treasurySettings = treasury.settings();
        if (!treasurySettings.enabled() || !treasurySettings.payRename()) {
            return ActionResult.refusedActionBar("company.bank.error.disabled");
        }
        var price = BigDecimal.valueOf(renamePrice());
        var result = treasury.spend(company, actor, price, TreasuryKind.RENAME, name, () -> companyData
                        .updateCompanyName(company, name)
                        .asFuture())
                .join();
        return switch (result.outcome()) {
            case DONE -> {
                changes.renamed(company.id(), company.name(), actor, false);
                yield ActionResult.done(company);
            }
            case PURCHASE_FAILED -> ActionResult.refusedActionBar(NAME_USED);
            case TREASURY_FUNDS -> ActionResult.refusedActionBar("company.bank.error.insufficient");
            case DISABLED -> ActionResult.refusedActionBar("company.bank.error.disabled");
            default -> ActionResult.refusedActionBar(SAVE_FAILED);
        };
    }

    private CommandFailure insufficient(double price) {
        return CommandFailure.actionBar(
                "error.insufficientCurrency",
                Replacement.create("currency", wallet.currencyName()),
                Replacement.create("amount", price));
    }

    private static <T> ActionResult<T> invalidLength() {
        return ActionResult.refusedChat(
                "error.invalidLength", Placeholder.unparsed("max", String.valueOf(MAX_NAME_LENGTH)));
    }

    private <T> T onMain(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, mainThread).join();
    }

    /**
     * Who pays the price of a rename.
     */
    public enum Payment {
        /**
         * The owner pays from their own money.
         */
        PLAYER,
        /**
         * The treasury of the company pays, when the server allows it.
         */
        TREASURY
    }

    /**
     * What a rename would cost.
     *
     * @param company         the company to rename
     * @param price           the rename price
     * @param treasuryBalance the balance of the treasury when the rename may be paid from it, empty otherwise
     */
    public record RenameQuote(CompanyProfile company, double price, Optional<BigDecimal> treasuryBalance) {}
}
