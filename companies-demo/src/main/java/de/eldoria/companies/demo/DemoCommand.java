/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /demo}: {@code guide} gives the guide book, {@code admin} takes the admin sandbox, {@code admin off} ends it
 * early, {@code reset-me} removes the player's company and open orders once confirmed. Its texts are MiniMessage from
 * the {@code messages} section of the config.
 */
final class DemoCommand implements BasicCommand {
    private final AdminSandbox sandbox;
    private final SandboxSessions sessions;
    private final GuideBook guide;
    private final ResetMe resetMe;
    private final Executor async;
    private final ConfigurationSection messages;
    private final Logger logger;

    /**
     * @param sandbox  the admin sandbox
     * @param sessions ends the dashboard sessions when the sandbox runs out
     * @param guide    the guide book
     * @param resetMe  the clean start
     * @param async    runs the clean start off the main thread
     * @param messages the texts
     * @param logger   told about failures
     */
    DemoCommand(
            AdminSandbox sandbox,
            SandboxSessions sessions,
            GuideBook guide,
            ResetMe resetMe,
            Executor async,
            ConfigurationSection messages,
            Logger logger) {
        this.sandbox = sandbox;
        this.sessions = sessions;
        this.guide = guide;
        this.resetMe = resetMe;
        this.async = async;
        this.messages = messages;
        this.logger = logger;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendRichMessage(text("players"));
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("guide")) {
            guide.give(player);
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reset-me")) {
            CompletableFuture.supplyAsync(() -> resetMe.request(player.getUniqueId()), async)
                    .thenAccept(step -> tell(player, step))
                    .exceptionally(err -> {
                        logger.log(Level.WARNING, "Could not reset " + player.getName(), err);
                        player.sendRichMessage(text("failed"));
                        return null;
                    });
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("companiesdemo.admin")) {
                player.sendRichMessage(text("permission"));
                return;
            }
            if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("off"))) {
                player.sendRichMessage(text("usage"));
                return;
            }
            var off = args.length == 2;
            (off ? disable(player) : enable(player)).exceptionally(err -> {
                logger.log(Level.WARNING, "Could not change the admin sandbox of " + player.getName(), err);
                player.sendRichMessage(text("failed"));
                return null;
            });
            return;
        }
        player.sendRichMessage(text("usage"));
    }

    private CompletableFuture<Void> enable(Player player) {
        return sandbox.enable(player.getUniqueId()).thenAccept(result -> {
            if (result == AdminSandbox.Result.ENABLED)
                sessions.started(player.getUniqueId(), player.getName(), sandbox.length());
            player.sendRichMessage(
                    text(
                            switch (result) {
                                case ENABLED -> "admin.enabled";
                                case ACTIVE -> "admin.active";
                                case WAIT -> "admin.wait";
                            }),
                    Placeholder.unparsed(
                            "minutes", Long.toString(sandbox.length().toMinutes())));
        });
    }

    private CompletableFuture<Void> disable(Player player) {
        return sandbox.disable(player.getUniqueId())
                .thenAccept(removed -> player.sendRichMessage(text(removed ? "admin.disabled" : "admin.none")));
    }

    private void tell(Player player, ResetMe.Step step) {
        switch (step) {
            case ResetMe.Step.Ask(var plan) -> {
                player.sendRichMessage(text("reset.ask"));
                plan.company()
                        .filter(ResetMe.Company::owned)
                        .ifPresent(company -> player.sendRichMessage(
                                text("reset.company"),
                                Placeholder.unparsed("company", company.name()),
                                Placeholder.unparsed("members", Integer.toString(company.members()))));
                if (!plan.open().isEmpty())
                    send(player, "reset.orders", plan.open().size());
                stays(player, plan.taken(), plan.company().filter(company -> !company.owned()));
                player.sendRichMessage(
                        text("reset.confirm"),
                        Placeholder.unparsed("seconds", Long.toString(ResetMe.CONFIRM.toSeconds())));
            }
            case ResetMe.Step.Done(var report) -> {
                player.sendRichMessage(text("reset.done"));
                report.disbanded().ifPresent(company -> named(player, "reset.disbanded", company));
                report.kept().ifPresent(company -> named(player, "reset.notDisbanded", company));
                if (report.cancelled() > 0) send(player, "reset.cancelled", report.cancelled());
                stays(player, report.taken(), report.member());
            }
            case ResetMe.Step.Nothing(var plan) -> {
                player.sendRichMessage(text("reset.nothing"));
                stays(player, plan.taken(), plan.company().filter(company -> !company.owned()));
            }
            case ResetMe.Step.Wait(var left) ->
                player.sendRichMessage(
                        text("reset.wait"),
                        Placeholder.unparsed(
                                "minutes",
                                Long.toString(Math.max(1, left.plusSeconds(59).toMinutes()))));
        }
    }

    private void stays(Player player, int taken, Optional<ResetMe.Company> member) {
        if (taken > 0) send(player, "reset.taken", taken);
        member.ifPresent(company -> named(player, "reset.member", company));
    }

    private void send(Player player, String key, int orders) {
        player.sendRichMessage(text(key), Placeholder.unparsed("orders", Integer.toString(orders)));
    }

    private void named(Player player, String key, ResetMe.Company company) {
        player.sendRichMessage(text(key), Placeholder.unparsed("company", company.name()));
    }

    private String text(String key) {
        return messages.getString(key, key);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) return List.of("guide", "admin", "reset-me");
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return List.of("off");
        return List.of();
    }
}
