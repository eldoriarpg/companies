package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class Member extends EldoCommand {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
    private final CompanyData companyData;
    private final BukkitAudiences audiences;

    public Member(Plugin plugin, CompanyData companyData) {
        super(plugin);
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (denyConsole(sender)) return true;
        var player = getPlayerFromSender(sender);

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendMessage(sender, "You are not part of a company.");
                        return;
                    }
                    var builder = Component.text()
                            .append(Component.text("Company Members:")).append(Component.newline());

                    List<Component> members = new ArrayList<>();
                    var self = optProfile.get().member(player).get();

                    for (var member : optProfile.get().members()) {
                        var mem = member.player();
                        if (mem == null) return;
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
                            hoverBuilder.append(Component.text("Permissions: "))
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
        return true;
    }
}
