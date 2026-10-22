/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus;

import de.eldoria.companies.util.Confirmations;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Asks a player to confirm an action: a confirmation dialog, or a chat line with {@code [Confirm]} and {@code [Deny]}.
 * <p>
 * Both buttons work once and only while the confirmation is valid. The chat line does not hold the chat, so an
 * unanswered confirmation simply expires.
 */
public final class ConfirmMenu implements Menu<ConfirmMenu.Prompt> {
    static final String CONFIRM = "act_confirm";
    static final String DENY = "act_deny";

    private final Plugin plugin;

    /**
     * @param plugin plugin whose messages render the dialog
     */
    public ConfirmMenu(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public MenuText chat(Player player, Prompt model, MenuActions actions) {
        return question(model)
                .space()
                .text(
                        "<act_confirm><add>[<i18n:words.confirm>]</add></act_confirm>",
                        actions.chatActionOnce(CONFIRM, model.onConfirm()))
                .space()
                .text(
                        "<act_deny><remove>[<i18n:words.deny>]</remove></act_deny>",
                        actions.chatActionOnce(DENY, model.onCancel()));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Prompt model, MenuActions actions) {
        var title = render(player, MenuText.create().locale("menu.confirm.title"));
        var question = render(player, question(model));
        var confirm = actions.buttonOnce(
                render(player, MenuText.create().text("<add><i18n:words.confirm>")),
                null,
                (clicked, response) -> model.onConfirm().accept(clicked));
        var deny = actions.buttonOnce(
                render(player, MenuText.create().text("<remove><i18n:words.deny>")),
                null,
                (clicked, response) -> model.onCancel().accept(clicked));
        var base = DialogBase.builder(title)
                .body(List.of(DialogBody.plainMessage(question)))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();
        return Optional.of(
                Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(confirm, deny))));
    }

    /**
     * @return the validity of a confirmation, so the buttons expire with it
     */
    @Override
    public Duration lifetime() {
        return Confirmations.VALIDITY;
    }

    /**
     * @return false, the confirmation is a single chat line
     */
    @Override
    public boolean holdsChat() {
        return false;
    }

    private static MenuText question(Prompt model) {
        return MenuText.create().text("<neutral>").append(model.question());
    }

    private Component render(Player player, MenuText text) {
        return MessageSender.getPluginMessageSender(plugin)
                .serializeMessage(player, text.build(), text.replacements().toArray(TagResolver[]::new));
    }

    /**
     * What to confirm and what each answer does.
     *
     * @param question  the question, with its placeholders
     * @param onConfirm runs when the player confirms
     * @param onCancel  runs when the player denies
     */
    public record Prompt(MenuText question, Consumer<Player> onConfirm, Consumer<Player> onCancel) {}
}
