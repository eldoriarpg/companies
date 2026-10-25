/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import de.eldoria.companies.util.Times;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * {@code /companyadmin settings}: the shared files of the network, stored as revisions.
 * <ul>
 *     <li>{@code status} shows for each file its latest revision, the one this server applied, whether this server
 *     changed it without sharing, and the servers still on an older revision;</li>
 *     <li>{@code history <file> [page]} lists the revisions of a file, newest first, with who stored them, where and
 *     when;</li>
 *     <li>{@code rollback <file> <revision>} asks to make an earlier revision the newest again;
 *     {@code rollback confirm}, within 30 seconds, stores it as the next revision, which every server then takes.</li>
 * </ul>
 * They need only the database and work from the console.
 */
@Subcommand("settings")
@Permission(Admin.ADMIN)
public final class SettingsCommand {
    private static final int PAGE_SIZE = 10;
    private static final int SHORT_UID = 8;
    private static final UUID CONSOLE = new UUID(0, 0);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CommandDependencies deps;
    private final Confirmations<Rollback> rollbacks = new Confirmations<>();

    /**
     * @param deps what the commands need
     */
    public SettingsCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows how each shared file stands.
     *
     * @param sender sender of the command
     */
    @Executes("status")
    public void status(CommandSender sender) {
        var nodeData = nodeData();
        run(sender, () -> deps.configuration().status(nodeData), statuses -> {
            var text = MenuText.create().text("<heading>").locale("companyadmin.settings.title");
            for (var status : statuses) {
                text.newLine()
                        .locale(
                                "companyadmin.settings.file",
                                Values.text("file", status.path()),
                                Values.number("latest", status.latest()),
                                Values.number("applied", status.applied()));
                if (status.edited()) text.locale("companyadmin.settings.edited");
                if (!status.behind().isEmpty()) {
                    text.locale("companyadmin.settings.behind", Values.text("nodes", shortUids(status.behind())));
                }
            }
            deps.messages().sendMessage(sender, text);
        });
    }

    /**
     * Lists the newest revisions of a shared file.
     *
     * @param sender sender of the command
     * @param file   the file, such as {@code company.yml}
     */
    @Executes("history")
    public void history(CommandSender sender, String file) {
        history(sender, file, 1);
    }

    /**
     * Lists a page of the revisions of a shared file.
     *
     * @param sender sender of the command
     * @param file   the file, such as {@code company.yml}
     * @param page   the page, from 1
     */
    @Executes("history")
    public void history(CommandSender sender, String file, int page) {
        var path = sharedPath(file);
        var nodeData = nodeData();
        var shown = Math.max(1, page);
        run(sender, () -> nodeData.settingsHistory(path, (shown - 1) * PAGE_SIZE, PAGE_SIZE), changes -> {
            var text = MenuText.create()
                    .text("<heading>")
                    .locale(
                            "companyadmin.settings.history.title",
                            Values.text("file", path),
                            Values.number("page", shown));
            if (changes.isEmpty()) text.newLine().locale("companyadmin.settings.history.none");
            for (var change : changes) {
                text.newLine()
                        .locale(
                                "companyadmin.settings.history.entry",
                                Values.number("revision", change.revision()),
                                Values.text("time", TIME.format(Times.local(change.changed()))),
                                Values.text("actor", change.actor()),
                                Values.text("node", shortUid(change.node())));
            }
            deps.messages().sendMessage(sender, text);
        });
    }

    /**
     * Asks to make an earlier revision of a shared file the newest again.
     *
     * @param sender   sender of the command
     * @param file     the file, such as {@code company.yml}
     * @param revision the earlier revision
     */
    @Executes("rollback")
    public void rollback(CommandSender sender, String file, int revision) {
        var path = sharedPath(file);
        var nodeData = nodeData();
        run(sender, () -> nodeData.settingsAt(path, revision).isPresent(), exists -> {
            if (!exists) {
                deps.messages()
                        .sendError(
                                sender,
                                "companyadmin.settings.error.unknownRevision",
                                Values.text("file", path),
                                Values.number("revision", revision));
                return;
            }
            rollbacks.request(senderId(sender), new Rollback(path, revision));
            deps.messages()
                    .sendMessage(
                            sender,
                            "companyadmin.settings.rollback.ask",
                            Values.text("file", path),
                            Values.number("revision", revision));
        });
    }

    /**
     * Makes the revision the sender was asked about the newest again.
     *
     * @param sender sender of the command
     */
    @Executes("rollback confirm")
    public void confirm(CommandSender sender) {
        var rollback =
                rollbacks.confirm(senderId(sender)).orElseThrow(() -> CommandFailure.of("error.confirmationExpired"));
        var nodeData = nodeData();
        run(
                sender,
                () -> deps.configuration()
                        .rollback(nodeData, rollback.path(), rollback.revision(), sender.getName())
                        .orElseThrow(() -> CommandFailure.of("companyadmin.settings.error.unknownRevision")),
                outcome -> rolledBack(sender, rollback, outcome));
    }

    private void rolledBack(CommandSender sender, Rollback rollback, Configuration.EditOutcome outcome) {
        if (outcome.revision().isEmpty()) {
            deps.messages()
                    .sendError(
                            sender,
                            "companyadmin.reload.conflict",
                            Values.text("file", outcome.path()),
                            Values.text("kept", String.valueOf(outcome.conflictFile())));
            return;
        }
        deps.adminLog()
                .entry(sender, "settings rollback")
                .with("file", rollback.path())
                .with("revision", rollback.revision())
                .with("new", outcome.revision().getAsInt())
                .write();
        if (outcome.replaced()
                && rollback.path().equals(Configuration.CONFIG_YML.path().toString())) {
            deps.plugin().getServer().getScheduler().runTask(deps.plugin(), () -> {
                deps.services().updates().apply();
                deps.reloaded();
            });
        }
        deps.messages()
                .sendMessage(
                        sender,
                        "companyadmin.settings.rollback.done",
                        Values.text("file", rollback.path()),
                        Values.number("revision", rollback.revision()),
                        Values.number("new", outcome.revision().getAsInt()));
    }

    private String sharedPath(String file) {
        var path = file.strip();
        if (deps.configuration().sharedKey(path).isEmpty()) {
            throw CommandFailure.of("companyadmin.settings.error.unknownFile", Values.text("file", path));
        }
        return path;
    }

    private ANodeData nodeData() {
        var nodeData = deps.nodeData();
        if (nodeData == null) throw CommandFailure.of("error.notReady");
        return nodeData;
    }

    private static String shortUids(List<UUID> nodes) {
        return nodes.stream().map(SettingsCommand::shortUid).collect(Collectors.joining(", "));
    }

    private static String shortUid(UUID node) {
        return node.toString().substring(0, SHORT_UID);
    }

    private static UUID senderId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    private <T> void run(CommandSender sender, Supplier<T> query, Consumer<T> answer) {
        CompletableFuture.supplyAsync(query, Threading.VIRTUAL)
                .thenAccept(answer)
                .exceptionally(err -> {
                    var cause = err.getCause() == null ? err : err.getCause();
                    if (cause instanceof CommandFailure failure) {
                        failure.sendTo(sender, deps.messages());
                        return null;
                    }
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not read or change the shared settings", err);
                    deps.messages().sendError(sender, "companyadmin.reload.failed");
                    return null;
                });
    }

    /**
     * A rollback waiting for its confirmation.
     *
     * @param path     the file
     * @param revision the earlier revision
     */
    private record Rollback(String path, int revision) {}
}
