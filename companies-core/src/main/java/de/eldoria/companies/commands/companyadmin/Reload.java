/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.util.Confirmations;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * {@code /companyadmin reload}: reloads the configuration and applies the update settings and everything registered
 * with {@link CommandDependencies#onReload(Runnable)}. Works from the console too.
 * <p>
 * A shared file changed on this server against the revision it last applied is offered to every server: in a network
 * the reload names the changed keys and {@code reload confirm}, within 30 seconds, stores them as the next revision; on
 * a single server they are stored at once. A file another server changed meanwhile is not stored; this server's version
 * is kept as a conflict file and the newer revision is used.
 */
@Subcommand("reload")
@Permission(Admin.RELOAD)
public final class Reload {
    private static final UUID CONSOLE = new UUID(0, 0);

    private final CommandDependencies deps;
    private final Confirmations<List<Configuration.Edit>> pending = new Confirmations<>();

    /**
     * @param deps what the commands need
     */
    public Reload(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     */
    @Executes
    public void reload(CommandSender sender) {
        var updates = deps.services().updates();
        deps.configuration().reload();
        updates.apply();
        deps.reloaded();
        deps.adminLog().entry(sender, "reload").write();
        var unreadable = deps.configuration().unreadableFiles();
        if (unreadable.isEmpty()) {
            deps.messages().sendMessage(sender, "companyadmin.reload.done");
        } else {
            deps.messages()
                    .sendError(
                            sender,
                            "companyadmin.reload.unreadable",
                            Values.text("files", String.join(", ", unreadable)));
        }
        offerEdits(sender);
    }

    /**
     * Stores the edits the sender was asked about.
     *
     * @param sender sender of the command
     */
    @Executes("confirm")
    public void confirm(CommandSender sender) {
        var edits = pending.confirm(senderId(sender)).orElseThrow(() -> CommandFailure.of("error.confirmationExpired"));
        publish(sender, edits);
    }

    private void offerEdits(CommandSender sender) {
        var nodeData = deps.nodeData();
        if (nodeData == null) return;
        CompletableFuture.supplyAsync(
                        () -> new Found(
                                deps.configuration().edits(nodeData),
                                nodeData.nodes().size()),
                        Threading.VIRTUAL)
                .thenAccept(found -> {
                    if (found.edits().isEmpty()) return;
                    if (found.nodes() <= 1) {
                        publish(sender, found.edits());
                        return;
                    }
                    pending.request(senderId(sender), found.edits());
                    deps.messages()
                            .sendMessage(
                                    sender,
                                    "companyadmin.reload.edits",
                                    Values.text("changes", describe(found.edits())));
                })
                .exceptionally(error -> failed(sender, error));
    }

    private void publish(CommandSender sender, List<Configuration.Edit> edits) {
        var nodeData = deps.nodeData();
        if (nodeData == null) throw CommandFailure.of("error.notReady");
        CompletableFuture.supplyAsync(
                        () -> deps.configuration().publishEdits(nodeData, edits, sender.getName()), Threading.VIRTUAL)
                .thenAccept(outcomes -> report(sender, outcomes))
                .exceptionally(error -> failed(sender, error));
    }

    private void report(CommandSender sender, List<Configuration.EditOutcome> outcomes) {
        var stored = outcomes.stream()
                .filter(outcome -> outcome.revision().isPresent())
                .map(Configuration.EditOutcome::path)
                .toList();
        if (!stored.isEmpty()) {
            deps.adminLog()
                    .entry(sender, "settings publish")
                    .with("files", String.join(", ", stored))
                    .write();
            deps.messages()
                    .sendMessage(
                            sender, "companyadmin.reload.published", Values.text("files", String.join(", ", stored)));
        }
        for (var outcome : outcomes) {
            if (outcome.revision().isPresent()) continue;
            var kept = outcome.conflictFile();
            if (kept == null) {
                deps.messages()
                        .sendError(sender, "companyadmin.reload.conflictNotKept", Values.text("file", outcome.path()));
            } else {
                deps.messages()
                        .sendError(
                                sender,
                                "companyadmin.reload.conflict",
                                Values.text("file", outcome.path()),
                                Values.text("kept", kept));
            }
        }
    }

    private static String describe(List<Configuration.Edit> edits) {
        return edits.stream()
                .map(edit -> edit.path() + " (" + String.join(", ", edit.changes()) + ")")
                .collect(Collectors.joining("; "));
    }

    private Void failed(CommandSender sender, Throwable error) {
        deps.plugin().getLogger().log(Level.SEVERE, "Could not share the changed settings.", error);
        deps.messages().sendError(sender, "companyadmin.syncconfiguration.failed");
        return null;
    }

    private static UUID senderId(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : CONSOLE;
    }

    /**
     * The edits a reload found and the size of the network.
     *
     * @param edits the edits
     * @param nodes how many nodes are registered
     */
    private record Found(List<Configuration.Edit> edits, int nodes) {}
}
