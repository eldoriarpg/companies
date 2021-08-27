package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Render extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;
    private final BukkitAudiences audiences;
    private final IMessageBlockerService messageBlocker;

    public Render(Plugin plugin, Query query, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("render")
                .build());
        this.query = query;
        audiences = BukkitAudiences.create(plugin);
        this.messageBlocker = messageBlocker;
    }

    public void renderSearch(Player player) {
        var playerSearch = query.getPlayerSearch(player);
        audiences.player(player).sendMessage(playerSearch.asComponent(localizer()));
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        renderSearch(player);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Collections.emptyList();
    }
}
