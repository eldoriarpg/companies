package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
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

public class Player extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;

    public Player(Plugin plugin, ACompanyData companyData, Configuration configuration) {
        super(plugin, CommandMeta.builder("player").build());
        this.companyData = companyData;
        this.configuration = configuration;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
    }

    @Override
    public void onCommand(org.bukkit.entity.@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        var target = args.asPlayer(0);

        companyData.retrievePlayerCompanyProfile(target)
                .asFuture()
                .whenComplete((optCompany, err) -> {
                    if (optCompany.isEmpty()) {
                        messageSender().sendError(player, "This player is not part of a company");
                        return;
                    }

                    var companyProfile = optCompany.get();
                    audiences.sender(player).sendMessage(miniMessage.parse(localizer().localize(companyProfile.asExternalProfileComponent(configuration))));
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(org.bukkit.entity.@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        if (args.size() == 1) {
            return TabCompleteUtil.completeOnlinePlayers(args.asString(0));
        }
        return Collections.emptyList();
    }
}
