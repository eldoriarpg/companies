package de.eldoria.companies.commands.companyadmin.level;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Info extends AdvancedCommand implements IPlayerTabExecutor {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final BukkitAudiences audiences;
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public Info(Plugin plugin, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("info")
                .addArgument("words.level", true)
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
                .text("<%s>", Colors.HEADING).localeCode("words.level").text(" %s ", level.level())
                .text("<click:suggest_command:'%s move %s '><%s>[", cmd, level.level(), Colors.MODIFY).localeCode("words.move").text("]</click>")
                .newLine()
                .text("<%s>", Colors.NAME).localeCode("words.name").text(": <%s>%s ", Colors.VALUE, level.levelName())
                .text("<click:suggest_command:%s name ><%s>[", edit, Colors.MODIFY).localeCode("words.change").text("]</click>")
                .newLine()
                .text("<%s>", Colors.HEADING).localeCode("level.requirements").newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.orderCount").text(": <%s>%s ", Colors.VALUE, level.requirement().orderCount())
                .text("<click:suggest_command:%s order_count ><%s>[", edit, Colors.MODIFY).localeCode("words.change").text("]</click>")
                .newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.memberCount").text(": <%s>%s ", Colors.VALUE, level.requirement().memberCount())
                .text("<click:suggest_command:%s member_count ><%s>[", edit, Colors.MODIFY).localeCode("words.change").text("]</click>")
                .newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.earnedMoney").text(": <%s>%s ", Colors.VALUE, level.requirement().earnedMoney())
                .text("<click:suggest_command:%s earned_money ><%s>[", edit, Colors.MODIFY).localeCode("words.change").text("]</click>")
                .newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.deliveredItems").text(": <%s>%s ", Colors.VALUE, level.requirement().deliveredItems())
                .text("<click:suggest_command:%s delivered_items ><%s>[", edit, Colors.MODIFY).localeCode("words.change").text("]</click>")
                .newLine()
                .text("<%s>", Colors.HEADING).localeCode("level.limits").newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.maxMember").text(": <%s>%s", Colors.VALUE, level.settings().maxMembers())
                .text("<click:suggest_command:%s max_members ><%s>[", edit, Colors.MODIFY).localeCode("words.change").text("]</click>")
                .newLine()
                .space(2).text("<%s>", Colors.NAME).localeCode("level.maxOrders").text(": <%s>%s", Colors.VALUE, level.settings().maxOrders())
                .text("<click:suggest_command:%s max_orders ><%s>[", edit, Colors.MODIFY).localeCode("words.change").text("]</click>");
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        messageBlocker.announce(player, "[x]");
        builder.prependLines(25);
        audiences.sender(player).sendMessage(miniMessage.deserialize(localizer().localize(builder.build())));
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var levelNr = arguments.asInt(0);

        var optLevel = configuration.companySettings().level(levelNr);
        if (optLevel.isEmpty()) {
            messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "Invalid level");
            return;
        }

        show(player, optLevel.get());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        if (args.sizeIs(1)) {
            if (args.asString(0).isEmpty()) {
                return Collections.singletonList(localizer().localize("words.index"));
            }
            return TabCompleteUtil.completeInt(args.asString(0), 1, configuration.companySettings().level().size());
        }
        return Collections.emptyList();
    }
}
