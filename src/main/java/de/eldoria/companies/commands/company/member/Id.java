package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.data.repository.ACompanyData;
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
import org.jetbrains.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Id extends AdvancedCommand implements IPlayerTabExecutor {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
    private final ACompanyData companyData;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final IMessageBlockerService messageBlocker;

    public Id(Plugin plugin, ACompanyData companyData, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("id").build());
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        var companyId = args.asInt(0);

        companyData.retrieveCompanyById(companyId)
                .asFuture()
                .thenAccept(optSimple -> {
                    if (optSimple.isEmpty()) {
                        messageSender().sendError(player, "error.unknownCompany");
                        return;
                    }
                    messageBlocker.blockPlayer(player);
                    var optProfile = companyData.retrieveCompanyProfile(optSimple.get()).asFuture().join().get();
                    var builder = MessageComposer.create().text("<%s>", Colors.HEADING).localeCode("company.member.members").text(":").newLine();
                    List<String> members = new ArrayList<>();

                    for (var member : optProfile.members()) {
                        var mem = member.player();
                        if (mem == null) continue;
                        var hover = MessageComposer.create();
                        hover.text(member.statusComponent());

                        var nameComp = MessageComposer.create().space(2).text("<hover:show_text:%s><%s>%s</hover>", hover.build(), Colors.VALUE, mem.getName());
                        members.add(nameComp.build());
                    }
                    builder.text(members);
                    if (messageBlocker.isBlocked(player)) {
                        builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</click>");
                    }
                    messageBlocker.announce(player, "[x]");
                    builder.prependLines(25);
                    audiences.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.build())));
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        return IPlayerTabExecutor.super.onTabComplete(player, alias, args);
    }
}
