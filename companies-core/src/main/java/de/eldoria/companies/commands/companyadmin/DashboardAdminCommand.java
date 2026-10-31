/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.KnownPlayers;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.dashboard.Dashboard;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

import java.net.URI;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

/**
 * {@code /companyadmin dashboard}: the web dashboard of this server.
 * <ul>
 *     <li>{@code status} shows whether the dashboard is enabled, the connection, the gateway with the protocol and the
 *     version status it reported, the kind of key and when it rotates, deprecation notices and the sessions. The key
 *     itself is never shown;</li>
 *     <li>{@code revoke <player>} ends every dashboard session of a player;</li>
 *     <li>{@code key <key>} stores the Lyna dashboard key or the server token of a self-hosted gateway for the whole
 *     network. The command line with the key is kept out of the server log.</li>
 * </ul>
 * They work from the console as well.
 */
@Subcommand("dashboard")
@Permission(Admin.DASHBOARD)
public final class DashboardAdminCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public DashboardAdminCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the state of the dashboard.
     *
     * @param sender sender of the command
     */
    @Executes("status")
    public void status(CommandSender sender) {
        deps.dashboard()
                .status()
                .thenAccept(status -> deps.messages().sendMessage(sender, describe(status, this::localize)))
                .exceptionally(error -> failed(sender, error));
    }

    /**
     * Writes the state of the dashboard, one line per part that is known.
     *
     * @param status the state
     * @param localize turns a message key into the text of the plugin's language
     * @return the text to send
     */
    static MenuText describe(Dashboard.Status status, UnaryOperator<String> localize) {
        var text = MenuText.create()
                .text("<heading>")
                .locale("companyadmin.dashboard.status.title")
                .newLine()
                .locale(
                        "companyadmin.dashboard.status.connection",
                        Values.text(
                                "enabled",
                                localize.apply(
                                        status.enabled()
                                                ? "companyadmin.dashboard.status.on"
                                                : "companyadmin.dashboard.status.off")),
                        Values.text(
                                "state",
                                status.state().name().toLowerCase(Locale.ROOT).replace('_', ' ')),
                        Values.text(
                                "gateway",
                                status.gateway()
                                        .map(DashboardAdminCommand::address)
                                        .orElse("-")));
        status.welcome().ifPresent(welcome -> text.newLine()
                .locale(
                        "companyadmin.dashboard.status.gateway",
                        Values.text(
                                "kind",
                                welcome.gateway()
                                        .name()
                                        .toLowerCase(Locale.ROOT)
                                        .replace('_', '-')),
                        Values.number("protocol", welcome.protocol()),
                        Values.text("version", welcome.version().status().name().toLowerCase(Locale.ROOT))));
        if (status.keyKind().isPresent()) {
            text.newLine()
                    .locale(
                            "companyadmin.dashboard.status.key",
                            Values.text(
                                    "key_kind",
                                    Dashboard.describe(status.keyKind().get())),
                            Values.text("refresh", status.refreshAt().orElse("-")));
        } else {
            text.newLine().locale("companyadmin.dashboard.status.noKey");
        }
        status.problem().ifPresent(problem -> text.newLine()
                .locale("companyadmin.dashboard.status.problem", Values.text("problem", problem)));
        if (!status.notices().isEmpty()) {
            text.newLine().locale("dashboard.notice", Values.text("notice", String.join("\n", status.notices())));
        }
        return text.newLine()
                .locale(
                        "companyadmin.dashboard.status.sessions",
                        Values.number("sessions", status.sessions()),
                        Values.number("subscriptions", status.subscriptions()));
    }

    private static String address(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority() + (uri.getPath() == null ? "" : uri.getPath());
    }

    /**
     * Ends every dashboard session of a player.
     *
     * @param sender sender of the command
     * @param player name of the player
     */
    @Executes("revoke")
    public void revoke(CommandSender sender, @KnownPlayers String player) {
        var target = deps.plugin().getServer().getOfflinePlayerIfCached(player);
        if (target == null) throw CommandFailure.of("error.unknownPlayer");
        deps.dashboard()
                .endSessions(target.getUniqueId(), CloseReason.REVOKED)
                .thenAccept(count -> {
                    deps.adminLog()
                            .entry(sender, "dashboard revoke")
                            .with("player", target.getName())
                            .with("uuid", target.getUniqueId())
                            .write();
                    deps.messages()
                            .sendMessage(
                                    sender,
                                    "companyadmin.dashboard.revoked",
                                    Values.number("count", count),
                                    Values.text("player", player));
                })
                .exceptionally(error -> failed(sender, error));
    }

    /**
     * Stores the dashboard key of the network.
     *
     * @param sender sender of the command
     * @param key    the Lyna dashboard key or the server token
     */
    @Executes("key")
    public void key(CommandSender sender, @StringArg(StringArgType.GREEDY) String key) {
        if (key.isBlank()) throw CommandFailure.of("companyadmin.dashboard.error.emptyKey");
        deps.dashboard()
                .enterKey(key)
                .thenAccept(stored -> {
                    deps.adminLog().entry(sender, "dashboard key").write();
                    deps.messages()
                            .sendMessage(
                                    sender,
                                    "companyadmin.dashboard.keyStored",
                                    Values.text("kind", Dashboard.describe(stored.kind())));
                })
                .exceptionally(error -> {
                    deps.plugin()
                            .getLogger()
                            .severe("Could not store the dashboard key: "
                                    + error.getClass().getSimpleName());
                    deps.messages().sendError(sender, "companyadmin.dashboard.error.failed");
                    return null;
                });
    }

    private String localize(String key) {
        return ILocalizer.getPluginLocalizer(deps.plugin()).localize(key);
    }

    private Void failed(CommandSender sender, Throwable error) {
        deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change the dashboard", error);
        deps.messages().sendError(sender, "companyadmin.dashboard.error.failed");
        return null;
    }
}
