/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.menus.ConfirmFlow;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.services.CompanyService;
import de.eldoria.companies.util.Permission.Company;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.threading.futures.BukkitFutureResult;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /company create <name>}: founds a company for the founding price after the founder confirmed it.
 * <p>
 * Integrations may refuse the founding before the founder is charged, and learn about the new company once its owner
 * is saved. The checks and the founding are made by {@link CompanyService}.
 */
@Subcommand("create")
@Permission(Company.CREATE)
@PlayerOnly
public final class Create {
    public static final int MAX_NAME_LENGTH = CompanyService.MAX_NAME_LENGTH;
    private final CommandDependencies deps;
    private final ConfirmFlow<String> registrations;

    /**
     * @param deps what the commands need
     */
    public Create(CommandDependencies deps) {
        this.deps = deps;
        this.registrations = ConfirmFlow.create(deps.plugin(), this::found);
    }

    /**
     * Asks the sender to confirm founding a company of that name.
     *
     * @param sender sender of the command
     * @param name   name of the company
     */
    @Executes
    public void create(CommandSender sender, @StringArg(StringArgType.GREEDY) String name) {
        var player = Senders.player(sender);
        var economy = deps.services().economy();
        var menus = deps.services().menus();
        var companies = deps.services().companies();
        Checks.shorterThan(name, MAX_NAME_LENGTH);

        BukkitFutureResult.of(companies.foundable(player.getUniqueId(), name)).whenComplete(result -> {
            switch (result) {
                case ActionResult.Refused<String> refused -> refused.failure().sendTo(player, messages());
                case ActionResult.Done<String> done -> {
                    var question = MenuText.create()
                            .locale(
                                    "company.create.create",
                                    Values.money("amount", economy, companies.foundingPrice()),
                                    Values.text("company_name", name));
                    registrations.ask(menus, player, name, question);
                }
            }
        });
    }

    /**
     * Founds the company the sender asked for.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        registrations.confirm(player);
    }

    /**
     * Drops the founding the sender was asked to confirm.
     *
     * @param sender sender of the command
     */
    @Executes("deny")
    public void deny(CommandSender sender) {
        var player = Senders.player(sender);
        deps.requireReady();
        registrations.cancel(player);
    }

    private void found(Player player, String name) {
        BukkitFutureResult.of(deps.services().companies().found(player.getUniqueId(), name))
                .whenComplete(result -> {
                    switch (result) {
                        case ActionResult.Refused<Integer> refused ->
                            refused.failure().sendTo(player, messages());
                        case ActionResult.Done<Integer> done ->
                            messages().sendMessage(player, "company.create.created");
                    }
                });
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
