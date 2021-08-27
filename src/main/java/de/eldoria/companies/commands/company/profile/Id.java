package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Id extends EldoCommand {
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;

    public Id(Plugin plugin, ACompanyData companyData, Configuration configuration) {
        super(plugin);
        this.companyData = companyData;
        this.configuration = configuration;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "<name>")) {
            return true;
        }

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
                    var companyProfile = optComp.get();
                    audiences.sender(sender).sendMessage(miniMessage.parse(localizer().localize(companyProfile.asExternalProfileComponent(configuration))));
                });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
