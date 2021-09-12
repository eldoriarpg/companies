package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.services.messages.IMessageBlockerService;
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

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class Id extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final BukkitAudiences audiences;
    private final IMessageBlockerService messageBlocker;
    private final MiniMessage miniMessage;

    public Id(Plugin plugin, ACompanyData companyData, Configuration configuration, IMessageBlockerService messageBlocker) {
        super(plugin, CommandMeta.builder("id")
                .addArgument("id", true)
                .build());
        this.companyData = companyData;
        this.configuration = configuration;
        audiences = BukkitAudiences.create(plugin);
        this.messageBlocker = messageBlocker;
        miniMessage = MiniMessage.get();
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var optionalInt = arguments.asInt(0);

        companyData.retrieveCompanyById(optionalInt)
                .asFuture()
                .whenComplete((optComp, err) -> {
                    if (err != null) {
                        plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                        return;
                    }
                    if (optComp.isEmpty()) {
                        messageSender().sendError(player, "error.unknownCompany");
                        return;
                    }
                    var optProfile = companyData.retrieveCompanyProfile(optComp.get()).asFuture().join();
                    if (optProfile.isEmpty()) return;
                    var builder = MessageComposer.create().text(optProfile.get().asExternalProfileComponent(configuration));
                    if (messageBlocker.isBlocked(player)) {
                        builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
                    }
                    messageBlocker.announce(player, "[x]");
                    builder.prependLines(25);
                    audiences.sender(player).sendMessage(miniMessage.parse(localizer().localize(builder.build())));
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return Collections.emptyList();
    }
}
