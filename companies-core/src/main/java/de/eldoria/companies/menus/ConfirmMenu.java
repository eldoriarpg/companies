/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
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
 * A prompt may name its confirm button and offer an alternative way to confirm, such as paying from the treasury
 * instead of paying oneself; the dialog then lists the three buttons.
 * <p>
 * Both buttons work once and only while the confirmation is valid. The chat line does not hold the chat, so an
 * unanswered confirmation simply expires.
 */
public final class ConfirmMenu implements Menu<ConfirmMenu.Prompt> {
    static final String CONFIRM = "act_confirm";
    static final String DENY = "act_deny";
    static final String ALTERNATIVE = "act_alternative";

    private final Plugin plugin;

    /**
     * @param plugin plugin whose messages render the dialog
     */
    public ConfirmMenu(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public MenuText chat(Player player, Prompt model, MenuActions actions) {
        var text = question(model)
                .space()
                .text("<act_confirm><add>[", actions.chatActionOnce(CONFIRM, model.onConfirm()))
                .append(model.confirmLabel())
                .text("]</add></act_confirm>");
        model.alternative().ifPresent(alternative -> text.space()
                .text("<act_alternative><add>[", actions.chatActionOnce(ALTERNATIVE, alternative.onChoose()))
                .append(alternative.label())
                .text("]</add></act_alternative>"));
        return text.space()
                .text(
                        "<act_deny><remove>[<i18n:words.deny>]</remove></act_deny>",
                        actions.chatActionOnce(DENY, model.onCancel()));
    }

    @Override
    public Optional<Dialog> dialog(Player player, Prompt model, MenuActions actions) {
        var title = render(player, MenuText.create().locale("menu.confirm.title"));
        var question = render(player, question(model));
        var confirm = actions.buttonOnce(
                render(player, MenuText.create().text("<add>").append(model.confirmLabel())),
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
        if (model.alternative().isPresent()) {
            var alternative = model.alternative().get();
            var chosen = actions.buttonOnce(
                    render(player, MenuText.create().text("<add>").append(alternative.label())),
                    null,
                    (clicked, response) -> alternative.onChoose().accept(clicked));
            var type = DialogType.multiAction(List.of(confirm, chosen, deny))
                    .columns(1)
                    .build();
            return Optional.of(
                    Dialog.create(factory -> factory.empty().base(base).type(type)));
        }
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
     * @param question     the question, with its placeholders
     * @param onConfirm    runs when the player confirms
     * @param onCancel     runs when the player denies
     * @param confirmLabel text of the confirm button
     * @param alternative  another way to confirm, or empty
     */
    public record Prompt(
            MenuText question,
            Consumer<Player> onConfirm,
            Consumer<Player> onCancel,
            MenuText confirmLabel,
            Optional<Choice> alternative) {
        /**
         * A prompt with the usual confirm and deny buttons.
         *
         * @param question  the question, with its placeholders
         * @param onConfirm runs when the player confirms
         * @param onCancel  runs when the player denies
         */
        public Prompt(MenuText question, Consumer<Player> onConfirm, Consumer<Player> onCancel) {
            this(question, onConfirm, onCancel, MenuText.create().text("<i18n:words.confirm>"), Optional.empty());
        }
    }

    /**
     * Another way to confirm, shown as its own button.
     *
     * @param label    text of the button, with its placeholders
     * @param onChoose runs when the player chooses it
     */
    public record Choice(MenuText label, Consumer<Player> onChoose) {}
}
