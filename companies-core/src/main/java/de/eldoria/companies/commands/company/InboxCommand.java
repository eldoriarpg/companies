/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.NotificationCategories;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.companies.menus.company.InboxMenu;
import de.eldoria.companies.menus.company.InboxSettingsMenu;
import de.eldoria.companies.services.notifications.Inbox;
import de.eldoria.companies.services.notifications.NotificationCategory;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * {@code /company inbox}: the player's notifications, a page at a time. {@code read} marks all read, {@code clear}
 * deletes the read ones, {@code mute}/{@code unmute} and {@code settings} change which categories are shown live.
 * Works for players without a company, since order owners are notified as well.
 */
@Subcommand("inbox")
@PlayerOnly
public final class InboxCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public InboxCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the newest page.
     *
     * @param sender sender of the command
     */
    @Executes
    public void show(CommandSender sender) {
        show(Senders.player(sender), 1, Optional.empty());
    }

    /**
     * Shows a page.
     *
     * @param sender sender of the command
     * @param page   page, starting at 1
     */
    @Executes
    public void show(CommandSender sender, @IntArg(min = 1) int page) {
        show(Senders.player(sender), page, Optional.empty());
    }

    /**
     * Marks every notification read.
     *
     * @param sender sender of the command
     */
    @Executes("read")
    public void readAll(CommandSender sender) {
        var player = Senders.player(sender);
        reply(player, inbox().readAll(player.getUniqueId(), Times.now()).thenApply(InboxCommand::markedRead));
    }

    /**
     * Deletes the read notifications.
     *
     * @param sender sender of the command
     */
    @Executes("clear")
    public void clearRead(CommandSender sender) {
        var player = Senders.player(sender);
        reply(player, inbox().clearRead(player.getUniqueId()).thenApply(InboxCommand::cleared));
    }

    /**
     * Mutes a category: its notifications arrive in the inbox but are not shown live.
     *
     * @param sender   sender of the command
     * @param category name of the category
     */
    @Executes("mute")
    public void mute(CommandSender sender, @NotificationCategories String category) {
        setMuted(Senders.player(sender), category(category), true);
    }

    /**
     * Shows the notifications of a muted category live again.
     *
     * @param sender   sender of the command
     * @param category name of the category
     */
    @Executes("unmute")
    public void unmute(CommandSender sender, @NotificationCategories String category) {
        setMuted(Senders.player(sender), category(category), false);
    }

    /**
     * Shows the mute settings.
     *
     * @param sender sender of the command
     */
    @Executes("settings")
    public void settings(CommandSender sender) {
        settings(Senders.player(sender));
    }

    /**
     * Shows a page of a player's inbox and marks it read.
     *
     * @param player viewing player
     * @param page   page, starting at 1
     * @param notice line shown on top
     */
    public void show(Player player, int page, Optional<MenuText> notice) {
        var services = deps.services();
        var menu = new InboxMenu(deps.messages(), services.notificationService().renderer(), new InboxButtons());
        inbox().page(player.getUniqueId(), page, Times.now())
                .thenAccept(shown -> services.menus().show(player, menu, new InboxMenu.Model(shown, notice)))
                .exceptionally(this::log);
    }

    /**
     * Shows a player's mute settings.
     *
     * @param player viewing player
     */
    public void settings(Player player) {
        var services = deps.services();
        var menu = new InboxSettingsMenu(deps.messages(), new SettingsButtons());
        inbox().muted(player.getUniqueId())
                .thenAccept(mask -> services.menus().show(player, menu, new InboxSettingsMenu.Model(mask)))
                .exceptionally(this::log);
    }

    private void setMuted(Player player, NotificationCategory category, boolean mute) {
        reply(player, inbox().mute(player.getUniqueId(), category, mute).thenApply(mask -> MenuText.create()
                .text("<neutral>")
                .locale(category.mutedBy(mask) ? "inbox.muted" : "inbox.unmuted", categoryName(player, category))));
    }

    private static NotificationCategory category(String name) {
        return NotificationCategory.mutable(name)
                .orElseThrow(() -> CommandFailure.of(
                        "error.invalidEnumValue",
                        Placeholder.unparsed(
                                "values",
                                NotificationCategory.mutables().stream()
                                        .map(category -> category.name().toLowerCase(Locale.ROOT))
                                        .collect(Collectors.joining(", ")))));
    }

    private TagResolver.Single categoryName(Player player, NotificationCategory category) {
        return Values.component("category", new MenuComponents(deps.messages(), player).locale(category.key()));
    }

    private static MenuText markedRead(int count) {
        return MenuText.create().text("<neutral>").locale("inbox.markedRead", Values.number("count", count));
    }

    private static MenuText cleared(int count) {
        return MenuText.create().text("<neutral>").locale("inbox.cleared", Values.number("count", count));
    }

    private void reply(Player player, CompletableFuture<MenuText> answer) {
        answer.thenAccept(text -> deps.messages().sendMessage(player, text)).exceptionally(this::log);
    }

    private Void log(Throwable err) {
        deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
        return null;
    }

    private Inbox inbox() {
        return deps.services().notificationService().inbox();
    }

    /**
     * The buttons of the inbox: an action shows the first page again with its result on top.
     */
    private final class InboxButtons implements InboxMenu.Handlers {
        @Override
        public void page(Player player, int number) {
            show(player, number, Optional.empty());
        }

        @Override
        public void readAll(Player player) {
            inbox().readAll(player.getUniqueId(), Times.now())
                    .thenAccept(count -> show(player, 1, Optional.of(markedRead(count))))
                    .exceptionally(InboxCommand.this::log);
        }

        @Override
        public void clearRead(Player player) {
            inbox().clearRead(player.getUniqueId())
                    .thenAccept(count -> show(player, 1, Optional.of(cleared(count))))
                    .exceptionally(InboxCommand.this::log);
        }

        @Override
        public void settings(Player player) {
            InboxCommand.this.settings(player);
        }
    }

    /**
     * The buttons of the mute settings: a switch changes the setting and shows the settings again.
     */
    private final class SettingsButtons implements InboxSettingsMenu.Handlers {
        @Override
        public void toggle(Player player, NotificationCategory category) {
            var settings = new InboxSettingsMenu(deps.messages(), this);
            inbox().muted(player.getUniqueId())
                    .thenCompose(mask -> inbox().mute(player.getUniqueId(), category, !category.mutedBy(mask)))
                    .thenAccept(
                            mask -> deps.services().menus().show(player, settings, new InboxSettingsMenu.Model(mask)))
                    .exceptionally(InboxCommand.this::log);
        }

        @Override
        public void back(Player player) {
            show(player, 1, Optional.empty());
        }
    }
}
