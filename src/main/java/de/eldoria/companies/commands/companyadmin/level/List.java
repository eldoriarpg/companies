package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class List extends EldoCommand {
    private MiniMessage miniMessage = MiniMessage.get();
    private Configuration configuration;
    private BukkitAudiences audiences;

    public List(Plugin plugin, Configuration configuration) {
        super(plugin);
        this.configuration = configuration;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        java.util.List<String> level = new ArrayList<>();

        for (var companyLevel : configuration.companySettings().level()) {
            var info = MessageComposer.create().text("<hover:show_text:%s>%s - %s</hover>", companyLevel.asComponent(), companyLevel.level(), companyLevel.levelName())
                    .text("<click:run_command:/companyadmin level show %s>[", companyLevel.level()).localeCode("info").text("]</click>").space()
                    .text("<click:suggest_command:/companyadmin level move %s >[", companyLevel.level()).localeCode("info").text("]</click>")
                    .build();
            level.add(info);
        }
        var composer = MessageComposer.create().localeCode("Level").text(" <click:suggest_command:/companyadmin level create >[").localeCode("create").text("]</click>").newLine()
                .text(String.join("\n", level));
        audiences.sender(sender).sendMessage(miniMessage.parse(composer.build()));
        return true;
    }
}
