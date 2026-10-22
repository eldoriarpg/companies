/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The list of company levels.
 * <p>
 * Each level shows its description when hovered. In chat it has {@code [Info]} and {@code [Remove]} buttons and a
 * {@code [Move]} button suggesting the typed move command; {@code [Create]} appends a level. As a dialog every level
 * is a button opening its editor, and {@code [Create]} adds a level at the chosen position.
 */
public final class LevelListMenu implements Menu<LevelListView> {
    private final MessageSender messages;
    private final LevelActions levelActions;

    /**
     * @param messages     renders the hover and dialog texts
     * @param levelActions what the buttons do
     */
    public LevelListMenu(MessageSender messages, LevelActions levelActions) {
        this.messages = messages;
        this.levelActions = levelActions;
    }

    @Override
    public MenuText chat(Player player, LevelListView model, MenuActions actions) {
        var texts = new MenuComponents(messages, player);
        var text = MenuText.create()
                .text(
                        "<heading><i18n:words.level> <act_create><add>[<i18n:words.create>]</act_create>",
                        actions.chatAction("act_create", viewer -> levelActions.create(viewer, model.lastPosition())));
        model.error().ifPresent(failure -> text.newLine()
                .text("<rejection>", Values.component("rejection", LevelTexts.error(texts, failure))));
        for (var level : model.levels()) {
            text.newLine().append(chatRow(level, actions, texts));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, LevelListView model, MenuActions actions) {
        var texts = new MenuComponents(messages, player);
        List<DialogBody> body = new ArrayList<>();
        model.error().ifPresent(failure -> body.add(DialogBody.plainMessage(LevelTexts.error(texts, failure))));
        List<ActionButton> buttons = new ArrayList<>();
        for (var level : model.levels()) {
            buttons.add(actions.button(
                    texts.of(LevelTexts.label(level)),
                    texts.of(LevelDescription.of(level)),
                    (viewer, response) -> levelActions.info(viewer, level)));
        }
        buttons.add(actions.button(
                texts.of(MenuText.create().text("<add><i18n:words.create>")),
                null,
                (viewer, response) -> levelActions.create(
                        viewer, LevelForm.position(response::getText).orElse(model.lastPosition()))));
        var base = DialogBase.builder(texts.locale("companyadmin.level.listTitle"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(List.of(positionInput(texts, model)))
                .build();
        return Optional.of(Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(2).build())));
    }

    private MenuText chatRow(CompanyLevel level, MenuActions actions, MenuComponents texts) {
        var number = level.level();
        var details = Keys.of("level", number, "details");
        var info = Keys.of("level", number, "info");
        var remove = Keys.of("level", number, "remove");
        var move = Keys.of("level", number, "move");
        return MenuText.create()
                .text(
                        "<hover:show_text:'<" + details + ">'>",
                        Values.component(details, texts.of(LevelDescription.of(level))))
                .append(LevelTexts.label(level, Keys.of("level", number)))
                .text("</hover> ")
                .text(
                        "<" + info + "><show>[<i18n:words.info>]</" + info + "> ",
                        actions.chatAction(info, viewer -> levelActions.info(viewer, level)))
                .text(
                        "<" + remove + "><remove>[<i18n:words.remove>]</" + remove + "> ",
                        actions.chatAction(remove, viewer -> levelActions.remove(viewer, level)))
                .text(
                        "<" + move + "><modify>[<i18n:words.move>]</" + move + ">",
                        Values.click(move, ClickEvent.suggestCommand(LevelTexts.moveCommand(level))));
    }

    private static DialogInput positionInput(MenuComponents texts, LevelListView model) {
        return LevelTexts.positionInput(
                texts.locale("companyadmin.level.newPosition"), model.lastPosition(), model.lastPosition());
    }
}
