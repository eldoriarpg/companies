/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order.search;

import de.eldoria.companies.commands.company.order.Search;
import de.eldoria.companies.commands.company.order.search.query.Clear;
import de.eldoria.companies.commands.company.order.search.query.Execute;
import de.eldoria.companies.commands.company.order.search.query.Material;
import de.eldoria.companies.commands.company.order.search.query.MaterialMatch;
import de.eldoria.companies.commands.company.order.search.query.MaterialSearch;
import de.eldoria.companies.commands.company.order.search.query.Name;
import de.eldoria.companies.commands.company.order.search.query.Order;
import de.eldoria.companies.commands.company.order.search.query.Price;
import de.eldoria.companies.commands.company.order.search.query.Render;
import de.eldoria.companies.commands.company.order.search.query.Size;
import de.eldoria.companies.commands.company.order.search.query.Sorting;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Query extends AdvancedCommand {
    private final Map<UUID, SearchQuery> searches = new HashMap<>();
    private final MessageBlocker messageBlocker;
    private Render render;

    public Query(Plugin plugin, AOrderData orderData, Search search, MessageBlocker messageBlocker) {
        super(plugin);
        this.messageBlocker = messageBlocker;
        var meta = CommandMeta.builder("query")
                .buildSubCommands((commands, builder) -> {
                    render = new Render(plugin, this, messageBlocker);
                    builder.withDefaultCommand(render);
                    commands.add(new Clear(plugin, this));
                    commands.add(new Execute(plugin, this, search, orderData));
                    commands.add(new Material(plugin, this));
                    commands.add(new MaterialMatch(plugin, this));
                    commands.add(new MaterialSearch(plugin, this));
                    commands.add(new Name(plugin, this));
                    commands.add(new Order(plugin, this));
                    commands.add(new Price(plugin, this));
                    commands.add(new Size(plugin, this));
                    commands.add(new Sorting(plugin, this));
                })
                .build();
        meta(meta);
    }

    @Override
    public void commandRoute(CommandSender sender, String label, Arguments args) throws CommandException {
        super.commandRoute(sender, label, args);
        CommandAssertions.player(sender);
        render.renderSearch((Player) sender);
    }

    public SearchQuery getPlayerSearch(Player player) {
        messageBlocker.blockPlayer(player);
        return searches.computeIfAbsent(player.getUniqueId(), key -> new SearchQuery());
    }

    public void reset(Player player) {
        searches.put(player.getUniqueId(), new SearchQuery());
    }
}
