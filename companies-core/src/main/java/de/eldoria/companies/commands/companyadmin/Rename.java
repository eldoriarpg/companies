package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Rename extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;

    public Rename(Plugin plugin, ACompanyData companyData) {
        super(plugin, CommandMeta.builder("rename")
                .withPermission(Permission.Admin.RENAME)
                .addArgument("words.name", true)
                .addArgument("words.name", true)
                .build());
        this.companyData = companyData;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        args.parseQuoted();
        CommandAssertions.invalidArguments(meta(), args);

        companyData.retrieveCompanyByName(args.asString(0))
                .asFuture()
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        messageSender().sendLocalizedError(player, "error.unknownCompany");
                        return;
                    }

                    var other = companyData.retrieveCompanyByName(args.asString(1)).join();

                    if (other == null || other.isEmpty()) {
                        messageSender().sendLocalizedError(player, "error.companyNameUsed");
                        return;
                    }

                    companyData.updateCompanyName(company.get(), args.asString(1));
                    messageSender().sendLocalizedMessage(player, "company.rename.changed");
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        args.parseQuoted();

        if (args.size() == 1) {
            return TabCompleteUtil.completeFreeInput(args.asString(0), 32, "<name>", localizer());
        }

        if (args.size() == 2) {
            return TabCompleteUtil.completeFreeInput(args.asString(1), 32, "<name>", localizer());
        }

        return Collections.emptyList();
    }
}
