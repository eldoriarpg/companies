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
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /demo}: {@code guide} gives the guide book, {@code admin} takes the admin sandbox, {@code admin off} ends it
 * early. Its texts are MiniMessage from the {@code messages} section of the config.
 */
final class DemoCommand implements BasicCommand {
    private final AdminSandbox sandbox;
    private final SandboxSessions sessions;
    private final GuideBook guide;
    private final ConfigurationSection messages;
    private final Logger logger;

    /**
     * @param sandbox  the admin sandbox
     * @param sessions ends the dashboard sessions when the sandbox runs out
     * @param guide    the guide book
     * @param messages the texts
     * @param logger   told about failures
     */
    DemoCommand(
            AdminSandbox sandbox,
            SandboxSessions sessions,
            GuideBook guide,
            ConfigurationSection messages,
            Logger logger) {
        this.sandbox = sandbox;
        this.sessions = sessions;
        this.guide = guide;
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
            if (result == AdminSandbox.Result.ENABLED) sessions.revokeLater(player.getName(), sandbox.length());
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

    private String text(String key) {
        return messages.getString(key, key);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) return List.of("guide", "admin");
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return List.of("off");
        return List.of();
    }
}
