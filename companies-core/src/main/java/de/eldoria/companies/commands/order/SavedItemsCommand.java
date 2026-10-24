/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.SavedItemNames;
import de.eldoria.companies.commands.suggest.SavedItemSources;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.saved.SavedItem;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.util.Permission.Orders;
import net.kyori.adventure.text.event.ClickEvent;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * {@code /order items}: the items a player saved to order them again. {@code save [name]} saves the item in hand,
 * which the player keeps; {@code remove <name>} forgets one. A saved item is added to a draft with
 * {@code /order draft add saved}, and offered when creating an order on the dashboard.
 */
@Subcommand("items")
@Permission(Orders.CREATE)
@PlayerOnly
public final class SavedItemsCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public SavedItemsCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Lists the sender's saved items.
     *
     * @param sender sender of the command
     */
    @Executes
    public void list(CommandSender sender) {
        var player = Senders.player(sender);
        deps.services()
                .savedItems()
                .items(player.getUniqueId())
                .thenAccept(items -> deps.messages().sendMessage(player, list(items)))
                .exceptionally(this::logError);
    }

    /**
     * Saves the item in the sender's main hand under the material's name.
     *
     * @param sender sender of the command
     */
    @Executes("save")
    public void save(CommandSender sender) {
        save(Senders.player(sender), Optional.empty());
    }

    /**
     * Saves the item in the sender's main hand.
     *
     * @param sender sender of the command
     * @param name   the name to save it under
     */
    @Executes("save")
    public void save(CommandSender sender, @StringArg(StringArgType.WORD) String name) {
        save(Senders.player(sender), Optional.of(name));
    }

    /**
     * @param sender sender of the command
     * @param name   name of the saved item
     */
    @Executes("remove")
    public void remove(CommandSender sender, @SavedItemNames @StringArg(StringArgType.WORD) String name) {
        var player = Senders.player(sender);
        deps.services()
                .savedItems()
                .remove(player.getUniqueId(), name)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<String> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case ActionResult.Done<String>(var removed) -> {
                            SavedItemSources.invalidate(player.getUniqueId());
                            deps.messages()
                                    .sendMessage(
                                            player,
                                            MenuText.create()
                                                    .locale("order.items.removed", Values.text("name", removed)));
                        }
                    }
                })
                .exceptionally(this::logError);
    }

    private void save(Player player, Optional<String> name) {
        var hand = player.getInventory().getItemInMainHand().clone();
        deps.services()
                .savedItems()
                .save(player.getUniqueId(), hand, name)
                .thenAccept(result -> {
                    switch (result) {
                        case ActionResult.Refused<SavedItem> refused ->
                            refused.failure().sendTo(player, deps.messages());
                        case ActionResult.Done<SavedItem>(var saved) -> {
                            SavedItemSources.invalidate(player.getUniqueId());
                            deps.messages()
                                    .sendMessage(
                                            player,
                                            MenuText.create()
                                                    .locale(
                                                            "order.items.saved",
                                                            Values.text("name", saved.name()),
                                                            Values.item("item", saved.stack())));
                        }
                    }
                })
                .exceptionally(this::logError);
    }

    private MenuText list(List<SavedItem> items) {
        var text = MenuText.create()
                .text("<heading>")
                .locale(
                        "order.items.list",
                        Values.number("count", items.size()),
                        Values.number(
                                "max",
                                deps.configuration().orderSetting().savedItems().max()));
        if (items.isEmpty()) return text.newLine().text("<inactive><i18n:order.items.empty>");
        for (var index = 0; index < items.size(); index++) {
            var item = items.get(index);
            var name = Keys.of("item", index, "name");
            var stack = Keys.of("item", index, "stack");
            var remove = Keys.of("item", index, "remove");
            text.newLine()
                    .text(
                            "<value><" + name + "></value> <" + stack + "> <" + remove
                                    + "><remove>[<i18n:words.remove>]</remove></" + remove + ">",
                            Values.text(name, item.name()),
                            Values.item(stack, item.stack()),
                            Values.click(remove, ClickEvent.runCommand("/order items remove " + item.name())));
        }
        return text;
    }

    private Void logError(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong with saved items", err);
        return null;
    }
}
