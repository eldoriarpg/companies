package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class MessageBlock extends EldoCommand {
    private IMessageBlockerService messagePreservingService;

    public MessageBlock(Plugin plugin, IMessageBlockerService messagePreservingService) {
        super(plugin);
        this.messagePreservingService = messagePreservingService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args[0].equalsIgnoreCase("block")) {
            messagePreservingService.blockPlayer(getPlayerFromSender(sender));
        }
        if ("unblock".equalsIgnoreCase(args[0])) {
            messagePreservingService.unblockPlayer(getPlayerFromSender(sender));
        }
        return true;
    }
}
