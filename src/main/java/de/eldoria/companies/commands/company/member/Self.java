package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.companies.services.messages.IMessageBlockerService;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class Self extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final IMessageBlockerService messageBlocker;

    public Self(Plugin plugin, ACompanyData companyData, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("self").build());
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .whenComplete((optProfile, err) -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.SUBTITLE,
                                MessageType.ERROR,player, "error.noMember");
                        return;
                    }
                    messageBlocker.blockPlayer(player);
                    var builder = MessageComposer.create().text("<%s>", Colors.HEADING).localeCode("company.member.members").text(":").newLine();

                    List<String> members = new ArrayList<>();
                    var self = optProfile.get().member(player).get();

                    for (var member : optProfile.get().members()) {
                        var mem = member.player();
                        if (mem == null) continue;
                        var hover = MessageComposer.create();

                        hover.text(member.statusComponent());

                        if (!member.permissions().isEmpty()) {
                            var permissions = member.permissions().stream()
                                    .map(perm -> "  " + perm.name().toLowerCase(Locale.ROOT))
                                    .collect(Collectors.toList());
                            hover.newLine().text("<%s>", Colors.HEADING).localeCode("words.permissions").text(":").newLine()
                                    .text("<%s>", Colors.ACTIVE).text(permissions, ", ");
                        }
                        var nameComp = MessageComposer.create().text("<hover:show_text:'%s'>%s</hover>", hover.build(), mem.getName());

                        if (self.hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
                            nameComp = nameComp.space().text("<click:run_command:/company permission %s><%s>[", mem.getName(), Colors.MODIFY).localeCode("words.permissions").text("]</click>");
                        }
                        members.add(nameComp.build());
                    }
                    builder.text(members);
                    if (messageBlocker.isBlocked(player)) {
                        builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                    }
                    messageBlocker.announce(player, "[x]");
                    builder.prependLines(25);
                    audiences.player(player).sendMessage(miniMessage.parse(localizer().localize(builder.build())));
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        return IPlayerTabExecutor.super.onTabComplete(player, alias, args);
    }
}
