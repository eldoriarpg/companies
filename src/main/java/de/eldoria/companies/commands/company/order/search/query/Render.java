package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Render extends EldoCommand {
    private final Query query;
    private final BukkitAudiences audiences;

    public Render(Plugin plugin, Query query) {
        super(plugin);
        this.query = query;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        renderSearch(getPlayerFromSender(sender));
        return false;
    }

    public void renderSearch(Player player) {
        var playerSearch = query.getPlayerSearch(player);
        audiences.player(player).sendMessage(playerSearch.asComponent(localizer()));
    }
}
