package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Id extends AdvancedCommand implements IPlayerTabExecutor {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
    private final ACompanyData companyData;
    private final BukkitAudiences audiences;

    public Id(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("id").build());
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        var companyId = args.asInt(0);

        companyData.retrieveCompanyById(companyId)
                .asFuture()
                .thenAccept(optSimple -> {
                    if (optSimple.isEmpty()) {
                        messageSender().sendError(player, "This company does not exist");
                        return;
                    }
                    var optProfile = companyData.retrieveCompanyProfile(optSimple.get()).asFuture().join().get();
                    var builder = Component.text()
                            .append(Component.text("Company Members:")).append(Component.newline());

                    List<Component> members = new ArrayList<>();

                    for (var member : optProfile.members()) {
                        var mem = member.player();
                        if (mem == null) continue;
                        var hoverBuilder = Component.text();

                        if (mem.isOnline()) {
                            hoverBuilder.append(Component.text("Online"));
                        } else {
                            var lastSeen = LocalDateTime.ofInstant(Instant.ofEpochMilli(mem.getLastPlayed()), ZoneId.systemDefault());
                            hoverBuilder.append(Component.text("Seen " + lastSeen.format(FORMATTER)));
                        }

                        var nameComp = Component.text(mem.getName()).hoverEvent(hoverBuilder.build());
                        members.add(nameComp);
                    }
                    builder.append(Component.join(Component.newline(), members));
                    audiences.sender(player).sendMessage(builder.build());
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        return IPlayerTabExecutor.super.onTabComplete(player, alias, args);
    }
}
