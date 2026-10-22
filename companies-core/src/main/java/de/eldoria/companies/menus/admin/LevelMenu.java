/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.menus.admin;

import de.eldoria.companies.menus.Keys;
import de.eldoria.companies.menus.Menu;
import de.eldoria.companies.menus.MenuActions;
import de.eldoria.companies.menus.MenuComponents;
import de.eldoria.companies.menus.MenuText;
import de.eldoria.companies.menus.Values;
import de.eldoria.eldoutilities.messages.MessageSender;
import io.papermc.paper.dialog.Dialog;
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
 * The editor of one company level.
 * <p>
 * In chat every field shows its value with a {@code [Change]} button suggesting the typed edit command, the move
 * button suggests the typed move command, and {@code [Remove]} and {@code [Back]} are buttons. The dialog is a form
 * with one input per field and a choice of the position; {@code [Save]} applies all of them at once, so an invalid
 * value changes nothing and shows the form again with the error on top.
 */
public final class LevelMenu implements Menu<LevelView> {
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_NUMBER_LENGTH = 16;

    private final MessageSender messages;
    private final LevelActions levelActions;

    /**
     * @param messages     renders the dialog texts
     * @param levelActions what the buttons do
     */
    public LevelMenu(MessageSender messages, LevelActions levelActions) {
        this.messages = messages;
        this.levelActions = levelActions;
    }

    @Override
    public MenuText chat(Player player, LevelView model, MenuActions actions) {
        var texts = new MenuComponents(messages, player);
        var level = model.level();
        var text = title(model)
                .text(
                        " <act_move><modify>[<i18n:words.move>]</act_move>",
                        Values.click("act_move", ClickEvent.suggestCommand(LevelTexts.moveCommand(level))))
                .text(
                        " <act_remove><remove>[<i18n:words.remove>]</act_remove>",
                        actions.chatAction("act_remove", viewer -> levelActions.remove(viewer, level)))
                .text(
                        " <act_back><show>[<i18n:words.back>]</act_back>",
                        actions.chatAction("act_back", levelActions::list));
        model.rejection().ifPresent(rejection -> text.newLine()
                .text("<rejection>", Values.component("rejection", LevelTexts.error(texts, rejection))));
        var group = LevelField.Group.NAME;
        for (var field : LevelField.values()) {
            if (field.group() != group) {
                group = field.group();
                text.newLine().text("<heading>").locale(group.headingKey());
            }
            text.newLine().append(chatField(model, field));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, LevelView model, MenuActions actions) {
        var texts = new MenuComponents(messages, player);
        var level = model.level();
        List<DialogBody> body = new ArrayList<>();
        model.rejection().ifPresent(rejection -> body.add(DialogBody.plainMessage(LevelTexts.error(texts, rejection))));
        body.add(DialogBody.plainMessage(texts.locale("companyadmin.level.taxHint")));
        List<DialogInput> inputs = new ArrayList<>();
        for (var field : LevelField.values()) {
            inputs.add(DialogInput.text(field.key(), texts.locale(field.labelKey()))
                    .initial(model.entered(field))
                    .maxLength(field == LevelField.NAME ? MAX_NAME_LENGTH : MAX_NUMBER_LENGTH)
                    .build());
        }
        inputs.add(LevelTexts.positionInput(
                texts.locale("companyadmin.level.position"), model.levelCount(), level.level()));
        var save = actions.button(
                texts.of(MenuText.create().text("<add><i18n:companyadmin.level.save>")),
                null,
                (viewer, response) -> levelActions.save(
                        viewer, model, LevelForm.read(response::getText), LevelForm.position(response::getText)));
        var remove = actions.button(
                texts.of(MenuText.create().text("<remove><i18n:words.remove>")),
                null,
                (viewer, response) -> levelActions.remove(viewer, level));
        var back = actions.button(texts.locale("words.back"), null, (viewer, response) -> levelActions.list(viewer));
        var base = DialogBase.builder(texts.of(title(model)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(inputs)
                .build();
        return Optional.of(Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(save, remove, back))
                        .columns(3)
                        .build())));
    }

    private static MenuText chatField(LevelView model, LevelField field) {
        var level = model.level();
        var valueKey = Keys.of("value", field.key());
        var changeKey = Keys.of("change", field.key());
        var indent = field.group() == LevelField.Group.NAME ? "" : "  ";
        return MenuText.create()
                .text(indent + "<name><i18n:" + field.labelKey() + ">: <value>")
                .append(value(field, valueKey, field.input(level)))
                .text(
                        " <" + changeKey + "><modify>[<i18n:words.change>]</" + changeKey + ">",
                        Values.click(changeKey, ClickEvent.suggestCommand(LevelTexts.editCommand(level, field))));
    }

    private static MenuText value(LevelField field, String key, String input) {
        if (field == LevelField.TAX_PERCENT && input.isEmpty()) {
            return MenuText.create().text("<i18n:companyadmin.level.globalTax>");
        }
        var shown = field == LevelField.TAX_PERCENT ? input + " %" : input;
        return MenuText.create().text("<" + key + ">", Values.text(key, shown));
    }

    private static MenuText title(LevelView model) {
        return MenuText.create().text("<heading><i18n:words.level> ").append(LevelTexts.label(model.level()));
    }
}
