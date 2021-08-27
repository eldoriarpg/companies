package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Player extends EldoCommand {
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;

    public Player(Plugin plugin, ACompanyData companyData, Configuration configuration) {
        super(plugin);
        this.companyData = companyData;
        this.configuration = configuration;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = getPlugin().getServer().getPlayer(args[0]);

        if (player == null) {
            messageSender().sendError(sender, "Unkown Player");
            return true;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optCompany -> {
                    if (optCompany.isEmpty()) {
                        messageSender().sendError(sender, "This player is not part of a company");
                        return;
                    }

                    var companyProfile = optCompany.get();
                    audiences.sender(sender).sendMessage(miniMessage.parse(localizer().localize(companyProfile.asExternalProfileComponent(configuration))));
                });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompleteUtil.completeOnlinePlayers(args[0]);
        }
        return Collections.emptyList();
    }
}
