package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private final MiniMessage miniMessage;

    public Render(Plugin plugin, Query query, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("render")
                .build());
        this.query = query;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
        this.messageBlocker = messageBlocker;
    }

    public void renderSearch(Player player) {
        var playerSearch = query.getPlayerSearch(player);
        var message = MessageComposer.create().text(playerSearch.asComponent());
        if (messageBlocker.isBlocked(player)) {
            message.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>").build();
        }
        message.prependLines(25);
        messageBlocker.announce(player, "[x]");
        audiences.player(player).sendMessage(miniMessage.parse(localizer().localize(message.build())));
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
