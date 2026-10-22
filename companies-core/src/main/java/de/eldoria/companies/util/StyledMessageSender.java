/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import de.eldoria.companies.configuration.elements.StyleSettings;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Message sender styled by the {@link StyleSettings} of the configuration.
 * <p>
 * The prefix and the tag colours are looked up for every message, so a reloaded style reaches every command and
 * service at once. They all keep the sender they got first, which is why the sender is registered once and never
 * replaced.
 */
public final class StyledMessageSender extends MessageSender {
    private final Supplier<StyleSettings> style;

    private StyledMessageSender(
            Plugin plugin,
            MiniMessage miniMessage,
            TagResolver messageTags,
            TagResolver errorTags,
            Supplier<StyleSettings> style) {
        super(plugin, miniMessage, messageTags, errorTags, Component.empty());
        this.style = style;
    }

    /**
     * Creates the sender and registers it as the plugin's message sender.
     *
     * @param plugin    plugin owning the sender
     * @param localizer localizer resolving locale codes in messages
     * @param style     supplier of the current style settings
     * @return the registered sender
     */
    public static StyledMessageSender register(Plugin plugin, ILocalizer localizer, Supplier<StyleSettings> style) {
        var tags = TagResolver.resolver(new StyleTags(style), StandardTags.defaults());
        var miniMessage = MiniMessage.builder()
                .tags(tags)
                .preProcessor(localizer::localize)
                .build();
        var sender = new StyledMessageSender(
                plugin,
                miniMessage,
                TagResolver.resolver(tags, new DefaultColor(() -> style.get().message())),
                TagResolver.resolver(tags, new DefaultColor(() -> style.get().error())),
                style);
        MessageSender.register(sender);
        return sender;
    }

    @Override
    public Component prefix() {
        return MiniMessage.miniMessage().deserialize(style.get().prefix());
    }

    @Override
    protected Component applyPrefix(Component component) {
        var prefix = prefix();
        if (PlainTextComponentSerializer.plainText().serialize(prefix).isEmpty()) return component;
        return prefix.appendSpace().append(component);
    }

    @Override
    public void sendMessage(CommandSender sender, Component component) {
        sender.sendMessage(applyPrefix(component));
    }

    @Override
    public void broadcast(String message) {
        plugin().getServer().broadcast(serialize(null, message, messageTagResolver()));
    }

    @Override
    public void sendTitle(Player player, Title title) {
        player.showTitle(title);
    }

    @Override
    public void sendActionBar(Player player, String message, TagResolver... placeholder) {
        player.sendActionBar(serialize(player, message, messageTagResolver(), placeholder));
    }

    @Override
    public void sendErrorActionBar(Player player, String message, TagResolver... placeholder) {
        player.sendActionBar(serialize(player, message, errorTagResolver(), placeholder));
    }

    @Override
    public void sendBossBar(Player player, BossBar bossBar) {
        player.showBossBar(bossBar);
    }

    @Override
    public BossBar sendBossBar(
            Player player,
            String message,
            float progress,
            BossBar.Color color,
            BossBar.Overlay overlay,
            Set<BossBar.Flag> flags) {
        var bossBar =
                BossBar.bossBar(serialize(player, message, messageTagResolver()), progress, color, overlay, flags);
        player.showBossBar(bossBar);
        return bossBar;
    }

    @Override
    public void hideBossBar(Player player, BossBar bossBar) {
        player.hideBossBar(bossBar);
    }

    @Override
    public Audience asAudience(Player player) {
        return player;
    }

    /**
     * Resolves the colour tags and the {@code <prefix>} tag from the current style.
     */
    private record StyleTags(Supplier<StyleSettings> style) implements TagResolver {
        private static final String PREFIX = "prefix";

        @Override
        public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) {
            var settings = style.get();
            if (PREFIX.equals(name))
                return Tag.selfClosingInserting(MiniMessage.miniMessage().deserialize(settings.prefix()));
            var color = settings.tags().get(name);
            return color == null ? null : Tag.styling(color);
        }

        @Override
        public boolean has(@NotNull String name) {
            return PREFIX.equals(name) || style.get().tags().containsKey(name);
        }
    }

    /**
     * Resolves the {@code <default>} tag every message starts with to the current message or error colour.
     */
    private record DefaultColor(Supplier<TextColor> color) implements TagResolver {
        private static final String DEFAULT = "default";

        @Override
        public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) {
            return DEFAULT.equals(name) ? Tag.styling(color.get()) : null;
        }

        @Override
        public boolean has(@NotNull String name) {
            return DEFAULT.equals(name);
        }
    }
}
