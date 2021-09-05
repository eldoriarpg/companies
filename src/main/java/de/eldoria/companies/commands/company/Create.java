package de.eldoria.companies.commands.company;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.localization.Replacement;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.eldoutilities.threading.futures.CompletableBukkitFuture;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Create extends AdvancedCommand implements IPlayerTabExecutor {
    public static final int MAX_NAME_LENGTH = 32;
    private final ACompanyData companyData;
    private final Configuration configuration;
    private final Economy economy;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;
    private final Map<UUID, String> registrations = new HashMap<>();

    public Create(Plugin plugin, ACompanyData companyData, Economy economy, Configuration configuration) {
        super(plugin, CommandMeta.builder("create")
                .addArgument("name", true)
                .build());
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.get();
        this.companyData = companyData;
        this.economy = economy;
        this.configuration = configuration;
    }

    private void createGuild(Player player) {
        var name = registrations.get(player.getUniqueId());

        if (name == null) {
            messageSender().sendError(player, "nothing to confirm");
            return;
        }

        companyData.retrieveCompanyByName(name)
                .whenComplete(company -> {
                    if (company.isPresent()) {
                        messageSender().sendError(player, "This company name is already in use.");
                        return;
                    }

                    CompletableBukkitFuture.supplyAsync(() -> {
                        if (!economy.has(player, configuration.companySettings().foudingPrice())) {
                            return false;
                        }
                        return economy.withdrawPlayer(player, configuration.companySettings().foudingPrice()).type == EconomyResponse.ResponseType.SUCCESS;
                    }).whenComplete(result -> {
                        if (!result) {
                            var fallbackCurr = economy.currencyNameSingular().isBlank() ? "money" : economy.currencyNameSingular();
                            var curr = economy.currencyNamePlural().isBlank() ? fallbackCurr : economy.currencyNamePlural();
                            messageSender().sendError(player, "Not enough " + curr);
                            return;
                        }
                        companyData.submitCompanyCreation(name)
                                .asFuture()
                                .thenApplyAsync(id -> companyData.submitMemberUpdate(CompanyMember.forCompanyId(id, player).addPermission(CompanyPermission.OWNER)));
                        messageSender().sendMessage(player, "Company created.");
                    });
                });
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("confirm".equalsIgnoreCase(arguments.asString(0))) {
            createGuild(player);
            return;
        }
        if ("deny".equalsIgnoreCase(arguments.asString(0))) {
            var name = registrations.remove(player.getUniqueId());
            if (name == null) {
                messageSender().sendError(player, "Nothing to deny");
                return;
            }
            messageSender().sendMessage(player, "Canceled");
            return;
        }

        var name = arguments.join();
        CommandAssertions.invalidLength(name, MAX_NAME_LENGTH);

        companyData.retrieveCompanyByName(name)
                .whenComplete(company -> {
                    if (company.isPresent()) {
                        messageSender().sendError(player, "This company name is already in use.");
                        return;
                    }

                    var composer = MessageComposer.create().text("<%s>", Colors.NEUTRAL).localeCode("Founding a company costs %AMOUNT%. Do you want to found a company with the name %NAME%",
                                    Replacement.create("AMOUNT", String.format("<%s>%s<%s>",
                                            Colors.HEADING, economy.format(configuration.companySettings().foudingPrice()), Colors.NEUTRAL)),
                                    Replacement.create("NAME", String.format("<%s>%s<%s>", Colors.HEADING, name, Colors.NEUTRAL)))
                            .newLine()
                            .text("<%s><click:run_command:/company create confirm><%s>[", Colors.ADD).localeCode("confirm").text("]</click>")
                            .text("<%s><click:run_command:/company create deny><%s>[", Colors.REMOVE).localeCode("deny").text("]</click>");
                    audiences.sender(player).sendMessage(miniMessage.parse(localizer().localize(composer.build())));
                    registrations.put(player.getUniqueId(), name);
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return TabCompleteUtil.completeFreeInput(arguments.join(), 32, "<name>", localizer());
    }
}
