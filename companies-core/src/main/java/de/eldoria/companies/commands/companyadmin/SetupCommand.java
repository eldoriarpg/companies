/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.SettingsEditor;
import de.eldoria.companies.dashboard.Dashboard;
import de.eldoria.companies.menus.admin.SetupMenu;
import de.eldoria.companies.menus.admin.SetupStatus;
import de.eldoria.companies.util.Permission.Admin;
import de.eldoria.companies.util.Threading;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import tools.jackson.databind.node.BooleanNode;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /companyadmin setup}: the in-game part of the setup, see {@link SetupMenu}. A player gets the menu, the console
 * the same status with the commands to use. {@code name <name>} names this server, {@code dashboard <on|off>} switches
 * the dashboard for the network, and the key is entered with {@code /companyadmin dashboard key}. Looking needs the
 * general admin permission, changing the reload permission too, as for settings on the dashboard.
 */
@Subcommand("setup")
@Permission(Admin.ADMIN)
public final class SetupCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public SetupCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the in-game setup.
     *
     * @param sender sender of the command
     */
    @Executes
    public void open(CommandSender sender) {
        status().thenAccept(status -> onMainThread(() -> {
                    if (sender instanceof Player player && deps.servicesReady()) {
                        deps.services()
                                .menus()
                                .show(
                                        player,
                                        new SetupMenu(
                                                deps.messages(), deps.services().menus(), this::apply),
                                        SetupMenu.View.of(status));
                        return;
                    }
                    deps.messages()
                            .sendMessage(
                                    sender, SetupMenu.status(status).newLine().locale("companyadmin.setup.console"));
                }))
                .exceptionally(error -> failed(sender, error));
    }

    /**
     * Names this server.
     *
     * @param sender sender of the command
     * @param name   the name, at most 32 characters
     */
    @Executes("name")
    @Permission(Admin.RELOAD)
    public void name(CommandSender sender, @StringArg(StringArgType.GREEDY) String name) {
        rename(sender, name);
        deps.messages().sendMessage(sender, "companyadmin.setup.renamed");
    }

    /**
     * Switches the dashboard on or off for the network.
     *
     * @param sender sender of the command
     * @param state  {@code on} or {@code off}
     */
    @Executes("dashboard")
    @Permission(Admin.RELOAD)
    public void dashboard(CommandSender sender, String state) {
        var on =
                switch (state.toLowerCase(Locale.ROOT)) {
                    case "on" -> true;
                    case "off" -> false;
                    default -> throw CommandFailure.of("companyadmin.setup.error.onOff");
                };
        CompletableFuture.runAsync(() -> switchDashboard(sender.getName(), on), Threading.VIRTUAL)
                .thenRun(() -> deps.messages()
                        .sendMessage(
                                sender,
                                on
                                        ? "companyadmin.setup.dashboardSwitchedOn"
                                        : "companyadmin.setup.dashboardSwitchedOff"))
                .exceptionally(error -> failed(sender, error));
    }

    private void apply(Player admin, SetupMenu.Input input, Consumer<@Nullable CommandFailure> then) {
        if (!admin.hasPermission(Admin.RELOAD)) {
            then.accept(CommandFailure.of("companyadmin.error.permission"));
            return;
        }
        CompletableFuture.runAsync(
                        () -> {
                            input.key().ifPresent(key -> {
                                deps.dashboard().enterKey(key).join();
                                deps.adminLog().entry(admin, "dashboard key").write();
                            });
                            switchDashboard(admin.getName(), input.dashboard());
                        },
                        Threading.VIRTUAL)
                .thenRun(() -> onMainThread(() -> {
                    rename(admin, input.name());
                    then.accept(null);
                }))
                .exceptionally(error -> {
                    var cause = error.getCause() == null ? error : error.getCause();
                    var failure = cause instanceof CommandFailure refused
                            ? refused
                            : CommandFailure.of("companyadmin.setup.error.failed");
                    if (!(cause instanceof CommandFailure)) {
                        deps.plugin().getLogger().log(Level.SEVERE, "Could not apply the setup.", error);
                    }
                    onMainThread(() -> then.accept(failure));
                    return null;
                });
    }

    private void rename(CommandSender sender, String name) {
        var settings = deps.configuration().nodeSettings();
        if (settings.name().equals(name.strip())) return;
        settings.name(name);
        deps.configuration().save(Configuration.NODE_SETTINGS);
        deps.adminLog()
                .entry(sender, "setup name")
                .with("name", settings.name())
                .write();
    }

    private void switchDashboard(String admin, boolean on) {
        var configuration = deps.configuration();
        if (configuration.dashboardSettings().enabled() == on) return;
        var nodeData = deps.nodeData();
        var base = nodeData.settingsRevisions()
                .getOrDefault(Configuration.CONFIG_YML.path().toString(), 0);
        var result = SettingsEditor.save(
                configuration,
                nodeData,
                Configuration.CONFIG_YML.path().toString(),
                base,
                Map.of("dashboard.enabled", BooleanNode.valueOf(on)),
                admin + "@setup");
        switch (result) {
            case SettingsEditor.Result.Invalid invalid -> throw CommandFailure.of("companyadmin.setup.error.failed");
            case SettingsEditor.Result.Newer newer -> throw CommandFailure.of("companyadmin.setup.error.newer");
            case SettingsEditor.Result.Done done -> {
                if (done.outcome() instanceof Configuration.StoreOutcome.Stored stored && stored.replaced()) {
                    onMainThread(deps::applyChanges);
                }
            }
        }
    }

    private CompletableFuture<SetupStatus> status() {
        return CompletableFuture.supplyAsync(
                () -> {
                    var configuration = deps.configuration();
                    var nodes = deps.nodeData().nodes().size();
                    var dashboard = dashboardStatus();
                    return new SetupStatus(
                            configuration.databaseSettings().storageType(),
                            nodes,
                            configuration.databaseSettings().redis().enabled(),
                            configuration.nodeSettings().nodeType(),
                            configuration.nodeSettings().name(),
                            configuration.dashboardSettings().enabled(),
                            dashboard == null
                                    ? "-"
                                    : dashboard
                                            .state()
                                            .name()
                                            .toLowerCase(Locale.ROOT)
                                            .replace('_', ' '),
                            dashboard != null && dashboard.keyKind().isPresent());
                },
                Threading.VIRTUAL);
    }

    private Dashboard.@Nullable Status dashboardStatus() {
        try {
            return deps.dashboard().status().join();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void onMainThread(Runnable task) {
        deps.plugin().getServer().getScheduler().runTask(deps.plugin(), task);
    }

    private Void failed(CommandSender sender, Throwable error) {
        var cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof CommandFailure failure) {
            failure.sendTo(sender, deps.messages());
            return null;
        }
        deps.plugin().getLogger().log(Level.SEVERE, "Could not run the setup.", error);
        deps.messages().sendError(sender, "companyadmin.setup.error.failed");
        return null;
    }
}
