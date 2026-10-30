/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The form creating a server event, see {@link EventForm}.
 * <p>
 * In chat every field shows its value with a {@code [Change]} button suggesting
 * {@code /companyadmin event set <field> <value>}, and {@code [Create]} and {@code [Discard]} are buttons;
 * {@code [Create]} creates the form as it is when clicked, with every change made from chat since. The dialog
 * has one input per field; {@code [Create]} reads all of them at once, so an invalid value creates nothing and shows
 * the form again with the error on top.
 */
public final class EventFormMenu implements Menu<EventFormMenu.View> {
    private static final int MAX_LENGTH = 256;

    private final MessageSender messages;
    private final Actions formActions;

    /**
     * @param messages    renders the dialog texts
     * @param formActions what the buttons do
     */
    public EventFormMenu(MessageSender messages, Actions formActions) {
        this.messages = messages;
        this.formActions = formActions;
    }

    @Override
    public MenuText chat(Player player, View model, MenuActions actions) {
        var form = model.form();
        var text = title(form)
                .text(
                        " <act_create><add>[<i18n:companyadmin.event.create>]</act_create>",
                        actions.chatAction("act_create", formActions::create))
                .text(
                        " <act_discard><remove>[<i18n:companyadmin.event.discard>]</act_discard>",
                        actions.chatAction("act_discard", formActions::discard));
        var error = model.error();
        if (error != null) text.newLine().append(error);
        text.newLine().locale("companyadmin.event.hint");
        for (var field : EventForm.Field.values()) {
            var valueKey = Keys.of("value", field.key());
            var changeKey = Keys.of("change", field.key());
            var entered = form.text(field);
            text.newLine()
                    .text(
                            "<name><i18n:" + field.labelKey() + ">: <value><" + valueKey + ">",
                            Values.text(valueKey, entered.isEmpty() ? "-" : entered))
                    .text(
                            " <" + changeKey + "><modify>[<i18n:words.change>]</" + changeKey + ">",
                            Values.click(
                                    changeKey,
                                    ClickEvent.suggestCommand(
                                            "/companyadmin event set " + field.key() + " " + entered)));
        }
        return text;
    }

    @Override
    public Optional<Dialog> dialog(Player player, View model, MenuActions actions) {
        var texts = new MenuComponents(messages, player);
        var form = model.form();
        List<DialogBody> body = new ArrayList<>();
        var error = model.error();
        if (error != null) body.add(DialogBody.plainMessage(texts.error(error)));
        body.add(DialogBody.plainMessage(texts.locale("companyadmin.event.hint")));
        List<DialogInput> inputs = new ArrayList<>();
        for (var field : EventForm.Field.values()) {
            inputs.add(DialogInput.text(field.key(), texts.locale(field.labelKey()))
                    .initial(form.text(field))
                    .maxLength(MAX_LENGTH)
                    .build());
        }
        var create = actions.button(
                texts.of(MenuText.create().text("<add><i18n:companyadmin.event.create>")),
                null,
                (viewer, response) -> formActions.save(viewer, read(form, response::getText)));
        var discard = actions.button(
                texts.of(MenuText.create().text("<remove><i18n:companyadmin.event.discard>")),
                null,
                (viewer, response) -> formActions.discard(viewer));
        var base = DialogBase.builder(texts.of(title(form)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(inputs)
                .build();
        return Optional.of(Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(create, discard))
                        .columns(2)
                        .build())));
    }

    /**
     * Reads the inputs of the dialog into the form.
     *
     * @param form   the form shown
     * @param inputs text of an input by its key, null when the dialog has no such input
     * @return the form with the entered texts
     */
    public static EventForm read(EventForm form, Function<String, @Nullable String> inputs) {
        var entered = new EnumMap<EventForm.Field, String>(EventForm.Field.class);
        for (var field : EventForm.Field.values()) {
            var text = inputs.apply(field.key());
            if (text != null && !text.isBlank()) entered.put(field, text.strip());
        }
        return new EventForm(form.name(), entered);
    }

    private static MenuText title(EventForm form) {
        return MenuText.create()
                .text("<heading>")
                .locale("companyadmin.event.formTitle", Values.text("event_name", form.name()));
    }

    /**
     * What the form shows.
     *
     * @param form  the form
     * @param error why the form was refused the last time, null when it was not
     */
    public record View(EventForm form, @Nullable MenuText error) {}

    /**
     * What the buttons of the form do.
     */
    public interface Actions {
        /**
         * Creates the event of a form.
         *
         * @param player the admin
         * @param form   the form
         */
        void save(Player player, EventForm form);

        /**
         * Creates the event of the form the admin has open now, which holds the changes made from chat since the form
         * was shown.
         *
         * @param player the admin
         */
        void create(Player player);

        /**
         * Throws the form away.
         *
         * @param player the admin
         */
        void discard(Player player);
    }
}
