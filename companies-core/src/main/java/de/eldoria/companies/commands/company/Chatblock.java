package de.eldoria.companies.commands.company;

import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Chatblock extends AdvancedCommand implements IPlayerTabExecutor {
    private final MessageBlocker messageBlockerService;

    public Chatblock(Plugin plugin, MessageBlocker messageBlockerService) {
        super(plugin, CommandMeta.builder("chatblock")
                .addArgument("state", true)
                .hidden()
                .build());
        this.messageBlockerService = messageBlockerService;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        if (args.asBoolean(0)) {
            messageBlockerService.blockPlayer(player);
        } else {
            messageBlockerService.unblockPlayer(player);
        }
    }
}
