/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.PerkIds;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.PerkDefinition;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.company.PerksMenu;
import de.eldoria.companies.perks.PerkService;
import de.eldoria.companies.services.TreasuryService;
import de.eldoria.companies.util.Money;
import de.eldoria.companies.util.Threading;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /company perks}: the perks of the server as the player's company sees them, and buying one.
 * <p>
 * The list is a {@link PerksMenu}, a dialog where the client shows one. {@code buy <perk>} asks to confirm the price,
 * {@code confirm} buys it from the company's treasury. Buying needs {@link CompanyPermission#MANAGE_PERKS} and the
 * perk's minimum level.
 */
@Subcommand("perks")
@PlayerOnly
public final class PerksCommand {
    private static final String PREFIX = "company.perks.";

    private final CommandDependencies deps;
    private final ConfirmFlow<String> purchases;

    /**
     * @param deps what the commands need
     */
    public PerksCommand(CommandDependencies deps) {
        this.deps = deps;
        this.purchases = ConfirmFlow.create(deps.plugin(), this::bought);
    }

    /**
     * Lists the perks.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        var perks = deps.services().perks();
        withCompany(player, company -> {
            var offers = perks.offers(company);
            if (offers.isEmpty()) {
                deps.messages().sendMessage(player, PREFIX + "none");
                return;
            }
            var mayBuy = company.member(player.getUniqueId())
                    .map(member -> member.hasPermission(CompanyPermission.MANAGE_PERKS))
                    .orElse(false);
            var menu = new PerksMenu(
                    deps.messages(),
                    deps.services().treasury()::format,
                    (clicker, offer) -> ask(clicker, offer.perk().id()));
            Threading.mainThread(deps.plugin()).execute(() -> {
                if (player.isOnline()) deps.services().menus().show(player, menu, new PerksMenu.Model(offers, mayBuy));
            });
        });
    }

    /**
     * Asks the sender to confirm buying a perk for their company.
     *
     * @param sender sender of the command
     * @param perk   id of the perk
     */
    @Executes("buy")
    public void buy(CommandSender sender, @PerkIds @StringArg(StringArgType.STRING) String perk) {
        var player = Senders.player(sender);
        deps.requireReady();
        ask(player, perk);
    }

    /**
     * Buys the perk the sender confirmed to buy.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        purchases.confirm(player);
    }

    private void ask(Player player, String id) {
        var perks = deps.services().perks();
        withCompany(player, company -> {
            var offer = perks.offers(company).stream()
                    .filter(found -> found.perk().id().equalsIgnoreCase(id))
                    .findFirst();
            if (offer.isEmpty()) {
                refuse(player, PREFIX + "error.unknown", Values.text("perk", id));
                return;
            }
            var found = offer.get();
            var refusal = refusal(company, player, found);
            if (refusal.isPresent()) {
                refuse(player, refusal.get(), names(found));
                return;
            }
            var question = MenuText.create()
                    .locale(
                            PREFIX + "confirm",
                            Values.text("perk_name", found.perk().name()),
                            Values.text("price", deps.services().treasury().format(found.price())));
            Threading.mainThread(deps.plugin()).execute(() -> {
                if (player.isOnline())
                    purchases.ask(deps.services().menus(), player, found.perk().id(), question);
            });
        });
    }

    /**
     * Closes a dialog that may still wait for the answer to a buy button, then shows the refusal.
     */
    private void refuse(Player player, String key, TagResolver... values) {
        deps.services().menus().close(player).whenComplete((done, err) -> deps.messages()
                .sendErrorActionBar(player, key, values));
    }

    private static Optional<String> refusal(CompanyProfile company, Player player, PerkService.Offer offer) {
        var member = company.member(player.getUniqueId());
        if (member.isEmpty() || !member.get().hasPermission(CompanyPermission.MANAGE_PERKS)) {
            return Optional.of(PREFIX + "error.permission");
        }
        return switch (offer.state()) {
            case OWNED, PAUSED -> Optional.of(PREFIX + "error.owned");
            case LOCKED -> Optional.of(PREFIX + "error.level");
            case AVAILABLE -> Optional.empty();
        };
    }

    private static TagResolver names(PerkService.Offer offer) {
        return TagResolver.resolver(
                Values.text("perk_name", offer.perk().name()), Values.number("level", offer.minLevel()));
    }

    private void bought(Player player, String id) {
        var perks = deps.services().perks();
        withCompany(player, company -> {
            var result = perks.buy(company, player.getUniqueId(), id).join();
            var name = perks.registered(id).map(PerkDefinition::name).orElse(id);
            switch (result.outcome()) {
                case DONE -> deps.messages().sendMessage(player, PREFIX + "bought", Values.text("perk_name", name));
                case UNKNOWN ->
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.unknown", Values.text("perk", id));
                case NO_PERMISSION -> deps.messages().sendErrorActionBar(player, PREFIX + "error.permission");
                case OWNED ->
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.owned", Values.text("perk_name", name));
                case LEVEL ->
                    deps.messages()
                            .sendErrorActionBar(
                                    player,
                                    PREFIX + "error.level",
                                    Values.text("perk_name", name),
                                    Values.number(
                                            "level",
                                            perks.registered(id)
                                                    .map(perks::minLevel)
                                                    .orElse(1)));
                case TREASURY -> {
                    var treasury = deps.services().treasury();
                    var answer = Objects.requireNonNullElse(
                            result.treasury(), TreasuryService.Result.of(TreasuryService.Outcome.FAILED));
                    treasury.explain(answer, Money.currencyName(deps.services().economy()))
                            .failure()
                            .sendTo(player, deps.messages());
                }
            }
        });
    }

    private void withCompany(Player player, Consumer<CompanyProfile> action) {
        CompletableFuture.runAsync(
                        () -> {
                            var company = deps.companyData()
                                    .retrievePlayerCompanyProfile(player)
                                    .asFuture()
                                    .join();
                            if (company.isEmpty()) {
                                deps.messages().sendErrorActionBar(player, PREFIX + "error.noCompany");
                                return;
                            }
                            action.accept(company.get());
                        },
                        Threading.VIRTUAL)
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not handle the perks of a company", err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.failed");
                    return null;
                });
    }
}
