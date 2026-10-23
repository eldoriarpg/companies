/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.history.HistorySnapshot;
import de.eldoria.companies.menus.HistoryEntryMenu;
import de.eldoria.companies.menus.HistoryListMenu;
import de.eldoria.companies.menus.HistoryNavigation;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.company.MemberEntry;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.arguments.LongArg;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /company history [page]} and {@code /company history info <entry>}: how the orders of the sender's company
 * ended, for its members. The entries never name the owner of an order.
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
     * Shows one entry of the history of the sender's company.
     *
     * @param sender sender of the command
     * @param entry  id of the entry
     */
    @Executes("info")
    public void info(CommandSender sender, @LongArg(min = 1) long entry) {
        entry(Senders.player(sender), entry, 1);
    }

    /**
     * An entry only when it is an entry of the company.
     *
     * @param entry   the entry
     * @param company id of the asking player's company
     * @return the entry, or empty when it belongs to another company or to an owner
     */
    static Optional<HistoryEntry> ofCompany(Optional<HistoryEntry> entry, int company) {
        return entry.filter(
                found -> found.companySide().map(id -> id == company).orElse(false));
    }

    /**
     * The names of the members an entry lists, as the server last knew them.
     *
     * @param snapshot snapshot of a company entry
     * @param server   server knowing the players
     * @return names by id
     */
    static Map<UUID, String> workerNames(HistorySnapshot snapshot, Server server) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (var worker : snapshot.workers()) {
            names.put(worker.worker(), MemberEntry.nameOf(server.getOfflinePlayer(worker.worker())));
        }
        return names;
    }

    @Override
    public void page(Player viewer, int page) {
        var services = deps.services();
        var number = Math.max(1, page);
        withCompany(viewer, company -> {
            var title = MenuText.create().locale("history.company.title", Values.text("company_name", company.name()));
            var menu = new HistoryListMenu(deps.messages(), services.economy(), this, title);
            var entries = deps.information()
                    .history()
                    .retrieveCompanyHistory(company.id(), number, PAGE_SIZE)
                    .join();
            Threading.mainThread(deps.plugin()).execute(() -> services.menus()
                    .show(viewer, menu, HistoryListMenu.Model.of(number, entries, PAGE_SIZE)));
        });
    }

    @Override
    public void entry(Player viewer, long entry, int returnPage) {
        var services = deps.services();
        var menu = new HistoryEntryMenu(deps.messages(), services.economy(), this);
        withCompany(viewer, company -> {
            var found =
                    ofCompany(deps.information().history().retrieveEntry(entry).join(), company.id());
            if (found.isEmpty()) {
                deps.messages().sendErrorActionBar(viewer, "history.error.unknownEntry");
                return;
            }
            var names = workerNames(found.get().snapshot(), deps.plugin().getServer());
            Threading.mainThread(deps.plugin()).execute(() -> services.menus()
                    .show(viewer, menu, new HistoryEntryMenu.Model(found.get(), names, returnPage)));
        });
    }

    private void withCompany(Player player, Consumer<CompanyProfile> action) {
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAccept(optCompany -> {
                    if (optCompany.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    action.accept(optCompany.get());
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not show the company history", err);
                    return null;
                });
    }
}
