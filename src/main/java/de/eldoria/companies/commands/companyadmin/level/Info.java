package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.commands.executor.ITabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Info extends AdvancedCommand implements IPlayerTabExecutor {
    private final MiniMessage miniMessage = MiniMessage.get();
    private final BukkitAudiences audiences;
    private final Configuration configuration;
    private final IMessageBlockerService messageBlocker;

    public Info(Plugin plugin, Configuration configuration, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("info")
                .addArgument("level", true)
                .build());
        audiences = BukkitAudiences.create(plugin);
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    public void show(Player player, CompanyLevel level) {
        messageBlocker.blockPlayer(player);
        var cmd = "/companyadmin level";
        var edit = cmd + " edit " + level.level();
        var builder = MessageComposer.create()
                .localeCode("Level").text(" %s ", level.level())
                .text("<click:suggest_command:%s move %s >[", cmd, level.level()).localeCode("move").text("]</click>")
                .newLine()
                .localeCode("Name").text(": %s ", level.levelName())
                .text("<click:suggest_command:%s name >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .localeCode("Requirements").newLine()
                .space(2).localeCode("Order count").text(": %s ", level.requirement().orderCount())
                .text("<click:suggest_command:%s order_count >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .space(2).localeCode("Member Count").text(": %s ", level.requirement().memberCount())
                .text("<click:suggest_command:%s member_count >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .space(2).localeCode("Earned money").text(": %s ", level.requirement().earnedMoney())
                .text("<click:suggest_command:%s earned_money >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .space(2).localeCode("Delivered Items").text(": %s ", level.requirement().deliveredItems())
                .text("<click:suggest_command:%s delivered_items >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .localeCode("Limits").newLine()
                .space(2).localeCode("Max Members").text(": %s", level.settings().maxMembers())
                .text("<click:suggest_command:%s max_members >[", edit).localeCode("edit").text("]</click>")
                .newLine()
                .space(2).localeCode("Max Orders").text(": %s", level.settings().maxOrders())
                .text("<click:suggest_command:%s max_orders >[", edit).localeCode("edit").text("]</click>");
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        messageBlocker.announce(player, "[x]");
        builder.fillLines();
        audiences.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.build())));
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var levelNr = arguments.asInt(0);

        var optLevel = configuration.companySettings().level(levelNr);
        if (optLevel.isEmpty()) {
            messageSender().sendError(player, "Invalid level");
            return;
        }

        show(player, optLevel.get());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        var args = arguments.asArray();
        if (args.length == 1) {
            if (args[0].isEmpty()) {
                return Collections.singletonList("<source>");
            }
            return TabCompleteUtil.completeInt(args[0], 1, configuration.companySettings().level().size(), localizer());
        }
        return Collections.emptyList();
    }
}
