/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Binds the buttons of one showing of a menu to handlers, through click callbacks instead of commands.
 * <p>
 * Chat buttons are {@link ClickEvent#callback(ClickCallback, ClickCallback.Options) click callbacks}, dialog buttons
 * are {@link DialogAction#customClick(io.papermc.paper.registry.data.dialog.action.DialogActionCallback,
 * ClickCallback.Options) custom clicks}. Destructive actions are bound once and work a single time; other actions
 * work until the lifetime ends. An expired button shows Paper's expiry text, and callbacks do not survive a restart,
 * so handlers check their state again, as a command would.
 * <p>
 * Handlers run on the main thread and only for the player the menu was shown to.
 */
public final class MenuActions {
    /**
     * Lifetime of the buttons of menus that belong to no shorter state.
     */
    public static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(15);

    private final Plugin plugin;
    private final UUID viewer;
    private final Duration lifetime;

    /**
     * @param plugin   plugin running the handlers
     * @param player   player the menu is shown to
     * @param lifetime how long the buttons stay clickable
     */
    public MenuActions(Plugin plugin, Player player, Duration lifetime) {
        this.plugin = plugin;
        this.viewer = player.getUniqueId();
        this.lifetime = lifetime;
    }

    /**
     * A chat button that works until the lifetime ends, used as {@code <key>[Button]</key>}.
     *
     * @param key     placeholder key
     * @param handler runs when clicked
     * @return placeholder
     */
    public TagResolver.Single chatAction(String key, Consumer<Player> handler) {
        return chatAction(key, handler, ClickCallback.UNLIMITED_USES);
    }

    /**
     * A chat button that works a single time, for destructive actions.
     *
     * @param key     placeholder key
     * @param handler runs when clicked
     * @return placeholder
     */
    public TagResolver.Single chatActionOnce(String key, Consumer<Player> handler) {
        return chatAction(key, handler, 1);
    }

    /**
     * A dialog button that works until the lifetime ends.
     *
     * @param label   text of the button
     * @param tooltip shown when hovered, or null
     * @param handler runs when clicked, with the values of the dialog inputs
     * @return button
     */
    public ActionButton button(
            Component label, @Nullable Component tooltip, BiConsumer<Player, DialogResponseView> handler) {
        return button(label, tooltip, handler, ClickCallback.UNLIMITED_USES);
    }

    /**
     * A dialog button that works a single time, for destructive actions.
     *
     * @param label   text of the button
     * @param tooltip shown when hovered, or null
     * @param handler runs when clicked, with the values of the dialog inputs
     * @return button
     */
    public ActionButton buttonOnce(
            Component label, @Nullable Component tooltip, BiConsumer<Player, DialogResponseView> handler) {
        return button(label, tooltip, handler, 1);
    }

    /**
     * @return how long the buttons stay clickable
     */
    public Duration lifetime() {
        return lifetime;
    }

    private TagResolver.Single chatAction(String key, Consumer<Player> handler, int uses) {
        return Values.click(key, ClickEvent.callback(audience -> run(audience, handler), options(uses)));
    }

    private ActionButton button(
            Component label, @Nullable Component tooltip, BiConsumer<Player, DialogResponseView> handler, int uses) {
        var action = DialogAction.customClick(
                (response, audience) -> run(audience, player -> handler.accept(player, response)), options(uses));
        return ActionButton.builder(label).tooltip(tooltip).action(action).build();
    }

    private ClickCallback.Options options(int uses) {
        return ClickCallback.Options.builder().uses(uses).lifetime(lifetime).build();
    }

    private void run(Audience audience, Consumer<Player> handler) {
        if (!(audience instanceof Player player) || !viewer.equals(player.getUniqueId())) return;
        if (plugin.getServer().isPrimaryThread()) {
            handler.accept(player);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> handler.accept(player));
    }
}
