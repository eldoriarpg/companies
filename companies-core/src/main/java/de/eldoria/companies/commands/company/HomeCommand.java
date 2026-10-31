/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.areas.AreaHome;
import de.eldoria.companies.areas.AreaServices;
import de.eldoria.companies.areas.HomeTeleports;
import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.area.CompanyHome;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.company.CompanyAreasChangeEvent;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.AsyncEvents;
import de.eldoria.companies.util.Permission;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /company home} and {@code /company sethome}: the home of the player's company. Any member with
 * {@code companies.company.home} teleports there, with the warm-up, cooldown and cost of {@code areas.yml}; a home on
 * another server is reached through the proxy. Setting the home needs {@code manage_areas}; a company with areas sets
 * it inside one of them while {@code homeInsideArea} is on.
 */
public final class HomeCommand {
    private static final String PREFIX = "company.home.";

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public HomeCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Teleports the player to their company's home.
     *
     * @param sender sender of the command
     */
    public void home(CommandSender sender) {
        var player = Senders.player(sender);
        if (!player.hasPermission(Permission.Company.HOME)) {
            deps.messages().sendError(player, PREFIX + "error.permission");
            return;
        }
        run(
                player,
                () -> {
                    var company = company(player);
                    var home = areas().homes()
                            .home(company.id())
                            .orElseThrow(() -> CommandFailure.of(PREFIX + "error.none"));
                    return new Destination(company, home);
                },
                destination -> Threading.mainThread(deps.plugin()).execute(() -> start(player, destination)));
    }

    /**
     * Sets the home of the player's company where the player stands.
     *
     * @param sender sender of the command
     */
    public void setHome(CommandSender sender) {
        var player = Senders.player(sender);
        var at = player.getLocation();
        Set<String> here = new HashSet<>();
        for (var area : areas().providers().areasAt(at)) here.add(area.toString());
        var home = new CompanyHome(
                0,
                areas().node(),
                areas().proxyServer().get(),
                at.getWorld().getName(),
                at.getX(),
                at.getY(),
                at.getZ(),
                at.getYaw(),
                at.getPitch(),
                player.getUniqueId(),
                Times.now());
        run(
                player,
                () -> {
                    var company = company(player);
                    var member = company.member(player.getUniqueId());
                    if (member.isEmpty() || !member.get().hasPermission(CompanyPermission.MANAGE_AREAS)) {
                        throw CommandFailure.of(PREFIX + "error.manage");
                    }
                    var owned = areas().areas().ofCompany(company.id()).stream()
                            .filter(area -> areas().providers().available(area.provider()))
                            .toList();
                    var inside = owned.stream().anyMatch(area -> here.contains(area.provider() + ":" + area.ref()));
                    if (!AreaHome.allowed(deps.configuration().areaSettings(), !owned.isEmpty(), inside)) {
                        throw CommandFailure.of(PREFIX + "error.outside");
                    }
                    var stored = new CompanyHome(
                            company.id(),
                            home.node(),
                            home.server(),
                            home.world(),
                            home.x(),
                            home.y(),
                            home.z(),
                            home.yaw(),
                            home.pitch(),
                            home.setBy(),
                            home.setAt());
                    if (!areas().homes().set(stored)) throw CommandFailure.of(PREFIX + "error.failed");
                    AsyncEvents.fire(
                            deps.plugin(), new CompanyAreasChangeEvent(company, CompanyAreasChangeEvent.Change.HOME));
                    return stored;
                },
                stored -> deps.messages()
                        .sendMessage(
                                player,
                                PREFIX + "set",
                                Values.text("world", stored.world()),
                                Values.text(
                                        "position",
                                        String.format(
                                                Locale.ROOT, "%.0f %.0f %.0f", stored.x(), stored.y(), stored.z()))));
    }

    private void start(Player player, Destination destination) {
        if (!player.isOnline()) return;
        switch (areas().teleports().start(player, destination.company(), destination.home())) {
            case HomeTeleports.Start.Started started -> {
                if (started.seconds() > 0) {
                    deps.messages().sendMessage(player, PREFIX + "warmup", Values.number("seconds", started.seconds()));
                }
            }
            case HomeTeleports.Start.Refused refused -> {
                var key =
                        switch (refused.refusal()) {
                            case WAITING -> "error.waiting";
                            case COOLDOWN -> "error.cooldown";
                            case UNREACHABLE -> "error.unreachable";
                            case NO_WORLD -> "error.noWorld";
                        };
                deps.messages()
                        .sendError(
                                player,
                                PREFIX + key,
                                Values.number("seconds", refused.seconds()),
                                Values.text("world", destination.home().world()));
            }
        }
    }

    private CompanyProfile company(Player player) {
        var found = deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .join();
        return Optional.ofNullable(found)
                .flatMap(profile -> profile)
                .orElseThrow(() -> CommandFailure.of(PREFIX + "error.noCompany"));
    }

    private AreaServices areas() {
        return deps.services().areas();
    }

    private <T> void run(Player player, Supplier<T> query, Consumer<T> answer) {
        deps.requireReady();
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAccept(answer)
                .exceptionally(err -> {
                    if (err.getCause() instanceof CommandFailure failure) {
                        failure.sendTo(player, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change a company home", err);
                    deps.messages().sendErrorActionBar(player, PREFIX + "error.failed");
                    return null;
                });
    }

    /**
     * Where a member travels.
     *
     * @param company the member's company
     * @param home    its home
     */
    private record Destination(CompanyProfile company, CompanyHome home) {}
}
