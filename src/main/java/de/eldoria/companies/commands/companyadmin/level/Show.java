package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Show extends EldoCommand {
    private final MiniMessage miniMessage = MiniMessage.get();
    private final BukkitAudiences audiences;
    private final Configuration configuration;

    public Show(MiniMessage miniMessage, Plugin plugin, Configuration configuration) {
        super(plugin);
        audiences = BukkitAudiences.create(plugin);
        this.configuration = configuration;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<level>")) {
            return true;
        }

        var levelNr = Parser.parseInt(args[0]);

        if (levelNr.isEmpty()) {
            messageSender().sendError(sender, "Invalid level");
            return true;
        }

        var optLevel = configuration.companySettings().level(levelNr.getAsInt());
        if (optLevel.isEmpty()) {
            messageSender().sendError(sender, "Invalid level");
            return true;
        }

        show(sender, optLevel.get());
        return true;
    }

    public void show(CommandSender sender, CompanyLevel level) {
        var cmd = "/companyadmin level";
        var edit = cmd + " edit " + level.level();
        var message = MessageComposer.create()
                .localeCode("Level").text(" %s", level.level())
                .text("<click:suggest_command:%s move %s >[", cmd, level.level()).localeCode("move").text("]</click>")
                .newLine()
                .localeCode("Name").text(": %s", level.levelName())
                .text("<click:suggest_command:%s name >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .localeCode("Requirements").newLine()
                .localeCode("Order count").text(": %s", level.requirement().orderCount())
                .text("<click:suggest_command:%s order_count >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .localeCode("Member Count").text(": %s", level.requirement().memberCount())
                .text("<click:suggest_command:%s member_count >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .localeCode("Earned money").text(": %s", level.requirement().earnedMoney())
                .text("<click:suggest_command:%s earned_money >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .localeCode("Delivered Items").text(": %s", level.requirement().deliveredItems())
                .text("<click:suggest_command:%s delivered_items >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .localeCode("Limits").newLine()
                .localeCode("Max Members").text(": %s", level.settings().maxMembers())
                .text("<click:suggest_command:%s max_members >[", edit).localeCode("edit").text("]</click>")
                .localeCode("Max Orders").text(": %s", level.settings().maxMembers())
                .text("<click:suggest_command:%s max_orders >[", edit).localeCode("edit").text("]</click>")
                .build();

        audiences.sender(sender).sendMessage(miniMessage.parse(localizer().localize(message)));
    }
}
