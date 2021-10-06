package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.companies.util.Colors;
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

import java.util.ArrayList;

public class List extends AdvancedCommand implements IPlayerTabExecutor {
    private final MiniMessage miniMessage = MiniMessage.get();
    private final Configuration configuration;
    private final BukkitAudiences audiences;
    private final IMessageBlockerService messageBlocker;

    public List(Plugin plugin, Configuration configuration, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("list").build());
        this.configuration = configuration;
        audiences = BukkitAudiences.create(plugin);
        this.messageBlocker = messageBlocker;
    }

    public void sendList(Player player) {
        var level = new ArrayList<String>();
        messageBlocker.blockPlayer(player);
        for (var companyLevel : configuration.companySettings().level()) {
            var info = MessageComposer.create().text("<hover:show_text:'%s'><%s>%s - <%s>%s</hover>", companyLevel.asComponent(), Colors.NAME, companyLevel.level(), Colors.VALUE, companyLevel.levelName()).space()
                    .text("<click:run_command:/companyadmin level info %s><%s>[", companyLevel.level(), Colors.SHOW).localeCode("words.info").text("]</click>").space()
                    .text("<click:run_command:/companyadmin level remove %s><%s>[", companyLevel.level(), Colors.REMOVE).localeCode("words.remove").text("]</click>").space()
                    .text("<click:suggest_command:/companyadmin level move %s >[", companyLevel.level(), Colors.MODIFY).localeCode("words.move").text("]</click>")
                    .build();
            level.add(info);
        }
        var builder = MessageComposer.create().text("<%s>", Colors.HEADING).localeCode("words.level").text(" <click:suggest_command:/companyadmin level create ><%s>[", Colors.ADD).localeCode("words.create").text("]</click>").newLine()
                .text(String.join("\n", level));
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        messageBlocker.announce(player, "[x]");
        builder.prependLines(25);

        audiences.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.build())));
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        sendList(player);
    }
}
