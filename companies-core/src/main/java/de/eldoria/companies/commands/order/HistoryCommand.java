/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.order;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.menus.HistoryEntryMenu;
import de.eldoria.companies.menus.HistoryListMenu;
import de.eldoria.companies.menus.HistoryNavigation;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.LongArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * {@code /order history [page]} and {@code /order history info <entry>}: how the sender's orders ended. The entries
 * never name the company that worked on an order.
 */
@Subcommand("history")
@PlayerOnly
public final class HistoryCommand implements HistoryNavigation {
    /**
     * Entries on one page.
     */
    static final int PAGE_SIZE = 10;

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public HistoryCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the first page.
     *
     * @param sender sender of the command
     */
    @Executes
    public void history(CommandSender sender) {
        history(sender, 1);
    }

    /**
     * Shows a page.
     *
     * @param sender sender of the command
     * @param page   page, starting at 1
     */
    @Executes
    public void history(CommandSender sender, @IntArg(min = 1) int page) {
        page(Senders.player(sender), page);
    }

    /**
     * Shows one entry of the sender's history.
     *
     * @param sender sender of the command
     * @param entry  id of the entry
     */
    @Executes("info")
    public void info(CommandSender sender, @LongArg(min = 1) long entry) {
        entry(Senders.player(sender), entry, 1);
    }

    /**
     * An entry only when it is an owner entry of the player.
     *
     * @param entry  the entry
     * @param player the asking player
     * @return the entry, or empty when it belongs to someone else or to a company
     */
    static Optional<HistoryEntry> ownedBy(Optional<HistoryEntry> entry, UUID player) {
        return entry.filter(found -> found.ownerSide().map(player::equals).orElse(false));
    }

    @Override
    public void page(Player viewer, int page) {
        var services = deps.services();
        var number = Math.max(1, page);
        var menu = new HistoryListMenu(
                deps.messages(), services.economy(), this, MenuText.create().locale("history.owner.title"));
        deps.information()
                .history()
                .retrieveOwnerHistory(viewer.getUniqueId(), number, PAGE_SIZE)
                .thenAcceptAsync(
                        entries -> services.menus()
                                .show(viewer, menu, HistoryListMenu.Model.of(number, entries, PAGE_SIZE)),
                        Threading.mainThread(deps.plugin()))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not show the order history", err);
                    return null;
                });
    }

    @Override
    public void entry(Player viewer, long entry, int returnPage) {
        var services = deps.services();
        var menu = new HistoryEntryMenu(deps.messages(), services.economy(), this);
        deps.information()
                .history()
                .retrieveEntry(entry)
                .thenAcceptAsync(
                        found -> {
                            var own = ownedBy(found, viewer.getUniqueId());
                            if (own.isEmpty()) {
                                deps.messages().sendErrorActionBar(viewer, "history.error.unknownEntry");
                                return;
                            }
                            services.menus()
                                    .show(viewer, menu, new HistoryEntryMenu.Model(own.get(), Map.of(), returnPage));
                        },
                        Threading.mainThread(deps.plugin()))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not show a history entry", err);
                    return null;
                });
    }
}
