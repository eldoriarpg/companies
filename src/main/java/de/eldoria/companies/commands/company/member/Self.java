package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class Self extends AdvancedCommand implements IPlayerTabExecutor {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
    private final ACompanyData companyData;
    private final BukkitAudiences audiences;

    public Self(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("self").build());
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .whenComplete((optProfile, err) -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendMessage(player, "You are not part of a company.");
                        return;
                    }
                    var builder = Component.text()
                            .append(Component.text("Company Members:")).append(Component.newline());

                    List<Component> members = new ArrayList<>();
                    var self = optProfile.get().member(player).get();

                    for (var member : optProfile.get().members()) {
                        var mem = member.player();
                        if (mem == null) continue;
                        var hoverBuilder = Component.text();

                        if (mem.isOnline()) {
                            hoverBuilder.append(Component.text("Online"));
                        } else {
                            var lastSeen = LocalDateTime.ofInstant(Instant.ofEpochMilli(mem.getLastPlayed()), ZoneId.systemDefault());
                            hoverBuilder.append(Component.text("Seen " + lastSeen.format(FORMATTER)));
                        }

                        if (!member.permissions().isEmpty()) {
                            var permissions = member.permissions().stream()
                                    .map(perm -> Component.text(perm.name().toLowerCase(Locale.ROOT)))
                                    .collect(Collectors.toList());
                            hoverBuilder.append(Component.newline())
                                    .append(Component.text("Permissions: "))
                                    .append(Component.join(Component.text(", "), permissions));
                        }
                        var nameComp = Component.text(mem.getName())
                                .hoverEvent(hoverBuilder.build());

                        if (self.hasPermission(CompanyPermission.MANAGE_PERMISSIONS)) {
                            nameComp = nameComp.append(Component.space())
                                    .append(Component.text("[Permissions]")
                                            .clickEvent(ClickEvent.runCommand("/company permission " + mem.getName())));
                        }
                        members.add(nameComp);
                    }
                    builder.append(Component.join(Component.newline(), members));
                    audiences.player(player).sendMessage(builder.build());
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        return IPlayerTabExecutor.super.onTabComplete(player, alias, args);
    }
}
