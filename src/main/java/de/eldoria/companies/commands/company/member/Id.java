package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Id extends EldoCommand {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
    private final ACompanyData companyData;
    private final BukkitAudiences audiences;

    public Id(Plugin plugin, ACompanyData companyData) {
        super(plugin);
        this.companyData = companyData;
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var optionalInt = Parser.parseInt(args[0]);
        if (optionalInt.isEmpty()) {
            messageSender().sendError(sender, "Not a number");
            return true;
        }

        companyData.retrieveCompanyById(optionalInt.getAsInt())
                .asFuture()
                .thenApplyAsync(optComp -> {
                    if (optComp.isEmpty()) {
                        messageSender().sendError(sender, "This company does not exist");
                        return Optional.ofNullable((CompanyProfile) null);
                    }
                    return companyData.retrieveCompanyProfile(optComp.get()).asFuture().join();
                })
                .thenAccept(optComp -> {
                    if (optComp.isEmpty()) return;

                    handleProfile(sender, optComp);
                });
        return true;
    }

    private void handleProfile(@NotNull CommandSender sender, Optional<CompanyProfile> optProfile) {
        var builder = Component.text()
                .append(Component.text("Company Members:")).append(Component.newline());

        List<Component> members = new ArrayList<>();

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

            var nameComp = Component.text(mem.getName()).hoverEvent(hoverBuilder.build());
            members.add(nameComp);
        }
        builder.append(Component.join(Component.newline(), members));
        audiences.sender(sender).sendMessage(builder.build());
    }
}
