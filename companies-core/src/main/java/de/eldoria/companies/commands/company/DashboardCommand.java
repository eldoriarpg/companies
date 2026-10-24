/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.dashboard.Dashboard;
import de.eldoria.companies.dashboard.sessions.SessionPermissions;
import de.eldoria.companies.data.wrapper.dashboard.StoredSession;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Times;
import de.eldoria.companies.web.api.session.CloseReason;
import de.eldoria.companies.web.api.session.PlayerRef;
import de.eldoria.companies.web.api.session.SessionCreated;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.kyori.adventure.text.event.ClickEvent;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * {@code /company dashboard}: opens the web dashboard.
 * <p>
 * Without a subcommand it asks the gateway for a session and shows its short link, clickable and spelled out, and the
 * six-letter login code in spaced capitals, so a player on a console can type both on a phone. {@code logout} ends
 * every session of the player, {@code sessions} lists them.
 */
@Subcommand("dashboard")
@Permission(de.eldoria.companies.util.Permission.Dashboard.USE)
@PlayerOnly
public final class DashboardCommand {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public DashboardCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Opens a session and shows how to log in.
     *
     * @param sender sender of the command
     */
    @Executes
    public void open(CommandSender sender) {
        var player = Senders.player(sender);
        var dashboard = deps.dashboard();
        dashboard
                .open(new PlayerRef(player.getUniqueId(), player.getName()), SessionPermissions.of(player))
                .thenAccept(opening -> {
                    switch (opening) {
                        case Dashboard.Opening.Created created -> showLogin(player, created.session());
                        case Dashboard.Opening.Refused refused -> refused(player, refused);
                    }
                })
                .exceptionally(error -> failed(player, error));
    }

    private void showLogin(Player player, SessionCreated session) {
        var address = session.link().replaceFirst("^https?://", "");
        var text = MenuText.create()
                .locale(
                        "dashboard.link",
                        Values.click("act_open", ClickEvent.openUrl(session.link())),
                        Values.text("address", address))
                .newLine()
                .locale("dashboard.code", Values.text("code", spaced(session.code())));
        deps.messages().sendMessage(player, text);
    }

    private void refused(Player player, Dashboard.Opening.Refused refused) {
        var message = refused.message();
        if (message != null) {
            deps.messages().sendError(player, "dashboard.discontinued", Values.text("message", message));
            return;
        }
        deps.messages().sendError(player, refused.reason());
    }

    /**
     * Ends every dashboard session of the sender.
     *
     * @param sender sender of the command
     */
    @Executes("logout")
    public void logout(CommandSender sender) {
        var player = Senders.player(sender);
        deps.dashboard()
                .endSessions(player.getUniqueId(), CloseReason.LOGOUT)
                .thenAccept(
                        count -> deps.messages().sendMessage(player, "dashboard.logout", Values.number("count", count)))
                .exceptionally(error -> failed(player, error));
    }

    /**
     * Lists the dashboard sessions of the sender.
     *
     * @param sender sender of the command
     */
    @Executes("sessions")
    public void sessions(CommandSender sender) {
        var player = Senders.player(sender);
        deps.dashboard()
                .sessions(player.getUniqueId())
                .thenAccept(sessions -> showSessions(player, sessions))
                .exceptionally(error -> failed(player, error));
    }

    private void showSessions(Player player, List<StoredSession> sessions) {
        if (sessions.isEmpty()) {
            deps.messages().sendMessage(player, "dashboard.sessions.none");
            return;
        }
        var text = MenuText.create().text("<heading>").locale("dashboard.sessions.title");
        for (var session : sessions) {
            var device = session.isOpen()
                    ? session.deviceName().orElse(localize("dashboard.unknownDevice"))
                    : localize("dashboard.sessions.pending");
            text.newLine()
                    .locale(
                            "dashboard.sessions.entry",
                            Values.text("device", device),
                            Values.text("created", time(session.created())),
                            Values.text("active", time(session.lastActive())));
        }
        deps.messages().sendMessage(player, text);
    }

    private String localize(String key) {
        return ILocalizer.getPluginLocalizer(deps.plugin()).localize(key);
    }

    private static String time(LocalDateTime utc) {
        return TIME.format(utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(Times.zone()));
    }

    /**
     * Spaces the letters of a login code, e.g. {@code K P M W Q R}.
     *
     * @param code the code
     * @return the spaced code in capitals
     */
    static String spaced(String code) {
        return code.toUpperCase(Locale.ROOT)
                .chars()
                .mapToObj(letter -> String.valueOf((char) letter))
                .collect(Collectors.joining(" "));
    }

    private Void failed(Player player, Throwable error) {
        deps.plugin().getLogger().log(Level.WARNING, "Could not open the dashboard", error);
        deps.messages().sendError(player, "dashboard.notConnected");
        return null;
    }
}
