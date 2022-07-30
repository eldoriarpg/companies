package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.util.Colors;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageChannel;
import de.eldoria.eldoutilities.messages.MessageType;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Leave extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Set<UUID> leaves = new HashSet<>();
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage;

    public Leave(Plugin plugin, ACompanyData companyData, AOrderData orderData) {
        super(plugin,
                CommandMeta.builder("leave")
                        .build());
        this.companyData = companyData;
        this.orderData = orderData;
        audiences = BukkitAudiences.create(plugin);
        miniMessage = MiniMessage.miniMessage();
    }

    public void leave(Player player) {
        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    if (profile.member(player).get().isOwner()) {
                        companyData.submitCompanyPurge(profile);
                        orderData.submitCompanyOrdersPurge(profile).join();
                        messageSender().sendMessage(player, "company.leave.disbanded");
                        plugin().getServer().getPluginManager().callEvent(new CompanyDisbandEvent(optProfile.get()));
                        return;
                    }
                    companyData.submitMemberUpdate(profile.member(player).get().kick()).join();
                    plugin().getServer().getPluginManager().callEvent(new CompanyLeaveEvent(optProfile.get(), player));
                    messageSender().sendLocalizedMessage(player, "company.leave.left");
                });
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if (!arguments.isEmpty() && "confirm".equalsIgnoreCase(arguments.asString(0))) {
            if (leaves.remove(player.getUniqueId())) {
                leave(player);
                return;
            }
            messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.noConfirm");
            return;
        }

        companyData.retrievePlayerCompanyProfile(player)
                .whenComplete(optProfile -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendLocalized(MessageChannel.ACTION_BAR, MessageType.ERROR, player, "error.noMember");
                        return;
                    }
                    var profile = optProfile.get();
                    leaves.add(player.getUniqueId());
                    var composer = MessageComposer.create().text("<%s>", Colors.NEUTRAL);
                    if (profile.member(player).get().isOwner()) {
                        composer.localeCode("company.leave.confirmOwner");
                    } else {
                        composer.localeCode("company.leave.confirm");
                    }
                    composer.text("<click:run_command:/company leave confirm><%s>[", Colors.REMOVE).localeCode("words.confirm").text("</click>");
                    audiences.player(player).sendMessage(miniMessage.deserialize(composer.buildLocalized(localizer())));
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        if (leaves.contains(player.getUniqueId())) {
            return TabCompleteUtil.complete(arguments.asString(0), "confirm");
        }
        return Collections.emptyList();
    }
}
